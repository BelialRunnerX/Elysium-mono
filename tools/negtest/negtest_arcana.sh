#!/usr/bin/env bash
# Turning things into other things, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-arcana
R=$M/src/main/java/com/elysium/arcana/rules/ArcanaRules.java
S=$M/src/main/java/com/elysium/arcana
G=$M/tools/gen_arcana.py
RES=$M/src/main/resources
SNAP=/tmp/arcana_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-arcana)
restore() { rm -rf $ROOT/elysium-arcana && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
expect_regen() { local w="$2" g
  (cd $M && python3 tools/gen_arcana.py >/dev/null 2>&1)
  g="$(out)"
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

echo "the table, which is the only place this can be catastrophically wrong"
edit $R '                    if (priced != null && (cheapest == null || priced < cheapest)) {
                        cheapest = priced;
                    }' '                    if (priced != null && (cheapest == null || priced > cheapest)) {
                        cheapest = priced;
                    }'
expect "a thing priced above what it actually costs" "exactly what a player rides"

edit $R '                if (seeds.containsKey(entry.getKey())) {
                    // A decided value is decided. Deriving over the top of one
                    // is how a seed table stops meaning anything.
                    continue;
                }' '                if (false) {
                    continue;
                }'
expect "a decided value derived over" "stops meaning anything"

edit $R '        return Map.copyOf(closed(recipes, worth, seeds));' '        return Map.copyOf(worth);'
expect "a table shipped without being closed against profit" "need not agree with one another"

edit $R '            if (each == null || each <= 0L) {' '            if (false) {'
expect "an unpriceable ingredient treated as free" "dragon egg at nothing"

edit $R '        long each = total / step.makes();
        if (each <= 0L) {' '        long each = total / step.makes();
        if (false) {'
expect "something worth nothing left in the table" "dissolved for ever"

edit $R '            if (total < 0L) {
                return null;                       // it overflowed; it is not a price
            }' '            if (false) {
                return null;
            }'
expect "a recipe whose ingredients overflow a long" "therefore free"

edit $R '            List<String> rideable = profitable(recipes, table);' '            List<String> rideable = java.util.List.of();'
expect "a closure that asks nothing" "closing against something else"

edit $R '                if (table.remove(step.output()) != null) {
                    removed = true;
                }' '                if (table.containsKey(step.output())) {
                    removed = true;
                }'
expect "a closure that removes nothing" "the loop is decoration"

edit $R '                if (seeds.containsKey(step.output())) {
                    // A decided value stays decided. One bad recipe for
                    // cobblestone must not take cobblestone out of the price
                    // list, because everything else is priced from it.
                    continue;
                }' '                if (false) {
                    continue;
                }'
expect "a bad recipe taking cobblestone out of the price list" "takes cobblestone out of the game"

edit $R '            if (priceable && made * step.makes() > spent) {' '            if (priceable && made > spent) {'
expect "a profit check that is not about what a recipe makes" "checking something else"

edit $R '                if (each == null) {
                    priceable = false;
                    break;
                }' '                if (false) {
                    priceable = false;
                    break;
                }'
expect "an unpriceable ingredient judged as free" "reads as profitable"

edit $R '        worth.put("minecraft:cobblestone", 1L);' '        worth.put("minecraft:cobblestone", 4L);'
expect "a unit nobody can hold" "relative to something nobody can hold"

edit $R '        worth.put("minecraft:diamond", 8_192L);' '        worth.put("minecraft:diamond", 128L);'
expect "iron worth more than diamond" "already has in their head"

echo "the two directions, and the difference between them"
edit $R '    public static final float BURN_SHARE = 0.9F;' '    public static final float BURN_SHARE = 1.0F;'
expect "a lossless round trip" "infinite supply of something"

edit $R '    public static final float BURN_SHARE = 0.9F;' '    public static final float BURN_SHARE = 0.2F;'
expect "a transmuter that is really a bin" "it is a bin"

