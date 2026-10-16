#!/usr/bin/env bash
# P11 — the spawner you can own, broken one way at a time.
#
# Every check the spawner validator and its harness make is deliberately
# defeated here, and the suite fails if the break is not noticed.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-spawners
J=$M/src/main/java/com/elysium/spawners
RES=$M/src/main/resources
LANG=$RES/assets/elysiumspawners/lang/en_us.json
GEN=$M/tools/gen_spawners.py
SNAP=/tmp/negtest_spawners_snapshot.tgz
pass=0; fail=0

tar czf $SNAP -C /tmp Elysium-mono/elysium-spawners Elysium-mono/art

out() { (cd $ROOT && python3 elysium-spawners/validate.py 2>&1); }

expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }

edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

restore() { rm -rf $M $ROOT/art; tar xzf $SNAP -C /tmp; }
regen() { (cd $ROOT && python3 elysium-spawners/tools/gen_spawners.py >/dev/null 2>&1); }

echo "the module on its own"
edit $J/ElysiumSpawners.java 'import net.neoforged.bus.api.IEventBus;' \
                             'import shadowsoffire.apothic_spawners.Spawners;
import net.neoforged.bus.api.IEventBus;'
expect "a reach for the mod this replaces" "refers to 'shadowsoffire'"
restore

edit $J/ElysiumSpawners.java 'import net.neoforged.bus.api.IEventBus;' \
                             'import com.elysium.core.Elysium;
import net.neoforged.bus.api.IEventBus;'
expect "a dependency on another Elysium module" "nothing else from Elysium"
restore

edit $RES/META-INF/neoforge.mods.toml '[[dependencies.${mod_id}]]' \
     '[[dependencies.${mod_id}]]
modId="apothic_spawners"
type="required"
versionRange="[0,)"
ordering="NONE"
side="BOTH"

[[dependencies.${mod_id}]]'
expect "a declared dependency on Apothic Spawners" "declares a dependency on"
restore

echo "the arithmetic, run rather than read"
edit $J/rules/SpawnerRules.java 'public final class SpawnerRules {' \
                                'import net.minecraft.world.level.BaseSpawner;

public final class SpawnerRules {'
expect "a game class in the pure rules" "imports nothing but the JDK"
restore

mv $M/tools/SpawnerHarness.java /tmp/sh.away
expect "the arithmetic with nothing to run it" "only read, never run"
restore
rm -f /tmp/sh.away

edit $J/rules/SpawnerRules.java 'public static final int BASE_MIN_DELAY = 200;' \
                                'public static final int BASE_MIN_DELAY = 100;'
expect "an untouched spawner that is not vanilla's (validator)" "is not vanilla's spawner"
expect "an untouched spawner that is not vanilla's (harness)" "does not wait what vanilla"
restore

edit $J/rules/SpawnerRules.java 'return at / (at + DELAY_PACE);' 'return Math.min(1.0D, at / DELAY_PACE);'
expect "waiting that can reach zero (validator)" "not cut by a share"
expect "waiting that can reach zero (harness)" "cut away entirely"
restore

edit $J/rules/SpawnerRules.java 'public static final int DELAY_FLOOR = 10;' \
                                'public static final int DELAY_FLOOR = 0;'
expect "no floor under the waiting" "no floor under how fast"
restore

edit $J/rules/SpawnerRules.java 'public static final float CAPTURE_CEILING = 0.5F;' \
                                'public static final float CAPTURE_CEILING = 1.0F;'
expect "capturing that becomes certain (validator)" "ceiling is certainty"
expect "capturing that becomes certain (harness)" "so the first Capturing sword"
restore

edit $J/rules/SpawnerRules.java 'return (float) (CAPTURE_CEILING * (at / (at + CAPTURE_PACE)));' \
                                'return (float) Math.min(0.9D, at / 10.0D);'
expect "a capture chance that is not a share of a ceiling" "not a share of a ceiling"
restore

echo "the spawner itself"
BE=$J/block/ElysiumSpawnerBlockEntity.java
BL=$J/block/ElysiumSpawnerBlock.java

edit $BE 'public final class Cage extends BaseSpawner {' 'public final class Cage extends Object {'
expect "a spawner that reimplements spawning" "game's own BaseSpawner"
restore

edit $BE 'tag.putShort("SpawnCount", (short) SpawnerRules.spawnCount(tuning.countSteps()));' ''
expect "a rune that raises a number nothing reads" "SpawnCount"
restore

edit $BE 'spawner.load(level, getBlockPos(), tag);
        }
    }' '}
    }'
