#!/usr/bin/env python3
"""Discover and download recent unique public FFLogs kill samples.

Report codes and player identities stay inside the git-ignored output tree.
The command prints only progress counts; use ``fflogs_plan_damage_calibration.py``
to produce an anonymous, reviewable calibration artifact.
"""

from __future__ import annotations

import argparse
import os
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

import fflogs_extract


REPORTS_QUERY = """
query VedaAxisRecentReports($zoneId: Int!, $limit: Int!) {
  reportData {
    reports(zoneID: $zoneId, limit: $limit) {
      data {
        code
        startTime
        endTime
        fights { id encounterID kill startTime endTime }
      }
    }
  }
}
"""


@dataclass(frozen=True)
class KillSample:
    report_code: str
    fight_id: int
    absolute_start_ms: int


def select_unique_kills(
        reports: Iterable[dict[str, Any]], encounter_id: int, sample_limit: int) -> list[KillSample]:
    candidates: list[KillSample] = []
    for report in reports:
        report_start = float(report.get("startTime") or 0)
        report_code = report.get("code")
        if not isinstance(report_code, str) or not report_code:
            continue
        for fight in report.get("fights") or []:
            if fight.get("encounterID") != encounter_id or fight.get("kill") is not True:
                continue
            fight_id = fight.get("id")
            if not isinstance(fight_id, int):
                continue
            candidates.append(KillSample(
                report_code=report_code,
                fight_id=fight_id,
                absolute_start_ms=round(report_start + float(fight.get("startTime") or 0)),
            ))

    selected: list[KillSample] = []
    seen_starts: set[int] = set()
    for candidate in sorted(candidates, key=lambda item: item.absolute_start_ms, reverse=True):
        if candidate.absolute_start_ms in seen_starts:
            continue
        seen_starts.add(candidate.absolute_start_ms)
        selected.append(candidate)
        if len(selected) >= sample_limit:
            break
    return selected


def collect(args: argparse.Namespace) -> int:
    fflogs_extract.load_env_file(Path(args.env_file))
    client_id = os.environ.get("FFLOGS_CLIENT_ID", "")
    client_secret = os.environ.get("FFLOGS_CLIENT_SECRET", "")
    if not client_id or not client_secret:
        raise RuntimeError("set FFLOGS_CLIENT_ID and FFLOGS_CLIENT_SECRET in the process environment")

    token = fflogs_extract.oauth_token(args.token_url, client_id, client_secret)
    result = fflogs_extract.graphql(
        args.api_url, token, REPORTS_QUERY,
        {"zoneId": args.zone_id, "limit": args.report_limit},
    )
    reports = result["data"]["reportData"]["reports"]["data"]
    samples = select_unique_kills(reports, args.encounter_id, args.sample_limit)
    if len(samples) < args.minimum_samples:
        raise RuntimeError(
            f"only {len(samples)} unique public kills found; {args.minimum_samples} required"
        )

    for index, sample in enumerate(samples, 1):
        extract_args = argparse.Namespace(
            report=sample.report_code,
            fight_id=sample.fight_id,
            output_dir=args.output_dir,
            token_url=args.token_url,
            api_url=args.api_url,
            access_token=token,
        )
        fflogs_extract.extract(extract_args)
        print(f"sample {index}/{len(samples)} downloaded")
    print(f"complete: {len(samples)} unique public kill samples")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zone-id", required=True, type=int)
    parser.add_argument("--encounter-id", required=True, type=int)
    parser.add_argument("--sample-limit", type=int, default=6)
    parser.add_argument("--minimum-samples", type=int, default=3)
    parser.add_argument("--report-limit", type=int, default=50, choices=range(1, 51), metavar="1..50")
    parser.add_argument("--output-dir", default="data/fflogs-poc")
    parser.add_argument("--env-file", default=".env")
    parser.add_argument("--token-url", default=fflogs_extract.DEFAULT_TOKEN_URL)
    parser.add_argument("--api-url", default=fflogs_extract.DEFAULT_API_URL)
    return parser


def main() -> int:
    try:
        return collect(build_parser().parse_args())
    except (ValueError, RuntimeError, KeyError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
