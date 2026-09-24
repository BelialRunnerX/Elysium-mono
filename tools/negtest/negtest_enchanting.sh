#!/usr/bin/env bash
# P10 — the Arcane Table, broken one way at a time.
#
# Every check the enchanting validator and its harness make is deliberately
# defeated here, and the suite fails if the break is not noticed. A check
# nobody has seen fail is a check nobody has seen.
#
# The tree is snapshotted whole at the start and restored whole at the end,
# rather than per file: one bad anchor in a per-file restore corrupts
# everything after it, and that has happened.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-enchanting
J=$M/src/main/java/com/elysium/enchanting
RES=$M/src/main/resources
LANG=$RES/assets/elysiumenchanting/lang/en_us.json
GEN=$M/tools/gen_enchanting.py
SNAP=/tmp/negtest_enchanting_snapshot.tgz
pass=0; fail=0

tar czf $SNAP -C /tmp Elysium-mono/elysium-enchanting Elysium-mono/art

out() { (cd $ROOT && python3 elysium-enchanting/validate.py 2>&1); }

expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }

edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@"; }

# Extracting alone would leave behind any file a break *added*, so the two
# directories are removed first: a restore that only overwrites is a restore
# that quietly keeps the damage.
restore() { rm -rf $M $ROOT/art; tar xzf $SNAP -C /tmp; }

# Some breaks are to the table rather than to a shipped file, and the shipped
# files are what the validator reads. Those breaks regenerate.
regen() { (cd $ROOT && python3 elysium-enchanting/tools/gen_enchanting.py >/dev/null 2>&1); }

echo "the module on its own"
edit $J/ElysiumEnchanting.java 'import net.neoforged.bus.api.IEventBus;' \
                               'import shadowsoffire.apotheosis.Apotheosis;
import net.neoforged.bus.api.IEventBus;'
expect "a reach for the mod this replaces" "refers to 'shadowsoffire'"
restore

edit $J/ElysiumEnchanting.java 'import net.neoforged.bus.api.IEventBus;' \
                               'import com.elysium.core.Elysium;
import net.neoforged.bus.api.IEventBus;'
expect "a dependency on another Elysium module" "nothing else from Elysium"
restore

edit $RES/META-INF/neoforge.mods.toml '[[dependencies.${mod_id}]]' \
     '[[dependencies.${mod_id}]]
modId="apothic_enchanting"
type="required"
versionRange="[0,)"
ordering="NONE"
side="BOTH"

[[dependencies.${mod_id}]]'
expect "a declared dependency on Apothic Enchanting" "declares a dependency on"
restore

echo "the arithmetic, run rather than read"
edit $J/rules/EnchantingRules.java 'import java.util.function.DoubleSupplier;' \
                                   'import net.minecraft.world.item.ItemStack;
import java.util.function.DoubleSupplier;'
expect "a game class in the pure rules" "imports nothing but the JDK"
restore

mv $M/tools/EnchantingHarness.java /tmp/eh.away
expect "the arithmetic with nothing to run it" "only read, never run"
restore
rm -f /tmp/eh.away

edit $J/rules/EnchantingRules.java 'eterna = Math.max(0.0F, eterna);' 'eterna = eterna;'
expect "a negative eterna from a datapack (validator)" "clamps eterna on the way in"
expect "a negative eterna from a datapack (harness)" "went backwards"
restore

edit $J/rules/EnchantingRules.java 'Math.max(0.0F, Math.min(1.0F, rectification))' \
                                   'Math.max(0.0F, rectification)'
expect "rectification past one" "held to one"
restore

echo "the two caps"
edit $J/rules/EnchantingRules.java \
  'return Math.min(Math.max(0.0F, roomCap), Math.max(0.0F, playerMax));' \
  'return Math.max(Math.max(0.0F, roomCap), Math.max(0.0F, playerMax));'
expect "a table holding the larger of the two caps (validator)" "does not take the smaller"
expect "a table holding the larger of the two caps (harness)" "was carried anyway"
restore

edit $J/data/EnchantingSurvey.java 'EnchantingRules.ceiling(roomCap, playerMax)' \
                                   'Math.min(roomCap, playerMax)'
expect "a survey with a ceiling rule of its own" "works out its own ceiling"
restore

edit $J/data/EnchantingSurvey.java 'float roomCap = 0.0F;' \
                                   'float roomCap = EnchantingRules.BASE_MAX_ETERNA;'
expect "a table in an empty room that works anyway" "does not start at nothing"
restore

edit $J/data/EnchantingSurvey.java 'roomCap = Math.max(roomCap, shelf.maxEterna());' \
                                   'roomCap = roomCap + shelf.maxEterna();'
expect "a cap that is one more bookshelf" "best single cap"
restore

edit $J/data/EnchantingSurvey.java \
  'float playerMax = EnchantingRules.BASE_MAX_ETERNA;
        if (player != null && player.getAttribute(EnchantingAttributes.MAX_ETERNA) != null) {
            playerMax = (float) player.getAttributeValue(EnchantingAttributes.MAX_ETERNA);
        }' \
  'float playerMax = EnchantingRules.BASE_MAX_ETERNA;'
