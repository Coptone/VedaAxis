#!/usr/bin/env python3
"""Build anonymous, plan-ready damage calibration candidates from FFLogs samples.

The input report directories are produced by ``fflogs_extract.py`` and remain
git-ignored. The committed output contains no report code or player identity.
For every raidwide/tankbuster mechanic, enemy damage events are matched by
relative fight time and target pattern. Repeated hits from the same action
within one mechanic window are summed per target before a cross-report P95 is
calculated.
"""

from __future__ import annotations

import argparse
import json
import math
import statistics
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


@dataclass(frozen=True)
class Impact:
    action_id: int
    name: str
    start_ms: int
    end_ms: int
    target_count: int
    per_target_totals: tuple[int, ...]


@dataclass(frozen=True)
class ReportSample:
    sample_key: str
    impacts: tuple[Impact, ...]


def percentile_95(values: list[int]) -> int:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * 0.95) - 1)]


def read_jsonl(path: Path) -> Iterable[dict[str, Any]]:
    with path.open(encoding="utf-8") as source:
        for line_number, line in enumerate(source, 1):
            if not line.strip():
                continue
            try:
                yield json.loads(line)
            except json.JSONDecodeError as error:
                raise ValueError(f"invalid JSONL at {path}:{line_number}") from error


def build_report_sample(
        metadata: dict[str, Any], events: Iterable[dict[str, Any]], *, cluster_gap_ms: int = 5_000) -> ReportSample:
    report = metadata["data"]["reportData"]["report"]
    fights = report.get("fights") or []
    if len(fights) != 1:
        raise ValueError("metadata must contain exactly one fight")
    fight = fights[0]
    friendly_ids = set(fight.get("friendlyPlayers") or [])
    actors = (report.get("masterData") or {}).get("actors") or []
    enemy_ids = {actor["id"] for actor in actors if actor.get("type") == "NPC"}

    by_action: dict[int, list[dict[str, Any]]] = defaultdict(list)
    for event in events:
        action_id = event.get("abilityGameId")
        if (
            event.get("type") != "calculateddamage"
            or event.get("sourceId") not in enemy_ids
            or event.get("targetId") not in friendly_ids
            or not isinstance(action_id, int)
            or not isinstance(event.get("fightTimeMs"), (int, float))
        ):
            continue
        by_action[action_id].append(event)

    impacts: list[Impact] = []
    for action_id, action_events in by_action.items():
        ordered = sorted(action_events, key=lambda event: event["fightTimeMs"])
        clusters: list[list[dict[str, Any]]] = []
        for event in ordered:
            if not clusters or event["fightTimeMs"] - clusters[-1][-1]["fightTimeMs"] > cluster_gap_ms:
                clusters.append([event])
            else:
                clusters[-1].append(event)
        for cluster in clusters:
            totals: dict[int, int] = defaultdict(int)
            target_ids: set[int] = set()
            for event in cluster:
                target_id = event["targetId"]
                target_ids.add(target_id)
                raw = event.get("unmitigatedAmount")
                actual = event.get("amount")
                value = raw if isinstance(raw, (int, float)) and raw > 0 else actual
                if isinstance(value, (int, float)) and value > 0:
                    totals[target_id] += round(value)
            if not totals:
                continue
            name = next((str(event.get("abilityName")) for event in cluster if event.get("abilityName")),
                        f"Action {action_id}")
            impacts.append(Impact(
                action_id=action_id,
                name=name,
                start_ms=round(cluster[0]["fightTimeMs"]),
                end_ms=round(cluster[-1]["fightTimeMs"]),
                target_count=len(target_ids),
                per_target_totals=tuple(totals.values()),
            ))

    # The sample key is only used for in-memory de-duplication and is never
    # emitted. Absolute start time identifies duplicate uploads of one pull.
    sample_key = str(round(float(report.get("startTime") or 0) + float(fight.get("startTime") or 0)))
    return ReportSample(sample_key=sample_key, impacts=tuple(sorted(impacts, key=lambda impact: impact.start_ms)))


def _matches_target_pattern(mechanic_type: str, impact: Impact) -> bool:
    if mechanic_type == "RAIDWIDE":
        return impact.target_count >= 4
    if mechanic_type == "TANK_BUSTER":
        return 1 <= impact.target_count <= 2 and impact.name.casefold() != "attack"
    return False


