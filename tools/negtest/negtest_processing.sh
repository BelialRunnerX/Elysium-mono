#!/usr/bin/env bash
# What the machines do, and the loop none of it may add up to. One break at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-kinetics
P=$M/src/main/java/com/elysium/kinetics/rules/ProcessRules.java
G=$M/tools/gen_kinetics.py
V=$M/validate.py
SNAP=/tmp/processing_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  (cd $M && timeout 400 python3 validate.py 2>&1 | tail -5)
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-kinetics)
restore() { rm -rf $ROOT/elysium-kinetics && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
gexpect() { (cd $M && python3 tools/gen_kinetics.py >/dev/null 2>&1); expect "$@"; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the one thing a roster of recipes may not add up to"
edit $P '            saw("bamboo_from_block", "minecraft:bamboo_block",
                    "minecraft:bamboo", 9, 100, STEADY),' '            saw("bamboo_from_block", "minecraft:bamboo_block",
                    "minecraft:bamboo", 10, 100, STEADY),'
gexpect "a loop that comes back with ten bamboo for nine" "comes back with more than it started with"

edit $P '                mill("sandstone", "minecraft:sandstone", "minecraft:sand", 2, 140, SLOW),' '                mill("sandstone", "minecraft:sandstone", "minecraft:sand", 5, 140, SLOW),'
gexpect "a mill that gets five sand out of a sandstone four sand made" "comes back with more than it started with"

edit $P '        for (int round = 0; round < nodes.size(); round++) {' '        for (int round = 0; round < 0; round++) {'
expect "a loop check that never relaxes an edge" "cannot say no"

edit $P '        for (Membership one : inTag) {
            edges.add(new String[] {one.item(), one.tag()});
            weights.add(0.0D);
        }' '        for (Membership one : inTag) {
            weights.add(0.0D);
            weights.remove(weights.size() - 1);
        }'
expect "a loop check blind to the tags a recipe reads from" "the tag half of the graph could be dropped"

edit $P '        for (Route one : routes) {
            edges.add(new String[] {one.input(), one.output()});
            weights.add(-Math.log(one.rate()));
        }' '        for (Route one : routes) {
            weights.add(-Math.log(one.rate()));
            weights.remove(weights.size() - 1);
        }'
expect "a loop check blind to the game's own recipes" "the game's half of the graph could be dropped"

echo "the game's half of the graph, swept rather than thought of"
edit $P '            new Route("minecraft:sandstone", "minecraft:sand", 4,
                    "minecraft:sandstone", 1),' '            new Route("minecraft:sandstone", "minecraft:sand", 2,
                    "minecraft:sandstone", 1),'
expect "a route declared taking half what the game's recipe takes" "and the game's own recipe takes"

edit $P '            new Route("minecraft:bone_meal", "minecraft:bone", 1,
                    "minecraft:bone_meal", 3),' '            new Route("minecraft:bone_meal", "minecraft:bone", 1,
                    "minecraft:bone_meal", 9),'
expect "a route declared making three times what the game makes" "and the game's own recipe takes"

edit $P '            new Route("minecraft:blaze_powder", "minecraft:blaze_rod", 1,
                    "minecraft:blaze_powder", 2),
' ''
expect "one of the game's loop-closing recipes left undeclared" "and are not declared"

edit $P '            new Route("minecraft:wheat", "minecraft:hay_block", 1,
                    "minecraft:wheat", 9));' '            new Route("minecraft:wheat", "minecraft:hay_block", 1,
                    "minecraft:wheat", 9),
            new Route("minecraft:diamond", "minecraft:sand", 1,
                    "minecraft:diamond", 1));'
expect "a route declared that the game does not have" "and the game does not have them"

edit $P '            new Membership("minecraft:oak_planks", "#minecraft:planks"));' '            new Membership("minecraft:oak_planks", "#minecraft:stone_bricks"));'
expect "a membership the game's own tag does not list" "tag does not list it"

edit $V '        inside = [one for one in jar.namelist()
                  if one.startswith("data/minecraft/recipe/") and one.endswith(".json")]' '        inside = [one for one in jar.namelist()
                  if one.startswith("data/nothing/recipe/") and one.endswith(".json")]'
