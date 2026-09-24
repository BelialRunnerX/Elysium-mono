package com.elysium.colonies.market;

import com.elysium.colonies.ElysiumColonies;
import com.elysium.colonies.rules.MarketRules;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * What a thing is worth before anything about where or when.
 *
 * <h2>Two tables, and why</h2>
 *
 * Values are given per <em>material family</em> first and per item second. A
 * family covers every mod's version of a material at once, which is the whole
 * point of having unified them; the per-item table is for the things that have
 * no family — a saddle, a chest, somebody's widget — and for overriding a
 * family where one particular item really is different.
 *
 * <p>Anything in neither table is worth {@link MarketRules#BASE_VALUE}. A
 * market that refused to price something it had not heard of would be a market
 * that stops working the moment a pack adds a mod.
 */
@EventBusSubscriber(modid = ElysiumColonies.MODID)
public final class MarketValues {

    private MarketValues() {
    }

    /** Where the table lives in a datapack. */
    public static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(ElysiumColonies.MODID,
                    "charter_needs/market_values.json");

    private static Map<String, Integer> families = Map.of();
    private static Map<String, Integer> items = Map.of();

    /** What this stack is worth before its world and its day are counted. */
    public static int of(ItemStack stack, String family) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            Integer named = items.get(id.toString());
            if (named != null) {
                return named;
            }
        }
        if (family != null) {
            Integer byFamily = families.get(family);
            if (byFamily != null) {
                return byFamily;
            }
        }
        return MarketRules.BASE_VALUE;
    }

    /** How many values are known, for a report or a validator. */
    public static int size() {
        return families.size() + items.size();
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<ResourceManager>() {
            @Override
            protected ResourceManager prepare(ResourceManager manager,
                                              net.minecraft.util.profiling.ProfilerFiller profiler) {
                return manager;
            }

            @Override
            protected void apply(ResourceManager manager, ResourceManager again,
                                 net.minecraft.util.profiling.ProfilerFiller profiler) {
                read(manager);
            }
        });
    }

    private static void read(ResourceManager manager) {
        Map<String, Integer> byFamily = new HashMap<>();
        Map<String, Integer> byItem = new HashMap<>();
        manager.getResource(FILE).ifPresent(resource -> {
            try (var reader = resource.openAsReader()) {
                JsonObject body = com.google.gson.JsonParser.parseReader(reader)
                        .getAsJsonObject();
                collect(body, "families", byFamily);
                collect(body, "items", byItem);
            } catch (Exception broken) {
                ElysiumColonies.LOGGER.error("market values are unreadable: {}",
                        broken.toString());
            }
        });
        families = Map.copyOf(byFamily);
        items = Map.copyOf(byItem);
        Market.invalidate();
        ElysiumColonies.LOGGER.info("market knows {} famil(ies) and {} item(s) by name",
                families.size(), items.size());
    }

    private static void collect(JsonObject body, String key, Map<String, Integer> into) {
        if (!body.has(key)) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : body.getAsJsonObject(key).entrySet()) {
            into.put(entry.getKey(), Math.max(1, entry.getValue().getAsInt()));
        }
    }
}
