#!/usr/bin/env bash
# Compression, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-logistics
C=$M/src/main/java/com/elysium/logistics/rules/CompressRules.java
L=$M/src/main/java/com/elysium/logistics/rules/LogisticRules.java
H=$M/tools/CompressHarness.java
RB=$M/src/main/java/com/elysium/logistics/registry/LogisticBlocks.java
G=$M/tools/gen_logistics.py
V=$M/validate.py
SNAP=/tmp/compress_snap
OUT=/tmp/compress_neg
KIN=$ROOT/elysium-kinetics/src/main/java/com/elysium/kinetics/rules
IND=$ROOT/elysium-industry/src/main/java/com/elysium/industry/rules
pass=0; fail=0
if ! python3 "$V" >/dev/null 2>&1; then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  python3 "$V" 2>&1 | tail -5
  exit 3
fi
rm -rf $SNAP && mkdir -p $SNAP
cp -a $M/src/main/java/com/elysium/logistics $SNAP/logistics
cp -a $M/tools $SNAP/tools
cp -a $M/src/main/resources $SNAP/resources
cp -a $M/validate.py $SNAP/validate.py
restore() {
  rm -rf $M/src/main/java/com/elysium/logistics $M/tools $M/src/main/resources
  cp -a $SNAP/logistics $M/src/main/java/com/elysium/logistics
  cp -a $SNAP/tools $M/tools
  cp -a $SNAP/resources $M/src/main/resources
  cp -a $SNAP/validate.py $M/validate.py
}
trap 'restore' EXIT INT TERM
out() {
  rm -rf $OUT && mkdir -p $OUT
  if ! javac -nowarn -d $OUT $M/src/main/java/com/elysium/logistics/rules/*.java $KIN/*.java $IND/*.java "$H" 2>&1 | grep -v JAVA_TOOL; then :; fi
  java -cp $OUT com.elysium.logistics.rules.CompressHarness 2>&1 | grep -v JAVA_TOOL
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
regen() { (cd $M && python3 tools/gen_logistics.py >/dev/null 2>&1); }

echo "the one number a save depends on"
edit $L '    public static final int COMPRESSION = 9;' '    public static final int COMPRESSION = 8;'
expect "a tier worth eight of the one below, which destroys matter in a loop" "a crafting grid is 9"

edit $L '    public static final int COMPRESSION = 9;' '    public static final int COMPRESSION = 10;'
expect "a tier worth ten of the one below, which makes matter in a loop" "a crafting grid is 9"

edit $C '        if (here != under * PER_STEP) {' '        if (here != under * PER_STEP && false) {'
expect "a price check that cannot report anything" "the price check cannot say yes"

echo "the round trip"
edit $L '        long divisor = inSmallest(1, steps);
        return new long[] {smallest / divisor, smallest % divisor};' '        long divisor = inSmallest(1, steps);
        return new long[] {smallest / divisor, 0};'
expect "a split that throws away the remainder" "so the split that stops a drawer eating the remainder is not splitting"

edit $L '        long worth = amount;
        for (int step = 0; step < Math.max(0, steps); step++) {
            worth *= COMPRESSION;
        }' '        long worth = amount;
        for (int step = 0; step < Math.max(0, steps) - 1; step++) {
            worth *= COMPRESSION;
        }'
expect "a compression that is one step shallow" "and nine of the tier below is"

edit $C '        if (back != amount) {' '        if (back != amount && false) {'
expect "a round-trip check that cannot report a pile coming back smaller" "the round-trip check cannot say yes"

echo "the ceiling every count here is a long for"
edit $C '            if (worth > Long.MAX_VALUE / PER_STEP / STACK) {' '            if (worth > Long.MAX_VALUE / PER_STEP / STACK && false) {'
expect "a ceiling that cannot say a ladder is too deep" "is not reported as going round the end of a long"

echo "two tiers a player cannot tell apart"
edit $C '            "minecraft:copper_block", "minecraft:iron_block",' '            "minecraft:iron_block", "minecraft:iron_block",'
expect "two tiers framed in the same metal" "frames two tiers"

edit $C '            "compressed", "double_compressed", "triple_compressed",' '            "compressed", "compressed", "triple_compressed",'
expect "two tiers with one name" "names two tiers"

edit $C '            "cobblestone", "dirt", "stone", "deepslate", "cobbled_deepslate",' '            "cobblestone", "cobblestone", "stone", "deepslate", "cobbled_deepslate",'
expect "a material compressed twice" "is compressed twice"

edit $C '    public static final List<String> DEPTHS = List.of(
            "compressed", "double_compressed", "triple_compressed",' '    public static final List<String> DEPTHS = List.of(
            "double_compressed", "triple_compressed",'
expect "fewer names than tiers, so a tier is unnamed" "the roster disagrees with itself"

echo "the strength ladder"
edit $C '        return Math.min(HARDEST, 2.0F + tier * 1.5F);' '        return Math.min(HARDEST, 2.0F - tier * 1.5F);'
expect "a compression that gets softer the deeper it goes" "is softer than the tier below it"

edit $C '    public static final float TOUGHEST = 1200.0F;' '    public static final float TOUGHEST = 1200000.0F;'
expect "a blast ceiling nothing ever reaches" "the ceiling is not a ceiling, it is a comment"

echo "the world the compressions are actually in"
edit $RB '        for (String material : CompressRules.MATERIALS) {' '        for (String material : CompressRules.MATERIALS.subList(0, 3)) {'
wexpect "compressions listed by hand rather than walked from the two lists" "not registered from the two lists"

edit $RB '        if (made.size() != CompressRules.howMany()) {' '        if (false) {'
wexpect "a registry that does not count what it registered" "counts the compressions registered"

edit $RB '                                .strength(hardness, resistance)' '                                .strength(3.0F, 6.0F)'
wexpect "a hardness that is not read off the tier" "not the ladder a player swings a pickaxe at"

echo "the two recipes that are the round trip"
edit $G '                "result": {"id": f"{NS}:{id}", "count": 1},' '                "result": {"id": f"{NS}:{id}", "count": 2},'
regen
wexpect "nine of something making two of the thing above" "a machine for making matter"

edit $G '                "result": {"id": under, "count": 9},' '                "result": {"id": under, "count": 10},'
regen
wexpect "one of something coming apart into ten" "makes matter every time it is run"

edit $G '                "result": {"id": under, "count": 9},' '                "result": {"id": under, "count": 8},'
regen
wexpect "one of something coming apart into eight" "destroys matter every time it is run"

edit $G '            under = ("minecraft:" + material if tier == 1
                     else f"{NS}:" + depths[tier - 2] + "_" + material)' '            under = "minecraft:" + material'
regen
wexpect "every tier compressed straight out of the base material" "and not elysiumlogistics"

echo "the frame that says which tier it is"
edit $G '            "textures": {"material": face, "frame": frame, "particle": face},' '            "textures": {"material": face, "frame": face, "particle": face},'
regen
wexpect "a compression framed in the thing it is made of" "it looks exactly like the block it compresses"

edit $G '                  compress_model(faces[material], frames[markers[tier - 1]],' '                  compress_model(faces[material], frames[markers[0]],'
regen
wexpect "every tier framed in the same metal" "cannot tell them apart across a room"

echo
echo "restoring and re-checking"
restore
if python3 "$V" >/dev/null 2>&1; then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "compression: $pass caught, $fail missed"
[[ $fail -eq 0 ]] || exit 1
