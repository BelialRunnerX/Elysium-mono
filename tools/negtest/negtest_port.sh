#!/usr/bin/env bash
# The spaceport, broken one way at a time.
#
# Every check the port added is deliberately defeated here and the suite fails
# if the validator or the harness does not notice. A check nobody has seen fail
# is a check nobody has seen.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-colonies
S=$M/src/main/java/com/elysium/colonies
R=$M/src/main/resources
SNAP=/tmp/port_snap.tar
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
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "the blocks a port is made of"
edit $S/registry/PortBlocks.java 'BLOCKS.register(
            "elevator_tether",' 'BLOCKS.register(
            "elevator_cable",'
expect "a block of the structure that is never registered" "is not registered as a block"

edit $S/registry/PortBlocks.java 'ITEMS.register("elevator_tether",' 'ITEMS.register("elevator_cable",'
expect "eighty blocks nobody can carry" "has no item, so it can be placed by a command"

rm $R/assets/elysiumcolonies/blockstates/elevator_counterweight.json
expect "a block with no blockstate" "has no blockstate"

rm $R/data/elysiumcolonies/loot_table/blocks/elevator_tether.json
expect "eighty blocks of tether that vanish when mined" "has no drop"

edit $R/data/elysiumcolonies/recipe/elevator_tether.json '"minecraft:iron_ingot"' '"elysium:aetherium_ingot"'
expect "a spaceport only a full Elysium install can build" "buildable out of the base game"

echo "the structure, read out of the world"
edit $S/port/SpaceElevator.java 'boolean capped = level.getBlockState(at)
                .is(PortBlocks.ELEVATOR_COUNTERWEIGHT.get());' 'boolean capped = true;'
expect "a bare mast counted as a spaceport" "nothing checks for a counterweight"

edit $S/port/SpaceElevator.java 'while (at.getY() < ceiling
                && level.getBlockState(at).is(PortBlocks.ELEVATOR_TETHER.get())) {' 'while (at.getY() < ceiling && false) {'
expect "a column nobody walks" "the column is never walked"

edit $S/port/SpaceElevator.java 'return new Reading(PortRules.tall(tether) && !roofed, tether,
                PortRules.tall(tether) ? null : at.immutable(), roofed, legs);' 'return new Reading(!roofed, tether, null, roofed, legs);'
expect "a three-block stub counted as a space elevator" "the minimum height is not applied"

edit $S/port/SpaceElevator.java 'boolean roofed = !openAbove(level, at);' 'boolean roofed = false;'
expect "a space elevator inside a mountain" "nothing checks the sky"

edit $S/port/SpaceElevator.java 'int highest = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                counterweight.getX(), counterweight.getZ());' 'int highest = counterweight.getY();
        for (int y = counterweight.getY(); y < 320; y++) { highest = y; }'
expect "the sky counted one block at a time" "off the heightmap"

echo "where the numbers come from"
edit $S/port/Spaceport.java 'long purse = PortRules.banked(treasury, sale.earned(), tether);' 'long purse = treasury + sale.earned();'
expect "a treasury with no ceiling" "never calls PortRules.banked("

edit $S/port/Spaceport.java 'Sale sale = sell(level, town, where, PortRules.outbound(tether, legs));' 'Sale sale = sell(level, town, where, 4096);'
expect "how much a port can send, decided in the handler" "never calls PortRules.outbound("

edit $S/port/Spaceport.java 'int room = PortRules.inbound(tether, legs);' 'int room = 4096;'
expect "how much a port can receive, decided in the handler" "never calls PortRules.inbound("

edit $S/port/Spaceport.java 'Math.min(PortRules.spareFor(other.tether(), other.legs()),
                                room - moved));' 'Math.min(spare, room - moved));'
expect "a port that empties itself supplying a sister colony" "never calls PortRules.spareFor("

edit $S/port/Spaceport.java 'int mid = PortRules.midPrice(' 'int mid = Math.min('
expect "freight priced by the handler" "never calls PortRules.midPrice("

echo "selling"
edit $S/port/Spaceport.java 'if (offer.getValue() <= 0 || waitingFor(waiting, offer.getKey())) {' 'if (offer.getValue() <= 0) {'
expect "a town selling the planks it is stopped on" "without checking the outstanding list"

edit $S/port/Spaceport.java 'Colonies.surplusOf(level, town.id(), ExchangeRules.KEEP);' 'Colonies.surplusOf(level, town.id(), 0);'
expect "a port with its own idea of what a surplus is" "decides for itself what a surplus is"

echo "freight"
edit $S/port/Spaceport.java 'ServerLevel theirs = server.getLevel(
                        ResourceKey.create(Registries.DIMENSION, other.dimension()));' 'ServerLevel theirs = level;'
