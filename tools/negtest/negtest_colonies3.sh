#!/usr/bin/env bash
# Elysium Colonies, part three: running with nobody there.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-colonies
S=$M/src/main/java/com/elysium/colonies
R=$M/src/main/resources
D=$R/data/elysiumcolonies
SNAP=/tmp/colonies3_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-colonies)
restore() { rm -rf $ROOT/elysium-colonies && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@"; }

echo "waiting for a player"
# foundNear()'s first statement (the method was found() once; the statement
# is unique in the file and outlives the signature).
edit $S/event/ColonyLife.java '        BlockPos hall = town.centre();' \
'        if (level.getServer().getPlayerList().getPlayer(town.owner()) == null) return false;
        BlockPos hall = town.centre();'
expect "a town that only grows while somebody is logged in" "only grows while somebody is logged in"

edit $S/colony/Colonies.java 'return order(colony, building, WorkOrderType.UPGRADE);' \
                             'building.requestUpgrade(null, BlockPos.ZERO); return true;'
expect "a town one refusal away from a crash" "no null check"

edit $S/colony/Colonies.java 'public static boolean raise(Level level, int colonyId, BlockPos where) {' \
'public static net.minecraft.world.entity.player.Player fake(ServerLevel l) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(l);
    }

    public static boolean raise(Level level, int colonyId, BlockPos where) {'
expect "a fake player conjured into somebody else's colony" "conjures a fake player"

echo "founding"
edit $S/event/ColonyLife.java 'if (!Colonies.inside(level, town.id(), ground)) {
                    continue;
                }' ''
expect "a town building outside its own ground" "outside its own ground"

edit $S/event/ColonyLife.java 'if (!Colonies.clearOf(level, town.id(), ground, SiteRules.SPACING - 1)) {
                    continue;
                }' ''
expect "a town building on top of itself" "on top of what it has already built"

edit $S/event/ColonyLife.java 'if (!SiteRules.levelEnough(hall.getY(), ground.getY())) {
                    continue;
                }' ''
expect "a town building up a cliff face" "cliff face"

edit $S/event/ColonyLife.java 'String wanted = Charters.plan().next(town.citizens(),
                kind -> Colonies.has(level, town.id(), kind));' \
                              'String wanted = "minecolonies:builder";'
expect "a build order nobody wrote down" "a pack cannot"

edit $S/colony/Colonies.java 'hut.setPlacedBy(level, where, state, null, ItemStack.EMPTY);' ''
expect "a hut the colony never hears about" "nothing owns"

edit $S/colony/Colonies.java 'String blueprint = blueprintFor(pack, entry);' 'String blueprint = "";'
expect "a hut the builder can never raise" "find and break the block"

edit $S/colony/Colonies.java 'level.removeBlock(where, false);' ''
expect "a failed founding leaving litter" "litters the town"

python3 -c "
import json
p='$D/charter_needs/settlement_plan.json'
d=json.load(open(p)); d['wants']=d['wants'][1:]
json.dump(d,open(p,'w'),indent=2)"
expect "a plan that does not start with a builder" "including the builder"

python3 -c "
import json
p='$D/charter_needs/settlement_plan.json'
d=json.load(open(p)); d['wants'].append(d['wants'][0])
json.dump(d,open(p,'w'),indent=2)"
expect "a plan asking for one thing twice" "the second is unreachable"

rm -f $D/charter_needs/settlement_plan.json
expect "no plan at all" "autonomy stops at upgrades"

echo "the caretaker"
edit $S/event/ColonyLife.java 'Colonies.repair(level, town.id(), turn.at());' ''
expect "damage nobody ever goes over" "no player is coming to notice"

edit $S/event/ColonyLife.java 'if (built.isEmpty() || town.day() % CARETAKING != 0) {
            return;
        }' 'if (built.isEmpty()) {
            return;
        }'
expect "a caretaker on no schedule" "no schedule at all"

echo "the exchange"
edit $S/exchange/FrontierExchange.java 'List<Colonies.Owed> owed = Colonies.owed(level, town.id());' \
                                       'List<Colonies.Owed> owed = List.of();'
expect "the one list that stops a town, unread" "stops it dead"

edit $S/exchange/FrontierExchange.java 'if (Colonies.deliver(level, town.id(), want, bought)) {' 'if (false) {'
expect "a town that works out what it needs and never gets it" "never hands it over"

