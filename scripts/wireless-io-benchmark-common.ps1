function Get-WirelessIoGitIdentity {
    param([Parameter(Mandatory)] [string] $Directory)
    $headOutput = @(& git -C $Directory rev-parse HEAD 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Cannot read benchmark Git HEAD: $headOutput" }
    $head = ($headOutput -join "`n").Trim()
    if ($head -notmatch '^[0-9a-f]{40,64}$') { throw "Invalid benchmark Git HEAD: $head" }
    $status = @(& git -C $Directory status --porcelain 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Cannot read benchmark Git status: $status" }
    [pscustomobject]@{ Head = $head; Dirty = $status.Count -gt 0 }
}
