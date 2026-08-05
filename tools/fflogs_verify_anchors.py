#!/usr/bin/env python3
"""Compare VedaAxis timeline anchors with one extracted FFLogs fight."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


EVENT_TYPE_BY_KIND = {
    "CAST_START": "begincast",
    "ACTION_EFFECT": "cast",
}


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def verify_anchors(report_dir: Path, seed_manifest: Path, tolerance_ms: int) -> dict[str, Any]:
    seed = read_json(seed_manifest)
    metadata = read_json(report_dir / "raw" / "metadata.json")
    report = metadata["data"]["reportData"]["report"]
    fight_id = int(report_dir.name.removeprefix("fight-"))
    fight = next((item for item in report.get("fights") or [] if item.get("id") == fight_id), None)
    if fight is None:
        raise ValueError(f"fight {fight_id} is missing from metadata")

    fight_start = float(fight["startTime"])
    phase_starts = {
        f"P{transition['id']}": round(float(transition["startTime"]) - fight_start)
        for transition in fight.get("phaseTransitions") or []
    }
    events = [
        json.loads(line)
        for line in (report_dir / "events.normalized.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]

    results: list[dict[str, Any]] = []
    for anchor in sorted(seed.get("anchors") or [], key=lambda item: item["plannedAtMs"]):
        expected_type = EVENT_TYPE_BY_KIND.get(anchor["kind"])
        if expected_type is None:
            raise ValueError(f"unsupported anchor kind: {anchor['kind']}")
        matches = [
            event
            for event in events
            if event.get("type") == expected_type
            and event.get("abilityGameId") == anchor["actionId"]
        ]
        occurrence_index = int(anchor["occurrence"]) - 1
        event = matches[occurrence_index] if occurrence_index < len(matches) else None
        seed_phase_start_ms = round(float(seed["phaseStartsSeconds"][anchor["phase"]]) * 1000)
        seed_phase_time_ms = int(anchor["plannedAtMs"]) - seed_phase_start_ms
        row: dict[str, Any] = {
            "anchorId": anchor["anchorId"],
            "phase": anchor["phase"],
            "kind": anchor["kind"],
            "expectedEventType": expected_type,
            "actionId": anchor["actionId"],
            "occurrence": anchor["occurrence"],
            "plannedAtMs": anchor["plannedAtMs"],
            "seedPhaseStartMs": seed_phase_start_ms,
            "seedPhaseTimeMs": seed_phase_time_ms,
            "matched": event is not None,
        }
        if event is not None:
            observed_at_ms = int(event.get("fightTimeMs", round(float(event["timestamp"]) - fight_start)))
            fflogs_phase_start_ms = phase_starts.get(anchor["phase"])
            row.update(
                {
                    "abilityName": event.get("abilityName"),
                    "observedAtMs": observed_at_ms,
                    "absoluteDriftMs": observed_at_ms - int(anchor["plannedAtMs"]),
                    "withinAbsoluteTolerance": abs(observed_at_ms - int(anchor["plannedAtMs"]))
                    <= tolerance_ms,
                    "fflogsPhaseStartMs": fflogs_phase_start_ms,
                }
            )
            if fflogs_phase_start_ms is not None:
                observed_phase_time_ms = observed_at_ms - fflogs_phase_start_ms
                row.update(
                    {
                        "observedPhaseTimeMs": observed_phase_time_ms,
                        "phaseLocalDriftMs": observed_phase_time_ms - seed_phase_time_ms,
                        "withinPhaseTolerance": abs(observed_phase_time_ms - seed_phase_time_ms)
                        <= tolerance_ms,
                    }
                )
        results.append(row)

    matched = sum(1 for row in results if row["matched"])
    within_absolute = sum(1 for row in results if row.get("withinAbsoluteTolerance"))
    return {
        "schemaVersion": "1.0",
        "reportCode": report_dir.parent.name,
        "fightId": fight_id,
        "fightName": fight.get("name"),
        "toleranceMs": tolerance_ms,
        "fflogsPhaseStartsMs": phase_starts,
        "summary": {
            "anchors": len(results),
            "matched": matched,
            "withinAbsoluteTolerance": within_absolute,
            "status": "MATCH" if matched == len(results) and within_absolute == len(results) else "MISMATCH",
        },
        "anchors": results,
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report-dir", type=Path, required=True)
    parser.add_argument("--seed-manifest", type=Path, default=Path("data/seeds/dmu/manifest.json"))
    parser.add_argument("--tolerance-ms", type=int, default=2000)
    parser.add_argument("--output", type=Path)
    return parser


def main() -> int:
    try:
        args = build_parser().parse_args()
        result = verify_anchors(args.report_dir.resolve(), args.seed_manifest.resolve(), args.tolerance_ms)
        output = args.output or args.report_dir / "anchor-verification.json"
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        print(output.resolve())
        return 0
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
