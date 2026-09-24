package com.elysium.atomics.registry;

import com.elysium.atomics.ElysiumAtomics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** One item per block, three of its own, and one tab to find them in. */
public final class AtomicItems {

    private AtomicItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumAtomics.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumAtomics.MODID);

    /** What a worn core is put right with. Age II's plate, by its common tag. */
    public static final TagKey<Item> STEEL_PLATE_TAG = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath("c", "plates/steel"));

    public static final DeferredItem<BlockItem> BASIC_CELL =
            ITEMS.registerSimpleBlockItem(AtomicBlocks.BASIC_CELL);
    public static final DeferredItem<BlockItem> ADVANCED_CELL =
            ITEMS.registerSimpleBlockItem(AtomicBlocks.ADVANCED_CELL);
    public static final DeferredItem<BlockItem> QUANTUM_CELL =
            ITEMS.registerSimpleBlockItem(AtomicBlocks.QUANTUM_CELL);
    public static final DeferredItem<BlockItem> REACTOR_CORE =
            ITEMS.registerSimpleBlockItem(AtomicBlocks.REACTOR_CORE);
    public static final DeferredItem<BlockItem> COOLANT_CHANNEL =
            ITEMS.registerSimpleBlockItem(AtomicBlocks.COOLANT_CHANNEL);
    public static final DeferredItem<BlockItem> SOLAR_PANEL =
            ITEMS.registerSimpleBlockItem(AtomicBlocks.SOLAR_PANEL);
    public static final DeferredItem<BlockItem> SERVO =
            ITEMS.registerSimpleBlockItem(AtomicBlocks.SERVO);
    public static final DeferredItem<BlockItem> ENRICHER =
            ITEMS.registerSimpleBlockItem(AtomicBlocks.ENRICHER);
    public static final DeferredItem<BlockItem> REACTION_CHAMBER =
            ITEMS.registerSimpleBlockItem(AtomicBlocks.REACTION_CHAMBER);
    public static final DeferredItem<BlockItem> CENTRIFUGE =
            ITEMS.registerSimpleBlockItem(AtomicBlocks.CENTRIFUGE);

    /**
     * The three things this age makes that are not blocks.
     *
     * <h2>Why a module about electricity ships raw ore</h2>
     *
     * Because {@code elysium-ores} generates uranium and every item that could
     * have filled {@code c:raw_materials/uranium} belongs to a mod this
     * conversion is replacing. An ore that drops nothing is not a
     * neutral state to leave the pack in, and the age that needs the material
     * is the age that owes it — which is the rule the conversion works to: a
     * module is not finished until the integrations it inherited are written.
     */
    private static DeferredItem<Item> part(String name) {
        return ITEMS.registerSimpleItem(name);
    }

    public static final DeferredItem<Item> RAW_URANIUM = part("raw_uranium");
    public static final DeferredItem<Item> URANIUM_INGOT = part("uranium_ingot");
    public static final DeferredItem<Item> ENRICHED_URANIUM = part("enriched_uranium");

    private static final List<DeferredItem<Item>> PARTS =
            List.of(RAW_URANIUM, URANIUM_INGOT, ENRICHED_URANIUM);

    /** The order the tab shows them in: keep it, make it, hand it back, spend it. */
    private static final List<DeferredItem<BlockItem>> ORDER = List.of(
            BASIC_CELL, ADVANCED_CELL, QUANTUM_CELL,
            REACTOR_CORE, COOLANT_CHANNEL, SOLAR_PANEL,
            SERVO,
            ENRICHER, REACTION_CHAMBER, CENTRIFUGE);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "atomics", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumatomics"))
                    .icon(() -> new ItemStack(REACTOR_CORE.get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredItem<? extends Item> item : ORDER) {
                            output.accept(item.get());
                        }
                        for (DeferredItem<? extends Item> item : PARTS) {
                            output.accept(item.get());
                        }
                    })
                    .build());
}
