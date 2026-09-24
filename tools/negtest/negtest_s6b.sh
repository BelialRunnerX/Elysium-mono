#!/bin/bash
cd /tmp/Elysium-mono || exit 1
exec 9>/tmp/negtest_s6b.lock; flock -n 9 || { echo busy; exit 2; }
J=elysium-space/src/main/java/com/elysium/space
RK=$J/entity/RocketEntity.java; CO=$J/machine/ConduitBlockEntity.java; CAP=$J/registry/SpaceBlockEntities.java; RT=$J/world/SpaceRoute.java
FILES="$RK $CO $CAP $RT"
pass=0; miss=0
snap(){ rm -rf /tmp/_s6; mkdir -p /tmp/_s6; i=0; for f in $FILES; do cp "$f" /tmp/_s6/$i; i=$((i+1)); done; }
restore(){ i=0; for f in $FILES; do cp /tmp/_s6/$i "$f"; i=$((i+1)); done; }
python3 elysium-space/validate.py >/dev/null 2>&1 || { echo "SETUP FAILED"; exit 2; }; echo "baseline clean"
try(){ snap; if ! python3 -c "$3"; then echo "SETUP FAILED: $1"; restore; return; fi
  if python3 elysium-space/validate.py 2>&1 | grep -qF "$2"; then echo "CAUGHT : $1"; pass=$((pass+1)); else echo "MISSED : $1 ($2)"; miss=$((miss+1)); fi; restore; }
R='
import sys
def rep(p, a, b):
    s=open(p).read()
    if a not in s: sys.exit("anchor: "+a[:50])
    open(p,"w").write(s.replace(a,b,1))
'
try "route ignores motor" "assumes plain fuel" "$R
rep('$RK', 'if (from == null || !SpaceRoute.canTravel(from, destination, fuel(), motor())) {', 'if (from == null || !SpaceRoute.canTravel(from, destination, fuel(), SpaceRoute.Fuel.FUEL)) {')"
try "hydrogen into fuel motor" "does not tie hydrogen canisters" "$R
rep('$RK', 'motor() == SpaceRoute.Fuel.HYDROGEN && held.is(SpaceItems.HYDROGEN_CANISTER.get())', 'held.is(SpaceItems.HYDROGEN_CANISTER.get())')"
try "motor swap keeps fuel" "does not require an empty tank" "$R
rep('$RK', 'if (phase() != Phase.IDLE || motor() == SpaceRoute.Fuel.HYDROGEN || fuel() > 0) {', 'if (phase() != Phase.IDLE || motor() == SpaceRoute.Fuel.HYDROGEN) {')"
try "cable makes energy" "a conduit creates energy" "$R
rep('$CO', '            pushEnergy(MachineRules.CABLE_FE_PER_TICK);', '            energy.produce(1);\n            pushEnergy(MachineRules.CABLE_FE_PER_TICK);')"
try "pipe not exposed" "the pipe does not expose" "$R
rep('$CAP', 'event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PIPE.get(), (be, side) -> be.fluids());', '')"
try "hydrogen weak" "WorldHarness failed" "$R
rep('$RT', 'HYDROGEN(21.36D)', 'HYDROGEN(15.0D)')"
echo "caught $pass, missed $miss"
