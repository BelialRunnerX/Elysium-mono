#!/usr/bin/env bash
# Somewhere to sleep that is not a bed, broken one way at a time.
#
# The feature is a partition of the clock and a spawn that does not move.
# Every rule below is a way one of those could quietly stop being true, and
# not one of them would look like a bug from inside the game.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-comforts
R=$M/src/main/java/com/elysium/comforts/rules/RestRules.java
BL=$M/src/main/java/com/elysium/comforts/block/RestBlock.java
K=$M/src/main/java/com/elysium/comforts/event/RestKeeper.java
CB=$M/src/main/java/com/elysium/comforts/registry/ComfortBlocks.java
G=$M/tools/gen_comforts.py
V=$M/validate.py
SNAP=/tmp/resting_snap.tar
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
expect_regen() { regen; expect "$1" "$2"; }

echo "sixteen colours, thirty-three blocks"
edit $R '        if (colours.size() != 16) {' '        if (false) {'
expect "a roster of one colour, unreported" "roster of one colour is not reported"

edit $R '            if (!seen.add(colour)) {' '            if (false) {'
expect "a colour listed twice, unreported" "colour listed twice is not reported"

edit $R '        if (roster.size() != wanted) {' '        if (false) {'
expect "a roster the wrong length, unreported" "one block too long is not reported"

edit $R '            if (!seen.add(one)) {
                bad.add(one + " is in the roster twice' '            if (false) {
                bad.add(one + " is in the roster twice'
expect "a block in the roster twice, unreported" "block in it twice is not reported"

edit $R '        bad.addAll(faultsInMissing(roster()));' '        '
expect "the rule that says every colour exists, never added up" "never added up by the"

echo "the clock, divided between the two"
edit $R '            if (hours.get(kind) == 0 && day > 0) {' '            if (false) {'
expect "a kind usable at no hour at all, unreported" "usable at no hour at all, is not reported"

edit $R '                if (!roster.contains(colour + "_" + kind)) {' '                if (false) {'
expect "a roster with nothing in it at all, unreported" "with nothing in it is not reported"

edit $R '        return at >= DUSK && at < DAWN;' '        return true;'
expect "a world that is night at every hour, so a hammock is never usable" "usable at no hour at all"

edit $R '        return isNight(tick) ? BAG : HAMMOCK;' '        return "wibble";'
expect "an hour at which the thing you may rest in is not a thing" "not a thing this module makes"

echo "waking on the far side of the line"
edit $R '        return BAG.equals(kind) ? DAWN : DUSK;' '        return BAG.equals(kind) ? DUSK : DAWN;'
expect "a rest that wakes you where you can lie straight back down" "wakes you somewhere you can lie straight back down"

edit $R '            if (!KINDS.contains(kind)) {
                bad.add(kind + " is not a thing anybody can rest in' '            if (false) {
                bad.add(kind + " is not a thing anybody can rest in'
expect "waking from a thing nobody can rest in, unreported" "not a kind of rest is not reported"

edit $R '        return Math.floorMod(wakes - at, DAY);' '        return Math.floorMod(wakes - at, DAY) + 1;'
expect "a skip that overshoots the hour it was aiming at" "lands at"

echo "six reasons, six sentences"
edit $R '        if (refusals.size() < 4) {' '        if (false) {'
expect "a module with two ways to say no and six ways to fail, unreported" "six ways to fail is not reported"

edit $R '            if (refusal == null || refusal.isEmpty()) {' '            if (false) {'
expect "a refusal with no words at all, unreported" "no words at all is not reported"

edit $R '            if (!seen.add(refusal)) {' '            if (false) {'
expect "two reasons wearing one sentence, unreported" "wearing one sentence are not reported"

edit $R '        bad.addAll(faultsInRefusals(REFUSALS));' '        '
expect "a resting rule that answers its controls and is never asked about the roster" "never added up by the"

echo "the block a player clicks"
edit $BL '        if (!RestRules.usableAt((int) (level.getDayTime() % RestRules.DAY))
                .equals(kind)) {
            return RestRules.NOT_NOW;
        }' '        '
expect "a rest that works at any hour, so the clock is divided between nothing" "nothing can ever give"

edit $BL '        if (RestRules.HAMMOCK.equals(kind) && !hangsFrom(level, pos)) {
            return RestRules.NO_ROPE;
        }' '        '
expect "a hammock strung from nothing at all" "nothing can ever give"

edit $BL '    public static final int LIES = 4;' '    public static final int LIES = 8;'
expect "a rest drawn a different height from the one it is" "you can see through and cannot walk past"

echo "the three things a bed does that this must not"
edit $K '            event.setCanceled(true);' '            return;'
expect "a rest that moves your spawn, which is the whole feature" "it is a bed with a different"

edit $K '        if (restAt(player.level(), where) != null) {' '        if (true) {'
expect "every bed in the pack losing its spawn instead of ours" "no bed anywhere works"

edit $K '        event.setTimeAddition(level.getDayTime() + RestRules.skipsFrom(tick, kind));' \
       '        event.setTimeAddition(level.getDayTime() + 12000);'
expect "a sleep that skips a number somebody typed" "not the number the rules worked out"

edit $K '        if (RestRules.usableAt(tick).equals(rest.kind())) {
            event.setProblem(null);
        }' '        if (true) {
            event.setProblem(null);
        }'
expect "what may be slept in at this hour decided somewhere other than the rules" "not the partition the game uses"

echo "thirty-three registered, and counted"
edit $CB '        int wanted = RestRules.KINDS.size() * RestRules.COLOURS.size();
        if (made.size() != wanted) {
            throw new IllegalStateException("the rules ask for " + wanted
                    + " place(s) to sleep and " + made.size() + " were registered");
        }' '        '
expect "a registry that never counts the places to sleep it registered" "does not count the places to sleep it registered"

echo "the world it is written out into"
edit $G '        write(os.path.join(DATA, "loot_table/blocks", name + ".json"),
              gives_itself_back(name) if name != "grave" else drops_nothing(name))' \
        '        write(os.path.join(DATA, "loot_table/blocks", name + ".json"),
              drops_nothing(name))'
