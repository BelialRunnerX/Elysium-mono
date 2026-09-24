#!/usr/bin/env bash
# The first age, broken one way at a time.
#
# Two kinds of break here. The first kind changes a constant so the ladder
# flattens — a crank that turns two millstones, a wheel fast enough for a saw —
# which the harness cannot see, because the harness checks the model is
# consistent and a flattened ladder is perfectly consistent. The second kind
# takes out a piece of the model itself, which is what turns this back into
# Create's light switch.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-kinetics
R=$M/src/main/java/com/elysium/kinetics/rules/KineticRules.java
R_KIN=$M/src/main/resources
H=$M/tools/KineticHarness.java
SNAP=/tmp/kinetics_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-kinetics docs/SPEC_TECH.md)
restore() { rm -rf $ROOT/elysium-kinetics && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 300 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n')
    sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the model, which is the part that is not Create"
edit $R 'return Math.max(0.0F, stallTorque * (1.0F - Math.abs(rpm) / freeSpeed));' \
        'return stallTorque;'
expect "a source that is as strong at full speed as at rest" "falls off along a line"

edit $R 'return Math.max(0.0F, stallTorque * (1.0F - Math.abs(rpm) / freeSpeed));' \
        'return stallTorque * (1.0F - Math.abs(rpm) / freeSpeed);'
expect "an overdriven source pushing backwards" "instead of freewheeling"

edit $R 'float rpm = (stall - demand) / slope;' 'float rpm = stall / slope;'
expect "a line that ignores its load when deciding its speed" "settles where supply meets demand"

edit $R '        if (demand >= stall) {' '        if (demand >= stall * 2.0F) {'
expect "stalling moved off the point where the sources run out" "outweighing every source"

edit $R '        boolean sheared = shears(shaft, rpm);' '        boolean sheared = false;'
expect "a line that never asks whether it survived its own speed" "shaft rating is decoration"

edit $R '            return Math.min(1.0F, demand / capacity);' '            return demand > 0.0F ? 1.0F : 0.0F;'
expect "an effort gauge that is a light switch again" "the gauge does not climb"

edit $R 'public record Reading(float rpm, float demand, float capacity,' \
        'public record Reading(float rpm, float demand, float capacity, float supply,'
expect "a supply field back on the reading" "carries a supply again"

edit $R '            float size = Math.abs(ratio);
            float kept = kept(stages);' '            float size = ratio;
            float kept = kept(stages);'
expect "a source reflected through a signed ratio" "signed ratio"

edit $R '        return (float) Math.pow(1.0F - GEAR_LOSS, count);' '        return 1.0F;'
expect "gearing that costs nothing" "no longer cost power"

edit $R 'public static final float GEAR_LOSS = 0.03F;' 'public static final float GEAR_LOSS = 0.10F;'
expect "a gear stage priced at ten percent" "rather than the 3% the spec states"

edit $R 'public static final float MAX_RPM = 1024.0F;' 'public static final float MAX_RPM = 4096.0F;'
expect "the ceiling every age shares moved" "ceiling every age shares has moved"

edit $R '            if (!load.working(rpm) && load.leastRpm() > wanted) {' \
        '            if (load.leastRpm() > wanted) {'
expect "a line that cannot say what it is waiting for" "waiting for"

echo "the ladder, which the harness cannot see flattening"
edit $R 'MILLSTONE = new Load(4.0F, 8.0F);' 'MILLSTONE = new Load(3.0F, 8.0F);'
expect "a crank strong enough for two millstones" "nobody builds a water wheel"

edit $R 'HAND_CRANK = new Source(32.0F, 8.0F);' 'HAND_CRANK = new Source(32.0F, 3.0F);'
expect "a crank too weak for even one millstone" "no first step"

edit $R 'WATER_WHEEL = new Source(16.0F, 48.0F);' 'WATER_WHEEL = new Source(32.0F, 48.0F);'
expect "a water wheel fast enough to run a saw unaided" "gearing is optional"

edit $R 'STEAM_ENGINE = new Source(128.0F, 40.0F);' 'STEAM_ENGINE = new Source(128.0F, 64.0F);'
expect "steam stronger than water, which retires the water wheel" "obsolete the moment"

edit $R 'MIXER = new Load(12.0F, 32.0F);' 'MIXER = new Load(5.0F, 32.0F);'
expect "a mixer that costs less than the press" "not the expensive machine"

edit $R 'DYNAMO = new Load(10.0F, 32.0F);' 'DYNAMO = new Load(10.0F, 16.0F);'
expect "a dynamo easier to spin than a mixer" "easy option"

edit $R 'TIMBER(64.0F),' 'TIMBER(256.0F),'
expect "timber that survives steam" "brass is cosmetic"

edit $R 'public static final float FAST_AT = 64.0F;' 'public static final float FAST_AT = 80.0F;'
expect "the recipe book and the shaft disagreeing about fast" "disagree about what fast means"

echo "the tree of speeds"
L=$M/src/main/java/com/elysium/kinetics/rules/LineRules.java

edit $L 'return Math.abs(a - b) / size > SAME_RATIO;' 'return a != b;'
expect "ratios compared exactly, so float drift jams a sound line" "without scaling by their size"

edit $L 'reflected.add(driven.source().through(driven.at().ratio(), driven.at().stages()));' \
        'reflected.add(driven.source());'
expect "sources that ignore the cogs between them and the root" "as though the cogs were not there"

edit $L 'drawn.add(load.load().through(load.at().ratio(), load.at().stages()));' \
        'drawn.add(load.load());'
expect "a machine behind a step-up that costs nothing extra" "behind a step-up is free"

edit $L 'boolean sheared = KineticRules.shears(shaft, atRoot.rpm() * fastest);' \
        'boolean sheared = KineticRules.shears(shaft, atRoot.rpm());'
expect "shearing judged at the root while the far end flies apart" "reports itself intact"

edit $L '                KineticRules.settle(reflected, drawn, KineticRules.Shaft.ASCENDANT);' \
        '                KineticRules.settle(reflected, drawn, shaft);'
expect "the line judged against two speeds, first answer winning" "judged twice"

edit $L '        if (jammed) {' '        if (false) {'
expect "a gear train that fights itself and turns anyway" "still settles"

edit $L '            float here = assembly.speedOf(drawn.at());' \
        '            float here = assembly.root().rpm();'
expect "a geared machine judged against the root's speed" "rather than their own"

edit $L '            if (!drawn.load().working(here) && drawn.load().leastRpm() > wanted) {' \
        '            if (!drawn.load().working(here) && here > wanted) {'
expect "a speed reported that the machine's own label never says" "not the number written on the machine"

python3 - <<'PYX'
import pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-kinetics/src/main/java/com/elysium/kinetics/rules/LineRules.java")
p.unlink()
PYX
expect "LineRules deleted outright" "no arithmetic"

echo "the machine hall"
B=$M/src/main/java/com/elysium/kinetics/block
LINE=$B/KineticLine.java
BLK=$B/KineticBlock.java
BE=$B/KineticBlockEntity.java

edit $LINE '        if (here.axis() != there.axis()) {' '        if (false) {'
expect "two shafts crossing at right angles treated as one" "different axes are allowed to connect"

edit $LINE '        if (face.getAxis() == here.axis()) {' '        if (false) {'
expect "a row of shafts that is not one shaft" "not treated as the same shaft"

edit $LINE '            return KineticRules.ratio(here.teeth(), there.teeth());' '            return 1.0F;'
expect "cogs that mesh without changing anything" "the teeth are decoration"

edit $LINE '        if (here.teeth() > 0 && there.teeth() > 0) {' '        if (here.teeth() > 0 || there.teeth() > 0) {'
expect "a shaft laid beside a cog acting as a gearbox" "a shaft laid beside a shaft is a gearbox"

edit $LINE '                    if (LineRules.conflict(already.ratio(), reached)) {' '                    if (false) {'
expect "a gear loop that never checks the second path" "turns anyway"

edit $LINE '                if (LineRules.tooBig(seen.size())) {' '                if (false) {'
expect "a walk with no bound on it" "a server tick that never ends"

edit $LINE '                int stages = at.stages() + (ratio == 1.0F ? 0 : 1);' '                int stages = at.stages() + 1;'
expect "plain shaft counted as a gear stage" "costs power that no teeth ever took"

edit $LINE '                node.driven(assembly.speedOf(member.at()), assembly.running());' \
            '                node.driven(assembly.root().rpm(), assembly.running());'
expect "every member told the root speed instead of its own" "told the root"

edit $LINE '            if (node.material().rating() < weakest.rating()) {' '            if (false) {'
expect "a line that ignores its weakest shaft" "judged by its weakest shaft"

edit $BE '        this.unsettled = true;
    }

    @Override
    protected void saveAdditional' '        this.unsettled = false;
    }

    @Override
    protected void saveAdditional'
