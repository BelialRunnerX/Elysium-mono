package com.elysium.npcs.village;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * The two shapes every trade in this mod takes.
 *
 * <h2>Why only two</h2>
 *
 * A villager either buys something for emeralds or sells something for them.
 * Vanilla has a dozen listing classes because it also has enchanted books, maps,
 * dyed armour and potions to special-case; none of that is here, and writing
 * twelve classes to express two ideas would make the trade tables unreadable —
 * which matters, because the tables are the design and the code is not.
 *
 * <h2>Why vanilla items only</h2>
 *
 * This mod depends on the library and treats elysium-core as optional. A trade
 * naming an Elysium ingot would resolve to air in a pack that has the NPCs and
 * not the content, and a villager offering air for emeralds is worse than a
 * villager with nothing to say. Everything the tables below name is something
 * the base game guarantees exists.
 */
public final class ElysiumTrades {

    private ElysiumTrades() {
    }

    /** The villager pays emeralds for something you bring. */
    public record Buys(Item wanted, int count, int emeralds, int maxUses, int xp)
            implements VillagerTrades.ItemListing {

        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            return new MerchantOffer(
                    new ItemCost(wanted, count),
                    new ItemStack(Items.EMERALD, emeralds),
                    maxUses, xp, 0.05F);
        }
    }

    /** The villager sells something for emeralds. */
    public record Sells(Item offered, int count, int emeralds, int maxUses, int xp)
            implements VillagerTrades.ItemListing {

        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            return new MerchantOffer(
                    new ItemCost(Items.EMERALD, emeralds),
                    new ItemStack(offered, count),
                    maxUses, xp, 0.05F);
        }
    }
}
