#!/usr/bin/env bash
# The book, broken one way at a time.
#
# Every check the almanac added is deliberately defeated here and the suite
# fails if the validator or the harness does not notice. A check nobody has
# seen fail is a check nobody has seen.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-almanac
R=$M/src/main/java/com/elysium/almanac/rules/AlmanacRules.java
S=$M/src/main/java/com/elysium/almanac
G=$M/tools/gen_almanac.py
RES=$M/src/main/resources
SNAP=/tmp/almanac_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-almanac)
restore() { rm -rf $ROOT/elysium-almanac && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 300 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
# For the rules that can only be defeated by editing the words and the files
# they generate together — an edit to one alone is caught by the drift check,
# which is a different rule and would make these vacuous.
expect_regen() { local w="$2" g
  (cd $M && python3 tools/gen_almanac.py >/dev/null 2>&1)
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

echo "the walk, and the three properties built on it"
edit $R '            if (!seen.add(here)) {
                continue;
            }' '            if (false) {
                continue;
            }'
expect "a walk over the book with no memory" "no memory"

edit $R '            if (waitsOn(quest.id(), quest.id())) {' '            if (waitsOn(quest.id(), "welcome")) {'
expect "a cycle check that asks a different question" "answering a different question"

edit $R '        stuck.removeAll(reached);' '        stuck.clear();'
expect "an unreachable check that always says nothing is stranded" "away from all of them"

edit $R '            deepest = Math.max(deepest, depthOf(need) + 1);' '            deepest = Math.max(deepest, 1);'
expect "depth that is not the longest chain behind a quest" "not sorted into the order"

edit $R '            if (done.contains(quest.id())) {
                continue;
            }' '            if (false) {
                continue;
            }'
expect "a book that offers a player the step they just finished" "already finished"

edit $R '            if (count < 1) {' '            if (count < 0) {'
expect "a task that wants none of something" "may want none"

edit $R '            if (tasks.isEmpty()) {' '            if (false) {'
expect "a quest that asks for nothing at all" "may ask for nothing"

echo "the book itself, checked a second time by the validator's own arithmetic"
edit $R 'quest("welcome", "first_light", List.of(), Task.read())' 'quest("welcome", "first_light", List.of("luna"), Task.read())'
expect "a quest that is, far enough back, its own prerequisite" "wait(s) on themselves"

edit $R 'quest("crank", "first_light", List.of("welcome"),' 'quest("crank", "first_light", List.of("welcom"),'
expect "a prerequisite that is not a quest" "are not quests"

edit $R 'quest("servo", "ascent", List.of("reactor"),' 'quest("servo", "ascent", List.of("reactor", "bronze"),'
expect "a chapter that opens before the chapter it follows" "a chapter that comes later"

edit $R 'new Chapter("first_light", List.of()),' 'new Chapter("first_light", List.of("frontier")),'
expect "chapters that follow each other in a circle" "chapters loop at"

edit $R 'new Chapter("garden", List.of("reconstruction")),' 'new Chapter("garden", List.of()),'
expect "a book with two opening pages" "first chapter(s)"

edit $R 'new Chapter("the_wilds", List.of("first_light")));' 'new Chapter("the_wilds", List.of("first_light")),
            new Chapter("epilogue", List.of("the_wilds")));'
expect "a heading with nothing under it" "have no quests in them"

edit $R 'quest("saw", "first_light", List.of("millstone"),' 'quest("saw", "firstlight", List.of("millstone"),'
expect "a quest in the book and on no page of it" "on no page of it"

edit $R 'quest("hive", "garden", List.of("pot"), Task.have(AGR + "hive", 1)),' 'quest("hive", "garden", List.of("pot"), Task.have(AGR + "hive", 1)),
            quest("hive", "garden", List.of("pot"), Task.have(AGR + "hive", 2)),'
expect "a quest declared twice" "more than once"

python3 - <<'PYX'
import pathlib, re
p = pathlib.Path("/tmp/Elysium-mono/elysium-almanac/src/main/java/com/elysium/almanac/rules/AlmanacRules.java")
s = p.read_text(encoding="utf-8")
# Every step hanging directly off the first one: a book with no shape to it.
s = re.sub(r'(quest\("(?!welcome)[a-z_]+", "[a-z_]+", )List\.of\([^)]*\)', r'\1List.of("welcome")', s)
p.write_text(s, encoding="utf-8")
PYX
expect "a four-age pack guided by a book one step deep" "longest chain in the book"

