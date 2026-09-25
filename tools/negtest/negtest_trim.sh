#!/usr/bin/env bash
# The fittings, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-atelier
T=$M/src/main/java/com/elysium/atelier/rules/TrimRules.java
H=$M/tools/TrimHarness.java
RB=$M/src/main/java/com/elysium/atelier/registry/AtelierBlocks.java
RI=$M/src/main/java/com/elysium/atelier/registry/AtelierItems.java
G=$M/tools/gen_atelier.py
V=$M/validate.py
SNAP=/tmp/trim_snap
OUT=/tmp/trim_neg
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
  java -cp $OUT com.elysium.atelier.rules.TrimHarness 2>&1 | grep -v JAVA_TOOL | grep -v "^TRIM " | grep -v "^TTAG "
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

echo "the ladder of facings"
edit $T '            case BLINDS -> made.add(new ArchRules.Box(0, 0, 13, 16, 16, 16));' '            case BLINDS -> made.add(new ArchRules.Box(0, 0, 15, 16, 16, 16));'
expect "a facing no deeper than the one below it" "so it is the same wall twice"

edit $T '            case BOARD -> made.add(new ArchRules.Box(0, 0, 11, 16, 16, 16));' '            case BOARD -> made.add(new ArchRules.Box(0, 0, 12, 16, 16, 16));'
expect "a deeper facing costing no more than the one below it" "so the deeper one is the cheaper one"

edit $T '            case BOARD -> made.add(new ArchRules.Box(0, 0, 11, 16, 16, 16));' '            case BOARD -> made.add(new ArchRules.Box(0, 0, 4, 16, 16, 16));'
expect "a facing as deep as a slab, which is a slab" "which is a slab, and the game has slabs"

edit $T '    public static final int DEEPEST_FACING = SPAN / 2 - 1;' '    public static final int DEEPEST_FACING = SPAN;'
expect "a depth ceiling high enough to allow any wall at all" "so the ceiling would allow any wall at all"

edit $T '    public static final List<String> FACINGS = List.of(PANEL, BLINDS, BOARD);' '    public static final List<String> FACINGS = List.of(PANEL, BLINDS);'
expect "a facing on no ladder, so nothing compares it with its neighbours" "is on no ladder"

echo "the hollow ones"
edit $T '            case FRAME -> {
                made.add(new ArchRules.Box(0, 0, 6, 16, 2, 10));' '            case FRAME -> {
                made.add(new ArchRules.Box(0, 0, 6, 16, 16, 10));'
expect "a frame with no hole in it" "so it is a solid block with a frame drawn on it"

edit $T '        if (clear == 0) {' '        if (clear == 0 && false) {'
expect "a hollowness check that cannot report anything" "is not reported, so the hollow check cannot say yes"

echo "the edges"
edit $T '            new Kind(BANISTER, EDGE, 14, 2, true, false),
            new Kind(CATWALK, EDGE, 7, 2, true, false),
            new Kind(GIRDER, EDGE, 14, 2, true, false),' '            new Kind(BANISTER, EDGE, 14, 2, false, false),
            new Kind(CATWALK, EDGE, 7, 2, false, false),
            new Kind(GIRDER, EDGE, 14, 2, false, false),'
expect "nothing along an edge joining anything" "is about a relation that is never true"

echo "the grain"
edit $T '        return !SOFT.equals(kind(kind).family()) || material.soft();' '        return true;'
expect "a cushion of deepslate" "which is a joke the generator makes on its own"

edit $T '            if (wants && !fitting.material().soft()) {' '            if (wants && !fitting.material().soft() && false) {'
expect "a soft check that cannot report anything" "handed straight to the check is not reported"

echo "the prices, which are the shapes"
edit $T '        return Math.max(1, (volume(name) + EIGHTH - 1) / EIGHTH);' '        return Math.max(1, volume(name) / EIGHTH);'
expect "a price rounded down, so a fitting sells for less than the material in it" "which is more than it costs"

edit $T '            new Kind(PANEL, FACING, 12, 2, false, false),' '            new Kind(PANEL, FACING, 24, 2, false, false),'
expect "a recipe that makes more than it takes" "so a loop of crafting it pays"

edit $T '            new Kind(BOARD, FACING, 5, 2, false, false),' '            new Kind(BOARD, FACING, 1, 2, false, false),'
expect "a recipe that wastes more than a block" "nobody will ever make one"

edit $T '    public static final int MOST_WASTED = WHOLE;' '    public static final int MOST_WASTED = WHOLE * 16;'
expect "a waste ceiling high enough to allow anything" "the ceiling would allow anything"

echo "one block with two names"
edit $T '            case CURTAIN -> made.add(new ArchRules.Box(0, 0, 14, 16, 16, 16));' '            case CURTAIN -> made.add(new ArchRules.Box(0, 0, 15, 16, 16, 16));'
expect "two fittings filling exactly the same sixteenths" "so they are one block with two names"

edit $T '            new Kind(ROPE, SOFT, 14, 2, false, true));' '            new Kind(ROPE, SOFT, 14, 2, false, true),
            new Kind(ROPE, SOFT, 14, 2, false, true));'
