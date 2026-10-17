#!/bin/bash
cd /tmp/Elysium-mono || exit 1
negtest_lock() { if command -v flock >/dev/null 2>&1; then flock -n "$1"; else mkdir "$2" 2>/dev/null && trap "rmdir '$2' 2>/dev/null" EXIT; fi; }  # Git Bash on Windows has no flock
exec 9>/tmp/negtest_machines.lock; negtest_lock 9 /tmp/negtest_machines.lockdir || { echo busy; exit 2; }
J=elysium-space/src/main/java/com/elysium/space
MR=$J/world/MachineRules.java; RF=$J/machine/FuelRefineryBlockEntity.java; OD=$J/block/OxygenDistributorBlockEntity.java
MT=$J/machine/MachineTanks.java; CAP=$J/registry/SpaceBlockEntities.java; RT=$J/world/SpaceRoute.java
LANG=elysium-space/src/main/resources/assets/elysiumspace/lang/en_us.json
FILES="$MR $RF $OD $MT $CAP $RT $LANG"
pass=0; miss=0
snap(){ rm -rf /tmp/_nm2; mkdir -p /tmp/_nm2; i=0; for f in $FILES; do cp "$f" /tmp/_nm2/$i; i=$((i+1)); done; }
restore(){ i=0; for f in $FILES; do cp /tmp/_nm2/$i "$f"; i=$((i+1)); done; }
python3 elysium-space/validate.py >/dev/null 2>&1 || { echo "SETUP FAILED"; exit 2; }; echo "baseline clean"
try(){ snap; if ! python3 -c "$3"; then echo "SETUP FAILED: $1"; miss=$((miss+1)); restore; return; fi
  if python3 elysium-space/validate.py 2>&1 | grep -qF "$2"; then echo "CAUGHT : $1"; pass=$((pass+1)); else echo "MISSED : $1 ($2)"; miss=$((miss+1)); fi; restore; }
R='
import sys, json
def rep(p, a, b):
    s=open(p, encoding="utf-8", newline="").read()
    if a not in s: sys.exit("anchor: "+a[:50])
    open(p,"w", encoding="utf-8", newline="").write(s.replace(a,b,1))
'
try "refinery recipe changed" "WorldHarness failed" "$R
rep('$MR', 'FUEL_PER_BATCH = 5;', 'FUEL_PER_BATCH = 10;')"
try "power ignored" "WorldHarness failed" "$R
rep('$MR', 'energy / REFINERY_FE_PER_BATCH));', '99999));')"
try "free air" "WorldHarness failed" "$R
rep('$MR', 'return Math.max(0, newBlocks) * OXYGEN_PER_NEW_BLOCK', 'return 0 * OXYGEN_PER_NEW_BLOCK')"
# The refinery makes diesel as well as fuel, so the rule's call also takes the
# diesel tank's space and wraps onto a second line; the anchor is that whole
# statement. The one-line form this case was written against predates the
# repository, so the case had never set up here (17 Oct 2026).
try "refinery bypasses rules" "no longer calls MachineRules.refineryBatches(" "$R
rep('$RF', '''if (MachineRules.refineryBatches(oil.getFluidAmount(), fuel.getSpace(), diesel.getSpace(),
                energy.getEnergyStored()) > 0) {''', 'if (oil.getFluidAmount() >= 10) {')"
try "distributor own fill" "no longer calls ElysiumRoomScan.interior(" "$R
rep('$OD', 'room.addAll(ElysiumRoomScan.interior(level, start, MAX_VOLUME));', 'room.add(start);')"
try "pipes fill outputs" "reaches past the input tanks" "$R
rep('$MT', '''    public int fill(FluidStack resource, FluidAction action) {
        for (FluidTank tank : inputs) {''', '''    public int fill(FluidStack resource, FluidAction action) {
        for (FluidTank tank : all) {''')"
try "pipes drain inputs" "reaches past the output tanks" "$R
rep('$MT', '''    public FluidStack drain(int maxDrain, FluidAction action) {
        for (FluidTank tank : outputs) {''', '''    public FluidStack drain(int maxDrain, FluidAction action) {
        for (FluidTank tank : all) {''')"
try "no energy capability" "Capabilities.EnergyStorage.BLOCK" "$R
rep('$CAP', 'event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> be.energy());', '')"
try "large tank too small" "WorldHarness failed" "$R
rep('$RT', 'LARGE_TANK_MB = 6000;', 'LARGE_TANK_MB = 4000;')"
try "fluid unnamed" "has no fluid_type lang entry" "$R
d=json.load(open('$LANG')); del d['fluid_type.elysiumspace.oxygen']; json.dump(d,open('$LANG','w'))"
echo "caught $pass, missed $miss"
[ "$miss" -eq 0 ]