edit $R '        return (long) Math.floor((double) worth * count * BURN_SHARE);' '        return (long) Math.ceil((double) worth * count * BURN_SHARE);'
expect "rounding that leans the wrong way" "the one direction the arithmetic leans"

edit $R '        return worth * (long) count;' '        return worth * (long) count / 2L;'
expect "condensing that costs less than the rules say" "not the loss the rules state"

edit $R '        if (worth <= 0L || motes <= 0L) {
            return 0;
        }' '        if (false) {
            return 0;
        }'
expect "a balance of nothing affording something" "affords something"

echo "who decides"
edit $S/transmute/Transmuting.java '        if (!ArcanaAttachments.knows(player, id)) {' '        if (false) {'
expect "a packet condensing anything at all" "defeated by one message"

edit $S/transmute/Transmuting.java '        long worth = MoteTable.table(player.level()).getOrDefault(id, 0L);' '        long worth = 1L;'
expect "a price that does not come from this server" "somewhere other than this server"

edit $S/transmute/Transmuting.java '        if (!ArcanaAttachments.spend(player, price)) {' '        if (false) {'
expect "motes that are never taken" "everything is free"

edit $S/transmute/Transmuting.java '        if (held.isDamaged() || !held.getComponentsPatch().isEmpty()) {' '        if (false) {'
expect "a worn pick priced as a fresh one" "enchantment launderer"

edit $S/transmute/Transmuting.java '        held.shrink(taken);' '        ;'
expect "one diamond dissolved for ever" "infinite supply of motes"

edit $S/registry/ArcanaAttachments.java '        if (now < 0L) {
            return false;
        }' '        if (false) {
            return false;
        }'
expect "a balance that can go below nothing" "spend twice"

edit $S/registry/ArcanaAttachments.java '        player.setData(LEARNED.get(), List.copyOf(now));' '        was.add(id);'
expect "a catalogue mutated in place" "silently stops growing"

edit $S/registry/ArcanaAttachments.java '                    .copyOnDeath()
                    .build());

    /** Every item this player has dissolved at least one of. */' '                    .build());

    /** Every item this player has dissolved at least one of. */'
expect "a balance lost on death" "punishment for dying"

edit $S/network/ArcanaPayloads.java '    public record ChooseTransmutation(String key, int count)' '    public record ChooseTransmutation(String key, int count, long price)'
expect "a packet that carries the price" "trusted with the one number"

edit $S/block/MoteTable.java '            worth = ArcanaRules.derive(recipes, ArcanaRules.SEEDS);' '            worth = new java.util.HashMap<>(ArcanaRules.SEEDS);'
expect "a table in the world the harness never proved" "a different table from the one that ships"

edit $S/block/MoteTable.java '        TreeMap<String, String> sorted = new TreeMap<>();' '        java.util.HashMap<String, String> sorted = new java.util.HashMap<>();'
expect "a price table that differs on a different day" "different on a different day"

edit $S/block/MoteTable.java '        if (manager != builtAgainst) {' '        if (true) {'
expect "several thousand recipes derived on every click" "does nothing"

echo "arcana in the world"
rm $RES/assets/elysiumarcana/textures/block/transmuter.png
expect "a transmuter with no texture" "purple cube"

edit $G '        "E": ITEM("minecraft:nether_star")}, "transmuter"),' '        "E": ITEM("elysiumfoundry:starsteel_ingot")}, "transmuter"),'
expect_regen "a transmuter only a full install can build" "cannot build the only block it adds"

edit $S/client/TransmuterScreen.java 'public class TransmuterScreen extends ElysiumChooser {' 'public class TransmuterScreen extends net.minecraft.client.gui.screens.Screen {'
expect "a catalogue that looks like a different mod" "different mod from every other list"

edit $S/client/TransmuterScreen.java 'known.keys().get(index), hasShiftDown() ? 64 : 1));' 'known.keys().get(index), 1));'
expect "sixty-four clicks to fill a stack" "sixty-four clicks"

