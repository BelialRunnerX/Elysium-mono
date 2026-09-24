package com.elysium.colonies.port;

import com.elysium.colonies.ElysiumColonies;
import com.elysium.colonies.citizen.ColonyMemory;
import com.elysium.colonies.colony.Colonies;
import com.elysium.colonies.market.Market;
import com.elysium.colonies.registry.PortBlocks;
import com.elysium.colonies.rules.ExchangeRules;
import com.elysium.colonies.rules.MarketRules;
import com.elysium.colonies.rules.PortRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * A day at the spaceport: what went out, what came in, and what it cost.
 *
 * <h2>Shipping and receiving, in that order</h2>
 *
 * <ol>
 *   <li><b>Export.</b> Everything the town genuinely has too much of, up to
 *       the port's outbound capacity, sold at this world's price and banked.
 *       A rich world sells cheap and a bare one sells dear, which is the
 *       charter's own sentence about the place, felt as income.</li>
 *   <li><b>Freight.</b> If any colony anywhere has what this one is waiting
 *       for, the goods move directly between the two ports at the mid price,
 *       with no market in between. This is the best deal in the game and it
 *       is deliberately the one that needs two players' worth of building to
 *       reach.</li>
 *   <li><b>Import.</b> Whatever is still outstanding is bought from the
 *       market with what is left in the treasury.</li>
 * </ol>
 *
 * <h2>Why the port runs before the emergency exchange</h2>
 *
 * Because the exchange is a lifeline and a lifeline should be the last thing
 * tried. A town with a spaceport that still sold its furniture at a crisis
 * spread every morning would have built the elevator for nothing. The port
 * settles what it can afford; the exchange picks up whatever is left, as it
 * always did, for towns that have no port at all — which is most of them.
 *
 * <h2>What it will not do</h2>
 *
 * It will not sell something the town is waiting for, it will not spend money
 * the town does not have, and it will not take goods from another colony
 * without paying that colony. Each of those is a way an import system quietly
 * becomes a duplication bug, and each is checked.
 */
public final class Spaceport {

    private Spaceport() {
    }

    /**
     * What a day at the port came to.
     *
     * @param sold      items shipped out
     * @param bought    items bought from the market
     * @param freighted items shipped in from another colony
     * @param treasury  what the colony has left
     * @param trades    how many separate dealings, for the notice they earn
     */
    public record Run(int sold, int bought, int freighted, long treasury, int trades) {

        /** Everything that arrived, however it got here. */
        public int received() {
            return bought + freighted;
        }

        /** Whether anything happened at all. */
        public boolean moved() {
            return sold > 0 || received() > 0;
        }
    }

    /** A day at a town with no port, which is most towns. */
    public static Run idle(long treasury) {
        return new Run(0, 0, 0, Math.max(0L, treasury), 0);
    }

