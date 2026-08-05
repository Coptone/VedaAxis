#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

required_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "error: required command '$1' is not available" >&2
    exit 1
  fi
}

required_command java
required_command mvn
required_command node
required_command npm
required_command python
required_command curl
required_command unzip

java_major="$(java -version 2>&1 | awk -F '[\".]' '/version/ { print $2; exit }')"
node_version="$(node --version | sed -E 's/^v//')"
node_major="${node_version%%.*}"
node_minor="$(printf '%s' "$node_version" | cut -d. -f2)"

if [[ -z "$java_major" || "$java_major" -lt 21 ]]; then
  echo "error: Java 21 or newer is required" >&2
  exit 1
fi

if [[ -z "$node_major" || -z "$node_minor" || "$node_major" -lt 22 || ( "$node_major" -eq 22 && "$node_minor" -lt 13 ) ]]; then
  echo "error: Node.js 22.13 or newer is required" >&2
  exit 1
fi

mkdir -p "$HOME/.local/bin"
export PATH="$HOME/.local/bin:$PATH"
npm install --global --prefix "$HOME/.local" pnpm@11.9.0
pnpm install --frozen-lockfile

(
  cd services/api
  mvn -q -DskipTests dependency:go-offline
)

if ! command -v dotnet >/dev/null 2>&1 || [[ "$(dotnet --version 2>/dev/null || true)" != 10.* ]]; then
  dotnet_install="$HOME/.cache/vedaaxis/dotnet-install.sh"
  mkdir -p "$(dirname "$dotnet_install")" "$HOME/.dotnet"
  curl -fsSL https://dot.net/v1/dotnet-install.sh -o "$dotnet_install"
  bash "$dotnet_install" --channel 10.0 --install-dir "$HOME/.dotnet"
  ln -sfn "$HOME/.dotnet/dotnet" "$HOME/.local/bin/dotnet"
fi

dalamud_home="$HOME/.xlcore/dalamud/Hooks/dev"
dalamud_zip="$HOME/.cache/vedaaxis/dalamud-latest.zip"
mkdir -p "$dalamud_home" "$(dirname "$dalamud_zip")"
curl -fsSL https://goatcorp.github.io/dalamud-distrib/latest.zip -o "$dalamud_zip"
unzip -q -o "$dalamud_zip" -d "$dalamud_home"

export DALAMUD_HOME="$dalamud_home"
dotnet restore plugins/VedaAxis.Core.Tests/VedaAxis.Core.Tests.csproj
dotnet restore plugins/VedaAxis/VedaAxis.csproj

echo "VedaAxis Codex cloud environment is ready."
echo "DALAMUD_HOME=$dalamud_home"
