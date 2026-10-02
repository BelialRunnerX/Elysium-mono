#!/usr/bin/env bash
# A drawer's seal and key, broken one way at a time.
#
# A drawer's lock and its voiding were saved, loaded and obeyed for as long as
# the drawer existed, and nothing a player could hold set either: only /data
# did. A void seal and a drawer key are how a survival player does now. Each
# case breaks one rule the logistics validator or its harness holds that to,
# runs the validator, and expects the sentence that names it.
#
# No case regenerates anything, so the snapshot is the files the cases touch
# rather than the whole module: restoring 3,500 files after every case is most
# of an hour, and restoring these is seconds.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
M=$ROOT/elysium-logistics
S=$M/src/main/java/com/elysium/logistics
R=$M/src/main/resources
SNAP=/tmp/logistics_drawer_snap.tar
KEPT="elysium-logistics/src/main/java elysium-logistics/src/main/resources/data/elysiumlogistics/recipe/drawer_key.json"
pass=0; fail=0
(cd $ROOT && tar cf $SNAP $KEPT)
# Tried again, up to four times in all, when it fails: on Windows a file
# another process has open cannot be replaced, and one refused restore would
# leave the next case testing a broken file.
restore() { local try; for try in 1 2 3; do (cd $ROOT && tar xf $SNAP 2>/dev/null) && return 0; sleep 2; done
  (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 300 python3 validate.py 2>&1); }
# A run that crashed, or could not compile the modules below, is run again
# before it counts: the validator compiles elysium-industry's rules, and that
# module's own negtests restore it with rm and tar, so a run can find it half
# extracted. No case here is answered by either, so neither is ever a catch.
answer() { local w="$1" g try; for try in 1 2 3 4 5; do g="$(out)"
    if [[ "$g" == *"$w"* ]]; then break; fi
    if [[ "$g" != *"Traceback"* && "$g" != *"do not compile against the JDK"* ]]; then break; fi
    sleep 15; done; printf '%s\n' "$g"; }
expect() { local w="$2" g; g="$(answer "$w")"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "$g" | grep -E "^  - |PROBLEM|passed|Traceback|Error" | head -4 | cut -c1-200 | sed 's/^/         got: /'
    fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; restore; exit 2; }; }

DB=$S/block/DrawerBlock.java
DST=$S/block/DrawerStore.java
LRU=$S/rules/LogisticRules.java
LIT=$S/registry/LogisticItems.java

echo "the key and the seal, used on a drawer"
edit $DB '        if (held.is(ours(LogisticRules.DRAWER_KEY))) {
            player.displayClientMessage(into.turnKey(), true);
            return ItemInteractionResult.SUCCESS;
        }
' ''
expect "a key that does nothing to a drawer, and is filed away in it" "does not turn in it, or is spent doing it"

edit $DB '            player.displayClientMessage(into.turnKey(), true);' '            player.displayClientMessage(into.turnKey(), true);
            held.shrink(1);'
expect "a key used up by turning it" "does not turn in it, or is spent doing it"

edit $DB '        if (held.is(ours(LogisticRules.DRAWER_SEAL))) {
            player.displayClientMessage(into.seal(held), true);
            return ItemInteractionResult.SUCCESS;
        }
' ''
expect "a seal that is never fitted to a drawer" "is not fitted to it, so a drawer voids for /data"

edit $DB '        // The key and the seal are asked about before anything goes in, or' '        if (into.offer(held)) {
            return ItemInteractionResult.SUCCESS;
        }
        // The key and the seal are asked about before anything goes in, or'
expect "a drawer that puts the key away before turning it" "filed away inside it"

edit $DB '        player.displayClientMessage(into.says(), true);
        return ItemInteractionResult.CONSUME;' '        return ItemInteractionResult.CONSUME;'
expect "a drawer that refuses in silence" "says nothing, so a locked one refusing a new kind"

echo "fitting the seal, and getting it back"
edit $DB '            if (!LogisticRules.takesSeal(store.voiding())) {' '            if (false) {'
expect "a drawer that takes a second seal" "takes a second seal and spends it on nothing"

edit $DB '            store.voiding(true);' '            store.voiding(false);'
expect "a seal spent to void nothing" "does not turn the drawer's voiding on"

edit $DB '            held.shrink(1);' ''
expect "a seal that is never spent" "one seal seals every drawer in the world"

