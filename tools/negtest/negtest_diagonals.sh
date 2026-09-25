#!/usr/bin/env bash
# The forty-five degrees, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-atelier
D=$M/src/main/java/com/elysium/atelier/rules/DiagonalRules.java
H=$M/tools/DiagonalHarness.java
RB=$M/src/main/java/com/elysium/atelier/registry/AtelierBlocks.java
RI=$M/src/main/java/com/elysium/atelier/registry/AtelierItems.java
G=$M/tools/gen_atelier.py
V=$M/validate.py
SNAP=/tmp/diag_snap
OUT=/tmp/diag_neg
pass=0; fail=0
if ! python3 "$V" >/dev/null 2>&1; then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  python3 "$V" 2>&1 | tail -5
  exit 3
fi
rm -rf $SNAP && mkdir -p $SNAP
cp -a $M/src/main/java/com/elysium/atelier $SNAP/atelier
cp -a $M/tools $SNAP/tools
cp -a $M/src/main/resources $SNAP/resources
cp -a $M/validate.py $SNAP/validate.py
restore() {
  rm -rf $M/src/main/java/com/elysium/atelier $M/tools $M/src/main/resources
  cp -a $SNAP/atelier $M/src/main/java/com/elysium/atelier
  cp -a $SNAP/tools $M/tools
  cp -a $SNAP/resources $M/src/main/resources
  cp -a $SNAP/validate.py $M/validate.py
}
trap 'restore' EXIT INT TERM
out() {
  rm -rf $OUT && mkdir -p $OUT
  if ! javac -nowarn -d $OUT $M/src/main/java/com/elysium/atelier/rules/*.java "$H" 2>&1 | grep -v JAVA_TOOL; then :; fi
  java -cp $OUT com.elysium.atelier.rules.DiagonalHarness 2>&1 | grep -v JAVA_TOOL | grep -v "^DIAG " | grep -v "^DTAG "
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
regen() { (cd $M && python3 tools/gen_atelier.py >/dev/null 2>&1); }

echo "the one thing this file exists for: a run with no gap in it"
edit $D '        int step = wide - 1;' '        int step = wide;'
expect "boxes that meet only at their corners, which the game does not count as meeting" "which the game does not count as meeting"

edit $D '            int[][] beside = {{x - 1, z}, {x + 1, z}, {x, z - 1}, {x, z + 1}};' '            int[][] beside = {{x - 1, z}, {x + 1, z}, {x, z - 1}, {x, z + 1},
                    {x - 1, z - 1}, {x + 1, z + 1}, {x - 1, z + 1}, {x + 1, z - 1}};'
expect "a flood fill that counts a corner as a join, so every gap is invisible" "every diagonal here could have a hole in it"

edit $D '        for (int at = 0; at < SPAN; at += step) {' '        for (int at = 0; at < SPAN - 4; at += step) {'
expect "a run that stops short of the corner" "so a run of them is a dotted line"

edit $D '        return plan[0] && plan[(SPAN - 1) * SPAN + (SPAN - 1)];' '        return true;'
expect "a corner check that cannot report anything" "so the corner check cannot say no"

edit $D '                if (!plan[x * SPAN + z]) {
                    return false;
                }' '                if (false) {
                    return false;
                }'
expect "a middle check that cannot report anything" "so that check cannot say no"

echo "the three thicknesses"
edit $D '            new Kind(FENCE, 3, 8, 3),' '            new Kind(FENCE, 2, 8, 3),'
expect "two diagonals of one thickness" "so two of the three are the same rail with two names"

edit $D '            new Kind(WALL, 4, 6, 3));' '            new Kind(WALL, 8, 6, 3));'
expect "a diagonal thick enough to be a wall" "which is a wall, and the game has walls"

echo "the prices"
edit $D '            new Kind(PANE, 2, 12, 3),' '            new Kind(PANE, 2, 24, 3),'
expect "a recipe that makes more than it takes" "so a loop of crafting it pays"

edit $D '            new Kind(WALL, 4, 6, 3));' '            new Kind(WALL, 4, 1, 3));'
expect "a recipe nobody would ever make" "nobody will ever make one"

edit $D '    public static final int MOST_WASTED = WHOLE;' '    public static final int MOST_WASTED = WHOLE * 16;'
expect "a waste ceiling high enough to allow anything" "the ceiling would allow anything"

echo "the tags an ordinary fence looks for"
edit $D '        if (FENCE.equals(run.kind())) {
            tags.add("minecraft:fences");' '        if (false) {
            tags.add("minecraft:fences");'
wexpect "a diagonal fence outside the game's fence tag" "no ordinary fence meets it"

edit $D '        tags.add(run.material().soft() ? "minecraft:mineable/axe"
                : "minecraft:mineable/pickaxe");' '        tags.add("minecraft:mineable/pickaxe");'
expect "a wooden diagonal worked with a pickaxe" "is worked by the wrong tool"

echo "the world the diagonals are actually in"
edit $RB '        for (DiagonalRules.Run run : DiagonalRules.catalogue()) {' '        for (DiagonalRules.Run run : DiagonalRules.catalogue().subList(0, 8)) {'
wexpect "diagonals listed by hand rather than walked from the catalogue" "not registered by walking the catalogue"

edit $RB '        if (made.size() != DiagonalRules.howMany()) {' '        if (false) {'
wexpect "a registry that does not count what it registered" "counts the diagonals registered"

edit $RI '                        AtelierBlocks.FITTINGS, AtelierBlocks.CLOTH,
                        AtelierBlocks.DIAGONALS)) {' '                        AtelierBlocks.FITTINGS, AtelierBlocks.CLOTH)) {'
wexpect "blocks a player can only place with a command" "only place with a command"

echo "the two files that describe one run"
edit $G '        to = min(at + wide, 16)' '        to = min(at + wide + 1, 16)'
regen
wexpect "a generator drawing a run the block does not collide as" "so it is a rail with a gap in it"

edit $G '    step = wide - 1' '    step = wide'
wexpect "a generator drawing boxes that meet only at their corners" "the generator will not run"

echo "the grid the game actually has"
edit $G '    "diagonal_wall":  ["M M", "  M"],' '    "diagonal_wall":  ["M M", "   ", "  M"],'
wexpect "a recipe pattern with no room for the row its second ingredient goes in" "the game refuses to load the recipe"

echo
echo "restoring and re-checking"
restore
if python3 "$V" >/dev/null 2>&1; then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "diagonals: $pass caught, $fail missed"
[[ $fail -eq 0 ]] || exit 1
