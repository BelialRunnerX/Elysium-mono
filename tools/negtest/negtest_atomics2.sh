#!/usr/bin/env bash
# The third age in the world, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-atomics
S=$M/src/main/java/com/elysium/atomics
R=$M/src/main/resources
G=$M/tools/gen_atomics.py
SNAP=/tmp/atomics2_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-atomics)
restore() { rm -rf $ROOT/elysium-atomics && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 600 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }
regen() { (cd $M && ELYSIUM_ATOMICS_OUT=$M python3 tools/gen_atomics.py >/dev/null) \
  || { echo "GENERATOR BROKE"; exit 2; }; }

echo "the ten blocks, and everything each of them needs"
edit $S/registry/AtomicBlocks.java 'BLOCKS.register("centrifuge",' 'BLOCKS.register("spinner",'
expect "a block registered under a name nothing else knows" "has no item, so it exists only to a command"

rm $R/assets/elysiumatomics/blockstates/quantum_cell.json
expect "a cell with no blockstate" "is a purple cube"

rm $R/data/elysiumatomics/loot_table/blocks/reactor_core.json
expect "a reactor that vanishes when mined" "has no drop"

edit $G '    "servo": "Servo",' '    "actuator": "Servo",'
regen
expect "a block with no name but its id" "has no name in the language file"

edit $S/registry/AtomicItems.java '            SERVO,' ''
expect "a block in no creative tab" "is in no creative tab"

edit $S/block/SolarPanelBlock.java 'box(0, 0, 0, 16, 4, 16)' 'box(0, 0, 0, 16, 10, 16)'
expect "a panel you can hit where you cannot see it" "different heights"

edit $G '        write(os.path.join(ASSETS, "models/block", name + "_on.json"),
              core_model(side + "_on", top))' \
        '        write(os.path.join(ASSETS, "models/block", name + "_on.json"),
              core_model(side, top))'
regen
edit $R/assets/elysiumatomics/blockstates/reactor_core.json '"elysiumatomics:block/reactor_core_on"' '"elysiumatomics:block/reactor_core"'
expect "a reactor that looks the same running and cold" "gives no sign that it is on"

echo "the cell, and the grid it must not re-walk"
edit $S/block/CellBlock.java '            return AtomicRules.reserveOf(List.of(cell()), List.of(held));' '            return new IndustryRules.Reserve(cell().mostOut(), cell().mostIn());'
expect "a cell that offers the grid more than it holds" "something other than what the rules say"

edit $S/block/CellBlock.java '            held = AtomicRules.after(cell(), held, flow, bank);' '            held = held + Math.round(flow.toReserve()) - Math.round(flow.fromReserve());'
expect "a cell that does its own arithmetic and its own rounding" "rounding that keeps energy from appearing"

edit $S/block/CellBlock.java '            if (moved) {' '            if (true) {'
expect "a cell that re-walks the grid every tick of its life" "or asks every tick"

echo "the reactor, and the ways it can stop being dangerous"
edit $S/block/ReactorBlock.java '            return spent() || fuelLeft <= 0 ? 0.0F : AtomicRules.throttleOf(notch);' '            return AtomicRules.throttleOf(notch);'
expect "a worn-out reactor still making power out of nothing" "neither wear nor fuel is a limit"

edit $S/block/ReactorBlock.java '            if (AtomicRules.meltingDown(temperature)) {' '            if (false) {'
expect "a reactor that cannot melt down" "never melts down"

edit $S/block/ReactorBlock.java '            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    AtomicRules.MELTDOWN_RADIUS, true, ExplosionInteraction.BLOCK);' ''
expect "a meltdown that quietly removes the block and nothing else" "a bang that never happens"

edit $S/block/ReactorBlock.java '                    AtomicRules.MELTDOWN_RADIUS, true, ExplosionInteraction.BLOCK);' '                    6.0F, true, ExplosionInteraction.BLOCK);'
expect "a meltdown whose size is decided a second time" "radius decided in two"

edit $S/block/ReactorBlock.java '            float worn = AtomicRules.wearAt(temperature);' '            float worn = 0.0F;'
expect "a core that never wears out however hot it gets" "worked out somewhere the harness cannot execute it"

edit $S/block/ReactorBlock.java '            return Math.min(AtomicRules.MOST_CHANNELS, found);' '            return found;'
expect "a reactor that counts more channels than the rules allow" "more coolant channels than the rules say"

edit $S/block/ReactorBlock.java '                        .getBlock() instanceof CoolantChannelBlock) {' '                        .getBlock() instanceof net.minecraft.world.level.block.Block) {'
expect "cooling that comes from any block at all" "not a build"