edit $DB '            Component name = held.getHoverName();
            if (!LogisticRules.takesSeal(store.voiding())) {' '            Component name = held.getHoverName();
            held.shrink(1);
            if (!LogisticRules.takesSeal(store.voiding())) {'
edit $DB '            store.voiding(true);
            held.shrink(1);' '            store.voiding(true);'
expect "a seal spent even when it is refused" "or a refusal costs a seal"

edit $DB '            ItemStack seal = sealsHeld();
            store.voiding(false);' '            store.voiding(false);
            ItemStack seal = sealsHeld();'
expect "a seal taken out after the drawer has forgotten it" "does not hand back the seal that was fitted"

edit $DB '                    LogisticRules.sealsIn(store.voiding()));' '                    1);'
expect "a drawer that hands back a seal whether it had one or not" "so a seal is made or lost on the way out"

edit $DB '            if (player.isShiftKeyDown() && from.store().voiding()) {' '            if (false) {'
expect "a seal that never comes back out" "a drawer sealed once voids for good"

edit $DB '                if (player.getMainHandItem().isEmpty()) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, seal);
                } else {
                    player.getInventory().placeItemBackInInventory(seal);
                }' ''
expect "a seal taken out and handed to nobody" "so taking it out destroys it"

edit $DB '            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), from.sealsHeld());' ''
expect "a broken drawer that destroys its seal" "destroys the seal fitted to it"

echo "turning the key"
edit $DB '                store.locked(false);' '                store.locked(true);'
expect "a key that cannot unlock" "a drawer locked once stays locked"

edit $DB '            if (!LogisticRules.mayLock(store.kindsKept())) {' '            if (false) {'
expect "a key that locks an empty drawer" "a key locks a drawer with nothing in it"

edit $DB '            store.locked(true);' '            store.locked(false);'
expect "a key that never locks" "a key never locks a drawer"

edit $DST '        counts[slot] -= given;
        if (!LogisticRules.keepsKind(locked, counts[slot])) {' '        counts[slot] -= given;
        if (counts[slot] <= 0) {'
expect "a locked drawer that forgets its kind when emptied" "is not the lock it obeys"

edit $DST '        for (int slot = 0; slot < kinds.length; slot++) {
            if (!LogisticRules.keepsKind(locked, counts[slot])) {
                kinds[slot] = ItemStack.EMPTY;
            }
        }
' ''
expect "an unlocked drawer still locked to what it last held" "stays locked to them while it says it is empty"

echo "what the drawer says"
edit $DB '            if (store.locked()) {
                line.append' '            if (false) {
                line.append'
expect "a locked drawer that does not say so" "a locked drawer does not say it is locked"

edit $DB '            if (store.voiding()) {
                line.append' '            if (false) {
                line.append'
expect "a sealed drawer that does not say so" "a sealed drawer does not say it voids overflow"

edit $DB '                line = Component.translatable("elysiumlogistics.drawer.empty",' '                return Component.translatable("elysiumlogistics.drawer.empty",'
expect "an empty drawer that says nothing of its lock or seal" "an empty locked drawer refuses things and never says why"

edit $DST '        return LogisticRules.ceiling(drawer.room(), voiding);' '        return drawer.room();'
expect "a sealed drawer that tells a hopper it is full at its room" "every hopper stops feeding it there"

echo "the rules, executed by the harness"
edit $LRU '        return !voiding;' '        return true;'
expect "rules that let a drawer take a second seal" "a sealed drawer takes another seal"

edit $LRU '        return voids(voiding) ? Integer.MAX_VALUE : Math.max(0, room);' '        return Math.max(0, room);'
expect "rules under which a sealed drawer is full at its room" "stops feeding it there and the seal voids"

edit $LRU '        return voiding ? 1 : 0;' '        return 0;'
expect "rules that give no seal back" "did not add up to the seals the hand started with"

edit $LRU '        return locked || count > 0;' '        return count > 0;'
expect "rules in which a lock keeps nothing" "the lock does nothing a hopper would notice"

edit $LRU '        return kinds > 0;' '        return true;'
expect "rules that let a key lock an empty drawer" "the rules let a key lock a drawer holding nothing"

edit $LRU '    public static final String DRAWER_SEAL = "void_seal";' '    public static final String DRAWER_SEAL = "sieve_mesh";'
expect "a drawer sealed with a part that is not the void part (harness)" "has learned nothing about a drawer"

edit $LRU '    public static final String DRAWER_SEAL = "void_seal";' '    public static final String DRAWER_SEAL = "sieve_mesh";'
expect "a drawer sealed with a part that is not the void part (validator)" "so the seal a bag takes does nothing to a drawer"

edit $LRU '    public static final String DRAWER_KEY = "drawer_key";' '    public static final String DRAWER_KEY = "small_pocket";'
expect "a key that shares its id with a pack part" "the drawer key shares its id with a pack or a part"

edit $LRU '    public static final String DRAWER_KEY = "drawer_key";' '    public static final String KEY_FOR_DRAWERS = "drawer_key";'
expect "rules that no longer name the key" "do not name a drawer's key and seal"

echo "the key as an item a survival player can make"
rm $R/data/elysiumlogistics/recipe/drawer_key.json
expect "a key with no recipe" "cannot make one or cannot tell what it is"

edit $R/data/elysiumlogistics/recipe/drawer_key.json '"id": "elysiumlogistics:drawer_key"' '"id": "minecraft:tripwire_hook"'
expect "a key recipe that makes something else" "so the key itself is made by nothing"

edit $LIT '    public static final DeferredItem<Item> DRAWER_KEY =
            ITEMS.registerSimpleItem(LogisticRules.DRAWER_KEY);' '    public static final DeferredItem<Item> DRAWER_KEY = null;'
expect "a key recipe for an item that is never registered" "its recipe makes an item that does not"

edit $LIT '                        output.accept(DRAWER_KEY.get());' ''
expect "a key in no creative tab" "the drawer key is in no creative tab"

echo
echo "restoring and re-checking"
restore
g="$(answer "all logistics checks passed")"
if [[ "$g" == *"all logistics checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
rm -f $SNAP
echo "logistics (the drawer's seal and key): $pass caught, $fail missed"
[ "$fail" -eq 0 ]
