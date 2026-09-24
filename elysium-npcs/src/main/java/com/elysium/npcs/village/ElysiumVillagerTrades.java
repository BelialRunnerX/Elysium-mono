package com.elysium.npcs.village;

import com.elysium.npcs.ElysiumNpcs;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;

/**
 * What the five new professions deal in.
 *
 * <h2>The table is the design</h2>
 *
 * Five levels each, novice to master, two offers a level. The numbers are
 * vanilla's shape — a novice sells for a handful of emeralds and buys a stack
 * of something common; a master sells one expensive thing — so a player who
 * knows how a librarian works knows how these work.
 *
 * <h2>Every one of these is reachable</h2>
 *
 * That is the constraint that shaped the tables more than flavour did. A trade
 * asking for something a player cannot get in the dimension the village is in
 * is a trade slot wasted, and villagers only offer what their level unlocks, so
 * a dead entry at level two blocks nothing but silently halves that tier. What
 * each profession buys is something a player already accumulates: crops, scrap,
 * stone, ore. What it sells is something worth walking to a village for.
 */
@EventBusSubscriber(modid = ElysiumNpcs.MODID)
public final class ElysiumVillagerTrades {

    private ElysiumVillagerTrades() {
    }

    private static VillagerTrades.ItemListing buy(Item wanted, int count,
                                                  int emeralds, int uses, int xp) {
        return new ElysiumTrades.Buys(wanted, count, emeralds, uses, xp);
    }

    private static VillagerTrades.ItemListing sell(Item offered, int count,
                                                   int emeralds, int uses, int xp) {
        return new ElysiumTrades.Sells(offered, count, emeralds, uses, xp);
    }

