#!/usr/bin/env bash
# The second age in the world, broken one way at a time.
#
# The first suite breaks the arithmetic. This one breaks the half that can be
# wrong while every number is right: a block with no model, a machine that
# drags a shaft it is not using, an engine that burns into an empty grid, a
# recipe for something nothing makes.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-industry
S=$M/src/main/java/com/elysium/industry
R=$M/src/main/resources
G=$M/tools/gen_industry.py
SNAP=/tmp/industry2_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-industry)
restore() { rm -rf $ROOT/elysium-industry && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 300 python3 validate.py 2>&1); }
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
regen() { (cd $M && ELYSIUM_INDUSTRY_OUT=$M python3 tools/gen_industry.py >/dev/null) \
  || { echo "GENERATOR BROKE"; exit 2; }; }

echo "the twelve blocks, and everything each of them needs"
edit $S/registry/IndustryBlocks.java 'BLOCKS.register("separator",' 'BLOCKS.register("sorter",'
expect "a block registered under a name nothing else knows" "has no item, so it exists only to a command"

rm $R/assets/elysiumindustry/blockstates/crusher.json
expect "a machine with no blockstate" "is a purple cube"

rm $R/data/elysiumindustry/loot_table/blocks/transformer.json
expect "a transformer that vanishes when mined" "has no drop"

edit $G '    "motor": "Motor",' '    "engine": "Motor",'
regen
expect "a block with no name but its id" "has no name in the language file"

edit $G '    write(os.path.join(picks, "mineable/pickaxe.json"),
          {"replace": False, "values": [f"{NS}:{name}" for name in BLOCKS]})' \
        '    write(os.path.join(picks, "mineable/pickaxe.json"),
          {"replace": False, "values": []})'
regen
expect "twelve metal blocks nobody needs a pickaxe for" "is in no mineable tag"

edit $S/registry/IndustryItems.java '            STEAM_TURBINE, DIESEL_ENGINE,' '            DIESEL_ENGINE,'
expect "a block in no creative tab" "is in no creative tab"

edit $S/block/WireBlock.java 'box(6, 6, 6, 10, 10, 10)' 'box(4, 4, 4, 12, 12, 12)'
expect "a wire you can hit where you cannot see it" "different sizes"

edit $G '        write(os.path.join(ASSETS, "models/block", name + "_on.json"),
              engine_model(front + "_on", side, top))' \
        '        write(os.path.join(ASSETS, "models/block", name + "_on.json"),
              engine_model(front, side, top))'
regen
edit $R/assets/elysiumindustry/blockstates/steam_turbine.json '"elysiumindustry:block/steam_turbine_on"' '"elysiumindustry:block/steam_turbine"'
expect "an engine that looks the same running and cold" "looks the same lit and cold"

echo "the two networks, and the ways they can be made to lie"
edit $S/block/PlantBlock.java '            if (plant == null || share() >= plant.bestOnAShaft()) {' '            if (plant == null) {'
expect "a grid-fed machine still dragging the shaft beside it" "does not let go of a shaft"

edit $S/block/PlantBlock.java '            return Math.max(onGrid, onShaft);' '            return onGrid;'
expect "a backup that is not a backup" "whichever is better"

edit $S/block/PlantBlock.java '            float onShaft = lineRunning && plant != null ? plant.shaftShare(rpm) : 0.0F;' '            float onShaft = lineRunning ? IndustryRules.shaftShare(rpm) : 0.0F;'
expect "a machine that decides for itself what a shaft is worth" "rather than asking its own block"

edit $S/block/CrusherBlock.java '        return IndustryRecipes.type(IndustryRecipe.Kind.CRUSHING);' '        return IndustryRecipes.type(IndustryRecipe.Kind.ROLLING);'
regen
expect "a crusher reading the rolling mill's recipes" "does the other's job"

edit $S/block/PlantBlock.java '            done += IndustryRules.workPerTick(speed);' '            done += 1;'
expect "a machine that counts ticks while the gauge counts work" "work-per-tick"

edit $S/block/PlantBlock.java '            result.setCount(result.getCount() * plant.multiplier());' '            result.setCount(result.getCount());'
expect "a separator that does not separate" "ignores its own multiplier"

edit $S/block/SeparatorBlock.java '        return IndustryRules.SEPARATOR_MULTIPLIER;' '        return 2;'
expect "the material ladder written down a second time" "decided twice"

