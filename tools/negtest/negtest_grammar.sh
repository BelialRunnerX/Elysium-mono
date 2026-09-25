#!/usr/bin/env bash
# The room grammar, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-wilds
GR=$M/src/main/java/com/elysium/wilds/rules/GrammarRules.java
ST=$M/src/main/java/com/elysium/wilds/structure/GrammarStructure.java
RP=$M/src/main/java/com/elysium/wilds/structure/RoomPiece.java
H=$M/tools/GrammarHarness.java
G=$M/tools/gen_wilds.py
V=$M/validate.py
SNAP=/tmp/gram_snap
OUT=/tmp/gram_neg
pass=0; fail=0
if ! python3 "$V" >/dev/null 2>&1; then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  python3 "$V" 2>&1 | tail -5
  exit 3
fi
rm -rf $SNAP && mkdir -p $SNAP
cp -a $M/src/main/java/com/elysium/wilds $SNAP/wilds
cp -a $M/tools $SNAP/tools
cp -a $M/src/main/resources $SNAP/resources
cp -a $M/validate.py $SNAP/validate.py
restore() {
  rm -rf $M/src/main/java/com/elysium/wilds $M/tools $M/src/main/resources
  cp -a $SNAP/wilds $M/src/main/java/com/elysium/wilds
  cp -a $SNAP/tools $M/tools
  cp -a $SNAP/resources $M/src/main/resources
  cp -a $SNAP/validate.py $M/validate.py
}
trap 'restore' EXIT INT TERM
out() {
  rm -rf $OUT && mkdir -p $OUT
  if ! javac -nowarn -d $OUT $M/src/main/java/com/elysium/wilds/rules/*.java "$H" 2>&1 | grep -v JAVA_TOOL; then :; fi
  java -cp $OUT com.elysium.wilds.rules.GrammarHarness 2>&1 | grep -v JAVA_TOOL
}
worldout() { python3 "$V" 2>&1 | grep -v JAVA_TOOL; }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
wexpect() { local w="$2" g; g="$(worldout)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }
regen() { (cd $M && python3 tools/gen_wilds.py >/dev/null 2>&1); }

echo "the one thing this file exists for: a room you can walk to"
edit $GR '                Room other = room(there.room());
                if (other == null || !other.opens(opposite(way))) {
                    continue;
                }' '                Room other = room(there.room());
                if (other == null) {
                    continue;
                }'
expect "a room walked into through a wall, because only one side has a door" "is counted as reachable"

edit $GR '            if (next == null || !next.opens(opposite(way))) {
                continue;
            }' '            if (next == null) {
                continue;
            }'
expect "a room attached where it has no door back, which strands it" "has a room nobody can walk to"

edit $GR '        List<String> stranded = new ArrayList<>();' '        if (true) { return List.of(); }
        List<String> stranded = new ArrayList<>();'
expect "a reachability check that cannot report anything" "is not reported as unreachable"

echo "two rooms in one place"
edit $GR '            if (!taken.add(at(x, y, z))) {
                continue;
            }' '            taken.add(at(x, y, z));
            if (false) {
                continue;
            }'
expect "a room put where a room already is" "has two rooms in one place"

edit $GR '            String key = at(one.x(), one.y(), one.z());
            if (!seen.add(key)) {
                bad.add("two rooms at " + key);
            }' '            String key = at(one.x(), one.y(), one.z());
            if (false) {
                bad.add("two rooms at " + key);
            }'
expect "an overlap check that cannot report anything" "are not reported, so a chest can be inside a staircase"

echo "the box a building claimed"
edit $GR '        int reach = (rules.box() - 1) / 2;' '        int reach = rules.box();'
expect "a building that spills out of the box it claimed" "it spills into the chunk next door"

edit $GR '            if (size[axis] > rules.box()) {' '            if (false) {'
expect "a box check that cannot report anything" "is not reported, so it spills into the chunk next door"

echo "laying one out finishing at all"
edit $GR '        for (int tries = 0; tries < TRIES && placed.size() < wanted; tries++) {' '        for (int tries = 0; placed.size() < wanted; tries++) {'
wexpect "a layout with no ceiling on how long it may take" "a chunk that never finishes generating"

echo "the same seed, the same building"
edit $GR '        long made = state * 6364136223846793005L + 1442695040888963407L;
        return made ^ (made >>> 29);' '        return state;'
expect "a sequence that never moves, so every building is the same building" "the sequence stays where it started"

edit $GR '        int wanted = rules.rooms() / 2 + 1
                + (int) Math.floorMod(state >> 16, rules.rooms() / 2 + 1);' '        int wanted = rules.rooms();'
expect "every building of a kind exactly the same size" "so every one of them is the same size"

echo "the grammars themselves"
edit $GR '            new Grammar("shrine", "door", after(
                    "door", "passage",' '            new Grammar("shrine", "vestibule", after(
                    "door", "passage",'
expect "a grammar whose door is not a room" "which is not a room"

edit $GR '            if (!canStop) {' '            if (false) {'
expect "a check that cannot notice a grammar with no room it may stop at" "is not reported, so laying one out would stop by running out of tries"

edit $GR '                    if (next.ends()) {' '                    if (one.rooms() < 0) {'
expect "a grammar with no room it may stop at" "so laying one out only ever stops by running out of tries"

echo "the world the buildings are actually in"
edit $G '    "shrine": (40, 16), "keep": (48, 20),' '    "shrine": (40, 40), "keep": (48, 20),'
wexpect "a separation the game refuses, caught before the file is written" "the guarantee has to be smaller than the spacing"

edit $G '    "shrine": (40, 16), "keep": (48, 20),' '    "shrine": (40, 40), "keep": (48, 20),'
edit $G '        if guaranteed >= apart:' '        if False:'
wexpect "a separation the game refuses, so the structure never appears" "the game refuses a separation"

edit $G '            "salt": 900000 + sum(ord(letter) for letter in name),' '            "salt": 900000,'
wexpect "two buildings placed with the same salt, so one always loses" "one of the two is always the one that loses"

edit $G '            "grammar": name,' '            "grammar": "vestibule",'
wexpect "a structure naming a grammar that is not one" "and a structure naming a grammar that is not one never generates"

echo "the building a player walks through"
edit $ST '        GrammarRules.Plan plan = GrammarRules.layOut(grammar, seed);' '        GrammarRules.Plan plan = new GrammarRules.Plan(grammar, java.util.List.of(), new int[] {0, 0, 0}, new int[] {0, 0, 0});'
wexpect "a structure not laid out by the rules the harness proved" "is about a building that does not ship"

edit $RP '            if (!kind.opens(way)) {
                continue;
            }' '            if (false) {
                continue;
            }'
wexpect "a room built with gaps where the rules say it has walls" "is not the building the harness walked through"

echo
echo "restoring and re-checking"
restore
(cd $M && python3 tools/gen_wilds.py >/dev/null 2>&1)
if python3 "$V" >/dev/null 2>&1; then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "grammar: $pass caught, $fail missed"
[[ $fail -eq 0 ]] || exit 1