edit $S/exchange/FrontierExchange.java 'int taken = Colonies.take(level, town.id(), offer.getKey(), selling);' \
                                       'int taken = selling;'
expect "an exchange that charges nothing" "has not paid for"

edit $S/exchange/FrontierExchange.java 'if (ItemStack.isSameItem(offer.getKey(), want.wanted())
                        || offer.getValue() <= 0) {' 'if (offer.getValue() <= 0) {'
expect "a town grinding a thing into itself" "into one of the same item"

# settle() tests the cap twice (per want, per offer) and the validator looks for
# the phrase anywhere in the body, so both copies are broken.
edit $S/exchange/FrontierExchange.java 'if (sold >= ExchangeRules.MOST_SPENT_PER_DAY) {
                break;
            }' 'if (sold >= Integer.MAX_VALUE) {
                break;
            }'
edit $S/exchange/FrontierExchange.java '|| sold >= ExchangeRules.MOST_SPENT_PER_DAY) {' '|| sold >= Integer.MAX_VALUE) {'
expect "an exchange with no daily limit in the code" "rather than a lifeline"

edit $S/colony/Colonies.java 'requests.getPlayerResolver().getAllAssignedRequests()' \
                             'java.util.List.<IToken<?>>of()'
expect "the outstanding list read from the wrong place" "everything else has failed"

edit $S/colony/Colonies.java 'colony.getRequestManager().overruleRequest(token, giving);' \
                             'if (giving.isEmpty()) return false;'
expect "a delivery the host does not know about" "bookkeeping"

echo "the arithmetic"
# Three cases were deleted here on 2 Oct 2026. ExchangeRules used to hold a flat
# BASE_RATE, a daily budget and a world-factor rate; pricing moved to
# Market/MarketRules and the daily cap into FrontierExchange.settle(), and the
# validator no longer reads anything in ExchangeRules but KEEP. The flat-rate
# fault ("a charter that stops mattering once a town trades") is
# negtest_colonies5.sh's "an exchange trading at a flat rate"; the cap is the
# exchange case above.
edit $S/rules/ExchangeRules.java 'return Math.min(MOST_NOTICE_PER_DAY,
                Math.max(0, trades) * NOTICE_PER_TRADE);' \
                                 'return Math.max(0, trades) * NOTICE_PER_TRADE;'
expect "notice that scales without limit" "bring the Empire down"

edit $S/rules/SiteRules.java 'if (at < side) {
            return -ring + at;               // north edge, west to east
        }' 'if (at < side) {
            return -ring;
        }'
expect "a ring search that visits a square twice" "twice"

echo "the ruins"
edit $S/rules/RuinRules.java 'public static final int SPACING = 56;' 'public static final int SPACING = 48;'
expect "a ruins grid the structure set does not agree with" "RuinRules says"

edit $S/settle/Ruins.java '        ElysiumColonies.LOGGER.info("a ruin of a {} stands at {} in {}: {} of {} blocks left, {} rows",' '        Colonies.settle(level, ground, "ruin", pack, java.util.UUID.randomUUID(), "nobody");
        ElysiumColonies.LOGGER.info("a ruin of a {} stands at {} in {}: {} of {} blocks left, {} rows",'
expect "a ruin that founds a colony" "a ruin founds a colony"

rm -f $D/loot_table/chests/ruin.json
expect "a ruin chest with no table" "loot table elysiumcolonies:chests/ruin is not shipped"

edit $S/settle/Ruins.java '            if (!RuinRules.keep(seed, at.getX(), row, at.getZ(), height, RuinRules.wooden(name))) {
                continue;
            }' '            if (false) {
                continue;
            }'
expect "a ruin that places every block" "places every block, so it is a building"

edit $S/settle/Ruins.java 'RuinRules.keep(seed, at.getX(), row, at.getZ(), height,' 'RuinRules.keep(seed, at.getX(), at.getY(), at.getZ(), height,'
expect "a ruin decayed by its height in the world" "every block reads as roof"

edit $S/rules/RuinRules.java 'List.of("minecolonies", "structurize");' 'List.of("minecolonies");'
expect "a ruin that places the blueprint's placeholders" "not both skipped"

echo
echo "restoring and re-checking"
restore
if (cd $M && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "colonies (autonomy): $pass caught, $fail missed"
[ "$fail" -eq 0 ]