expect "a tuning never handed to the spawner" "never handed to the spawner"
restore

edit $BE 'return tuning.ignoresPlayers() ? SpawnerRules.ALWAYS_AWAKE
                : SpawnerRules.playerRange(tuning.rangeSteps());' \
         'return SpawnerRules.playerRange(tuning.rangeSteps());'
expect "a spawner that cannot be told to ignore players" "always contains"
restore

edit $BE 'level.hasNeighborSignal(pos)' 'false'
expect "a spawner that never looks for its signal" "never checks for one"
restore

edit $BE 'entity.tuning.redstone() && !' '!'
expect "every spawner waiting for a signal" "only the ones told to"
restore

edit $BE 'super.loadAdditional(tag, registries);
        spawner.load(level, getBlockPos(), tag);' 'super.loadAdditional(tag, registries);'
expect "a spawner that forgets what is in it" "lost on the next restart"
restore

edit $BE 'tag.put("tuning", written));' 'tag.put("tune", written));'
expect "a tuning written under another name" "lost on the next restart"
restore

edit $BE 'protected void collectImplicitComponents(DataComponentMap.Builder builder) {' \
         'protected void notCollecting(DataComponentMap.Builder builder) {'
expect "a spawner that forgets what was done to it when carried" "as a component"
restore

edit $BL 'SpawnerModifiers.Modifier modifier = SpawnerModifiers.get().forItem(held);' \
         'SpawnerModifiers.Modifier modifier = null;'
expect "runes decided in Java" "read from the data"
restore

edit $BL 'if (!spend(player, held, SpawnerRules.stepCost(steps))) {' 'if (!spend(player, held, 1)) {'
expect "a step that costs a number nobody wrote down" "made up on the spot"
restore

edit $BL 'held.shrink(cost);' ''
expect "a rune that is never consumed" "does not consume the runes"
restore

edit $BL 'if (player.getAbilities().instabuild) {
            return true;
        }' ''
expect "creative mode charged for a step" "creative mode is charged"
restore

edit $BL 'if (modifier.maxSteps() > 0 && steps >= modifier.maxSteps()) {' 'if (false) {'
expect "an axis with a limit that is not limited" "is not limited"
restore

edit $BL 'SpawnerRules.UNSTABLE_POWER' '0.0F'
expect "an unstable spawner that does not go off" "does not go off"
restore

echo "the runes"
edit $GEN '("rune_of_haste", "Rune of Haste"' '("rune_of_speed", "Rune of Haste"'
expect "a rune the generator has never heard of" "never heard of it"
restore

edit $J/registry/SpawnerItems.java 'rune("rune_of_silence")' 'rune("rune_of_quiet")'
expect "a rune nothing registers" "dead weight in the jar"
restore

edit $RES/data/elysiumspawners/elysium_spawner_modifiers/rune_of_haste.json \
     '"stat": "delay"' '"stat": "count"'
expect "a modifier file edited by hand" "a shipped file was edited by hand"
restore

edit $RES/data/elysiumspawners/elysium_spawner_modifiers/rune_of_haste.json \
     '"item": "elysiumspawners:rune_of_haste"' '"item": "minecraft:sugar"'
expect "a rune and its meaning about different items" "different items"
restore

edit $RES/data/elysiumspawners/elysium_spawner_modifiers/rune_of_haste.json \
     '"stat": "delay"' '"stat": "delay",
  "flag": "silent"'
expect "a modifier naming both a stat and a flag" "both a stat and a flag"
restore

edit $RES/data/elysiumspawners/elysium_spawner_modifiers/rune_of_haste.json \
     '"max_steps": 12' '"max_steps": 3'