    /**
     * Level -> the offers unlocked at it, for one profession.
     *
     * Written as a nested array so a whole profession reads as one block. The
     * alternative — a builder call per offer — is forty lines per villager and
     * makes comparing two professions' curves impossible.
     */
    private static VillagerTrades.ItemListing[][] tableFor(VillagerProfession profession) {
        if (profession == ElysiumStations.SIGNALLER.profession().get()) {
            return new VillagerTrades.ItemListing[][]{
                    {buy(Items.COPPER_INGOT, 12, 1, 16, 2),
                            sell(Items.COMPASS, 1, 4, 12, 1)},
                    {buy(Items.AMETHYST_SHARD, 6, 1, 16, 5),
                            sell(Items.REDSTONE, 8, 1, 12, 5)},
                    {buy(Items.LAPIS_LAZULI, 10, 1, 16, 10),
                            sell(Items.ENDER_PEARL, 1, 8, 8, 10)},
                    {buy(Items.GLOWSTONE_DUST, 8, 1, 12, 15),
                            sell(Items.LODESTONE, 1, 22, 4, 15)},
                    {sell(Items.RECOVERY_COMPASS, 1, 36, 2, 30),
                            sell(Items.ECHO_SHARD, 1, 20, 3, 30)},
            };
        }
        if (profession == ElysiumStations.SPLICER.profession().get()) {
            return new VillagerTrades.ItemListing[][]{
                    {buy(Items.GLOW_BERRIES, 16, 1, 16, 2),
                            sell(Items.GLOW_LICHEN, 4, 1, 12, 1)},
                    {buy(Items.SPIDER_EYE, 12, 1, 16, 5),
                            sell(Items.FERMENTED_SPIDER_EYE, 2, 3, 12, 5)},
                    {buy(Items.NETHER_WART, 12, 1, 16, 10),
                            sell(Items.GOLDEN_CARROT, 3, 4, 12, 10)},
                    {buy(Items.HONEY_BOTTLE, 6, 1, 12, 15),
                            sell(Items.GHAST_TEAR, 1, 14, 4, 15)},
                    {sell(Items.GOLDEN_APPLE, 1, 28, 3, 30),
                            sell(Items.SCULK_CATALYST, 1, 30, 2, 30)},
            };
        }
        if (profession == ElysiumStations.VOIDWRIGHT.profession().get()) {
            return new VillagerTrades.ItemListing[][]{
                    {buy(Items.COBBLED_DEEPSLATE, 24, 1, 16, 2),
                            sell(Items.TUFF, 8, 1, 12, 1)},
                    {buy(Items.RAW_IRON, 12, 1, 16, 5),
                            sell(Items.IRON_INGOT, 2, 3, 12, 5)},
                    {buy(Items.RAW_GOLD, 10, 1, 16, 10),
                            sell(Items.GOLD_INGOT, 2, 4, 12, 10)},
                    {buy(Items.OBSIDIAN, 6, 2, 12, 15),
                            sell(Items.DIAMOND, 1, 30, 4, 15)},
                    {sell(Items.NETHERITE_SCRAP, 1, 40, 2, 30),
                            sell(Items.AMETHYST_BLOCK, 2, 12, 4, 30)},
            };
        }
        if (profession == ElysiumStations.HYDROPONICIST.profession().get()) {
            return new VillagerTrades.ItemListing[][]{
                    {buy(Items.WHEAT, 20, 1, 16, 2),
                            sell(Items.BONE_MEAL, 6, 1, 16, 1)},
                    {buy(Items.POTATO, 20, 1, 16, 5),
                            sell(Items.BREAD, 6, 1, 16, 5)},
                    {buy(Items.KELP, 24, 1, 16, 10),
                            sell(Items.CAKE, 1, 4, 8, 10)},
                    {buy(Items.PUMPKIN, 8, 1, 12, 15),
                            sell(Items.GOLDEN_CARROT, 4, 3, 12, 15)},
                    {sell(Items.ENCHANTED_GOLDEN_APPLE, 1, 60, 1, 30),
                            sell(Items.MELON_SEEDS, 4, 2, 8, 30)},
            };
        }
        if (profession == ElysiumStations.GEAR_SMITH.profession().get()) {
            // Metal in, tools out. Everything named here is something the base
            // game guarantees exists, for the same reason every other table
            // here does: this module treats the content mod as optional, and a
            // villager offering air for emeralds is worse than one with
            // nothing to say.
            return new VillagerTrades.ItemListing[][]{
                    {buy(Items.COPPER_INGOT, 12, 1, 16, 2),
                            sell(Items.STONE_PICKAXE, 1, 2, 12, 1)},
                    {buy(Items.IRON_INGOT, 6, 1, 16, 5),
                            sell(Items.IRON_PICKAXE, 1, 6, 12, 5)},
                    {buy(Items.COAL, 20, 1, 16, 10),
                            sell(Items.SMITHING_TABLE, 1, 8, 8, 10)},
                    {buy(Items.GOLD_INGOT, 6, 1, 12, 15),
                            sell(Items.GRINDSTONE, 1, 7, 8, 15)},
                    {sell(Items.DIAMOND, 1, 32, 2, 30),
                            sell(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 40, 1, 30)},
            };
        }
        if (profession == ElysiumStations.SALVAGER.profession().get()) {
            return new VillagerTrades.ItemListing[][]{
                    {buy(Items.ROTTEN_FLESH, 32, 1, 16, 2),
                            sell(Items.LEATHER, 3, 1, 12, 1)},
                    {buy(Items.IRON_NUGGET, 24, 1, 16, 5),
                            sell(Items.IRON_INGOT, 1, 2, 12, 5)},
                    {buy(Items.BONE, 20, 1, 16, 10),
                            sell(Items.CHAIN, 3, 2, 12, 10)},
                    {buy(Items.GUNPOWDER, 12, 1, 12, 15),
                            sell(Items.TNT, 1, 6, 8, 15)},
                    {sell(Items.NETHERITE_SCRAP, 1, 45, 1, 30),
                            sell(Items.ANVIL, 1, 28, 2, 30)},
            };
        }
        return null;
    }

    @SubscribeEvent
    public static void onTrades(VillagerTradesEvent event) {
        VillagerTrades.ItemListing[][] table = tableFor(event.getType());
        if (table == null) {
            return;
        }
        for (int level = 0; level < table.length; level++) {
            // Vanilla numbers levels from one; the array is zero-based. Getting
            // this wrong puts the master's trades on a novice and leaves level
            // five empty, which looks like generosity and is a bug.
            List<VillagerTrades.ItemListing> offers =
                    event.getTrades().get(level + 1);
            if (offers != null) {
                offers.addAll(List.of(table[level]));
            }
        }
    }
}
