package com.elysium.colonies.charter;

import com.elysium.colonies.ElysiumColonies;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Every charter the pack has, reloaded whenever the pack is.
 *
 * <h2>Two files, not one</h2>
 *
 * The charters say what each world has. A second file says what a settlement
 * actually consumes — wood, stone, iron, food — and the two are kept apart on
 * purpose: a world's charter is a fact about the world, and the list of what a
 * town needs is a fact about towns. A pack that adds a world writes one file;
 * a pack that changes what settlements are made of writes the other.
 *
 * <p>The score a world gets is the overlap. A world that produces forty exotic
 * materials a town never touches is not a rich world to settle, and this is
 * the arithmetic that says so.
 */
@EventBusSubscriber(modid = ElysiumColonies.MODID)
public final class Charters {

    private Charters() {
    }

    /** Where the per-world files live in a datapack. */
    public static final String DIRECTORY = "charter";

    /** The one file that says what a settlement consumes. */
    public static final ResourceLocation NEEDS =
            ResourceLocation.fromNamespaceAndPath(ElysiumColonies.MODID,
                    "settlement_needs");

    /** Where the plan lives in a datapack. */
    public static final ResourceLocation PLAN =
            ResourceLocation.fromNamespaceAndPath(ElysiumColonies.MODID,
                    "settlement_plan");

    private static Map<ResourceLocation, Charter> charters = Map.of();
    private static Set<String> needs = Set.of();
    private static SettlementPlan plan = SettlementPlan.EMPTY;

    /** The charter for a world, or the neutral one for a world nobody wrote. */
    public static Charter of(ResourceLocation dimension) {
        Charter written = charters.get(dimension);
        return written != null ? written : Charter.unwritten(dimension);
    }

    /** What a settlement consumes, as material family names. */
    public static Set<String> needs() {
        return needs;
    }

    /** What a settlement builds, in the order it wants it. */
    public static SettlementPlan plan() {
        return plan;
    }

    /** How many charters were loaded, for a report or a validator. */
    public static int count() {
        return charters.size();
    }

    /** Every charter, for a command that lists them. */
    public static Map<ResourceLocation, Charter> all() {
        return Map.copyOf(charters);
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    /**
     * The reload listener.
     *
     * Reads {@code data/<any>/charter/*.json} — any namespace, so a pack adds
     * a world's charter under its own name rather than under this module's —
     * plus the single needs file.
     */
    private static final class Loader extends SimpleJsonResourceReloadListener {

        private Loader() {
            super(new com.google.gson.GsonBuilder().create(), DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files,
                             ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, Charter> found = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
                Charter.CODEC.parse(JsonOps.INSTANCE, file.getValue())
                        .resultOrPartial(problem -> ElysiumColonies.LOGGER.error(
                                "charter {} is malformed: {}", file.getKey(), problem))
                        .ifPresent(charter -> found.put(charter.dimension(), charter));
            }
            charters = Map.copyOf(found);
            needs = readNeeds(manager);
            plan = readPlan(manager);
            ElysiumColonies.LOGGER.info("{} charter(s) loaded; a settlement consumes {} "
                            + "material famil(ies) and builds {} thing(s)",
                    charters.size(), needs.size(), plan.wants().size());
        }

        private static SettlementPlan readPlan(ResourceManager manager) {
            ResourceLocation path = ResourceLocation.fromNamespaceAndPath(
                    PLAN.getNamespace(), "charter_needs/" + PLAN.getPath() + ".json");
            SettlementPlan[] held = {SettlementPlan.EMPTY};
            manager.getResource(path).ifPresent(resource -> {
                try (var reader = resource.openAsReader()) {
                    JsonElement body = com.google.gson.JsonParser.parseReader(reader);
                    SettlementPlan.CODEC.parse(JsonOps.INSTANCE, body)
                            .resultOrPartial(problem -> ElysiumColonies.LOGGER.error(
                                    "the settlement plan is malformed: {}", problem))
                            .ifPresent(read -> held[0] = read);
                } catch (Exception broken) {
                    ElysiumColonies.LOGGER.error("the settlement plan is unreadable: {}",
                            broken.toString());
                }
            });
            return held[0];
        }

        private static Set<String> readNeeds(ResourceManager manager) {
            ResourceLocation path = ResourceLocation.fromNamespaceAndPath(
                    NEEDS.getNamespace(), "charter_needs/" + NEEDS.getPath() + ".json");
            Set<String> out = new LinkedHashSet<>();
            manager.getResource(path).ifPresent(resource -> {
                try (var reader = resource.openAsReader()) {
                    JsonElement body = com.google.gson.JsonParser.parseReader(reader);
                    for (JsonElement entry : body.getAsJsonObject()
                            .getAsJsonArray("needs")) {
                        out.add(entry.getAsString());
                    }
                } catch (Exception broken) {
                    ElysiumColonies.LOGGER.error("settlement needs are unreadable: {}",
                            broken.toString());
                }
            });
            return Set.copyOf(out);
        }
    }
}
