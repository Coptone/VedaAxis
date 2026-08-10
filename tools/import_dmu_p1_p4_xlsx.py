from __future__ import annotations

import argparse
import json
import re
import uuid
from copy import deepcopy
from dataclasses import dataclass
from datetime import time
from pathlib import Path
from typing import Any

from openpyxl import load_workbook


NAMESPACE = uuid.UUID("2fffe1d5-707c-4df3-95e0-c1f624f2e8da")


TRACK_COLUMNS = {
    "P1": {"MT": 7, "MT1": 8, "ST": 9, "H1": 10, "H2": 11, "D1": 12, "D2": 13, "D3": 14, "D4": 15},
    "P2": {"MT": 8, "MT1": 9, "ST": 10, "H1": 11, "H2": 12, "D1": 13, "D2": 14, "D3": 15, "D4": 16},
    "P3": {"MT": 8, "MT1": 9, "ST": 10, "H1": 11, "H2": 12, "D1": 13, "D2": 14, "D3": 15, "D4": 16},
    "P4": {"MT": 8, "MT1": 9, "ST": 10, "H1": 11, "H2": 12, "D1": 13, "D2": 14, "D3": 15, "D4": 16},
}


TIME_COLUMN = {"P1": 1, "P2": 2, "P3": 2, "P4": 2}
MECHANIC_COLUMNS = {
    "P1": {"name": 2, "effect": 3, "damage_type": 4, "damage": 5, "note": 6},
    "P2": {"name": 3, "effect": 4, "damage_type": 5, "damage": 6, "note": 7},
    "P3": {"name": 3, "effect": 4, "damage_type": 5, "damage": 6, "note": 7},
    "P4": {"name": 3, "effect": 4, "damage_type": 5, "damage": 6, "note": 7},
}


TRACK_ALIASES = {"MT1": "MT"}
TRACK_TARGETS = {"MT", "ST", "H1", "H2", "D1", "D2", "D3", "D4"}


@dataclass(frozen=True)
class Ability:
    action_id: int
    confirmation_strategy: str
    cooldown_ms: int
    max_charges: int = 1


ABILITIES: dict[int, Ability] = {
    43: Ability(43, "STATUS_APPLY", 240_000),
    40: Ability(40, "STATUS_APPLY", 90_000),
    7531: Ability(7531, "STATUS_APPLY", 90_000),
    7535: Ability(7535, "STATUS_APPLY", 60_000),
    7388: Ability(7388, "STATUS_APPLY", 90_000),
    36923: Ability(36923, "STATUS_APPLY", 120_000),
    25751: Ability(25751, "STATUS_APPLY", 25_000),
    16152: Ability(16152, "STATUS_APPLY", 360_000),
    25758: Ability(25758, "STATUS_APPLY", 25_000),
    16160: Ability(16160, "STATUS_APPLY", 90_000),
    7432: Ability(7432, "STATUS_APPLY", 30_000, 2),
    25861: Ability(25861, "STATUS_APPLY", 60_000),
    16536: Ability(16536, "STATUS_APPLY", 120_000),
    7433: Ability(7433, "STATUS_APPLY", 60_000),
    25862: Ability(25862, "ACTION_EFFECT", 180_000),
    37011: Ability(37011, "STATUS_APPLY", 1_000),
    3570: Ability(3570, "ACTION_EFFECT", 60_000, 2),
    16531: Ability(16531, "ACTION_EFFECT", 2_500),
    16534: Ability(16534, "ACTION_EFFECT", 2_500),
    24300: Ability(24300, "STATUS_APPLY", 120_000),
    37034: Ability(37034, "STATUS_APPLY", 1_500),
    24291: Ability(24291, "STATUS_APPLY", 1_500),
    24298: Ability(24298, "STATUS_APPLY", 30_000),
    24302: Ability(24302, "STATUS_APPLY", 60_000),
    24303: Ability(24303, "STATUS_APPLY", 45_000),
    24305: Ability(24305, "STATUS_APPLY", 120_000),
    24310: Ability(24310, "STATUS_APPLY", 120_000),
    24311: Ability(24311, "STATUS_APPLY", 120_000),
    24317: Ability(24317, "STATUS_APPLY", 60_000),
    24318: Ability(24318, "ACTION_EFFECT", 120_000),
    24299: Ability(24299, "ACTION_EFFECT", 30_000),
    24296: Ability(24296, "ACTION_EFFECT", 1_000),
    37035: Ability(37035, "STATUS_APPLY", 180_000),
    7549: Ability(7549, "STATUS_APPLY", 90_000),
    7560: Ability(7560, "STATUS_APPLY", 90_000),
    16012: Ability(16012, "STATUS_APPLY", 90_000),
    16889: Ability(16889, "STATUS_APPLY", 90_000),
    2887: Ability(2887, "STATUS_APPLY", 120_000),
}


