#!/usr/bin/env bash
# How a budding block is made, broken one way at a time.
#
# The property every case below circles: a seeding is a multiplier on something
# the player already has, never a source of something they have not. A budding
# block grows for ever and drops itself, so seeding one is a single price for a
# permanent source -- which is what the player is buying and is meant to pay
# off. What decides whether the whole feature is a shortcut round four other
# modules is what that price is paid *in*. Its own shards and it is a
# multiplier: you need thirty-two platinum shards to farm platinum, and where
# the first thirty-two came from is still the foundry's problem. Anything else
# and every ore in the pack is obtainable from whichever one is cheapest.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-arcana
R=$M/src/main/java/com/elysium/arcana/rules/BuddingRules.java
G=$M/tools/gen_geodes.py
V=$M/validate.py
SNAP=/tmp/seeding_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit."
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-arcana)
restore() { rm -rf $ROOT/elysium-arcana && (cd $ROOT && tar xf $SNAP); }
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
regen() { (cd $M && python3 tools/gen_geodes.py >/dev/null 2>&1); }
expect_regen() { regen; expect "$1" "$2"; }
remove() { rm -f "$1"; }

echo "a source rather than a multiplier"
edit $R '            new Budding("platinum", "platinum", 32, DEARER_CATALYST),' \
        '            new Budding("platinum", "coal", 32, DEARER_CATALYST),'
expect "a geode seeded out of another material's shards" "it is a source of something they have not"

edit $R '            if (!one.from().equals(one.material())) {' '            if (false) {'
expect "the rule that keeps a seeding a multiplier" "out of another material's shards is not reported"

edit $R '            new Budding("platinum", "platinum", 32, DEARER_CATALYST),' \
        '            new Budding("platinum", "platinum", 32, "elysiumarcana:coal_shard"),'
expect "a catalyst that is another material's shard" "by the back door"

edit $R '                if (plain.equals(material + "_shard")) {' '                if (false) {'
expect "the rule that shuts the back door" "another material's shard is not reported"

echo "the ladder four modules are priced against"
edit $R '            new Budding("iron", "iron", 20, DEARER_CATALYST),' \
        '            new Budding("iron", "iron", 8, DEARER_CATALYST),'
expect "something mined seeded cheaper than something given" "a way round the ladder"

edit $R '        if (dearestGiven > 0 && cheapestMined > 0 && cheapestMined <= dearestGiven) {' \
        '        if (false) {'
expect "the rule that keeps the ladder" "seeded cheaper than a given one is not reported"

echo "the wait"
edit $R '            new Budding("coal", "coal", 8, COMMON_CATALYST),' \
        '            new Budding("coal", "coal", 4, COMMON_CATALYST),'
expect "a seeding that pays for itself almost at once" "finding a geode in the world stops being worth anything"

edit $R '            if (one.cycles() < leastCycles) {' '            if (false) {'
expect "the rule that keeps seeding a decision" "pays for itself in one cluster is not reported"

edit $R '            if (one.cycles() > mostCycles) {' '            if (false) {'
expect "the rule that keeps seeding worth doing" "nobody would ever do is not reported"

echo "what a crafting grid can hold"
edit $R '            new Budding("bort", "bort", 32, DEARER_CATALYST));' \
        '            new Budding("bort", "bort", 72, DEARER_CATALYST));'
expect "a recipe too big for the grid" "things in a grid of"

edit $R '            if (blocks + 1 > grid) {' '            if (false) {'
expect "the rule that keeps a recipe writable" "too big for the grid is not reported"

edit $R '            new Budding("coal", "coal", 8, COMMON_CATALYST),' \
        '            new Budding("coal", "coal", 10, COMMON_CATALYST),'
expect "a price that is not a whole number of blocks" "not a whole number of the thing the recipe is made of"

edit $R '            if (toABlock > 0 && one.shards() % toABlock != 0) {' '            if (false) {'
expect "the rule that keeps a price expressible" "not a whole number of blocks is not reported"

echo "every geode, and nothing else"
edit $R '            new Budding("bort", "bort", 32, DEARER_CATALYST));' \
        '            new Budding("unobtainium", "unobtainium", 32, DEARER_CATALYST));'
expect "a geode with no way to seed it" "which is a rule nothing states and nobody could guess"

edit $R '            if (!priced) {' '            if (false) {'
expect "the rule that finds an unseedable geode" "no way to seed it is not reported"

edit $R '            if (!materials.contains(one.material())) {' '            if (false) {'
expect "the rule that finds a price for nothing" "this module does not have is not reported"

edit $R '            if (!seen.add(one.material())) {' '            if (false) {'
expect "the rule that finds a material priced twice" "priced twice is not reported"

echo "the number the arithmetic is about"
edit $R '    public static final int CLUSTER_GIVES = 4;' \
        '    public static final int CLUSTER_GIVES = 5;'
expect "a payback worked out against a number the game does not use" "every payback this module proves is about a number the game does not use"

edit $R '            if (!one.catalyst().contains(":")) {' '            if (false) {'
expect "the rule that a catalyst is a name" "not the name of anything is not reported"

echo "what the recipe on disk says"
edit $G '            [{"item": "%s:%s_block" % (NS, one["material"])}] * blocks' \
        '            [{"item": "%s:coal_block" % NS}] * blocks'
expect_regen "a recipe paid in somebody else's blocks" "which is neither its own block nor the catalyst its row names"

edit $G '    blocks = one["shards"] // SHARDS_TO_A_BLOCK' '    blocks = 1'
expect_regen "a recipe costing fewer blocks than its row prices" "what the player pays and what this module proves about the price are different numbers"

edit $G '            + [{"item": one["catalyst"]}]),' '            + [{"item": "minecraft:stone"}]),'
expect_regen "a recipe with a catalyst the rules never named" "neither its own block nor the catalyst its row names"

remove $M/src/main/resources/data/elysiumarcana/recipe/budding_bort.json
expect "a material with no recipe to seed it" "that material is the one a player has to go and find"

echo "the rules are all asked"
edit $R '        bad.addAll(faultsInBootstrap(BUDDINGS));' '        '
expect "a rule written, compiled and never added up" "faultsInBootstrap is written, compiled and never added up"

edit $R '        bad.addAll(faultsInLadder(BUDDINGS, GeodeRules.MINED,' '        if (false) bad.addAll(faultsInLadder(BUDDINGS, GeodeRules.MINED,'
expect "a rule added up only sometimes" "faults() has a condition in it"

echo
echo "seeding: $pass caught, $fail missed"
[ "$fail" -eq 0 ] || exit 1
