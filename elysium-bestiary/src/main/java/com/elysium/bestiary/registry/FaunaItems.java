package com.elysium.bestiary.registry;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.rules.FaunaRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/** One spawn egg per animal, and a tab of their own to find them in. */
public final class FaunaItems {

    private FaunaItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumBestiary.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumBestiary.MODID);

    /** One colour per kind, so a tab of eggs reads as four groups. */
    private static final Map<String, Integer> KIND_COLOUR = Map.of(
            FaunaRules.GRAZER, 0x7C6A43,
            FaunaRules.FORAGER, 0x5E6B3A,
            FaunaRules.FOWL, 0xB0A07A,
            FaunaRules.SWIMMER, 0x3E6E7A);

    public static final Map<String, DeferredItem<DeferredSpawnEggItem>> EGGS = register();

    private static Map<String, DeferredItem<DeferredSpawnEggItem>> register() {
        Map<String, DeferredItem<DeferredSpawnEggItem>> made = new LinkedHashMap<>();
        for (FaunaRules.Animal animal : FaunaRules.ANIMALS) {
            int background = KIND_COLOUR.getOrDefault(animal.kind(), 0x555555);
            int highlight = 0xE8E0C8;
            made.put(animal.id(), ITEMS.register(animal.id() + "_spawn_egg",
                    () -> new DeferredSpawnEggItem(
                            () -> FaunaEntities.ALL.get(animal.id()).get(),
                            background, highlight, new Item.Properties())));
        }
        if (made.size() != FaunaRules.ANIMALS.size()) {
            throw new IllegalStateException("the fauna has "
                    + FaunaRules.ANIMALS.size() + " animals and " + made.size()
                    + " spawn egg(s) were registered");
        }
        return Map.copyOf(made);
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "fauna", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumbestiary.fauna"))
                    .icon(() -> new ItemStack(EGGS.get(
                            FaunaRules.ANIMALS.get(0).id()).get()))
                    .displayItems((parameters, output) -> {
                        for (FaunaRules.Animal animal : FaunaRules.ANIMALS) {
                            output.accept(EGGS.get(animal.id()).get());
                        }
                    })
                    .build());
}
