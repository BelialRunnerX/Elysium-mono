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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every part definition the datapacks define, from {@code data/<namespace>/elysium_parts/*.json}. */
@EventBusSubscriber(modid = ElysiumArmory.MODID)
public final class ArmoryParts extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "elysium_parts";

    private static final ArmoryParts INSTANCE = new ArmoryParts();

    private Map<String, ArmoryPart> parts = Map.of();

    private ArmoryParts() {
        super(new com.google.gson.GsonBuilder().create(), DIRECTORY);
    }

    public static ArmoryParts get() {
        return INSTANCE;
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public Map<String, ArmoryPart> all() {
        return parts;
    }

    public ArmoryPart byId(String id) {
        return parts.get(id);
    }

    /** The parts a piece of this gear type is built from, main first. */
    public List<ArmoryPart> forGear(String gearType) {
        List<ArmoryPart> out = new ArrayList<>();
        for (ArmoryPart part : parts.values()) {
            if (part.gearType().equals(gearType) && part.fits(gearType)) {
                out.add(part);
            }
        }
        out.sort((a, b) -> Boolean.compare(!a.partType().equals("main"), !b.partType().equals("main")));
        return out;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, ArmoryPart> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().toString();
            try {
                loaded.put(id, ArmoryPart.parse(id, entry.getValue().getAsJsonObject()));
            } catch (RuntimeException e) {
                ElysiumArmory.LOGGER.error("part {} was not loaded: {}", id, e.getMessage());
            }
        }
        parts = Map.copyOf(loaded);
        ElysiumArmory.LOGGER.info("loaded {} part definition(s)", parts.size());
    }

    /** Only for the harness: parse without a server. */
    public static Map<String, ArmoryPart> parseAll(Map<String, JsonObject> files) {
        Map<String, ArmoryPart> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> e : files.entrySet()) {
            out.put(e.getKey(), ArmoryPart.parse(e.getKey(), e.getValue()));
        }
        return out;
    }
}
