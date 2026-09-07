$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'wireless-io-benchmark-common.ps1')
$project = Split-Path -Parent $PSScriptRoot
$testRoot = Join-Path $project ('build/benchmark-tool-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$repo = Join-Path $testRoot 'repo'
& git init --quiet $repo
if ($LASTEXITCODE -ne 0) { throw 'test git init failed' }
& git -C $repo -c user.name=BenchmarkTest -c user.email=benchmark@example.invalid commit --quiet --allow-empty -m initial
if ($LASTEXITCODE -ne 0) { throw 'test git commit failed' }
if ((Get-WirelessIoGitIdentity $repo).Dirty) { throw 'clean repo reported dirty' }
Set-Content -LiteralPath (Join-Path $repo 'one.txt') -Value one
if (-not (Get-WirelessIoGitIdentity $repo).Dirty) { throw 'single file not reported dirty' }
Set-Content -LiteralPath (Join-Path $repo 'two.txt') -Value two
if (-not (Get-WirelessIoGitIdentity $repo).Dirty) { throw 'multiple files not reported dirty' }
$failed = $false
try { Get-WirelessIoGitIdentity (Join-Path $testRoot 'missing') | Out-Null } catch { $failed = $true }
if (-not $failed) { throw 'Git failure was accepted as clean' }

$baseline = Join-Path $testRoot 'baseline'
$candidate = Join-Path $testRoot 'candidate'
New-Item -ItemType Directory -Path $baseline, $candidate | Out-Null
foreach ($directory in @($baseline, $candidate)) {
    foreach ($role in @('control', 'stress')) {
        foreach ($run in 1..5) {
            # Control labels deliberately have different P99s. They are loaded
            # repeats, so no meaningless difference-of-P99s may gate the result.
            $p99 = if ($role -eq 'control') { 8.0 } else { 10.0 }
            $report = [ordered]@{
                scenario = "gametest-$role-equal-load-sustained-run$run"
                partial = $false; samples = 1200; warmupTicks = 200
                javaVersion = 'test'; serverVersion = 'test'
                interfaceCalls = 1200; fastInterfaceCalls = 1200
                tickMs = @{ mean = 1.0; p95 = 5.0; p99 = $p99 }
                wirelessIoMs = @{ p99 = 2.0 }; ticksOver50MsRatio = 0.0
                capacityTps = 20.0; gcMillis = 10; peakUsedHeapBytes = 1000000
                workloadObservationMode = 'formal_aggregate_only'
                workloadPlannedProductionItems = 100; workloadActualProductionItems = 100
                workloadProducedItems = 100; workloadExtractedItems = 100
                workloadNetworkImportedItems = 100; workloadRecoveryTick = -1
                workloadFinalRemainingItems = 0; workloadBufferedItems = 0
                workloadBlockedProductionEvents = 0
                workloadMinimumWindowThroughput = 1.0; workloadMinimumTargetThroughput = 1.0
            }
            $report | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $directory "$role-run$run.json")
        }
    }
}
function Invoke-ComparisonTest([string] $Name, [bool] $ExpectSuccess, [double] $MaxIoP99Ms = 12.0) {
    $log = Join-Path $testRoot "$Name.log"
    # Absolute script invocation must select its own project even when the
    # caller is in a different directory (as in alternating worktree runs).
    Push-Location $testRoot
    try {
        & (Join-Path $PSScriptRoot 'compare-wireless-io-gametest-benchmarks.ps1') `
            -BaselineDirectory $baseline -CandidateDirectory $candidate -OutputDirectory (Join-Path $testRoot 'comparison') `
            -MaxIoP99Ms $MaxIoP99Ms *> $log
        if (($LASTEXITCODE -eq 0) -ne $ExpectSuccess) { throw "Unexpected $Name result; see $log" }
    } finally {
        Pop-Location
    }
}
Invoke-ComparisonTest 'repeated-load' $true
$comparison = Get-Content (Join-Path $testRoot 'comparison/live-comparison.md') -Raw
if ($comparison -notmatch '\| p99 \| 9.0 \| 9.0 \|' -or $comparison -notmatch '\| adjustedP99 \| N/A \| N/A \|') {
    throw 'loaded repetitions were not summarized as ten runs without subtraction'
}
$path = Join-Path $candidate 'stress-run1.json'
$original = Get-Content $path -Raw
foreach ($case in @('unequal-work', 'diagnostic', 'mixed-profile', 'gc-window')) {
    $changed = $original | ConvertFrom-Json
    switch ($case) {
        'unequal-work' { $changed.workloadProducedItems = 99 }
        'diagnostic' { $changed.workloadObservationMode = 'diagnostic_target_key' }
        'mixed-profile' { $changed.scenario = 'gametest-stress-equal-load-recovery-run1' }
        'gc-window' { $changed | Add-Member gcMeasurementWindow 'sample-ticks-only' }
    }
    $changed | ConvertTo-Json -Depth 5 | Set-Content $path
    Invoke-ComparisonTest $case $false
    Set-Content $path $original
}
# Confirm raw tail regressions still fail without a control subtraction.
Get-ChildItem $candidate -Filter '*.json' | ForEach-Object {
    $r = Get-Content $_.FullName -Raw | ConvertFrom-Json
    $r.tickMs.p99 = 30.0
    $r | ConvertTo-Json -Depth 5 | Set-Content $_.FullName
}
Invoke-ComparisonTest 'raw-tail-regression' $false
# Existing idle-control profiles retain their original paired comparison.
foreach ($directory in @($baseline, $candidate)) {
    Get-ChildItem $directory -Filter '*.json' | ForEach-Object {
        $r = Get-Content $_.FullName -Raw | ConvertFrom-Json
        $r.scenario = $r.scenario.Replace('equal-load-sustained', '1024x27')
        $r.tickMs.p99 = if ($r.scenario.Contains('-control-')) { 0.5 } else { 10.0 }
        $r | ConvertTo-Json -Depth 5 | Set-Content $_.FullName
    }
}
Invoke-ComparisonTest 'idle-controls' $true