@dataclass(frozen=True)
class AssignmentCandidate:
    action_id: int
    target_slot: str | None = None


def stable_uuid(value: str) -> str:
    return str(uuid.uuid5(NAMESPACE, value))


def parse_time_ms(value: Any) -> int | None:
    if value in (None, ""):
        return None
    if isinstance(value, time):
        return ((value.hour * 60) + value.minute) * 1000 + value.second * 1000 + value.microsecond // 1000
    if isinstance(value, str):
        match = re.match(r"^\s*(\d{1,2}):(\d{2})(?::\d{2})?\s*$", value)
        if match:
            return (int(match.group(1)) * 60 + int(match.group(2))) * 1000
    return None


def inferred_time_ms(ws: Any, phase: str, row: int, note: str) -> int | None:
    if phase == "P4" and row == 2 and "转场5s" in note:
        for next_row in range(row + 1, ws.max_row + 1):
            next_time = parse_time_ms(ws.cell(next_row, TIME_COLUMN[phase]).value)
            if next_time is not None:
                return max(0, next_time - 5_000)
    return None


def clean_text(value: Any) -> str:
    if value in (None, ""):
        return ""
    return str(value).replace("\n", " ").strip()


def parse_damage_amount(value: Any) -> int | None:
    text = clean_text(value)
    if not text:
        return None
    if "x" in text.lower():
        parts = re.findall(r"\d+", text)
        if len(parts) >= 2:
            return int(parts[0]) * int(parts[1])
    if "/" in text:
        numbers = [int(item) for item in re.findall(r"\d+", text)]
        return max(numbers) if numbers else None
    numbers = re.findall(r"\d+", text)
    if not numbers:
        return None
    return int("".join(numbers))


def damage_type(raw: str) -> str:
    if "物理" in raw:
        return "PHYSICAL"
    if "魔法" in raw:
        return "MAGICAL"
    if "固定" in raw:
        return "SPECIAL"
    return "UNKNOWN"


def mechanic_type(name: str, effect: str, raw_damage_type: str, damage: int | None) -> str:
    combined = f"{name} {effect} {raw_damage_type}"
    if "死刑" in combined:
        return "TANK_BUSTER"
    if damage is None:
        return "MECHANIC"
    if any(keyword in combined for keyword in ["AOE", "全体", "分摊", "踩塔", "地震", "大十字", "究极", "冲击", "暗黑光", "死亡落雷", "死亡波纹", "死亡波涛", "轰隆隆"]):
        return "RAIDWIDE"
    return "MECHANIC"


def mechanic_target(kind: str, name: str, effect: str) -> str:
    combined = f"{name} {effect}"
    if kind == "TANK_BUSTER":
        return "坦克"
    if "平A" in combined or "攻击" in name:
        return "当前一仇"
    if "分摊" in combined:
        return "分摊目标"
    if kind == "RAIDWIDE":
        return "全体"
    return "机制目标"


