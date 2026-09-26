#!/usr/bin/env bash
# Where nine things are, and whether they can be there at all.
#
# A mob with a spawn that cannot happen is the quietest bug a pack has: it is
# registered, it renders, its loot table is right, every other check passes,
# and the only symptom is that nobody has ever seen one. So every guard that
# says where a beast is gets broken here, one way at a time, and has to be
# reported by something.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-bestiary
R=$M/src/main/java/com/elysium/bestiary/rules/HauntRules.java
EN=$M/src/main/java/com/elysium/bestiary/entity/BeastEntity.java
G=$M/tools/gen_bestiary.py
V=$M/validate.py
W=$M/src/main/resources/data/elysiumbestiary/neoforge/biome_modifier
SNAP=/tmp/haunts_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit."
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-bestiary)
restore() { rm -rf $ROOT/elysium-bestiary && (cd $ROOT && tar xf $SNAP); }
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
# Several of these are only visible once the world has been written out from
# the broken table, so the generated tree is rebuilt after the edit.
regen() { (cd $M && python3 tools/gen_bestiary.py >/dev/null 2>&1); }
expect_regen() { regen; expect "$1" "$2"; }

echo "every beast somewhere, and everything somewhere a beast"
edit $R '            if (!any) {' '            if (false) {'
expect "a beast with nowhere to be, unreported" "could be one nobody ever meets"

edit $R '            if (!seen.add(one.beast())) {' '            if (false) {'
expect "one beast in two places, unreported" "one beast in two places is not reported"

edit $R '            if (!named.contains(one.beast())) {' '            if (false) {'
expect "a haunt belonging to nothing, unreported" "somewhere that is not a beast's is not reported"

edit $R '            new Haunt("keener", "#minecraft:is_taiga", 0, 4, 0, SKY, 1, 2),
            new Haunt("hollow_lord", "#minecraft:is_overworld", 0, 0, BEDROCK, 0, 1, 1));' \
        '            new Haunt("hollow_lord", "#minecraft:is_overworld", 0, 0, BEDROCK, 0, 1, 1));'
expect "a beast dropped out of the table entirely" "somewhere twice or nowhere at all"

echo "a band a world really has"
edit $R '            if (one.lightBand() <= 0) {' '            if (false) {'
expect "a light band with nothing in it, unreported" "a light band with nothing in it is not reported"

edit $R '            if (one.darkest() < PITCH_DARK || one.brightest() > FULL_LIGHT) {' \
        '            if (false) {'
expect "a light level no block has, unreported" "a light level the game does not have is not reported"

edit $R '            if (one.heightBand() <= 0) {' '            if (false) {'
expect "a floor above its own ceiling, unreported" "a floor above its own ceiling"

edit $R '            if (one.lowest() < BEDROCK || one.highest() > SKY) {' \
        '            if (false) {'
expect "a height band above the sky, unreported" "a height band above the sky is not reported"

edit $R '            if (!one.where().startsWith("#")) {' '            if (false) {'
expect "a beast tied to one biome rather than a tag, unreported" "tied to one biome rather than a tag is not reported"

echo "a group that fits where it is asked to stand"
edit $R '            if (room > ROOM_FOR_A_PACK) {' '            if (false) {'
expect "a pack that cannot physically fit, unreported" "sixty siege beasts at a time are not reported"

edit $R '    public static final double ROOM_FOR_A_PACK = 144.0D;' \
        '    public static final double ROOM_FOR_A_PACK = 100000.0D;'
expect "the floor loosened until nothing is ever too big" "sixty siege beasts at a time are not reported"

edit $R '    public static final double ROOM_FOR_A_PACK = 144.0D;' \
        '    public static final double ROOM_FOR_A_PACK = 1.0D;'
expect "the floor tightened until this pack's own groups do not fit" "square blocks for a full group and there are 1.0"

edit $R '            double room = one.most() * beast.width() * beast.width();' \
        '            double room = beast.width() * beast.width();'
expect "room worked out for one of them rather than a group" "sixty siege beasts at a time are not reported"

echo "nothing a weaker copy of something already fought"
edit $R '                if (everywhere && somewhere && better.tier() == worse.tier()) {' \
        '                if (false) {'
expect "a strictly worse beast, unreported" "beaten on every axis by another of its own family"

edit $R '                if (everywhere && somewhere && better.tier() == worse.tier()) {' \
        '                if (everywhere && somewhere) {'
expect "a rule that compares across tiers, so it reports nine times" "one beast is a strictly worse version of another"

edit $R '                if (everywhere && somewhere && better.tier() == worse.tier()) {' \
        '                if (somewhere && better.tier() == worse.tier()) {'
expect "worse on one axis counted as worse everywhere" "two beasts that each beat the other somewhere are reported as"

