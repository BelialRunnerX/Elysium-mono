package com.elysium.colonies.market;

import com.elysium.colonies.charter.Charter;
import com.elysium.colonies.charter.Charters;
import com.elysium.colonies.rules.MarketRules;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the frontier will pay, and what it will charge.
 *
 * <h2>Where a price comes from</h2>
 *
 * Three things, and all three are already written down somewhere else.
 *
 * <ol>
 *   <li><b>What the thing is worth.</b> A shipped table of base values, so an
 *       ingot is worth more than a cobblestone and a datapack can disagree.</li>
 *   <li><b>Where you are.</b> The world's charter. A material the world
 *       produces is cheap here and a material a settlement needs and the world
 *       does not have is dear — which is the charter's own statement about the
 *       place, priced.</li>
 *   <li><b>When it is.</b> A drift that wanders and returns on a fixed period,
 *       readable from the day rather than stored, so a market a player has
 *       never visited still has a history and a route can be learned.</li>
 * </ol>
 *
 * <h2>Which family a thing belongs to</h2>
 *
 * By {@code c:} tag, the same vocabulary elysium-ores unified the pack onto.
 * That means the market prices <em>materials</em> rather than items: every
 * mod's lead is lead, and a world short of lead is short of all of it.
 * Anything that answers to no family is priced as an ordinary thing and drifts
 * like everything else.
 */
public final class Market {

    private Market() {
    }

    /** The kinds of thing a material comes in, cheapest form first. */
    private static final List<String> KINDS =
            List.of("raw_materials", "ores", "ingots", "gems", "dusts", "nuggets",
                    "storage_blocks", "plates");

    private static final Map<Item, String> FAMILIES = new ConcurrentHashMap<>();

    /** The sentinel for "belongs to no family", since a map holds no nulls. */
    private static final String NONE = "";

    /** Forget every resolved item. Called when tags reload. */
    public static void invalidate() {
        FAMILIES.clear();
    }

    /**
     * The material family this item belongs to, or null.
     *
     * Cached per item: the answer depends only on the tags, and a busy market
     * day asks it hundreds of times.
     */
    @Nullable
    public static String familyOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String found = FAMILIES.computeIfAbsent(stack.getItem(), Market::search);
        return found.equals(NONE) ? null : found;
    }

    private static String search(Item item) {
        for (TagKey<Item> tag : BuiltInRegistries.ITEM.getTagNames().toList()) {
            ResourceLocation id = tag.location();
            if (!id.getNamespace().equals("c")) {
                continue;
            }
            String path = id.getPath();
            int slash = path.indexOf('/');
            if (slash < 0 || !KINDS.contains(path.substring(0, slash))) {
                continue;
            }
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                if (holder.value() == item) {
                    return path.substring(slash + 1);
                }
            }
        }
        return NONE;
    }

    /**
     * What one of these is worth on this world today, before the spread.
     *
     * @param dimension the world the market stands on
     * @param day       the colony's own day count, which is what drifts
     */
    public static int priceOf(ResourceLocation dimension, ItemStack stack, int day) {
        if (stack.isEmpty()) {
            return MarketRules.BASE_VALUE;
        }
        Charter charter = Charters.of(dimension);
        String family = familyOf(stack);
        int base = MarketValues.of(stack, family);

        boolean local = family != null && charter.natives().contains(family);
        float demand;
        if (family == null) {
            // Something with no material identity: a tool, a decoration, a
            // mod's own oddity. The frontier wants it moderately everywhere,
            // which keeps it tradeable without making it a route.
            demand = 0.5F;
        } else if (local) {
            demand = 0.0F;
        } else {
            // A thing a settlement consumes and this world cannot produce is
            // the dearest thing on it. That is the charter's own sentence,
            // read as a price.
            demand = Charters.needs().contains(family) ? 1.0F : 0.4F;
        }
        int priced = MarketRules.priceOf(base, local, demand);
        return MarketRules.drifted(priced, day, seedFor(stack, dimension));
    }

    /** What the market will pay for one. */
    public static int offering(ResourceLocation dimension, ItemStack stack, int day) {
        return MarketRules.offering(priceOf(dimension, stack, day));
    }

    /** What the market wants for one. */
    public static int asking(ResourceLocation dimension, ItemStack stack, int day) {
        return MarketRules.asking(priceOf(dimension, stack, day));
    }

    /**
     * The number that decides where in its cycle a commodity's drift is.
     *
     * Derived from the item's name and the world's, so every commodity on
     * every world is at a different point — and the same one every time, on
     * every machine, without anything being stored.
     */
    private static int seedFor(ItemStack stack, ResourceLocation dimension) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        int hash = (id == null ? 0 : id.toString().hashCode()) * 31
                + dimension.toString().hashCode();
        return Math.floorMod(hash, 1024);
    }

    /** The registry key of the item registry, kept for the tag walk above. */
    static final net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<Item>>
            ITEM_REGISTRY = Registries.ITEM;
}
