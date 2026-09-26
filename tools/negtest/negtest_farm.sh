#!/usr/bin/env bash
# Nine machines a mob farm is made of, broken one way at a time.
#
# The property every case below circles: no farm anybody can build changes which
# mob is worth farming. BeastRules spends most of its length proving that reward
# per unit of danger stays inside a band of 1.5 across the whole roster. A farm
# is a multiplier on exactly that ratio -- and a multiplier can break it without
# touching a line of the bestiary, which is why the bestiary's own proof would
# still pass.
#
# The thing that actually breaks it is not looting. It is a killer that takes
# the player's credit away: the game gives no experience for a mob nothing
# player-ish killed, so such a farm pays in drops alone, and the drop share is a
# rounding artefact rather than a constant. The spread goes to 1.618 and the best
# thing in the pack to farm quietly changes. Nothing in the game reports that --
# the farm works and the items arrive.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-bestiary
R=$M/src/main/java/com/elysium/bestiary/rules/FarmRules.java
AC=$M/src/main/java/com/elysium/bestiary/block/act/FarmActs.java
BL=$M/src/main/java/com/elysium/bestiary/block/FarmBlock.java
BE=$M/src/main/java/com/elysium/bestiary/block/FarmBlockEntity.java
RG=$M/src/main/java/com/elysium/bestiary/registry/FarmBlocks.java
MD=$M/src/main/java/com/elysium/bestiary/ElysiumBestiary.java
G=$M/tools/gen_bestiary.py
V=$M/validate.py
SNAP=/tmp/farm_snap.tar
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
regen() { (cd $M && python3 tools/gen_bestiary.py >/dev/null 2>&1); }
expect_regen() { regen; expect "$1" "$2"; }
remove() { rm -f "$1"; }

echo "a killer that takes the player's credit"
edit $R '            new Machine("grinder_spikes", KILLS, 0.5D, 1.0D, "minecraft:iron_ingot", false),' \
        '            new Machine("grinder_spikes", KILLS, 0.0D, 1.0D, "minecraft:iron_ingot", false),'
expect "a killer leaving no experience at all" "kills without the player's credit"

edit $R '            if (one.orbs() <= 0.0D) {' '            if (false) {'
expect "the rule that finds a killer taking the credit" "takes the player's credit away is not reported"

edit $R '            new Machine("grinder_spikes", KILLS, 0.5D, 1.0D, "minecraft:iron_ingot", false),' \
        '            new Machine("grinder_spikes", KILLS, 0.1D, 1.0D, "minecraft:iron_ingot", false),'
expect "a killer inside the margin the module keeps" "against a floor of 0.5"

edit $R '            if (one.orbs() > 0.0D && one.orbs() < floor) {' '            if (false) {'
expect "the rule that keeps the margin" "leaving less experience than the floor is not reported"

edit $R '    public static final double KEEPS_ORBS = 0.5D;' \
        '    public static final double KEEPS_ORBS = 0.0D;'
expect "a floor low enough that nothing is ever too low" "leaving less experience than the floor is not reported"

echo "the band itself"
edit $R '        double[] both = together(machines);' '        double[] both = {1.0D, 1.0D};'
expect "a spread worked out from a farm nobody is holding" "is reported wrongly"

edit $R '        if (through > widest) {' '        if (false) {'
expect "the rule that holds a whole farm to the band" "pushes the spread past the band is not reported"

edit $R '        bad.addAll(faultsInSpread(MACHINES, BeastRules.BEASTS,' '        if (false) bad.addAll(faultsInSpread(MACHINES, BeastRules.BEASTS,'
expect "a rule added up only sometimes" "faults() has a condition in it"

edit $R '        bad.addAll(faultsInSpread(MACHINES, BeastRules.BEASTS,
                BeastRules.derive(BeastRules.BEASTS), BeastRules.WIDEST_SPREAD));' '        '
expect "the band rule never asked about what ships" "faultsInSpread is written, compiled and never added up"

