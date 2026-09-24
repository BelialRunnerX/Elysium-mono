package com.elysium.ores.unify;

import com.elysium.ores.rules.UnifyRules;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The pack's answer to "which steel", worked out once and then just read.
 *
 * <h2>When this is computed</h2>
 *
 * When tags finish loading, and never before. The families are {@code c:}
 * tags, a tag is a datapack thing, and a datapack can be changed by the player
 * — so any answer worked out at registration time would be an answer about a
 * pack that may no longer exist. Recomputing on every tag reload is also what
 * makes {@code /reload} able to fix a mistake in the priority.
 *
 * <h2>What it produces</h2>
 *
 * For each kind of thing (ingots, raw materials, gems, dusts, nuggets, plates,
 * storage blocks) and each family within it: the canonical item, and the list
 * of variants that should convert into it. Plus the set of items that answer
 * to two families at once and are therefore never touched — the guard
 * {@link UnifyRules#ambiguous} exists for.
 */
public final class OreUnification {

    private OreUnification() {
    }

    /** The kinds of thing a material comes in, in the order they are reported. */
    public static final List<String> KINDS = List.of(
            "raw_materials", "ingots", "gems", "dusts", "nuggets", "plates",
            "storage_blocks");

    /** family name to canonical item, per kind. */
    private static Map<String, Map<String, Item>> canonical = Map.of();

    /** every variant to the item it converts into, across all kinds. */
    private static Map<Item, Item> conversions = Map.of();

    /** the items that answer to more than one family and are left alone. */
    private static Set<Item> ambiguous = Set.of();

    /** How many families actually needed unifying in this pack. */
    private static int unified;

    /**
     * Reads every family tag and settles the pack's answer.
     *
     * Called from the tag-reload handler. Everything it publishes is replaced
     * wholesale rather than mutated, so a reader mid-lookup sees one consistent
     * answer or the other and never half of each.
     */
    public static void recompute() {
        Map<String, Map<String, Item>> chosen = new LinkedHashMap<>();
        Map<Item, Item> converts = new HashMap<>();
        Set<Item> twoFamilies = new HashSet<>();
        int families = 0;

        for (String kind : KINDS) {
            Map<String, List<Item>> members = membersOf(kind);

            // The guard, computed per kind: an item in two families of the
            // same kind is a tagging mistake somewhere, and converting it
            // would destroy a material.
            Map<Item, List<String>> belongs = new HashMap<>();
            for (Map.Entry<String, List<Item>> family : members.entrySet()) {
                for (Item item : family.getValue()) {
                    belongs.computeIfAbsent(item, ignored -> new ArrayList<>())
                            .add(family.getKey());
                }
            }
            for (Map.Entry<Item, List<String>> entry : belongs.entrySet()) {
                if (UnifyRules.ambiguous(new HashSet<>(entry.getValue()))) {
                    twoFamilies.add(entry.getKey());
                }
            }

            Map<String, Item> perFamily = new TreeMap<>();
            for (Map.Entry<String, List<Item>> family : members.entrySet()) {
                List<String> ids = idsOf(family.getValue());
                if (!UnifyRules.worthUnifying(ids)) {
                    continue;
                }
                String pick = UnifyRules.canonical(ids);
                if (pick == null) {
                    continue;
                }
                Item winner = byId(pick);
                if (winner == null) {
                    continue;
                }
                perFamily.put(family.getKey(), winner);
                families++;
                for (String variant : UnifyRules.conversions(ids, pick, idsOf(twoFamilies))) {
                    Item loser = byId(variant);
                    if (loser != null && loser != winner) {
                        converts.put(loser, winner);
                    }
                }
            }
            if (!perFamily.isEmpty()) {
                chosen.put(kind, Collections.unmodifiableMap(perFamily));
            }
        }

        canonical = Map.copyOf(chosen);
        conversions = Map.copyOf(converts);
        ambiguous = Set.copyOf(twoFamilies);
        unified = families;
    }

    // ------------------------------------------------------------------
    // Reading the answer
    // ------------------------------------------------------------------

    /** What this item should become, or null when it is already right. */
    @Nullable
    public static Item canonicalFor(Item item) {
        return conversions.get(item);
    }

    /** The same stack, of the canonical item, keeping its count. */
    public static ItemStack unify(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        Item want = conversions.get(stack.getItem());
        if (want == null) {
            return stack;
        }
        ItemStack out = new ItemStack(want, stack.getCount());
        // Components are deliberately not copied. A variant's components
        // belong to that variant's item; carrying them onto a different item
        // is how a unifier produces something nobody's recipe expects.
        return out;
    }

    /** Whether this item is one the guard protects. */
    public static boolean isAmbiguous(Item item) {
        return ambiguous.contains(item);
    }

    /** Every conversion, for the recipe injector and the report. */
    public static Map<Item, Item> all() {
        return conversions;
    }

    /** The canonical of one family, for a report. */
    public static Map<String, Map<String, Item>> canonicals() {
        return canonical;
    }

    /** How many families needed unifying. */
    public static int families() {
        return unified;
    }

    /** How many items the guard is protecting. */
    public static int protectedCount() {
        return ambiguous.size();
    }

    // ------------------------------------------------------------------

    private static Map<String, List<Item>> membersOf(String kind) {
        Map<String, List<Item>> out = new LinkedHashMap<>();
        String prefix = kind + "/";
        for (TagKey<Item> tag : BuiltInRegistries.ITEM.getTagNames().toList()) {
            ResourceLocation id = tag.location();
            if (!id.getNamespace().equals("c") || !id.getPath().startsWith(prefix)) {
                continue;
            }
            String family = id.getPath().substring(prefix.length());
            if (family.isEmpty() || family.contains("/")) {
                continue;
            }
            List<Item> items = new ArrayList<>();
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                items.add(holder.value());
            }
            if (!items.isEmpty()) {
                out.put(family, items);
            }
        }
        return out;
    }

    private static List<String> idsOf(Iterable<Item> items) {
        List<String> ids = new ArrayList<>();
        for (Item item : items) {
            ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
        }
        return ids;
    }

    @Nullable
    private static Item byId(String id) {
        return BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.parse(id)).orElse(null);
    }

    /** The registry key of the item registry, kept for the tag lookups above. */
    static final net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<Item>>
            ITEM_REGISTRY = Registries.ITEM;
}
