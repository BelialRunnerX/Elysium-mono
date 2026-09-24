package com.elysium.armory.registry;

import com.elysium.armory.ElysiumArmory;
import com.elysium.armory.block.ArmoryAlloyForgeBlock;
import com.elysium.armory.block.ArmoryMaterialGraderBlock;
import com.elysium.armory.block.ArmoryMetalPressBlock;
import com.elysium.armory.block.ArmoryStarlightChargerBlock;
import com.elysium.armory.block.ArmoryReforgingTableBlock;
import com.elysium.armory.block.ArmorySalvagerBlock;
import com.elysium.armory.block.ArmorySmithingTableBlock;
import com.elysium.armory.block.ArmoryStoneAnvilBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The benches: where gear is designed, taken apart, rolled again, pressed and —
 * before any of that is affordable — beaten out on a rock.
 */
public final class ArmoryBlocks {

    private ArmoryBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ElysiumArmory.MODID);

    public static final DeferredBlock<Block> GEAR_SMITHING_TABLE = BLOCKS.register("gear_smithing_table",
            () -> new ArmorySmithingTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(2.5F).sound(SoundType.METAL)));

    public static final DeferredItem<Item> GEAR_SMITHING_TABLE_ITEM =
            ArmoryItems.ITEMS.registerItem("gear_smithing_table",
                    p -> new BlockItem(GEAR_SMITHING_TABLE.get(), p));

    public static final DeferredBlock<Block> SALVAGER = BLOCKS.register("salvager",
            () -> new ArmorySalvagerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(2.5F).sound(SoundType.METAL)));

    public static final DeferredItem<Item> SALVAGER_ITEM =
            ArmoryItems.ITEMS.registerItem("salvager", p -> new BlockItem(SALVAGER.get(), p));

    public static final DeferredBlock<Block> REFORGING_TABLE = BLOCKS.register("reforging_table",
            () -> new ArmoryReforgingTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(2.5F).sound(SoundType.METAL)));

    public static final DeferredItem<Item> REFORGING_TABLE_ITEM =
            ArmoryItems.ITEMS.registerItem("reforging_table",
                    p -> new BlockItem(REFORGING_TABLE.get(), p));

    public static final DeferredBlock<Block> METAL_PRESS = BLOCKS.register("metal_press",
            () -> new ArmoryMetalPressBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL)));

    public static final DeferredItem<Item> METAL_PRESS_ITEM =
            ArmoryItems.ITEMS.registerItem("metal_press", p -> new BlockItem(METAL_PRESS.get(), p));

    public static final DeferredBlock<Block> STONE_ANVIL = BLOCKS.register("stone_anvil",
            () -> new ArmoryStoneAnvilBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(2.0F).sound(SoundType.STONE)));

    public static final DeferredItem<Item> STONE_ANVIL_ITEM =
            ArmoryItems.ITEMS.registerItem("stone_anvil", p -> new BlockItem(STONE_ANVIL.get(), p));

    public static final DeferredBlock<Block> ALLOY_FORGE = BLOCKS.register("alloy_forge",
            () -> new ArmoryAlloyForgeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.FIRE).strength(3.5F).sound(SoundType.METAL)));

    public static final DeferredItem<Item> ALLOY_FORGE_ITEM =
            ArmoryItems.ITEMS.registerItem("alloy_forge", p -> new BlockItem(ALLOY_FORGE.get(), p));

    public static final DeferredBlock<Block> MATERIAL_GRADER = BLOCKS.register("material_grader",
            () -> new ArmoryMaterialGraderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.STONE)));

    public static final DeferredItem<Item> MATERIAL_GRADER_ITEM =
            ArmoryItems.ITEMS.registerItem("material_grader",
                    p -> new BlockItem(MATERIAL_GRADER.get(), p));

    public static final DeferredBlock<Block> STARLIGHT_CHARGER = BLOCKS.register("starlight_charger",
            () -> new ArmoryStarlightChargerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0F).sound(SoundType.AMETHYST)
                    .lightLevel(state -> 5)));

    public static final DeferredItem<Item> STARLIGHT_CHARGER_ITEM =
            ArmoryItems.ITEMS.registerItem("starlight_charger",
                    p -> new BlockItem(STARLIGHT_CHARGER.get(), p));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
