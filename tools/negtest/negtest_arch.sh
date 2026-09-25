#!/usr/bin/env bash
# The architectural pieces, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-atelier
A=$M/src/main/java/com/elysium/atelier/rules/ArchRules.java
R=$M/src/main/java/com/elysium/atelier/rules/AtelierRules.java
H=$M/tools/ArchHarness.java
BL=$M/src/main/java/com/elysium/atelier/block/ArchBlock.java
RB=$M/src/main/java/com/elysium/atelier/registry/AtelierBlocks.java
RI=$M/src/main/java/com/elysium/atelier/registry/AtelierItems.java
G=$M/tools/gen_atelier.py
V=$M/validate.py
SNAP=/tmp/arch_snap
OUT=/tmp/arch_out
pass=0; fail=0
# A run that is killed part-way leaves the tree broken, and the next run then
# snapshots the breakage and restores it after every case. Refuse to start
# from a tree that is not already healthy.
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
  java -cp $OUT com.elysium.atelier.rules.ArchHarness 2>&1 | grep -v JAVA_TOOL | grep -v "^GEOM"
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

echo "the one thing this file exists for: a roof you can close"
edit $A '            found.add(roof(pitch, INNER, steep ? 6 : 5, 5, 4));' '            if (!steep) { found.add(roof(pitch, INNER, 5, 4, 4)); }'
expect "a pitch with no valley, so an L-shaped house cannot be roofed" "cannot be closed"

edit $A '            found.add(roof(pitch, RIDGE, steep ? 5 : 4, steep ? 5 : 4, steep ? 4 : 3));' '            if (steep) { found.add(roof(pitch, RIDGE, 5, 4, 4)); }'
expect "a pitch open along the top" "cannot be closed"

edit $A '            found.add(roof(pitch, OUTER, steep ? 4 : 3, 4, 2));' '            if (!steep) { found.add(roof(pitch, OUTER, 3, 4, 2)); }'
expect "a pitch with no outer corner, caught by the count as well as by name" "needs"

edit $A '        found.add(new Piece("path", PATH, "flat", "", 1, 8, 1, 1, 0, false));' '        found.add(new Piece("gambrel_roof_slope", ROOF, SLOPE, "gambrel", 4, 4, 3,
                WHOLE, 0, false));
        found.add(new Piece("path", PATH, "flat", "", 1, 8, 1, 1, 0, false));'
expect "a pitch that exists in the roster and in no list that counts it" "nothing lists"

edit $A '            found.add(roof(pitch, SLOPE, steep ? 5 : 4, 4, 3));' '            found.add(roof(pitch, SLOPE, 4, 4, 3));'
expect "two pitches that are one pitch with two names" "one pitch with two names"

echo "the light ladder"
edit $A '        found.add(new Piece("lamp", LIGHT, "standing", "", 6, 2, 2, WHOLE, 13,
                false));' '        found.add(new Piece("lamp", LIGHT, "standing", "", 1, 2, 2, WHOLE, 13,
                false));'
expect "a brighter light that costs less than a dimmer one" "costs no more"

edit $A '        found.add(new Piece("sconce", LIGHT, "wall", "", 2, 4, 1, 4, 7, false));' '        found.add(new Piece("sconce", LIGHT, "wall", "", 2, 4, 1, 4, 10, false));'
expect "two lights at one brightness" "one light with two names"

edit $A '        found.add(new Piece("chandelier", LIGHT, "hanging", "", 8, 1, 1, WHOLE,
                BRIGHTEST, false));' '        found.add(new Piece("chandelier", LIGHT, "hanging", "", 8, 1, 1, WHOLE,
                14, false));'
expect "a ladder whose top rung nobody put anything on" "rung nobody put anything on"

echo "paths, bridges and the economy"
edit $A '        found.add(new Piece("path_step", PATH, "step", "", 3, 2, 1, 3, 0, false));' '        found.add(new Piece("path_step", PATH, "step", "", 3, 2, 1, 6, 0, false));'
expect "a path a slab would do instead" "is a slab"

edit $A '        found.add(new Piece("bridge_rail", BRIDGE, "rail", "", 2, 6, 2, WHOLE, 0,
                true));' '        found.add(new Piece("bridge_rail", BRIDGE, "rail", "", 2, 6, 2, WHOLE, 0,
                false));'
expect "a railing with gaps in it" "does not reach the rail beside it"

edit $A '        found.add(new Piece("bridge_support", BRIDGE, "support", "", 6, 3, 3, WHOLE,
                0, false));' '        found.add(new Piece("bridge_deck", BRIDGE, "deck", "", 6, 2, 3, WHOLE,
                0, false));'
