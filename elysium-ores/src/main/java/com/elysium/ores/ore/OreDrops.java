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
 * Raw material, then gem, then dust, then ingot. A player breaking an ore
 * expects to hold something that still has to be smelted, so the raw form wins
 * wherever there is one; the further down the list, the more processed the
 * material already is, and the list only gets that far for materials that have
 * no raw form at all — lapis has no raw lapis, and never will.
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

    /** Where a material may be found, best first. */
    public static final List<String> KINDS =
            List.of("raw_materials", "gems", "dusts", "ingots");

    private static final Map<String, Item> RESOLVED = new ConcurrentHashMap<>();

    /** The sentinel for "this pack has no such material", since a map holds no nulls. */
    private static final Item NONE = net.minecraft.world.item.Items.AIR;

    /**
     * The item a family's ore drops, or null when this pack has no such
     * material.
     *
     * @param family  the {@code c:} family name, {@code "lead"}
     * @param vanilla an item id to fall back on, or null
     */
    @Nullable
    public static Item of(String family, @Nullable String vanilla) {
        Item found = RESOLVED.computeIfAbsent(family, name -> search(name, vanilla));
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
        for (String kind : KINDS) {
            Item chosen = bestIn(kind + "/" + family);
            if (chosen != null) {
                return chosen;
            }
        }
        if (vanilla != null) {
            Item fallback = BuiltInRegistries.ITEM
                    .getOptional(ResourceLocation.parse(vanilla)).orElse(null);
            if (fallback != null) {
                return fallback;
            }
        }
        return NONE;
    }

    /** The canonical member of one {@code c:} tag, or null if it is empty. */
    @Nullable
    private static Item bestIn(String path) {
        TagKey<Item> key = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", path));
        List<String> ids = new ArrayList<>();
        Map<String, Item> byId = new java.util.HashMap<>();
        for (net.minecraft.core.Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
            ResourceKey<Item> id = holder.unwrapKey().orElse(null);
            if (id == null) {
                continue;
            }
            String spelled = id.location().toString();
            ids.add(spelled);
            byId.put(spelled, holder.value());
        }
        String canonical = UnifyRules.canonical(ids);
        return canonical == null ? null : byId.get(canonical);
    }
}