expect "a fitting declared twice" "is declared twice"

echo "the world the fittings are actually in"
edit $RB '        for (TrimRules.Fitting fitting : TrimRules.catalogue()) {' '        for (TrimRules.Fitting fitting : TrimRules.catalogue().subList(0, 8)) {'
wexpect "fittings listed by hand rather than walked from the catalogue" "not registered by walking the catalogue"

edit $RB '        if (made.size() != TrimRules.howMany()) {' '        if (false) {'
wexpect "a registry that does not count what it registered" "counts the fittings registered"

edit $RI '                        AtelierBlocks.PIECES, AtelierBlocks.COURSES,
                        AtelierBlocks.FITTINGS)) {' '                        AtelierBlocks.PIECES, AtelierBlocks.COURSES)) {'
wexpect "blocks a player can only place with a command" "only place with a command"

echo "the two files that describe one geometry"
edit $G '    "cushion":  [(1, 0, 1, 15, 5, 15)],' '    "cushion":  [(1, 0, 1, 15, 7, 15)],'
regen
wexpect "a generator drawing a shape the block does not collide as" "cannot step onto"

edit $G '    "rope":     [(7, 0, 7, 9, 16, 9)],' '    "hemp":     [(7, 0, 7, 9, 16, 9)],'
wexpect "a fitting with no shape in the generator at all" "will not run"

echo "the crossing with a condition in it"
edit $G '            if family == soft_family and not soft:
                continue' '            if False:
                continue'
regen
wexpect "a generator making a cushion out of deepslate after all" "the rules refuse to make"

edit $G '        for kind, family, made, of, joins, climbs in kinds:
            if family == soft_family and not soft:
                continue
            id = name + "_" + kind' '        for kind, family, made, of, joins, climbs in kinds:
            if family == soft_family:
                continue
            id = name + "_" + kind'
regen
wexpect "a generator refusing to make the soft fittings at all" "generated file(s) differ"

echo "the tags"
edit $T '        if (kind(fitting.kind()).climbs()) {
            tags.add("minecraft:climbable");
        }' '        if (false) {
            tags.add("minecraft:climbable");
        }'
wexpect "a rope the rules stop putting in the game's climbable tag" "so a player slides down it"

edit $T '        tags.add(fitting.material().soft() ? "minecraft:mineable/axe"
                : "minecraft:mineable/pickaxe");' '        tags.add("minecraft:mineable/pickaxe");'
wexpect "a wooden fitting worked with a pickaxe" "worked by the wrong tool"

echo
echo "restoring and re-checking"
restore
if python3 "$V" >/dev/null 2>&1; then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "trim: $pass caught, $fail missed"
[[ $fail -eq 0 ]] || exit 1
