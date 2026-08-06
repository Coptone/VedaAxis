#!/usr/bin/env python3
"""Build anonymized FFLogs damage-calibration candidates from an extracted fight.

The output is evidence for review, not plan-ready fixed damage. FFLogs actual
amounts already include the observed run's mitigation and target context, while
``unmitigatedAmount`` is the pre-multiplier raw value. Neither is safe to copy
directly into a universal mitigation plan.
"""

from __future__ import annotations

import argparse
import json
import math
import statistics
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable


def _percentile_95(values: list[int | float]) -> int | float:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * 0.95) - 1)]


def _summary(values: Iterable[int | float | None], *, decimals: int = 0) -> dict[str, int | float] | None:
    cleaned = [value for value in values if isinstance(value, (int, float)) and value > 0]
    if not cleaned:
        return None
    normalized = [round(value, decimals) if decimals else round(value) for value in cleaned]
    return {
        "minimum": min(normalized),
        "median": round(statistics.median(normalized), decimals) if decimals else round(statistics.median(normalized)),
        "p95": _percentile_95(normalized),
        "maximum": max(normalized),
    }


def _report(metadata: dict[str, Any]) -> dict[str, Any]:
    return metadata["data"]["reportData"]["report"]


def build_candidates(metadata: dict[str, Any], events: Iterable[dict[str, Any]]) -> dict[str, Any]:
    report = _report(metadata)
    fights = report.get("fights") or []
    if len(fights) != 1:
        raise ValueError("metadata must contain exactly one fight")
    fight = fights[0]
    friendly_ids = set(fight.get("friendlyPlayers") or [])
    actors = (report.get("masterData") or {}).get("actors") or []
    player_ids = {
        actor["id"]
        for actor in actors
        if actor.get("id") in friendly_ids and actor.get("type") == "Player"
    }
    enemy_ids = {
        actor["id"]
        for actor in actors
        if actor.get("type") == "NPC"
    }

    grouped: dict[int, list[dict[str, Any]]] = defaultdict(list)
    for event in events:
        action_id = event.get("abilityGameId")
        if (
            event.get("type") not in {"calculateddamage", "cast"}
            or event.get("sourceId") not in enemy_ids
            or event.get("targetId") not in player_ids
            or not isinstance(action_id, int)
        ):
            continue
        grouped[action_id].append(event)

    actions: list[dict[str, Any]] = []
    for action_id, observed_events in sorted(grouped.items()):
        hits = [event for event in observed_events if event.get("type") == "calculateddamage"]
        casts = [event for event in observed_events if event.get("type") == "cast"]
        target_sets: dict[tuple[str, int], set[int]] = defaultdict(set)
        hit_cast_keys: set[tuple[str, int]] = set()
        for hit in hits:
            if isinstance(hit.get("packetId"), int):
                cast_key = ("packet", hit["packetId"])
            else:
                cast_key = ("timestamp", int(hit.get("timestamp") or 0))
            hit_cast_keys.add(cast_key)
            target_sets[cast_key].add(hit["targetId"])
        explicit_cast_keys = {
            ("packet", event["packetId"])
            if isinstance(event.get("packetId"), int)
            else ("timestamp", int(event.get("timestamp") or 0))
            for event in casts
        }
        max_targets = max((len(targets) for targets in target_sets.values()), default=0)
        ability_name = next(
            (event.get("abilityName") for event in observed_events if event.get("abilityName")),
            f"Action {action_id}",
        )
        if str(ability_name).casefold() == "attack":
            target_pattern = "AUTO_ATTACK"
        elif max_targets >= 4:
            target_pattern = "AOE_CANDIDATE"
        elif max_targets >= 2:
            target_pattern = "MULTI_TARGET_CANDIDATE"
        else:
            target_pattern = "SINGLE_TARGET_CANDIDATE"
        actions.append({
            "actionId": action_id,
            "name": ability_name,
            "targetPattern": target_pattern,
            "hitEventCount": len(hits),
            "observedCastCount": len(explicit_cast_keys or hit_cast_keys),
            "maximumTargetsPerObservedCast": max_targets,
            "firstFightTimeMs": min((event.get("fightTimeMs", 0) for event in observed_events), default=0),
            "lastFightTimeMs": max((event.get("fightTimeMs", 0) for event in observed_events), default=0),
            "actualAmount": _summary(hit.get("amount") for hit in hits),
            "rawUnmitigatedAmount": _summary(hit.get("unmitigatedAmount") for hit in hits),
            "observedMultiplier": _summary((hit.get("multiplier") for hit in hits), decimals=4),
            "confidence": "POC_PENDING",
            "promotionAllowed": False,
        })

    return {
        "schemaVersion": "1.0",
        "encounterId": fight.get("encounterID"),
        "fightId": fight.get("id"),
        "actionCount": len(actions),
        "containsPlayerNames": False,
        "basis": "OBSERVED_FFLOGS_CANDIDATE",
        "promotionAllowed": False,
        "warnings": [
            "actualAmount already includes mitigation, shielding and the observed target context.",
            "rawUnmitigatedAmount is a pre-multiplier FFLogs value and is not direct character incoming damage.",
            "Target pattern is inferred only from targets observed per cast; tankbuster classification still requires mechanic review.",
            "A single report cannot establish a cross-party or cross-gear survival guarantee.",
        ],
        "actions": actions,
    }


def read_jsonl(path: Path) -> Iterable[dict[str, Any]]:
    with path.open(encoding="utf-8") as source:
        for line_number, line in enumerate(source, 1):
            if line.strip():
                try:
                    yield json.loads(line)
                except json.JSONDecodeError as error:
                    raise ValueError(f"invalid JSONL at {path}:{line_number}") from error


def generate(report_dir: Path, output: Path | None = None) -> Path:
    report_dir = report_dir.resolve()
    metadata_path = report_dir / "raw" / "metadata.json"
    events_path = report_dir / "events.normalized.jsonl"
    if not metadata_path.is_file() or not events_path.is_file():
        raise ValueError("report directory must contain raw/metadata.json and events.normalized.jsonl")
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    result = build_candidates(metadata, read_jsonl(events_path))
    output = (output or report_dir / "damage-candidates.json").resolve()
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return output


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report-dir", required=True, type=Path)
    parser.add_argument("--output", type=Path)
    return parser


def main() -> int:
    try:
        args = build_parser().parse_args()
        print(generate(args.report_dir, args.output))
        return 0
    except (ValueError, KeyError, OSError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
