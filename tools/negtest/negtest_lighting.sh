#!/usr/bin/env bash
# What lighting a room costs, broken one way at a time.
#
# The property every case below circles: a decoration roster is where a
# bestiary quietly dies. Thirty pretty light sources, each fine on its own,
# each brighter and cheaper than a torch -- and nine monsters somewhere else in
# the pack that only appear in the dark. Nobody decides to remove them. They
# are removed by the sum of thirty decisions that were each about how a room
# looks, and nothing anywhere reports it: the blocks work, the rooms look good,
# and the pack simply stops having a night in it.
#
# So the number that matters most here is not in this module. STILL_DARK is
# the brightest light at which a majority of elysium-bestiary's own monsters
# still spawn, and the two cases that move it -- one from this side, one from
# the bestiary's -- are the point of the whole file.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-comforts
B=$ROOT/elysium-bestiary
R=$M/src/main/java/com/elysium/comforts/rules/LightRules.java
BLK=$M/src/main/java/com/elysium/comforts/block/FittingBlock.java
REG=$M/src/main/java/com/elysium/comforts/registry/ComfortBlocks.java
H=$B/src/main/java/com/elysium/bestiary/rules/HauntRules.java
G=$M/tools/gen_comforts.py
SNAP=/tmp/lighting_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit."
  exit 3
fi
rm -f $SNAP
# The bestiary goes in the snapshot too, because one case edits it: a number
# this module copies is only proved to be derived if moving the original is
# shown to break the copy.
(cd $ROOT && tar cf $SNAP elysium-comforts elysium-bestiary)
restore() { rm -rf $ROOT/elysium-comforts $ROOT/elysium-bestiary
            (cd $ROOT && tar xf $SNAP); }
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
remove() { rm -f "$1"; }

echo "the light the game keeps for itself"
edit $R '        made.add(new Fitting("gold_" + HUNG, HUNG, 12, 6, ""));' \
        '        made.add(new Fitting("gold_" + HUNG, HUNG, 15, 6, ""));'
expect "a decoration block as bright as glowstone" "is proof against everything in the dark"

edit $R '            if (one.light() > brightest) {' '            if (false) {'
expect "the rule that keeps a ceiling on it" "at the game's own full light is not reported"

echo "brighter costs more"
edit $R '        made.add(new Fitting("lead_" + HUNG, HUNG, 9, 4, ""));' \
        '        made.add(new Fitting("lead_" + HUNG, HUNG, 9, 6, ""));'
expect "a fitting nobody would ever place" "so nobody ever places the second one"

edit $R '                    if (one.light() > other.light() && one.cost() <= other.cost()) {' \
        '                    if (false) {'
expect "the rule that keeps a kind a decision" "brighter and no dearer than another of its kind is not reported"

echo "colour is not a stat"
edit $R '        made.add(new Fitting("cupric_" + HELD, HELD, 7, 4, ""));' \
        '        made.add(new Fitting("cupric_" + HELD, HELD, 7, 4, "red"));'
expect "one colour of a thing worth more than the others" "stopped being a decision about how the room looks"

edit $R '                if (one.light() != first.light() || one.cost() != first.cost()) {' \
        '                if (false) {'
expect "the rule that holds a dyed set to one price" "two colours of one fitting at two light levels are not reported"

edit $R '        for (String colour : COLOURS) {' \
        '        for (String colour : COLOURS.subList(0, 15)) {'
expect "fifteen colours of a sixteen-colour thing" "finds a gap that reads as a bug"

edit $R '                if (!found.contains(colour)) {' '                if (false) {'
expect "the rule that finds the missing colour" "a dyed set missing a colour is not reported"

echo "something in the room that is not a lamp"
edit $R '        made.add(new Fitting(UNDERFOOT, UNDERFOOT, 0, 2, ""));
        made.add(new Fitting(RestRules.ROPE, STRUNG, 0, 1, ""));' \
        '        made.add(new Fitting(UNDERFOOT, UNDERFOOT, 1, 2, ""));
        made.add(new Fitting(RestRules.ROPE, STRUNG, 1, 1, ""));'