edit $R 'new Chapter("automation", List.of("warehouse", "ascent")),' 'new Chapter("automation", List.of("warehouse", "foundry")),'
expect "a chapter listed before the one it follows" "which it follows"

echo "what the book points at"
edit $R 'Task.have(KIN + "hand_crank", 1)' 'Task.have(KIN + "hand_cranke", 1)'
expect "a task asking for an item nothing in the tree makes" "no module in this tree makes"

edit $R 'Task.have(ATO + "enriched_uranium", 1)' 'Task.have("mekanism:enriched_uranium", 1)'
expect "a task asking for an item out of a mod being replaced" "no such module in the tree"

echo "the words, and that they are generated"
edit $G '    "crusher": ("Crush it",' '    "crushed": ("Crush it",'
expect "a quest whose words were deleted" "generator will not run"

edit $RES/assets/elysiumalmanac/lang/en_us.json '"Almanac"' '"Elysium Almanac Book"'
expect "a language file edited by hand under a generator" "differ from what the generator writes"

edit $G '    quests = re.findall(r'"'"'quest\("([a-z_]+)", "([a-z_]+)"'"'"', source)
    chapters = re.findall(r'"'"'new Chapter\("([a-z_]+)"'"'"', source)
    return [q for q, _ in quests], list(dict.fromkeys(chapters))' '    return list(QUESTS), list(CHAPTERS)'
expect "words and graph kept as two hand-written lists" "two hand-kept lists"

edit $G '    "motor": ("Drive the old hall",
              "Electricity back into rotation. Your first machine hall does "
              "not stop being useful — it stops being hand-cranked."),' '    "motor": ("Drive the old hall", "Craft a motor."),'
expect_regen "a reason line that only restates the title" "under sixty characters"

edit $G '    "warehouse": ("The Warehouse",
                  "Somewhere to put all of it."),' '    "warehouse": ("The Warehouse", "Storage."),'
expect_regen "a chapter subtitle that says nothing" "say almost nothing"

echo "the book in the world"
rm $RES/assets/elysiumalmanac/textures/item/almanac.png
expect "the one item this module adds having no texture" "has no a texture"

edit $G '{"item": "minecraft:book"}' '{"item": "elysiumfoundry:starsteel_ingot"}'
expect_regen "a guide only somebody who did not need it could craft" "craftable out of the base game"

edit $S/registry/AlmanacAttachments.java '                    .copyOnDeath()' '                    '
expect "everything a player has done lost when they die" "difficulty setting"

edit $S/registry/AlmanacAttachments.java '                    .sync(StreamCodec.of(' '                    .unsynced(StreamCodec.of('
expect "a list the client never receives" "never sent to the client"

edit $S/registry/AlmanacAttachments.java '                    .serialize(Codec.STRING.listOf())' '                    '
expect "a book that empties every restart" "not written to disk"

