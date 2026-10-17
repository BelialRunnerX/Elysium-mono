#!/bin/bash
cd /tmp/Elysium-mono || exit 1
negtest_lock() { if command -v flock >/dev/null 2>&1; then flock -n "$1"; else mkdir "$2" 2>/dev/null && trap "rmdir '$2' 2>/dev/null" EXIT; fi; }  # Git Bash on Windows has no flock
exec 9>/tmp/negtest_s6b.lock; negtest_lock 9 /tmp/negtest_s6b.lockdir || { echo busy; exit 2; }
J=elysium-space/src/main/java/com/elysium/space
RK=$J/entity/RocketEntity.java; CO=$J/machine/ConduitBlockEntity.java; CAP=$J/registry/SpaceBlockEntities.java; RT=$J/world/SpaceRoute.java
FILES="$RK $CO $CAP $RT"
pass=0; miss=0
snap(){ rm -rf /tmp/_s6; mkdir -p /tmp/_s6; i=0; for f in $FILES; do cp "$f" /tmp/_s6/$i; i=$((i+1)); done; }
restore(){ i=0; for f in $FILES; do cp /tmp/_s6/$i "$f"; i=$((i+1)); done; }
python3 elysium-space/validate.py >/dev/null 2>&1 || { echo "SETUP FAILED"; exit 2; }; echo "baseline clean"
try(){ snap; if ! python3 -c "$3"; then echo "SETUP FAILED: $1"; miss=$((miss+1)); restore; return; fi
  if python3 elysium-space/validate.py 2>&1 | grep -qF "$2"; then echo "CAUGHT : $1"; pass=$((pass+1)); else echo "MISSED : $1 ($2)"; miss=$((miss+1)); fi; restore; }
R='
import sys
def rep(p, a, b):
    s=open(p, encoding="utf-8", newline="").read()
    if a not in s: sys.exit("anchor: "+a[:50])
    open(p,"w", encoding="utf-8", newline="").write(s.replace(a,b,1))
'
try "route ignores motor" "assumes plain fuel" "$R
rep('$RK', 'if (from == null || !SpaceRoute.canTravel(from, destination, fuel(), motor())) {', 'if (from == null || !SpaceRoute.canTravel(from, destination, fuel(), SpaceRoute.Fuel.FUEL)) {')"
# Since cf24eb16 fuelIn is one switch on the motor with a case per fuel, so
# the old guard (motor() == HYDROGEN && a hydrogen canister) is gone, and so is
# the rule that read it. The same break in the new shape is a hydrogen canister
# taken outside its motor's case: tested before the switch (what dropping the
# motor test did before), or beside the fuel canister in the FUEL case. The
# rule that replaced it in elysium-space/validate.py catches both.
try "hydrogen into fuel motor" "does not tie SpaceItems.HYDROGEN_CANISTER to one motor" "$R
rep('$RK', '        switch (motor()) {\n            case FUEL -> {', '        if (held.is(SpaceItems.HYDROGEN_CANISTER.get())) {\n            return CANISTER_MB;\n        }\n        switch (motor()) {\n            case FUEL -> {')"
try "hydrogen in the fuel case" "does not tie SpaceItems.HYDROGEN_CANISTER to one motor" "$R
rep('$RK', 'if (held.is(SpaceItems.FUEL_CANISTER.get())) {', 'if (held.is(SpaceItems.FUEL_CANISTER.get()) || held.is(SpaceItems.HYDROGEN_CANISTER.get())) {')"
# Each fuel item is in exactly one case, but the wrong one: the reviewer's two
# breaks (17 October 2026) that the one-case rule alone passed.
try "hydrogen moved into the fuel case" "does not tie SpaceItems.HYDROGEN_CANISTER to its own motor" "$R
rep('$RK', '            case HYDROGEN -> {
                if (held.is(SpaceItems.HYDROGEN_CANISTER.get())) {
                    return CANISTER_MB;
                }
            }', '            case HYDROGEN -> {
            }')
rep('$RK', 'if (held.is(SpaceItems.FUEL_CANISTER.get())) {', 'if (held.is(SpaceItems.FUEL_CANISTER.get()) || held.is(SpaceItems.HYDROGEN_CANISTER.get())) {')"
try "the two canisters swapped" "does not tie SpaceItems.FUEL_CANISTER to its own motor" "$R
rep('$RK', 'if (held.is(SpaceItems.FUEL_CANISTER.get())) {', 'if (held.is(SpaceItems.SWAP_MARK.get())) {')
rep('$RK', 'if (held.is(SpaceItems.HYDROGEN_CANISTER.get())) {', 'if (held.is(SpaceItems.FUEL_CANISTER.get())) {')
rep('$RK', 'if (held.is(SpaceItems.SWAP_MARK.get())) {', 'if (held.is(SpaceItems.HYDROGEN_CANISTER.get())) {')"
try "a motor's case left with none of its own fuel" "case takes none of hydrogen's own fuel" "$R
rep('$RK', '            case HYDROGEN -> {
                if (held.is(SpaceItems.HYDROGEN_CANISTER.get())) {
                    return CANISTER_MB;
                }
            }', '            case HYDROGEN -> {
            }')"
try "a sixth motor the fuel table cannot name" "whose fuel this validator cannot name" "$R
rep('$RT', 'PLUTONIUM(29.3D);', 'PLUTONIUM(29.3D), ANTIMATTER(40.0D);')"
# Any item motorOf names fits a motor now, not only the hydrogen one, so the
# guard reads motor() == fitted. negtest_rocket.sh's "a motor fitted over a
# full tank" makes the same break.
try "motor swap keeps fuel" "does not require an empty tank" "$R
rep('$RK', 'if (phase() != Phase.IDLE || motor() == fitted || fuel() > 0) {', 'if (phase() != Phase.IDLE || motor() == fitted) {')"
# A cable moves energy along its run with carryEnergy since the runs (1d7d704d).
try "cable makes energy" "a conduit creates energy" "$R
rep('$CO', '            carryEnergy(MachineRules.CABLE_FE_PER_TICK);', '            energy.produce(1);\n            carryEnergy(MachineRules.CABLE_FE_PER_TICK);')"
# The pipe hands out the side it was asked about since the runs (1d7d704d).
try "pipe not exposed" "the pipe does not expose" "$R
rep('$CAP', 'event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PIPE.get(), (be, side) -> be.fluidsFrom(side));', '')"
try "hydrogen weak" "WorldHarness failed" "$R
rep('$RT', 'HYDROGEN(21.36D)', 'HYDROGEN(15.0D)')"
echo "caught $pass, missed $miss"
[ "$miss" -eq 0 ]
