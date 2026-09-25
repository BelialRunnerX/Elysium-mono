#!/usr/bin/env bash
# The colour axis, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-atelier
C=$M/src/main/java/com/elysium/atelier/rules/ClothRules.java
H=$M/tools/ClothHarness.java
RB=$M/src/main/java/com/elysium/atelier/registry/AtelierBlocks.java
RI=$M/src/main/java/com/elysium/atelier/registry/AtelierItems.java
G=$M/tools/gen_atelier.py
V=$M/validate.py
SNAP=/tmp/cloth_snap
OUT=/tmp/cloth_neg
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
  java -cp $OUT com.elysium.atelier.rules.ClothHarness 2>&1 | grep -v JAVA_TOOL | grep -v "^PAD " | grep -v "^CLOTH "
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

echo "the sixteen colours"
edit $C '            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green",' '            "gray", "light_gray", "cyan", "purple", "blue", "brown",'
expect "fifteen colours where the game has sixteen" "so a colour of everything here is missing or made up"

edit $C '            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",' '            "white", "white", "magenta", "light_blue", "yellow", "lime", "pink",'
expect "a colour named twice" "so one colour of everything here simply does not exist"

edit $C '            "red", "black");' '            "red", "quartz");'
expect "a colour that is also a material, so one of the two does not exist" "and two blocks with one id is one of them not existing"

echo "the padding"
edit $C '            new Seat("chair", 6, List.of(0, 5)),' '            new Seat("chair", 7, List.of(0, 5)),'
wexpect "a seat upholstered as a different number of boxes than it is drawn as" "the cushion would be on whichever box that number lands on"

edit $C '            new Seat("stool", 5, List.of(0)),' '            new Seat("stool", 5, List.of(1)),'
wexpect "a cushion on one of the legs" "so it is a chair with a cushion on one of its legs"

edit $C '            if (padded.size() >= boxes) {' '            if (padded.size() > boxes) {'
expect "a seat that may be upholstered on every box it has" "the seat record allows what it says it forbids"

echo "the crossing"
edit $C '        for (String colour : COLOURS) {
            for (AtelierRules.Material wood : woods()) {
                for (Seat seat : SEATS) {
                    made.add(new Upholstered(colour, wood, seat.shape()));' '        for (String colour : COLOURS) {
            for (AtelierRules.Material wood : woods().subList(0, 3)) {
                for (Seat seat : SEATS) {
                    made.add(new Upholstered(colour, wood, seat.shape()));'
expect "a hole in the crossing, which is a seat nobody can make" "is a hole in the crossing"

edit $C '        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            if (material.soft()) {
                found.add(material);
            }
        }' '        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            found.add(material);
        }'
expect "a seat framed in deepslate" "the frame of a seat is not a wood at all"

echo "one block with two names"
edit $C '            return colour + "_" + wood.name() + "_" + shape;' '            return wood.name() + "_" + shape;'
expect "a coloured seat with the same id as the bare one" "is already a block this module makes"

echo "the price of a cushion"
edit $C '        return bare.units() + WHOLE;' '        return bare.units();'
expect "upholstery that costs nothing" "so the wool in it is free"

edit $C '            new Cloth(PILLOW, 7, 2),' '            new Cloth(PILLOW, 12, 2),'
expect "a piece of cloth whose recipe pays" "so a loop of crafting it pays"

edit $C '            new Cloth(RUNNER, 14, 2));' '            new Cloth(RUNNER, 1, 2));'
expect "a piece of cloth nobody would ever make" "nobody will ever make one"

echo "names that are already taken"
edit $C '    public static final String PILLOW = "pillow";' '    public static final String PILLOW = "cushion";'
expect "a piece of cloth named after a fitting" "is also a fitting"

edit $C '    public static final String RUNNER = "runner";' '    public static final String RUNNER = "rug";'
expect "a piece of cloth named after a furniture shape" "is also a furniture shape"

echo "the world the colours are actually in"
edit $RB '        for (ClothRules.Upholstered seat : ClothRules.seats()) {' '        for (ClothRules.Upholstered seat : ClothRules.seats().subList(0, 8)) {'
wexpect "coloured blocks listed by hand rather than walked from the crossing" "not registered by walking the crossing"

edit $RB '        if (made.size() != ClothRules.howMany()) {' '        if (false) {'
wexpect "a registry that does not count what it registered" "counts the coloured blocks registered"

edit $RI '                        AtelierBlocks.FITTINGS, AtelierBlocks.CLOTH)) {' '                        AtelierBlocks.FITTINGS)) {'
wexpect "blocks a player can only place with a command" "only place with a command"

echo "the colour a seat actually wears"
edit $G '        wool_face = "minecraft:block/" + colour + "_wool"' '        wool_face = "minecraft:block/white_wool"'
regen
wexpect "every seat upholstered in white whatever its name says" "wear the wrong colour"

edit $G '            "textures": {"wood": wood_face, "cloth": cloth_face,
                         "particle": cloth_face},' '            "textures": {"wood": cloth_face, "cloth": cloth_face,
                         "particle": cloth_face},'
regen
wexpect "a seat whose frame is wool as well as its cushion" "so it is a block of wool shaped like a chair"

edit $G '        which = "#cloth" if index in padded else "#wood"' '        which = "#wood"'
regen
wexpect "a seat upholstered nowhere at all" "and the rules pad"

echo "the recipes"
edit $G '                    "ingredients": [{"item": f"{NS}:{name}_{shape}"},
                                    {"item": wool}],' '                    "ingredients": [{"item": f"{NS}:{name}_{shape}"},
                                    {"item": "minecraft:white_wool"}],'
regen
wexpect "a red chair made out of white wool" "is not made out of"

edit $G '                    "type": "minecraft:crafting_shapeless",' '                    "type": "minecraft:crafting_shaped",'
regen
wexpect "an upholstered seat put on a grid this module has none of" "this module has no grids left"

echo
echo "restoring and re-checking"
restore
if python3 "$V" >/dev/null 2>&1; then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "cloth: $pass caught, $fail missed"
[[ $fail -eq 0 ]] || exit 1