expect "a table that never asks who is standing at it" "never asks the player what they can hold"
restore

echo "reading the room"
edit $J/data/EnchantingSurvey.java 'if (shelf == null || !visible(level, table, x, y, z)) {' \
                                   'if (shelf == null) {'
expect "shelves counted through a wall" "can see a shelf"
restore

edit $J/data/EnchantingSurvey.java 'if (Math.abs(x) < 2 && Math.abs(z) < 2) {' \
                                   'if (false) {'
expect "a table with nowhere to stand" "cannot stand at their own table"
restore

edit $J/data/EnchantingSurvey.java 'public static final int RADIUS = 3;' \
                                   'public static final int RADIUS = 1;'
expect "a room smaller than the one everybody builds" "smaller than vanilla's"
restore

edit $J/data/EnchantingSurvey.java 'public static final int RADIUS = 3;' \
                                   'public static final int RADIUS = 40;'
expect "a table reading the next building" "further than a room"
restore

edit $J/data/EnchantingSurvey.java 'public static final int HEIGHT = 2;' \
                                   'public static final int HEIGHT = 0;'
expect "a wall counted as a floor" "no longer looks above the table"
restore

edit $J/data/EnchantingSurvey.java 'total = total.plus(shelf.stats());' ''
expect "shelves worth nothing once they are found" "does not add up what the shelves are worth"
restore

echo "uncertainty and rarity"
edit $J/rules/EnchantingRules.java 'roll *= 1.0D - stats.rectification();' ''
expect "rectification that removes nothing (validator)" "no longer reads rectification"
expect "rectification that removes nothing (harness)" "did not remove the downside"
restore

edit $J/rules/EnchantingRules.java 'if (roll < 0.0D) {' 'if (roll > 0.0D) {'
expect "rectification removing the upside instead" "removed the upside"
restore

edit $J/rules/EnchantingRules.java 'return at / (at + ARCANA_PACE);' \
                                   'return Math.min(1.0D, at / ARCANA_PACE);'
expect "arcana that becomes certain (validator)" "is not x/(x+b)"
expect "arcana that becomes certain (harness)" "reached certainty"
restore

echo "the shelves"
edit $RES/data/elysiumenchanting/elysium_enchanting_stats/bookshelf.json '"eterna": 2.0' \
                                                                        '"eterna": 9.0'
expect "a shipped stat file edited by hand" "edited by hand"
restore

edit $GEN '("minecraft:bell", 0.0, 0.0, 0.0, 0.04, 0.0,
     "something that is the same note every time"),
    ("minecraft:sea_lantern", 1.0, 0.0, 0.0, 0.02, 0.0,
     "steady light, which is rarer than it sounds"),
    ("minecraft:conduit", 0.0, 1.0, 0.0, 0.08, 55.0,
     "it keeps its own weather"),
    ("minecraft:beacon", 1.0, 0.0, 0.0, 0.10, 60.0,
     "a straight line, held, for as long as you feed it"),
    ("minecraft:lodestone", 0.0, 0.0, 0.0, 0.06, 30.0,
     "the one block in the game whose whole job is not being wrong"),
    ("minecraft:calibrated_sculk_sensor", 0.0, 0.0, 0.5, 0.05, 0.0,
     "the same listening, told what to listen for"),' ''
edit $GEN '("minecraft:netherite_block", 1.0, 0.0, 0.0, 0.05, 50.0,' \
          '("minecraft:netherite_block", 1.0, 0.0, 0.0, 0.0, 50.0,'
expect "a number almost nothing in the game answers" "so a room built for it is not a"
restore

edit $J/rules/EnchantingRules.java 'public static final float BASE_MAX_ETERNA = 50.0F;' \
                                   'public static final float BASE_MAX_ETERNA = 100.0F;'
expect "an attribute no room can ever be worth raising" "can never be the thing"
restore

edit $GEN '("minecraft:magma_block", 0.0, 0.75, 0.0, 0.0, 0.0,' \
          '("minecraft:magma_block", 0.0, 0.0, 0.0, 0.0, 0.0,'
expect "a shelf worth nothing at all" "worth nothing at all"
restore

edit $GEN '("minecraft:bookshelf", 2.0, 0.0, 0.0, 0.0, 15.0,
     "the wall everybody already builds"),
    ("minecraft:chiseled_bookshelf", 1.0, 0.75, 0.0, 0.0, 15.0,
     "a shelf you can take books out of, and it reads as unsettled"),
    ("minecraft:lectern", 1.5, 0.0, 0.5, 0.0, 18.0,
     "one book, open, which is worth more than six closed"),
    ("minecraft:cartography_table", 0.5, 0.0, 0.75, 0.0, 15.0,
     "somewhere to work out where the rare thing is"),' ''
expect "a first hour with no room in it" "there is no first room"
restore

edit $GEN '("minecraft:bookshelf", 2.0' '("elysium:bookshelf", 2.0'
expect "a shelf from a mod that may not be installed" "neither a vanilla block nor one"
restore

edit $GEN '("minecraft:lectern", 1.5' '("minecraft:bookshelf", 1.5'
expect "two rows naming one block" "name the same block"
restore

