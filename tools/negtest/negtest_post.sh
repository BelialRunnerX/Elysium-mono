#!/usr/bin/env bash
# A postal system, broken one way at a time.
#
# Mail is worth having only if it takes time and can go wrong. Every case below
# is a way the in-between could quietly collapse into one end or the other -- a
# parcel that arrives at once, a parcel that never arrives, or a parcel that
# stops existing on the way.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-comforts
P=$M/src/main/java/com/elysium/comforts/rules/PostRules.java
MB=$M/src/main/java/com/elysium/comforts/block/MailboxBlock.java
BE=$M/src/main/java/com/elysium/comforts/block/MailboxBlockEntity.java
PH=$M/src/main/java/com/elysium/comforts/block/PigeonholeBlock.java
BI=$M/src/main/java/com/elysium/comforts/entity/PigeonEntity.java
MI=$M/src/main/java/com/elysium/comforts/item/MailItem.java
RB=$M/src/main/java/com/elysium/comforts/registry/ComfortBlocks.java
RE=$M/src/main/java/com/elysium/comforts/registry/ComfortEntities.java
RI=$M/src/main/java/com/elysium/comforts/registry/ComfortItems.java
MD=$M/src/main/java/com/elysium/comforts/ElysiumComforts.java
CL=$M/src/main/java/com/elysium/comforts/client/ComfortsClient.java
G=$M/tools/gen_comforts.py
V=$M/validate.py
SNAP=/tmp/post_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit."
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-comforts)
restore() { rm -rf $ROOT/elysium-comforts && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }
regen() { (cd $M && python3 tools/gen_comforts.py >/dev/null 2>&1); }
remove() { rm -f "$1"; }
expect_regen() { regen; expect "$1" "$2"; }

echo "a journey that is a journey"
edit $P '        if (soonest <= 0) {' '        if (false) {'
expect "a floor of nought ticks, unreported" "a floor of nought ticks is not reported"

edit $P '            if (took < soonest) {' '            if (false) {'
expect "a parcel arriving sooner than the post promises, unreported" "no handling at either end"

edit $P '        if (latest <= soonest) {' '        if (false) {'
expect "a band with no width in it, unreported" "a band with no width in it"

edit $P '            if (took > latest) {' '            if (false) {'
expect "a journey past the longest wait, unreported" "runs past the longest wait"

edit $P '    public static final int HANDLING = 600;' \
        '    public static final int HANDLING = 0;'
expect "no handling at either end, so the post is an ender chest" "sooner than the 600 the post"

edit $P '    public static final int FURTHEST = 5_000;' \
        '    public static final int FURTHEST = 9_000;'
expect "a journey longer than a player will wait" "past the 6000 the post promises"

edit $P '    public static final int SOONEST = 600;' \
        '    public static final int SOONEST = 900;'
expect "a floor the arithmetic does not reach" "sooner than the 900 the post"

edit $P '    public static final int LATEST = 6_000;' \
        '    public static final int LATEST = 5_000;'
expect "a ceiling the arithmetic goes past" "past the 5000 the post"

edit $P '    public static final int LATEST = 6_000;' \
        '    public static final int LATEST = 600;'
expect "a ceiling set down onto the floor" "distance means nothing"

edit $P '        int far = Math.max(0, Math.min(blocks, furthest));' \
        '        int far = Math.max(0, blocks);'
expect "a journey that never stops growing" "the journey never stops growing"

edit $P '        double journey = blocksATick <= 0.0D ? 0.0D : far / blocksATick;' \
        '        double journey = blocksATick <= 0.0D ? 0.0D : (furthest - far) / blocksATick;'
expect "a post that is quicker the further it goes" "quicker the further it goes"

echo "what it costs to send"
edit $P '                if (asGood && better) {' '                if (false) {'
expect "a parcel dominated on room and price at once, unreported" "more room for less paper"

edit $P '            if (one.posted() && one.postage() <= 0) {' '            if (false) {'
expect "free postage on something posted, unreported" "free postage on something that is posted"

edit $P '            if (!one.posted() && one.postage() > 0) {' '            if (false) {'
expect "a charge on something never posted, unreported" "a charge on something that is never posted"

edit $P '                if (one.postage() <= other.postage()) {' '                if (false) {'
expect "a seal that costs nothing, unreported" "a seal that costs no more than no seal"

edit $P '            new Mail(PAPER_BOX, 9, 0, false, false, false),' \
        '            new Mail(PAPER_BOX, 9, 0, false, false, true),'