expect "a block entity trusting the speed it was saved with" "trusts the speed it was saved with"

edit $BE '                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);' \
         '                setChanged();'
expect "a speed change never sent to the client" "not sent to the client"

edit $BE 'net.minecraft.world.level.block.Block.UPDATE_CLIENTS);' '3);'
expect "an update that pokes the neighbours and re-solves the line" "notify neighbours"

edit $BE '        if (level.isClientSide) {' '        if (false) {'
expect "a client working the line out for itself" "overwriting the speed"

edit $BLK 'if (assembly.jammed()) {
            return Component.translatable("elysiumkinetics.line.jammed");
        }' 'if (false) {
            return Component.translatable("elysiumkinetics.line.stalled");
        }'
expect "a readout that cannot say a line is jammed" "cannot say a line is jammed"

edit $BLK '            float speed = Math.abs(assembly.speedOf(member.at()));' \
            '            float speed = Math.abs(assembly.root().rpm());'
expect "geared machines judged against the root" "rather than their own"

echo "the gearbox and the windmill"
W=$B/WindmillBearingBlock.java

edit $LINE '        if (!here.couplesAnyAxis() && here.axis() != face.getAxis()) {' \
           '        if (false) {'
expect "a gearbox grabbing a shaft that merely lies across its face" "merely lying across its face"

