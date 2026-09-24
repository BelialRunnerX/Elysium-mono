package com.elysium.foundry.registry;

import com.elysium.foundry.ElysiumFoundry;
import com.elysium.foundry.rules.FoundryRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every shape of every metal, read off the chain rather than typed out.
 *
 * <h2>Why a loop and not a hundred fields</h2>
 *
 * Twenty-two materials in four or five shapes each is more than a hundred
 * items, and a hundred hand-written fields is a hundred chances for one of
 * them to have the wrong name, the wrong tag or no model. The chain is
 * declared once in {@code FoundryRules} and everything here is read off it, so
 * adding a metal is a line in that file and nothing else.
 *
 * <h2>What it deliberately does not register</h2>
 *
 * Anything another module already supplies. Iron, copper and gold are
 * vanilla's; steel is Age II's; uranium is Age III's. Registering a second
 * one of any of those would be this module creating exactly the duplication
 * {@code elysium-ores} exists to remove — so they are named here, skipped, and
 * reached through their common tags instead.
 */
public final class FoundryItems {

    private FoundryItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumFoundry.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumFoundry.MODID);

    /**
     * Materials somebody else already supplies, which this module must not
     * supply a second copy of.
     */
    public static final Set<String> SOMEBODY_ELSE_SUPPLIES =
            Set.of("iron", "copper", "gold", "steel", "uranium", "redstone_dust");

    /**
     * Materials that exist in exactly one shape, named after themselves.
     *
     * Coal dust is a dust and nothing else; calling its dust form
     * {@code coal_dust_dust} would be the model leaking into the item names.
     */
    public static final Map<String, String> ONE_FORM = Map.of(
            "coal_dust", "dust",
            "bort", "gem",
            "certus_quartz", "gem",
            "fluorite", "gem",
            "rime_shard", "gem",
            "uraninite", "raw");

    /** The shapes a mined metal comes in. */
    public static final List<String> MINED_FORMS =
            List.of("raw", "ingot", "nugget", "dust", "plate");

    /** The shapes an alloy comes in. It is never raw. */
    public static final List<String> ALLOY_FORMS =
            List.of("ingot", "nugget", "dust", "plate");

    /** The two shapes only a casing material needs. */
    public static final List<String> WORKED_FORMS = List.of("rod", "gear");

    private static final Map<String, DeferredItem<Item>> MADE = new LinkedHashMap<>();

    /** The name an item of this material and shape goes by. */
    public static String nameOf(String material, String form) {
        if (ONE_FORM.containsKey(material)) {
            return material.equals("uraninite") ? "raw_uraninite" : material;
        }
        return form.equals("raw") ? "raw_" + material : material + "_" + form;
    }

    static {
        for (FoundryRules.Material material : FoundryRules.MATERIALS) {
            if (SOMEBODY_ELSE_SUPPLIES.contains(material.name())) {
                continue;
            }
            List<String> forms = ONE_FORM.containsKey(material.name())
                    ? List.of(ONE_FORM.get(material.name()))
                    : material.mined() ? MINED_FORMS : ALLOY_FORMS;
            for (String form : forms) {
                String name = nameOf(material.name(), form);
                MADE.put(name, ITEMS.registerSimpleItem(name));
            }
        }
        // Rods and gears only for what a machine casing is made of: every
        // metal in six shapes would be a creative tab nobody can read, and
        // five of the six would never be crafted.
        for (FoundryRules.Casing casing : FoundryRules.CASINGS) {
            for (String form : WORKED_FORMS) {
                String name = nameOf(casing.madeOf(), form);
                MADE.computeIfAbsent(name, ITEMS::registerSimpleItem);
            }
        }
    }

    /** Every item this module makes, by name. */
    public static Map<String, DeferredItem<Item>> made() {
        return Map.copyOf(MADE);
    }

    /** One item, by material and shape, or null if this module does not make it. */
    public static DeferredItem<Item> of(String material, String form) {
        return MADE.get(nameOf(material, form));
    }

    public static final DeferredItem<BlockItem> ALLOY_SMELTER =
            ITEMS.registerSimpleBlockItem(FoundryBlocks.ALLOY_SMELTER);

    private static final Map<Integer, DeferredItem<BlockItem>> CASINGS =
            new LinkedHashMap<>();

    static {
        for (FoundryRules.Casing casing : FoundryRules.CASINGS) {
            CASINGS.put(casing.tier(),
                    ITEMS.registerSimpleBlockItem(FoundryBlocks.casing(casing.tier())));
        }
    }

    public static DeferredItem<BlockItem> casing(int tier) {
        return CASINGS.get(tier);
    }

    /** The order the tab shows them in: the machine, the casings, the metal. */
    private static List<DeferredItem<? extends Item>> order() {
        List<DeferredItem<? extends Item>> shown = new ArrayList<>();
        shown.add(ALLOY_SMELTER);
        shown.addAll(CASINGS.values());
        shown.addAll(MADE.values());
        return shown;
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "foundry", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumfoundry"))
                    .icon(() -> new ItemStack(ALLOY_SMELTER.get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredItem<? extends Item> item : order()) {
                            output.accept(item.get());
                        }
                    })
                    .build());
}