expect "freight that never leaves its own world" "never leaves the world it started in"

edit $S/port/Spaceport.java 'if (!other.working() || sameTown(other, town)) {' 'if (!other.working()) {'
expect "a colony shipping to itself" "can ship to itself"

edit $S/port/Spaceport.java 'long cost = PortRules.freightCost(taken, mid);' 'long cost = PortRules.freightCost(shipping, mid);'
expect "a short delivery charged at full price" "by what was ordered rather than by what actually left"

edit $S/port/Spaceport.java 'pay(theirs, other, cost);' ''
expect "goods leaving a warehouse with nobody paid" "theft with extra steps"

edit $S/port/Spaceport.java 'if (!Colonies.deliver(level, town.id(), want, shipping)) {
                    continue;
                }
                int taken = Colonies.take(theirs, other.colony(), want.wanted(),
                        shipping);' 'int taken = Colonies.take(theirs, other.colony(), want.wanted(),
                        shipping);
                if (!Colonies.deliver(level, town.id(), want, taken)) {
                    continue;
                }'
expect "a refused delivery that destroys the goods" "before the buyer's request has accepted"

edit $S/port/Spaceport.java 'memory.record(berth.colony(), ledger.withTreasury(
                PortRules.banked(ledger.treasury(), cost, berth.tether())));' ''
expect "a seller who is never actually paid" "not banked in the seller's own treasury"

echo "buying"
edit $S/port/Spaceport.java 'MarketRules.buys(purse - spent, price));' 'MarketRules.buys(purse, price));'
expect "a treasury spent twice in one pass" "spent twice in one pass"

edit $S/port/Spaceport.java 'spent += MarketRules.costOf(buying, price);' ''
expect "imports that cost nothing" "never taken out of the treasury"

edit $S/port/Spaceport.java 'int price = Market.priceOf(where, want.wanted(), town.day());' 'int price = MarketRules.BASE_VALUE;'
expect "a price that ignores where the colony is" "other than this world's market"

echo "the port and the town that has none"
edit $S/port/Spaceport.java 'if (server == null) {
            return idle(treasury);' 'if (server == null) {
            return new Run(0, 0, 0, 0L, 0);'
edit $S/port/Spaceport.java 'if (berth == null || !berth.working()) {
            return idle(treasury);' 'if (berth == null || !berth.working()) {
            return new Run(0, 0, 0, 0L, 0);'
expect "a colony with no port having its treasury emptied" "is not simply left alone"

edit $S/port/Spaceport.java 'PortRegistry.Berth berth = verified(level, ports, town);' 'PortRegistry.Berth berth = ports.berth(town.dimension(), town.id());'
expect "a port nobody re-reads, so cutting the tether does nothing" "without ever re-reading the world"

edit $S/port/Spaceport.java 'if (!level.hasChunkAt(berth.anchor())) {
            return berth;
        }' ''
expect "a chunk load per port per day forever" "chunk load per port per day"

edit $S/port/Spaceport.java 'ports.forget(town.dimension(), town.id());' ''
expect "a dismantled spaceport that goes on shipping" "stays in the registry, so a dismantled"

echo "where the ports are remembered"
edit $S/port/PortRegistry.java 'ServerLevel home = server.getLevel(Level.OVERWORLD);' 'ServerLevel home = server.overworld();
        home = null;'
expect "a registry kept per world" "kept per world"

edit $S/citizen/ColonyMemory.java 'Codec.LONG.optionalFieldOf("treasury", 0L)' 'Codec.LONG.fieldOf("treasury")'
expect "a ledger field that breaks every existing save" "not an optional ledger field"

echo "the anchor"
edit $S/port/PortAnchorBlock.java 'if (PortRegistry.of(server.getServer())
                    .forgetAt(server.dimension().location(), pos)) {' 'if (false) {'
expect "a spaceport that survives being dismantled" "leaves the port in the registry"

edit $S/port/PortAnchorBlock.java 'if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server) {' 'if (level instanceof ServerLevel server) {'
expect "a port lost when a neighbour changes" "deregisters itself on any block update"

edit $S/port/PortAnchorBlock.java 'ports.record(new PortRegistry.Berth(level.dimension().location(), town.id(),
                pos.immutable(), reading.tether(), reading.working(), town.day(),
                reading.legs()));' ''
expect "a built spaceport nothing ever finds" "does not record the port"

edit $S/port/PortAnchorBlock.java 'case "roofed" -> Component.translatable("elysium.colony.port.roofed");' 'case "covered" -> Component.translatable("elysium.colony.port.covered");'
expect "a complaint with no message behind it" "can complain that it is roofed"

echo "the flare"
edit $S/rules/PortRules.java 'public static final int GATHER = 36;' 'public static final int GATHER = 10;'
expect "a flare as wide as it is tall" "reads as a tent"

