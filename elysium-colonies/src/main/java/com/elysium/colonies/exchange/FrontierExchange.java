package com.elysium.colonies.exchange;

import com.elysium.colonies.ElysiumColonies;
import com.elysium.colonies.colony.Colonies;
import com.elysium.colonies.market.Market;
import com.elysium.colonies.rules.ExchangeRules;
import com.elysium.colonies.rules.MarketRules;
import com.elysium.colonies.rules.ReliefRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * The thing a town does when it has run out of something.
 *
 * <h2>The deadlock</h2>
 *
 * MineColonies' request system is a cascade: a citizen wants forty spruce
 * planks, the warehouse is asked, the crafters are asked, the delivery workers
 * are asked, and if none of them can supply it the request lands on the
 * <em>player</em> resolver and waits. That last step is a note pinned to the
 * town hall saying "somebody bring these". It is a perfectly good design for a
 * colony a player visits, and a full stop for one meant to run itself.
 *
 * <p>So the town reads its own note, sells what it has too much of, and buys
 * what it lacks — at the market's prices, which depend on where the town is
 * and what day it is. A well-sited town sells dear and buys cheap; a town on a
 * bare rock does the opposite. That is the charter's decision, felt again at
 * the one moment a player is most likely to notice it.
 *
 * <h2>Why this is not cheating</h2>
 *
 * Because it is paid for out of the town's own stores, at a spread that makes
 * round-tripping always lose, capped at two stacks a day, and it makes the
 * owner more conspicuous every time. A town that tries to live off the market
 * will strip its warehouses in a week and still be poor.
 *
 * <h2>And when it has nothing at all</h2>
 *
 * A new colony has no surplus, so the market cannot help it, so it cannot get
 * the builder's hut without which it can build nothing — including the
 * builder's hut. {@link #grant} is the answer, and it is strictly bounded:
 * only while the settlement is inside its founding grant, only for what it is
 * actually waiting for, and only so much a day.
 */
public final class FrontierExchange {

    private FrontierExchange() {
    }

    /**
     * Settles as much of a town's outstanding list as it can afford today.
     *
     * @return how many separate trades were made, for the notice they earn
     */
    public static int settle(ServerLevel level, Colonies.Settlement town) {
        List<Colonies.Owed> owed = Colonies.owed(level, town.id());
        if (owed.isEmpty()) {
            return 0;
        }
        Map<ItemStack, Integer> spare =
                Colonies.surplusOf(level, town.id(), ExchangeRules.KEEP);
        if (spare.isEmpty()) {
            return 0;
        }
        ResourceLocation where = town.dimension();
        int sold = 0;
        int trades = 0;
        long purse = 0L;

        for (Colonies.Owed want : owed) {
            if (sold >= ExchangeRules.MOST_SPENT_PER_DAY) {
                break;
            }
            // Never trade a thing for itself. A town that has a surplus of the
            // very item it is waiting for has a delivery problem rather than a
            // supply one, and buying more would be this module destroying the
            // player's materials to solve a problem it misdiagnosed.
            int already = Colonies.stockOf(level, town.id(), want.wanted());
            if (already >= want.count()) {
                continue;
            }
            int missing = want.count() - already;
            int price = Market.priceOf(where, want.wanted(), town.day());

            // Sell until the purse covers what is missing, or the day runs out.
            for (Map.Entry<ItemStack, Integer> offer : spare.entrySet()) {
                if (purse >= MarketRules.costOf(missing, price)
                        || sold >= ExchangeRules.MOST_SPENT_PER_DAY) {
                    break;
                }
                if (ItemStack.isSameItem(offer.getKey(), want.wanted())
                        || offer.getValue() <= 0) {
                    continue;
                }
                int room = ExchangeRules.MOST_SPENT_PER_DAY - sold;
                int selling = Math.min(offer.getValue(), room);
                int taken = Colonies.take(level, town.id(), offer.getKey(), selling);
                if (taken <= 0) {
                    continue;
                }
                purse += MarketRules.purseFrom(taken,
                        Market.priceOf(where, offer.getKey(), town.day()));
                offer.setValue(offer.getValue() - taken);
                sold += taken;
            }

            int bought = Math.min(missing, MarketRules.buys(purse, price));
            if (bought <= 0) {
                continue;
            }
            if (Colonies.deliver(level, town.id(), want, bought)) {
                purse -= MarketRules.costOf(bought, price);
                trades++;
                ElysiumColonies.LOGGER.debug("{} bought {} x{} at {}", town.name(),
                        want.wanted().getDescriptionId(), bought, price);
            }
        }
        return trades;
    }

    /**
     * Gives a new settlement what it is waiting for.
     *
     * <h3>Why anything is ever free</h3>
     *
     * Because the alternative is a colony that can never start. Everything
     * this module does to keep a town alive costs the town something, and a
     * town founded yesterday has nothing — no surplus to sell, no mine, no
     * farm, and no builder to make any of those, because the builder's hut is
     * itself the thing it cannot get.
     *
     * <p>So the charter grant covers the beginning and nothing else. It runs
     * for {@link ReliefRules#GRANT_DAYS} days from founding, hands over only
     * what the town is actually waiting for, is capped per day, and then ends
     * for that settlement permanently.
     *
     * @return how many separate wants were covered
     */
    public static int grant(ServerLevel level, Colonies.Settlement town) {
        List<Colonies.Owed> owed = Colonies.owed(level, town.id());
        if (owed.isEmpty()) {
            return 0;
        }
        int given = 0;
        int covered = 0;
        for (Colonies.Owed want : owed) {
            int already = Colonies.stockOf(level, town.id(), want.wanted());
            if (already >= want.count()) {
                continue;
            }
            int giving = ReliefRules.granting(want.count() - already, given);
            if (giving <= 0) {
                break;
            }
            if (Colonies.deliver(level, town.id(), want, giving)) {
                given += giving;
                covered++;
                ElysiumColonies.LOGGER.info("{} was granted {} x{} under its charter",
                        town.name(), want.wanted().getDescriptionId(), giving);
            }
        }
        return covered;
    }
}
