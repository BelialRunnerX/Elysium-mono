#!/usr/bin/env bash
# Elysium Flora, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-flora
S=$M/src/main/java/com/elysium/flora
R=$M/src/main/resources
SNAP=/tmp/flora_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-flora)
restore() { rm -rf $ROOT/elysium-flora && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@"; }
jedit() { python3 -c "
import json,sys
exec(sys.argv[2])
" "$1" "$2"; }

echo "independence"
edit $S/ElysiumFlora.java 'import net.neoforged.bus.api.IEventBus;' \
  'import net.neoforged.bus.api.IEventBus;
import com.elysium.core.Elysium;'
expect "a sibling content module reached for" "talks to the library"

edit $S/registry/FloraItems.java 'public static final DeferredRegister.Items ITEMS =' \
  'public static final String FOREIGN = "silentgear:flax_fiber";
    public static final DeferredRegister.Items ITEMS ='
expect "another mod's item named" "belongs to another mod"

edit $R/META-INF/neoforge.mods.toml 'modId="elysiumspace"
    type="optional"' 'modId="elysiumspace"
    type="required"'
expect "an optional world module made required" "needs nothing but the engine"

echo "the slower road"
edit $S/rules/FloraRules.java 'public static final int FIBRE_PER_STRING = 3;' \
                              'public static final int FIBRE_PER_STRING = 1;'
expect "one fibre that is a string" "replaces a spider"

edit $S/rules/FloraRules.java 'public static final int WILD_SEEDS = 2;' \
                              'public static final int WILD_SEEDS = 1;'
expect "a wild plant that cannot start a field" "becomes an errand"

edit $S/rules/FloraRules.java 'return at / (at + BONUS_PACE);' \
                              'return Math.min(1.0F, at / BONUS_PACE);'
expect "a harvest bonus that becomes certain" "became certain at tier"

edit $S/rules/FloraRules.java 'public static final int TIERS_PER_EXTRA = 6;' \
                              'public static final int TIERS_PER_EXTRA = 2;'
expect "a crop that pays out as fast as an ore" "farming replaces mining"

edit $S/rules/FloraRules.java 'public static final int CHARCOAL_TICKS = COAL_TICKS * 3 / 2;' \
                              'public static final int CHARCOAL_TICKS = COAL_TICKS * 3;'
expect "one farmed fuel worth three coal" "nobody mines coal again"

edit $S/rules/FloraRules.java 'return CHARCOAL_TICKS * 9;' 'return CHARCOAL_TICKS * 4;'
expect "a block of charcoal that loses fuel" "not worth the nine that made it"

echo "the plants"
edit $S/block/FibreCropBlock.java 'if (!FloraRules.ripe(age)) {' 'if (false) {'
expect "a seedling that pays like a harvest" "do not check whether it was ripe"

edit $S/block/FibreCropBlock.java 'FloraRules.harvest(tierOf(params), random.nextDouble())' '1'
expect "a yield decided outside the rules" "buys nothing in a field"

edit $S/block/FibreCropBlock.java 'amount = FloraRules.fortuned(amount, fortune, random.nextDouble());' ''
expect "fortune that does nothing in a field" "whatever it does to the seeds"

edit $S/block/WildPlantBlock.java 'FloraRules.WILD_SEEDS' '2'
expect "a wild plant giving a number nobody decided" "nobody decided"

edit $S/registry/FloraBlocks.java 'copy(Blocks.WHEAT).noLootTable()' 'copy(Blocks.WHEAT)'
expect "a crop with a loot table shadowing its own answer" "still has a loot table"

python3 -c "
import json
p='$R/assets/elysiumflora/blockstates/loomflax.json'
d=json.load(open(p)); d['variants'].pop('age=7')
json.dump(d,open(p,'w'),indent=2)"
expect "a crop with the wrong number of stages" "growth stage(s) rather than the eight"

rm -f $R/assets/elysiumflora/textures/block/loomflax_stage3.png
expect "a growing field that is invisible" "no texture"

echo "the wood"
python3 -c "
import json
p='$R/data/minecraft/tags/block/planks.json'
d=json.load(open(p)); d['values']=[]
json.dump(d,open(p,'w'),indent=2)"
expect "planks that are not planks" "every vanilla recipe written against"

python3 -c "
import json
p='$R/data/minecraft/tags/block/mineable/axe.json'
d=json.load(open(p)); d['values']=[v for v in d['values'] if 'fence' not in v]
json.dump(d,open(p,'w'),indent=2)"
expect "wood that behaves like stone" "behaves like stone"

python3 -c "
import json
p='$R/data/elysiumflora/tags/block/emberwood_logs.json'
d=json.load(open(p)); d['values']=d['values'][:2]
json.dump(d,open(p,'w'),indent=2)"
expect "a log form that cannot be stripped or smelted" "silently does nothing"

rm -f $R/data/elysiumflora/loot_table/blocks/emberwood_planks.json
expect "a block that destroys itself when broken" "no loot table"

edit $S/event/FloraHandler.java 'event.setBurnTime(ticks);' ''
expect "a furnace never told how long emberwood burns" "behaves like oak"

edit $S/registry/FloraItems.java 'return FloraRules.CHARCOAL_TICKS;' 'return 1900;'
expect "a burn time nobody decided" "nobody decided"

echo "recipes and where they grow"
rm -f $R/data/elysiumflora/recipe/string_from_downbloom.json
expect "a crop with no purpose" "each needs one"

rm -f $R/data/elysiumflora/recipe/emberwood_charcoal.json
expect "a tree that gives no fuel" "gives no fuel"

python3 -c "
import json
p='$R/data/elysiumflora/neoforge/biome_modifier/wild_crops.json'
d=json.load(open(p)); d['features']=[f for f in d['features'] if 'downbloom' not in f]
json.dump(d,open(p,'w'),indent=2)"
expect "a crop that cannot be started at all" "is in no biome"

python3 -c "
import json
p='$R/data/elysiumflora/neoforge/biome_modifier/emberwood.json'
d=json.load(open(p)); d['step']='underground_ores'
json.dump(d,open(p,'w'),indent=2)"
expect "plants placed before the ground they need" "vegetal_decoration"

python3 -c "
import json
p='$R/data/elysiumflora/worldgen/configured_feature/emberwood.json'
s=json.dumps(json.load(open(p))).replace('elysiumflora:emberwood_leaves','minecraft:oak_leaves')
open(p,'w').write(s)"
expect "a tree that cannot be replanted" "drops no saplings"

python3 -c "
import json
p='$R/assets/elysiumflora/lang/en_us.json'
d=json.load(open(p)); d.pop('block.elysiumflora.emberwood_planks')
json.dump(d,open(p,'w'),indent=2)"
expect "a block with no name" "reads a raw key"

echo
echo "restoring and re-checking"
restore
if (cd $M && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "flora: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