edit $LANG '"elysiumenchanting.shelf.bookshelf"' '"elysiumenchanting.shelf.shelf"'
expect "a shelf with no reason given" "ships without a sentence"
restore

edit $J/data/EnchantingStatBlocks.java '"elysium_enchanting_stats"' '"elysium_shelves"'
expect "a loader looking where the files are not" "does not read the directory"
restore

edit $J/data/EnchantingStatBlocks.java 'extends SimpleJsonResourceReloadListener' \
                                       'extends Object'
expect "stat blocks that need a restart" "takes a restart"
restore

echo "the offers"
edit $J/menu/ArcaneTableMenu.java 'offers.set(slot, roll(offered, stats, target, seed + slot));' \
                                  'offers.set(slot, roll(offered, stats, target, seed));'
expect "three offers rolled from one seed" "are not seeded"
restore

edit $J/menu/ArcaneTableMenu.java 'long seed = (long) target.getDescriptionId().hashCode() * 31L' \
                                  'long seed = level.random.nextLong() * 31L'
expect "offers that reroll every time the screen opens" "world's random"
restore

edit $J/menu/ArcaneTableMenu.java 'EnchantingSurvey.survey(level, pos, owner)' \
                                  'EnchantingRules.Stats.NONE'
expect "a table that never looks at the room" "no longer reads the room"
restore

edit $J/menu/ArcaneTableMenu.java '!target.isEnchantable() || ' ''
expect "an offer on something that takes no enchantment" "cannot take one"
restore

edit $J/menu/ArcaneTableMenu.java 'int rolled = EnchantingRules.rolledLevel(stats, EnchantingRules.offerLevel(stats, slot),
                    level.random::nextDouble);' \
                                  'int rolled = EnchantingRules.offerLevel(stats, slot);'
expect "quanta that never has its say" "uncertainty is never applied"
restore

edit $J/menu/ArcaneTableMenu.java 'player.onEnchantmentPerformed(target, cost);' ''
expect "an enchantment that costs no experience" "costs no experience"
restore

edit $J/menu/ArcaneTableMenu.java 'inputs.removeItem(1, cost);' ''
expect "an enchantment that costs no lapis" "costs no lapis"
restore

