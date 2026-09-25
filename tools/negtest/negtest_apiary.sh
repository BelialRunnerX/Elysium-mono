#!/usr/bin/env bash
# The apiary, the upgrades and the centrifuge, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-agronomy
A=$M/src/main/java/com/elysium/agronomy/rules/ApiaryRules.java
B=$M/src/main/java/com/elysium/agronomy/rules/BeeRules.java
K=$M/src/main/java/com/elysium/agronomy/block/HiveBlock.java
C=$M/src/main/java/com/elysium/agronomy/block/CentrifugeBlock.java
G=$M/tools/gen_bees.py
SNAP=/tmp/apiary_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  (cd $M && timeout 400 python3 validate.py 2>&1 | tail -5)
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-agronomy elysium-foundry)
restore() { rm -rf $ROOT/elysium-agronomy $ROOT/elysium-foundry && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
gexpect() { (cd $M && python3 tools/gen_bees.py >/dev/null 2>&1); expect "$@"; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the one line a hive may not cross"
edit $A '        return Math.round(effective * AgronomyRules.TICKS_PER_HOUR / ceiling());' '        return Math.round(effective * AgronomyRules.TICKS_PER_HOUR / ceiling()) * 4;'
expect "a comb time four times slower than the ceiling wants, so the top rung is not worth building" "the top of the ladder is a block nobody would climb to"

edit $A '        return Math.round(effective * AgronomyRules.TICKS_PER_HOUR / ceiling());' '        return Math.round(effective * AgronomyRules.TICKS_PER_HOUR / ceiling()) / 4;'
expect "a comb time four times faster than the ceiling allows, so a hive out-earns a mine" "an hour and the ceiling is"

edit $A '    public static final float BARE_AT_MOST = 0.5F;' '    public static final float BARE_AT_MOST = 0.05F;'
expect "a floor so low that a bare hive is already over it" "there is no room under the line for a ladder"

edit $A '        return perHour(tier, housing, fitted) <= ceiling() + 0.001F;' '        return true;'
expect "a ceiling check that cannot report anything" "so the ceiling check cannot say no"

echo "the number that is solved rather than chosen"
edit $B '    public static final int FIRST_RESOURCE_TICKS = ApiaryRules.ticksForTheCeiling();' '    public static final int FIRST_RESOURCE_TICKS = 64_800;'
expect "the bees taking their comb time from a constant rather than the ceiling" "BeeRules no longer takes its first resource comb from the ceiling"

edit $A '        float ticks = BeeRules.combTicks(tier) / speedIn(fitted);
        return beesIn(housing, fitted)
                * (AgronomyRules.TICKS_PER_HOUR / ticks) * BeeRules.PER_COMB;' '        return beesIn(housing, fitted) * speedIn(fitted)
                * ((float) AgronomyRules.TICKS_PER_HOUR / BeeRules.combTicks(tier))
                * BeeRules.PER_COMB;'
expect "a speed upgrade multiplying the bees rather than shortening the comb" "which is the same number today and a different one"

echo "the ladder"
edit $A '            new Housing("advanced_apiary", 20, 4, "apiary"),' '            new Housing("advanced_apiary", 14, 1, "apiary"),'
expect "a rung no better than the one below it" "so the rung above is no better than the one below"

edit $A '            new Housing("advanced_apiary", 20, 4, "apiary"),' '            new Housing("advanced_apiary", 20, 4, "hive"),'
expect "a rung built out of something other than the rung below it" "so the ladder has a rung nobody climbs through"

edit $A '            new Housing("apiary", 16, 2, "hive"),' '            new Housing("hive", 16, 2, "hive"),'
expect "two rungs with one name" "names two rungs of the ladder"

echo "what an upgrade is allowed to be"
edit $A '            new Upgrade("sampling_upgrade", 0, 1.0F, "catching one to look at"));' '            new Upgrade("sampling_upgrade", 0, 1.0F, ""));'
expect "an upgrade that earns nothing and takes no chore away" "so it is a recipe that costs something and does nothing"

edit $A '            new Upgrade("flowering_upgrade", 0, 1.0F, "planting flowers"),' '            new Upgrade("flowering_upgrade", 4, 1.0F, "planting flowers"),'
expect "an upgrade that both earns and buys attention" "those are two different kinds of thing"

edit $A '            new Upgrade("simulation_upgrade", 0, 1.0F, "bees wandering off"),' '            new Upgrade("simulation_upgrade", 0, 1.0F, "planting flowers"),'
expect "two upgrades that take away the same chore" "so one of them is a slot nobody would spend"

echo "what a comb comes apart into"
edit $A '        if (BeeRules.RESOURCE.equals(bee.kind())) {
            made.add(OURS + ":" + chunkOf(bee.produce()));
        }
        made.add(OURS + ":wax");' '        made.add(OURS + ":" + chunkOf(bee.produce()));
        made.add(OURS + ":wax");'
expect "a comb of honey that spins into metal" "bee and its comb gives"

edit $A '        made.add(VANILLA + ":honey_bottle");' '        made.add("someothermod:honey_bottle");'
expect "a comb that spins into another mod's item" "may only hand over its own items and the game's"

edit $A '        List<String> made = new ArrayList<>(SeedRules.chunked());
        for (String material : BeeRules.materialsNamed()) {
            if (!made.contains(material)) {
                made.add(material);
            }
        }
        return List.copyOf(made);' '        return SeedRules.chunked();'
expect "a material a bee makes with no chunk registered for it" "and no chunk of it is registered"

echo "the block a player actually uses"
edit $K '            return ApiaryRules.perHour(1, housing(), fitted()) * share
                    * ratio();' '            return crowded;'
expect "a hive that works out its own rate instead of asking the rules" "so the one line a hive may not cross is enforced over arithmetic"

edit $K '            float perHour = rate();' '            float perHour = AgronomyRules.combsPerHour(bees(), flowering());'
expect "a hive at work ignoring the rate it worked out" "so the upgrades fitted to it change a number nobody reads"

edit $C '            java.util.List<String> made = ApiaryRules.spun(bee);
            java.util.List<Integer> counts = ApiaryRules.spunCounts(bee);
            for (int at = 0; at < made.size(); at++) {
                put(FIRST_OUT + at, AgronomyItems.thing(made.get(at)),
                        counts.get(at));
            }' '            put(FIRST_OUT, AgronomyItems.thing("elysiumagronomy:wax"), 1);'
expect "a centrifuge deciding for itself what a comb is worth" "which is a second opinion about what a bee produces"

edit $C '            if (bee == null || share() <= 0.0F || burned()) {' '            if (bee == null) {'
expect "a centrifuge that spins with no power at all" "so the one cost of running it is optional"

edit $C '            return items.getStackInSlot(IN).isEmpty() ? null : ApiaryRules.SPINNER;' '            return ApiaryRules.SPINNER;'
expect "a centrifuge that taxes the grid whether or not it has anything to spin" "is a permanent cost with no effect"

edit $C '            if (!room(bee)) {
                return;
            }' '            if (false) {
                return;
            }'