expect_regen "a rest that vanishes when a creeper clips the floor under it" "does not give itself back"

edit $G '            pattern = (["###", "///"] if kind == KIND_NAMES["HAMMOCK"]
                       else ["###", "..."])' '            pattern = ["###", "..."]'
expect_regen "a bag and a hammock made of exactly the same things" "one of the two kinds is a second name"

edit $G '                "key": {symbol: what for symbol, what in everything.items()
                        if symbol in used},' '                "key": everything,'
expect_regen "a recipe key with a symbol its pattern never uses, which the game refuses whole" "never uses it in its pattern"

edit $G '            everything = {
                "#": {"item": f"minecraft:{colour}_wool"},' '            everything = {
                "@": {"item": f"minecraft:{colour}_wool"},'
expect_regen "a pattern using a symbol its key does not explain" "its key does not say what that is"

edit $R '            "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown",' \
        '            "pink", "gray", "light_gray", "cyan", "purple", "blue", "beige",'
expect "a colour the game does not have" "not one of the game's own dyes"

rm -f $M/src/main/resources/assets/elysiumcomforts/blockstates/red_hammock.json
expect "a rest with no blockstate, which is a missing-texture cube" "has no a blockstate"

edit $G '    "not_now": "Not at this hour. A bag is for the night and a hammock for "
               "the day.",' '    "not_nowt": "Not at this hour. A bag is for the night and a hammock for "
               "the day.",'
expect_regen "a refusal a player is given as a translation key" "told a translation key"

echo "the checks themselves"
edit $V '    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(", REST))' \
        '    _rules = set(re.findall(r"public static List<String> (faultsInZ\w+)\(", REST))'
expect "a rule sweep that finds no resting rules at all" "resting rule(s) are declared at all"

edit $V '    return {"minecraft:" + key.split(".", 2)[2] for key in named
            if key.startswith(("item.minecraft.", "block.minecraft."))}' \
        '    return {"minecraft:" + key.split(".", 2)[2] for key in named
            if key.startswith(("item.mojang.", "block.mojang."))}'
expect "a game list that reads as empty, so the dye check stops quietly" "is checking nothing at all"

rm -f $R
expect "nothing saying how to rest anywhere but a bed" "one bed carried everywhere and never placed"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all comforts checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "resting: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