edit $J/menu/ArcaneTableMenu.java 'if (!player.getAbilities().instabuild) {
                player.onEnchantmentPerformed(target, cost);' 'if (true) {
                player.onEnchantmentPerformed(target, cost);'
expect "creative mode charged for an enchantment" "creative mode is charged"
restore

edit $J/menu/ArcaneTableMenu.java 'target.enchant(instance.enchantment, instance.level);' ''
expect "a purchase that enchants nothing" "does not enchant anything"
restore

edit $J/menu/ArcaneTableMenu.java 'int shown = Math.min(all.size(), EnchantingRules.clues(current(), slot));' \
                                  'int shown = all.size();'
expect "a screen that decides its own clues" "decides for itself"
restore

edit $J/menu/ArcaneTableMenu.java 'HolderSet<Enchantment> allowed = owner.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(EnchantmentTags.IN_ENCHANTING_TABLE)
                .map(named -> (HolderSet<Enchantment>) named)
                .orElse(HolderSet.direct());' \
                                  'HolderSet<Enchantment> allowed = HolderSet.direct();'
expect "a table with its own list of enchantments" "rather than the game's"
restore

edit $J/menu/ArcaneTableMenu.java 'if (EnchantingRules.available(stats, instance.enchantment.value().getWeight())) {' \
                                  'if (true) {'
expect "arcana that takes nothing off the table" "never removes anything"
restore

echo "the block, and getting hold of one"
edit $J/block/ArcaneTableBlock.java 'public class ArcaneTableBlock extends Block {' \
                                    'public class ArcaneTableBlock extends net.minecraft.world.level.block.BaseEntityBlock {'
expect "a table that is no longer a plain block" "in its own right"
restore

edit $J/block/ArcaneTableBlock.java 'import net.minecraft.world.level.block.Block;' \
                                    'import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;'
edit $J/block/ArcaneTableBlock.java 'public ArcaneTableBlock() {' \
                                    'public static final Block REPLACED = Blocks.ENCHANTING_TABLE;

    public ArcaneTableBlock() {'
expect "a reach for vanilla's own table" "reaches for vanilla's enchanting table"
restore

mv $RES/assets/elysiumenchanting/blockstates/arcane_table.json /tmp/bs.away
expect "a block with no blockstate" "has no blockstate"
restore
rm -f /tmp/bs.away

mv $RES/data/elysiumenchanting/recipe/arcane_table.json /tmp/rc.away
expect "a block nobody can craft" "has no recipe"
restore
rm -f /tmp/rc.away

mv $RES/data/elysiumenchanting/loot_table/blocks/arcane_table.json /tmp/lt.away
expect "a block that drops nothing" "has no loot table"
restore
rm -f /tmp/lt.away

mv $RES/assets/elysiumenchanting/textures/block/arcane_table.png /tmp/tx.away
expect "a block with no texture" "has no texture"
restore
rm -f /tmp/tx.away

edit $RES/data/minecraft/tags/block/mineable/pickaxe.json '"elysiumenchanting:arcane_table"' \
                                                          '"elysiumenchanting:not_the_table"'
expect "a block no tool is ever correct for" "no tool is ever correct"
restore

edit $LANG '"block.elysiumenchanting.arcane_table"' '"block.elysiumenchanting.table"'
expect "a block with no name" "has no name"
restore

edit $J/registry/EnchantingBlocks.java 'TABS.register(modBus);' ''
expect "a creative tab built and never registered" "never registered"
restore

edit $J/registry/EnchantingBlocks.java 'TAB_ORDER.add(item);
        return item;' 'return item;'
expect "an item registered but never put in the tab" "the one place that was meant to do both"
restore

edit $LANG '"itemGroup.elysiumenchanting"' '"itemGroup.enchanting"'
expect "a tab with no name" "creative tab has no name"
restore

echo "the attribute"
edit $J/registry/EnchantingAttributes.java 'ATTRIBUTES.register("max_eterna"' \
                                           'ATTRIBUTES.register("eterna_cap"'
expect "the one attribute, registered under another name" "max_eterna is not registered"
restore

edit $J/registry/EnchantingAttributes.java '.setSyncable(true)' '.setSyncable(false)'
expect "a cap the client never hears about" "not synced to the client"
restore

edit $J/registry/EnchantingAttributes.java 'Double.MAX_VALUE' '100.0D'
expect "a capped cap" "has a ceiling of its own"
restore

edit $LANG '"attribute.name.elysiumenchanting.max_eterna"' '"attribute.name.elysiumenchanting.eterna"'
expect "an attribute shown as a raw key" "nothing translates it"
restore

edit $LANG '"elysiumenchanting.stat.arcana"' '"elysiumenchanting.stat.arcane"'
expect "one of the four numbers with no label" "elysiumenchanting.stat.arcana and nothing translates it"
restore

echo "the ladder"
EN=$RES/data/minecraft/enchantment
edit $EN/sharpness.json '"max_level": 10' '"max_level": 4'
expect "a shipped enchantment edited by hand" "a shipped file was edited by hand"
restore

edit $EN/sharpness.json '"per_level_above_first": 0.5' '"per_level_above_first": 5.0'
expect "an enchantment quietly made to do more" "changed from vanilla's"
restore

edit $GEN '("sharpness", 10,' '("sharpness", 5,'
regen
expect "a row that re-paces nothing" "no further than vanilla"
restore

edit $GEN '    ("smite", 10, "the same ladder, for one kind of thing"),' \
          '    ("silk_touch", 3, "rungs on a statement"),
    ("smite", 10, "the same ladder, for one kind of thing"),'
regen
expect "rungs on something that has no amount" "statement rather than an amount"
restore

edit $GEN 'TOP_POWER = 45' 'TOP_POWER = 200'
regen
expect "a top rung nobody can reach" "nobody has ever stood on"
restore

edit $GEN 'entry["min_cost"]["base"]
                                     + max(band, step + BAND_OVER_STEP),' \
          'entry["min_cost"]["base"] + 1,'
regen
expect "a band narrower than the step" "reach no level of it at all"
restore

edit $EN/sharpness.json '"max_cost": {
    "base": 21,
    "per_level_above_first": 5
  }' '"max_cost": {
    "base": 21,
    "per_level_above_first": 0
  }'
expect "two cost curves climbing at different rates" "different rates"
restore

edit $EN/swift_sneak.json '"min_cost": {
    "base": 25,' '"min_cost": {
    "base": 40,'
expect "a first rung that is not a first rung" "is not a first rung"
restore

cp $M/tools/vanilla_enchantments/mending.json $EN/mending.json
expect "a vanilla enchantment overridden without being asked for" "never asked to change"
restore

mv $EN/sharpness.json /tmp/sh.away
expect "a row with no file behind it" "and no file ships"
restore
rm -f /tmp/sh.away

edit $RES/data/minecraft/tags/enchantment/in_enchanting_table.json '"replace": false' \
                                                                   '"replace": true'
expect "every other mod's enchantments taken off the table" "every other mod"
restore

edit $RES/data/minecraft/tags/enchantment/in_enchanting_table.json \
     '"minecraft:mending",' ''
expect "treasure promised and not delivered" "is not in the tag"
restore

edit $GEN 'FROM_THE_TABLE = ["mending",' 'FROM_THE_TABLE = ["efficiency", "mending",'
expect "treasure with an ordinary weight" "treasure has to be gated by arcana"
restore

edit $GEN '    ("smite", 10, "the same ladder, for one kind of thing"),' \
          '    ("sharpness", 8, "a second row for one enchantment"),
    ("smite", 10, "the same ladder, for one kind of thing"),'
expect "one enchantment listed twice" "listed twice in the ladder"
restore

echo "the new enchantments"
OE=$RES/data/elysiumenchanting/enchantment
ET=$RES/data/minecraft/tags/enchantment

edit $OE/ambush.json '"max_level": 5' '"max_level": 2'
expect "one of ours edited by hand" "different level or weight"
restore

edit $OE/ambush.json '"base": 1.5' '"base": 15.0'
expect "an enchantment quietly made stronger than its table says" "different effects than the table"
restore

edit $OE/ambush.json '"minecraft:damage": [' '"minecraft:damage_dealt": ['
expect "an effect component the game does not have" "does not have: the file will not parse"
restore

edit $OE/ambush.json '"type": "minecraft:add"' '"type": "minecraft:plus"'
expect "an effect type the game does not have" "which the game does not"
restore

edit $OE/ambush.json '"#minecraft:enchantable/sharp_weapon"' '"#minecraft:enchantable/sharp"'
expect "an enchantment that may go on nothing" "may go on nothing"
restore

edit $OE/ambush.json '"translate": "enchantment.elysiumenchanting.ambush"' \
                     '"translate": "enchantment.elysiumenchanting.sneak"'
expect "an enchantment naming somebody else's description" "does not name its own description key"
restore

edit $LANG '"enchantment.elysiumenchanting.ambush":' '"enchantment.elysiumenchanting.ambushed":'
expect "an enchantment with no name" "ships without a name"
restore

edit $LANG '"enchantment.elysiumenchanting.ambush.desc"' '"enchantment.elysiumenchanting.ambush.note"'
expect "an enchantment with nothing said about it" "saying what it is for"
restore

edit $OE/ambush.json '"min_cost": {
    "base": 5,
    "per_level_above_first": 10' '"min_cost": {
    "base": 5,
    "per_level_above_first": 20'
expect "a rung of ours nobody can reach" "nobody has ever stood on"
restore

edit $OE/ambush.json '"min_cost": {
    "base": 5,' '"min_cost": {
    "base": 7,'
expect "one of ours re-paced by hand" "re-paced by hand"
restore

edit $GEN '        5, 5, 2, 5, ["mainhand"], WEAPON,
        {"minecraft:damage": [{"effect": add(linear(1.5, 1.5)),
                               "requirements": flags("attacker", is_sneaking=True)}]},' \
          '        5, 10, 2, 5, ["mainhand"], WEAPON,
        {"minecraft:damage": [{"effect": add(linear(1.5, 1.5)),
                               "requirements": flags("attacker", is_sneaking=True)}]},'
regen
expect "a new enchantment every bookshelf room hands out" "puts it on every table"
restore

edit $GEN 'exclusive=f"#{NS}:exclusive_set/stance"),
    new("charge"' 'exclusive=f"#{NS}:exclusive_set/stances"),
    new("charge"'
regen
expect "an exclusive set that does not exist" "exclusive with nothing"
restore

edit $GEN '"stance": ["ambush", "charge"],' '"stance": ["charge"],'
regen
expect "an enchantment excluding others and not itself" "excludes others and not itself"
restore

edit $ET/in_enchanting_table.json '"elysiumenchanting:ambush",' ''
expect "an enchantment no table will ever offer" "can never be offered at a table"
restore

mv $ET/on_random_loot.json /tmp/rl.away
expect "enchantments no chest will ever hold" "found in a chest"
restore
rm -f /tmp/rl.away

mv $OE/ambush.json /tmp/am.away
expect "a declared enchantment with no file" "declared and no file ships"
restore
rm -f /tmp/am.away

cp $OE/ambush.json $OE/zzz_stray.json
expect "a file the table never asked for" "is not in the table"
restore

edit $GEN '    new("charge", "Charge",' '    new("ambush", "Ambush", "a second row", 3, 2, 2, 5,
        ["mainhand"], WEAPON, {"minecraft:damage": [{"effect": add(linear(1.0, 1.0))}]}),
    new("charge", "Charge",'
expect "two of ours with one name" "the same name"
restore

edit $GEN '    new("venom", "Venom",
        "it keeps working after you have stopped",
        3, 2, 4, 8, ["mainhand"], WEAPON,
        {"minecraft:post_attack": [
            {"affected": "victim", "enchanted": "attacker",
             "effect": mob_effect("minecraft:poison", 2.0, 1.0),
             "requirements": direct()}]},
        primary=SWORD),
    new("siphon", "Siphon",
        "what you take, you keep",
        4, 2, 4, 12, ["mainhand"], WEAPON,
        {"minecraft:post_attack": [
            {"affected": "attacker", "enchanted": "attacker",
             "effect": mob_effect("minecraft:regeneration", 2.0, 1.0),
             "requirements": direct()}]},
        primary=SWORD),
    new("hoarfrost", "Hoarfrost",
        "nothing gets away twice",
        4, 3, 4, 10, ["mainhand"], WEAPON,
        {"minecraft:post_attack": [
            {"affected": "victim", "enchanted": "attacker",
             "effect": mob_effect("minecraft:slowness", 2.0, 1.0, amplifier=1),
             "requirements": direct()}]},
        primary=SWORD),' ''
regen
expect "a phase delivered short" "are missing rather than declined"
restore

echo "infusion"
IR=$J/rules/InfusionRules.java
IM=$J/menu/InfusionMenu.java

mv $IR /tmp/ir.away
expect "the certain path with no arithmetic" "certain path is either missing"
restore
rm -f /tmp/ir.away

edit $IR 'return EnchantingRules.available(stats, weight);' 'return true;'
expect "a rarity gate of its own" "route straight past the one number"
restore

edit $IR 'public static final float ETERNA_PER_LEVEL = 8.0F;' \
         'public static final float ETERNA_PER_LEVEL = 1.0F;'
expect "certainty that reaches as high as the gamble (harness)" "no reason left to gamble"
restore

edit $IR 'public static final float DISCOUNT = 0.4F;' 'public static final float DISCOUNT = 0.0F;'
expect "rectification that does nothing on this bench" "took nothing off the price"
restore

edit $IR 'return Math.max(MINIMUM_COST, Math.round(paid));' 'return Math.max(0, Math.round(paid));'
expect "an infusion that can be free" "fell through the floor"
restore

edit $IR 'Math.max(1, enchantmentMax)' '99'
expect "a level past what the enchantment's own file allows" "past what its own file allows"
restore

edit $IM 'EnchantingSurvey.survey(level, pos, owner);
            readout.set(ETERNA' 'EnchantingRules.Stats.NONE;
            readout.set(ETERNA'
expect "a basin that never reads the room" "does not read the room"
restore

edit $IM 'if (!enchantment.value().canEnchant(gear)) {' 'if (false) {'
expect "Respiration on a shovel" "may go on the item at all"
restore

edit $IM 'if (!InfusionRules.allows(stats, enchantment.value().getWeight())) {' 'if (false) {'
expect "a basin that never asks how strange the room is" "strange enough"
restore

edit $IM 'if (!worn.equals(enchantment) && !Enchantment.areCompatible(worn, enchantment)) {' \
         'if (false) {'
expect "Silk Touch and Fortune on one pickaxe" "argues with what is already"
restore

edit $IM 'enchantment.value().getMaxLevel());' '99);'
expect "a basin outranking an enchantment's own file" "own maximum"
restore

edit $IM 'int wanted = InfusionRules.levelFor(entry.getIntValue(), stats.eterna(),' \
         'int wanted = Math.min(entry.getIntValue(), 1 + (int) (stats.eterna() / 8.0F)) + 0 * ('
expect "a basin with its own idea of how high a book lands" "decides for itself how high"
restore

edit $IM 'Plan plan = plan(stats);
            if (plan.status() != READY) {' 'Plan plan = new Plan(List.of(), readout.get(COST),
                    readout.get(LAPIS), readout.get(STATUS));
            if (plan.status() != READY) {'
expect "a purchase priced by the client" "A client can be told anything"
restore

edit $IM 'inputs.removeItem(1, 1);' ''
expect "a book that survives being used" "book survives being infused"
restore

edit $IM 'player.onEnchantmentPerformed(gear, plan.cost());' ''
expect "an infusion that costs no experience" "costs no experience"
restore

edit $IM 'inputs.removeItem(2, plan.lapis());' ''
expect "an infusion that costs no lapis" "costs no lapis"
restore

edit $IM 'if (plan.status() != READY) {
                return;
            }' ''
expect "a button that acts whatever the bench said" "whatever the bench said"
restore

edit $J/client/InfusionScreen.java '"elysiumenchanting.infusion.too_rare"' \
                                   '"elysiumenchanting.infusion.nothing"'
expect "a refusal a player is never shown" "refuse for a reason it never shows"
restore

edit $J/client/EnchantingClient.java 'event.register(EnchantingMenus.INFUSION_BASIN.get(), InfusionScreen::new);' ''
expect "a menu nothing can draw" "no screen registered for it"
restore

echo "the library"
LR=$J/rules/LibraryRules.java
SE=$J/block/ScriptoriumBlockEntity.java
SM=$J/menu/ScriptoriumMenu.java

mv $LR /tmp/lr.away
expect "a library with no arithmetic" "no LibraryRules"
restore
rm -f /tmp/lr.away

edit $LR 'public static final int COPY_TAX = 2;' 'public static final int COPY_TAX = 1;'
expect "a library that duplicates books (validator)" "library duplicates books"
expect "a library that duplicates books (harness)" "costs no more than filing one"
restore

edit $LR 'long value = 1L << (held - 1);' 'long value = held;'
expect "a worth that no longer doubles (validator)" "no longer doubles"
expect "a worth that no longer doubles (harness)" "not worth twice the one below"
restore

edit $LR 'public static final int GRAND_CAPACITY = 65536;' \
         'public static final int GRAND_CAPACITY = 1024;'
expect "a great library that holds no more" "holds no more than the small one"
restore

edit $LR 'if (!room(stored, level, capacity)) {
            return stored;
        }' ''
expect "a shelf that eats what it has no room for" "eats what it"
restore

edit $LR 'return Math.max(1, EXPERIENCE_PER_LEVEL * Math.max(1, level));' 'return 0;'
expect "a shelf that is a vending machine" "copy was free"
restore

edit $LR 'if (cost(level) <= stored) {' 'if (cost(level) <= stored + 1) {'
expect "a shelf one page short buying anyway" "one page short of a level bought it anyway"
restore

edit $SE 'tag.put("shelf", filed);' ''
expect "a shelf that is never written down" "lost on the next restart"
restore

edit $SE 'public CompoundTag getUpdateTag(HolderLookup.Provider registries) {' \
         'public CompoundTag notTheUpdateTag(HolderLookup.Provider registries) {'
expect "a shelf the client never sees" "never sent to the client"
restore

edit $SE 'scriptorium.size().capacity' 'LibraryRules.SHELF_CAPACITY'
expect "two sizes that hold the same" "recipe with nothing at the end of it"
restore

edit $SM 'if (id.isEmpty()
                    || !LibraryRules.room(shelf.pagesOf(id), entry.getIntValue(),
                    shelf.capacity())) {
                return;
            }' ''
expect "a book filed into a shelf with no room" "asking whether it has room"
restore

edit $SM 'books.removeItem(0, 1);
    }

    private void buy' 'books.removeItem(0, 0);
    }

    private void buy'
expect "a book that files itself forever" "does not consume it"
restore

edit $SM 'int best = LibraryRules.highest(shelf.pagesOf(chosen), maxLevel(level, chosen));' \
         'int best = Math.min(5, shelf.pagesOf(chosen));'
expect "a shelf with its own idea of what it will buy" "decides for itself what a shelf will buy"
restore

edit $SM 'player.giveExperienceLevels(-LibraryRules.experience(best));' ''
expect "a copy that costs no experience" "copy costs no experience"
restore

edit $SM 'if (enchantment.isEmpty() || !shelf.take(chosen, best)) {' \
         'if (enchantment.isEmpty()) {'
expect "a copy handed over unpaid for" "without the pages being spent"
restore

edit $J/registry/EnchantingBlockEntities.java 'EnchantingBlocks.GRAND_SCRIPTORIUM.get()' \
                                              'EnchantingBlocks.SCRIPTORIUM.get()'
expect "a block its own block entity does not accept" "throws rather than opening"
restore

echo "the made shelves"
edit $GEN '    ("hellshelf", "obsidian", "plasma", 3, 2.5, 1.5, 0.0, 0.0, 25.0,' \
          '    ("hellshelf", "obsidian", "plasma", 3, 0.0, 0.0, 0.0, 0.0, 0.0,'
regen
expect "a made shelf worth nothing" "worth nothing at all"
restore

edit $J/registry/EnchantingBlocks.java 'shelf("hellshelf", MapColor.NETHER, 7)' \
                                       'shelf("firehelf", MapColor.NETHER, 7)'
expect "a block the generator has never heard of" "never heard of it"
restore

edit $RES/data/minecraft/tags/block/mineable/axe.json '"elysiumenchanting:hellshelf",' ''
expect "a shelf no axe will break" "gives an axe nothing to do"
restore

echo "the anvil and the tomes"
AN=$J/event/EnchantingAnvil.java
AR=$J/rules/AnvilRules.java

mv $AR /tmp/ar.away
expect "an anvil with no arithmetic" "the anvil is untouched"
restore
rm -f /tmp/ar.away

edit $AN 'if (!AnvilRules.refusedByVanilla(cost)) {
            // Vanilla is willing to do this one. Leave it alone: an anvil that
            // behaves differently in the ordinary case is a bug report nobody
            // can reproduce.
            return;
        }' ''
expect "a handler that owns every anvil operation" "only the ones"
restore

edit $AN 'if (!worn.equals(enchantment) && !Enchantment.areCompatible(worn, enchantment)) {
                    clashes = true;' 'if (false) {
                    clashes = true;'
expect "incompatible enchantments on one item" "no anvil in the game has ever done"
restore

edit $AN 'if (!enchantment.value().canEnchant(output)) {
                continue;
            }' ''
expect "an anvil putting Respiration on a shovel" "cannot take them"
restore

edit $AN 'int landed = AnvilRules.merged(merged.getLevel(enchantment), entry.getIntValue(),
                    enchantment.value().getMaxLevel());' \
          'int landed = merged.getLevel(enchantment) + entry.getIntValue();'
expect "an anvil past the enchantment's own maximum" "ignores the enchantment"
restore

edit $AN 'output.set(DataComponents.REPAIR_COST,
                AnvilRules.nextPriorWork(priorWork(left), priorWork(right)));' ''
