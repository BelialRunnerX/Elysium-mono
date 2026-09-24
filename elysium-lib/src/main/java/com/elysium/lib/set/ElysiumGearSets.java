package com.elysium.lib.set;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.effect.ElysiumItemEffects;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every gear set the packs declare, and what a wearer has on.
 *
 * <h2>An attachment like any other</h2>
 *
 * A set registers with the effect engine as one more thing that can be
 * attached to an item, so a set bonus is read by the handlers that already
 * read traits and affixes. What makes it different from the others is only
 * where it looks: at the whole of what is worn rather than at the one stack in
 * hand.
 *
 * <p>That is why the source is asked about a stack and answers about its
 * wearer. The engine hands it the piece; the piece names its set; the set's
 * bonus depends on how many of its siblings are on the same body. A piece in a
 * chest grants nothing, which is right — a set bonus is for wearing.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumGearSets extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final ElysiumGearSets INSTANCE = new ElysiumGearSets();

    private Map<String, ElysiumGearSet> byId = Map.of();
    private Map<String, ElysiumGearSet> byItem = Map.of();

    private ElysiumGearSets() {
        super(GSON, "elysium_gear_sets");
    }

    public static ElysiumGearSets get() {
        return INSTANCE;
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    /** Registers the sets as an attachment. Called once, at mod construction. */
    public static void register() {
        ElysiumItemEffects.register("elysiumlib:gear_sets", new ElysiumItemEffects.Source() {

            @Override
            public List<ElysiumItemEffects.Active> effects(ItemStack stack, String kind) {
                return INSTANCE.effectsOn(stack, kind);
            }

            @Override
            public int level(ItemStack stack, String name) {
                ElysiumGearSet set = INSTANCE.setOf(stack);
                return set != null && set.id().equals(name) ? 1 : 0;
            }
        });
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                         ProfilerFiller profiler) {
        Map<String, ElysiumGearSet> loaded = new LinkedHashMap<>();
        Map<String, ElysiumGearSet> items = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().toString();
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                ElysiumGearSet set = ElysiumGearSet.parse(id, json);
                if (set.items().size() < 2) {
                    ElysiumLib.LOGGER.error("gear set {} names fewer than two items, so there is "
                            + "nothing to complete", id);
                    continue;
                }
                loaded.put(id, set);
                for (String item : set.items()) {
                    items.put(item, set);
                }
            } catch (RuntimeException e) {
                ElysiumLib.LOGGER.error("gear set {} could not be read: {}", id, e.toString());
            }
        }
        byId = Map.copyOf(loaded);
        byItem = Map.copyOf(items);
        ElysiumLib.LOGGER.info("loaded {} gear set(s)", byId.size());
    }

    public Map<String, ElysiumGearSet> all() {
        return byId;
    }

    public ElysiumGearSet byId(String id) {
        return byId.get(id);
    }

    /** The set this stack belongs to, or null. */
    public ElysiumGearSet setOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return byItem.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /** How many pieces of a set an entity is wearing. */
    public int wornCount(LivingEntity entity, ElysiumGearSet set) {
        int count = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty() && set.items().contains(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                count++;
            }
        }
        return count;
    }

    /**
     * The effects this stack grants because of the set it is part of.
     *
     * The stack has to be worn by someone for the count to mean anything, and
     * the engine does not say who is holding it — so the effects are attached
     * to the piece and the count is taken from whoever has it on, found
     * through the stack's own entity representation where one exists. A piece
     * in a chest grants nothing.
     */
    private List<ElysiumItemEffects.Active> effectsOn(ItemStack stack, String kind) {
        ElysiumGearSet set = setOf(stack);
        if (set == null) {
            return List.of();
        }
        LivingEntity wearer = ElysiumGearSetWearers.wearerOf(stack);
        if (wearer == null) {
            return List.of();
        }
        int worn = wornCount(wearer, set);
        List<ElysiumItemEffects.Active> out = new ArrayList<>();
        for (JsonObject effect : set.effects(worn, kind)) {
            // Level 1, not the worn count. The count has already chosen which
            // bonuses apply; handing it on as a level would multiply every
            // number in them by how many pieces are on, so a four-piece set
            // would grant four times what its file says.
            out.add(new ElysiumItemEffects.Active(set.id(), 1, effect));
        }
        return out;
    }
}
