#!/usr/bin/env bash
# The small mercies, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-comforts
R=$M/src/main/java/com/elysium/comforts/rules/ComfortRules.java
S=$M/src/main/java/com/elysium/comforts
G=$M/tools/gen_comforts.py
RES=$M/src/main/resources
T=$M/src/main/java/com/elysium/comforts/rules/TraderRules.java
I=$M/src/main/java/com/elysium/comforts/item/TraderItem.java
SNAP=/tmp/comforts_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-comforts)
restore() { rm -rf $ROOT/elysium-comforts && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
expect_regen() { local w="$2" g
  (cd $M && python3 tools/gen_comforts.py >/dev/null 2>&1)
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

echo "the counting, which is the whole module"
echo "a grave water can wash away"
edit $S/registry/ComfortBlocks.java '                    .noOcclusion()
                    // A headstone is not a full cube, and the game lets water flow' '                    .noOcclusion().sound(SoundType.STONE)
                    // A headstone is not a full cube, and the game lets water flow'
edit $S/registry/ComfortBlocks.java '                    .forceSolidOn()));' '                    ));'
expect "a grave water washes away" "washes it away"

echo "the birds"
edit $S/client/PigeonModel.java 'boolean flying = entity != null && !entity.onGround();' 'boolean flying = false;'
expect "a bird that stopped flying" "no longer beats its wings off the ground"

edit $R '        return Math.max(1, Math.min(MOST_SLOTS, Math.max(0, stacks)));' '        return 41;'
expect "a grave built to a number decided in advance" "ate somebody"

edit $R '        return Math.max(0, stacks - slotsFor(stacks));' '        return 0;'
expect "a spill count that measures nothing" "measuring something else"

edit $R '    public static final int MOST_SLOTS = 512;' '    public static final int MOST_SLOTS = 64;'
expect "a cap smaller than an inventory with backpacks in it" "costs somebody their evening"

edit $S/block/GraveBlock.java '            int room = ComfortRules.slotsFor(carried.size());' '            int room = 41;'
expect "a grave in the world sized by something the harness never proved" "a different grave from the one that ships"

edit $S/block/GraveBlock.java '                held.add(carried.get(at).copy());' '                held.add(carried.get(at));'
expect "a grave holding the player's own stack objects" "empties the grave too"

echo "where it goes"
edit $R '        int atLeast = Math.min(lowest + 1, highest);' '        int atLeast = lowest;'
expect "a grave in the bedrock floor of the world" "nobody can break"

edit $R '        return Math.max(atLeast, Math.min(highest, died));' '        return died;'
expect "a grave in the void" "grave would be in the void"

edit $S/event/GraveKeeper.java '        int y = ComfortRules.graveHeight(died.getY(), level.getMinBuildHeight(),
                level.getMaxBuildHeight() - 1);' '        int y = died.getY();'
expect "a grave placed at a height the rules never clamped" "about a different function"

edit $S/event/GraveKeeper.java '            for (Direction face : Direction.values()) {
                BlockPos next = start.relative(face, reach);' '            for (Direction face : new Direction[0]) {
                BlockPos next = start.relative(face, reach);'
expect "a grave that never looks for somewhere it can sit" "dying inside a wall"

echo "who may open it, and what dying costs"
edit $R '        return owner || operator || age >= PRIVATE_FOR;' '        return true;'
expect "a grave anybody can open the moment it is made" "standing nearby"

edit $R '        return Math.max(0L, PRIVATE_FOR - Math.max(0L, age));' '        return PRIVATE_FOR - age;'
expect "a countdown that goes negative" "disagree with the lock"

edit $R '    public static final float XP_KEPT = 0.75F;' '    public static final float XP_KEPT = 1.0F;'
expect "dying that costs nothing at all" "the whole of the game"

edit $R '    public static final float XP_KEPT = 0.75F;' '    public static final float XP_KEPT = 0.1F;'
expect "a grave that is a punishment rather than a mercy" "punishment rather"

edit $R '        return (int) Math.floor(had * XP_KEPT);' '        return (int) Math.ceil(had * XP_KEPT);'
expect "the one operation in the pack that rounds the player's way" "rounds in the player"

echo "the three ways out"
edit $S/block/GraveBlock.java '        if (!ComfortRules.mayOpen(owner, operator, age)) {' '        if (false) {'
expect "a lock decided in the block rather than the rule" "the two can disagree"

edit $S/block/GraveBlock.java '        grave.giveBackTo(player);
        level.removeBlock(pos, false);' '        level.removeBlock(pos, false);
        grave.giveBackTo(player);'
expect "a grave removed before it hands anything over" "loses the lot"

edit $S/block/GraveBlock.java '            Containers.dropContents(level, pos, grave.spill());' '            grave.spill();'
expect "breaking a grave destroying what is in it" "exists to prevent"

edit $S/block/GraveBlock.java '            grave.scatterExperience(level, pos);' '            ;'
expect "breaking a grave destroying the experience in it" "destroys the experience"

edit $S/block/GraveBlock.java '    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }' '    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.NORMAL;
    }'
expect "a grave a piston can shove into a wall" "pushed into a wall is destroyed"

edit $S/block/GraveBlock.java '                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }' '                player.getInventory().add(stack);'
expect "a full inventory losing the rest of a grave" "a full inventory loses the rest"

edit $S/block/GraveBlock.java '            held.clear();
            if (experience > 0) {' '            if (experience > 0) {'
expect "a grave that can be opened twice" "duplication bug"

edit $G '    return {"type": "minecraft:block", "pools": []}' '    return {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}]}]}'
expect_regen "a grave that drops itself" "stockpile empty graves"

echo "catching the drops"
edit $S/event/GraveKeeper.java '        for (ItemEntity dropped : event.getDrops()) {' '        for (ItemEntity dropped : new java.util.ArrayList<ItemEntity>()) {'
expect "a grave filled from somewhere other than the drops" "quietly lost"

edit $S/event/GraveKeeper.java '        event.setCanceled(true);' '        ;'
expect "things buried and dropped on the floor as well" "duplication bug wearing"

edit $S/event/GraveKeeper.java '        if (event.getDrops().isEmpty()) {' '        if (false) {'
expect "an empty grave every time anybody dies" "empty block in the floor"

edit $S/event/GraveKeeper.java '        grave.fill(player, carried, player.totalExperience, level.getGameTime());
        event.getDrops().clear();' '        event.getDrops().clear();
        grave.fill(player, carried, player.totalExperience, level.getGameTime());'
expect "drops cleared before the grave is filled" "loses everything"

edit $S/event/GraveKeeper.java '            event.setDroppedExperience(0);' '            ;'
expect "levels kept and dropped as well" "twice out of one purse"

edit $S/event/GraveKeeper.java '                && buried.remove(player.getUUID())' '                && buried.contains(player.getUUID())'
expect "a record of the dead that grows for ever" "as long as the server runs"

echo "comforts in the world"
edit $RES/assets/elysiumcomforts/models/block/grave.json '"north"' '"norht"'
expect "a model edited by hand under a generator" "differ from what the generator writes"

edit $S/registry/ComfortBlocks.java '.strength(1.0F, 3_600_000.0F)' '.strength(1.0F, 3.0F)'
expect "a creeper taking everything somebody was carrying" "takes everything they were carrying"

edit $G '''        "facing=west": {"model": f"{NS}:block/grave", "y": 270},''' '''        "facing=wast": {"model": f"{NS}:block/grave", "y": 270},'''
expect_regen "a grave invisible from one side" "invisible from one"

echo "loot that belongs to whoever opened it"
edit $R '        return mix(mix(mix(container) ^ playerHigh) ^ playerLow);' '        return container;'
expect "a roll that is not a function of the player" "a slot machine"

edit $R '        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;' '        z = z * 3L;'
expect "a weaker mix that hands sequential ids identical loot" "identical loot"

edit $R '        return emptied;' '        return true;'
expect "loot thrown away that somebody is coming back for" "coming back for"

edit $S/event/LootKeeper.java '        if (key == null) {
            // An ordinary chest somebody filled by hand. Nothing to share out.
            return;
        }' '        if (false) {
            return;
        }'
expect "a shared base chest showing every player a different thing" "filled by hand"

edit $S/event/LootKeeper.java '        event.setCanceled(true);' '        ;'
expect "a player shown their copy and the real container as well" "the real container as well"

edit $S/event/LootKeeper.java '        if (player.isSecondaryUseActive() && !player.getMainHandItem().isEmpty()) {' '        if (false) {'
expect "placing a block against a loot chest opening it" "opens it instead"

edit $S/event/LootKeeper.java '        if (event.getHand() != InteractionHand.MAIN_HAND' '        if (false'
expect "a container opened twice by one click" "opens it twice"

edit $S/event/LootKeeper.java '            long seed = ComfortRules.seedFor(seedOf(container, pos),' '            long seed = 0L; if (false) seed = ComfortRules.seedFor(seedOf(container, pos),'
expect "a roll in the world the harness never proved" "a different chest from the one that ships"

edit $S/event/LootKeeper.java '        if (shares.contains(who)) {' '        if (false) {'
expect "a fresh roll every time a player reopens a chest" "therefore a slot machine"

edit $S/event/LootKeeper.java '        return seed != 0L ? seed : pos.asLong();' '        return seed;'
expect "a room of four chests that is one chest four times" "one chest four times"

edit $S/event/LootKeeper.java '        if (ComfortRules.forgettable(empty)) {
            shares.remove(who);
        } else {' '        if (false) {
            shares.remove(who);
        } else {'
expect "a table of everybody who ever opened a chest" "never empties"

edit $S/event/LootKeeper.java '        CompoundTag shares = container.getData(ComfortAttachments.SHARES.get()).copy();' '        CompoundTag shares = container.getData(ComfortAttachments.SHARES.get());'
expect "shares edited in place and never saved" "restart loses what everybody took"

edit $S/event/LootKeeper.java '        container.setChanged();' '        ;'
expect "a container never marked changed" "is not saved"

echo "independence and the seam"
edit $R 'import java.util.ArrayList;' 'import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;'
expect "rules no harness could count without a game" "without a game"

edit $S/ElysiumComforts.java '    public static final String MODID = "elysiumcomforts";' '    public static final String MODID = "elysiumcomforts";
    static final Class<?> UP = com.elysium.arcana.rules.ArcanaRules.class;'
expect "a grave that knows which mods made the things in it" "which mods added the items in it"

edit $M/src/main/resources/META-INF/neoforge.mods.toml '    modId="elysiumlib"' '    modId="gravestone"'
expect "a dependency on the mod this module replaces" "must not require any of it"

edit $S/ElysiumComforts.java 'MODID = "elysiumcomforts"' 'MODID = "elysium_comforts"'
expect "a module id nothing else in the tree spells that way" "everything else in the tree spells"

edit $S/ElysiumComforts.java '        com.elysium.comforts.registry.ComfortBlockEntities.TYPES.register(modEventBus);' '        '
expect "a block entity written and never registered" "none of it exists"

edit $S/ElysiumComforts.java '        com.elysium.comforts.registry.ComfortAttachments.ATTACHMENTS.register(modEventBus);' '        '
expect "the one table this design needs, never registered" "none of it exists"

echo "a villager in your pocket, and the clock that travels with it"
edit $T '        long since = now - trader.restockedAt();' '        long since = now;'
expect "what a villager has earned no longer travelling with the villager" "which is the exploit"

edit $T '        if (since < 0L) {
            return 0;
        }' '        if (false) {
            return 0;
        }'
expect "a world restored from a backup owing every restock at once" "negative number of restocks is"

edit $T '        if (!mayRestock(trader, now)) {
            return trader;
        }' '        if (false) {
            return trader;
        }'
expect "a villager that restocks whenever it is asked" "with no item involved at all"

edit $T '                fresh, now, trader.restocks() + 1);' '                fresh, trader.restockedAt(), trader.restocks());'
expect "a restock that leaves the next one free" "does not move the clock forward"

edit $T '        return Objects.equals(trader, unpacked(packed(trader)));' \
        '        return trader.profession().equals(unpacked(packed(trader)).profession());'
expect "a round trip checked on one field out of six" "a field can go missing and pass"

edit $T '    public static Trader pocketed(Trader trader) {
        return trader;
    }' '    public static Trader pocketed(Trader trader) {
        return new Trader(trader.profession(), trader.level(), trader.experience(),
                trader.offers(), 0L, 0);
    }'
