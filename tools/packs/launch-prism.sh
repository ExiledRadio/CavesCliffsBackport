#!/bin/bash
# launch-prism.sh <instance id> <instance dir>: launches a Prism test copy, waits for the client test's DONE line, a
# crash or exit, then stops only that instance's javaw (matched on its own directory), by PID.
id=$1; D=$2/minecraft; L=$D/logs/latest.log
pid=$(powershell -NoProfile -c "Get-CimInstance Win32_Process -Filter \"Name='javaw.exe'\" | Where-Object { \$_.CommandLine -like '*$id*' } | Select-Object -ExpandProperty ProcessId")
[ -n "$pid" ] && { echo "$id already running ($pid)"; exit 2; }
free=$(powershell -NoProfile -c "[math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1MB,1)")
echo "free RAM $free GB"; awk "BEGIN{exit !($free < 8)}" && { echo "under 8 GB free"; exit 2; }
rm -f $L $D/selftest-client-report.txt
before=$(ls $D/crash-reports 2>/dev/null | wc -l)
"/c/Users/Radio/AppData/Local/Programs/PrismLauncher/prismlauncher.exe" --launch "$id" >/dev/null 2>&1 &
sleep 40
for i in $(seq 1 200); do
  now=$(ls $D/crash-reports 2>/dev/null | wc -l)
  [ "$now" != "$before" ] && { echo "CRASH: $(ls -t $D/crash-reports | head -1)"; break; }
  grep -q 'CLIENT SELFTEST DONE' "$L" 2>/dev/null && { echo "DONE"; break; }
  pid=$(powershell -NoProfile -c "Get-CimInstance Win32_Process -Filter \"Name='javaw.exe'\" | Where-Object { \$_.CommandLine -like '*$id*' } | Select-Object -ExpandProperty ProcessId")
  [ $i -gt 5 ] && [ -z "$pid" ] && { echo "javaw exited"; break; }
  sleep 8
done
for i in $(seq 1 12); do
  pid=$(powershell -NoProfile -c "Get-CimInstance Win32_Process -Filter \"Name='javaw.exe'\" | Where-Object { \$_.CommandLine -like '*$id*' } | Select-Object -ExpandProperty ProcessId")
  [ -z "$pid" ] && break
  [ $i -eq 12 ] && taskkill //PID $pid //F >/dev/null && echo "killed lingering client pid $pid"
  sleep 5
done
grep -E "Extended height|CLIENT SELFTEST" "$L" | cut -c1-260
cat $D/selftest-client-report.txt 2>/dev/null
