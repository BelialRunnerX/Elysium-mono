#!/bin/bash
# Install the built jars on both test instances and restart the dedicated server.
#
#     bash tools/livetest/install.sh [--smoke]
#
# Copies dist/elysium-all-1.0.0.jar and dist/elysiumcolonies-1.0.0.jar into the
# mods folder of the dedicated server and of the dev client under
# ELYSIUM_TEST_ROOT (default C:/testingbuilds: ElysiumServer and Elysium). A jar
# already there is retired to mods/_superseded/<name>-<timestamp>.jar, never
# deleted. The server is stopped over RCON first (and the client closed, so it
# does not hold the jar being replaced), started again minimised, and waited
# for until its log says "Done (". --smoke then runs the harvest and the ruin
# scenarios of tools/livetest/livetest.py against it.
#
# Run it from anywhere; it finds the repository from its own location. Build
# first (./compile.sh --jars --bundle); never run it beside a compile or a
# negtest.
#
# It came back from the session transcript on 14 October 2026: it was the
# scratchpad's obtain/install_and_live.sh (written 29 September 15:51 UTC),
# lost when Windows emptied %TEMP% on 11 October, and is generalised here from
# the lost E: instances to ELYSIUM_TEST_ROOT and the repository's own dist.
set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
TEST_ROOT="${ELYSIUM_TEST_ROOT:-C:/testingbuilds}"
TEST_ROOT="${TEST_ROOT%/}"
SERVER="$TEST_ROOT/ElysiumServer"
CLIENT="$TEST_ROOT/Elysium"
JARS="elysium-all-1.0.0.jar elysiumcolonies-1.0.0.jar"
PW="${ELYSIUM_RCON:-elysiumtest}"

SMOKE=0
for arg in "$@"; do
  case "$arg" in
    --smoke) SMOKE=1 ;;
    -h|--help) sed -n '2,17p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown argument: $arg (only --smoke)" >&2; exit 2 ;;
  esac
done

PYDIR="/c/Users/weaks/AppData/Local/Programs/Python/Python312"
[ -d "$PYDIR" ] && export PATH="$PYDIR:$PATH"
export PYTHONIOENCODING=utf-8
cd "$ROOT" || exit 1

for j in $JARS; do
  [ -f "dist/$j" ] || { echo "dist/$j is missing: build first (./compile.sh --jars --bundle)" >&2; exit 1; }
done
[ -d "$SERVER" ] || { echo "no dedicated server at $SERVER (ELYSIUM_TEST_ROOT=$TEST_ROOT)" >&2; exit 1; }

server_up() {
  powershell -NoProfile -Command "if (Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*user_jvm_args*' }) { exit 0 } else { exit 1 }"
}

if server_up; then
  python3 "$HERE/rcon.py" stop 2>&1 | tail -1
  for i in $(seq 1 30); do
    server_up || break
    sleep 2
  done
  if server_up; then
    echo "the dedicated server is still running after a minute; stop it and run this again" >&2
    exit 1
  fi
fi
# the client too, if it is up: it must not hold the jar we replace
powershell -NoProfile -Command "Get-Process java -ErrorAction SilentlyContinue | Where-Object { \$_.MainWindowTitle -like 'Minecraft*' } | Stop-Process -Force" 2>/dev/null
sleep 3

stamp=$(date +%Y%m%d-%H%M%S)
for inst in "$SERVER" "$CLIENT"; do
  if [ ! -d "$inst" ]; then
    echo "no instance at $inst: skipped"
    continue
  fi
  mkdir -p "$inst/mods/_superseded"
  for j in $JARS; do
    [ -f "$inst/mods/$j" ] && mv "$inst/mods/$j" "$inst/mods/_superseded/${j%.jar}-$stamp.jar"
    cp "dist/$j" "$inst/mods/$j"
  done
  if cmp -s "dist/elysium-all-1.0.0.jar" "$inst/mods/elysium-all-1.0.0.jar"; then
    echo "$inst: on the dist jar ($(ls -la --time-style=+%H:%M dist/elysium-all-1.0.0.jar | awk '{print $5, $6}'))"
  else
    echo "$inst: the jar in mods is not the dist jar" >&2
    exit 1
  fi
done

SERVER_W="$(cygpath -w "$SERVER")"
started=$(date +%s)
powershell -NoProfile -Command "Start-Process -FilePath '$SERVER_W\\run.bat' -ArgumentList 'nogui' -WorkingDirectory '$SERVER_W' -WindowStyle Minimized"
sleep 8
L="$SERVER/logs/latest.log"
# log4j rolls the old latest.log away at start; until it has, the old one still says "Done (".
fresh() { [ -f "$L" ] && [ "$(stat -c %Y "$L")" -ge "$started" ]; }
for i in $(seq 1 90); do
  fresh && grep -q 'Done (' "$L" 2>/dev/null && break
  sleep 5
done
if ! { fresh && grep -q 'Done (' "$L"; }; then
  echo "the server did not say \"Done (\" within eight minutes; see $L" >&2
  exit 1
fi
grep 'Done (' "$L" | cut -c1-110
echo "errors at boot: $(grep -c '/ERROR\]' "$L")"
grep '/ERROR\]' "$L" | grep -v 'jei' | head -5 | cut -c1-220

if [ "$SMOKE" = 1 ]; then
  sleep 60   # dimensions still loading in the first minute make spurious failures
  python3 tools/livetest/livetest.py --password "$PW" --only harvest 2>&1 | tail -6
  python3 tools/livetest/livetest.py --password "$PW" --only "ruin" 2>&1 | tail -4
fi
echo "install done"
