#!/usr/bin/env python3
"""FFLogs v2 PoC extractor: OAuth, metadata/masterData, paged events and JSONL."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


DEFAULT_TOKEN_URL = "https://www.fflogs.com/oauth/token"
DEFAULT_API_URL = "https://www.fflogs.com/api/v2/client"
REPORT_PATTERN = re.compile(r"(?:https?://(?:cn\.)?fflogs\.com/reports/)?([A-Za-z0-9]+)")


def load_env_file(path: Path) -> None:
    """Load simple KEY=VALUE entries without overriding process variables."""
    if not path.is_file():
        return

    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"invalid env entry at {path}:{line_number}")
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if value[:1] == value[-1:] and value.startswith(("'", '"')):
            value = value[1:-1]
        if not key:
            raise ValueError(f"empty env key at {path}:{line_number}")
        os.environ.setdefault(key, value)

METADATA_QUERY = """
query VedaAxisReport($code: String!, $fightIds: [Int!]) {
  reportData {
    report(code: $code) {
      title
      startTime
      endTime
      fights(fightIDs: $fightIds) {
        id
        startTime
        endTime
        name
        kill
        encounterID
        friendlyPlayers
        phaseTransitions { id startTime }
        gameZone { id name }
      }
      masterData {
        actors { id name type subType gameID }
        abilities { gameID name type icon }
      }
    }
  }
}
"""

EVENTS_QUERY = """
query VedaAxisEvents(
  $code: String!,
  $fightIds: [Int!],
  $startTime: Float!,
  $endTime: Float!
) {
  reportData {
    report(code: $code) {
      events(
        fightIDs: $fightIds,
        startTime: $startTime,
        endTime: $endTime,
        limit: 10000
      ) {
        data
        nextPageTimestamp
      }
    }
  }
}
"""


def parse_report_code(value: str) -> str:
    match = REPORT_PATTERN.fullmatch(value.strip().split("?", 1)[0].rstrip("/"))
    if not match:
        raise ValueError("invalid FFLogs report URL or code")
    return match.group(1)


def request_json(
    url: str,
    *,
    data: bytes | None = None,
    headers: dict[str, str] | None = None,
) -> dict[str, Any]:
    request = urllib.request.Request(url, data=data, headers=headers or {}, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"FFLogs request failed with HTTP {error.code}: {detail[:500]}") from error


def oauth_token(token_url: str, client_id: str, client_secret: str) -> str:
    payload = urllib.parse.urlencode({"grant_type": "client_credentials"}).encode("ascii")
    authorization = (f"{client_id}:{client_secret}").encode("utf-8")
    import base64

    result = request_json(
        token_url,
        data=payload,
        headers={
            "Authorization": "Basic " + base64.b64encode(authorization).decode("ascii"),
            "Content-Type": "application/x-www-form-urlencoded",
            "Accept": "application/json",
        },
    )
    token = result.get("access_token")
    if not isinstance(token, str) or not token:
        raise RuntimeError("OAuth response did not contain access_token")
    return token


def graphql(api_url: str, token: str, query: str, variables: dict[str, Any]) -> dict[str, Any]:
    result = request_json(
        api_url,
        data=json.dumps({"query": query, "variables": variables}).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )
    if result.get("errors"):
        raise RuntimeError("FFLogs GraphQL error: " + json.dumps(result["errors"], ensure_ascii=False))
    return result


def normalize_event(
    report_code: str,
    fight_id: int,
    event: dict[str, Any],
    *,
    ability_names: dict[int, str] | None = None,
    fight_start_time: float | None = None,
) -> dict[str, Any]:
    ability = event.get("ability") or {}
    ability_game_id = ability.get("gameID") or event.get("abilityGameID")
    extra_ability_game_id = (
        (event.get("extraAbility") or {}).get("gameID") or event.get("extraAbilityGameID")
    )
    timestamp = event.get("timestamp")
    normalized = {
        "schemaVersion": "1.0",
        "reportCode": report_code,
        "fightId": fight_id,
        "timestamp": timestamp,
        "fightTimeMs": (
            round(float(timestamp) - fight_start_time)
            if timestamp is not None and fight_start_time is not None
            else None
        ),
        "type": event.get("type"),
        "sourceId": event.get("sourceID"),
        "targetId": event.get("targetID"),
        "abilityGameId": ability_game_id,
        "abilityName": ability.get("name") or (ability_names or {}).get(ability_game_id),
        "amount": event.get("amount"),
        "unmitigatedAmount": event.get("unmitigatedAmount"),
        "absorbed": event.get("absorbed"),
        "overheal": event.get("overheal"),
        "extraAbilityGameId": extra_ability_game_id,
        "extraAbilityName": (ability_names or {}).get(extra_ability_game_id),
        "stack": event.get("stack"),
        "targetResources": event.get("targetResources"),
    }
    return {key: value for key, value in normalized.items() if value is not None}


def extract(args: argparse.Namespace) -> Path:
    client_id = os.environ.get("FFLOGS_CLIENT_ID", "")
    client_secret = os.environ.get("FFLOGS_CLIENT_SECRET", "")
    if not client_id or not client_secret:
        raise RuntimeError("set FFLOGS_CLIENT_ID and FFLOGS_CLIENT_SECRET in the process environment")

    report_code = parse_report_code(args.report)
    token = oauth_token(args.token_url, client_id, client_secret)
    output = Path(args.output_dir).resolve() / report_code / f"fight-{args.fight_id}"
    raw = output / "raw"
    raw.mkdir(parents=True, exist_ok=True)

    metadata = graphql(
        args.api_url,
        token,
        METADATA_QUERY,
        {"code": report_code, "fightIds": [args.fight_id]},
    )
    (raw / "metadata.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    report = metadata["data"]["reportData"]["report"]
    fights = report.get("fights") or []
    if len(fights) != 1:
        raise RuntimeError(f"fight {args.fight_id} was not returned by FFLogs")
    fight = fights[0]
    cursor = float(fight["startTime"])
    fight_start_time = cursor
    end_time = float(fight["endTime"])
    ability_names = {
        int(ability["gameID"]): ability["name"]
        for ability in (report.get("masterData") or {}).get("abilities") or []
        if ability.get("gameID") is not None and ability.get("name")
    }
    page = 1
    normalized_path = output / "events.normalized.jsonl"

    with normalized_path.open("w", encoding="utf-8", newline="\n") as target:
        while cursor < end_time:
            result = graphql(
                args.api_url,
                token,
                EVENTS_QUERY,
                {
                    "code": report_code,
                    "fightIds": [args.fight_id],
                    "startTime": cursor,
                    "endTime": end_time,
                },
            )
            page_path = raw / f"events-{page:04d}.json"
            page_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
            events = result["data"]["reportData"]["report"]["events"]
            for event in events.get("data") or []:
                target.write(
                    json.dumps(
                        normalize_event(
                            report_code,
                            args.fight_id,
                            event,
                            ability_names=ability_names,
                            fight_start_time=fight_start_time,
                        ),
                        ensure_ascii=False,
                    )
                )
                target.write("\n")
            next_page = events.get("nextPageTimestamp")
            if next_page is None or float(next_page) <= cursor:
                break
            cursor = float(next_page)
            page += 1

    manifest = {
        "schemaVersion": "1.0",
        "reportCode": report_code,
        "fightId": args.fight_id,
        "apiUrl": args.api_url,
        "pages": page,
        "metadata": "raw/metadata.json",
        "normalizedEvents": normalized_path.name,
        "credentialsPersisted": False,
    }
    (output / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return output


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", required=True, help="report code or public report URL")
    parser.add_argument("--fight-id", required=True, type=int)
    parser.add_argument("--output-dir", default="data/fflogs-poc")
    parser.add_argument("--env-file", default=".env", help="local KEY=VALUE file; defaults to .env")
    parser.add_argument("--token-url", default=DEFAULT_TOKEN_URL)
    parser.add_argument("--api-url", default=DEFAULT_API_URL)
    return parser


def main() -> int:
    try:
        args = build_parser().parse_args()
        load_env_file(Path(args.env_file))
        output = extract(args)
        print(output)
        return 0
    except (ValueError, RuntimeError, KeyError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
