package com.elysium.lib.elite;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.affix.ElysiumAffixRules;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every augment the packs declare, and how one is drawn.
 *
 * <p>The drawing is the affix pool's, reused rather than rewritten: weights
 * and a roll are weights and a roll, and there is no reason for a creature's
 * augments to be chosen by a second implementation of the same arithmetic that
 * could disagree with the first about what a weight of zero means.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumMobAugments extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final ElysiumMobAugments INSTANCE = new ElysiumMobAugments();

    private Map<String, ElysiumMobAugment> byId = Map.of();

    private ElysiumMobAugments() {
        super(GSON, "elysium_mob_augments");
    }

    public static ElysiumMobAugments get() {
        return INSTANCE;
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                         ProfilerFiller profiler) {
        Map<String, ElysiumMobAugment> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().toString();
            try {
                ElysiumMobAugment augment =
                        ElysiumMobAugment.parse(id, entry.getValue().getAsJsonObject());
                if (augment.isEmpty()) {
                    ElysiumLib.LOGGER.error("mob augment {} does nothing at all", id);
                    continue;
                }
                loaded.put(id, augment);
            } catch (RuntimeException e) {
                ElysiumLib.LOGGER.error("mob augment {} could not be read: {}", id, e.toString());
            }
        }
        byId = Map.copyOf(loaded);
        ElysiumLib.LOGGER.info("loaded {} mob augment(s)", byId.size());
    }

    public Map<String, ElysiumMobAugment> all() {
        return byId;
    }

    public ElysiumMobAugment byId(String id) {
        return byId.get(id);
    }

    /**
     * Draws augments for a creature, without repeating one.
     *
     * Fewer than asked for is a legal answer: a pack with three augments
     * cannot dress a creature in five, and the honest result is a creature
     * with three rather than one with the same augment twice.
     */
    public List<ElysiumMobAugment> draw(int count, int worldTier, RandomSource random) {
        List<ElysiumAffixRules.Candidate> pool = new ArrayList<>();
        for (ElysiumMobAugment augment : byId.values()) {
            if (augment.allowedAt(worldTier)) {
                pool.add(new ElysiumAffixRules.Candidate(augment.id(), augment.weight()));
            }
        }
        List<ElysiumMobAugment> out = new ArrayList<>();
        for (int drawn = 0; drawn < count && !pool.isEmpty(); drawn++) {
            String chosen = ElysiumAffixRules.pick(pool, random.nextDouble());
            if (chosen == null) {
                break;
            }
            final String picked = chosen;
            pool.removeIf(candidate -> candidate.id().equals(picked));
            ElysiumMobAugment augment = byId.get(picked);
            if (augment != null) {
                out.add(augment);
            }
        }
        return out;
    }
}