echo "what multiplies what"
edit $R '            new Machine("grinder_saw", KILLS, 1.0D, 2.0D, "minecraft:iron_block", true),' \
        '            new Machine("grinder_saw", KILLS, 1.0D, 40.0D, "minecraft:iron_block", true),'
expect "a machine reaping forty times what falls" "against a ceiling of 4.0"

edit $R '            if (one.drops() > most) {' '            if (false) {'
expect "the rule that caps what a machine reaps" "multiplying drops forty times is not reported"

edit $R '            new Machine("mob_fan", MOVES, 1.0D, 1.0D, "minecraft:iron_ingot", true),' \
        '            new Machine("mob_fan", MOVES, 0.5D, 1.0D, "minecraft:iron_ingot", true),'
expect "something that is not a killer changing the experience" "is not a killer and changes the experience"

edit $R '            new Machine("mob_conveyor", MOVES, 1.0D, 1.0D, "minecraft:copper_ingot", false),' \
        '            new Machine("mob_conveyor", MOVES, 1.0D, 2.0D, "minecraft:copper_ingot", false),'
expect "something that is not a killer changing the drops" "is not a killer and changes the drops"

edit $R '            if (!one.kind().equals(KILLS) && one.orbs() != 1.0D) {' '            if (false) {'
expect "the rule that keeps a fan out of the arithmetic" "changing the experience is not reported"

edit $R '            if (!one.kind().equals(KILLS) && one.drops() != 1.0D) {' '            if (false) {'
expect "the rule that keeps a conveyor out of the arithmetic" "changing the drops is not reported"

echo "one each and none spare"
edit $R '            new Machine("dread_soil", SETTLES, 1.0D, 1.0D, "minecraft:soul_sand", false));' \
        '            new Machine("dread_soil", MOVES, 1.0D, 1.0D, "minecraft:soul_sand", false));'
edit $R '            new Machine("quiet_casing", SETTLES, 1.0D, 1.0D, "minecraft:white_wool", false),' \
        '            new Machine("quiet_casing", MOVES, 1.0D, 1.0D, "minecraft:white_wool", false),'
expect_regen "a kind of machine nothing in the roster is" "leaves a kind of machine unbuilt"

edit $R '            if (!built) {' '            if (false) {'
expect "the rule that finds an unbuilt kind" "nothing in the roster is, is not reported"

edit $R '            if (!kinds.contains(one.kind())) {' '            if (false) {'
expect "the rule that holds a kind to something made to happen" "of a kind nothing here has heard of is not reported"

edit $R '            if (!seen.add(one.id())) {' '            if (false) {'
expect "the rule that finds two machines under one name" "written under one name are not reported"

echo "what a machine is built out of"
edit $R '            new Machine("quiet_casing", SETTLES, 1.0D, 1.0D, "minecraft:white_wool", false),' \
        '            new Machine("quiet_casing", SETTLES, 1.0D, 1.0D, "minecraft:wool", false),'
expect_regen "a machine built out of a tag that reads like an item" "so its recipe is dropped at load"

edit $G '                "i": {"item": one["needs"]},' '                "i": {"item": "minecraft:stone"},'
expect_regen "a recipe costing something other than what the row says" "written in two places and they have already stopped agreeing"

echo "one branch per kind, and no machine named"
edit $AC '            case FarmRules.GATHERS -> {' '            case "unreachable" -> {'
expect "a kind of machine nothing makes happen" "nothing in the farm gathers"

edit $AC '        switch (row.kind()) {' '        switch (row.hashCode() + "") {'
expect "what a machine does chosen by something a validator cannot read" "not chosen by which kind it is"

edit $AC '    public static final double REACHES = 2.0D;' \
        '    public static final double REACHES = 2.0D;
    private static final String WHICH = "grinder_saw";'
expect "a machine named in the branch that makes its kind happen" "is named in the acts"

echo "the numbers that can reorder the bestiary"
edit $AC '                    strike(self, mob, row.orbs(), row.drops());' \
        '                    strike(self, mob, 1.0D, 2.0D);'