expect "an item that never ages" "does not age"
restore

edit $AN 'enchantment.value().definition().anvilCost()' '2'
expect "every enchantment priced the same" "priced the same"
restore

edit $AR 'public static int priorWorkCost(int priorWork) {
        int held = Math.max(0, Math.min(PRIOR_WORK_LIMIT, priorWork));' \
          'public static int priorWorkCost(int priorWork) {
        int held = Math.max(0, Math.min(29, priorWork));'
expect "a prior-work penalty that runs away (harness)" "runs away past the limit"
restore

edit $AR 'int combined = onto == from ? best + 1 : best;' 'int combined = best;'
expect "two equal enchantments that do not combine (harness)" "one higher"
restore

edit $AN 'output.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);' ''
expect "a scrapping tome that scraps nothing" "leaves enchantments on the item"
restore

edit $AN 'book.set(DataComponents.STORED_ENCHANTMENTS, on);' ''
expect "a transcription tome that writes nothing" "does not write the enchantments"
restore

edit $AN 'if (!left.is(Items.ENCHANTED_BOOK) || on.size() < 2) {' \
         'if (!left.is(Items.ENCHANTED_BOOK)) {'
expect "a concentration tome spent on nothing" "nothing to narrow"
restore

edit $AN 'output.set(DataComponents.REPAIR_COST, AnvilRules.forgiven());' \
         'output.set(DataComponents.REPAIR_COST, 1);'
expect "a patience tome that leaves some history" "a number of its own"
restore

edit $AN 'int history = priorWork(left);
        if (history <= 0) {
            return false;
        }' 'int history = priorWork(left);'
expect "a patience tome spent on a fresh item" "no history to clear"
restore

edit $AN 'event.setMaterialCost(1);
    }

    // ---' 'event.setMaterialCost(0);
    }

    // ---'