    /**
     * Runs this colony's port for the day.
     *
     * @param treasury what the colony had banked when the day began
     */
    public static Run run(ServerLevel level, Colonies.Settlement town, long treasury) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return idle(treasury);
        }
        PortRegistry ports = PortRegistry.of(server);
        PortRegistry.Berth berth = verified(level, ports, town);
        if (berth == null || !berth.working()) {
            return idle(treasury);
        }
        int tether = berth.tether();
        int legs = berth.legs();
        ResourceLocation where = town.dimension();

        Sale sale = sell(level, town, where, PortRules.outbound(tether, legs));
        long purse = PortRules.banked(treasury, sale.earned(), tether);

        int room = PortRules.inbound(tether, legs);
        Freight freight = freight(server, level, town, ports, purse, room);
        purse = Math.max(0L, purse - freight.spent());
        room -= freight.moved();

        Purchase purchase = buy(level, town, where, room, purse);
        purse = Math.max(0L, purse - purchase.spent());

        Run run = new Run(sale.moved(), purchase.moved(), freight.moved(), purse,
                sale.trades() + freight.trades() + purchase.trades());
        if (run.moved()) {
            ElysiumColonies.LOGGER.debug("{} shipped {} out and took {} in, holding {}",
                    town.name(), run.sold(), run.received(), run.treasury());
        }
        return run;
    }

    // ------------------------------------------------------------------
    // The structure, as it stands today
    // ------------------------------------------------------------------

    /**
     * Re-reads the elevator if anybody can see it, and trusts yesterday if not.
     *
     * A port's column is up to three hundred blocks in one chunk column, and
     * reading it means loading that chunk. Doing that every day for every
     * colony on a server would be a daily chunk load per port whether or not a
     * player has been within a mile of it — a cost paid forever for a check
     * that can only change when somebody is there to change it.
     *
     * <p>So a loaded port is re-read and its entry brought up to date, and an
     * unloaded one runs on its last reading. Sabotage still stops it: to cut
     * the tether you have to be standing there, and standing there is what
     * makes the chunk loaded.
     */
    private static PortRegistry.Berth verified(ServerLevel level, PortRegistry ports,
                                               Colonies.Settlement town) {
        PortRegistry.Berth berth = ports.berth(town.dimension(), town.id());
        if (berth == null) {
            return null;
        }
        if (!level.hasChunkAt(berth.anchor())) {
            return berth;
        }
        if (!level.getBlockState(berth.anchor()).is(PortBlocks.ELEVATOR_ANCHOR.get())) {
            ports.forget(town.dimension(), town.id());
            return null;
        }
        PortRegistry.Berth now = berth.read(SpaceElevator.read(level, berth.anchor()),
                town.day());
        ports.record(now);
        return now;
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    private record Sale(int moved, long earned, int trades) {
    }

    /**
     * Sells the town's surplus, keeping back what it is waiting for.
     *
     * The second half of that sentence is the whole of the rule that stops
     * this being a bug. A town whose builder has filed a request for sixty
     * planks and whose sawmill has just produced eighty of them has a surplus
     * of planks on paper; selling it would empty the warehouse of the one
     * thing the settlement is currently stopped on, and then buy it back at a
     * worse price. So anything on the outstanding list is not for sale today,
     * whatever the stock count says.
     */
    private static Sale sell(ServerLevel level, Colonies.Settlement town,
                             ResourceLocation where, int room) {
        if (room <= 0) {
            return new Sale(0, 0L, 0);
        }
        Map<ItemStack, Integer> spare =
                Colonies.surplusOf(level, town.id(), ExchangeRules.KEEP);
        if (spare.isEmpty()) {
            return new Sale(0, 0L, 0);
        }
        List<Colonies.Owed> waiting = Colonies.owed(level, town.id());
        int moved = 0;
        long earned = 0L;
        int trades = 0;
        for (Map.Entry<ItemStack, Integer> offer : spare.entrySet()) {
            if (moved >= room) {
                break;
            }
            if (offer.getValue() <= 0 || waitingFor(waiting, offer.getKey())) {
                continue;
            }
            int selling = Math.min(offer.getValue(), room - moved);
            int taken = Colonies.take(level, town.id(), offer.getKey(), selling);
            if (taken <= 0) {
                continue;
            }
            earned += MarketRules.purseFrom(taken,
                    Market.priceOf(where, offer.getKey(), town.day()));
            moved += taken;
            trades++;
        }
        return new Sale(moved, earned, trades);
    }

    /** Whether the town has this on its "somebody bring this" list. */
    private static boolean waitingFor(List<Colonies.Owed> waiting, ItemStack stack) {
        for (Colonies.Owed want : waiting) {
            if (ItemStack.isSameItem(want.wanted(), stack)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Freight between two colonies
    // ------------------------------------------------------------------

    private record Freight(int moved, long spent, int trades) {
    }

    /**
     * Buys what this town needs from another town that has it.
     *
     * <h3>Why this is the good deal</h3>
     *
     * There is no market standing between two colonies, so neither side pays
     * the spread: the buyer pays less than the market would charge and the
     * seller is paid more than the market would offer, both at once, out of
     * money that used to go to nobody. That is the reason to build a second
     * spaceport, and it is a better reason than an unlock.
     *
     * <h3>The order of the two halves</h3>
     *
     * The goods are handed over first and taken from the seller second, and
     * the seller is paid for what actually left their stores rather than for
     * what was ordered. The delivery is the half that can refuse — the host's
     * request system is entitled to say no — and doing the refusable half
     * first means a refusal costs nobody anything. Taking from an inventory
     * that was counted a moment ago in the same tick cannot meaningfully
     * fail; if it somehow comes up short, the seller is simply paid less, and
     * nobody's materials are destroyed.
     */
    private static Freight freight(MinecraftServer server, ServerLevel level,
                                   Colonies.Settlement town, PortRegistry ports,
                                   long purse, int room) {
        if (room <= 0) {
            return new Freight(0, 0L, 0);
        }
        List<Colonies.Owed> waiting = Colonies.owed(level, town.id());
        if (waiting.isEmpty()) {
            return new Freight(0, 0L, 0);
        }
        List<PortRegistry.Berth> elsewhere = ports.all();
        int moved = 0;
        long spent = 0L;
        int trades = 0;
        for (Colonies.Owed want : waiting) {
            if (moved >= room) {
                break;
            }
            int already = Colonies.stockOf(level, town.id(), want.wanted());
            if (already >= want.count()) {
                continue;
            }
            int missing = want.count() - already;
            for (PortRegistry.Berth other : elsewhere) {
                if (!other.working() || sameTown(other, town)) {
                    continue;
                }
                ServerLevel theirs = server.getLevel(
                        ResourceKey.create(Registries.DIMENSION, other.dimension()));
                if (theirs == null) {
                    continue;              // that world is not loaded on this server
                }
                Colonies.Settlement seller = settlement(theirs, other.colony());
                if (seller == null) {
                    continue;
                }
                int spare = Colonies.stockOf(theirs, other.colony(), want.wanted())
                        - ExchangeRules.KEEP;
                if (spare <= 0) {
                    continue;
                }
                int mid = PortRules.midPrice(
                        Market.asking(town.dimension(), want.wanted(), town.day()),
                        Market.offering(seller.dimension(), want.wanted(),
                                seller.day()));
                int shipping = Math.min(Math.min(missing, spare),
                        Math.min(PortRules.spareFor(other.tether(), other.legs()),
                                room - moved));
                shipping = Math.min(shipping,
                        PortRules.freightBuys(purse - spent, mid));
                if (shipping <= 0) {
                    continue;
                }
                if (!Colonies.deliver(level, town.id(), want, shipping)) {
                    continue;
                }
                int taken = Colonies.take(theirs, other.colony(), want.wanted(),
                        shipping);
                long cost = PortRules.freightCost(taken, mid);
                spent += cost;
                moved += taken;
                trades++;
                pay(theirs, other, cost);
                ElysiumColonies.LOGGER.info("{} shipped {} x{} to {} at {} each",
                        seller.name(), want.wanted().getDescriptionId(), taken,
                        town.name(), mid);
                break;                     // this want is settled; on to the next
            }
        }
        return new Freight(moved, spent, trades);
    }

    /** Puts the sale in the seller's treasury, where its own port can spend it. */
    private static void pay(ServerLevel theirs, PortRegistry.Berth berth, long cost) {
        if (cost <= 0L) {
            return;
        }
        ColonyMemory memory = ColonyMemory.of(theirs);
        ColonyMemory.Ledger ledger = memory.ledgerOf(berth.colony());
        memory.record(berth.colony(), ledger.withTreasury(
                PortRules.banked(ledger.treasury(), cost, berth.tether())));
    }

    private static boolean sameTown(PortRegistry.Berth berth,
                                    Colonies.Settlement town) {
        return berth.colony() == town.id()
                && berth.dimension().equals(town.dimension());
    }

    /** The settlement with this number in this world, or null. */
    private static Colonies.Settlement settlement(Level level, int colony) {
        for (Colonies.Settlement found : Colonies.in(level)) {
            if (found.id() == colony) {
                return found;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    private record Purchase(int moved, long spent, int trades) {
    }

    /**
     * Buys whatever is still outstanding, with whatever is left.
     *
     * At the market's asking price, which is the same number a player standing
     * in the same world would be charged. A colony that got a better rate than
     * a player would make the player's own trading pointless, and one that got
     * a worse rate would make the settlement's survival depend on somebody
     * being logged in.
     */
    private static Purchase buy(ServerLevel level, Colonies.Settlement town,
                                ResourceLocation where, int room, long purse) {
        if (room <= 0 || purse <= 0L) {
            return new Purchase(0, 0L, 0);
        }
        int moved = 0;
        long spent = 0L;
        int trades = 0;
        for (Colonies.Owed want : Colonies.owed(level, town.id())) {
            if (moved >= room) {
                break;
            }
            int already = Colonies.stockOf(level, town.id(), want.wanted());
            if (already >= want.count()) {
                continue;
            }
            int price = Market.priceOf(where, want.wanted(), town.day());
            int buying = Math.min(Math.min(want.count() - already, room - moved),
                    MarketRules.buys(purse - spent, price));
            if (buying <= 0) {
                continue;
            }
            if (!Colonies.deliver(level, town.id(), want, buying)) {
                continue;
            }
            spent += MarketRules.costOf(buying, price);
            moved += buying;
            trades++;
        }
        return new Purchase(moved, spent, trades);
    }
}
