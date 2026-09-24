package com.elysium.armory.material;

import com.elysium.armory.ElysiumArmory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every material the datapacks define, loaded from
 * {@code data/<namespace>/elysium_materials/**.json} and resolved.
 *
 * <p>Loading is in two passes, because a child may be listed before its parent
 * and a parent may itself have one: the files are parsed, then each is resolved
 * down its chain of parents ({@link ArmoryMaterial#inherit}). A chain that
 * loops, or that names a parent nobody defined, drops that one material with a
 * line in the log — one broken file must not cost a world its other 131.
 *
 * <p>Data, not a registry: a material is not an item and nothing is registered
 * for it, so a pack may add, change or remove one at any reload.
 */
@EventBusSubscriber(modid = ElysiumArmory.MODID)
public final class ArmoryMaterials extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "elysium_materials";

    private static final ArmoryMaterials INSTANCE = new ArmoryMaterials();

    private Map<String, ArmoryMaterial> materials = Map.of();

    private ArmoryMaterials() {
        super(new com.google.gson.GsonBuilder().create(), DIRECTORY);
    }

    public static ArmoryMaterials get() {
        return INSTANCE;
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    /** Every material, by id ("elysiumarmory:iron"). */
    public Map<String, ArmoryMaterial> all() {
        return materials;
    }

    public ArmoryMaterial byId(String id) {
        return materials.get(id);
    }

    /**
     * The material an item <em>is</em>, or null for an item that is not one.
     *
     * <p>A material names its ingredient as an item id or a {@code #tag}, and
     * tags are only bound once the packs have finished loading — so the index
     * is built on the first question asked after a reload rather than during
     * one. Two materials claiming the same item is a pack's business, not a
     * crash: the first one wins and the clash is logged once.
     */
    public ArmoryMaterial byItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (index == null) {
            index = buildIndex();
        }
        return index.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private Map<String, ArmoryMaterial> index;

    private Map<String, ArmoryMaterial> buildIndex() {
        Map<String, ArmoryMaterial> out = new LinkedHashMap<>();
        for (ArmoryMaterial material : materials.values()) {
            String ingredient = material.ingredient();
            if (ingredient == null) {
                continue;
            }
            for (String item : itemsOf(ingredient)) {
                ArmoryMaterial first = out.putIfAbsent(item, material);
                if (first != null && first != material) {
                    ElysiumArmory.LOGGER.warn("{} is both {} and {}; the first stands",
                            item, first.id(), material.id());
                }
            }
        }
        return out;
    }

    private static List<String> itemsOf(String ingredient) {
        if (!ingredient.startsWith("#")) {
            return List.of(ingredient);
        }
        net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag =
                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                        net.minecraft.resources.ResourceLocation.parse(ingredient.substring(1)));
        List<String> out = new ArrayList<>();
        net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(tag).ifPresent(holders ->
                holders.forEach(holder -> holder.unwrapKey().ifPresent(key -> out.add(key.location().toString()))));
        return out;
    }

    /** Every material that can go into a part of this type. */
    public List<ArmoryMaterial> forPart(String partType) {
        List<ArmoryMaterial> out = new ArrayList<>();
        for (ArmoryMaterial material : materials.values()) {
            if (material.supports(partType)) {
                out.add(material);
            }
        }
        return out;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, JsonObject> json = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            json.put(entry.getKey().toString(), entry.getValue().getAsJsonObject());
        }
        index = null;               // tags may have changed with the packs
        materials = Map.copyOf(ArmoryMaterialTree.resolve(json,
                (id, problem) -> ElysiumArmory.LOGGER.error("material {} was not loaded: {}", id, problem)));
        ElysiumArmory.LOGGER.info("loaded {} material(s)", materials.size());
    }

    /** Every material id a pack defined, for a command or a screen. */
    public Collection<String> ids() {
        return materials.keySet();
    }
}
