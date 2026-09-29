package com.elysium.agronomy.data;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.rules.KitchenRules;
import com.elysium.agronomy.rules.PotRules;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * What the kitchen and the pots read off disk.
 *
 * <h2>Why data at all</h2>
 *
 * The generator writes every meal, cut, larder item, soil and sprout out as a
 * JSON file in a folder of its own, and for a long time that was where it
 * stopped: five folders and eighty-eight files that nothing opened. A pack
 * could add a meal by dropping a file in, and the pot would go on not making
 * it, because the pot did not know the folder existed.
 *
 * <p>This is the one place that opens them. Every file becomes a record the
 * rules already define — a {@link KitchenRules.Order}, a
 * {@link KitchenRules.Cut}, a {@link PotRules.Soil}, a {@link PotRules.Sprout}
 * — so the matching a station does and the growing a pot does are the same
 * arithmetic the harness ran, handed the same shapes. The blocks ask here;
 * nothing here decides.
 *
 * <h2>What is read</h2>
 *
 * Five folders, every namespace, every {@code .json}: {@code cooking} and
 * {@code larder} into orders, {@code cutting} into cuts, {@code botany_soil}
 * into soils keyed by the block that is the soil, {@code botany_sprout} into
 * sprouts keyed by the item that plants them. A file that will not parse is
 * logged and skipped rather than taking the rest down with it: a pack with
 * one bad meal is a pack with twenty-eight meals, not a pack with none.
 */
@EventBusSubscriber(modid = ElysiumAgronomy.MODID)
public final class AgronomyData {

    private AgronomyData() {
    }

    /** The five folders, by the name the generator gives them. */
    public static final String COOKING = "cooking";
    public static final String LARDER = "larder";
    public static final String CUTTING = "cutting";
    public static final String SOILS = "botany_soil";
    public static final String SPROUTS = "botany_sprout";

    /** One soil as the game sees it: the rules' soil, and the block that is it. */
    public record Bed(PotRules.Soil soil, String block) {
    }

    /**
     * One sprout as the game sees it: the rules' sprout, the item that plants
     * it and the item it drops, both by their full names.
     */
    public record Sown(PotRules.Sprout sprout, String seed, String drop) {
    }

    private static List<KitchenRules.Order> menu = List.of();
    private static List<KitchenRules.Cut> cuts = List.of();
    private static Map<String, Bed> bedsByBlock = Map.of();
    private static Map<String, Sown> sownBySeed = Map.of();

    /** Every order any station knows. */
    public static List<KitchenRules.Order> menu() {
        return menu;
    }

    /** Every cut a knife makes. */
    public static List<KitchenRules.Cut> cuts() {
        return cuts;
    }

    /** The order these contents are exactly the ingredients of, or null. */
    @Nullable
    public static KitchenRules.Order orderFor(String station, List<String> have) {
        return KitchenRules.match(station, have, menu);
    }

    /** Whether these contents are the start of something this station makes. */
    public static boolean couldBecome(String station, List<String> have) {
        return KitchenRules.couldBecome(station, have, menu);
    }

    /**
     * What a knife makes of that item, or null. The knife is named by what a
     * cutting recipe calls it: a kitchen knife and the fillet knife cut
     * different things.
     */
    @Nullable
    public static KitchenRules.Cut cutFor(String item, String tool) {
        return KitchenRules.cutFor(item, tool, cuts);
    }

    /** The soil that block is, or null when it is not one. */
    @Nullable
    public static Bed bedFor(String block) {
        return bedsByBlock.get(block);
    }

    /** The sprout that item plants, or null when it plants nothing. */
    @Nullable
    public static Sown sownFor(String item) {
        return sownBySeed.get(item);
    }