edit $S/rules/PortRules.java 'public static final float BEND = 2.0F;' 'public static final float BEND = 1.0F;'
expect "legs that bend evenly, so the base is a cone" "base is a cone"

edit $S/rules/PortRules.java 'public static final int PER_LEG = 64;' 'public static final int PER_LEG = 0;'
expect "a brace worth nothing" "no more to spare for a sister colony"

edit $S/rules/PortRules.java 'public static final int PER_LEG = 64;' 'public static final int PER_LEG = 4000;'
expect "a flare that makes height pointless" "nobody would ever build height again"

edit $S/rules/PortRules.java 'int braced = Math.max(0, Math.min(LEGS, legs));' 'int braced = Math.max(0, legs);'
expect "more legs than there are legs" "more legs than there are legs"

edit $S/rules/PortRules.java 'public static final int LEG_TOLERANCE = 3;' 'public static final int LEG_TOLERANCE = 12;'
expect "a tolerance wider than the flare" "tolerance is wider than the flare"

edit $S/rules/PortRules.java 'return new int[]{Math.max(1, GATHER / 12), GATHER / 4, GATHER / 2,
                GATHER * 3 / 4};' 'return new int[]{1, 2, 3};'
expect "a brace decided entirely near the ground" "half a leg is a whole brace"

edit $S/port/ElevatorBase.java 'if (level.getBlockState(at).is(PortBlocks.ELEVATOR_PYLON.get())) {' 'if (level.getBlockState(at).isSolid()) {'
expect "a brace that can be made of anything" "a brace can be made of anything"

edit $S/port/ElevatorBase.java 'PortRules.legX(leg, height), height,
                PortRules.legZ(leg, height)' '0, height, 0'
expect "legs looked for somewhere the rules never put them" "without asking the rules where a leg should be"

edit $S/port/ElevatorBase.java 'for (int height : PortRules.legSamples()) {' 'for (int height : new int[]{2}) {'
expect "a brace decided at a height the handler picked" "heights the handler picked"

edit $S/port/SpaceElevator.java 'int legs = ElevatorBase.legs(level, anchor);' 'int legs = 0;'
expect "an elevator read without looking at what holds it up" "looking at what is holding it up"

edit $S/port/Spaceport.java 'Sale sale = sell(level, town, where, PortRules.outbound(tether, legs));' 'Sale sale = sell(level, town, where, PortRules.outbound(tether));'
expect "legs counted, stored and never spent" "counted, stored, reported and never spent"

edit $M/tools/gen_spaceport.py 'FOOT = float(rule("FOOT"))' 'FOOT = 12'
expect "a generator with its own copy of the curve" "hard-codes FOOT = 12"

python3 - <<'PYEOF'
import os
p = "/tmp/Elysium-mono/elysium-colonies/src/main/resources/blueprints/elysiumcolonies/Bastion/infrastructure/spaceport.blueprint"
os.rename(p, p + ".bak")
PYEOF
expect "a style with no spaceport" "has no spaceport blueprint"
python3 - <<'PYEOF'
import os
p = "/tmp/Elysium-mono/elysium-colonies/src/main/resources/blueprints/elysiumcolonies/Bastion/infrastructure/spaceport.blueprint"
if os.path.isfile(p + ".bak") and not os.path.isfile(p):
    os.rename(p + ".bak", p)
PYEOF

