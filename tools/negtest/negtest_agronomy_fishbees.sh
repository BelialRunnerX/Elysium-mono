#!/usr/bin/env bash
# Elysium Agronomy: fishing and bees in the world.
#
# Rods that cast their tackle, a catch drawn from the proved table, lava and
# the void, the worm farm and the tackle box, fish in the water, bees in their
# biomes and nests, and every comb off a bee or out of a hive of its own.
# Each case breaks one thing, one way, and expects validate.py to say so.
set -uo pipefail
ROOT="${ELYSIUM_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
# Windows Python wants a drive path; Git Bash gives /c/...
PYROOT="$ROOT"
if command -v cygpath >/dev/null 2>&1; then PYROOT="$(cygpath -m "$ROOT")"; fi
M=$ROOT/elysium-agronomy
P=$PYROOT/elysium-agronomy
S=$P/src/main/java/com/elysium/agronomy
D=$M/src/main/resources/data/elysiumagronomy
DP=$P/src/main/resources/data/elysiumagronomy
L=$P/src/main/resources/assets/elysiumagronomy/lang/en_us.json
SNAP="${TMPDIR:-/tmp}/agronomy_fishbees_snap.tar"
pass=0; fail=0
(cd "$ROOT" && tar cf "$SNAP" elysium-agronomy)
restore() { rm -rf "$ROOT/elysium-agronomy" && (cd "$ROOT" && tar xf "$SNAP"); }
out() { (cd "$M" && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8',newline='\n').write(s.replace(old, new, 1))
" "$@"; }

echo "the rods"
edit $S/registry/FishItems.java 'new com.elysium.agronomy.item.ElysiumRodItem(held,' 'new Item('
expect "rods registered as plain items" "casts nothing"

edit $S/item/ElysiumRodItem.java 'AgronomyComponents.tackleOn(rod), AgronomyComponents.baitOn(rod)));' \
                                 'TackleRules.NONE, FishRules.NOTHING));'
expect "a rod casting a float that forgets its tackle" "fishes like a bare line"

edit $S/item/ElysiumRodItem.java 'rod.set(AgronomyComponents.HOOK.get(), id);' ''
expect "a hook used up and never fitted" "changes nothing"

edit $S/item/ElysiumRodItem.java 'int taken = Math.min(part.getCount(), WaterRules.MOST_BAIT_ON_ROD - count);' \
                                 'int taken = part.getCount();'
expect "a rod carrying bait without limit" "as much bait as a player owns"

echo "the catch"
edit $S/event/FishingEvents.java '@SubscribeEvent
    public static void onFished(ItemFishedEvent event) {' 'public static void onFished(ItemFishedEvent event) {'
expect "nothing listening for a catch" "nothing listens"

edit $S/event/FishingEvents.java 'rung = ours.rung();' 'rung = 0;'
expect "every rod fishing as the first rung" "the rod's rung"

edit $S/event/Casts.java 'List<FishRules.Chance> table = TackleRules.table(where, rod, bait, tackle);' \
                         'List<FishRules.Chance> table = FishRules.table(where, rod, bait);'
expect "a cast drawn from a table the tackle never touched" "somewhere the harness never looked"

edit $S/event/Casts.java 'caught < tackle.hook().catches();' 'caught < 1;'
expect "a double hook bringing up one fish" "brings up one"

edit $S/rules/TackleRules.java 'int luck = Math.min(FishRules.RODS - 1, rod + tackle.hook().luck());' \
                               'int luck = Math.max(0, FishRules.RODS - 1 - rod) + tackle.hook().luck();'
expect "a better rod that is a worse rod" "harder to get than rung"

echo "lava and the void"
edit $S/entity/ElysiumHook.java 'List<ItemStack> drops = Casts.pick(server, judgedAt(), rung, tackle, bait, random);' \
                                'List<ItemStack> drops = java.util.List.of();'
expect "a float in lava that catches nothing" "unobtainable on a line"

edit $S/entity/ElysiumHook.java 'surface = lava.getY() + fluid.getHeight(level(), lava);' \
                                'surface = lava.getY() - 1.0D;'
expect "a float that sinks in lava" "sinks"

edit $S/entity/ElysiumHook.java 'write(LURED, WaterRules.waitFor(lured, tackle.bobber()));' ''
expect "a bobber that shortens nothing" "is a tooltip"

echo "the worm farm and the tackle box"
edit $S/block/WormFarmBlock.java 'if (family.isEmpty() || held >= TackleRules.WORM_FARM_HOLDS) {' \
                                 'if (family.isEmpty()) {'
expect "a worm farm with no cap" "warehouse of bait"

edit $S/rules/TackleRules.java 'public static final int WORM_TICKS = 1_200;' \
                               'public static final int WORM_TICKS = 20;'
expect "a worm farm over the bait ceiling" "bait an hour and the"

edit $S/registry/AgronomyBlocks.java 'return new com.elysium.agronomy.block.WormFarmBlock(timber().noOcclusion());' \
                                     'return new Block(timber());'
expect "a worm farm that is a plain block" "makes no bait"

edit $S/block/TackleBoxBlock.java 'return container.canPlaceItem(getContainerSlot(), stack);' 'return true;'
expect "a tackle box that takes anything" "chest with a fish on it"

echo "fish in the water"
rm -f "$D/neoforge/biome_modifier/fish_spawn_river.json"
expect "river fish never in the water" "river fish are never in the water"

python3 -c "
import json
p='$DP/neoforge/biome_modifier/fish_spawn_ocean.json'
d=json.load(open(p)); d['spawners'][-1]['weight']=64
json.dump(d,open(p,'w'),indent=2)"
expect "a rare fish spawning as often as a common one" "not the rarer sight"

edit $S/rules/WaterRules.java 'haunt(FishRules.CAVE, "#c:is_cave");' 'haunt(FishRules.CAVE, "#c:is_underground");'
expect "a haunt changed and the spawns not regenerated" "cave fish spawn in"

edit $S/rules/WaterRules.java 'haunt(FishRules.NETHER, "#minecraft:is_nether");' \
                              'haunt(FishRules.NETHER, "#minecraft:is_nether");
        haunt(FishRules.END, "#minecraft:is_end");'
expect "the void family given water to swim in" "rod-only family given a haunt"

edit $S/registry/FishEntities.java '(type, level, reason, pos, random) -> Casts.suits(one, level, pos),' \
                                   '(type, level, reason, pos, random) -> true,'
expect "a fish with no spawn placement of its own" "never finds anywhere to put it"

edit $S/entity/FishEntity.java 'setAirSupply(getMaxAirSupply());' ''
expect "a lava fish that drowns in lava" "drowns in lava"

echo "bees in the world"
rm -f "$D/neoforge/biome_modifier/bee_spawn_meadow.json"
expect "a wild bee that never spawns" "is wild and never spawns"

cp "$D/neoforge/biome_modifier/bee_spawn_meadow.json" "$D/neoforge/biome_modifier/bee_spawn_iron.json"
expect "a bred bee spawning wild" "is bred and also spawns wild"

rm -f "$D/neoforge/biome_modifier/plains_nest.json"
expect "a nest generated nowhere" "plains_nest is generated nowhere"

edit $S/rules/KeepingRules.java 'biome("plains", "#c:is_plains");' 'biome("plains", "#minecraft:is_overworld");'
expect "a home's biome changed and the spawns not regenerated" "the meadow bee spawns in"

edit $S/rules/KeepingRules.java 'biome("swamp", "#c:is_swamp");' ''
expect "a home no biome means" "no biome means it"

edit $S/rules/KeepingRules.java '|| (parents.get(0).equals(second) && parents.get(1).equals(first))' ''
expect "breeding that depends on which jar is in which hand" "which hand"

edit $S/rules/KeepingRules.java 'if (!biomeOf(bee.home()).isEmpty() && wildAt(bee.home()) != null) {' \
                                'if (bee.id().equals("meadow")) {'
expect "three wild bees no jar can reach" "no jar can ever hold"

edit $S/rules/KeepingRules.java 'public static final int SMOKE_TICKS = 1_200;' \
                                'public static final int SMOKE_TICKS = 200;'
expect "smoke that wears off before the anger" "before the smoke does"

echo "working bees"
edit $S/item/BeeScoopItem.java 'if (!bee.harvest()) {' 'if (false) {'
expect "a scoop that never asks the bee" "harvest has no caller"

edit $S/item/BeeScoopItem.java 'ItemStack comb = new ItemStack(BeeItems.COMBS.get(bee.species()).get());' \
                               'ItemStack comb = new ItemStack(com.elysium.agronomy.registry.AgronomyItems.COMB.get());'
expect "a scoop that gives the plain comb" "centrifuge's outputs are unreachable"

edit $S/block/HiveBlock.java 'ItemStack comb = combOf();' 'ItemStack comb = new ItemStack(AgronomyItems.COMB.get());'
expect "a hive that makes the plain comb whatever lives in it" "none of the roster's"

edit $S/block/HiveBlock.java 'String child = KeepingRules.childOf(here, mate);' 'String child = null;'
expect "a treat that breeds nothing" "does not breed the pair"

edit $S/block/HiveBlock.java 'player.displayClientMessage(hive.offerJar(held), true);' ''
expect "a jar used on a hive doing nothing" "a jar used on a hive does nothing"

edit $S/item/BeeSmokerItem.java 'int calmed = Keeping.smoke(server, player.blockPosition());' 'int calmed = 0;'
expect "a smoker that calms nothing" "the smoker calms nothing"

edit $S/entity/BeeEntity.java 'if (calm() || by.getAbilities().instabuild) {' 'if (by.getAbilities().instabuild) {'
expect "a smoked bee upset anyway" "the smoker buys nothing"

edit $S/registry/BeeBlocks.java '() -> new com.elysium.agronomy.block.NestBlock(nesting(), home)' '() -> new Block(nesting())'
expect "nests as plain blocks" "nests are plain blocks"

edit $S/block/NestBlock.java 'level.scheduleTick(pos, this, KeepingRules.NEST_REFILL_TICKS);
        Keeping.robbed(level, pos, player);' 'Keeping.robbed(level, pos, player);'
expect "a nest that never grows a bee back" "once one is taken"

edit $S/block/NestBlock.java 'if (now + 1 < KeepingRules.NEST_BEES) {
            level.scheduleTick(pos, this, KeepingRules.NEST_REFILL_TICKS);
        }' ''
expect "a nest that grows back one bee and stops" "past the first"

edit $S/registry/BeeItems.java 'new com.elysium.agronomy.item.BeeJarItem(' 'new Item('
expect "a jar that is a plain item" "the bee_jar is a plain item"

python3 -c "
import json
p='$L'
d=json.load(open(p,encoding='utf-8')); d.pop('elysiumagronomy.rod.hook')
json.dump(d,open(p,'w',encoding='utf-8'),indent=2)"
expect "a rod tooltip with no words" "have no words"

echo
echo "restoring and re-checking"
restore
if (cd "$M" && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
rm -f "$SNAP"
echo "agronomy (fishing and bees in the world): $pass caught, $fail missed"
[ "$fail" -eq 0 ]