edit $R '                if (worse == better || !worse.family().equals(better.family())) {' \
        '                if (worse == better) {'
expect "a prowler and a brute read as two strengths of one idea" "a prowler and a brute are reported as two strengths of one"

echo "a haunt that is not a haunt, refused rather than listed"
edit $R '            Objects.requireNonNull(beast, "a haunt of nothing");' \
        '            String unusedBeast = beast;'
expect "a haunt of no beast at all, built without complaint" "a haunt of nothing is accepted"

edit $R '            if (where.isEmpty()) {' '            if (false) {'
expect "a beast living in the empty string" "a haunt nowhere is accepted"

edit $R '            if (least <= 0 || most < least) {' '            if (false) {'
expect "a group of none accepted as a group" "a group of none is accepted as a group"

echo "the rules added up rather than merely written"
edit $R '        bad.addAll(faultsInRoom(BeastRules.BEASTS, HAUNTS));' '        '
expect "a rule that answers its controls and is never asked about the roster" "never added up by"

edit $R '    public static List<String> faultsInBands(List<Haunt> haunts) {' \
        '    public static java.util.List<String> faultsInBands(List<Haunt> haunts) {'
expect "the band rule taken out from under the validator" "bands are bands a world really has"

edit $R '    public static List<String> faultsInRoom(List<BeastRules.Beast> beasts,' \
        '    public static java.util.List<String> faultsInRoom(List<BeastRules.Beast> beasts,'
expect "the room rule taken out from under the validator" "fits on the floor it is asked to stand on"

edit $R '    public static List<String> faultsInDominance(List<BeastRules.Beast> beasts) {' \
        '    public static java.util.List<String> faultsInDominance(List<BeastRules.Beast> beasts) {'
expect "the dominance rule taken out from under the validator" "a strictly worse version of"

echo "the bands reaching the game"
edit $EN '                com.elysium.bestiary.rules.HauntRules.haunt(id);' \
         '                com.elysium.bestiary.rules.HauntRules.haunt("zombie");'
expect "a beast that looks up somebody else's haunt" "a table nothing reads"

edit $EN '        if (pos.getY() < haunt.lowest() || pos.getY() > haunt.highest()) {' \
         '        if (false) {'
expect "the height band never reaching the game" "living deep is a beast that spawns on the surface"

edit $EN '        return light >= haunt.darkest() && light <= haunt.brightest();' \
         '        return true;'
expect "the light band never reaching the game" "spawns in daylight"

echo "the world written out from the table"
edit $G '                  "biomes": haunt["where"],' '                  "biomes": "#minecraft:is_overworld",'
expect_regen "a generator that puts every beast wherever it likes" "is not where a player meets it"

edit $G '           "highest": _number(one[5]), "least": int(one[6]),
           "most": int(one[7])}' '           "highest": _number(one[5]), "least": int(one[7]),
           "most": int(one[6])}'
expect_regen "a group size written out the wrong way round" "and its spawn rule says 4 to 2"

rm -f $W/add_stalker_spawns.json
expect "a beast with a haunt and no spawn rule in the world" "has a haunt and no spawn rule in the world"

edit $R '            new Haunt("keener", "#minecraft:is_taiga", 0, 4, 0, SKY, 1, 2),' \
        '            new Haunt("keener", "#c:is_cold", 0, 4, 0, SKY, 1, 2),'
expect_regen "a biome tag that reads like a tag and is not one" "neither the game nor this pack has such a tag"

edit $R '            new Haunt("shardling", "#minecraft:is_overworld", 0, 7, 0, SKY, 2, 4),' \
        '            new Haunt("shardling", "#minecraft:is_forest", 0, 7, 0, SKY, 2, 4),'
expect_regen "a whole family nobody who stays in the plains ever meets" "nothing in the prowler family is in #minecraft:is_overworld"

echo "the checks themselves"
edit $V '                            if "/tags/worldgen/biome/" in one' \
        '                            if "/tags/worldgen/biomes/" in one'
expect "a game-jar sweep that reads no tags, so the check below checks nothing" "could be read out of the game's jar, so the check below is checking nothing"

edit $V '    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(",' \
        '    _rules = set(re.findall(r"public static List<String> (faultsInZ\w+)\(",'
expect "a rule sweep that finds no rules at all" "haunt rule(s) are declared at all"

edit $G '    MOD, "src/main/java/com/elysium/bestiary/rules/HauntRules.java"),' \
        '    MOD, "src/main/java/com/elysium/bestiary/rules/NoSuchRules.java"),'
expect "a generator that dies reading the rules, taking the validator with it" "the generator stopped before it could be compared against the rules"

rm -f $R
expect "nothing saying where any beast is" "nothing says where any beast is"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all bestiary checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "haunts: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