expect "a tome that is never consumed" "does every job in the world forever"
restore

edit $J/registry/EnchantingItems.java 'public static final DeferredItem<Item> PATIENCE_TOME = tome("patience_tome");' \
                                      'public static final DeferredItem<Item> SPARE_TOME = tome("spare_tome");'
expect "an item with nothing drawn for it" "has no item model"
restore

edit $LANG '"elysiumenchanting.tooltip.patience_tome"' '"elysiumenchanting.tooltip.patience"'
expect "a tome that says nothing about itself" "says nothing about itself"
restore

echo "the client, and the tiers"
TT=$J/client/EnchantingTooltips.java

mv $TT /tmp/tt.away
expect "a mod that says nothing on a tooltip" "twenty names"
restore
rm -f /tmp/tt.away

edit $TT 'if (id != null && I18n.exists(key)) {' 'if (id != null) {'
expect "a raw key under every block in the game" "asking whether anything translates"
restore

edit $TT 'if (!net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            return;
        }' ''
expect "a tooltip a player cannot see past" "cannot see past"
restore

edit $TT 'value = Dist.CLIENT)' 'value = Dist.DEDICATED_SERVER)'
expect "a server reading the language file" "not client-only"
restore

edit $J/data/EnchantingSurvey.java 'playerMax += EnchantingRules.tierBonus(
                    com.elysium.lib.tier.ElysiumTiers.of(player));' ''
