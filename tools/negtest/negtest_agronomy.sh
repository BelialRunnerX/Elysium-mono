#!/usr/bin/env bash
# Things that grow, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-agronomy
S=$M/src/main/java/com/elysium/agronomy
R=$M/src/main/resources
A=$S/rules/AgronomyRules.java
G=$M/tools/gen_agronomy.py
SNAP=/tmp/agronomy_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-agronomy)
restore() { rm -rf $ROOT/elysium-agronomy && (cd $ROOT && tar xf $SNAP); }
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
regen() { (cd $M && ELYSIUM_AGRONOMY_OUT=$M python3 tools/gen_agronomy.py >/dev/null) \
  || { echo "GENERATOR BROKE"; exit 2; }; }

echo "the margin, which is the whole module"
edit $A '    public static final Crop FIRST_CROP = new Crop(1, 12_000, 1);' '    public static final Crop FIRST_CROP = new Crop(1, 1_200, 1);'
expect "a crop fast enough to retire mining" "past the margin"

edit $A '    public static final float FARM_AT_MOST = 0.66F;' '    public static final float FARM_AT_MOST = 1.4F;'
expect "a farm allowed to beat a mine outright" "three ages of ore ladder stop mattering"

edit $A '    public static final int ESSENCE_PER_INGOT = 8;' '    public static final int ESSENCE_PER_INGOT = 1;'
expect "essence that turns into metal one for one" "past the margin"

edit $A '    public static final int BEST_MULTIPLIER = 3;' '    public static final int BEST_MULTIPLIER = 1;'
expect "a farm measured against a mine with no processing behind it" "not three"

edit $A '    public static final float TIER_SLOWER = 2.0F;' '    public static final float TIER_SLOWER = 0.5F;'
expect "a ladder whose expensive rungs are the quick ones" "grows no slower than the one below"

edit $A '        for (int step = 1; step < Math.max(1, Math.min(TIERS, tier)); step++) {' '        for (int step = 1; step < Math.max(1, tier); step++) {'
expect "a sixth tier invented by a data file" "keeps getting slower"

echo "the machines"
edit $A '        if (light < LEAST_LIGHT) {' '        if (false) {'
expect "a pot that grows things in the dark" "grows things in the dark"

edit $A '            speed *= 1.0F + (ACCELERATOR_FASTER - 1.0F) * fed;' '            speed *= ACCELERATOR_FASTER;'
expect "an accelerator that works without power" "still works"

edit $A '            float fed = Math.max(0.0F, Math.min(1.0F, share));' '            float fed = Math.max(0.0F, share);'
expect "an accelerator made faster than full by over-building" "faster than full"

edit $A '        int working = Math.max(0, Math.min(MOST_BEES, bees));' '        int working = Math.max(0, bees);'
expect "a hive working more bees than it holds" "more bees than it says it holds"

edit $A '                : Math.min(1.0F, Math.max(0, flowers) / wanted);' '                : Math.max(0, flowers) / wanted;'
expect "a hive made faster than full by planting a meadow" "faster than full"

edit $A '        float fed = rung == 0 ? 1.0F : Math.max(0.0F, Math.min(1.0F, share));' '        float fed = Math.max(0.0F, Math.min(1.0F, share));'
expect "a first rock generator that needs a grid" "which it does not need"

edit $A '        int rung = Math.max(0, Math.min(ROCK_PER_HOUR.length - 1, tier));' '        int rung = Math.max(0, tier);'
expect "a fourth rock generator invented by a data file" "keeps going"

echo "the diet and the frontier"
edit $A '        float through = (float) Math.sqrt((double) tried / FOODS_FOR_FULL);' '        float through = (float) tried / FOODS_FOR_FULL;'
expect "a diet bonus that climbs in a straight line" "checklist rather than a discovery"

edit $A '        int tried = Math.max(0, Math.min(FOODS_FOR_FULL, foods));' '        int tried = Math.max(0, foods);'
expect "a diet bonus with no cap on it" "past its cap"

edit $A '            new Frontier("pale", 0, 4, false),' '            new Frontier("pale", 0, 15, false),'
expect "a frontier crop that grows absolutely anywhere" "it is not a frontier crop"

edit $A '            new Frontier("luna", 0, 15, true),' '            new Frontier("luna", 12, 4, true),'
expect "a crop that wants more light than it will tolerate" "grows nowhere at all"

echo "the blocks"
edit $S/block/PotBlock.java '            return AgronomyRules.potSpeed(light, driver != null,' '            return light < AgronomyRules.LEAST_LIGHT ? 0.0F : (driver == null ? 1.5F : 6.0F) * (0 *'
edit $S/block/PotBlock.java '                    driver == null ? 0.0F : driver.share());' '                    1) + (driver == null ? 1.5F : 6.0F) * 0;'
expect "a pot doing its own arithmetic on growth" "rather than asking the rules"

edit $S/block/PotBlock.java '            grown += done;' '            grown += 1;'
expect "a pot counting ticks where the rules count work" "invents or throws away progress"

edit $S/block/AcceleratorBlock.java '            return pots <= 0 ? null : AgronomyRules.ACCELERATOR;' '            return AgronomyRules.ACCELERATOR;'
expect "an accelerator that taxes the grid with no pot beside it" "permanent cost with no effect"

edit $S/block/HiveBlock.java '                if (level.getBlockState(around).is(net.minecraft.tags.BlockTags.FLOWERS)) {' '                if (level.getBlockState(around).isAir()) {'
expect "a hive that counts air instead of flowers" "walk outside and look at"

edit $S/block/RockGeneratorBlock.java '            return draw.draw() <= 0.0F ? null : draw;' '            return draw;'
expect "a first rock generator that asks the grid for nothing and gets charged" "behind the second age"

echo "the ladder, in two places"
edit $S/registry/AgronomyItems.java '        SEEDS.put("diamond", 4);' '        SEEDS.put("diamond", 3);'
expect "a seed at one tier in the Java and another in the data" "disagree"

rm $R/data/elysiumagronomy/recipe/gold_from_essence.json
expect "essence with nothing to turn it into" "no use"

rm $R/data/elysiumagronomy/recipe/iron_seed.json
expect "a rung with no seed to reach it" "cannot be reached"

edit $G '        "C": ITEM("minecraft:clay_ball"), "P": TAG("minecraft:planks")},' '        "C": TAG("c:plates/steel"), "P": TAG("minecraft:planks")},'
regen
expect "a pot that needs a factory" "long before it"

edit $G '        "P": TAG("minecraft:planks"), "H": ITEM("minecraft:honeycomb")},' '        "P": TAG("minecraft:planks"), "H": ITEM("productivebees:bee_cage")},'
regen
expect "a dependency on one of the ten" "and nothing else"

edit $R/assets/elysiumagronomy/lang/en_us.json '"No bees in it."' '"No bees in it. "'
expect "a generated file quietly edited by hand" "edited by hand"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all agronomy checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "agronomy: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
