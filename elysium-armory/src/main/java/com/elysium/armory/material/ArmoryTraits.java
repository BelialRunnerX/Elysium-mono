package com.elysium.armory.material;

import com.elysium.armory.ElysiumArmory;
import com.elysium.armory.property.ArmoryTraitRules;
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

/**
 * Every trait the datapacks define, and the one place a piece of gear's traits
 * are worked out.
 *
 * <p>Resolving is in two steps, as Silent Gear had it: the grants that survive
 * their own conditions are averaged into levels, and then a trait whose own
 * file says it only belongs on (say) a tool is dropped from anything else.
 */
@EventBusSubscriber(modid = ElysiumArmory.MODID)
public final class ArmoryTraits extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "elysium_traits";

    private static final ArmoryTraits INSTANCE = new ArmoryTraits();

    private Map<String, ArmoryTraitDefinition> traits = Map.of();

    private ArmoryTraits() {
        super(new com.google.gson.GsonBuilder().create(), DIRECTORY);
    }

    public static ArmoryTraits get() {
        return INSTANCE;
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public Map<String, ArmoryTraitDefinition> all() {
        return traits;
    }

    /** A trait by its bare id ("accelerate") or its full one. */
    public ArmoryTraitDefinition byId(String id) {
        ArmoryTraitDefinition found = traits.get(id);
        return found != null ? found : traits.get(ElysiumArmory.MODID + ":" + id);
    }

    /**
     * The traits a built piece ends up with, and at what level: conditions
     * judged, levels averaged, then the traits that do not belong on this kind
     * of gear dropped.
     */
    public Map<String, Integer> resolve(ArmoryGearBuild build) {
        List<ArmoryTraitRules.Instance> granted = build.traitGrants();
        Map<String, Integer> levels = ArmoryTraitRules.combine(granted, build.traitGrantCount());
        Map<String, Integer> out = new LinkedHashMap<>();
        List<String> lineage = new ArrayList<>();
        com.elysium.armory.property.ArmoryGearType type =
                com.elysium.armory.property.ArmoryGearType.named(build.gearType());
        if (type != null) {
            for (com.elysium.armory.property.ArmoryGearType parent : type.lineage()) {
                lineage.add(parent.name());
            }
        }
        ArmoryTraitRules.Context context = new ArmoryTraitRules.Context(1.0D, 1, build.gearType(), lineage);
        for (Map.Entry<String, Integer> entry : levels.entrySet()) {
            ArmoryTraitDefinition definition = byId(entry.getKey());
            if (definition == null) {
                continue;               // a pack removed the trait; the gear keeps working
            }
            boolean belongs = true;
            for (String condition : definition.conditions()) {
                belongs &= any(condition, context);
            }
            if (belongs) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    /** "any:on:armor,on:curio" is one condition that several spellings satisfy. */
    private static boolean any(String condition, ArmoryTraitRules.Context context) {
        if (!condition.startsWith("any:")) {
            return ArmoryTraitRules.holds(condition, context);
        }
        for (String part : condition.substring(4).split(",")) {
            if (ArmoryTraitRules.holds(part, context)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, ArmoryTraitDefinition> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().toString();
            try {
                loaded.put(id, ArmoryTraitDefinition.parse(id, entry.getValue().getAsJsonObject()));
            } catch (RuntimeException e) {
                ElysiumArmory.LOGGER.error("trait {} was not loaded: {}", id, e.getMessage());
            }
        }
        traits = Map.copyOf(loaded);
        ElysiumArmory.LOGGER.info("loaded {} trait(s)", traits.size());
    }

    /** Only for the harness: parse without a server. */
    public static Map<String, ArmoryTraitDefinition> parseAll(Map<String, JsonObject> files) {
        Map<String, ArmoryTraitDefinition> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> e : files.entrySet()) {
            out.put(e.getKey(), ArmoryTraitDefinition.parse(e.getKey(), e.getValue()));
        }
        return out;
    }
}