expect "a sweep that reads none of the game's recipes" "sweeping nothing"

echo "the roster itself"
edit $P '            if (!seen.add(one.machine() + "/" + one.name())) {' '            if (false) {'
expect "a roster check that cannot report two recipes under one name" "under one name are not reported"

edit $P '            if (!MACHINES.contains(one.machine())) {' '            if (false) {'
expect "a roster check that cannot report a machine that does not exist" "machine that does not exist is not reported"

edit $P '            if (one.input().equals(one.output())) {' '            if (false) {'
expect "a roster check that cannot report a thing turned into itself" "turns a thing into itself is not reported"

edit $P '            if (one.pace() == KineticRules.Pace.STOPPED) {' '            if (false) {'
expect "a roster check that cannot report a recipe needing no machine" "runs on a stopped line is not reported"

echo "the join onto the farm"
edit $P '            saw("cabbage_leaf", FARM + ":cabbage", FARM + ":cabbage_leaf", 3, 120, STEADY),' '            saw("cabbage_leaf", FARM + ":cabbage", FARM + ":cabbage_leaves", 3, 120, STEADY),'
gexpect "a join naming an item the farm does not have" "name something the farm does not have"

edit $P '            saw("cabbage_leaf", FARM + ":cabbage", FARM + ":cabbage_leaf", 3, 120, STEADY),' '            saw("cabbage_leaf", FARM + ":cabbage", FARM + ":cabbage_leaf", 4, 120, STEADY),'
gexpect "a saw that beats the knife it automates" "also the better way of doing it"

edit $G '        if joins:' '        if False:'
gexpect "a joining recipe with no condition naming what it joins" "is not conditional on it"

edit $M/src/main/resources/data/elysiumkinetics/recipe/milling/bone.json '  "ticks": 100,
  "pace": "slow"
}' '  "ticks": 100,
  "pace": "slow",
  "neoforge:conditions": [
    {
      "type": "neoforge:mod_loaded",
      "modid": "elysiumagronomy"
    }
  ]
}'
expect "a recipe switched off by a condition naming nothing it uses" "switches off a recipe for no reason"

echo "the roster written against the roster declared"
rm -f $M/src/main/resources/data/elysiumkinetics/recipe/milling/rice_seeds.json
expect "a recipe the rules declare and nothing wrote" "the rules declare are not written"

edit $M/src/main/resources/data/elysiumkinetics/recipe/milling/bone.json '"count": 4' '"count": 6'
expect "a recipe written making more than the rules say" "and the rules say"

edit $M/src/main/resources/data/elysiumkinetics/recipe/sawing/dough.json '"ticks": 120' '"ticks": 40'
expect "a recipe written quicker than the rules say" "and the rules say"

edit $P '    public static final List<String> SEEDED = List.of(
            "onion", "barley", "garlic", "cabbage",' '    public static final List<String> SEEDED = List.of(
            "onion", "barley", "garlic",'
expect "a crop dropped out of the seed supply, leaving a recipe nothing declares" "the rules do not declare"

echo "no game jar to sweep the game's routes in (15 Oct 2026)"
# Until 15 October no vanilla.jar printed "the game's jar is not here" and the
# loop rule passed looking at half its graph. It must now FAIL and name
# ELYSIUM_MC_LIBS, which is pointed at an empty folder for this one run; the
# real jar is not touched.
env_expect() { local what="$1" w="$2" g; shift 2
  g="$(cd $M && env "$@" timeout 400 python3 validate.py 2>&1)"
  if [[ "$g" == *"PROBLEM(S)"* && "$g" == *"$w"* ]]; then echo "  caught : $what"; pass=$((pass+1));
  else echo "  MISSED : $what"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
NOWHERE=$(mktemp -d)
env_expect "no vanilla.jar where ELYSIUM_MC_LIBS points, which used to print not swept for and pass" \
  "the game's routes cannot be swept for loops: the game's jar is not at" ELYSIUM_MC_LIBS="$NOWHERE"
rmdir "$NOWHERE"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all kinetics checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | tail -3 | head -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "processing: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