def workbook_mechanics(xlsx: Path) -> dict[str, dict[str, Any]]:
    wb = load_workbook(xlsx, data_only=True)
    mechanics: dict[str, dict[str, Any]] = {}
    for phase in ["P3", "P4"]:
        ws = wb[phase]
        columns = MECHANIC_COLUMNS[phase]
        for row in range(2, ws.max_row + 1):
            planned_at_ms = parse_time_ms(ws.cell(row, TIME_COLUMN[phase]).value)
            name = clean_text(ws.cell(row, columns["name"]).value)
            effect = clean_text(ws.cell(row, columns["effect"]).value)
            raw_damage_type = clean_text(ws.cell(row, columns["damage_type"]).value)
            raw_damage = ws.cell(row, columns["damage"]).value
            note = clean_text(ws.cell(row, columns["note"]).value)
            if planned_at_ms is None:
                planned_at_ms = inferred_time_ms(ws, phase, row, note)
            if planned_at_ms is None or not (name or effect):
                continue
            external_id = f"XLSX-{phase}-R{row}"
            display_name = name or effect
            amount = parse_damage_amount(raw_damage)
            kind = mechanic_type(display_name, effect, raw_damage_type, amount)
            mechanic: dict[str, Any] = {
                "mechanicId": stable_uuid(f"dmu:{external_id}:mechanic"),
                "externalId": external_id,
                "phase": phase,
                "name": display_name,
                "plannedAtMs": planned_at_ms,
                "durationMs": 0,
                "type": kind,
                "damageType": damage_type(raw_damage_type),
                "target": mechanic_target(kind, display_name, effect),
                "actionId": None,
                "confidence": "POC_PENDING",
            }
            if amount is not None and amount > 0:
                mechanic["damageProfile"] = {
                    "amount": amount,
                    "basis": "OBSERVED_TARGET_ADJUSTED",
                    "sampleCount": 1,
                    "statistic": "MAX_OBSERVED",
                    "source": f"Owner supplied p1-4.xlsx peak damage · {phase} row {row} · note: {note or 'none'}",
                    "confidence": "POC_PENDING",
                }
            mechanics[external_id] = mechanic
    return mechanics


def target_from_text(text: str) -> str | None:
    normalized = text.upper()
    for slot in sorted(TRACK_TARGETS, key=len, reverse=True):
        if slot in normalized:
            return slot
    return None


def add_if(candidates: list[AssignmentCandidate], action_id: int, text: str, target: str | None = None) -> None:
    candidates.append(AssignmentCandidate(action_id, target or target_from_text(text)))


def map_cell_to_actions(slot: str, raw_text: str) -> tuple[list[AssignmentCandidate], list[str], list[str]]:
    text = raw_text.replace("，", ",").replace("＋", "+").replace("青汁", "清汁")
    candidates: list[AssignmentCandidate] = []
    unresolved: list[str] = []
    ignored: list[str] = []

    if "活化" in text and "群盾" in text:
        add_if(candidates, 24300, text)
        add_if(candidates, 37034, text)
    elif "活化" in text:
        add_if(candidates, 24300, text)

    if "群盾" in text or "刷个盾" in text:
        add_if(candidates, 37034, text)
    if "单盾" in text:
        add_if(candidates, 24291, text)
    if "罩子" in text:
        add_if(candidates, 24298, text)
    if "自生" in text:
        add_if(candidates, 24302, text)
    if "白牛" in text:
        add_if(candidates, 24303, text)
    if "寄生" in text:
        add_if(candidates, 24299, text)
    if "拯救" in text or "混合" in text:
        add_if(candidates, 24317, text)
    if "泛输血" in text:
        add_if(candidates, 24311, text)
    elif "输血" in text:
        add_if(candidates, 24305, text)
    if "贤炮" in text:
        add_if(candidates, 24318, text)
    if "整体论" in text:
        add_if(candidates, 24310, text)
    if "智慧之爱" in text:
        add_if(candidates, 37035, text)
    if "单奶" in text and slot == "H2":
        add_if(candidates, 24296, text)

    if "神祝" in text:
        add_if(candidates, 7432, text)
    if "水流" in text:
        add_if(candidates, 25861, text)
    if "翅膀" in text:
        add_if(candidates, 16536, text)
    if "全大" in text:
        add_if(candidates, 7433, text)
    if "铃兰" in text:
        add_if(candidates, 25862, text)
    if slot == "H1" and "盾" in text:
        add_if(candidates, 37011, text)
    if "神明" in text or "神名" in text:
        add_if(candidates, 3570, text)
    if "安慰之心" in text or "安慰" in text:
        add_if(candidates, 16531, text)
    if "狂喜" in text:
        add_if(candidates, 16534, text)

    if "摆脱" in text:
        add_if(candidates, 7388, text)
    if "雪仇" in text or "血仇" in text:
        add_if(candidates, 7535, text)
    if "铁壁" in text:
        add_if(candidates, 7531, text)
    if "战栗" in text:
        add_if(candidates, 40, text)
    if "血气" in text:
        add_if(candidates, 25751, text)
    if "大减" in text:
        add_if(candidates, 36923, text)
    if "死斗" in text:
        add_if(candidates, 43, text)
    if "刚玉" in text:
        add_if(candidates, 25758, text)
    if "光之心" in text:
        add_if(candidates, 16160, text)
    if "无敌" in text and slot == "ST":
        add_if(candidates, 16152, text)

    if "牵制" in text:
        add_if(candidates, 7549, text)
    if "混乱" in text:
        add_if(candidates, 7560, text)
    if "桑巴" in text:
        add_if(candidates, 16012, text)
    if "策动" in text:
        add_if(candidates, 16889, text)
    if "扳手" in text or "武装解除" in text:
        add_if(candidates, 2887, text)

    if "TLB" in text.upper():
        ignored.append("tank-limit-break")
    if "拉蓝色" in text or "拉黄色" in text:
        ignored.append("boss-position-note")

    unique: list[AssignmentCandidate] = []
    seen: set[tuple[int, str | None]] = set()
    for candidate in candidates:
        key = (candidate.action_id, candidate.target_slot)
        if key not in seen:
            seen.add(key)
            unique.append(candidate)
    if not unique and not unresolved and not ignored:
        unresolved.append("unmapped-text")
    return unique, unresolved, ignored


