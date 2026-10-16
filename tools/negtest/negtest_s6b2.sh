#!/bin/bash
cd /tmp/Elysium-mono || exit 1
negtest_lock() { if command -v flock >/dev/null 2>&1; then flock -n "$1"; else mkdir "$2" 2>/dev/null && trap "rmdir '$2' 2>/dev/null" EXIT; fi; }  # Git Bash on Windows has no flock
exec 9>/tmp/negtest_s6b2.lock; negtest_lock 9 /tmp/negtest_s6b2.lockdir || { echo busy; exit 2; }
J=elysium-space/src/main/java/com/elysium/space
MR=$J/world/MachineRules.java; PJ=$J/machine/PumpjackBlockEntity.java; FL=$J/registry/SpaceFluids.java
LT=elysium-space/src/main/resources/data/elysiumspace/loot_table/blocks/fluid_tank.json
FILES="$MR $PJ $FL $LT"
pass=0; miss=0
snap(){ rm -rf /tmp/_s62; mkdir -p /tmp/_s62; i=0; for f in $FILES; do cp "$f" /tmp/_s62/$i; i=$((i+1)); done; }
restore(){ i=0; for f in $FILES; do cp /tmp/_s62/$i "$f"; i=$((i+1)); done; }
python3 elysium-space/validate.py >/dev/null 2>&1 || { echo "SETUP FAILED"; exit 2; }; echo "baseline clean"
try(){ snap; if ! python3 -c "$3"; then echo "SETUP FAILED: $1"; miss=$((miss+1)); restore; return; fi
  if python3 elysium-space/validate.py 2>&1 | grep -qF "$2"; then echo "CAUGHT : $1"; pass=$((pass+1)); else echo "MISSED : $1 ($2)"; miss=$((miss+1)); fi; restore; }
R='
import sys, json
def rep(p, a, b):
    s=open(p).read()
    if a not in s: sys.exit("anchor: "+a[:50])
    open(p,"w").write(s.replace(a,b,1))
'
try "pump without power" "WorldHarness failed" "$R
rep('$MR', 'if (energy < PUMP_FE_PER_TICK) {', 'if (false) {')"
try "deposit random each time" "WorldHarness failed" "$R
rep('$MR', 'long h = seed ^', 'long h = System.nanoTime() ^ seed ^')"
try "every chunk has oil" "WorldHarness failed" "$R
rep('$MR', 'if ((h & 1L) == 0L) {', 'if (false) {')"
try "diesel ignored" "WorldHarness failed" "$R
rep('$MR', 'dieselSpace / DIESEL_PER_BATCH), energy', '99999), energy')"
try "pumpjack own rule" "no longer calls MachineRules.pumped(" "$R
rep('$PJ', 'int taken = MachineRules.pumped(deposit, oil.getSpace(), energy.getEnergyStored());', 'int taken = 10;')"
try "tank loses fluid" "does not copy its contents" "$R
d=json.load(open('$LT')); d['pools'][0]['entries'][0].pop('functions'); json.dump(d,open('$LT','w'))"
try "diesel fluid gone" "expected Stellaris's five fluids" "$R
rep('$FL', 'public static final Kind DIESEL = new Kind(\"diesel\"', 'public static final Kind DIESEL = new Kind(\"gasoline\"')"
echo "caught $pass, missed $miss"
[ "$miss" -eq 0 ]
