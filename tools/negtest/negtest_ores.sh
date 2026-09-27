#!/usr/bin/env bash
# Elysium Ores, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-ores
S=$M/src/main/java/com/elysium/ores
R=$M/src/main/resources
SNAP=/tmp/ores_snap.tar
pass=0; fail=0

(cd $ROOT && tar cf $SNAP elysium-ores)

restore() { rm -rf $ROOT/elysium-ores && (cd $ROOT && tar xf $SNAP); }

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

echo "naming another mod"
edit $S/ore/OreDrops.java 'public static final List<String> KINDS =' \
  'public static final String LEAD = "mekanism:ingot_lead";
    public static final List<String> KINDS ='
expect "another mod's item id written into Java" "a material is a tag here"

edit $S/ElysiumOres.java 'import net.neoforged.bus.api.IEventBus;' \
  'import net.neoforged.bus.api.IEventBus;
import mekanism.common.MekanismItems;'
expect "another mod imported" "names no other mod in Java"

edit $R/META-INF/neoforge.mods.toml 'modId="elysiumspace"
    type="optional"' 'modId="elysiumspace"
    type="required"'
expect "an optional host made required" "must load beside"

edit $S/data/OreCatalogue.java '"minecraft:coal"' '"mekanism:ingot_lead"'
expect "a fallback that is not vanilla's" "the only ids it may fall back on"

echo "the guard"
edit $S/rules/UnifyRules.java 'if (ambiguousIds.contains(id)) {
                continue;
            }' ''
expect "the guard consulted by nobody" "the guard exists"

edit $S/rules/UnifyRules.java 'return familiesContaining.size() > 1;' 'return false;'
expect "a guard that never fires (harness)" "would be convertible into steel"

edit $S/unify/OreUnification.java 'UnifyRules.ambiguous(new HashSet<>(entry.getValue()))' 'false'
expect "the map that never asks the guard" "never reaches the items it protects"

edit $S/unify/OreUnification.java 'if (!UnifyRules.worthUnifying(ids)) {
                    continue;
                }' ''
expect "every material unified, including the ones with one supplier" "a conversion recipe for every material"

edit $S/loot/UnifyingModifier.java 'OreUnification.unify(stack)' 'new ItemStack(stack.getItem(), stack.getCount())'
expect "loot rewritten around the map" "the guard does not apply to loot"

edit $S/unify/OreUnification.java 'ItemStack out = new ItemStack(want, stack.getCount());' \
                                  'ItemStack out = new ItemStack(want, 1);'
expect "a unified stack that loses its count" "cost the player all but one"

echo "additive only"
edit $S/unify/UnifyRecipes.java 'all.add(existing);' '{ }'
expect "the existing recipes not copied across" "lose every recipe it had"

edit $S/unify/UnifyRecipes.java 'for (RecipeHolder<?> existing : recipes.getRecipes()) {' \
                                'for (RecipeHolder<?> existing : List.<RecipeHolder<?>>of()) {'
expect "an injector that never reads what is there" "delete every other mod"

edit $S/unify/UnifyRecipes.java '|| !existing.id().getPath().startsWith(PREFIX)' ''
expect "an injector that cannot recognise its own work" "accumulates two copies"

edit $S/event/OreUnifyHandler.java '    @SubscribeEvent
    public static void onTags' '    @SubscribeEvent
    public static void onCrafted(net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
    }

    @SubscribeEvent
    public static void onTags'
expect "a crafted result swapped out from under the player" "eats materials"

echo "the arithmetic"
edit $S/rules/OreRules.java 'return at / (at + BONUS_PACE);' 'return Math.min(1.0F, at / BONUS_PACE);'
expect "a bonus chance that becomes certain" "became certain at tier"

edit $S/rules/OreRules.java 'public static int bonus(int worldTier) {
        return Math.max(0, worldTier) / TIERS_PER_EXTRA;' \
     'public static int bonus(int worldTier) {
        return Math.min(3, Math.max(0, worldTier) / TIERS_PER_EXTRA);'
expect "a bonus that stops climbing" "stopped climbing"

edit $S/rules/OreRules.java 'if (extra <= 0) {
            return 1;
        }' 'if (extra <= 0) {
            return 0;
        }'
expect "a block of ore that yields nothing" "yielded nothing at"

edit $S/rules/UnifyRules.java 'return a.compareTo(b) < 0;' 'return false;'
expect "a canonical that depends on the order it was found in" "whichever loaded first"

edit $S/rules/UnifyRules.java '            "minecraft",
' ''
expect "vanilla not first" "not the iron ingot"

edit $S/rules/OreRules.java 'public final class OreRules {' \
  'public final class OreRules {
    static final Class<?> REACH = com.elysium.lib.tier.ElysiumTiers.class;'
expect "rules that reach outside the JDK" "the JDK and nothing else"

echo "the matrix"
rm -f $R/assets/elysiumores/textures/block/cinder_lead_ore.png
expect "an ore with no texture" "no texture"

rm -f $R/data/elysiumores/worldgen/placed_feature/cinder_lead_ore.json
expect "an ore with no placement" "no placed feature"