def timing_for(text: str, planned_at_ms: int, action_id: int) -> tuple[int, int, int, int]:
    post_keywords = ["判定后", "击退后", "死刑结束", "分摊后", "清1血", "奶满后", "结束"]
    if any(keyword in text for keyword in post_keywords) and action_id not in {7531, 36923}:
        return planned_at_ms - 4_000, planned_at_ms + 1_000, planned_at_ms + 3_000, planned_at_ms + 3_000
    if any(keyword in text for keyword in ["读条前", "击退前", "buff消失"]):
        return planned_at_ms - 11_000, planned_at_ms - 6_000, planned_at_ms - 2_000, planned_at_ms
    return planned_at_ms - 7_000, planned_at_ms - 2_000, planned_at_ms, planned_at_ms


def workbook_assignments(
    xlsx: Path,
    mechanics_by_external: dict[str, dict[str, Any]],
    tracks: dict[str, str],
    initial_assignments: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    wb = load_workbook(xlsx, data_only=True)
    assignments: list[dict[str, Any]] = []
    unresolved: list[dict[str, Any]] = []
    ignored: list[dict[str, Any]] = []
    cooldown_state: dict[tuple[str, int], int] = {}
    for assignment in sorted(initial_assignments, key=lambda item: item["earliestUseAtMs"]):
        action_id = int(assignment["actionId"])
        ability = ABILITIES.get(action_id)
        if ability is None:
            continue
        cooldown_key = (assignment["trackId"], action_id)
        next_available = cooldown_state.get(cooldown_key, -10**12)
        scheduled_at = max(int(assignment["earliestUseAtMs"]), next_available)
        recovery = max(1, ability.cooldown_ms // max(1, ability.max_charges))
        cooldown_state[cooldown_key] = scheduled_at + recovery

    for phase in ["P3", "P4"]:
        ws = wb[phase]
        for row in range(2, ws.max_row + 1):
            planned_at_ms = parse_time_ms(ws.cell(row, TIME_COLUMN[phase]).value)
            note = clean_text(ws.cell(row, MECHANIC_COLUMNS[phase]["note"]).value)
            if planned_at_ms is None:
                planned_at_ms = inferred_time_ms(ws, phase, row, note)
            external_id = f"XLSX-{phase}-R{row}"
            mechanic = mechanics_by_external.get(external_id)
            if planned_at_ms is None or mechanic is None:
                for slot, col in TRACK_COLUMNS[phase].items():
                    text = clean_text(ws.cell(row, col).value)
                    if text:
                        unresolved.append({
                            "sheet": phase,
                            "row": row,
                            "slot": slot,
                            "text": text,
                            "reason": "missing-time-or-mechanic",
                        })
                continue

            for slot, col in TRACK_COLUMNS[phase].items():
                text = clean_text(ws.cell(row, col).value)
                if not text:
                    continue
                actual_slot = TRACK_ALIASES.get(slot, slot)
                candidates, reasons, ignored_reasons = map_cell_to_actions(actual_slot, text)
                for reason in reasons:
                    unresolved.append({
                        "sheet": phase,
                        "row": row,
                        "slot": slot,
                        "text": text,
                        "reason": reason,
                    })
                for reason in ignored_reasons:
                    ignored.append({
                        "sheet": phase,
                        "row": row,
                        "slot": slot,
                        "text": text,
                        "reason": reason,
                    })
                for candidate in candidates:
                    ability = ABILITIES[candidate.action_id]
                    highlight, earliest, latest, impact = timing_for(text, planned_at_ms, candidate.action_id)
                    track_id = tracks[actual_slot]
                    target_track_id = tracks.get(candidate.target_slot or "")
                    recovery = max(1, ability.cooldown_ms // max(1, ability.max_charges))
                    cooldown_key = (track_id, candidate.action_id)
                    available_at = cooldown_state.get(cooldown_key, -10**12)
                    if earliest < available_at:
                        post_support = any(keyword in text for keyword in ["判定后", "击退后", "死刑结束", "分摊后", "清1血", "奶满后", "结束", "好了就放"])
                        if available_at <= latest or (post_support and available_at <= latest + 5_000):
                            earliest = available_at
                            latest = max(latest, available_at)
                            impact = max(impact, latest)
                        else:
                            unresolved.append({
                                "sheet": phase,
                                "row": row,
                                "slot": slot,
                                "text": text,
                                "actionId": candidate.action_id,
                                "reason": "cooldown-conflict",
                                "availableAtMs": available_at,
                                "earliestUseAtMs": earliest,
                            })
                            continue
                    if earliest > latest:
                        unresolved.append({
                            "sheet": phase,
                            "row": row,
                            "slot": slot,
                            "text": text,
                            "actionId": candidate.action_id,
                            "reason": "invalid-adjusted-window",
                            "earliestUseAtMs": earliest,
                            "latestUseAtMs": latest,
                        })
                        continue
                    cooldown_state[cooldown_key] = earliest + recovery
                    assignments.append({
                        "assignmentId": stable_uuid(f"dmu:{external_id}:{actual_slot}:{candidate.action_id}:{candidate.target_slot or 'none'}"),
                        "mechanicId": mechanic["mechanicId"],
                        "trackId": track_id,
                        "actionId": candidate.action_id,
                        "anchorId": None,
                        "targetTrackId": target_track_id,
                        "highlightAtMs": highlight,
                        "earliestUseAtMs": earliest,
                        "latestUseAtMs": latest,
                        "impactAtMs": impact,
                        "locked": True,
                        "confirmationStrategy": ability.confirmation_strategy,
                        "fallbacks": [],
                    })
    return assignments, unresolved, ignored


def phase(phase_name: str, planned_at_ms: int, duration_ms: int, timing_mode: str) -> dict[str, Any]:
    return {
        "phaseId": stable_uuid(f"dmu:{phase_name}:phase"),
        "externalId": f"XLSX-{phase_name}",
        "name": phase_name,
        "plannedAtMs": planned_at_ms,
        "durationMs": duration_ms,
        "timingMode": timing_mode,
        "confidence": "POC_PENDING",
    }


def build_plan(template_path: Path, xlsx: Path) -> tuple[dict[str, Any], dict[str, Any]]:
    base = json.loads(template_path.read_text(encoding="utf-8"))
    plan = deepcopy(base)
    tracks = {track["slot"]: track["trackId"] for track in plan["tracks"]}

    for track in plan["tracks"]:
        if track["slot"] == "D3":
            track["allowedJobIds"] = [23, 31, 38]
            track["displayName"] = "远敏"

    new_mechanics_by_external = workbook_mechanics(xlsx)
    preserved_mechanics = [mechanic for mechanic in plan["mechanics"] if mechanic["phase"] in {"P1", "P2"}]
    all_mechanics = preserved_mechanics + list(new_mechanics_by_external.values())
    all_mechanics.sort(key=lambda item: (item["plannedAtMs"], item["externalId"] or ""))
    plan["mechanics"] = all_mechanics

    p3_mechanics = [item for item in new_mechanics_by_external.values() if item["phase"] == "P3"]
    p4_mechanics = [item for item in new_mechanics_by_external.values() if item["phase"] == "P4"]
    p3_start = min(item["plannedAtMs"] for item in p3_mechanics)
    p4_start = min(item["plannedAtMs"] for item in p4_mechanics)
    p4_end = max(item["plannedAtMs"] for item in p4_mechanics)
    plan["phases"] = [
        *[item for item in plan["phases"] if item["name"] in {"P1", "P2"}],
        phase("P3", p3_start, p4_start - p3_start, "RELATIVE"),
        phase("P4", p4_start, max(1, p4_end - p4_start), "ABSOLUTE"),
    ]
    plan["phases"].sort(key=lambda item: item["plannedAtMs"])

    preserved_assignments = [
        assignment for assignment in plan["assignments"]
        if any(mechanic["mechanicId"] == assignment["mechanicId"] and mechanic["phase"] in {"P1", "P2"} for mechanic in preserved_mechanics)
    ]
    new_assignments, unresolved, ignored = workbook_assignments(xlsx, new_mechanics_by_external, tracks, preserved_assignments)
    plan["assignments"] = sorted(
        preserved_assignments + new_assignments,
        key=lambda item: (item["highlightAtMs"], item["trackId"], item["actionId"]),
    )
    plan["timelineVersion"] = max(2, int(plan.get("timelineVersion", 1)) + 1)
    plan["minimumPluginVersion"] = "0.1.19"
    plan["source"] = {
        "kind": "PUBLIC_TEMPLATE",
        "reference": "attachment:p1-4.xlsx; original sheet https://docs.qq.com/sheet/DY1dHUUFkRWxDa2tL?tab=ksr0lg",
        "confidence": "POC_PENDING",
    }

    audit = {
        "source": f"attachment:{xlsx.name}",
        "generatedAt": "2026-08-10",
        "workbookSheets": ["P1", "P2", "P3", "P4"],
        "mechanicCount": len(plan["mechanics"]),
        "newMechanicCount": len(new_mechanics_by_external),
        "assignmentCount": len(plan["assignments"]),
        "newAssignmentCount": len(new_assignments),
        "targetAssignmentCount": sum(1 for assignment in plan["assignments"] if assignment.get("targetTrackId")),
        "unresolvedCells": unresolved,
        "ignoredCells": ignored,
        "notes": [
            "P1/P2 mechanics, assignments, action IDs and FFLogs damage calibration are preserved from the previous reviewed seed.",
            "P3 is marked RELATIVE because the owner noted that phase-end timing can shift with boss kill time.",
            "P3/P4 damage profiles use owner supplied peak-damage cells and remain POC_PENDING until FFLogs calibration is added.",
            "MT1 is treated as the MT personal-mitigation subcolumn, not a ninth execution track.",
            "Cooldown-conflicting generated assignments are omitted and listed in unresolvedCells instead of making the default plan unpublishable.",
            "Tank limit break and boss-position instructions are preserved in ignoredCells because the owner confirmed they are outside the mitigation-reminder scope.",
        ],
    }
    return plan, audit


def main() -> None:
    parser = argparse.ArgumentParser(description="Import owner supplied DMU P1-P4 mitigation workbook into the default seed plan.")
    parser.add_argument("--xlsx", required=True, type=Path)
    parser.add_argument("--template", default=Path("data/seeds/dmu/p1-p2-default-plan.json"), type=Path)
    parser.add_argument("--output", default=Path("data/seeds/dmu/p1-p2-default-plan.json"), type=Path)
    parser.add_argument("--audit-output", default=Path("data/seeds/dmu/p1-p2-import-audit.json"), type=Path)
    args = parser.parse_args()

    plan, audit = build_plan(args.template, args.xlsx)
    args.output.write_text(json.dumps(plan, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.audit_output.write_text(json.dumps(audit, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "mechanicCount": audit["mechanicCount"],
        "newMechanicCount": audit["newMechanicCount"],
        "assignmentCount": audit["assignmentCount"],
        "newAssignmentCount": audit["newAssignmentCount"],
        "unresolvedCellCount": len(audit["unresolvedCells"]),
        "ignoredCellCount": len(audit["ignoredCells"]),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
