package com.elysium.trinkets;

import com.elysium.lib.trinket.ElysiumTrinket;
import com.elysium.trinkets.item.ElysiumTrinketItem;
import com.elysium.trinkets.trinket.CraftedTrinkets;
import com.elysium.trinkets.trinket.UniqueTrinkets;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elysium Trinkets — forty accessories worn in Curios slots.
 *
 * <h2>What this mod is</h2>
 *
 * Twenty-four found and sixteen crafted. The found ones change a rule and never
 * ascend; the crafted ones change an amount and ascend without a ceiling. See
 * {@link UniqueTrinkets} and {@link CraftedTrinkets} for why that split is the
 * shape of the whole mod rather than a filing convenience.
 *
 * <h2>How little of it is here</h2>
 *
 * This class registers forty items and installs one adapter. It contains no
 * effect, no combat code, no tick handler and no attribute logic, because none
 * of that belongs to a trinket mod:
 *
 * <ul>
 *   <li><b>What a trinket does</b> is an {@code ElysiumPassive} held by the
 *       library's {@code ElysiumTrinket}. The library's fifteen hooks were
 *       written for races and classes and a trinket answers the same ones —
 *       there is deliberately no trinket-only hook, so a trinket can do
 *       anything a class can and nothing needed adding to support any of the
 *       forty.</li>
 *   <li><b>Where a trinket is worn</b> is Curios, through {@link CuriosSlots},
 *       which is the only file in this mod that imports it.</li>
 *   <li><b>Reforging and ascension</b> are elysium-core's reforge table, which
 *       accepts anything implementing {@code ElysiumSocketable}. This mod is
 *       not named there and does not name it; a trinket is reforgeable because
 *       of what it is, not because anyone wired the two together.</li>
 * </ul>
 *
 * The mod is therefore about a hundred lines of registration and forty
 * descriptions of what an accessory ought to do — which is what it should be.
 */
@Mod(ElysiumTrinkets.MODID)
public class ElysiumTrinkets {

    public static final String MODID = "elysiumtrinkets";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, MODID);

    /**
     * Item per trinket, in registration order.
     *
     * A LinkedHashMap rather than two parallel lists: the creative tab wants
     * them in order and the recipe generator wants to look one up by trinket,
     * and keeping those as separate structures is how they come to disagree.
     */
    private static final Map<ElysiumTrinket, DeferredHolder<Item, Item>> ITEM_BY_TRINKET =
            new LinkedHashMap<>();

    public ElysiumTrinkets(IEventBus modEventBus) {
        // Registration happens from the constructor, and every trinket object
        // is created by the two class initialisations below. Nothing here calls
        // get() on a DeferredHolder: the registries do not exist yet, and doing
        // so is what killed two earlier launches of this project.
        for (ElysiumTrinket trinket : UniqueTrinkets.ALL) {
            register(trinket, 0);
        }
        for (ElysiumTrinket trinket : CraftedTrinkets.ALL) {
            register(trinket, CraftedTrinkets.CRAFTED_TIER);
        }

        // The one line that needs Curios to exist.
        CuriosSlots.install();

        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        LOGGER.info("Elysium Trinkets ready: {} found, {} crafted, {} slots",
                UniqueTrinkets.ALL.length, CraftedTrinkets.ALL.length, slotCount());
    }

    /**
     * One item for one trinket.
     *
     * The registry name is the trinket's own path, so a trinket and its item
     * can never drift apart into two names for one thing — and the model, the
     * texture, the recipe and the loot entry all derive from that same string.
     */
    private static void register(ElysiumTrinket trinket, int tier) {
        String name = trinket.getId().getPath();
        ITEM_BY_TRINKET.put(trinket, ITEMS.register(name,
                () -> new ElysiumTrinketItem(trinket, tier, 0.0F, 0.0F, new Item.Properties())));
    }

    /** The item carrying a trinket, or null if it has none. */
    public static DeferredHolder<Item, Item> itemFor(ElysiumTrinket trinket) {
        return ITEM_BY_TRINKET.get(trinket);
    }

    /** Every trinket item, in registration order. */
    public static List<DeferredHolder<Item, Item>> allItems() {
        return List.copyOf(ITEM_BY_TRINKET.values());
    }

    private static int slotCount() {
        List<String> slots = new ArrayList<>();
        for (ElysiumTrinket trinket : ElysiumTrinket.REGISTRY.all()) {
            if (!slots.contains(trinket.getSlot())) {
                slots.add(trinket.getSlot());
            }
        }
        return slots.size();
    }

    // ------------------------------------------------------------------

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TRINKETS_TAB =
            CREATIVE_TABS.register("trinkets", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumtrinkets"))
                    // A lambda: it runs when the tab is built, long after the
                    // registries are full. Resolving a holder here directly
                    // would be the unbound-value crash again.
                    .icon(() -> new ItemStack(itemFor(UniqueTrinkets.WIDOWS_THIMBLE).get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredHolder<Item, Item> item : allItems()) {
                            output.accept(item.get());
                        }
                    })
                    .build());
}