expect "a roster in which everything glows" "no way to furnish a room out of this module"

edit $R '            any |= one.light() == 0;' '            any = true;'
expect "the rule that keeps something dark in it" "roster in which everything gives light is not reported"

echo "the set a player places by the dozen"
edit $R '        made.add(new Fitting("ender_" + HELD, HELD, 9, 5, ""));' \
        '        made.add(new Fitting("ender_" + HELD, HELD, 9, 5, "red"));'
expect "the matching set lighting every room it is in" "is one of a dyed set, which is the set that matches"

edit $R '            if (one.dyed() && one.light() > stillDark) {' '            if (false) {'
expect "the rule that keeps the matching set dim" "a dyed fitting over the band is not reported"

echo "a rope, and not two ropes"
edit $R '        made.add(new Fitting(RestRules.ROPE, STRUNG, 0, 1, ""));' \
        '        made.add(new Fitting("rope", STRUNG, 0, 1, ""));'
expect "a second rope under a shorter name" "are the same thing under two names"

edit $R '                boolean same = have.startsWith(one.id() + "_")' \
        '                boolean same = false && have.startsWith(one.id() + "_")'
expect "the rule that finds the second one" "is not reported, so the pack ships two ropes"

edit $R '    public static final List<String> ALREADY = List.of(RestRules.ROPE);' \
        '    public static final List<String> ALREADY = List.of("nothing_here");'
expect "a list of what the module has that names nothing it has" "and no fitting is it, so the roster and the registry disagree"

edit $R '                any |= one.id().equals(have);' '                any = true;'
expect "the rule that keeps that list an overlap" "no fitting is, is not reported"

echo "what a crafting grid can hold"
edit $R '        made.add(new Fitting("blackstone_" + FLOOR, FLOOR, BRIGHTEST, 9, ""));' \
        '        made.add(new Fitting("blackstone_" + FLOOR, FLOOR, BRIGHTEST, 12, ""));'
expect "a fitting too dear to have a recipe at all" "its recipe is dropped during data load"

edit $R '            if (one.cost() > grid) {' '            if (false) {'
expect "the rule that keeps a fitting makeable" "costing more than a crafting grid holds is not reported"

echo "a third of it dark enough to decorate with"
edit $R '    public static final double DIM_SHARE = 1.0D / 3.0D;' \
        '    public static final double DIM_SHARE = 1.0D / 2.0D;'
expect "a roster that cannot furnish a dark room" "under the share this module keeps dim"

edit $R '        if (dim < these.size() * share) {' '        if (false) {'
expect "the rule that keeps the roster usable in the dark" "nine bright shapes and nothing dim is not reported"

edit $R '        List<Fitting> these = shapes(roster);' '        List<Fitting> these = roster;'
expect "a dyed set answering the question sixteen times" "eight bright ones is reported as dark enough"

echo "the number this module does not own"
edit $R '    public static final int STILL_DARK = 7;' \
        '    public static final int STILL_DARK = 9;'
expect "a band this module widened for itself" "monsters say that number is 7"

edit $H '            new Haunt("shardling", "#minecraft:is_overworld", 0, 7, 0, SKY, 2, 4),' \
        '            new Haunt("shardling", "#minecraft:is_overworld", 0, 4, 0, SKY, 2, 4),'
expect "a band moved in the bestiary and not here" "monsters say that number is 4"

echo "one fitting, one block, and no fitting twice"
edit $REG '            if (LightRules.ALREADY.contains(one.id())) {' \
          '            if (false) {'
expect "nothing skipping what the module already has" "hammock hangs from is registered a second time"

edit $REG '                            .lightLevel(state -> one.light())' \
          '                            .lightLevel(state -> 7)'
expect "a light level typed into the registration" "the number a player measures in a room"

edit $REG '        for (LightRules.Fitting one : LightRules.FITTINGS) {' \
          '        for (LightRules.Fitting one : java.util.List.of(LightRules.FITTINGS.get(0))) {'
expect "blocks registered from a list somebody wrote out" "the blocks it ships are two lists that can differ"

