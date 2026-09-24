package com.elysium.bestiary.registry;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.rules.BeastRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/** One spawn egg per beast, and a tab to find them in. */
public final class BeastItems {

    private BeastItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumBestiary.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumBestiary.MODID);

    /** The two colours of each egg, one per family and one per tier. */
    private static final Map<String, Integer> FAMILY_COLOUR = Map.of(
            BeastRules.PROWLER, 0x6B4A7A,
            BeastRules.BRUTE, 0x7A5230,
            BeastRules.WIGHT, 0x3E5A62);

    private static final int[] TIER_COLOUR = {0xC8BEE0, 0x9A86C8, 0x6E56A8};

    public static final Map<String, DeferredItem<DeferredSpawnEggItem>> EGGS = register();

    private static Map<String, DeferredItem<DeferredSpawnEggItem>> register() {
        Map<String, DeferredItem<DeferredSpawnEggItem>> made = new LinkedHashMap<>();
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            int background = FAMILY_COLOUR.getOrDefault(beast.family(), 0x555555);
            int highlight = TIER_COLOUR[Math.min(TIER_COLOUR.length - 1,
                    Math.max(0, beast.tier() - 1))];
            made.put(beast.id(), ITEMS.register(beast.id() + "_spawn_egg",
                    () -> new DeferredSpawnEggItem(
                            () -> BeastEntities.ALL.get(beast.id()).get(),
                            background, highlight, new Item.Properties())));
        }
        // Same count, same reason: an egg missing is a beast a player cannot
        // put in front of themselves to look at, and nothing else would say so.
        if (made.size() != BeastRules.BEASTS.size()) {
            throw new IllegalStateException("the bestiary has "
                    + BeastRules.BEASTS.size() + " beasts and " + made.size()
                    + " spawn egg(s) were registered");
        }
        return Map.copyOf(made);
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "bestiary", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumbestiary"))
                    .icon(() -> new ItemStack(EGGS.get(
                            BeastRules.BEASTS.get(0).id()).get()))
                    .displayItems((parameters, output) -> {
                        for (BeastRules.Beast beast : BeastRules.BEASTS) {
                            output.accept(EGGS.get(beast.id()).get());
                        }
                    })
                    .build());
}
