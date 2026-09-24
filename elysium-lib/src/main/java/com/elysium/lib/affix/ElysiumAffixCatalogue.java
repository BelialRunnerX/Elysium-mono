package com.elysium.lib.affix;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.item.ElysiumGearTree;
import com.elysium.lib.item.ElysiumLootCategory;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every affix the packs declare, and the pools they form.
 *
 * <h2>Typed pools</h2>
 *
 * The pool for an item is every affix whose {@code on} selectors all hold for
 * that item's kind. That is what "typed" means here and it is the whole of it:
 * there is no second list of which affixes go with which items, because a
 * second list is a list that can disagree with the first.
 *
 * <p>Pools are worked out per kind and cached, since rolling asks for one
 * every time an item is generated and the answer only changes when the packs
 * reload.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumAffixCatalogue extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final ElysiumAffixCatalogue INSTANCE = new ElysiumAffixCatalogue();

    private Map<String, ElysiumAffixDefinition> byId = Map.of();
    private final Map<String, List<ElysiumAffixRules.Candidate>> pools = new LinkedHashMap<>();

    private ElysiumAffixCatalogue() {
        super(GSON, "elysium_affixes");
    }

    public static ElysiumAffixCatalogue get() {
        return INSTANCE;
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onReload(net.neoforged.neoforge.event.AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                         ProfilerFiller profiler) {
        Map<String, ElysiumAffixDefinition> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().toString();
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                ElysiumAffixDefinition affix = ElysiumAffixDefinition.parse(id, json);
                if (!ElysiumAffixRules.isPlacement(affix.type())) {
                    ElysiumLib.LOGGER.error("affix {} is a '{}', which is neither a prefix "
                            + "nor a suffix, so its name could not be put anywhere", id, affix.type());
                    continue;
                }
                if (!affix.live()) {
                    ElysiumLib.LOGGER.error("affix {} states an effect that is only read while "
                            + "gear is built from parts; an affix is rolled onto a finished "
                            + "item, so it would do nothing", id);
                    continue;
                }
                loaded.put(id, affix);
            } catch (RuntimeException e) {
                ElysiumLib.LOGGER.error("affix {} could not be read: {}", id, e.toString());
            }
        }
        byId = Map.copyOf(loaded);
        pools.clear();
        ElysiumLib.LOGGER.info("loaded {} affix(es)", byId.size());
    }

    public ElysiumAffixDefinition byId(String id) {
        return byId.get(id);
    }

    public Map<String, ElysiumAffixDefinition> all() {
        return byId;
    }

    /**
     * The affixes of one placement that can land on this kind of item.
     *
     * <p>Tier is a gate rather than a weight: an affix above the world's tier
     * is not rare there, it is absent, so reaching a tier is felt as something
     * new appearing.
     *
     * @param kind      a kind from {@link ElysiumGearTree}
     * @param type      {@code prefix} or {@code suffix}
     * @param worldTier the tier of whoever the loot is for
     */
    public List<ElysiumAffixRules.Candidate> pool(String kind, String type, int worldTier) {
        if (kind == null) {
            return List.of();
        }
        return pools.computeIfAbsent(kind + "/" + type + "/" + Math.max(0, worldTier), key -> {
            List<ElysiumAffixRules.Candidate> out = new ArrayList<>();
            for (ElysiumAffixDefinition affix : byId.values()) {
                if (affix.type().equals(type) && affix.allowedAt(worldTier)
                        && ElysiumGearTree.matchesAll(kind, affix.on())) {
                    out.add(new ElysiumAffixRules.Candidate(affix.id(), affix.weight()));
                }
            }
            return List.copyOf(out);
        });
    }

    /** The pool for a stack, resolved through the loot category. */
    public List<ElysiumAffixRules.Candidate> pool(ItemStack stack, String type, int worldTier) {
        return pool(ElysiumLootCategory.of(stack), type, worldTier);
    }
}
