param(
    [ValidateRange(1, 20)]
    [int] $Runs = 5,

    [ValidateRange(40, 1200)]
    [int] $WarmupTicks = 200,

    [ValidateRange(200, 1200)]
    [int] $SampleTicks = 1200,

    [string] $Commit = "",

    [string] $OutputDirectory = ""
)

$ErrorActionPreference = "Stop"
if ($WarmupTicks + $SampleTicks -gt 1420) {
    throw "WarmupTicks + SampleTicks must not exceed the 1420-tick GameTest sampling budget"
}
$projectDirectory = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $projectDirectory "gradlew.bat"
$sourceDirectory = Join-Path $projectDirectory `
    "run-wireless-io-gametest\benchmark-reports\wireless-interface-io"

if ([string]::IsNullOrWhiteSpace($Commit)) {
    $Commit = (& git -C $projectDirectory rev-parse --short=12 HEAD).Trim()
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

    $scenario = "gametest-$Kind-1024x27-run$Run"
    $before = Get-Date
    $arguments = @(
        "runWirelessIoGameTestServer",
        "-Pae2ltBenchmarkScenario=$scenario",
        "-Pae2ltBenchmarkCommit=$Commit",
        "-Pae2ltBenchmarkWarmupTicks=$WarmupTicks",
        "-Pae2ltBenchmarkSampleTicks=$SampleTicks",
        "--no-daemon"
    )
    if ($Kind -eq "control") {
        $arguments += "-Pae2ltBenchmarkControl=true"
    }

    Write-Host "[$Kind $Run/$Runs] starting a fresh GameTestServer JVM"
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
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath `
    (Join-Path $OutputDirectory "manifest.json") -Encoding UTF8

Write-Host "Completed $Runs control/stress pairs. Reports: $OutputDirectory"