expect "a limit shipped that is not the limit written" "limit shipped as"
restore

edit $LANG '"item.elysiumspawners.rune_of_haste"' '"item.elysiumspawners.rune_haste"'
expect "a rune with no name" "has no name"
restore

edit $LANG '"elysiumspawners.tooltip.rune_of_haste"' '"elysiumspawners.tooltip.haste"'
expect "a rune that says nothing about itself" "says nothing about itself"
restore

edit $GEN '"haste", "plasma"' '"hastiness", "plasma"'
expect "a rune drawn with a mark nobody has" "the art package does"
restore

edit $J/data/SpawnerTuning.java '"silent", "redstone");' '"silent", "redstone", "invisible");'
expect "a behaviour no rune can switch on" "no rune switches it on"
restore

edit $GEN '"stat:delay", 12' '"stat:speed", 12'
expect "a rune that raises an axis a spawner does not have" "not one of a spawner's axes"
restore

mv $RES/assets/elysiumspawners/models/item/rune_of_haste.json /tmp/rm.away
expect "a rune with nothing to draw" "has no model"
restore
rm -f /tmp/rm.away

echo "picking one up"
PU=$J/event/SpawnerPickup.java

edit $PU 'if (!hasSilkTouch(event.getLevel(), event.getTool())) {
            smashed(event);
            return;
        }' ''
expect "a spawner that comes up with any tool" "any tool at all"
restore

edit $PU 'stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(contents));' ''
expect "a spawner that arrives empty" "arrives empty"
restore

edit $PU 'stack.set(SpawnerComponents.TUNING.get(),
                    ((ElysiumSpawnerBlockEntity) entity).tuning());' ''
expect "a move that loses every rune spent" "every rune they spent"
restore

edit $PU 'boolean vanilla = isVanillaSpawner(entity);' 'boolean vanilla = false;'
expect "vanilla's spawners left out of it" "only applies to itself"
restore

edit $PU 'if (ours && !carriable(((ElysiumSpawnerBlockEntity) entity).tuning())) {' 'if (false) {'
expect "an unstable spawner in a pocket" "the one thing it is not for"
restore

edit $PU 'smashed(event);
            return;' 'return;'
expect "a smashed spawner worth nothing" "smashing one is"
restore

edit $PU 'for (ItemStack stack : table.getRandomItems(params)) {' \
         'for (ItemStack stack : java.util.List.of(new ItemStack(net.minecraft.world.item.Items.IRON_NUGGET))) {'
expect "a smashed spawner priced in Java" "rather than by a loot table"
restore

edit $PU 'tag.putString("id", ResourceLocation.fromNamespaceAndPath(
                ElysiumSpawners.MODID, "spawner").toString());' ''
expect "a carried spawner that puts nothing back" "placing it puts nothing back"
restore

mv $RES/data/elysiumspawners/loot_table/unstable_spawner.json /tmp/ut.away
expect "a smash table that does not ship" "does not ship"
restore
rm -f /tmp/ut.away

echo "what it sends"
BH=$J/event/SpawnerBehaviour.java