echo "what a player can do with it"
edit $S/command/ColonyCommand.java 'if (!Colonies.owns(level, here.id(), player.getUUID())
                && !memberOf(player, here)) {' 'if (false) {'
expect "cargo given away to a stranger's treasury" "nothing to do with"

edit $S/command/ColonyCommand.java 'if (berth == null || !berth.working()) {' 'if (berth == null) {'
expect "selling through a port that is not standing" "sold through a port that is not standing"

edit $S/command/ColonyCommand.java 'long paid = MarketRules.purseFrom(count,
                Market.priceOf(here.dimension(), carried, here.day()));' 'long paid = (long) count * MarketRules.asking(
                Market.priceOf(here.dimension(), carried, here.day()));'
expect "a player paid the asking price for their own cargo" "two different numbers depending on who"

edit $S/command/ColonyCommand.java 'long held = PortRules.banked(ledger.treasury(), paid, berth.tether());' 'long held = ledger.treasury() + paid;'
expect "one good route funding a colony forever" "ignores the treasury's ceiling"

edit $S/command/ColonyCommand.java 'player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                ItemStack.EMPTY);' ''
expect "cargo paid for and never taken" "duplication bug with a shopfront"

echo "the order of the day"
edit $S/event/ColonyLife.java 'Spaceport.Run shipped = Spaceport.run(level, town, ledger.treasury());' 'Spaceport.Run shipped = Spaceport.idle(ledger.treasury());'
expect "a spaceport that is scenery" "never runs, so it is scenery"

edit $S/event/ColonyLife.java 'Spaceport.Run shipped = Spaceport.run(level, town, ledger.treasury());
        ledger = ledger.withTreasury(shipped.treasury());
        int traded = FrontierExchange.settle(level, town);' 'int traded = FrontierExchange.settle(level, town);
        Spaceport.Run shipped = Spaceport.run(level, town, ledger.treasury());
        ledger = ledger.withTreasury(shipped.treasury());'
expect "the lifeline tried before the port" "tried before the spaceport"

edit $S/event/ColonyLife.java 'ledger = ledger.withTreasury(shipped.treasury());' ''
expect "a treasury that resets every morning" "never written back"

edit $S/event/ColonyLife.java 'long banked = GrowthRules.banked(ledger.development(), earned);' 'long banked = GrowthRules.banked(ledger.treasury(), earned);'
expect "money and labour as the same number" "the same number, so a colony can buy its way"

echo "the charter grant"
edit $S/rules/ReliefRules.java 'public static final int GRANT_DAYS = 100;' 'public static final int GRANT_DAYS = 12;'
expect "a grant sized for the bootstrap alone" "sized for the bootstrap"

edit $S/rules/ReliefRules.java 'public static final int GRANT_DAYS = 100;' 'public static final int GRANT_DAYS = 4000;'
expect "a grant that never ends in practice" "never ends in practice"

edit $S/rules/ReliefRules.java 'public static final int GRANT_STACKS_PER_DAY = 64;' 'public static final int GRANT_STACKS_PER_DAY = 2;'
expect "a day's grant that cannot finish a building" "cannot finish a building"

edit $S/rules/ReliefRules.java 'public static final int GRANT_PER_DAY = GRANT_STACKS_PER_DAY * 64;' 'public static final int GRANT_PER_DAY = 128;'
expect "two ways of saying how much, disagreeing" "two ways of saying how much disagree"

edit $S/rules/ReliefRules.java 'public static final int REPORT_AFTER = 4;' 'public static final int REPORT_AFTER = 40;'
expect "an owner told only after months of silence" "months of silence"

edit $S/event/ColonyLife.java 'if (relief == ReliefRules.Relief.REPORT
                || ReliefRules.alsoReport(town.day(), ledger.lastProgress())) {' 'if (relief == ReliefRules.Relief.REPORT) {'
expect "a town that is helped and never heard from" "sits silent for months"

echo "the arithmetic itself"
edit $S/rules/PortRules.java 'public static final int MINIMUM_TETHER = 80;' 'public static final int MINIMUM_TETHER = 20;'
expect "a radio mast calling itself a space elevator" "radio mast"

edit $S/rules/PortRules.java 'public static final int BASE_THROUGHPUT = 256;' 'public static final int BASE_THROUGHPUT = 100;'
expect "a port that moves less than the emergency exchange" "no more than the emergency exchange"

edit $S/rules/PortRules.java 'public static final int PER_BLOCK = 4;' 'public static final int PER_BLOCK = 0;'
expect "height that buys nothing" "a taller elevator moved no more"

edit $S/rules/PortRules.java 'public static int outbound(int tether, int legs) {
        return throughput(tether, legs) / 2;' 'public static int outbound(int tether, int legs) {
        return throughput(tether, legs);'
expect "a port that only sends" "only sends or only receives"

edit $S/rules/PortRules.java 'return outbound(tether) / 2;
    }' 'return outbound(tether);
    }'
expect "a port that would give away its whole day" "everything it had to send"

edit $S/rules/PortRules.java 'return Math.min(ceiling(tether), Math.max(0L, held) + Math.max(0L, earned));' 'return Math.max(0L, held) + Math.max(0L, earned);'
expect "a treasury with no ceiling at all" "banked past its ceiling"

edit $S/rules/PortRules.java 'return Math.max(1, (Math.max(1, asking) + Math.max(1, offering)) / 2);' 'return Math.max(1, Math.max(1, asking) + Math.max(1, offering));'
expect "freight that costs more than the market" "shipping direct cost more than buying"

edit $S/rules/PortRules.java 'return (long) Math.max(0, count) * Math.max(1, mid);' 'return (long) Math.max(0, count) * Math.max(1, mid) * 2L;'
expect "freight charged at something other than the mid price" "not priced at the mid price"

edit $S/rules/PortRules.java 'long many = Math.max(0L, treasury) / Math.max(1, mid);' 'long many = (Math.max(0L, treasury) + Math.max(1, mid) - 1) / Math.max(1, mid);'
expect "a purse that buys more than it can pay for" "bought one anyway"

echo
echo "restoring and re-checking"
restore
if (cd $ROOT && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "port: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