function Write-ExportReports([bool] $Idle) {
    $profile = if ($Idle) { 'export-empty-1024' } else { 'export-continuous-256x27' }
    $targets = if ($Idle) { 1024 } else { 256 }
    $keys = if ($Idle) { 0 } else { 27 }
    foreach ($directory in @($baseline, $candidate)) {
        foreach ($role in @('control', 'stress')) {
            foreach ($run in 1..5) {
                $consuming = (-not $Idle) -and $role -eq 'stress'
                $planned = if ($consuming) { $targets * 1360L } else { 0L }
                $starved = if ($consuming) { $targets * 2L } else { 0L }
                $completed = $planned - $starved
                $steady = if ($consuming) { $targets * 1320L } else { 0L }
                $supply = $targets * 1361L * $keys * 64L
                $remaining = $targets * $keys * 64L
                $consumed = $completed * $keys * 64L
                $report = [ordered]@{
                    schema = 4; scenario = "gametest-$role-$profile-run$run"
                    ioMeasurementScope = 'grid-item-io-including-eligibility'
                    comparisonKind = $(if ($Idle) { 'repeated-idle' } else { 'idle-control' })
                    gcMeasurementWindow = 'sample-ticks-only'
                    partial = $false; samples = 1200; warmupTicks = 200
                    javaVersion = 'test'; serverVersion = 'test'
                    interfaceCalls = 240; fastInterfaceCalls = 240
                    configuredConnectionVisits = 240L * $targets
                    tickMs = @{ mean = 1.0; p95 = 3.0; p99 = 4.0 }
                    wirelessIoMs = @{ mean = 0.5; p99 = 2.0 }; ticksOver50MsRatio = 0.0
                    capacityTps = 20.0; gcMillis = 10; peakUsedHeapBytes = 1000000
                    workloadObservationMode = 'formal_aggregate_only'
                    exportWorkload = @{
                        contract = 'consumer-start40-steady80-v1'; profile = $profile
                        targets = $targets; configuredKeys = $keys; itemKeys = $keys
                        unitsPerKey = 64; consuming = $consuming
                        suppliedItems = $supply; targetRemainingItems = $remaining; bufferedItems = 0
                        networkRemainingItems = $supply - $remaining - $consumed
                        plannedEvents = $planned; completedEvents = $completed; starvedEvents = $starved
                        maxStarveStreak = $(if ($consuming) { 2 } else { 0 })
                        plannedItems = $planned * $keys * 64L; consumedItems = $consumed
                        steadyPlannedEvents = $steady; steadyCompletedEvents = $steady
                        steadyStarvedEvents = 0; steadyMaxStarveStreak = 0
                        minimumWindowThroughput = $(if ($consuming) { 1.0 } else { -1.0 })
                        minimumTargetThroughput = $(if ($consuming) { 1.0 } else { -1.0 })
                        startupWaitTicks = $(if ($Idle) { -1 } else { 2 })
                    }
                }
                $report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $directory "$role-run$run.json")
            }
        }
    }
}

Write-ExportReports $false
Invoke-ComparisonTest 'export-continuous' $true 4.0
Invoke-ComparisonTest 'export-size-budget' $false 1.5
$path = Join-Path $candidate 'stress-run1.json'
$original = Get-Content -LiteralPath $path -Raw
foreach ($case in @('export-mixed-scope', 'export-topology', 'export-ownership', 'export-starvation', 'export-contract')) {
    $changed = $original | ConvertFrom-Json
    switch ($case) {
        'export-mixed-scope' { $changed.ioMeasurementScope = 'wireless-io-body' }
        'export-topology' { $changed.exportWorkload.targets = 64 }
        'export-ownership' { $changed.exportWorkload.networkRemainingItems++ }
        'export-contract' { $changed.exportWorkload.contract = 'old-fixture' }
        'export-starvation' {
            $w = $changed.exportWorkload
            $w.completedEvents -= 1024; $w.starvedEvents += 1024; $w.maxStarveStreak = 4
            $w.steadyCompletedEvents -= 1024; $w.steadyStarvedEvents += 1024; $w.steadyMaxStarveStreak = 4
            $w.consumedItems -= 1024L * 27 * 64
            $w.networkRemainingItems += 1024L * 27 * 64
            $w.minimumWindowThroughput = 0.96
        }
    }
    $changed | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $path
    Invoke-ComparisonTest $case $false
    Set-Content -LiteralPath $path $original
}
Write-ExportReports $true
Invoke-ComparisonTest 'export-repeated-idle' $true
$comparison = Get-Content -LiteralPath (Join-Path $testRoot 'comparison/live-comparison.md') -Raw
if ($comparison -notmatch 'ten repeated idle runs' -or $comparison -notmatch '\| adjustedP99 \| N/A \| N/A \|') {
    throw 'empty export was treated as a loaded or control-subtracted benchmark'
}
Get-ChildItem -LiteralPath $candidate -Filter '*.json' | ForEach-Object {
    $r = Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json
    $r.tickMs.p99 = 30.0
    $r | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $_.FullName
}
Invoke-ComparisonTest 'export-idle-tail-regression' $false
Write-Host "Benchmark tooling tests passed. Artifacts: $testRoot"
exit 0