edit $LINE '        return -1.0F;
    }

    // ------------------------------------------------------------------
    // The walk' '        return 1.0F;
    }

    // ------------------------------------------------------------------
    // The walk'
expect "a gearbox that does not reverse" "does not reverse"

edit $B/ShaftBlock.java '        @Override
        public Direction.Axis axis() {' '        @Override
        public boolean couplesAnyAxis() {
            return true;
        }

        @Override
        public Direction.Axis axis() {'
expect "a second block coupling shafts of any axis" "couple shafts of any axis"

edit $W '                if (face.getAxis() == axis) {' '                if (false) {'
expect "a windmill that counts sails along its own axis" "a windmill is a drum"

edit $W '        return Math.min(KineticRules.MOST_SAILS, found.size());' '        return found.size();'
expect "a windmill with no limit on its sails" "a steam engine made of wool"

edit $W '            return new KineticRules.Source(one.freeSpeed(), one.stallTorque() * sails);' \
        '            return new KineticRules.Source(one.freeSpeed() * sails, one.stallTorque());'
expect "sails that add speed instead of force" "buys nothing"

echo "what is actually in the jar"
rm $R_KIN/assets/elysiumkinetics/blockstates/millstone.json
expect "a block with no blockstate" "millstone has no blockstate"

rm $R_KIN/data/elysiumkinetics/loot_table/blocks/large_cogwheel.json
expect "a cogwheel that vanishes when mined" "large_cogwheel has no drop"

rm $R_KIN/assets/elysiumkinetics/textures/block/small_cogwheel.png
expect "a model naming a texture that is not there" "which is not there"

python3 - <<'PYX'
import json, pathlib, collections
p = pathlib.Path("/tmp/Elysium-mono/elysium-kinetics/src/main/resources/assets/elysiumkinetics/lang/en_us.json")
d = json.loads(p.read_text(), object_pairs_hook=collections.OrderedDict)
d.pop("block.elysiumkinetics.water_wheel", None)
p.write_text(json.dumps(d, indent=2) + "\n")
PYX
expect "a block with no name in any language" "water_wheel has no name in any language"

python3 - <<'PYX'
import pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-kinetics/src/main/java/com/elysium/kinetics/registry/KineticItems.java")
s = p.read_text()
old = "HAND_CRANK, WATER_WHEEL, WINDMILL_BEARING, SAIL,"
assert old in s, "STALE ANCHOR in KineticItems.java"
s = s.replace(old, "HAND_CRANK, WINDMILL_BEARING, SAIL,", 1)
p.write_text(s)
PYX
expect "a block nobody can find in the creative tab" "not in the creative tab"

python3 - <<'PYX'
import json, pathlib, glob
for f in glob.glob("/tmp/Elysium-mono/elysium-kinetics/src/main/resources/data/elysiumkinetics/recipe/*/*.json"):
    d = json.loads(pathlib.Path(f).read_text())
    d["pace"] = "slow"
    pathlib.Path(f).write_text(json.dumps(d, indent=2) + "\n")
PYX
expect "every machine recipe at the same pace, so speed decides nothing" "the pace field does nothing"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-kinetics/src/main/resources/data/elysiumkinetics/recipe/milling/bone.json")
d = json.loads(p.read_text())
d["result"]["id"] = "create:crushed_raw_iron"
p.write_text(json.dumps(d, indent=2) + "\n")
PYX
expect "a machine recipe needing a mod Age I does not depend on" "does not depend on"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-kinetics/src/main/resources/data/elysiumkinetics/recipe/sawing/planks_from_logs.json")
d = json.loads(p.read_text())
d["type"] = "elysiumkinetics:pressing"
p.write_text(json.dumps(d, indent=2) + "\n")
PYX
expect "a sawing recipe filed under sawing and declared as pressing" "runs in another"

python3 - <<'PYX'
import shutil, pathlib
shutil.rmtree("/tmp/Elysium-mono/elysium-kinetics/src/main/resources/data/elysiumkinetics/recipe/mixing")
PYX
expect "a machine with nothing it can do" "draws torque and does no work"

K=$M/src/main/java/com/elysium/kinetics
edit $K/recipe/KineticRecipe.java '        MIXING("mixing");' '        MIXING("stirring");'
expect "a recipe kind whose folder does not exist" "a machine nobody can give work to"

edit $K/block/MachineBlock.java '            if (!recipe.possibleAt(speed)) {' '            if (false) {'
expect "a machine that works at any speed at all" "the pace on a recipe is decoration"

edit $K/block/MachineBlock.java '            if (!items.insertItem(OUT, result, true).isEmpty()) {' '            if (false) {'
expect "a machine with a full output destroying what it makes" "destroys what it makes"

edit $K/block/MachineBlock.java '    public abstract KineticRecipe.Kind kind();' '    public KineticRecipe.Kind kind() {
        return KineticRecipe.Kind.MILLING;
    }'
expect "a machine that need not say which recipes it reads" "which recipes it reads"

echo "independence, and the age above"
edit $M/src/main/java/com/elysium/kinetics/ElysiumKinetics.java \
  'public static final String MODID = "elysiumkinetics";' \
  'public static final String MODID = "elysiumkinetics";
    static final Class<?> UP = com.elysium.industry.Anything.class;'
expect "Age I reaching up into Age II" "depend downwards only"

python3 - <<'PY'
import pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-kinetics/src/main/resources/META-INF/neoforge.mods.toml")
s = p.read_text(encoding="utf-8")
p.write_text(s + '''
[[dependencies.${mod_id}]]
    modId="create"
    type="required"
    versionRange="[1.0.0,)"
    ordering="AFTER"
    side="BOTH"
''', encoding="utf-8")
PY
expect "a declared dependency on the mod this module replaces" "Age I needs the game and the library"

echo "the contract the later ages read"
edit $ROOT/docs/SPEC_TECH.md 'rpm = (Σs − D) / Σ(s/f)' 'the line settles somewhere sensible'
expect "the spec losing the formula three ages are built on" "so a later age has nothing to build against"

edit $ROOT/docs/SPEC_TECH.md '| Timber | 64 rpm | I |' '| Pine | 64 rpm | I |'
expect "a shaft material the spec forgot" "does not mention the Timber shaft"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all kinetics checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "kinetics: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
