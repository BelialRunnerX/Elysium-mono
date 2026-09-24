package com.elysium.lib.practice;

import com.elysium.lib.ElysiumLib;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What each villager has spent their life doing.
 *
 * <h2>Keyed by what they sell, not by what they are</h2>
 *
 * A baker's practice lives under {@code minecraft:bread}, not under
 * {@code baker}. That is what makes this different from vanilla's levels: two
 * bakers in the same village who have sold different things are differently
 * good at different things, and a villager whose trades are rerolled keeps the
 * skill for the ones they still offer and quietly loses the rest.
 *
 * <h2>Why the discount is applied when the screen opens</h2>
 *
 * Because that is the only moment the offers exist and the price is still
 * changeable. Adjusting on the trade itself would be too late — the player has
 * already paid — and rewriting the offer list when it is built would mean
 * recomputing it every time the villager restocks, whether or not anybody is
 * looking.
 */
public final class ElysiumPracticeLog {

    private ElysiumPracticeLog() {
    }

    private static final Codec<Map<String, Integer>> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    public static final Supplier<AttachmentType<Map<String, Integer>>> TRADES =
            ATTACHMENTS.register("practice", () ->
                    AttachmentType.builder(
                                    (Supplier<Map<String, Integer>>) LinkedHashMap::new)
                            .serialize(CODEC)
                            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    /** The registry id of what an offer produces, as a log key. */
    public static String keyFor(ItemStack result) {
        if (result == null || result.isEmpty()) {
            return "";
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
        return id == null ? "" : id.toString();
    }

    /** How many times this villager has made this trade. */
    public static int made(LivingEntity seller, String key) {
        if (seller == null || key.isEmpty()) {
            return 0;
        }
        Integer held = seller.getData(TRADES.get()).get(key);
        return held == null ? 0 : held;
    }

    /** How good they are at it. */
    public static ElysiumPractice bandFor(LivingEntity seller, String key) {
        return ElysiumPractice.forTrades(made(seller, key));
    }

    /** Record one more of the same trade. */
    public static void record(LivingEntity seller, ItemStack result) {
        if (seller == null || seller.level().isClientSide()) {
            return;
        }
        String key = keyFor(result);
        if (key.isEmpty()) {
            return;
        }
        Map<String, Integer> log = new LinkedHashMap<>(seller.getData(TRADES.get()));
        int was = log.getOrDefault(key, 0);
        if (was >= ElysiumPractice.CEILING) {
            return;
        }
        log.put(key, Math.min(ElysiumPractice.CEILING, was + 1));
        seller.setData(TRADES.get(), log);
    }

    /**
     * Price every offer this villager is showing, by how often they have made
     * it.
     *
     * The diff is <em>set</em> rather than added, because vanilla also uses the
     * special price for Hero of the Village and for a villager's opinion of the
     * player, and those are recomputed on every open. Adding would compound the
     * discount a little further every time the screen was closed and reopened,
     * which is the sort of bug that only shows up after somebody has stood at a
     * trade screen for a while — and then shows up as free goods.
     */
    public static void price(LivingEntity seller, MerchantOffers offers) {
        if (seller == null || offers == null || seller.level().isClientSide()) {
            return;
        }
        for (MerchantOffer offer : offers) {
            ElysiumPractice band = bandFor(seller, keyFor(offer.getResult()));
            offer.setSpecialPriceDiff(
                    band.priceDiffFor(offer.getBaseCostA().getCount()));
        }
    }

    /** What this villager is practised at, best first. For the readout. */
    public static List<Map.Entry<String, Integer>> best(LivingEntity seller,
                                                        int howMany) {
        if (seller == null) {
            return List.of();
        }
        List<Map.Entry<String, Integer>> all =
                new ArrayList<>(seller.getData(TRADES.get()).entrySet());
        all.sort(Comparator.comparingInt(
                (Map.Entry<String, Integer> one) -> one.getValue()).reversed());
        return all.subList(0, Math.min(howMany, all.size()));
    }
}