expect "a ceiling the world tier never touches" "stops mattering here"
restore

edit $J/rules/EnchantingRules.java 'return ETERNA_PER_TIER * Math.max(0, worldTier);' \
                                   'return ETERNA_PER_TIER * (float) Math.sqrt(Math.max(0, worldTier));'
expect "a world tier that curves rather than climbs (harness)" "curves rather than climbing"
restore

echo "the advancements"
AD=$RES/data/elysiumenchanting/advancement

edit $AD/arcane_table.json '"parent": "elysiumenchanting:root"' \
                           '"parent": "elysiumenchanting:enchanted"'
expect "an advancement moved by hand" "a shipped file was edited by hand"
restore

edit $GEN '    ("arcane_table", "root", f"{NS}:arcane_table", "task",' \
          '    ("arcane_table", None, f"{NS}:arcane_table", "task",'
regen
expect "a tree with two roots" "A tree has one root"
restore

edit $GEN '("enchanted", "arcane_table",' '("enchanted", "arcane_desk",'
regen
expect "an advancement hanging off nothing" "not an advancement this module has"
restore

edit $LANG '"advancements.elysiumenchanting.deep_work.title"' \
           '"advancements.elysiumenchanting.deep_work.name"'
expect "an advancement with no title" "has no title"
restore