edit $S/block/CrusherBlock.java '    @Override
    public RecipeType<? extends PlantRecipe> recipes() {' '    @Override
    public int multiplier() {
        return 2;
    }

    @Override
    public RecipeType<? extends PlantRecipe> recipes() {'
expect "a second machine that doubles, retiring the one built for it" "the separator's alone"

edit $S/block/MotorBlock.java '        @Nullable
        @Override
        public IndustryRules.Machine machine() {
            return MOTOR;
        }' '        @Nullable
        @Override
        public IndustryRules.Machine machine() {
            return new IndustryRules.Machine(
                    IndustryRules.MOTOR_RATED_FE * share(), IndustryRules.Voltage.LOW);
        }'
expect "a motor whose draw depends on what it is given" "oscillates and never settles"

edit $S/block/MotorBlock.java '            return fed <= 0.0F ? null : IndustryRules.motorAsSource(fed);' '            return fed <= 0.0F ? null : new KineticRules.Source(256.0F, 96.0F);'
expect "a motor driving the line by arithmetic nobody proved" "a motor the game does not have"

edit $S/block/FueledBlock.java '            } else if (wanted()) {' '            } else {'
expect "an engine burning fuel into a grid with nothing on it" "whether anything wants the power"

edit $S/block/FueledBlock.java '            return burning > 0 && engine != null ? engine.output() : null;' '            return engine == null ? null : engine.output();'
expect "a cold firebox the grid measures its distances from" "still a generator"

edit $S/block/SteelNode.java '        return KineticRules.Shaft.STEEL;' '        return KineticRules.Shaft.TIMBER;'
expect "Age II machinery that shears a brass line" "caps that line at the rating of timber"

edit $S/block/PlantBlock.java 'public static class PlantBlockEntity extends GridBlockEntity implements SteelNode {' 'public static class PlantBlockEntity extends GridBlockEntity
            implements com.elysium.kinetics.block.KineticNode {'
expect "a machine on a line that never says what it is made of" "without saying what it is made of"

echo "the recipes, and the ladder inside them"
edit $G '        "K": ITEM(f"{KIN}:brass_shaft")}, "alternator"),' '        "K": ITEM("mekanism:steel_casing")}, "alternator"),'
regen
expect "a spaceport-grade dependency on a mod we are replacing" "and nothing else"

edit $G '    "iron_from_raw": ("minecraft:raw_iron", "minecraft:iron_ingot", 1, 200),
    "copper_from_raw": ("minecraft:raw_copper", "minecraft:copper_ingot", 1, 200),' \
        '    "iron_from_raw": ("minecraft:raw_iron", "minecraft:iron_ingot", 2, 200),
    "copper_from_raw": ("minecraft:raw_copper", "minecraft:copper_ingot", 1, 200),'
regen
expect "a separating recipe that states its own doubling" "states its own doubling"

edit $G '    "steel_wire": shaped(["SSS"], {"S": STEEL_TAG}, "steel_wire", 8),' '    "steel_wire": shaped(["SSS"], {"S": ITEM("minecraft:iron_ingot")}, "steel_wire", 8),'
regen
expect "the top tier of wire with no arc furnace behind it" "can be built without steel"

edit $G '        "I": ITEM("minecraft:iron_ingot"), "C": ITEM("minecraft:copper_ingot"),
        "F": ITEM("minecraft:blast_furnace"), "B": ITEM("minecraft:bricks")},
        "steam_turbine"),' \
        '        "I": TAG("c:plates/iron"), "C": ITEM("minecraft:copper_ingot"),
        "F": ITEM("minecraft:blast_furnace"), "B": ITEM("minecraft:bricks")},
        "steam_turbine"),'
regen
expect "a first generator that needs a factory to build" "cannot be built without a grid"

edit $R/data/elysiumindustry/recipe/crushing/sand_from_gravel.json '"elysiumindustry:crushing"' '"elysiumindustry:rolling"'
expect "a recipe sitting in one machine and claiming another" "work in neither machine"

edit $G 'CRUSHING = {' 'CRUSHING_UNUSED = {'
edit $G 'KINDS = (("crushing", CRUSHING),' 'KINDS = (("crushing", {}),'
rm -rf $R/data/elysiumindustry/recipe/crushing
regen
expect "a machine with nothing it can make" "does nothing at all"

edit $R/data/c/tags/item/ingots/steel.json '"elysiumindustry:steel_ingot"' ''
expect "a tag every gated recipe asks for and nothing fills" "is empty, so every recipe"

edit $R/assets/elysiumindustry/lang/en_us.json '"Crushing, at %s."' '"Crushing, at %s. "'
expect "a generated file quietly edited by hand" "edited by hand"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all industry checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "industry2: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