edit $BH 'if (event.getSpawner() instanceof ElysiumSpawnerBlockEntity.Cage cage
                && cage.owner().tuning().ignoresLight()) {' 'if (true) {'
expect "every spawner in the game ignoring the light" "every spawner in the game"
restore

edit $BH 'cage.owner().tuning().ignoresLight()' 'true'
expect "a spawner ignoring light it was never told to ignore" "whether or not it was told to"
restore

edit $BH 'mob.setNoAi(true);' ''
expect "a stillness rune that does nothing" "no_ai behaviour is a rune that does nothing"
restore

edit $BH 'mob.setSilent(true);' ''
expect "a silence rune that does nothing" "silent behaviour is a rune that does nothing"
restore

edit $BH 'mob.setPersistenceRequired();' ''
expect "a tethering rune that does nothing" "persistent behaviour is a rune that does nothing"
restore

edit $BH 'SpawnerTuning tuning = tuningOf(event);' 'SpawnerTuning tuning = SpawnerTuning.NONE;'
expect "behaviour applied without asking which spawner" "which spawner sent it"
restore

echo "capturing"
CM=$J/loot/CapturingModifier.java

edit $CM 'SpawnerRules.captureChance(level)' '0.5F'
expect "a capture chance of the modifier's own" "chance of its own"
restore

edit $CM 'Item egg = net.minecraft.world.item.SpawnEggItem.byId(victim.getType());' 'Item egg = null;'
expect "a capture that produces no egg" "creature's own egg"
restore

edit $CM 'LootContextParams.ATTACKING_ENTITY' 'LootContextParams.THIS_ENTITY'
expect "a capture that never asks who killed it" "who did the killing"
restore

edit $RES/data/elysiumspawners/enchantment/capturing.json '"weight": 1' '"weight": 10'
expect "treasure offered at every table" "every table in the game"
restore

edit $RES/data/neoforge/loot_modifiers/global_loot_modifiers.json \
     '"elysiumspawners:capturing"' '"elysiumspawners:nothing"'
expect "a loot modifier the game never loads" "never listed"
restore

edit $RES/data/neoforge/loot_modifiers/global_loot_modifiers.json '"replace": false' '"replace": true'
expect "every other mod's loot modifiers turned off" "every other mod's loot modifiers"
restore

echo "the block"
mv $RES/assets/elysiumspawners/blockstates/spawner.json /tmp/bs.away
expect "a block with no blockstate" "has no blockstate"
restore
rm -f /tmp/bs.away

cp $RES/data/elysiumspawners/recipe/rune_of_haste.json $RES/data/elysiumspawners/recipe/spawner.json
expect "a craftable spawner" "the generator says it should not"
restore

edit $RES/data/minecraft/tags/block/mineable/pickaxe.json '"elysiumspawners:spawner"' '"minecraft:stone"'
expect "a block no tool is correct for" "no tool is ever correct"
restore

edit $J/registry/SpawnerBlockEntities.java 'SpawnerBlocks.SPAWNER.get()' 'null'
expect "a block its own block entity does not accept" "throws rather than working"
restore

edit $J/client/SpawnersClient.java 'event.registerBlockEntityRenderer(SpawnerBlockEntities.SPAWNER.get(),
                SpawnerCageRenderer::new);' ''
expect "a cage with nothing drawn in it" "the cage is empty"
restore

edit $J/client/SpawnerCageRenderer.java 'SpawnerRenderer.renderEntityInSpawner(partialTick, pose, buffers, light, shown,
                entities, spawner.spawner().getoSpin(), spawner.spawner().getSpin());' ''
expect "a creature drawn by this module" "drawn by this module"
restore

edit $LANG '"block.elysiumspawners.spawner"' '"block.elysiumspawners.cage"'
expect "a block with no name" "has no name"
restore

edit $LANG '"itemGroup.elysiumspawners"' '"itemGroup.spawners"'
expect "a tab with no name" "creative tab has no name"
restore

echo "one jar, two mods adding a loot modifier"
# These two run the packaging checks rather than the module validator: the
# file they are about belongs to no module, and it is the one file in the mod
# that two modules are *expected* to both write.
MERGE=$ROOT/tools/mergejar.py
CHECK=$ROOT/check_merge.py

expectmerge() { local w="$2" g; g="$( (cd $ROOT && python3 check_merge.py 2>&1) )"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }

