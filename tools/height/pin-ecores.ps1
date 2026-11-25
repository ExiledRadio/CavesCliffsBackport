# pin-ecores.ps1 [-Match ccrender] [-Minutes 240]: pins every javaw.exe whose command line contains -Match to the
# i9-14900K's E-cores (logical CPUs 16-31) as soon as it appears. The P-cores miscompute under load on this machine
# (JIT and driver-compiler access violations); E-cores run the same work cleanly. Logs each pin to stdout.
param([string]$Match = 'ccrender', [int]$Minutes = 240)
$mask = [IntPtr][long]4294901760
$done = @{}
$until = (Get-Date).AddMinutes($Minutes)
while ((Get-Date) -lt $until) {
    Get-CimInstance Win32_Process -Filter "Name='javaw.exe'" | Where-Object { $_.CommandLine -like "*$Match*" } | ForEach-Object {
        $id = $_.ProcessId
        if (-not $done.ContainsKey($id)) {
            try {
                $p = Get-Process -Id $id -ErrorAction Stop
                $p.ProcessorAffinity = $mask
                $done[$id] = $true
                $name = if ($_.CommandLine -match 'instances[\\/](ccrender-[a-z-]+)') { $matches[1] } else { '?' }
                Write-Output ("{0:HH:mm:ss} pinned {1} ({2}) to E-cores" -f (Get-Date), $id, $name)
            } catch { }
        }
    }
    Start-Sleep -Milliseconds 500
}
