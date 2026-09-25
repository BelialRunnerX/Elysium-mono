package com.elysium.industry.registry;

import com.elysium.industry.ElysiumIndustry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** One item per block, and one tab to find them in. */
public final class IndustryItems {

    private IndustryItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumIndustry.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumIndustry.MODID);

    public static final DeferredItem<BlockItem> COPPER_WIRE =
            ITEMS.registerSimpleBlockItem(IndustryBlocks.COPPER_WIRE);
    public static final DeferredItem<BlockItem> GOLD_WIRE =
            ITEMS.registerSimpleBlockItem(IndustryBlocks.GOLD_WIRE);
    public static final DeferredItem<BlockItem> STEEL_WIRE =
            ITEMS.registerSimpleBlockItem(IndustryBlocks.STEEL_WIRE);
    public static final DeferredItem<BlockItem> TRANSFORMER =
            ITEMS.registerSimpleBlockItem(IndustryBlocks.TRANSFORMER);
    public static final DeferredItem<BlockItem> STEAM_TURBINE =
            ITEMS.registerSimpleBlockItem(IndustryBlocks.STEAM_TURBINE);
    public static final DeferredItem<BlockItem> DIESEL_ENGINE =
            ITEMS.registerSimpleBlockItem(IndustryBlocks.DIESEL_ENGINE);
    public static final DeferredItem<BlockItem> ALTERNATOR =
            ITEMS.registerSimpleBlockItem(IndustryBlocks.ALTERNATOR);
    public static final DeferredItem<BlockItem> MOTOR =
            ITEMS.registerSimpleBlockItem(IndustryBlocks.MOTOR);
    public static final DeferredItem<BlockItem> CRUSHER =
            ITEMS.registerSimpleBlockItem(IndustryBlocks.CRUSHER);
    public static final DeferredItem<BlockItem> ROLLING_MILL =
            ITEMS.registerSimpleBlockItem(IndustryBlocks.ROLLING_MILL);
    public static final DeferredItem<BlockItem> ARC_FURNACE =
            ITEMS.registerSimpleBlockItem(IndustryBlocks.ARC_FURNACE);
    public static final DeferredItem<BlockItem> SEPARATOR =
            ITEMS.registerSimpleBlockItem(IndustryBlocks.SEPARATOR);

    /**
     * The five things Age II makes that are not blocks.
     *
     * <h2>Why this module has a material chain at all</h2>
     *
     * Because the age needs one thing it cannot be handed. Steel is what the
     * arc furnace is <em>for</em>, and it gates the top tier of wire, the
     * diesel engine and the separator — so the machine a player builds in the
     * middle of the age is the machine that opens the rest of it, rather than
     * a faster way of doing what they could already do.
     *
     * <p>Plates are the same argument at one remove: they are what the rolling
     * mill makes, and every block above the bootstrap pair needs them, so a
     * factory is a thing you build with a factory.
     *
     * <h2>And why they are tagged</h2>
     *
     * {@code elysium-foundry} owns the full material ladder and will have its
     * own routes to steel and to plate. These sit under {@code c:ingots/steel}
     * and {@code c:plates/*} so that it can add to them without either module
     * knowing about the other, and so that every recipe here asks for the tag
     * and not for this module's own item.
     */
    private static DeferredItem<Item> part(String name) {
        return ITEMS.registerSimpleItem(name);
    }

    public static final DeferredItem<Item> STEEL_INGOT = part("steel_ingot");
    public static final DeferredItem<Item> IRON_PLATE = part("iron_plate");
    public static final DeferredItem<Item> COPPER_PLATE = part("copper_plate");
    public static final DeferredItem<Item> GOLD_PLATE = part("gold_plate");
    public static final DeferredItem<Item> STEEL_PLATE = part("steel_plate");

    /** What a coke oven makes: the fuel the blast furnace runs on. */
    public static final DeferredItem<Item> COKE =
            part(com.elysium.industry.rules.FurnaceRules.COKE);

    /** The parts, in the order the tab shows them. */
    private static final List<DeferredItem<Item>> PARTS = List.of(
            STEEL_INGOT, IRON_PLATE, COPPER_PLATE, GOLD_PLATE, STEEL_PLATE, COKE);

    /**
     * The bricks and the two machines made of them, walked out of the shapes.
     *
     * <p>Walked rather than listed for the usual reason: a brick a shape
     * names and nothing registers is a machine nobody can build, and a hand
     * list of two is a list that is wrong the day there are three.
     */
    private static final List<DeferredItem<BlockItem>> BRICKWORK = brickwork();

    private static List<DeferredItem<BlockItem>> brickwork() {
        List<DeferredItem<BlockItem>> made = new java.util.ArrayList<>();
        for (java.util.Map<String, net.neoforged.neoforge.registries.DeferredBlock<
                net.minecraft.world.level.block.Block>> group
                : List.of(IndustryBlocks.BRICKS, IndustryBlocks.FURNACES)) {
            for (net.neoforged.neoforge.registries.DeferredBlock<
                    net.minecraft.world.level.block.Block> block : group.values()) {
                made.add(ITEMS.registerSimpleBlockItem(block));
            }
        }
        return List.copyOf(made);
    }

    /** The order the tab shows them in: carry it, make it, trade it, spend it. */
    private static final List<DeferredItem<BlockItem>> ORDER = List.of(
            COPPER_WIRE, GOLD_WIRE, STEEL_WIRE, TRANSFORMER,
            STEAM_TURBINE, DIESEL_ENGINE,
            ALTERNATOR, MOTOR,
            CRUSHER, ROLLING_MILL, ARC_FURNACE, SEPARATOR);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "industry", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumindustry"))
                    .icon(() -> new ItemStack(ALTERNATOR.get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredItem<? extends Item> item : ORDER) {
                            output.accept(item.get());
                        }
                        for (DeferredItem<? extends Item> item : BRICKWORK) {
                            output.accept(item.get());
                        }
                        for (DeferredItem<? extends Item> item : PARTS) {
                            output.accept(item.get());
                        }
                    })
                    .build());
}