expect "a centrifuge that spins a comb into a full slot, which destroys it" "so a full output slot is a comb destroyed"

echo "the world the machinery is actually in"
rm -f $M/src/main/resources/data/elysiumagronomy/recipe/advanced_apiary.json
expect "a rung of the ladder with no recipe" "so a rung of the ladder is a block only the creative menu has"

edit $G '                    "H": {"item": f"{NS}:{housing['"'"'from'"'"']}"}},' '                    "H": {"item": "minecraft:oak_planks"}},'
gexpect "a rung built in the recipe out of something other than the rung below it" "are two different ladders"

edit $G '        "values": [f"{NS}:{bee['"'"'id'"'"']}_comb" for bee in BEES] + [f"{NS}:comb"],' '        "values": [f"{NS}:comb"],'
gexpect "combs left out of the tag the centrifuge reads" "are not in the tag the centrifuge reads"

rm -f $M/src/main/resources/data/c/tags/item/raw_materials/gold.json
expect "a chunk with no raw-material tag, so it never reaches a furnace" "end up in two different furnaces"

rm -f $M/src/main/resources/data/elysiumagronomy/recipe/sampling_upgrade.json
expect "an upgrade with no recipe, so a slot is a slot nothing goes in" "so an upgrade slot is a slot nothing goes in"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all agronomy checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | tail -3 | head -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "apiary: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
