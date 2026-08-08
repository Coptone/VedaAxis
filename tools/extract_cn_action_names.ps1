param(
    [Parameter(Mandatory = $true)]
    [string]$SqpackPath,

    [string[]]$IdsFile = @(),

    [string]$OutDir = "artifacts\cn-action-names",

    [switch]$NoDefaultDmu,

    [string]$Language = "ChineseSimplified",

    [string]$DotnetPath = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$toolProject = Join-Path $PSScriptRoot "ffxiv_cn_action_extract\VedaAxis.FfxivCnActionExtract.csproj"

function Test-DirectFfxivDataFolder {
    param([string]$Path)
    return (Test-Path -LiteralPath (Join-Path $Path "0a0000.win32.index")) -and
           (Test-Path -LiteralPath (Join-Path $Path "0a0000.win32.dat0"))
}

function Get-DirectoryHashSlug {
    param([string]$Path)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes((Resolve-Path -LiteralPath $Path).Path.ToLowerInvariant())
        return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace("-", "").Substring(0, 12).ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

$dotnetCandidates = New-Object System.Collections.Generic.List[string]
if ($DotnetPath) {
    $dotnetCandidates.Add($DotnetPath)
}
if ($env:DOTNET_EXE) {
    $dotnetCandidates.Add($env:DOTNET_EXE)
}
$userProfilePath = [Environment]::GetFolderPath("UserProfile")
if ($userProfilePath) {
    $dotnetCandidates.Add((Join-Path $userProfilePath ".cache\codex-runtimes\dotnet-sdk-10\dotnet.exe"))
}
$dotnetCommand = Get-Command dotnet -ErrorAction SilentlyContinue
if ($dotnetCommand) {
    $dotnetCandidates.Add($dotnetCommand.Source)
}

$resolvedDotnet = $null
foreach ($candidate in $dotnetCandidates) {
    if ($candidate -and (Test-Path -LiteralPath $candidate)) {
        $resolvedDotnet = $candidate
        break
    }
}

if (-not $resolvedDotnet) {
    throw "未找到可用的 dotnet SDK。可用 -DotnetPath 指定 dotnet.exe，例如 C:\...\dotnet.exe"
}

$resolvedSqpackInput = (Resolve-Path -LiteralPath $SqpackPath -ErrorAction Stop).Path
if (Test-DirectFfxivDataFolder -Path $resolvedSqpackInput) {
    $slug = Get-DirectoryHashSlug -Path $resolvedSqpackInput
    $wrapper = Join-Path $repoRoot ".tmp\vedaaxis-cn-game-$slug\sqpack"
    New-Item -ItemType Directory -Force -Path $wrapper | Out-Null
    $link = Join-Path $wrapper "ffxiv"
    if (-not (Test-Path -LiteralPath $link)) {
        try {
            New-Item -ItemType Junction -Path $link -Target $resolvedSqpackInput | Out-Null
        } catch {
            throw "检测到你传入的是直接包含 0a0000.win32.* 的目录，但创建临时 ffxiv 目录链接失败：$($_.Exception.Message)"
        }
    }
    $SqpackPath = $wrapper
}

$runArgs = @(
    "run",
    "--project", $toolProject,
    "--configuration", "Release",
    "--",
    "--sqpack", $SqpackPath,
    "--out", $OutDir,
    "--language", $Language
)

if (-not $NoDefaultDmu) {
    $defaultFiles = @(
        (Join-Path $repoRoot "data\seeds\dmu\p1-p2-damage-map.json"),
        (Join-Path $PSScriptRoot "ffxiv_cn_action_extract\dmu_p3_p5_action_ids.csv")
    )
    foreach ($file in $defaultFiles) {
        if (Test-Path -LiteralPath $file) {
            $runArgs += @("--ids-file", $file)
        }
    }
}

foreach ($file in $IdsFile) {
    $runArgs += @("--ids-file", $file)
}

Push-Location $repoRoot
try {
    & $resolvedDotnet @runArgs
} finally {
    Pop-Location
}