echo "what you bump into is what you see"
edit $BLK '    private static final VoxelShape HELD_SHAPE = box(5.0D, 0.0D, 5.0D, 11.0D, 7.0D, 11.0D);' \
          '    private static final VoxelShape HELD_SHAPE = box(3.0D, 0.0D, 3.0D, 13.0D, 9.0D, 13.0D);'
expect "a model and a collision box that disagree" "either walks through it or stops short of it"

edit $BLK '            case LightRules.HELD -> HELD_SHAPE;' \
          '            case LightRules.HELD -> Shapes.block();'
expect "a kind of fitting that has lost its shape" "cube a player cannot see the edges of"

edit $BLK '    private final String name;' \
          '    private final String name;
    private static final int light = 7;'
expect "a light level written into the block" "can say different things about the same fitting"

echo "what the generator writes"
edit $G '            "ingredients": ([{"item": body}] * (one[3] - 1)) + [{"item": heart}],' \
        '            "ingredients": ([{"item": body}] * (one[3] + 1)) + [{"item": heart}],'
expect_regen "a recipe costing more than the row prices" "about a price the game does not charge"

edit $G '    "fire_pit": ("minecraft:stick", "minecraft:coal"),' \
        '    "fire_pit": ("minecraft:kindling", "minecraft:coal"),'
expect_regen "a fitting made out of something the game has never heard of" "which the game does not make"

edit $G '            "type": "minecraft:crafting_shapeless",' \
        '            "type": "minecraft:crafting_shaped",'
expect_regen "a recipe whose shape hides how much it costs" "which only a shapeless recipe counts"

edit $G 'for _one in MADE_HERE:
    NAMES[_one[0]] = titled(_one[0])' \
        'for _one in MADE_HERE:
    if _one[0] != "doormat":
        NAMES[_one[0]] = titled(_one[0])'
expect_regen "a fitting with no name but its id" "doormat has no name in the language file"

remove $M/src/main/resources/data/elysiumcomforts/recipe/fire_pit.json
expect "a fitting nothing says how to make" "because nothing says how to make one"

remove $M/src/main/resources/assets/elysiumcomforts/textures/block/doormat.png
expect "a fitting with no texture" "doormat has no texture"

remove $M/src/main/resources/assets/elysiumcomforts/blockstates/stone_lamp.json
expect "a fitting with no blockstate" "stone_lamp has no blockstate"

remove $M/src/main/resources/data/elysiumcomforts/loot_table/blocks/gold_lantern.json
expect "a fitting that drops nothing when broken" "gold_lantern has no loot table"

echo "one each and none spare"
edit $R '        made.add(new Fitting("candelabra", HELD, 12, 7, ""));' \
        '        made.add(new Fitting("doormat", HELD, 12, 7, ""));'
expect "two fittings written under one name" "the game registers second is a block nobody can place"

edit $R '            if (!seen.add(one.id())) {' '            if (false) {'
expect "the rule that finds two under one name" "two fittings under one name are not reported"

edit $R '            if (!kinds.contains(one.kind())) {' '            if (false) {'
expect "the rule that finds a fitting of no kind" "of a kind the module does not have is not reported"

edit $R '        made.add(new Fitting(HEARTH, HEARTH, 11, 5, ""));' '        '
expect "a way of fitting out a room that nothing is" "this module names and never makes"

edit $R '            if (ofKind(kind, roster).isEmpty()) {' '            if (false) {'
expect "the rule that finds the unbuilt kind" "kind of fitting nothing in the roster is is not reported"

echo "the rules are all asked"
edit $R '        bad.addAll(faultsInUnlit(FITTINGS));' '        '
expect "a rule written, compiled and never added up" "faultsInUnlit is written, compiled and never added up"

edit $R '        bad.addAll(faultsInGrid(FITTINGS, ALREADY, GRID));' \
        '        if (false) bad.addAll(faultsInGrid(FITTINGS, ALREADY, GRID));'
expect "a rule added up only sometimes" "faults() has a branch in it"

echo
echo "lighting: $pass caught, $fail missed"
[ "$fail" -eq 0 ] || exit 1