echo "independence, which for a module that prices everything is the whole design"
edit $S/ElysiumArcana.java '    public static final String MODID = "elysiumarcana";' '    public static final String MODID = "elysiumarcana";
    static final Class<?> UP = com.elysium.industry.rules.IndustryRules.class;'
expect "a module that prices everything depending on something" "must not depend on any of the modules"

edit $M/src/main/resources/META-INF/neoforge.mods.toml '    modId="elysiumlib"' '    modId="projecte"'
expect "a dependency on the mod this module replaces" "must not require any of it"

edit $R 'import java.util.ArrayList;' 'import net.minecraft.world.item.Item;
import java.util.ArrayList;'
expect "rules no harness could price without a game" "without a game"

echo "the seam"
edit $S/ElysiumArcana.java 'MODID = "elysiumarcana"' 'MODID = "elysium_arcana"'
expect "a module id nothing else in the tree spells that way" "everything else in the tree spells"

edit $S/ElysiumArcana.java '        com.elysium.arcana.registry.ArcanaAttachments.ATTACHMENTS.register(modEventBus);' '        '
expect "a ledger written and never registered" "none of it exists"

echo "the first shard: a geode of every material, in a world a player walks to"
W=$S/rules/GeodeWorldRules.java
G2=$M/tools/gen_geodes.py
DATA=$RES/data/elysiumarcana
expect_geodes() { local w="$2" g
  (cd $M && python3 tools/gen_geodes.py >/dev/null 2>&1)
  g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }

edit $W '            new Find("platinum", OVERWORLD_BIOMES, THE_GAMES_BAND),' ''
expect "a material with no geode in any world" "no platinum geode in any world"

edit $W '            if (found == 0) {' '            if (false) {'
expect "the rule that finds a material with no geode" "a material with no geode in any world is not reported"

edit $W '            "minecraft:the_nether", List.of("#minecraft:is_nether"),' '            "elysiumspace:cinderveil", List.of("#minecraft:is_nether"),'
expect "geodes only a rocket reaches" "waits on a rocket"

edit $W '            if (worldOf(find, onFoot) == null) {' '            if (false) {'
expect "the rule that keeps a geode out of space" "a geode only a rocket reaches is not reported"

edit $W '    public static final Band ON_THE_FLOOR = new Band(8, 24);' '    public static final Band ON_THE_FLOOR = new Band(2, 24);'
expect "a geode drawn from the bedrock floor" "part of it is in the bedrock"

edit $W '            if (find.band().lowest() < world.bottom() + margin) {' '            if (false) {'
expect "the rule that keeps a geode off the floor" "drawn from the bedrock floor is not reported"

edit $W '    public static final int CHUNKS_PER_SHARD = 8;' '    public static final int CHUNKS_PER_SHARD = 2;'
expect "a geode commoner than the game's own" "commoner than the game's own amethyst geode"

edit $W '            } else if (chance < floor) {' '            } else if (false) {'
expect "the rule that keeps a geode rarer than the game's own" "commoner than the game's own amethyst geode is not reported"

edit $W '    public static final int CHUNKS_PER_SHARD = 8;' '    public static final int CHUNKS_PER_SHARD = 3;'
expect "a world with a geode round every corner" "a crystal wall round every corner is not a find"

edit $W '            if (world.getValue() > most) {' '            if (false) {'
expect "the rule that keeps a find a find" "a geode round every corner is not reported"

edit $W '            List.of(OVERWORLD_BIOMES, MOUNTAIN_BIOMES, NETHER_BIOMES);' '            List.of(OVERWORLD_BIOMES, NETHER_BIOMES);'
expect "a geode under a ground no biome modifier is written for" "it is placed nowhere"

edit $W '            if (!grounds.contains(find.ground())) {' '            if (false) {'
expect "the rule that finds a geode placed nowhere" "no biome modifier is written for is not reported"

edit $W '            "minecraft:blackstone", "minecraft:basalt");' '            "minecraft:blackstone", "elysiumarcana:iron_block");'
expect "geodes wrapped in another material's blocks" "not what the geode is of"

