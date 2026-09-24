package com.elysium.enchanting.data;

import com.elysium.enchanting.ElysiumEnchanting;
import com.elysium.enchanting.rules.EnchantingRules;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What each block around a table is worth (Apothic Enchanting's AE-1 and
 * AE-3).
 *
 * <pre>
 * { "block": "minecraft:bookshelf",
 *   "eterna": 2.0, "quanta": 0.0, "arcana": 0.0, "rectification": 0.0,
 *   "max_eterna": 15.0 }
 * </pre>
 *
 * <h2>Data, because the answer is a pack's to give</h2>
 *
 * Which blocks are worth building a room out of is exactly the kind of thing a
 * modpack wants to decide — and a pack that adds its own blocks has no way to
 * say so if the table's table is in Java. A shelf is a file.
 *
 * <p>The {@code max_eterna} field is separate from {@code eterna} on purpose:
 * a block can raise what a table may hold without contributing any of it,
 * which is what makes a room a structure rather than a pile.
 */
@EventBusSubscriber(modid = ElysiumEnchanting.MODID)
public final class EnchantingStatBlocks extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final EnchantingStatBlocks INSTANCE = new EnchantingStatBlocks();

    /** One block's contribution, and the cap it raises. */
    public record Shelf(EnchantingRules.Stats stats, float maxEterna) {
    }

    private Map<Block, Shelf> byBlock = Map.of();

    private EnchantingStatBlocks() {
        super(GSON, "elysium_enchanting_stats");
    }

    public static EnchantingStatBlocks get() {
        return INSTANCE;
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                         ProfilerFiller profiler) {
        Map<Block, Shelf> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().toString();
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                ResourceLocation block = ResourceLocation.parse(json.get("block").getAsString());
                Block found = BuiltInRegistries.BLOCK.getOptional(block).orElse(null);
                if (found == null) {
                    // A pack naming a block from a mod that is not installed is
                    // not an error: it is a pack that works in more than one
                    // configuration.
                    continue;
                }
                EnchantingRules.Stats stats = new EnchantingRules.Stats(
                        number(json, "eterna"), number(json, "quanta"),
                        number(json, "arcana"), number(json, "rectification"));
                float maxEterna = number(json, "max_eterna");
                if (stats.isEmpty() && stats.quanta() == 0.0F && stats.arcana() == 0.0F
                        && stats.rectification() == 0.0F && maxEterna == 0.0F) {
                    ElysiumEnchanting.LOGGER.error("enchanting stat block {} is worth nothing "
                            + "at all, so putting one next to a table would do nothing", id);
                    continue;
                }
                loaded.put(found, new Shelf(stats, maxEterna));
            } catch (RuntimeException e) {
                ElysiumEnchanting.LOGGER.error("enchanting stat block {} could not be read: {}",
                        id, e.toString());
            }
        }
        byBlock = Map.copyOf(loaded);
        ElysiumEnchanting.LOGGER.info("loaded {} enchanting stat block(s)", byBlock.size());
    }

    private static float number(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsFloat() : 0.0F;
    }

    /** What this block is worth to a table, or null. */
    public Shelf shelfFor(BlockState state) {
        return byBlock.get(state.getBlock());
    }

    public Map<Block, Shelf> all() {
        return byBlock;
    }
}