echo "the servo, and the line it must not shear"
edit $S/block/ServoBlock.java '            return AtomicRules.MADE_OF;' '            return KineticRules.Shaft.BRASS;'
expect "a servo that caps the line it is driving below the speed it drives it at" "does not say what it is made of"

edit $S/block/ServoBlock.java '            return AtomicRules.SERVO;' '            return new IndustryRules.Machine(
                    AtomicRules.SERVO_RATED_FE * share(), IndustryRules.Voltage.HIGH);'
expect "a servo whose draw depends on what it is given" "oscillates and never settles"

edit $S/block/ServoBlock.java '            return fed <= 0.0F ? null : AtomicRules.servoAsSource(fed);' '            return fed <= 0.0F ? null : new KineticRules.Source(1024.0F, 900.0F);'
expect "a servo driving the line by arithmetic nobody proved" "a servo the game does not have"

echo "the machines, the panel and the ladder"
edit $S/block/WorksBlock.java '        return AtomicRules.shaftShare(rpm);' '        return super.shaftShare(rpm);'
expect "an Age III machine driven by a shaft on Age II's terms" "stops getting worse as the spine climbs"

edit $S/block/WorksBlock.java '        return AtomicRules.ON_A_SHAFT;' '        return super.bestOnAShaft();'
expect "an Age III machine letting go of a shaft at Age II's share" "at Age II's share rather than its own"

edit $S/block/EnricherBlock.java '        return AtomicRules.ENRICHER_MULTIPLIER;' '        return 3;'
expect "the material ladder written down a second time" "decided twice"

edit $S/block/CentrifugeBlock.java '    @Override
    public RecipeType<? extends PlantRecipe> recipes() {' '    @Override
    public int multiplier() {
        return 3;
    }

    @Override
    public RecipeType<? extends PlantRecipe> recipes() {'
expect "a second machine that triples, retiring the one built for it" "the enricher's alone"

edit $S/block/CentrifugeBlock.java '        return AtomicRecipes.type(AtomicRecipe.Kind.CENTRIFUGING);' '        return AtomicRecipes.type(AtomicRecipe.Kind.REACTING);'
expect "a centrifuge reading the reaction chamber's recipes" "does the other's job"

edit $S/block/SolarPanelBlock.java 'level.canSeeSky(pos.above())' 'true'
expect "a solar panel that works underground" "whether it can see the sky"

echo "the recipes, and what this age owes the pack"
edit $G '        "M": ITEM(f"{IND}:motor")}, "servo"),' '        "M": ITEM("mekanism:ultimate_energy_cube")}, "servo"),'
regen
expect "a dependency on a mod we are replacing" "and nothing else"

edit $G '    "uranium_from_raw": ("#c:raw_materials/uranium", f"{NS}:uranium_ingot", 1, 400),' '    "uranium_from_raw": ("#c:raw_materials/uranium", f"{NS}:uranium_ingot", 3, 400),'
regen
expect "an enriching recipe that states its own tripling" "states its own tripling"

edit $G '    "reactor_core": shaped(["PPP", "PFP", "PPP"], {
        "P": STEEL_PLATE, "F": ITEM(f"{IND}:arc_furnace")}, "reactor_core"),' \
        '    "reactor_core": shaped(["PPP", "PFP", "PPP"], {
        "P": ITEM("minecraft:iron_ingot"), "F": ITEM(f"{IND}:arc_furnace")}, "reactor_core"),'
regen
expect "a reactor buildable without ever having had a factory" "without a single plate"

edit $R/data/elysiumatomics/recipe/reacting/leather_from_flesh.json '"elysiumatomics:reacting"' '"elysiumatomics:enriching"'
expect "a recipe sitting in one machine and claiming another" "work in neither machine"

edit $G 'KINDS = (("enriching", ENRICHING), ("reacting", REACTING),' 'KINDS = (("enriching", ENRICHING), ("reacting", {}),'
rm -rf $R/data/elysiumatomics/recipe/reacting
regen
expect "a machine with nothing it can make" "does nothing at all"

edit $R/data/c/tags/item/raw_materials/uranium.json '"elysiumatomics:raw_uranium"' ''
expect "a tag the uranium ore drop depends on and nothing fills" "is empty, so every recipe"

edit $R/assets/elysiumatomics/lang/en_us.json '"Spinning, at %s."' '"Spinning, at %s. "'
expect "a generated file quietly edited by hand" "edited by hand"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all atomics checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "atomics2: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
