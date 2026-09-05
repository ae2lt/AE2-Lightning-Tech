param(
    [ValidateRange(1, 20)]
    [int] $Runs = 5,

    [ValidateRange(40, 1200)]
    [int] $WarmupTicks = 200,

    [ValidateRange(200, 1200)]
    [int] $SampleTicks = 1200,

    [ValidateSet(
        "1024x27",
        "high-cardinality-reject",
        "equal-load-recovery",
        "equal-load-partial-recovery",
        "equal-load-sustained")]
    [string] $Profile = "1024x27",

    [string] $Commit = "",

    [string] $OutputDirectory = "",

    [switch] $Diagnostics
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot 'wireless-io-benchmark-common.ps1')
if ($WarmupTicks + $SampleTicks -gt 1420) {
    throw "WarmupTicks + SampleTicks must not exceed the 1420-tick GameTest sampling budget"
}
$projectDirectory = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $projectDirectory "gradlew.bat"
$sourceDirectory = Join-Path $projectDirectory `
    "run-wireless-io-gametest\benchmark-reports\wireless-interface-io"

$identity = Get-WirelessIoGitIdentity -Directory $projectDirectory
$gitHead = $identity.Head
$gitDirty = $identity.Dirty
if ([string]::IsNullOrWhiteSpace($Commit)) {
    $Commit = $gitHead.Substring(0, 12)
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $projectDirectory `
        "benchmark-results\wireless-io-gametest-$Commit"
} elseif (-not [IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory = Join-Path $projectDirectory $OutputDirectory
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Invoke-BenchmarkRun {
    param(
        [Parameter(Mandatory)] [ValidateSet("control", "stress")] [string] $Kind,
        [Parameter(Mandatory)] [int] $Run
    )

    $scenario = "gametest-$Kind-$Profile-run$Run"
    $before = Get-Date
    $arguments = @(
        "runWirelessIoGameTestServer",
        "-Pae2ltBenchmarkScenario=$scenario",
        "-Pae2ltBenchmarkCommit=$Commit",
        "-Pae2ltBenchmarkWarmupTicks=$WarmupTicks",
        "-Pae2ltBenchmarkSampleTicks=$SampleTicks",
        "-Pae2ltBenchmarkDiagnostics=$($Diagnostics.IsPresent.ToString().ToLowerInvariant())",
        "-Pae2ltBenchmarkGitHead=$gitHead",
        "-Pae2ltBenchmarkWorktreeDirty=$($gitDirty.ToString().ToLowerInvariant())",
        "--no-daemon"
    )
    if ($Kind -eq "control") {
        $arguments += "-Pae2ltBenchmarkControl=true"
    }

    Write-Host "[$Profile $Kind $Run/$Runs] starting a fresh GameTestServer JVM"
    & $gradle @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "GameTestServer failed for $scenario"
    }

    $json = Get-ChildItem -LiteralPath $sourceDirectory -Filter "*$scenario.json" |
        Where-Object LastWriteTime -ge $before |
        Sort-Object LastWriteTime |
        Select-Object -Last 1
    if ($null -eq $json) {
        throw "No JSON report was generated for $scenario"
    }

    $csv = Join-Path $json.DirectoryName ($json.BaseName + "-ticks.csv")
    if (-not (Test-Path -LiteralPath $csv -PathType Leaf)) {
        throw "No tick CSV was generated for $scenario"
    }

    $destinationJson = Join-Path $OutputDirectory "$Kind-run$Run.json"
    $destinationCsv = Join-Path $OutputDirectory "$Kind-run$Run-ticks.csv"
    Copy-Item -LiteralPath $json.FullName -Destination $destinationJson
    Copy-Item -LiteralPath $csv -Destination $destinationCsv
}

for ($run = 1; $run -le $Runs; $run++) {
    Invoke-BenchmarkRun -Kind control -Run $run
    Invoke-BenchmarkRun -Kind stress -Run $run
}

$manifest = [ordered]@{
    schema = 1
    commit = $Commit
    runs = $Runs
    warmupTicks = $WarmupTicks
    sampleTicks = $SampleTicks
    profile = $Profile
    comparisonKind = $(if ($Profile.StartsWith('equal-load-')) { 'repeated-load' } else { 'idle-control' })
    diagnostics = $Diagnostics.IsPresent
    gitHead = $gitHead
    workingTreeDirty = $gitDirty
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath `
    (Join-Path $OutputDirectory "manifest.json") -Encoding UTF8

Write-Host "Completed $Runs control/stress pairs. Reports: $OutputDirectory"