edit $GEN '{"enchanted": {"trigger": "minecraft:enchanted_item"}}' \
          '{"enchanted": {"trigger": "minecraft:enchanted_thing"}}'
regen
expect "an advancement waiting for a trigger the game has not" "not a trigger the game has"
restore

edit $GEN '("arcane_table", "root", f"{NS}:arcane_table", "task",' \
          '("arcane_table", "root", f"{NS}:arcane_desk", "task",'
regen
expect "an advancement drawn with an item nobody registers" "does not register"
restore

edit $GEN '"The Long Study",' '"The Long Study",' 
edit $GEN '("root", None, "minecraft:bookshelf", "task",' \
          '("root", None, "minecraft:bookshelf", "epic",'
regen
expect "an advancement in a frame the game cannot draw" "the game does not draw"
restore

edit $GEN '{"table": has(f"{NS}:arcane_table")}' '{"table": has(f"{NS}:arcane_desk")}'
regen
expect "an advancement nobody can ever earn" "can never be earned"
restore

cp $AD/root.json $AD/stray.json
expect "an advancement file the table never asked for" "is not in the table"
restore

mv $AD/tome.json /tmp/adv.away
expect "a declared advancement with no file" "declared and no file ships"
restore
rm -f /tmp/adv.away

echo
echo "restoring and re-checking"
restore
if (cd $ROOT && python3 elysium-enchanting/validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
rm -f $SNAP
echo "enchanting: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
