#!/usr/bin/env bash
# The courses, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-atelier
C=$M/src/main/java/com/elysium/atelier/rules/CourseRules.java
H=$M/tools/CourseHarness.java
BL=$M/src/main/java/com/elysium/atelier/block/CourseBlock.java
RB=$M/src/main/java/com/elysium/atelier/registry/AtelierBlocks.java
RI=$M/src/main/java/com/elysium/atelier/registry/AtelierItems.java
G=$M/tools/gen_atelier.py
V=$M/validate.py
SNAP=/tmp/course_snap
OUT=/tmp/course_neg
pass=0; fail=0
# A run killed part-way leaves the tree broken, and the next run then snapshots
# the breakage and restores it after every case. Refuse to start from a tree
# that is not already healthy.
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
  java -cp $OUT com.elysium.atelier.rules.CourseHarness 2>&1 | grep -v JAVA_TOOL | grep -v "^COURSE " | grep -v "^CTAG "
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

echo "the one thing this file exists for: a stair you can walk up"
edit $C '            case COMPACT -> {
                made.add(new ArchRules.Box(0, 0, 0, 8, 8, 16));
                made.add(new ArchRules.Box(0, 8, 0, 8, 16, 8));
            }' '            case COMPACT -> {
                made.add(new ArchRules.Box(0, 0, 0, 8, 8, 16));
            }'
expect "a way up that stands at one height" "so it is a slab with a staircase drawn on it"

edit $C '            case BULK -> {
                made.add(new ArchRules.Box(0, 0, 0, 16, 8, 16));
                made.add(new ArchRules.Box(0, 8, 0, 16, 16, 12));
            }' '            case BULK -> {
                made.add(new ArchRules.Box(0, 0, 0, 16, 2, 16));
                made.add(new ArchRules.Box(0, 8, 0, 16, 16, 12));
            }'
expect "a stair with a riser taller than a player walks up" "which is more than a player walks up"

edit $C '            case LOFT -> {
                made.add(new ArchRules.Box(0, 6, 0, 16, 8, 16));
                made.add(new ArchRules.Box(0, 8, 0, 16, 16, 8));
            }' '            case LOFT -> {
                made.add(new ArchRules.Box(0, 6, 0, 16, 8, 16));
                made.add(new ArchRules.Box(0, 8, 0, 16, 12, 8));
            }'
expect "a stair that stops short of the block above it" "so it does not reach the block above it"

edit $C '    public static final int LONGEST_STRIDE = 8;' '    public static final int LONGEST_STRIDE = 16;'
expect "a stride ceiling high enough to allow anything" "would allow any staircase at all"

edit $C '            if (tread - below > LONGEST_STRIDE) {' '            if (tread - below > LONGEST_STRIDE && false) {'
expect "a stride check that cannot report anything" "is not reported, so the stride check cannot say yes"

echo "the decks"
edit $C '            case PLATFORM -> made.add(new ArchRules.Box(0, 4, 0, 16, 8, 16));' '            case PLATFORM -> made.add(new ArchRules.Box(0, 4, 0, 4, 8, 4));'
expect "a floor a player cannot stand on" "which is not a floor"