edit $W '                if (!block.startsWith("minecraft:")) {' '                if (false) {'
expect "the rule that keeps a rind the game's rock" "wrapped in this module's own blocks is not reported"

edit $W '    public static final double MINED_BUDDING = 0.04;' '    public static final double MINED_BUDDING = 0.2;'
expect "a mined geode budding more than a given one" "the dearer seeding is the one found for nothing more often"

edit $W '        if (mined > given) {' '        if (false) {'
expect "the rule that buds the dearer seedings less" "budding more than a given one is not reported"

edit $W '        bad.addAll(faultsInWorlds(FINDS, ON_FOOT));' '        '
expect "a rule about the geodes never added up" "faultsInWorlds is written, compiled and never added up"

edit $W '        bad.addAll(faultsInRinds(GROUNDS));' '        if (false) bad.addAll(faultsInRinds(GROUNDS));'
expect "a rule about the geodes added up only sometimes" "faults() has a condition in it"

edit $M/tools/ArcanaHarness.java '        theFinds();' '        '
expect "a harness that never asks where the geodes are" "the harness never asks where the geodes are"

edit $S/block/GeodeClusterBlock.java '    public static final DirectionProperty FACING = BlockStateProperties.FACING;' '    public static final DirectionProperty FACING = DirectionProperty.create("facing");'
expect "crystals the geode feature cannot turn" "off walls and ceilings as well"

echo "what the geode files on disk say"
edit $G2 "                \"inner_layer_provider\": provider(f\"{NS}:{block_id(material, 'block')}\")," "                \"inner_layer_provider\": provider(f\"{NS}:coal_block\"),"
expect_geodes "geodes walled in coal whatever they are of" "so its wall is not its own material"

edit $G2 "                    provider(f\"{NS}:{block_id(material, 'budding')}\")," '                    provider("minecraft:budding_amethyst"),'
expect_geodes "geodes budding the game's amethyst" "so its wall buds something other than its own crystals"

edit $G2 '                    for step in CHAIN],' '                    for step in CHAIN[:-1]],'
expect_geodes "geodes that never grow a cluster" "where its own four steps are"

edit $G2 '             "chance": PER_SHARD * priced["shards"] if priced else 0},' '             "chance": 24},'
expect_geodes "geodes as common as the game's own, whatever their price" "how rare it is and how dear seeding it is have come apart"

edit $G2 '                        "min_inclusive": {"absolute": lowest},' '                        "min_inclusive": {"above_bottom": 0},'
expect_geodes "geodes drawn from outside their band" "where its row's band is"

edit $G2 '                MINED_SHARE if material in MINED else GIVEN_SHARE,' '                GIVEN_SHARE,'
expect_geodes "mined geodes budding at the given share" "budding, where GeodeWorldRules says"

edit $G2 '            "step": STEP,' '            "step": "underground_ores",'
expect_geodes "geodes placed at another step" "the step the game places its own geode in"

edit $G2 '            "biomes": f"#{NS}:{name}",' '            "biomes": "#minecraft:is_overworld",'
expect_geodes "nether geodes under the overworld's biomes" "ground its row names"

python3 - "$DATA/neoforge/biome_modifier/geodes_nether.json" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["features"].remove("elysiumarcana:bort_geode")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "a geode no biome modifier places" "no biome modifier places the bort geode"

rm -f $DATA/tags/worldgen/biome/geodes_mountains.json
expect "a biome modifier naming a tag nobody ships" "is not a tag this module ships"

rm -f $DATA/worldgen/placed_feature/uraninite_geode.json
expect "a geode with no placement" "there is no placed feature for the uraninite geode"

cp $DATA/worldgen/placed_feature/iron_geode.json $DATA/worldgen/placed_feature/allthemodium_geode.json
expect "a geode of a material this module does not ship" "a geode of a material this module does not ship"

echo "what mines a geode, and a storage block is one way"
GR=$S/rules/GeodeRules.java
GB=$S/registry/GeodeBlocks.java
T=$RES/data/minecraft/tags/block/mineable/pickaxe.json

