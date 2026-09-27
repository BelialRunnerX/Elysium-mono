#!/usr/bin/env bash
# The seam between rotation and Forge Energy, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-kinetics
S=$M/src/main/java/com/elysium/kinetics/rules/SeamRules.java
K=$M/src/main/java/com/elysium/kinetics/rules/KineticRules.java
D=$M/src/main/java/com/elysium/kinetics/block/DynamoBlock.java
R=$M/src/main/java/com/elysium/kinetics/registry/KineticBlockEntities.java
G=$M/tools/gen_kinetics.py
SPEC=$ROOT/docs/SPEC_TECH.md
SNAP=/tmp/seam_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  (cd $M && timeout 400 python3 validate.py 2>&1 | tail -5)
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-kinetics docs/SPEC_TECH.md)
restore() { rm -rf $ROOT/elysium-kinetics && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
gexpect() { (cd $M && python3 tools/gen_kinetics.py >/dev/null 2>&1); expect "$@"; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the one thing a crossing may not do"
edit $K '    public static final float DYNAMO_FE_PER_RPM = 0.25F;' '    public static final float DYNAMO_FE_PER_RPM = 0.5F;'
expect "a dynamo paying exactly what a motor charges" "perpetual motion machine"

edit $K '    public static final float DYNAMO_FE_PER_RPM = 0.25F;' '    public static final float DYNAMO_FE_PER_RPM = 0.75F;'
expect "a dynamo paying more than a motor charges" "perpetual motion machine"

edit $S '    public static final float WORTH_OF_A_TORQUE_RPM = 0.05F;' '    public static final float WORTH_OF_A_TORQUE_RPM = 0.026F;'
expect "a torque-rpm cheapened until the seam nearly pays for itself" "a curiosity"

edit $S '        if (!(home < 1.0F)) {' '        if (false) {'
expect "a crossing check that cannot report a loop" "cannot say no"

edit $S '        if (home > WORST_WORTH_HAVING) {' '        if (false) {'
expect "a crossing check that cannot report a seam too good to be bad" "is not reported, so Age I could quietly become a power plant"

edit $S '        if (fePerRpm <= 0.0F) {' '        if (false) {'
expect "a crossing check that cannot report a dynamo making nothing" "a dynamo that makes nothing is not reported"

edit $S '        if (worth <= 0.0F) {' '        if (false) {'
expect "a crossing check that cannot report free rotation" "a torque-rpm worth nothing is not reported"

echo "the gearing that is solved rather than guessed"
edit $S '        return KineticRules.kept(stages) * source.stallTorque()
                / (2.0F * KineticRules.DYNAMO.draw());' '        return KineticRules.kept(stages) * source.stallTorque()
                / KineticRules.DYNAMO.draw();'
expect "a solved best gearing with the halving left out" "the ratio the sweep found best and the ratio the algebra solves for disagree"

edit $S '        return KineticRules.kept(stages)
                * source.powerAt(source.freeSpeed() / 2.0F)
                / KineticRules.DYNAMO.draw();' '        return KineticRules.kept(stages)
                * source.powerAt(source.freeSpeed() / 3.0F)
                / KineticRules.DYNAMO.draw();'
expect "a solved best speed taken at a third of the free speed" "the closed form"

edit $S '        return KineticRules.kept(stages)
                * source.powerAt(source.freeSpeed() / 2.0F)
                / KineticRules.DYNAMO.draw();' '        return 2.0F * KineticRules.kept(stages)
                * source.powerAt(source.freeSpeed() / 2.0F)
                / KineticRules.DYNAMO.draw();'
expect "a solved best that nothing can reach" "a ceiling nothing reaches"

edit $S '        if (Math.abs(line * ratio - rpm) > Math.max(1.0E-3F, rpm * 1.0E-3F)) {' '        if (false) {'
expect "a gearing check that cannot notice the ratio and the speed disagree" "the ratio and the speed could drift apart"

echo "what a dynamo takes and what it makes"
edit $S '        if (!KineticRules.DYNAMO.working(dynamoRpm)) {
            return 0.0F;
        }
        return KineticRules.DYNAMO.draw() * Math.abs(dynamoRpm);' '        if (!KineticRules.DYNAMO.working(dynamoRpm)) {
            return 0.0F;
        }
        return KineticRules.DYNAMO.draw() * Math.abs(dynamoRpm) * 0.25F;'
expect "a dynamo reported as taking a quarter of what it takes" "a machine hall that makes energy"

edit $S '        return KineticRules.dynamoOutput(Math.min(Math.abs(dynamoRpm),
                KineticRules.MAX_RPM));' '        return KineticRules.dynamoOutput(Math.abs(dynamoRpm) * 8.0F);'
expect "a dynamo making eight times what its speed is worth" "a machine hall that makes energy"

edit $S '        return Math.max(1, Math.round(best * BUFFER_TICKS));' '        return 1;'
expect "a dynamo tank too small to hold a tick of what it makes" "throws away what it made"

echo "the block a player actually places"
edit $D '            making = running() ? SeamRules.output(rpm()) : 0.0F;' '            making = running() ? KineticRules.dynamoOutput(rpm()) : 0.0F;'
expect "a dynamo working out its own output" "a number no harness has ever executed"

edit $D '                    public boolean canReceive() {
                        return false;
                    }' '                    public boolean canReceive() {
                        return true;
                    }'
expect "a dynamo a cable can charge" "a battery that fills itself"

edit $R '            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DYNAMO.get(),
                    (entity, side) -> entity.energy());' ''
expect "a dynamo nothing can take the power out of" "offers no energy capability"

edit $D '    public static final String NAME = "dynamo";' '    public static final String THING = "dynamo";'
expect "a dynamo that no longer says what it is called" "so the seam is a block nothing below it can find"

echo "the world the dynamo is actually in"
rm -f $M/src/main/resources/data/elysiumkinetics/recipe/dynamo.json
expect "a dynamo with no recipe" "dynamo has no recipe"

rm -f $M/src/main/resources/assets/elysiumkinetics/textures/block/dynamo.png
expect "a dynamo whose model names a texture that is not there" "which is not there"

edit $M/src/main/java/com/elysium/kinetics/registry/KineticItems.java '        made.add(DYNAMO);
        made.addAll(ANCHORS.values());' '        made.addAll(ANCHORS.values());'
expect "a dynamo nobody can find in the creative tab" "is not in the creative tab"

echo "the contract the later ages read"
edit $SPEC 'The number that makes both directions checkable lives in
`SeamRules.WORTH_OF_A_TORQUE_RPM`' 'The number that makes both directions checkable lives in
`SeamRules`'
edit $SPEC '    (DYNAMO_FE_PER_RPM / DYNAMO.draw) / WORTH_OF_A_TORQUE_RPM  =  0.5' '    (DYNAMO_FE_PER_RPM / DYNAMO.draw) / the rate  =  0.5'
expect "a spec that no longer names the rate Age II is defined against" "will choose its own rate"

edit $SPEC 'the best a dynamo can ever do is to hold
its source at half its free speed**' 'the best a dynamo can ever do is to hold
its source where it makes the most power**'
expect "a spec that no longer says what the best gearing is" "an answer only this module"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all kinetics checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | tail -3 | head -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "seam: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
