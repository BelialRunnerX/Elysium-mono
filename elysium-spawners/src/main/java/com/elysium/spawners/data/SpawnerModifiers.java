package com.elysium.spawners.data;

import com.elysium.spawners.ElysiumSpawners;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a held item does to a spawner (Apothic Spawners' item-in-hand recipes,
 * and Apotheosis' runes: one mechanism, and the runes are its content).
 *
 * <pre>
 * { "item": "elysiumspawners:rune_of_haste", "stat": "delay", "max_steps": 12 }
 * { "item": "elysiumspawners:rune_of_indifference", "flag": "ignores_players" }
 * </pre>
 *
 * <h2>Data, because this is the part a pack argues with</h2>
 *
 * Which item tunes which axis, and how far it may go, is exactly the question a
 * modpack wants a different answer to — and a pack that adds its own rune has
 * no way to say so if the table is in Java. There is no hard-coded list
 * anywhere in this module; the runes it ships are twelve files.
 */
@EventBusSubscriber(modid = ElysiumSpawners.MODID)
public final class SpawnerModifiers extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final SpawnerModifiers INSTANCE = new SpawnerModifiers();

    /**
     * One item's effect.
     *
     * Exactly one of {@code stat} and {@code flag} is set: an item that did
     * both would be two upgrades at one price, and an item that did neither
     * would be a rune that does nothing.
     */
    public record Modifier(Item item, @Nullable String stat, @Nullable String flag,
                           int maxSteps) {
    }

    private Map<Item, Modifier> byItem = Map.of();

    private SpawnerModifiers() {
        super(GSON, "elysium_spawner_modifiers");
    }

    public static SpawnerModifiers get() {
        return INSTANCE;
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                         ProfilerFiller profiler) {
        Map<Item, Modifier> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().toString();
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                ResourceLocation item = ResourceLocation.parse(json.get("item").getAsString());
                Item found = BuiltInRegistries.ITEM.getOptional(item).orElse(null);
                if (found == null) {
                    // A pack naming an item from a mod that is not installed is
                    // not an error: it is a pack that works in more than one
                    // configuration.
                    continue;
                }
                String stat = json.has("stat") ? json.get("stat").getAsString() : null;
                String flag = json.has("flag") ? json.get("flag").getAsString() : null;
                if ((stat == null) == (flag == null)) {
                    ElysiumSpawners.LOGGER.error("spawner modifier {} names {} of a stat and "
                            + "a flag; it has to name exactly one", id,
                            stat == null ? "neither" : "both");
                    continue;
                }
                if (stat != null && !SpawnerTuning.STATS.contains(stat)) {
                    ElysiumSpawners.LOGGER.error("spawner modifier {} tunes {}, which is not "
                            + "one of the axes a spawner has", id, stat);
                    continue;
                }
                if (flag != null && !SpawnerTuning.FLAGS.contains(flag)) {
                    ElysiumSpawners.LOGGER.error("spawner modifier {} sets {}, which is not "
                            + "one of the behaviours a spawner has", id, flag);
                    continue;
                }
                int maxSteps = json.has("max_steps") ? json.get("max_steps").getAsInt() : 0;
                loaded.put(found, new Modifier(found, stat, flag, maxSteps));
            } catch (RuntimeException e) {
                ElysiumSpawners.LOGGER.error("spawner modifier {} could not be read: {}",
                        id, e.toString());
            }
        }
        byItem = Map.copyOf(loaded);
        ElysiumSpawners.LOGGER.info("loaded {} spawner modifier(s)", byItem.size());
    }

    /** What this stack does to a spawner, or null. */
    @Nullable
    public Modifier forItem(ItemStack stack) {
        return stack.isEmpty() ? null : byItem.get(stack.getItem());
    }

    public Map<Item, Modifier> all() {
        return byItem;
    }
}