python3 -c "
import json
p='$R/data/elysiumores/worldgen/placed_feature/cinder_lead_ore.json'
d=json.load(open(p)); d['placement']=[s for s in d['placement'] if 'family_present' not in str(s.get('type'))]
json.dump(d,open(p,'w'),indent=2)"
expect "an ore that generates whether the material exists or not" "does not begin with the family gate"

edit $R/data/elysiumores/worldgen/placed_feature/cinder_lead_ore.json \
  '"family": "lead"' '"family": "gold"'
expect "an ore gated on somebody else's material" "gates on gold"

edit $R/data/elysiumores/worldgen/configured_feature/cinder_lead_ore.json \
  '"Name": "elysiumores:cinder_lead_ore"' '"Name": "minecraft:stone"'
expect "a feature that places something other than itself" "places minecraft:stone rather than itself"

python3 -c "
import json
p='$R/data/elysiumores/neoforge/biome_modifier/ores_nether.json'
d=json.load(open(p)); d['features']=[f for f in d['features'] if 'lead' not in f]
json.dump(d,open(p,'w'),indent=2)"
expect "an ore in no world at all" "is in no world"

python3 -c "
import json
p='$R/data/elysiumores/neoforge/biome_modifier/ores_nether.json'
d=json.load(open(p)); d['step']='top_layer_modification'
json.dump(d,open(p,'w'),indent=2)"
expect "ores added while the terrain is still being built" "underground_ores"

python3 -c "
import json
p='$R/data/minecraft/tags/block/needs_diamond_tool.json'
d=json.load(open(p)); d['values'].append('elysiumores:cinder_lead_ore')
json.dump(d,open(p,'w'),indent=2)"
expect "an ore in two tool tags at once" "tool tag(s)"

python3 -c "
import json
p='$R/assets/elysiumores/lang/en_us.json'
d=json.load(open(p)); d.pop('block.elysiumores.cinder_lead_ore')
json.dump(d,open(p,'w'),indent=2)"
expect "an ore with no name" "reads a raw key"

echo "the family tags"
python3 -c "
import json
p='$R/data/c/tags/item/ingots/steel.json'
d=json.load(open(p)); d['values']=[v['id'] if isinstance(v,dict) else v for v in d['values']]
json.dump(d,open(p,'w'),indent=2)"
expect "a tag entry that is not optional" "has to be optional"

python3 -c "
import json
p='$R/data/c/tags/item/ingots/steel.json'
d=json.load(open(p)); d['replace']=True
json.dump(d,open(p,'w'),indent=2)"
expect "a tag that replaces every other mod's entries" "thrown away"

python3 -c "
import json
p='$R/data/c/tags/block/ores/lead.json'
d=json.load(open(p)); d['values']=[v for v in d['values'] if not (isinstance(v,str) and 'cinder' in v)]
json.dump(d,open(p,'w'),indent=2)"
expect "this module's own ore left out of its family" "ores"

echo "the drop"
edit $S/ore/OreDrops.java 'List.of("raw_materials", "gems", "dusts", "ingots")' \
                          'List.of("ingots", "raw_materials", "gems", "dusts")'
expect "an ore that hands over a finished ingot" "hand a player a finished ingot"

edit $S/ore/OreDrops.java 'UnifyRules.canonical(ids)' 'ids.isEmpty() ? null : ids.get(0)'
expect "an ore that drops a seventh lead" "seventh lead"

edit $S/ore/ElysiumOreBlock.java 'if (tool != null && silked(tool, params)) {' 'if (false) {'
expect "an ore that ignores silk touch" "ignores silk touch"

edit $S/ore/ElysiumOreBlock.java 'OreRules.yield(tierOf(params), random.nextDouble())' '1'
expect "a yield decided outside the rules" "buys nothing at the rock face"

edit $S/ore/ElysiumOreBlock.java 'OreRules.fortuned(amount, fortune(tool, params), random.nextDouble())' 'amount'
expect "fortune that does nothing" "fortune does nothing"

edit $S/place/FamilyPresent.java 'return OreDrops.present(family, vanilla.isEmpty() ? null : vanilla);' 'return true;'
expect "a gate that gates on nothing" "gates on nothing"

echo "the loot modifier"
python3 -c "
import json
p='$R/data/neoforge/loot_modifiers/global_loot_modifiers.json'
d=json.load(open(p)); d['entries']=[]
json.dump(d,open(p,'w'),indent=2)"
expect "a loot modifier that is shipped and never runs" "never runs"

python3 -c "
import json
p='$R/data/neoforge/loot_modifiers/global_loot_modifiers.json'
d=json.load(open(p)); d['replace']=True
json.dump(d,open(p,'w'),indent=2)"
expect "every other mod's loot modifiers switched off" "switched off"

edit $S/registry/OreLoot.java 'UnifyingModifier.CODEC' 'null'
expect "a loot modifier naming a serializer nobody registered" "nobody registered"

echo
echo "restoring and re-checking"
restore
if (cd $M && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "ores: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
