package com.elysium.kinetics.registry;

import com.elysium.kinetics.ElysiumKinetics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One item per block, and one tab to find them in. */
public final class KineticItems {

    private KineticItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumKinetics.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumKinetics.MODID);

    public static final DeferredItem<BlockItem> TIMBER_SHAFT =
            ITEMS.registerSimpleBlockItem(KineticBlocks.TIMBER_SHAFT);
    public static final DeferredItem<BlockItem> BRASS_SHAFT =
            ITEMS.registerSimpleBlockItem(KineticBlocks.BRASS_SHAFT);
    public static final DeferredItem<BlockItem> SMALL_COGWHEEL =
            ITEMS.registerSimpleBlockItem(KineticBlocks.SMALL_COGWHEEL);
    public static final DeferredItem<BlockItem> LARGE_COGWHEEL =
            ITEMS.registerSimpleBlockItem(KineticBlocks.LARGE_COGWHEEL);
    public static final DeferredItem<BlockItem> HAND_CRANK =
            ITEMS.registerSimpleBlockItem(KineticBlocks.HAND_CRANK);
    public static final DeferredItem<BlockItem> WATER_WHEEL =
            ITEMS.registerSimpleBlockItem(KineticBlocks.WATER_WHEEL);
    public static final DeferredItem<BlockItem> GEARBOX =
            ITEMS.registerSimpleBlockItem(KineticBlocks.GEARBOX);
    public static final DeferredItem<BlockItem> WINDMILL_BEARING =
            ITEMS.registerSimpleBlockItem(KineticBlocks.WINDMILL_BEARING);
    public static final DeferredItem<BlockItem> SAIL =
            ITEMS.registerSimpleBlockItem(KineticBlocks.SAIL);
    public static final DeferredItem<BlockItem> MILLSTONE =
            ITEMS.registerSimpleBlockItem(KineticBlocks.MILLSTONE);
    public static final DeferredItem<BlockItem> PRESS =
            ITEMS.registerSimpleBlockItem(KineticBlocks.PRESS);
    public static final DeferredItem<BlockItem> SAW =
            ITEMS.registerSimpleBlockItem(KineticBlocks.SAW);
    public static final DeferredItem<BlockItem> MIXER =
            ITEMS.registerSimpleBlockItem(KineticBlocks.MIXER);

    /** One item per engine on the ladder, walked out of the rules. */
    public static final java.util.Map<String, DeferredItem<BlockItem>> ENGINES =
            registerEngines();

    private static java.util.Map<String, DeferredItem<BlockItem>> registerEngines() {
        java.util.Map<String, DeferredItem<BlockItem>> made =
                new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, net.neoforged.neoforge.registries.DeferredBlock<net.minecraft.world.level.block.Block>> one
                : KineticBlocks.ENGINES.entrySet()) {
            made.put(one.getKey(), ITEMS.registerSimpleBlockItem(one.getValue()));
        }
        if (made.size() != KineticBlocks.ENGINES.size()) {
            throw new IllegalStateException("there are "
                    + KineticBlocks.ENGINES.size() + " engine block(s) and "
                    + made.size() + " item(s) for them");
        }
        return java.util.Map.copyOf(made);
    }

    /**
     * The fuels this module makes itself.
     *
     * <p>Two: what a press gets out of a block of coal, and what a second
     * pressing gets out of that. The game's own four are the bottom of the
     * ladder and are not ours to register.
     */
    public static final java.util.Map<String, DeferredItem<Item>> FUELS =
            registerFuels();

    private static java.util.Map<String, DeferredItem<Item>> registerFuels() {
        java.util.Map<String, DeferredItem<Item>> made =
                new java.util.LinkedHashMap<>();
        for (String name : com.elysium.kinetics.rules.EngineRules.ours()) {
            made.put(name, ITEMS.registerSimpleItem(name));
        }
        if (made.size()
                != com.elysium.kinetics.rules.EngineRules.ours().size()) {
            throw new IllegalStateException("the rules have "
                    + com.elysium.kinetics.rules.EngineRules.ours().size()
                    + " fuel(s) of our own and " + made.size()
                    + " were registered");
        }
        return java.util.Map.copyOf(made);
    }

    /** The refinery, which turns one fuel into the next. */
    public static final DeferredItem<BlockItem> REFINERY =
            ITEMS.registerSimpleBlockItem(KineticBlocks.REFINERY);

    /** The dynamo, which is where the next age starts. */
    public static final DeferredItem<BlockItem> DYNAMO =
            ITEMS.registerSimpleBlockItem(KineticBlocks.DYNAMO);

    /** The order the tab shows them in: carry it, change it, make it, spend it. */
    private static final java.util.List<DeferredItem<BlockItem>> ORDER = order();

    private static java.util.List<DeferredItem<BlockItem>> order() {
        java.util.List<DeferredItem<BlockItem>> made =
                new java.util.ArrayList<>(java.util.List.of(
                        TIMBER_SHAFT, BRASS_SHAFT, SMALL_COGWHEEL,
                        LARGE_COGWHEEL, GEARBOX, HAND_CRANK, WATER_WHEEL,
                        WINDMILL_BEARING, SAIL, MILLSTONE, PRESS, SAW, MIXER));
        made.addAll(ENGINES.values());
        made.add(REFINERY);
        made.add(DYNAMO);
        return java.util.List.copyOf(made);
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "kinetics", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumkinetics"))
                    .icon(() -> new ItemStack(LARGE_COGWHEEL.get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredItem<? extends Item> item : ORDER) {
                            output.accept(item.get());
                        }
                    })
                    .build());

}
