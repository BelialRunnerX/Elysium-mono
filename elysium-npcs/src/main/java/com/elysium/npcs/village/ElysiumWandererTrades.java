package com.elysium.npcs.village;

import com.elysium.npcs.ElysiumNpcs;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the wandering trader carries (Apotheosis' AP-27, and the registry of
 * PL-3).
 *
 * <h2>What is wrong with him in vanilla</h2>
 *
 * He sells saplings, dyes and coral. Every one of those is a thing a player
 * either already has or can walk to, so the only reason anybody has ever
 * stopped him is the leads on his llamas — and the standing joke about killing
 * him for them is the honest review of his stock.
 *
 * <p>He is the one merchant in the game who is not from here, which is the
 * thing worth leaning on. So what he buys is what a player has too much of,
 * and what he sells is the small list of things that are not hard so much as
 * <em>tedious</em>: a nautilus shell, a name tag, a saddle, honeycomb, a
 * bucket of powder snow. None of it is powerful and all of it is a Saturday
 * afternoon saved.
 *
 * <h2>The registry</h2>
 *
 * {@link #register} is the whole of PL-3. Anything that wants to put something
 * in his pack — another Elysium module, or another mod entirely — calls it,
 * and nothing has to edit the table below. The list is copy-on-write because
 * registration happens during startup and the event fires on a world thread.
 *
 * <h2>Vanilla items only, like every other table here</h2>
 *
 * This module treats the content mod as optional, and a trader offering air
 * for emeralds is worse than one with nothing to say. Anything Elysium-specific
 * arrives through {@link #register}, from the module that owns it.
 */
@EventBusSubscriber(modid = ElysiumNpcs.MODID)
public final class ElysiumWandererTrades {

    private ElysiumWandererTrades() {
    }

    /** One offer somebody else has added, and which pocket it goes in. */
    public record Added(VillagerTrades.ItemListing listing, boolean rare) {
    }

    private static final List<Added> ADDED = new CopyOnWriteArrayList<>();

    /**
     * Put something in the wandering trader's pack.
     *
     * @param rare true for the two slots he only sometimes has, false for the
     *             common ones he always offers some of
     */
    public static void register(VillagerTrades.ItemListing listing, boolean rare) {
        if (listing != null) {
            ADDED.add(new Added(listing, rare));
        }
    }

    /** Everything registered from outside, for a validator or a debug command. */
    public static List<Added> added() {
        return List.copyOf(ADDED);
    }

    private static VillagerTrades.ItemListing buy(Item wanted, int count,
                                                  int emeralds, int uses) {
        return new ElysiumTrades.Buys(wanted, count, emeralds, uses, 1);
    }

    private static VillagerTrades.ItemListing sell(Item offered, int count,
                                                   int emeralds, int uses) {
        return new ElysiumTrades.Sells(offered, count, emeralds, uses, 1);
    }

    /**
     * The common pocket: what he takes off your hands, and the small useful
     * things.
     *
     * He buys what accumulates — rotten flesh, bones, gravel, string — because
     * that is the trade nobody else in the game offers and it is the reason to
     * stop him at all.
     */
    private static List<VillagerTrades.ItemListing> generic() {
        return new ArrayList<>(List.of(
                buy(Items.ROTTEN_FLESH, 32, 1, 16),
                buy(Items.BONE, 20, 1, 16),
                buy(Items.GRAVEL, 24, 1, 12),
                buy(Items.STRING, 20, 1, 12),
                buy(Items.SPIDER_EYE, 12, 1, 12),
                sell(Items.LEAD, 1, 3, 8),
                sell(Items.HONEYCOMB, 3, 3, 8),
                sell(Items.SEA_PICKLE, 2, 4, 6),
                sell(Items.GLOW_INK_SAC, 2, 4, 6),
                sell(Items.PRISMARINE_CRYSTALS, 3, 5, 6),
                sell(Items.AMETHYST_SHARD, 4, 5, 6),
                sell(Items.POWDER_SNOW_BUCKET, 1, 6, 4)));
    }

    /**
     * The rare pocket: two slots, and he only has some of these.
     *
     * Deliberately nothing powerful. A saddle and a name tag are famous for
     * being impossible to find when you want one and unavoidable when you do
     * not, and that is the whole joke this is built on.
     */
    private static List<VillagerTrades.ItemListing> rare() {
        return new ArrayList<>(List.of(
                sell(Items.NAME_TAG, 1, 12, 2),
                sell(Items.SADDLE, 1, 14, 2),
                sell(Items.NAUTILUS_SHELL, 1, 10, 3),
                sell(Items.ENDER_PEARL, 2, 12, 3),
                sell(Items.SLIME_BALL, 6, 9, 3),
                sell(Items.PHANTOM_MEMBRANE, 2, 12, 2),
                sell(Items.SHULKER_SHELL, 1, 32, 1)));
    }

    @SubscribeEvent
    public static void onWanderer(WandererTradesEvent event) {
        event.getGenericTrades().addAll(generic());
        event.getRareTrades().addAll(rare());
        for (Added extra : ADDED) {
            if (extra.rare()) {
                event.getRareTrades().add(extra.listing());
            } else {
                event.getGenericTrades().add(extra.listing());
            }
        }
    }
}
