#!/usr/bin/env bash
# A hundred and forty biomes in somebody else's overworld, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-wilds
OR=$M/src/main/java/com/elysium/wilds/rules/OverworldRules.java
H=$M/tools/OverworldHarness.java
R=$M/tools/OverworldRoster.java
G=$M/tools/gen_wilds.py
V=$M/validate.py
D=$M/tools/data
SNAP=/tmp/over_snap
OUT=/tmp/over_neg
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
  java -cp $OUT com.elysium.wilds.rules.OverworldHarness 2>&1 | grep -v JAVA_TOOL
}
worldout() { python3 "$V" 2>&1 | grep -v JAVA_TOOL; }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
gexpect() { regen; wexpect "$@"; }
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

echo "the one thing this file exists for: a biome that generates somewhere"
edit $OR '            new WildRules.Span(-0.9333D, -0.7666D),' '            new WildRules.Span(-0.4500D, -0.1500D),'
expect "two bands of ours laid over each other on the same axis" "belongs to two biomes of ours and one of them is the one that never wins"

edit $OR '            new WildRules.Span(-0.2225D, 0.0500D),' '            new WildRules.Span(-0.9000D, 0.0500D),'
expect "an erosion band that swallows the two above it" "claim climate in common"

edit $OR '            if (low == high && !bothPoints) {' '            if (low == high) {'
expect "an overlap check that cannot see past a point axis, so nothing ever overlaps" "two claims that share half an axis are not reported"

edit $OR '                if (one.inside(earlier)) {' '                if (false) {'
expect "a containment check that cannot report anything" "is not reported, and it wins nothing anywhere, ever"

echo "the bands themselves"
edit $OR '            new WildRules.Span(-0.45D, -0.15D),
            new WildRules.Span(-0.15D, 0.20D),' '            new WildRules.Span(-0.45D, -0.20D),
            new WildRules.Span(-0.15D, 0.20D),'
expect "a gap between two bands of warmth, so climate in it goes to whatever is nearest" "so climate in the gap goes to whatever box happens"

edit $OR '            new WildRules.Span(0.0500D, 1.0000D));' '            new WildRules.Span(0.0500D, 0.9000D));'
expect "an axis that stops short of its own end" "and the axis ends at 1"

edit $OR '        for (int at = 1; at < bands.size(); at++) {' '        for (int at = 1; at < 1; at++) {'
expect "a band check that never looks at a second band" "a gap between two bands is not reported"

echo "what being declared first buys"
edit $OR '    public static final WildRules.Span SURFACE = WildRules.Span.at(0.0D);' '    public static final WildRules.Span SURFACE = new WildRules.Span(0.0D, 0.9D);'
expect "a surface claim that is a slab rather than the point vanilla uses" "the surface is a point at 0 and the distance to it"

echo "no two the same biome under two names"
edit $OR '                    mix(0xF4, 0x60, relief, RELIEFS.size()));' '                    mix(0xF4, 0xF4, relief, RELIEFS.size()));'
edit $OR '                    mix(0xD0, 0xB4, relief, RELIEFS.size()),' '                    mix(0xD0, 0xD0, relief, RELIEFS.size()),'
edit $OR '                    mix(0x6C, 0x38, relief, RELIEFS.size()));' '                    mix(0x6C, 0x6C, relief, RELIEFS.size()));'
expect "colours that stop varying with the shape of the ground, so five biomes are painted the same" "is painted exactly like"

edit $OR '            "cinder", "ember",  "sun",     "palm",   "steam");' '            "cinder", "ember",  "sun",     "palm",   "rime");'
expect "two climates with one name, so one of the biomes never generates" "names two biomes"

edit $OR '            "frostvault",   "rimegrotto",   "icewell",' '            "frostvault",   "rimegrotto",'
expect "fewer cave names than crossings, so a cave is unnamed" "cave names for"

echo "what we took"
edit $OR '    public static final double MOST_OF_THE_LAND = 0.55D;' '    public static final double MOST_OF_THE_LAND = 0.40D;'
expect "a ceiling on our share that the design already goes past" "of the inland surface and the ceiling is"

edit $OR '    public static final double LEAST_OF_THE_LAND = 0.25D;' '    public static final double LEAST_OF_THE_LAND = 0.60D;'
expect "a floor on our share that the design cannot reach" "which is 140 biomes nobody will ever stand in"

echo "the decoration, and the order the game refuses to sort twice"
edit $OR '        made.sort((left, right) -> OURS.indexOf(left) - OURS.indexOf(right));
        return List.copyOf(made);
    }

    /** What one of our caves adds of its own. */' '        java.util.Collections.reverse(made);
        return List.copyOf(made);
    }

    /** What one of our caves adds of its own. */'
expect "our own features listed in a different order by different biomes" "out of the one order every biome lists them in"

edit $OR '        made.add(one.wet() <= 1 ? "patch_scrub"
                : one.wet() == 2 ? "patch_bloom" : "patch_thicket");' '        if (one.wet() > 5) { made.add("patch_scrub"); }'