    /** How many of each were read, for a log line or a validator. */
    public static String summary() {
        return menu.size() + " order(s), " + cuts.size() + " cut(s), "
                + bedsByBlock.size() + " soil(s), " + sownBySeed.size() + " sprout(s)";
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<ResourceManager>() {
            @Override
            protected ResourceManager prepare(ResourceManager manager,
                                              ProfilerFiller profiler) {
                return manager;
            }

            @Override
            protected void apply(ResourceManager manager, ResourceManager again,
                                 ProfilerFiller profiler) {
                read(manager);
            }
        });
    }

    /** Open all five folders and keep what parses. */
    static void read(ResourceManager manager) {
        List<KitchenRules.Order> orders = new ArrayList<>();
        orders.addAll(each(manager, COOKING, AgronomyData::meal));
        orders.addAll(each(manager, LARDER, AgronomyData::larder));
        menu = List.copyOf(orders);
        cuts = List.copyOf(each(manager, CUTTING, AgronomyData::cut));
        Map<String, Bed> beds = new LinkedHashMap<>();
        for (Bed bed : each(manager, SOILS, AgronomyData::soil)) {
            beds.put(bed.block(), bed);
        }
        bedsByBlock = Map.copyOf(beds);
        Map<String, Sown> sown = new LinkedHashMap<>();
        for (Sown one : each(manager, SPROUTS, AgronomyData::sprout)) {
            sown.put(one.seed(), one);
        }
        sownBySeed = Map.copyOf(sown);
        ElysiumAgronomy.LOGGER.info("the kitchen and the pots know {}", summary());
    }

    /**
     * Every file in a folder, parsed, in a stable order.
     *
     * <p>The parser is handed the file's own name — {@code apple_pie} for
     * {@code cooking/apple_pie.json} — because a meal's data file names its
     * result and a soil's names its block, and the file name is what a
     * player reading the folder would call it.
     */
    private static <T> List<T> each(ResourceManager manager, String folder,
                                    BiFunction<String, JsonObject, T> parse) {
        List<T> made = new ArrayList<>();
        Map<ResourceLocation, Resource> found = new java.util.TreeMap<>(
                manager.listResources(folder, path -> path.getPath().endsWith(".json")));
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            String name = path.substring(folder.length() + 1, path.length() - 5);
            try (var reader = entry.getValue().openAsReader()) {
                JsonObject body = JsonParser.parseReader(reader).getAsJsonObject();
                T one = parse.apply(name, body);
                if (one != null) {
                    made.add(one);
                }
            } catch (Exception broken) {
                ElysiumAgronomy.LOGGER.error("{}/{} is unreadable and was skipped: {}",
                        folder, name, broken.toString());
            }
        }
        return made;
    }

    private static List<String> strings(JsonObject body, String key) {
        List<String> out = new ArrayList<>();
        if (body.has(key)) {
            for (JsonElement one : body.getAsJsonArray(key)) {
                out.add(one.getAsString());
            }
        }
        return out;
    }

    /** {@code cooking/*.json}: station, ingredients, ticks, servings, result. */
    private static KitchenRules.Order meal(String name, JsonObject body) {
        return new KitchenRules.Order(body.get("result").getAsString(),
                body.get("station").getAsString(), strings(body, "ingredients"),
                body.get("ticks").getAsInt(), body.get("servings").getAsInt());
    }

    /** {@code larder/*.json}: station, of, result, count, ticks. */
    private static KitchenRules.Order larder(String name, JsonObject body) {
        return new KitchenRules.Order(body.get("result").getAsString(),
                body.get("station").getAsString(), strings(body, "of"),
                body.has("ticks") ? body.get("ticks").getAsInt() : KitchenRules.PREP_TICKS,
                body.has("count") ? body.get("count").getAsInt() : 1);
    }

    /** {@code cutting/*.json}: input, tool, yields. */
    private static KitchenRules.Cut cut(String name, JsonObject body) {
        Map<String, Integer> yields = new LinkedHashMap<>();
        for (JsonElement one : body.getAsJsonArray("yields")) {
            JsonObject made = one.getAsJsonObject();
            yields.merge(made.get("item").getAsString(), made.get("count").getAsInt(),
                    Integer::sum);
        }
        return new KitchenRules.Cut(body.get("input").getAsString(),
                body.get("tool").getAsString(), yields);
    }

    /** {@code botany_soil/*.json}: block, tier, speed, grows. */
    private static Bed soil(String name, JsonObject body) {
        return new Bed(new PotRules.Soil(name, body.get("tier").getAsInt(),
                body.get("speed").getAsFloat(), strings(body, "grows")),
                body.get("block").getAsString());
    }

    /** {@code botany_sprout/*.json}: needs, ticks, drop, amount, seed. */
    private static Sown sprout(String name, JsonObject body) {
        String drop = body.get("drop").getAsString();
        String seed = body.has("seed") ? body.get("seed").getAsString() : drop;
        return new Sown(new PotRules.Sprout(name, body.get("needs").getAsString(),
                body.get("ticks").getAsInt(), drop, body.get("amount").getAsInt()),
                seed, drop);
    }
}