expect "a bridge with nothing holding it up" "something holding it up"

edit $A '        found.add(new Piece("awning", TRIM, "awning", "", 3, 8, 4, 3, 0, false));' '        found.add(new Piece("awning", TRIM, "awning", "", 9, 4, 4, 3, 0, false));'
expect "a recipe that makes more than it takes" "a loop of crafting it pays"

edit $A '        public boolean profitable() {
            return yields() > spends();' '        public boolean profitable() {
            return false;'
expect "a profitability check that cannot report anything" "cannot report anything"

edit $A '        found.add(new Piece("awning", TRIM, "awning", "", 3, 8, 4, 3, 0, false));' '        found.add(new Piece("awning", TRIM, "awning", "", 3, 2, 4, 3, 0, false));'
expect "a recipe that wastes more than a block, so nobody would use it" "nobody will ever make one"

edit $A '    public static final int MOST_WASTED = WHOLE;' '    public static final int MOST_WASTED = WHOLE * 16;'
expect "a waste ceiling high enough to allow anything" "the ceiling would allow anything"

echo "the geometry, which two files describe"
edit $A '            return List.of(new Box(0, 0, 0, 16, tall, 16));' '            return List.of(new Box(0, 0, 0, 16, 16, 16));'
expect "a path whose boxes are not the height the rules price it at" "about a different block"

edit $A '                case OUTER -> {
                    made.add(new Box(0, 0, 0, 16, 8, 16));
                    made.add(new Box(0, 8, 0, rise, 16, rise));
                }' '                case OUTER -> {
                    made.add(new Box(0, 0, 0, 16, 8, 16));
                    made.add(new Box(0, 8, 0, 16, 16, rise));
                }'
wexpect "a model and a collision box that disagree" "cannot step onto"

edit $G '            return [(0, 0, 0, 16, 8, 16), (0, 8, 0, rise, 16, rise)]' '            return [(0, 0, 0, 16, 8, 16), (0, 8, 0, 8, 16, 8)]'
regen
wexpect "a generator drawing a shape the block does not collide as" "cannot step onto"

echo "the world the pieces are actually in"
edit $RB '                    properties = properties.lightLevel(state -> piece.light());' '                    properties = properties.lightLevel(state -> 15);'
wexpect "a light ladder nobody can see in a dark room" "not the ladder a player sees"

edit $RB '        if (made.size() != ArchRules.howMany()) {' '        if (false) {'
wexpect "a registry that does not count what it registered" "counts the architectural blocks registered"

edit $RB '        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (ArchRules.Piece piece : ArchRules.PIECES) {' '        for (AtelierRules.Material material : AtelierRules.MATERIALS.subList(0, 1)) {
            for (ArchRules.Piece piece : ArchRules.PIECES) {'
wexpect "pieces listed by hand rather than walked from the rosters" "not registered from the two rosters"

edit $RI '                : java.util.List.of(AtelierBlocks.ALL, AtelierBlocks.OPENINGS,
                        AtelierBlocks.PIECES)) {' '                : java.util.List.of(AtelierBlocks.ALL, AtelierBlocks.OPENINGS)) {'
wexpect "blocks a player can only place with a command" "only place with a command"

edit $G '            entries["block." + NS + "." + name + "_" + piece[0]] = \
                MATERIAL_NAMES[name] + " " + PIECE_NAMES[piece[0]]' '            if piece[0] != "cornice":
                entries["block." + NS + "." + name + "_" + piece[0]] = \
                    MATERIAL_NAMES[name] + " " + PIECE_NAMES[piece[0]]'
regen
wexpect "a block called by its id" "called by its id"

# The three recipe-grid cases live in negtest_grids.sh: they break the
# generator, so they have to be proved against the generator, which
# validate.py only sees the output of.

echo "the checkers themselves"
edit $A '    public static boolean reaches(Piece one, Piece other) {
        return one.joins() && other.joins() && one.family().equals(other.family());' '    public static boolean reaches(Piece one, Piece other) {
        return one.joins();'
expect "a joining relation that is not symmetric" "disagree about whether they meet"

edit $A '        return one.joins() && other.joins() && one.family().equals(other.family());' '        return one.joins() && other.joins();'
expect "a rail joining across families" "reaches across families"

echo
echo "restoring and re-checking"
restore
if python3 "$V" >/dev/null 2>&1; then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "arch: $pass caught, $fail missed"
[[ $fail -eq 0 ]] || exit 1