expect "a biome that adds nothing of ours at all" "adds nothing of ours at all"

edit $OR '        return feature.startsWith("boulder_") ? LOCAL_MODIFICATIONS
                : VEGETAL_DECORATION;' '        return feature.startsWith("boulder_") ? 40 : VEGETAL_DECORATION;'
expect "a feature in a generation step a biome file does not have" "and a biome file has"

edit $OR '            "jagged_peaks", "frozen_peaks", "stony_peaks", "windswept_hills",' '            "swamp", "frozen_peaks", "stony_peaks", "windswept_hills",'
expect "a crag that decorates like a meadow" "which is not one of the game's mountains"

echo "the world the biomes are actually in"
edit $G '    return low + (carried if step >= 0 else -carried)' '    return low + (high - low) * at // (bands - 1)'
gexpect "the generator rounding a colour the other way from the rules" "in the Vale's files are not what ValeRules works out"

edit $G '    entries = [{"biome": name, "parameters": parameters}
               for name, parameters in overworld_claims(shape)]' '    entries = []'
gexpect "a preset with none of our biomes in its list" "lists 7593 entries and the rules plus the dump"

# Since 88af2f0e the generator drops the vanilla claims our boxes shadow
# (unshadowed), and the count message names them before it says what the
# list comes to.
edit $G '    entries += unshadowed(vanilla_entries(), entries)' '    entries = entries + unshadowed(vanilla_entries(), entries)[:2000]'
gexpect "a preset that drops most of vanilla's own list" "entries and the rules plus the dump less the"

edit $G '        overworld["generator"]["biome_source"] = {
            "type": "minecraft:multi_noise",
            "biomes": entries,
        }' '        overworld["generator"]["biome_source"] = {
            "type": "minecraft:multi_noise",
            "preset": "minecraft:overworld",
        }'
gexpect "a preset that names vanilla's list instead of writing one" "still names a preset, which takes the name of a list"

edit $G '    entries = [{"biome": name, "parameters": parameters}
               for name, parameters in overworld_claims(shape)]
    entries += unshadowed(vanilla_entries(), entries)' '    ours = [{"biome": name, "parameters": parameters}
            for name, parameters in overworld_claims(shape)]
    entries = unshadowed(vanilla_entries(), ours)
    entries += ours'
gexpect "our biomes declared after vanilla's, which wins them nothing at all" "and a box inside a box declared first wins nothing"

edit $G '        preset = json.loads(read(os.path.join(VANILLA_PRESETS, name)))' '        preset = json.loads(read(os.path.join(VANILLA_PRESETS, name)))
        preset["dimensions"]["minecraft:the_nether"]["generator"]["settings"] = "minecraft:end"'
gexpect "a preset that rewrites the nether on the way past" "on the way past, and nobody asked us to touch it"

edit $G '    for name in sorted(os.listdir(VANILLA_PRESETS)):
        if not name.endswith(".json"):
            continue' '    for name in sorted(os.listdir(VANILLA_PRESETS)):
        if name != "normal.json":
            continue'
gexpect "only one of the three presets written, so Large Biomes has none of ours" "world preset(s) were written and vanilla has three"

edit $G '                   blend(0xA8, 0x9E, wet, wets),
                     blend(0x6C, 0x38, relief, reliefs)),' '                   blend(0xA8, 0x9E, wet, wets),
                     blend(0x6C, 0x38, wet, wets)),'
gexpect "a colour mixed on the wrong axis from the one the rules mix it on" "in the file and"

edit $G '             "weight": weight} for kind, most, fewest, weight in rows]' '             "weight": weight} for kind, fewest, most, weight in rows]'
gexpect "a spawner wanting more than it will take, which the game drops in silence" "the game drops the spawner, logs a line, and the biome is empty"

edit $G '        write(os.path.join(DATA, "worldgen/placed_feature", name + ".json"),
              {"feature": "%s:%s" % (NS, name), "placement": placement})' '        write(os.path.join(DATA, "worldgen/placed_feature", name + ".json"),
              {"feature": "%s:%s" % (NS, name), "placement": []})'
gexpect "a feature of ours placed with no placement at all" "which is once at the corner of every chunk"

# The tag files (8d7ef5e3) walk the same two lists first, so the anchor
# carries the docstring of the function that names them.
edit $G '    """A name a player reads, for every one of them."""
    for one in shape["lands"] + shape["caves"]:' '    """A name a player reads, for every one of them."""
    for one in shape["caves"]:'
gexpect "biomes with no name a player reads" "have no name a player reads"

echo "the dumps everything rests on"
edit $D/overworld-vanilla.tsv 'minecraft:mushroom_fields' '#minecraft:mushroom_fields'
gexpect "a row of vanilla's own list quietly commented out" "is not vanilla's overworld any more"

echo
echo "restoring and re-checking"
restore
(cd $M && python3 tools/gen_wilds.py >/dev/null 2>&1)
if python3 "$V" >/dev/null 2>&1; then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "overworld: $pass caught, $fail missed"
[[ $fail -eq 0 ]] || exit 1
