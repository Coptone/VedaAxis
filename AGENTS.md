# VedaAxis repository instructions

## Product boundary

VedaAxis is an FFXIV mitigation-planning, personal reminder, and execution-review system. It must never cast actions, simulate input, or mutate the native hotbar. The Dalamud plugin may only observe game state and draw a read-only overlay.

The repository intentionally has no open-source license. Do not add one unless the owner explicitly changes that decision.

## Source of truth

- Product requirements: `FFXIV_Mitigation_Planner_PRD_v0.1.docx` supplied by the owner; it is not committed to this repository.
- Curated handoff context for every new local or cloud Codex task: `docs/CODEX_CONTEXT.md`. Read it before planning work.
- Verified project status and current blockers: `docs/PROGRESS.md`.
- Architecture and product decisions: `docs/architecture.md` and `docs/decisions.md`.
- PoC evidence and acceptance boundaries: `docs/poc/fflogs.md` and `docs/poc/dalamud.md`.

Do not mark a PoC or acceptance item complete merely because code compiles. Update `docs/PROGRESS.md` only when the repository contains or links to reproducible evidence.

## Repository map

- `apps/web`: Vue 3 + TypeScript plan editor, sharing, account, and device authorization UI.
- `services/api`: Java 21 + Spring Boot + MyBatis modular monolith.
- `plugins/VedaAxis.Core`: game-independent runtime and state machine.
- `plugins/VedaAxis`: Dalamud SDK 15 / .NET 10 Windows plugin.
- `contracts`: versioned cross-language JSON schemas.
- `data/seeds`: encounter/timeline seed data with source and confidence status.
- `tools`: FFLogs extraction/verification and Codex cloud setup helpers.

## Setup

For Codex cloud, configure the environment setup command as:

```bash
bash tools/codex_setup.sh
```

For local Windows development, copy `.env.example` to `.env` and keep all credentials local. Never commit FFLogs, DeepSeek, JWT, database, or OAuth secrets. Generated FFLogs samples under `data/fflogs-poc/` are intentionally ignored.

## Required checks

Run the checks relevant to the changed area. Before changing shared contracts, plan semantics, release artifacts, or the default branch, run all of them.

```bash
pnpm install --frozen-lockfile
pnpm check:web
pnpm test:web
pnpm build:web

cd services/api && mvn test

python -m unittest discover -s tools/tests -v

dotnet test plugins/VedaAxis.Core.Tests/VedaAxis.Core.Tests.csproj -c Release
dotnet build plugins/VedaAxis/VedaAxis.csproj -c Release
```

The full plugin build needs `DALAMUD_HOME` to point to an extracted official Dalamud development distribution. Core tests do not require the game client.

## Implementation rules

- Preserve the 4-track and 8-track modes; new plan logic must not hard-code only one layout.
- Keep plugin combat execution based on a local immutable snapshot. Network calls must not sit on the combat/render path.
- Treat AI output as an untrusted candidate. Validate it against schemas and rules, show a diff, and require explicit confirmation before publication or activation.
- Keep account/API concerns outside the battle state machine where possible.
- Add or update versioned JSON schemas before changing shared Java/TypeScript/C# payloads.
- Do not change `POC_PENDING` source confidence to `VERIFIED` without the evidence required by the corresponding PoC document.

## Release synchronization

GitHub `main` is the source of truth and Gitee is the domestic mirror. A plugin release requires all of the following:

1. Update the version in `plugins/VedaAxis/VedaAxis.csproj` and `pluginmaster.json`.
2. Build Release with the official Dalamud development files.
3. Replace `repository/VedaAxis.zip` with the verified package.
4. Update the changelog and verify the package SHA-256.
5. Commit, push GitHub, create/push the matching `vX.Y.Z` tag, then force-sync Gitee.
6. Verify `pluginmaster.json` and the ZIP return HTTP 200 from the configured public HTTPS repository URLs before asking users to update.

Do not publish credentials, local plan files, diagnostic logs containing character/account identifiers, or ignored PoC raw data.