expectunion() { local w="$2" g
  g="$(cd $ROOT && python3 -c "
import sys, json
sys.path.insert(0, 'tools')
import mergejar
lib = json.dumps({'replace': False, 'entries': ['elysiumlib:affixes']})
sp = json.dumps({'replace': False, 'entries': ['elysiumspawners:capturing']})
try:
    print(mergejar.union_tags(mergejar.GLOBAL_LOOT_MODIFIERS,
                              [('lib', lib), ('spawners', sp)]).decode())
except Exception as problem:
    print(problem)
" 2>&1)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }

cp $RES/data/neoforge/loot_modifiers/global_loot_modifiers.json /tmp/glm.bak
edit $RES/data/neoforge/loot_modifiers/global_loot_modifiers.json '"replace": false' '"replace": true'
expectmerge "a module that turns off every other mod's loot modifiers" "no order left"
cp /tmp/glm.bak $RES/data/neoforge/loot_modifiers/global_loot_modifiers.json
rm -f /tmp/glm.bak

cp $MERGE /tmp/mergejar.bak
edit $MERGE 'return "entries" if path == GLOBAL_LOOT_MODIFIERS else "values"' 'return "values"'
expectunion "a merge that reads the wrong key and quietly empties the list" "not under 'values'"
cp /tmp/mergejar.bak $MERGE
rm -f /tmp/mergejar.bak

cp $MERGE /tmp/mergejar.bak
edit $MERGE 'return is_additive_tag(path) or path == GLOBAL_LOOT_MODIFIERS' 'return is_additive_tag(path)'
cp $CHECK /tmp/check_merge.bak
edit $CHECK 'and path.endswith(".json")) or path == GLOBAL_LOOT_MODIFIERS' 'and path.endswith(".json"))'
expectmerge "two mods' loot modifiers where one silently wins" "silently wins"
cp /tmp/mergejar.bak $MERGE
cp /tmp/check_merge.bak $CHECK
rm -f /tmp/mergejar.bak /tmp/check_merge.bak

echo "the advancements"
AD=$RES/data/elysiumspawners/advancement

edit $AD/carried.json '"parent": "elysiumspawners:root"' '"parent": "elysiumspawners:tuned"'
expect "an advancement moved by hand" "a shipped file was edited by hand"
restore

edit $AD/behaviours.json '"requirements": [
    [
      "indifference"
    ],' '"requirements": ['
expect "a challenge satisfied by five of six" "not the challenge it says it is"
restore

edit $GEN '("captured", "root", "minecraft:pig_spawn_egg", "challenge",' \
          '("captured", None, "minecraft:pig_spawn_egg", "challenge",'
regen
expect "a tree with two roots" "A tree has one root"
restore

edit $GEN '"trigger": "minecraft:item_used_on_block"' '"trigger": "minecraft:rune_used"'
regen
expect "an advancement waiting for a trigger the game has not" "not a trigger the game has"
restore

edit $LANG '"advancements.elysiumspawners.carried.description"' \
           '"advancements.elysiumspawners.carried.text"'
expect "an advancement with nothing said about it" "has no description"
restore

mv $AD/tuned.json /tmp/adv.away
expect "a declared advancement with no file" "declared and no file ships"
restore
rm -f /tmp/adv.away

echo "the command"
SC=$J/command/SpawnerCommand.java

mv $SC /tmp/sc.away
expect "no way to ask what a spawner is" "invisible to them"
restore
rm -f /tmp/sc.away

edit $SC 'dispatcher.register(Commands.literal("spawner").executes(context -> {' \
         'dispatcher.register(Commands.literal("spawner").requires(source -> source.hasPermission(2)).executes(context -> {'
expect "a read-only command behind a permission" "stops only the people it was written for"
restore

edit $SC 'HitResult hit = player.pick(REACH, 0.0F, false);' \
         'HitResult hit = null;'
expect "a command that reports somebody else's spawner" "does not read the spawner the player is looking at"
restore

edit $SC 'line(source, "spread", tuning.spreadSteps(),' 'line(source, "scatter", tuning.spreadSteps(),'
expect "an axis a player cannot see" "does not report the spread axis"
restore

edit $SC 'value, steps, SpawnerRules.stepCost(steps))' 'value, steps, 1)'
expect "a command that never says what the next step costs" "the question it exists to answer"
restore

edit $SC 'source.sendSuccess(() -> Component.translatable("elysiumspawners.command.spent",
                        tuning.spent()).withStyle(ChatFormatting.DARK_GRAY), false);' ''
expect "a spawner whose history is invisible" "what has gone into the spawner already"
restore

echo
echo "restoring and re-checking"
restore
if (cd $ROOT && python3 elysium-spawners/validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
rm -f $SNAP
echo "spawners: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