expect "picking a villager up helpfully refreshing it" "fails rather than ships"

edit $I '        villager.saveWithoutId(written);' '        written.putString("profession", "farmer");'
expect "a villager copied field by field instead of whole" "a field missing"

edit $I '        villager.load(written);' '        villager.setCustomName(net.minecraft.network.chat.Component.literal("x"));'
expect "a villager put back from something other than what was written" "put back from exactly what was written"

edit $I '        stack.remove(ComfortComponents.TRADER.get());
        return InteractionResult.SUCCESS;' '        written.putLong(LAST_RESTOCK, 0L);
        stack.remove(ComfortComponents.TRADER.get());
        return InteractionResult.SUCCESS;'
expect "putting a villager down resetting its restock clock" "one villager, two hands and infinite emeralds"

edit $M/src/main/java/com/elysium/comforts/ElysiumComforts.java \
  'com.elysium.comforts.registry.ComfortComponents.TYPES.register(modEventBus);' '/* */'
expect "a component written and never registered" "written and none of it exists"

echo "the way in, and the loss (29 Sep 2026)"
edit $M/src/main/java/com/elysium/comforts/rules/PostRules.java '            new Wild("pigeon", "#c:is_plains", 8, 2, 4));' '            new Wild("pigeon", "#c:is_plains", 0, 2, 4));'
expect "a bird that spawns at weight nought" "which is never"

edit $M/src/main/java/com/elysium/comforts/block/MailboxBlockEntity.java 'if (level != null && PostRules.lostOn(level.random.nextInt(1000))) {' 'if (false) {'
expect "a loss priced and never rolled" "rolled nowhere"

edit $M/src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json '"elysiumcomforts:lost_mail_from_cartographer"' '"elysiumcomforts:nothing"'
expect "a loot modifier written and never listed" "never applies it"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all comforts checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "comforts: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
