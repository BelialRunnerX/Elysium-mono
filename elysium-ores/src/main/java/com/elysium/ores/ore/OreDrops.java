package com.elysium.ores.ore;

import com.elysium.ores.rules.UnifyRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a block of ore is actually made of, asked at runtime.
 *
 * <h2>Why this is not in the loot table</h2>
 *
 * A loot table has to name an item. The item this module's lead ore should
 * drop is whichever lead the pack settled on, which depends on which of the
 * six lead-supplying mods are installed — a question no file shipped in a jar
 * can answer. Naming one would make the jar depend on that mod; naming
 * Elysium's own would mean a seventh lead, which is the problem this module
 * exists to solve.
 *
 * <p>So the loot table drops the ore block itself, which is always correct and
 * is what silk touch wants anyway, and this turns that into the material.
 *
 * <h2>The search order</h2>
 *
 * {@link UnifyRules#DROP_ORDER}: raw material, then gem, then the base game's
 * own item, then dust, then ingot. A player breaking an ore expects to hold
 * something that still has to be smelted, so the raw form wins wherever there
 * is one; the further down the list, the more processed the material already
 * is. The base game's coal and redstone come before any dust or ingot a mod
 * tags, because a dust is what a crusher made of them: with the base game
 * last, the Nether's coal ore dropped the foundry's coal dust.
 *
 * <p>Within a kind the choice is {@link UnifyRules}', so the ore drops exactly
 * the item everything else in the pack was unified onto. That is the whole
 * point of the two halves being one module: an ore that dropped a different
 * lead from the one the conversion recipes produce would be this module
 * creating the duplication it set out to remove.
 */
public final class OreDrops {

    private OreDrops() {
    }

    private static final Map<String, Item> RESOLVED = new ConcurrentHashMap<>();

    /** The sentinel for "this pack has no such material", since a map holds no nulls. */
    private static final Item NONE = net.minecraft.world.item.Items.AIR;

    /**
     * The item a family's ore drops, or null when this pack has no such
     * material.
     *
     * @param family  the {@code c:} family name, {@code "lead"}
     * @param vanilla the family's own item in the base game, looked for after
     *                a raw form and a gem and before a dust or an ingot, or null
     */
    @Nullable
    public static Item of(String family, @Nullable String vanilla) {
        // Remembered per family and fallback, not per family. The placement
        // gate asks without the fallback and the ore with it, and with the base
        // game's item ahead of a dust the two answers differ: one entry for
        // both would hand the Nether's coal ore whatever worldgen asked first,
        // which is coal dust.
        String key = family + "|" + (vanilla == null ? "" : vanilla);
        Item found = RESOLVED.computeIfAbsent(key, ignored -> search(family, vanilla));
        return found == NONE ? null : found;
    }

    /** One block of this ore, or an empty stack for a material nobody supplies. */
    public static ItemStack stack(String family, @Nullable String vanilla, int count) {
        Item item = of(family, vanilla);
        return item == null || count <= 0 ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    /** Whether this pack has the material at all. Read by the placement gate. */
    public static boolean present(String family, @Nullable String vanilla) {
        return of(family, vanilla) != null;
    }

    /** Forget every answer. Called when tags reload, because the answer can change. */
    public static void invalidate() {
        RESOLVED.clear();
    }

    // ------------------------------------------------------------------

    private static Item search(String family, @Nullable String vanilla) {
        Map<String, List<String>> members = new HashMap<>();
        Map<String, Item> byId = new HashMap<>();
        for (String kind : UnifyRules.DROP_ORDER) {
            if (!UnifyRules.VANILLA.equals(kind)) {
                members.put(kind, idsIn(kind + "/" + family, byId));
            }
        }
        Item fallback = vanilla == null || vanilla.isEmpty() ? null
                : BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(vanilla)).orElse(null);
        if (fallback != null) {
            byId.put(vanilla, fallback);
        }
        String chosen = UnifyRules.drop(members, fallback == null ? null : vanilla);
        Item item = chosen == null ? null : byId.get(chosen);
        return item == null ? NONE : item;
    }

    /** Every id in one {@code c:} tag, noting which item each one is. */
    private static List<String> idsIn(String path, Map<String, Item> byId) {
        TagKey<Item> key = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", path));
        List<String> ids = new ArrayList<>();
        for (net.minecraft.core.Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
            ResourceKey<Item> id = holder.unwrapKey().orElse(null);
            if (id == null) {
                continue;
            }
            String spelled = id.location().toString();
            ids.add(spelled);
            byId.put(spelled, holder.value());
        }
        return ids;
    }
}
