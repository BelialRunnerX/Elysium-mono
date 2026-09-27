#!/usr/bin/env bash
# Something that makes a sound when the wind moves it, broken one at a time.
#
# The claim is that a room of them is no louder than one of them. Every
# ambient block that has ever had to be muted failed it the same way.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-comforts
C=$M/src/main/java/com/elysium/comforts/rules/ChimeRules.java
CB=$M/src/main/java/com/elysium/comforts/block/ChimeBlock.java
RB=$M/src/main/java/com/elysium/comforts/registry/ComfortBlocks.java
G=$M/tools/gen_comforts.py
V=$M/validate.py
SNAP=/tmp/chimes_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit."
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-comforts)
restore() { rm -rf $ROOT/elysium-comforts && (cd $ROOT && tar xf $SNAP); }
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
regen() { (cd $M && python3 tools/gen_comforts.py >/dev/null 2>&1); }
expect_regen() { regen; expect "$1" "$2"; }

echo "a room of them no louder than one of them"
edit $C '        return A_SECOND * Math.max(1, nearby) / (blowing * MOST_A_SECOND);' \
        '        return A_SECOND / (blowing * MOST_A_SECOND);'
expect "a wait that ignores the company it keeps, which is the wall of noise" "sounds a second and the ceiling is"

edit $C '        return Math.min(1.0D, waitOf(nearby, wind) / ideal);' '        return 1.0D;'
expect "a chime that always rings when it wakes, whatever the rules worked out" "sounds a second and the ceiling is"

edit $C '    public static final int LONGEST_WAIT = 20 * 60;' '    public static final int LONGEST_WAIT = 20 * 60 * 60;'
expect "a chime that goes an hour between looking at the sky" "ticks between looking at the sky"

edit $C '        for (int many : new int[]{1, 2, 8, 64, 200}) {' '        for (int many : new int[]{}) {'
expect "a loudness rule that looks at no room at all" "louder than its own ceiling is not reported"

edit $C '            if (loudest > ceiling + 1.0E-9D) {' '            if (false) {'
expect "a room louder than its own ceiling, unreported" "louder than its own ceiling is not reported"

edit $C '        bad.addAll(faultsInCeiling(MOST_A_SECOND));' '        '
expect "the rule that says a ceiling is a ceiling, never added up" "never added up by the chimes"

edit $C '        if (ceiling <= 0.0D) {' '        if (false) {'
expect "a ceiling of nothing, unreported" "under which no chime ever sounds, is not reported"

echo "six materials, six notes"
edit $C '        if (materials.size() < 2) {' '        if (false) {'
expect "a single note with nothing to be a chord with, unreported" "nothing to be a chord with is not reported"

edit $C '            if (!seen.add(material)) {' '            if (false) {'
expect "a material listed twice, unreported" "material listed twice is not reported"

edit $C '            if (!madeOf.containsKey(material) || madeOf.get(material).isEmpty()) {' \
        '            if (false) {'
expect "a chime made of nothing at all, unreported" "made of nothing at all is not reported"

edit $C '            String already = heard.put(pitchOf(material), material);
            if (already != null) {' '            String already = heard.put(pitchOf(material), material);
            if (false) {'
expect "two materials on one note, unreported" "landing on the same note are not reported"

edit $C '            if (note < lowest || note > highest) {' '            if (false) {'
expect "a note outside the band, unreported" "a note under its own floor is not reported"

edit $C '        return LOWEST + (HIGHEST - LOWEST) * at / (MATERIALS.size() - 1.0F);' \
        '        return LOWEST;'
expect "every chime ringing at the same note" "ring at the same note"

echo "the wind, which is what a player builds around"
edit $C '        if (full <= none) {
            bad.add("the wind is as strong as it gets at "' '        if (false) {
            bad.add("the wind is as strong as it gets at "'
expect "a world where the wind is strongest at the bottom, unreported" "strongest at the bottom is not"

edit $C '            if (windAt(height, false, none, full) != 0.0D) {' '            if (false) {'
edit $C '        if (!underSky) {
            return 0.0D;
        }' '        if (false) {
            return 0.0D;
        }'
expect "a roof that stops nothing, with the rule that should say so switched off" "the rule that should have said so did not"

edit $C '        if (half <= 0.0D || half >= 1.0D) {' '        if (false) {'
expect "a wind that is a switch rather than a slope, unreported" "with no middle is not reported"

edit $C '        if (!underSky) {
            return 0.0D;
        }' '        if (false) {
            return 0.0D;
        }'
expect "a chime that hears the wind through a roof" "under a roof at"

edit $C '        bad.addAll(faultsInSlope(NO_WIND, FULL_WIND));' '        '
expect "a chime rule that answers its controls and is never asked about the roster" "never added up by the chimes"

echo "the block a player hangs up"
edit $CB '        int nearby = nearby(level, pos);' '        int nearby = 1;'
expect "a chime that never counts the company it keeps" "does not count the company it keeps"

edit $CB '        if (wind > 0.0D
                && random.nextDouble() < ChimeRules.chanceOnWaking(nearby, wind)) {' \
         '        if (wind > 0.0D) {'
expect "a chime that rings every time it wakes, whatever the rules say" "decided somewhere other than the rules"

edit $CB '        level.scheduleTick(pos, this, ChimeRules.waitOf(nearby, wind));
    }' '        level.scheduleTick(pos, this, 20);
    }'
expect "a chime that puts itself back to sleep for a number somebody typed" "for as long as the rules say"

edit $CB '        double wind = ChimeRules.windAt(pos.getY(), level.canSeeSky(pos.above()));
        int nearby' '        double wind = 1.0D;
        int nearby'
expect "a chime that feels the same wind wherever it is" "put a roof over it"

edit $CB '                    ChimeRules.pitchOf(material));' '                    1.0F);'
expect "every chime ringing at the same note whatever it is made of" "the most tiring noise a game can make"

edit $CB '                pos.offset(-reach, -layers, -reach),
                pos.offset(reach, layers, reach))) {' '                pos.offset(-reach, -reach, -reach),
                pos.offset(reach, reach, reach))) {'
expect "a chime counting a cube of thirty-five thousand blocks every time it rings" "rather than in the slab they actually hang in"

edit $RB '        int wanted = ChimeRules.MATERIALS.size() * RestRules.COLOURS.size();
        if (made.size() != wanted) {
            throw new IllegalStateException("the rules ask for " + wanted
                    + " chime(s) and " + made.size() + " were registered");
        }' '        '
expect "a registry that never counts the chimes it registered" "counts the chimes that were registered"

echo "the world it is written out into"
edit $C '    made.put("copper", "minecraft:copper_ingot");' '    made.put("copper", "minecraft:copper_ingots");'
expect_regen "a chime made of something the game does not have" "the game does not have one"

rm -f $M/src/main/resources/assets/elysiumcomforts/blockstates/red_iron_chimes.json
expect "a chime with no blockstate, which is a missing-texture cube" "has no blockstate"

edit $V '    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(", CHIME))' \
        '    _rules = set(re.findall(r"public static List<String> (faultsInZ\w+)\(", CHIME))'
expect "a rule sweep that finds no chime rules at all" "chime rule(s) are declared at all"

rm -f $C
expect "nothing saying how often a chime sounds" "a row of them is a wall of noise"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all comforts checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "chimes: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