def build_calibration(
        plan: dict[str, Any], reports: Iterable[ReportSample], *, tolerance_ms: int = 2_500,
        minimum_reports: int = 3, collected_at: str) -> dict[str, Any]:
    unique_reports = {report.sample_key: report for report in reports}
    mechanics: list[dict[str, Any]] = []
    unresolved: list[dict[str, Any]] = []

    for mechanic in plan.get("mechanics") or []:
        mechanic_type = mechanic.get("type")
        if mechanic_type not in {"RAIDWIDE", "TANK_BUSTER"}:
            continue
        planned_at = int(mechanic["plannedAtMs"])
        matches: list[tuple[str, Impact]] = []
        for sample_key, report in unique_reports.items():
            candidates = [
                impact for impact in report.impacts
                if abs(impact.start_ms - planned_at) <= tolerance_ms
                and _matches_target_pattern(mechanic_type, impact)
            ]
            if candidates:
                matches.append((sample_key, min(candidates, key=lambda impact: abs(impact.start_ms - planned_at))))

        action_counts = Counter(impact.action_id for _, impact in matches)
        if not action_counts:
            unresolved.append({
                "mechanicId": mechanic["mechanicId"], "phase": mechanic["phase"],
                "name": mechanic["name"], "plannedAtMs": planned_at, "reason": "NO_TIME_MATCH",
            })
            continue
        action_id, _ = action_counts.most_common(1)[0]
        selected = [(sample_key, impact) for sample_key, impact in matches if impact.action_id == action_id]
        report_count = len({sample_key for sample_key, _ in selected})
        values = [value for _, impact in selected for value in impact.per_target_totals]
        if report_count < minimum_reports or not values:
            unresolved.append({
                "mechanicId": mechanic["mechanicId"], "phase": mechanic["phase"],
                "name": mechanic["name"], "plannedAtMs": planned_at,
                "reason": "INSUFFICIENT_REPORTS", "matchedReportCount": report_count,
            })
            continue
        errors = [impact.start_ms - planned_at for _, impact in selected]
        action_name = Counter(impact.name for _, impact in selected).most_common(1)[0][0]
        amount = percentile_95(values)
        mechanics.append({
            "mechanicId": mechanic["mechanicId"],
            "phase": mechanic["phase"],
            "name": mechanic["name"],
            "plannedAtMs": planned_at,
            "actionId": action_id,
            "actionName": action_name,
            "mechanicType": mechanic_type,
            "damageType": mechanic.get("damageType", "UNKNOWN"),
            "amount": amount,
            "statistic": "P95",
            "targetObservationCount": len(values),
            "reportSampleCount": report_count,
            "medianTimingErrorMs": round(statistics.median(errors)),
            "maximumAbsoluteTimingErrorMs": max(abs(error) for error in errors),
            "confidence": "POC_PENDING",
        })

    return {
        "schemaVersion": "1.0",
        "encounterId": plan.get("encounterId"),
        "collectedAt": collected_at,
        "source": "FFLogs public zone 76 multi-report calibration",
        "reportSampleCount": len(unique_reports),
        "containsPlayerNames": False,
        "containsReportCodes": False,
        "basis": "OBSERVED_TARGET_ADJUSTED",
        "statistic": "P95",
        "promotionAllowed": False,
        "mechanics": mechanics,
        "unresolved": unresolved,
        "warnings": [
            "P95 values are target-adjusted FFLogs observations, not a universal boss damage formula.",
            "Repeated hits from the same action within five seconds are summed per target.",
            "Candidates require mechanic mapping review before publication and remain POC_PENDING.",
        ],
    }


def apply_calibration(plan: dict[str, Any], calibration: dict[str, Any]) -> dict[str, Any]:
    candidates = {item["mechanicId"]: item for item in calibration.get("mechanics") or []}
    for mechanic in plan.get("mechanics") or []:
        candidate = candidates.get(mechanic.get("mechanicId"))
        if not candidate:
            continue
        mechanic["actionId"] = candidate["actionId"]
        mechanic["damageProfile"] = {
            "amount": candidate["amount"],
            "basis": "OBSERVED_TARGET_ADJUSTED",
            "sampleCount": candidate["targetObservationCount"],
            "statistic": "P95",
            "source": (
                f"FFLogs public zone 76 · {candidate['reportSampleCount']} unique kills · "
                f"Action {candidate['actionId']} · {calibration['collectedAt']}"
            ),
            "confidence": "POC_PENDING",
        }
    return plan


def load_report_dir(path: Path) -> ReportSample:
    metadata_path = path / "raw" / "metadata.json"
    events_path = path / "events.normalized.jsonl"
    if not metadata_path.is_file() or not events_path.is_file():
        raise ValueError(f"invalid report directory: {path}")
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    return build_report_sample(metadata, read_jsonl(events_path))


def discover_report_dirs(root: Path) -> list[Path]:
    if not root.is_dir():
        raise ValueError(f"invalid report root: {root}")
    return sorted({
        events_path.parent.resolve()
        for events_path in root.rglob("events.normalized.jsonl")
        if (events_path.parent / "raw" / "metadata.json").is_file()
    })


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", required=True, type=Path)
    parser.add_argument("--report-dir", action="append", type=Path)
    parser.add_argument("--report-root", action="append", type=Path,
                        help="recursively discover extractor output below this directory")
    parser.add_argument("--collected-at", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--apply-output", type=Path)
    parser.add_argument("--tolerance-ms", type=int, default=2_500)
    parser.add_argument("--minimum-reports", type=int, default=3)
    args = parser.parse_args()

    plan = json.loads(args.plan.read_text(encoding="utf-8"))
    report_dirs = [path.resolve() for path in (args.report_dir or [])]
    for root in args.report_root or []:
        report_dirs.extend(discover_report_dirs(root.resolve()))
    report_dirs = sorted(set(report_dirs))
    if not report_dirs:
        parser.error("at least one --report-dir or --report-root is required")
    reports = [load_report_dir(path) for path in report_dirs]
    calibration = build_calibration(
        plan, reports, tolerance_ms=args.tolerance_ms,
        minimum_reports=args.minimum_reports, collected_at=args.collected_at,
    )
    args.output.write_text(json.dumps(calibration, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.apply_output:
        calibrated = apply_calibration(plan, calibration)
        args.apply_output.write_text(json.dumps(calibrated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