edit $G '            for material in materials for kind in kinds]' '            for material in materials for kind in kinds if kind != "budding"]'
expect_regen "budding blocks that ask for a pickaxe and are in no tool's tag" "ask for the right tool and are in no tool's tag"

edit $GR '            List.of(BLOCK, BUDDING, SMALL, MEDIUM, LARGE, CLUSTER);' '            List.of(BUDDING, SMALL, MEDIUM, LARGE, CLUSTER);'
expect "a storage block nothing says how to mine" "block is in no tool's tag and not broken by hand either"

edit $GR '            if (!mined && !touched) {' '            if (false) {'
expect "the rule that finds a kind nothing says how to mine" "a kind no tool is decided for is not reported"

python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].remove("elysiumarcana:iron_cluster")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "a crystal in no tool's tag" "are in no mineable tag and not exempt"

python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].append("elysiumarcana:iron_bloc")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "a pickaxe tag naming a block nobody registers" "so the game refuses the whole tag"

python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].append("elysiumarcana:iron_tinted_glass")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "glass exempt from the tags and in one all the same" "are exempt from the mineable tags and in one all the same"

edit $GB '        return BlockBehaviour.Properties.ofFullCopy(Blocks.TINTED_GLASS)' '        return BlockBehaviour.Properties.ofFullCopy(Blocks.TINTED_GLASS).requiresCorrectToolForDrops()'
expect "exempt glass that asks for the right tool" "it says so where it is registered"

edit $GB '        return BlockBehaviour.Properties.ofFullCopy(Blocks.TINTED_GLASS)' '        return BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK)'
expect "exempt glass built on a block that asks for the right tool" "it copies Blocks.AMETHYST_BLOCK, which does"

edit $GB '        return BlockBehaviour.Properties.ofFullCopy(Blocks.TINTED_GLASS)' '        return BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)'
expect "a block built on something nobody has looked up" "nothing here knows whether that asks for the right tool"

edit $GR '            List.of(BLOCK, BUDDING, SMALL, MEDIUM, LARGE, CLUSTER);' '            List.of(BLOCK, BUDDING, SMALL, MEDIUM, LARGE);'
edit $GR '    public static final List<String> BY_HAND = List.of(GLASS);' '    public static final List<String> BY_HAND = List.of(GLASS, CLUSTER);'
expect_regen "clusters broken by hand for no stated reason" "breaks cluster by hand and this gives no reason"

edit $GR '    public static final List<String> BY_HAND = List.of(GLASS);' '    public static final List<String> BY_HAND = List.of();'
expect "a reason kept for an exemption the rules dropped" "GeodeRules no longer does"

edit $G2 '        write(os.path.join(DATA, "recipe", f"{material}_spyglass.json"), {' '        write(os.path.join(DATA, "recipe", f"{material}_shard_from_block.json"), {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [{"item": f"{NS}:{material}_block"}], "result": {"id": f"{NS}:{material}_shard", "count": 4}}); write(os.path.join(DATA, "recipe", f"{material}_spyglass.json"), {'
expect_geodes "the way back from a storage block to four shards" "and a storage block is one way"

python3 - "$DATA/recipe/iron_shard_from_glass.json" <<'PYX'
import json, sys
recipe = {"type": "minecraft:smelting", "category": "misc",
          "ingredient": {"item": "elysiumarcana:iron_tinted_glass"},
          "result": {"id": "elysiumarcana:iron_shard"}, "experience": 0.1,
          "cookingtime": 200}
open(sys.argv[1], "w", encoding="utf-8", newline="\n").write(json.dumps(recipe, indent=2) + "\n")
PYX
expect "a shard made by a recipe rather than grown" "a shard comes only from a cluster"

edit $G2 '            "key": {"S": {"item": f"{NS}:{material}_shard"}},' '            "key": {"S": {"item": f"{NS}:coal_shard"}},'
expect_geodes "storage blocks made of coal whatever they are of" "is not made of four of its own shards"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all arcana checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "arcana: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