expect "a killer handed numbers typed into the branch" "what a farm actually pays is a number typed into the branch"

edit $AC '        int worth = (int) Math.round(mob.getExperienceReward(server, mob) * orbs);' \
        '        int worth = (int) Math.round(12 * orbs);'
expect "a share of a number this module made up" "a share of a number this module made up"

echo "the block and what it remembers"
edit $BL '        FarmRules.Machine found = FarmRules.machine(name);' \
        '        FarmRules.Machine found = FarmRules.MACHINES.get(0);'
expect "a machine that does not find its own row" "does not find its own row"

edit $BL '        if (level.isClientSide() || !machine().powered()) {' '        if (level.isClientSide()) {'
expect "every machine ticking whether it has anything to do or not" "makes a large farm unplayable"

edit $BE '        FarmActs.happen(this, machine());' '        sinceAct = 0;'
expect "nothing handing a machine to the acts" "no machine ever does anything"

edit $BE '        tag.putInt("ElysiumHeldOrbs", heldOrbs);' '        '
expect "a gatherer that forgets what it collected" "lost the first time the chunk unloads"

echo "nothing registered by hand"
edit $RG '        for (FarmRules.Machine one : FarmRules.MACHINES) {
            made.put(one.id(), BLOCKS.register(one.id(),' \
        '        for (FarmRules.Machine one : java.util.List.of(FarmRules.MACHINES.get(0))) {
            made.put(one.id(), BLOCKS.register(one.id(),'
expect "machines registered from a list somebody wrote out" "named by hand rather than walked"

edit $RG '        if (made.size() != FarmRules.MACHINES.size()) {
            throw new IllegalStateException("the rules name "
                    + FarmRules.MACHINES.size() + " machine(s) and " + made.size()
                    + " block(s) were registered");
        }' '        '
expect "nothing counting the machine blocks registered" "nothing counts the machine blocks"

edit $RG '        if (made.size() != FarmRules.MACHINES.size()) {
            throw new IllegalStateException("the rules name "
                    + FarmRules.MACHINES.size() + " machine(s) and " + made.size()
                    + " item(s) were registered");
        }' '        '
expect "nothing counting the machine items registered" "nothing counts the machine items"

edit $RG '                            ALL.values().stream().map(DeferredBlock::get)' \
        '                            java.util.List.of(ALL.values().iterator().next()).stream().map(DeferredBlock::get)'
expect "a block entity type built from a list of names" "throws the first time anybody places one"

edit $MD '        com.elysium.bestiary.registry.FarmBlocks.BLOCKS.register(modEventBus);' '        '
expect "blocks never handed to the bus" "so none of them exists"

edit $MD '        com.elysium.bestiary.registry.FarmBlocks.ITEMS.register(modEventBus);' '        '
expect "blocks with no items to place them" "nothing can place any of them"

edit $MD '        com.elysium.bestiary.registry.FarmBlocks.BLOCK_ENTITIES.register(modEventBus);' '        '
expect "a block entity type never registered" "so placing one throws"

echo "what the generator writes"
remove $M/src/main/resources/assets/elysiumbestiary/textures/block/grinder_saw.png
expect "a machine with no texture" "has no texture"

remove $M/src/main/resources/assets/elysiumbestiary/blockstates/mob_fan.json
expect "a machine with no blockstate" "has no blockstate"

remove $M/src/main/resources/data/elysiumbestiary/loot_table/blocks/dread_soil.json
expect "a machine that drops nothing when broken" "has no loot table"

edit $G '        lang["bestiary.%s.machine.%s" % (NS, one["id"])] = MACHINE_WORDS[one["id"]]' \
        '        pass'
expect_regen "a machine with no sentence saying what it does" "has no sentence saying what it does"

echo "the rules are all asked"
edit $R '        bad.addAll(faultsInCredit(MACHINES, KEEPS_ORBS));' '        '
expect "a rule written, compiled and never added up" "faultsInCredit is written, compiled and never added up"

echo
echo "farm: $pass caught, $fail missed"
[ "$fail" -eq 0 ] || exit 1