expect "an empty box handed to the post for nothing" "postage is free and the price of sending"

edit $P '            new Mail(LOST_MAIL, 3, 0, false, false, false));' \
        '            new Mail(LOST_MAIL, 3, 2, false, false, false));'
expect "a charge for posting what turns up when nothing arrived" "charged for something they cannot send"

edit $P '            new Mail(SEALED_PACKAGE, 9, 6, true, false, true),' \
        '            new Mail(SEALED_PACKAGE, 9, 4, true, false, true),'
expect "a seal on a parcel that costs no more than none" "the seal is free"

echo "the eight kinds"
edit $P '            if (!kinds.contains(one.id())) {' '            if (false) {'
expect "mail of a kind nothing names, unreported" "mail of a kind nothing else names"

edit $P '            if (!named.add(one.id())) {
                bad.add(one.id() + " is written twice, so one of the two rows "' \
        '            if (false) {
                bad.add(one.id() + " is written twice, so one of the two rows "'
expect "two pieces of mail under one name, unreported" "two pieces of mail written under one name"

edit $P '            if (!made.contains(kind)) {' '            if (false) {'
expect "a kind of mail nothing makes, unreported" "a kind of mail nothing makes"

echo "payment on delivery, and the swindle it could be"
edit $P '            if (now <= before) {' '            if (false) {'
expect "payback windows out of order, unreported" "payback windows out of order"

edit $P '            if (window <= latest) {' '            if (false) {'
expect "a payback window shorter than a delivery, unreported" "shorter than the longest delivery"

edit $P '    public static final int SHORT_WAIT = 30;' \
        '    public static final int SHORT_WAIT = 4;'
expect "a payback clock that runs out before the goods can arrive" "ships a swindle as a feature"

edit $P '    public static final int MEDIUM_WAIT = 180;' \
        '    public static final int MEDIUM_WAIT = 20;'
expect "the usual payback window put below the short one" "are not in order"

echo "what is lost"
edit $P '        if (inAThousand <= 0) {' '        if (false) {'
expect "a post that never loses anything, unreported" "a post that never loses anything"

edit $P '        if (inAThousand > most) {' '        if (false) {'
expect "a post that loses more than it allows, unreported" "loses more than it allows"

edit $P '        if (worth >= 1.0D) {' '        if (false) {'
expect "lost mail worth as much as the parcel, unreported" "worth as much as what was posted"

edit $P '        if (worth < 0.0D) {' '        if (false) {'
expect "lost mail worth less than nothing, unreported" "worth less than nothing"

edit $P '    public static final int LOSS_IN_A_THOUSAND = 15;' \
        '    public static final int LOSS_IN_A_THOUSAND = 0;'
expect "nothing ever lost, so a sealed parcel and a chest are the same" "so a sealed parcel and a chest"

edit $P '    public static final double LOST_MAIL_WORTH = 0.25D;' \
        '    public static final double LOST_MAIL_WORTH = 1.5D;'
expect "losing a parcel paying better than it arriving" "the way to play the post is to post to nowhere"

echo "the counters you can write to"
edit $P '            if (one.away() > furthest) {' '            if (false) {'
expect "a service past the cap, unreported" "past where the journey stops growing"

edit $P '            if (!at.add(one.away())) {' '            if (false) {'
expect "two services at one distance, unreported" "two services the same distance off"

edit $P '            new Service("supply_service", 1_000, false),' \
        '            new Service("supply_service", 2_000, false),'
expect "two services at the same counter under two names" "the same counter under two names"

edit $P '        if (written.length() > LONGEST_ADDRESS) {' '        if (false) {'
expect "an address longer than the post will carry" "past the 24 the post says is the longest"

edit $P '        if (written.isEmpty()) {
            return false;
        }' '        if (false) {
            return false;
        }'
expect "an empty address, which addresses everything" "the empty string is an address"

echo "eleven woods and a wall of holes"
edit $P '            if (!made.contains(pigeonhole(wood))) {' '            if (false) {'
expect "a wood with no pigeonhole, unreported" "a wood with no pigeonhole in it"

edit $P '            if (!known) {' '            if (false) {'
expect "a pigeonhole in a wood the pack has not got, unreported" "a pigeonhole in a wood the pack does not have is not"

edit $P '            if (!made.add(hole)) {' '            if (false) {'
expect "a pigeonhole made twice, unreported" "a pigeonhole made twice is not reported"

edit $P '        if (foulest <= 0) {' '        if (false) {'
expect "a pigeonhole that never gets dirty, unreported" "never gets dirty is not reported"

edit $P '            if (ladder.get(level) <= ladder.get(level - 1)) {' \
        '            if (false) {'
expect "a pigeonhole worth no more for being dirtier, unreported" "worth no more at level two"

edit $P '        if (!ladder.isEmpty() && ladder.get(0) != 0) {' '        if (false) {'
expect "a clean pigeonhole that pays anyway, unreported" "clean pigeonhole that pays anyway"

edit $P '        if (ladder.isEmpty()) {' '        if (false) {'
expect "a pigeonhole with no ladder at all, unreported" "no ladder at all is not reported"

edit $P '        if (atTop != past) {' '        if (false) {'
expect "a top level that is not the top, unreported" "goes on paying past its dirtiest"

edit $P '        return held * 2;' '        return 2;'
expect "a pigeonhole worth the same however long a bird has been in it" "there is no reason to leave a bird in it"

edit $P '        int held = Math.max(0, Math.min(level, foulest));' \
        '        int held = Math.max(0, level);'
expect "a pigeonhole whose top level is not the top" "the top level is not the top"

echo "two birds, and the loop that has to close"
edit $P '            if (!homes.add(one.home())) {' '            if (false) {'
expect "two birds in one dimension, unreported" "two birds that belong in one dimension"

edit $P '            if (!named.add(one.id())) {
                bad.add(one.id() + " is written twice, so one of the two rows is "' \
        '            if (false) {
                bad.add(one.id() + " is written twice, so one of the two rows is "'
expect "two birds under one name, unreported" "two birds written under one name"

edit $P '            if (one.becomes() == null) {
                bad.add(one.id() + " turns into nothing wherever it is taken, so "' \
        '            if (false) {
                bad.add(one.id() + " turns into nothing wherever it is taken, so "'
expect "a bird that turns into nothing, unreported" "turns into nothing wherever it is carried"

edit $P '            if (among(birds, one.becomes()) == null) {' '            if (false) {'
expect "a bird that turns into something absent, unreported" "something this module does not have"

edit $P '            if (!one.id().equals(then.becomes())) {' '            if (false) {'
expect "a one-way change, unreported" "a one-way change, where a bird carried below"

edit $P '            new Bird("charred_pigeon", BELOW, "pigeon", 300, 1));' \
        '            new Bird("charred_pigeon", BELOW, null, 300, 1));'
expect "a bird carried below that never comes back" "turns into nothing wherever it is taken"

edit $P '            new Bird("pigeon", ABOVE, "charred_pigeon", 300, 4),' \
        '            new Bird("pigeon", BELOW, "charred_pigeon", 300, 4),'
expect "two birds that never stop turning into each other" "never stop turning into each other"

echo "the stamps"
edit $P '            if (!have.add(one)) {
                bad.add("the symbol " + one + " is cut twice, so one of the two "' \
        '            if (false) {
                bad.add("the symbol " + one + " is cut twice, so one of the two "'
expect "a symbol cut twice, unreported" "a symbol cut twice is not reported"

edit $P '        for (char one = '"'"'a'"'"'; one <= '"'"'z'"'"'; one++) {
            if (!have.contains(String.valueOf(one))) {' \
        '        for (char one = '"'"'a'"'"'; one <= '"'"'z'"'"'; one++) {
            if (false) {'
expect "an alphabet with letters missing, unreported" "an alphabet with letters missing"

edit $P '        for (char one = '"'"'0'"'"'; one <= '"'"'9'"'"'; one++) {
            if (!have.contains(String.valueOf(one))) {' \
        '        for (char one = '"'"'0'"'"'; one <= '"'"'9'"'"'; one++) {
            if (false) {'
expect "a set of figures with numbers missing, unreported" "figures with numbers missing"

edit $P '            if (row.getValue() != symbols.size()) {' '            if (false) {'
expect "a material barely cut at all, unreported" "a material barely cut at all"

edit $P '        if (seals.size() != want) {' '        if (false) {'
expect "a roster that does not come to what the lists multiply to, unreported" "what the two lists multiply to"

edit $P '            if (!have.add(one)) {
                bad.add(one + " is in the roster twice' \
        '            if (false) {
                bad.add(one + " is in the roster twice'
expect "a seal in the roster twice, unreported" "a seal in the roster twice is not reported"

echo "the compact constructors"
edit $P '            if (slots < 0) {' '            if (false) {'
expect "mail with less than no room, accepted" "less than no room in it is accepted"

edit $P '            if (postage < 0) {' '            if (false) {'
expect "mail costing less than nothing, accepted" "costs less than nothing to send is accepted"

edit $P '            if (away < 0) {' '            if (false) {'
expect "a service less than nowhere away, accepted" "less than nowhere away is accepted"

edit $P '            if (variants <= 0) {' '            if (false) {'
expect "a bird with no colourings, accepted" "no colourings at all"

edit $P '            if (becomes != null && afterTicks <= 0) {' '            if (false) {'
expect "a bird that changes in no time at all, accepted" "changes in no time at all is accepted"

echo "the journey the block keeps"
edit $MB '        if (!box.send(one, level.getGameTime(), PostRules.UNKNOWN_DISTANCE)) {' \
        '        if (!box.send(one, level.getGameTime(), 1500)) {'
expect "a distance typed into the block beside the rules" "sent 1500 blocks by a number typed into"

edit $BE '        arrives = now + PostRules.travelTicks(blocks);' \
        '        arrives = now + (long) blocks;'
expect "a mailbox doing the journey sum itself" "does not ask the rules how long a parcel takes"

edit $BE '        arrives = now + PostRules.travelTicks(blocks);' \
        '        arrives = now + 400;'
expect "a landing time typed into the block" "a number typed into the block"

edit $BE '        if (parcel.isEmpty() || expecting()) {' '        if (parcel.isEmpty()) {'
expect "a second parcel accepted while one is in the air" "takes a second parcel while one is already"

edit $BE '        arrives = now + PostRules.SOONEST;' '        arrives = now;'
expect "a parcel that lands at a full mailbox and stops existing" "is not sent round again"

edit $BE '        if (!flying.isEmpty()) {
            all.add(flying.copy());
        }' '        if (false) {
            all.add(flying.copy());
        }'
expect "a box broken while a parcel is coming, which eats it" "keeps whatever was in the air towards it"

edit $BE '    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {' \
        '    protected void neverSaves(CompoundTag tag, HolderLookup.Provider lookup) {'
expect "a mailbox that never writes down what is in it" "never writes down what is in it"

edit $BE '    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {' \
        '    protected void neverLoads(CompoundTag tag, HolderLookup.Provider lookup) {'
expect "a mailbox that never reads back what was in it" "never reads back what was in it"

edit $BE '        ContainerHelper.saveAllItems(tag, held, lookup);' \
        '        tag.putInt("Count", held.size());'
expect "a mailbox saving its contents its own way" "some way other than the game"

edit $MB '                Containers.dropContents(level, pos, all);' \
        '                all.clear();'
expect "breaking a mailbox keeping what was in it" "the one way to move a postbox is to lose the post"


echo "the parcel that must never be in two places"
edit $MI '        stack.remove(ComfortComponents.CONTENTS.get());
        for (ItemStack one : held) {' \
        '        for (ItemStack one : held) {'
expect "a parcel that hands its contents over and keeps them" "hands its contents over and keeps them as well"

edit $MI '        NonNullList<ItemStack> held = inside(stack, player.registryAccess());
        stack.remove(ComfortComponents.CONTENTS.get());
        for (ItemStack one : held) {
            if (!player.getInventory().add(one)) {
                player.drop(one, false);
            }
        }' \
        '        NonNullList<ItemStack> held = inside(stack, player.registryAccess());
        for (ItemStack one : held) {
            if (!player.getInventory().add(one)) {
                player.drop(one, false);
            }
        }
        stack.remove(ComfortComponents.CONTENTS.get());'
expect "a parcel emptied after it has handed everything over" "before clearing itself"

edit $MI '        if (sealed() && !addressedTo(stack, player)) {' '        if (false) {'
expect "a sealed parcel that opens in anybody hands" "opens in anybody"

echo "the birds in the world"
edit $BI '        PostRules.Bird found = PostRules.bird(name);' \
        '        PostRules.Bird found = PostRules.BIRDS.get(0);'
expect "a bird that does not find its own row" "does not find its own row"

edit $BI '        server.addFreshEntity(made);
        discard();' '        discard();
        server.addFreshEntity(made);'
expect "a bird discarded before its replacement is placed" "discarded before its replacement"

edit $BI '        server.addFreshEntity(made);' '        made.setInvisible(false);'
expect "a bird that never becomes the other bird" "does not become the other bird"

edit $BI '    public void addAdditionalSaveData(CompoundTag tag) {' \
        '    public void neverSaves(CompoundTag tag) {'
expect "a bird that never writes down its own clocks" "never writes down how long it has been"

edit $BI '    public void readAdditionalSaveData(CompoundTag tag) {' \
        '    public void neverReads(CompoundTag tag) {'
expect "a bird that never reads back its own clocks" "never reads back its own clocks"

echo "the pigeonhole in the world"
edit $PH '            int many = PostRules.wasteAt(level_);' '            int many = 4;'
expect "a pigeonhole paying an amount it worked out itself" "an amount it works out itself"

edit $PH '                popResource(level, pos, new ItemStack(Items.BONE_MEAL, many));' \
        '                popResource(level, pos, new ItemStack(Items.BONE_MEAL, 3));'
expect "a bone meal count typed into the block" "by a number typed into the block"

echo "nothing registered by hand"
edit $RB '        for (String wood : PostRules.WOODS) {' \
        '        for (String wood : java.util.List.of("oak", "spruce")) {'
expect "pigeonholes named by hand" "named by hand rather than walked"

edit $RB '        if (made.size() != PostRules.WOODS.size()) {' '        if (false) {'
expect "nothing counting the pigeonholes registered" "nothing counts the pigeonholes"

edit $RE '        for (PostRules.Bird bird : PostRules.BIRDS) {
            made.put(bird.id(), TYPES.register(bird.id(),' \
        '        for (PostRules.Bird bird : java.util.List.of(PostRules.BIRDS.get(0))) {
            made.put(bird.id(), TYPES.register(bird.id(),'
expect "birds registered from a list somebody wrote out" "the birds are named by hand"

edit $RE '        if (made.size() != PostRules.BIRDS.size()) {' '        if (false) {'
expect "nothing counting the birds registered" "nothing counts the birds"

edit $RI '        for (com.elysium.comforts.rules.PostRules.Mail one' \
        '        for (com.elysium.comforts.rules.PostRules.Mail notWalked'
expect "mail named by hand" "the mail is named by hand"

edit $MD '        com.elysium.comforts.registry.ComfortEntities.TYPES.register(modEventBus);' \
        ''
expect "birds never put on the mod event bus" "never put on the mod event bus"

edit $CL '        for (com.elysium.comforts.rules.PostRules.Bird bird
                : com.elysium.comforts.rules.PostRules.BIRDS) {' \
        '        for (com.elysium.comforts.rules.PostRules.Bird bird
                : java.util.List.<com.elysium.comforts.rules.PostRules.Bird>of()) {'
expect "a renderer registered for one bird of two" "black-and-magenta cube"

echo "what the generator writes"
edit $G '    "payback_box": ["p p", "hsh", "ppp"],' \
        '    "payback_box": ["ppp", "psp", "pwp"],'
expect_regen "two kinds of mail crafted the same way" "are crafted the same way"

edit $G '        used = {symbol for row in shape for symbol in row if symbol != " "}' \
        '        used = set("pwhs")'
expect_regen "a recipe key defining a symbol its pattern never uses" "the game refuses the whole recipe"

edit $G '            for level in range(FOULEST + 1)' '            for level in range(FOULEST)'
expect_regen "a pigeonhole with no model at its dirtiest" "has no model at waste level"

edit $G '        for variant in range(one[1]):' '        for variant in range(1):'
expect_regen "a bird colouring with no skin drawn for it" "there is no skin for it"

edit $G '        made[f"item.{NS}.{name}"] = titled(name)' \
        '        made[f"item.{NS}.{name}_x"] = titled(name)'
expect_regen "a kind of mail with no name" "reads as its own translation key"

edit $G '        made[f"entity.{NS}.{one}"] = titled(one)' \
        '        made[f"entity.{NS}.{one}_x"] = titled(one)'
expect_regen "a bird with no name" "reads as its own translation key"

remove $M/src/main/resources/assets/elysiumcomforts/textures/item/sealed_letter.png
expect "a kind of mail with no sprite drawn for it" "holding a missing-texture cube"

echo "the rules are all asked"
edit $P '        bad.addAll(faultsInPayback(WAITS, LATEST));' ''
expect "a rule written, compiled and never added up" "never added up by the post"

echo
echo "post: $pass caught, $fail missed"
[ $fail -eq 0 ] || exit 1
