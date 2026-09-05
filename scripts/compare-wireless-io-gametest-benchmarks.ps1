param(
    [Parameter(Mandatory)]
    [string] $BaselineDirectory,

    [Parameter(Mandatory)]
    [string] $CandidateDirectory,

    [string] $OutputDirectory = ""
)

$ErrorActionPreference = "Stop"
$projectDirectory = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $projectDirectory "gradlew.bat"

function Resolve-ReportDirectory([string] $Path) {
    if (-not [IO.Path]::IsPathRooted($Path)) {
        $Path = Join-Path $projectDirectory $Path
    }
    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction Stop
    return $resolved.Path
}

function Get-Reports([string] $Directory, [string] $Kind) {
    $reports = Get-ChildItem -LiteralPath $Directory -Filter "$Kind-run*.json" |
        Sort-Object Name
    if ($reports.Count -ne 5) {
        throw "Expected exactly 5 $Kind reports in $Directory, got $($reports.Count)"
    }
    return ($reports.FullName -join ";")
}

$baseline = Resolve-ReportDirectory $BaselineDirectory
$candidate = Resolve-ReportDirectory $CandidateDirectory

$arguments = @(
    "checkWirelessIoBenchmarkRegression",
    "-Pae2ltBenchmarkBaselineStressReports=$(Get-Reports $baseline 'stress')",
    "-Pae2ltBenchmarkBaselineControlReports=$(Get-Reports $baseline 'control')",
    "-Pae2ltBenchmarkCandidateStressReports=$(Get-Reports $candidate 'stress')",
    "-Pae2ltBenchmarkCandidateControlReports=$(Get-Reports $candidate 'control')",
    "--no-daemon"
)
if (-not [string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $arguments += "-Pae2ltBenchmarkLiveComparisonDir=$OutputDirectory"
}

& $gradle @arguments
exit $LASTEXITCODE
