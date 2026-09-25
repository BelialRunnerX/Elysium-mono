#!/usr/bin/env bash
# The Vale, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-wilds
VR=$M/src/main/java/com/elysium/wilds/rules/ValeRules.java
H=$M/tools/ValeHarness.java
G=$M/tools/gen_wilds.py
V=$M/validate.py
W=$ROOT/check_worldgen.py
SNAP=/tmp/vale_snap
OUT=/tmp/vale_neg
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
  java -cp $OUT com.elysium.wilds.rules.ValeHarness 2>&1 | grep -v JAVA_TOOL
}
worldout() { python3 "$V" 2>&1 | grep -v JAVA_TOOL; }
genout() { (cd $M && python3 tools/gen_wilds.py >/dev/null 2>&1); python3 "$W" 2>&1 | grep -v JAVA_TOOL; }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
wexpect() { local w="$2" g; g="$(worldout)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
gexpect() { local w="$2" g; g="$(genout)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; (cd $M && python3 tools/gen_wilds.py >/dev/null 2>&1); }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the one thing this file exists for: every biome generating somewhere"
edit $VR '        return -1.0D + 2.0D * at / bands;' '        return -1.0D + 1.8D * at / bands;'
expect "bands that stop short of the end of the axis, leaving climate to whatever is nearest" "and the axis ends at 1"

edit $VR '    public static final int WETS = 3;' '    public static final int WETS = 2;'
expect "more names than crossings, so a name is for a biome nobody can reach" "so a biome is unnamed or a name is for nothing"

edit $VR '            "rime_barrens", "glacier_vale", "frostmere",' '            "rime_barrens", "rime_barrens", "frostmere",'
expect "two biomes with one name, so one of them never generates" "so one of them never generates and nothing reports it"

edit $VR '        return mineWarm[0] >= theirsWarm[0] && mineWarm[1] <= theirsWarm[1]
                && mineWet[0] >= theirsWet[0] && mineWet[1] <= theirsWet[1];' '        return true;'
expect "a containment check that says every claim is inside every other" "are reported as one inside the other"

echo "the partition"
edit $VR '            boolean inWarm = temperature >= warm[0]
                    && (temperature < warm[1] || warm[1] >= 1.0D);' '            boolean inWarm = temperature >= warm[0] && temperature < warm[1];'
expect "a top band that stops just short, so the hottest climate belongs to nothing" "belongs to nothing"

echo "no two biomes the same biome under two names"
edit $VR '        private static int mix(int from, int to, int at, int bands) {
            if (bands < 2) {
                return from;
            }
            return from + (to - from) * at / (bands - 1);' '        private static int mix(int from, int to, int at, int bands) {
            if (bands < 2) {
                return from;
            }
            return from;'
expect "every biome painted the same, so fifteen of them are one biome" "are painted exactly the same"

edit $VR '        public float warmthValue() {
            return -0.5F + warmth * (2.5F / (WARMTHS - 1));
        }' '        public float warmthValue() {
            return 0.5F;
        }'
expect "every band the same temperature, so the coldest is no colder than the hottest" "and is not colder"

echo "how much of a world one biome may be"
edit $VR '    public static final double LEAST_OF_THE_WORLD = 0.005D;' '    public static final double LEAST_OF_THE_WORLD = 0.5D;'
expect "a floor half the world, which no biome on a fifteen-way grid can meet" "and the floor is"

edit $VR '    public static final double MOST_OF_THE_WORLD = 0.30D;' '    public static final double MOST_OF_THE_WORLD = 0.01D;'
expect "a ceiling below what an even fifteenth is" "and the ceiling is"

echo "the bands themselves"
edit $VR '            if (warmth < 0 || warmth >= WARMTHS) {' '            if (false) {'
expect "a guard that would let a biome sit in a band that does not exist" "the bands allow what they say they forbid"

echo "the data the game actually reads"
edit $G '                "temperature": [round(band_edge(warmth, warmths), 4),
                                round(band_edge(warmth + 1, warmths), 4)],' '                "temperature": [-1.0, 1.0],'
wexpect "a dimension whose claims have drifted from the bands the rules work out" "are not the bands the rules work out"

edit $G '        write(os.path.join(data, "worldgen/biome", id + ".json"),
              vale_biome(name, warmth, wet, warmths, wets))' '        write(os.path.join(data, "worldgen/biome", id + ".json"),
              vale_biome(name, 2, 1, warmths, wets))'
wexpect "fifteen biome files that are all the same file" "so they are one biome with two names"

edit $G '        claims.append({
            "biome": f"{NS}:{id}",' '        claims.append({
            "biome": f"{NS}:{NS}",'
wexpect "a dimension that names one biome fifteen times" "names one biome twice"

edit $G '    [("minecraft:rabbit", 3, 2, 6), ("minecraft:fox", 2, 2, 4)],' '    [("minecraft:rabbit", 2, 3, 6), ("minecraft:fox", 2, 2, 4)],'
wexpect "a spawner wanting more than it will take, which the game drops in silence" "leaving the biome empty and nothing saying why"

echo "the world the Vale is one of"
edit $G '        "min_y": -64, "monster_spawn_block_light_limit": 0,' '        "min_y": -64, "logical_height": 768, "monster_spawn_block_light_limit": 0,'
wexpect "a world whose logical height is above its real height" "let nothing stand there"

echo
echo "restoring and re-checking"
restore
(cd $M && python3 tools/gen_wilds.py >/dev/null 2>&1)
if python3 "$V" >/dev/null 2>&1 && python3 "$W" >/dev/null 2>&1; then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "vale: $pass caught, $fail missed"
[[ $fail -eq 0 ]] || exit 1