# And the same three asked of the *second* attachment, because a check that
# only asks whether the file mentions copyOnDeath passes while one of the two
# loses everything on death.
edit $S/registry/AlmanacAttachments.java '    public static final Supplier<AttachmentType<List<String>>> SLAIN =
            ATTACHMENTS.register("slain", () -> AttachmentType
                    .<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())' \
        '    public static final Supplier<AttachmentType<List<String>>> SLAIN =
            ATTACHMENTS.register("slain", () -> AttachmentType
                    .<List<String>>builder(() -> List.of())'
expect "one of the two lists not written to disk" "the kill tally is not written to disk"

edit $S/registry/AlmanacAttachments.java '                                    .decode((RegistryFriendlyByteBuf) buffer)))
                    .copyOnDeath()
                    .build());

    /**
     * How many times each counted task has happened' \
        '                                    .decode((RegistryFriendlyByteBuf) buffer)))
                    .build());

    /**
     * How many times each counted task has happened'
expect "a kill tally lost on death while the other list survives" "the kill tally is lost when the player dies"

edit $S/registry/AlmanacAttachments.java '        List<String> now = new ArrayList<>(was);
        now.add(quest);
        player.setData(DONE.get(), List.copyOf(now));' '        was.add(quest);'
expect "an attachment mutated in place, which the sync never hears about" "sync never hears about it"

edit $S/event/AlmanacProgress.java 'PlayerEvent.PlayerChangedDimensionEvent event' 'PlayerEvent.PlayerRespawnEvent event'
expect "arriving somewhere never noticed" "frontier cannot be finished"

edit $S/event/AlmanacProgress.java 'BlockEvent.EntityPlaceEvent event' 'BlockEvent.BreakEvent event'
expect "placing a block never noticed" "every PLACE quest is impossible"

edit $S/event/AlmanacProgress.java 'PlayerEvent.PlayerLoggedInEvent event' 'PlayerEvent.Clone event'
expect "a guide nobody is ever handed" "nobody is ever handed the book"

edit $S/event/AlmanacProgress.java 'if (player.level().isClientSide() || player.tickCount % LOOKS_EVERY != 0) {' 'if (player.level().isClientSide()) {'
expect "forty inventory walks a second per player" "inventory walks a second"

edit $S/event/AlmanacProgress.java '        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return false;' '        if (key == null) {
            return false;'
expect "a task naming an item from a mod nobody installed" "designed to survive"

edit $S/event/AlmanacProgress.java 'GIVEN = "#given"' 'GIVEN = "welcome"'
expect "being handed the book finishing a step of it" "is also a quest id"

edit $S/event/AlmanacProgress.java 'GIVEN = "#given"' 'GIVEN = "given"'
expect "a marker spelled the way a quest id is" "spelled the way a quest id is"

edit $S/client/QuestScreen.java 'class QuestScreen extends ElysiumChooser' 'class QuestScreen extends net.minecraft.client.gui.screens.Screen'
expect "the book drawn like a different mod" "different mod from everything else"

edit $S/item/AlmanacItem.java 'if (level.isClientSide() && FMLEnvironment.dist == Dist.CLIENT) {' 'if (level.isClientSide()) {'
expect "a screen class loaded on a dedicated server" "nobody is watching"

echo "independence, which for this module is the whole design"
edit $S/ElysiumAlmanac.java '    public static final String MODID = "elysiumalmanac";' '    public static final String MODID = "elysiumalmanac";
    static final Class<?> UP = com.elysium.industry.rules.IndustryRules.class;'
expect "the book naming a module it describes in code" "a class reference does not"

edit $M/src/main/resources/META-INF/neoforge.mods.toml '    modId="elysiumlib"' '    modId="elysiumindustry"'
expect "a guide that could only ever describe a complete install" "complete install"

edit $R 'import java.util.ArrayDeque;' 'import com.elysium.lib.ElysiumPalette;
import java.util.ArrayDeque;'
expect "a graph that is not the JDK alone" "the graph is the JDK alone"

edit $R 'import java.util.ArrayDeque;' 'import net.minecraft.world.item.Item;
import java.util.ArrayDeque;'
expect "a graph no harness could play without a game" "without a game"

mv $M/tools/AlmanacHarness.java /tmp/almanac_harness_hidden.java
expect "a book checked by reading it" "provably does not find"
mv /tmp/almanac_harness_hidden.java $M/tools/AlmanacHarness.java

echo "the seam"
edit $S/ElysiumAlmanac.java 'MODID = "elysiumalmanac"' 'MODID = "elysium_almanac"'
expect "a module id nothing else in the tree spells that way" "everything else in the tree spells"

edit $S/ElysiumAlmanac.java '        com.elysium.almanac.registry.AlmanacItems.ITEMS.register(modEventBus);' '        '
expect "a module written and never registered" "none of it exists"

echo "the half of the pack that is not machines"
edit $R '        SLAY,' '        SLAY_UNUSED,'
expect "a task kind the book uses and the rules no longer have" "does not compile against the JDK alone"

edit $R '                    Task.slay(BST + "spallet", 1)),' '                    Task.slay(BST + "shardlet", 1)),'
expect "a quest pointing at a beast nothing spawns" "nothing in the tree spawns it"

edit $R '                    Task.have(ARS + "iron_greatsword", 1)),' '                    Task.have(ARS + "iron_glaive", 1)),'
expect "a quest pointing at a weapon nothing makes" "a player cannot tell apart"

edit $R '            new Chapter("reconstruction", List.of("first_light")),' \
        '            new Chapter("reconstruction", List.of("the_wilds")),'
expect "a chapter listed before the one it follows" "which it follows; the book is read in the order it is written"

edit $R '            new Chapter("the_wilds", List.of("first_light")));' \
        '            new Chapter("the_wilds", List.of("the_wilds")));'
expect "a chapter that waits on itself" "waits on itself"


edit $S/registry/AlmanacAttachments.java '                player.setData(SLAIN.get(), List.copyOf(now));
                return;' '                return;'
expect "a tally counted up and never stored" "counted and never stored"

echo "the rewards, the kinds, and what the book points at now"
edit $S/event/AlmanacProgress.java 'case SLAY -> AlmanacAttachments.slain(player, task.what()) >= task.count();' 'case SLAY -> AlmanacAttachments.slain(player, task.what()) >= 1;'
expect "a kill quest that finishes on the first kill" "finishes on the first thing anybody kills"

edit $S/event/AlmanacProgress.java 'case PLACE, BREAK, CRAFT, USE, EAT, SMELT ->
                        AlmanacAttachments.count(player, task) >= task.count();' 'case PLACE, BREAK, CRAFT, USE, EAT, SMELT -> true;'
expect "counted kinds that never read their tally" "never read their tally"

edit $S/event/AlmanacProgress.java '                if (matches) {
                    AlmanacAttachments.bump(player, task, by);
                }' ''
expect "events that are noticed and never counted" "every counted step waits for ever"

edit $S/event/AlmanacProgress.java 'BlockEvent.BreakEvent event' 'BlockEvent.FarmlandTrampleEvent event'
expect "breaking a block never noticed" "every BREAK step is impossible"

edit $S/event/AlmanacProgress.java '                case LEVEL -> ElysiumCharacter.getLevel(player) >= task.count();
' ''
expect "a kind of task nothing answers" "never answered in AlmanacProgress"

edit $S/client/StepScreen.java '            case WEB -> Component.translatable("elysiumalmanac.task.web", count);
' ''
expect "a kind of task with no label" "has no label on the step screen"

edit $S/event/AlmanacRewards.java '        if (!AlmanacAttachments.has(player, questId)) {
            return WHY_NOT_DONE;
        }
' ''
expect "a reward paid for a step not finished" "pays for steps not finished"

edit $S/event/AlmanacRewards.java '        if (!AlmanacAttachments.claim(player, questId)) {
            return WHY_ALREADY;
        }' '        if (AlmanacAttachments.claimed(player, questId)) {
            return WHY_ALREADY;
        }'
expect "a reward never marked taken" "paid every time it is asked for"

edit $S/network/AlmanacNetwork.java '        registrar.playToServer(AlmanacClaim.TYPE, AlmanacClaim.STREAM_CODEC,
                AlmanacNetwork::onClaim);' ''
expect "a claim nobody is listening for" "nobody is listening for"

edit $S/command/AlmanacCommand.java 'Commands.literal("finish").requires(source -> source.hasPermission(2))' 'Commands.literal("finish")'
expect "anybody finishing their own book from the console" "anybody can finish"

edit $S/registry/AlmanacAttachments.java '            ATTACHMENTS.register("tally", () -> AttachmentType
                    .<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())' '            ATTACHMENTS.register("tally", () -> AttachmentType
                    .<List<String>>builder(() -> List.of())'
expect "an event tally that empties every restart" "the event tally is not written to disk"

edit $R 'Task.visit("elysiumspace:pale_moon")' 'Task.visit("elysiumspace:luna")'
expect "a step in a world nothing in the tree has" "has a world of that name"

edit $R 'Task.have(AGR + "hive", 1)' 'Task.have("#elysiumagronomy:hives", 1)'
expect "a tag no module ships" "no module ships a tag"

edit $R 'LEVEL_XP_BASE = 100;' 'LEVEL_XP_BASE = 90;'
expect "a level curve the game does not use" "does not use"

edit $R 'Map.entry("welcome", Reward.of(XP_BASE, new Give("minecraft:torch", 16))),' 'Map.entry("welcom", Reward.of(XP_BASE, new Give("minecraft:torch", 16))),'
expect "a reward declared for a step that is not one" "nobody is ever paid for"

edit $R 'public static final int XP_BASE = 20;' 'public static final int XP_BASE = 0;'
expect "a step that pays nothing by the rule" "harness failed"

edit $G '    "elysiumalmanac.reward.claim": "Click to take it.",
' ''
expect_regen "a reward row with no words" "shows a key"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all almanac checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "almanac: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
