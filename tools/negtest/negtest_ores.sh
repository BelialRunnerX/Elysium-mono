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
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }
regen() { (cd $M && python3 tools/gen_ores.py >/dev/null) || { echo "GENERATOR BROKE"; exit 2; }; }

echo "naming another mod"
edit $S/ore/OreDrops.java 'private static final Map<String, Item> RESOLVED =' \
  'public static final String LEAD = "mekanism:ingot_lead";
    private static final Map<String, Item> RESOLVED ='
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
edit $S/rules/UnifyRules.java 'List.of("raw_materials", "gems", "vanilla", "dusts", "ingots")' \
                              'List.of("ingots", "raw_materials", "gems", "vanilla", "dusts")'
expect "an ore that hands over a finished ingot" "hand a player a finished ingot"

edit $S/rules/UnifyRules.java 'String chosen = found == null ? null : canonical(found);' \
     'String chosen = found == null || found.isEmpty() ? null : found.iterator().next();'
expect "an ore that drops a seventh lead" "seventh lead"

edit $S/rules/UnifyRules.java 'List.of("raw_materials", "gems", "vanilla", "dusts", "ingots")' \
                              'List.of("raw_materials", "gems", "dusts", "vanilla", "ingots")'
expect "a coal ore that drops coal dust (the order)" "before the game's own item"

edit $S/rules/UnifyRules.java 'if (vanilla != null && !vanilla.isEmpty()) {' 'if (false) {'
expect "a drop that never reaches the game's own item (harness)" "came before the game's own item"

edit $S/ore/OreDrops.java 'String chosen = UnifyRules.drop(members, fallback == null ? null : vanilla);' \
                          'String chosen = UnifyRules.canonical(members.get("raw_materials"));'
expect "an ore that decides its drop around the rules" "the game plays another"

edit $S/ore/OreDrops.java 'String key = family + "|" + (vanilla == null ? "" : vanilla);' 'String key = family;'
expect "one remembered drop for the gate and the ore" "remembers one answer per family"

echo "the metal modules in the priority"
edit $S/rules/UnifyRules.java '            "elysiumfoundry", "elysiumatomics",
' ''
expect "the foundry and atomics unlisted" "wins its metals' families"

edit $S/rules/UnifyRules.java '            "elysiumfoundry", "elysiumatomics",
            "mekanism",' '            "mekanism",'
edit $S/rules/UnifyRules.java '"mysticalagradditions");' '"mysticalagradditions", "elysiumfoundry", "elysiumatomics");'
expect "the foundry and atomics below every other mod" "is ranked below another mod"

echo "a gem's raw chunk"
rm -f $R/data/elysiumores/recipe/ruby_from_raw.json
expect "a ruby chunk nothing smelts" "cannot be smelted from its raw chunk"

edit $R/data/elysiumores/recipe/quartz_from_raw_blasting.json '"id": "minecraft:quartz"' '"id": "elysiumores:quartz"'
expect "a quartz chunk blasted into something else" "is not a blasting of"

echo "the stones a retired mod filled"
edit $M/tools/gen_ores.py '        if not shipped(block):
            continue
' ''
regen
expect "the scan asked of every mod it ever saw" "left to a mod the pack no longer ships"

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

echo "what mines them (the harvest audit, 29 Sep 2026)"
HR=$S/rules/HarvestRules.java
MB=$S/registry/MaterialBlocks.java
T=$R/data/minecraft/tags/block/mineable/pickaxe.json

python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].remove("elysiumores:cobalt_block")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "a metal block in no tool's tag" "ask for the right tool and are in no tool's tag"

edit $HR '            if (said == 0) {' '            if (false) {'
expect "the rule that finds a block no tool is decided for, disabled" "a block no tool is decided for is not reported"

edit $MB '            return BlockBehaviour.Properties.of().sound(SoundType.WOOL).strength(0.8F);' \
        '            return BlockBehaviour.Properties.of().sound(SoundType.WOOL).strength(0.8F).requiresCorrectToolForDrops();'
expect "the exempt cloth block asking for the right tool" "but it asks for the right tool: it says so where it is registered"

edit $MB '            return BlockBehaviour.Properties.of().sound(SoundType.WOOL).strength(0.8F);' \
        '            return BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).strength(0.8F);'
expect "the cloth block built on a game block nobody has looked up" "nothing here knows whether that asks for the right tool"

python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].append("elysiumores:mireweave_bloc")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "a pickaxe tag naming a block nobody registers" "so the game refuses the whole tag"

python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].append("elysiumores:mireweave_block")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "the cloth block exempt from the tags and in one all the same" "is exempt from the mineable tags and in one all the same"

edit $HR '    public static final List<String> BY_HAND_KINDS = List.of("CLOTH");' \
        '    public static final List<String> BY_HAND_KINDS = List.of("CLOTH", "SHARD");'
expect "shard blocks broken by hand for no stated reason" "breaks every SHARD block by hand and this gives no reason"

edit $HR '    public static final List<String> BY_HAND_KINDS = List.of("CLOTH");' \
        '    public static final List<String> BY_HAND_KINDS = List.of();'
expect "a reason kept for an exemption the rules dropped" "HarvestRules no longer does"

edit $HR '    public static final List<String> PICKAXE_KINDS = List.of("METAL", "GEM", "ALLOY", "SHARD");' \
        '    public static final List<String> PICKAXE_KINDS = List.of("METAL", "GEM", "ALLOY");'
expect "a kind of block the rules do not decide a tool for" "a kind HarvestRules does not say what mines"

edit $HR '    public static final String ORE_TOOL = "pickaxe";' '    public static final String ORE_TOOL = "axe";'
expect "the rules moved every ore and the tag left where it was" "the tags are written from the rules by tools/gen_ores.py"

edit $HR 'import java.util.Map;' 'import java.util.Map;
import net.minecraft.world.level.block.Block;'
expect "the rules reaching for the game" "reaches for net.minecraft"

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