edit $C '            case RAILING -> {
                made.add(new ArchRules.Box(6, 0, 6, 10, 16, 10));' '            case RAILING -> {
                made.add(new ArchRules.Box(0, 0, 0, 16, 16, 10));'
expect "a rail filling most of its block, which is a wall" "which is a wall"

edit $C '            new Kind(RAILING, DECK, RAIL, 12, 2, true),' '            new Kind(RAILING, DECK, RAIL, 12, 2, false),'
expect "a rail that does not reach the rail beside it" "so a run of them has gaps"

echo "the climb"
edit $C '                made.add(new ArchRules.Box(2, 7, 14, 14, 9, 16));' '                made.add(new ArchRules.Box(2, 7, 0, 14, 9, 16));'
expect "a ladder drawn through the middle of its block" "so it is not on one"

echo "the prices, which are the shapes"
edit $C '    public static int units(String name) {
        int made = (volume(name) + EIGHTH - 1) / EIGHTH;' '    public static int units(String name) {
        int made = volume(name) / EIGHTH;'
expect "a price rounded down, so a piece sells for less than the material in it" "which is more than it costs"

edit $C '            new Kind(TERRACE, STEP, "", 6, 4, false),' '            new Kind(TERRACE, STEP, "", 12, 4, false),'
expect "a recipe that makes more than it takes" "so a loop of crafting it pays"

edit $C '            new Kind(SKYLINE, STEP, "", 4, 4, false),' '            new Kind(SKYLINE, STEP, "", 1, 4, false),'
expect "a recipe that wastes more than a block" "nobody will ever make one"

edit $C '    public static final int MOST_WASTED = WHOLE;' '    public static final int MOST_WASTED = WHOLE * 16;'
expect "a waste ceiling high enough to allow anything" "the ceiling would allow anything"

echo "the stonecutter"
edit $C '            new Kind(SLAB, CUT, "", 2, 1, false),' '            new Kind(SLAB, CUT, "", 2, 2, false),'
expect "a stonecutter recipe taking two blocks" "and a stonecutter takes one"

edit $C '            List.of(STAIR, SLAB, VERTICAL_SLAB, WALL, LADDER);' '            List.of(STAIR, SLAB, VERTICAL_SLAB, WALL, LADDER, BALCONY);'
expect "something crafted on a grid claimed to be cut on a stonecutter" "and a stonecutter makes cuts of a block"

echo "one block with two names"
edit $C '            case VERTICAL_SLAB -> made.add(new ArchRules.Box(0, 0, 0, 8, 16, 16));' '            case VERTICAL_SLAB -> made.add(new ArchRules.Box(0, 0, 0, 16, 8, 16));'
expect "two courses filling exactly the same sixteenths" "so they are one block with two names"

edit $C '            new Kind(PLATFORM, DECK, FLOOR, 8, 2, false),' '            new Kind(PLATFORM, DECK, FLOOR, 8, 2, false),
            new Kind(PLATFORM, DECK, FLOOR, 8, 2, false),'
expect "a course declared twice" "is declared twice"

echo "what the game already has"
edit $C '            WALL, Set.of("tuff", "blackstone", "andesite", "diorite", "granite",' '            WALL, Set.of("tuff", "blackstone", "andesite", "diorite", "granite", "oak",'
wexpect "a cut claimed for a material the game has no such block in" "so that cut is simply missing"

edit $C '            SLAB, Set.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                    "mangrove", "cherry", "crimson", "warped", "bamboo", "stone",' '            SLAB, Set.of("spruce", "birch", "jungle", "acacia", "dark_oak",
                    "mangrove", "cherry", "crimson", "warped", "bamboo", "stone",'
wexpect "a cut the game already has that this module would add a second of" "adds a second one"

echo "the world the courses are actually in"
edit $RB '        for (CourseRules.Course course : CourseRules.catalogue()) {' '        for (CourseRules.Course course : CourseRules.catalogue().subList(0, 8)) {'
wexpect "courses listed by hand rather than walked from the catalogue" "not registered by walking the catalogue"

edit $RB '        if (made.size() != CourseRules.howMany()) {' '        if (false) {'
wexpect "a registry that does not count what it registered" "counts the courses registered"

edit $RB '                case CourseRules.LADDER -> BLOCKS.register(id,
                        () -> new LadderBlock(settled));' '                case CourseRules.LADDER -> BLOCKS.register(id,
                        () -> new CourseBlock(settled, kind));'
wexpect "a ladder that is not the game's own ladder" "a player slides down"

edit $RI '                        AtelierBlocks.PIECES, AtelierBlocks.COURSES)) {' '                        AtelierBlocks.PIECES)) {'
wexpect "blocks a player can only place with a command" "only place with a command"

edit $RI '        int blocks = AtelierBlocks.ALL.size() + AtelierBlocks.OPENINGS.size()
                + AtelierBlocks.PIECES.size() + AtelierBlocks.COURSES.size();' '        int blocks = AtelierBlocks.ALL.size() + AtelierBlocks.OPENINGS.size()
                + AtelierBlocks.PIECES.size();'
wexpect "a count that does not add up every roster it checks" "would not be noticed"

echo "the two files that describe one geometry"
edit $G '    "platform":       [(0, 4, 0, 16, 8, 16)],' '    "platform":       [(0, 2, 0, 16, 8, 16)],'
regen
wexpect "a generator drawing a shape the block does not collide as" "cannot step onto"

edit $G '    "balcony":        [(0, 0, 0, 16, 3, 16), (0, 3, 13, 16, 14, 16)],' '    "not_a_course":   [(0, 0, 0, 16, 3, 16), (0, 3, 13, 16, 14, 16)],'
wexpect "a course with no shape in the generator at all" "will not run"

echo "the recipes"
edit $G '                "ingredient": {"item": material},' '                "ingredient2": {"item": material},'
regen
wexpect "a stonecutter recipe with no ingredient in it" "with the wrong ingredient, or none"

edit $G '        return {"type": "minecraft:stonecutting",' '        return {"type": "minecraft:crafting_shaped", "pattern": ["M"], "key": {"M": {"item": material}},'
regen
wexpect "a cut put on a crafting grid after all" "forty-five other recipes competing for"

echo "the game's own blockstates"
edit $G '        made[id] = {"parent": "minecraft:block/slab", "textures": cut}' '        made[id] = {"parent": "minecraft:block/slab_top", "textures": cut}'
regen
wexpect "a slab built on the wrong template" "the piece is the wrong shape"

edit $G '    text = text.replace("{FULL}", "minecraft:block/" + block.split(":", 1)[1])' '    text = text.replace("{FULL}", "minecraft:block/stone")'
regen
wexpect "a double slab drawn as somebody else's block" "turns into somebody else's block"

echo "the tags"
edit $C '            case SLAB -> tags.add("minecraft:slabs");' '            case SLAB -> tags.add("minecraft:elysium_slabs");'
wexpect "a slab put in a tag nothing writes a file for" "has no file, so every course that needs it"

edit $C '            case SLAB -> tags.add("minecraft:slabs");' '            case SLAB -> tags.add("minecraft:mineable/pickaxe");'
wexpect "a slab the rules stop putting in the game's own slab tag" "the rules do not put it in"

edit $C '        tags.add(course.material().soft() ? "minecraft:mineable/axe"
                : "minecraft:mineable/pickaxe");' '        tags.add("minecraft:mineable/pickaxe");'
wexpect "a wooden course worked with a pickaxe" "worked by the wrong tool"

echo
echo "restoring and re-checking"
restore
if python3 "$V" >/dev/null 2>&1; then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "courses: $pass caught, $fail missed"
[[ $fail -eq 0 ]] || exit 1
