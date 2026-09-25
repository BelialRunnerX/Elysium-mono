package com.elysium.agronomy.registry;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.rules.KitchenRules;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The kitchen itself: four stations, a cabinet per wood, a crate per crop.
 *
 * <p>The stations come out of {@link KitchenRules#STATIONS}, so a fifth kind
 * of cooking is a line in the rules and a texture. The cabinets and crates are
 * the catalogue half, and they are two loops rather than twenty-one
 * declarations for the same reason everything else in this module is.
 */
public final class KitchenBlocks {

    private KitchenBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumAgronomy.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumAgronomy.MODID);

    /** The woods the game gives us to make a cabinet out of. */
    public static final List<String> WOODS = List.of("oak", "spruce", "birch",
            "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo",
            "crimson", "warped");

    /** What is worth keeping a crate of. */
    public static final List<String> CRATED = List.of("cabbage", "onion", "tomato",
            "rice", "barley", "chili", "garlic", "ginger", "carrot", "potato");

    /** The block each station is. */
    public static final Map<String, String> STATION_BLOCK = Map.of(
            KitchenRules.POT, "cooking_pot",
            KitchenRules.SKILLET, "skillet",
            KitchenRules.BOARD, "cutting_board",
            KitchenRules.OVEN, "stone_oven");

    private static BlockBehaviour.Properties timber() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(2.0F);
    }

    private static BlockBehaviour.Properties iron() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(3.5F)
                .noOcclusion();
    }

    public static final Map<String, DeferredBlock<Block>> STATIONS = registerStations();
    public static final Map<String, DeferredBlock<Block>> CABINETS = registerCabinets();
    public static final Map<String, DeferredBlock<Block>> CRATES = registerCrates();

    /**
     * A crop block and a seed for every crop the kitchen asks for.
     *
     * <p>Eight ingredients had a sprite, a crate and a place in a dozen
     * recipes and no way on earth to obtain one. These are the plants.
     *
     * <p>{@link net.minecraft.world.level.block.CropBlock} and not a class of
     * our own, because the game's own crop is trampled, bone-mealed, grown by
     * random tick and harvested by every automation mod there is, and a crop
     * of our own would have to be all of that again to be a crop rather than
     * a block that changes colour.
     */
    public static final Map<String, DeferredBlock<Block>> CROPS = registerCrops();

    private static Map<String, DeferredBlock<Block>> registerCrops() {
        Map<String, DeferredBlock<Block>> made = new java.util.LinkedHashMap<>();
        for (com.elysium.agronomy.rules.CropRules.Crop crop
                : com.elysium.agronomy.rules.CropRules.CROPS) {
            DeferredBlock<Block> block = BLOCKS.register(crop.id() + "_crop",
                    () -> new com.elysium.agronomy.block.KitchenCropBlock(
                            BlockBehaviour.Properties.ofFullCopy(
                                            net.minecraft.world.level.block.Blocks.WHEAT)
                                    .noCollission()
                                    .randomTicks()
                                    .instabreak()
                                    .sound(net.minecraft.world.level.block.SoundType.CROP),
                            crop.id()));
            made.put(crop.id(), block);
        }
        if (made.size()
                != com.elysium.agronomy.rules.CropRules.CROPS.size()) {
            throw new IllegalStateException("there are "
                    + com.elysium.agronomy.rules.CropRules.CROPS.size()
                    + " crop(s) and " + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    /** The colony, which grows mushrooms rather than being one. */
    public static final DeferredBlock<Block> COLONY = BLOCKS.register(
            com.elysium.agronomy.rules.CropRules.COLONY,
            () -> new Block(BlockBehaviour.Properties
                    .ofFullCopy(net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM_BLOCK)
                    .randomTicks()));

    private static DeferredBlock<Block> block(String name,
                                              BlockBehaviour.Properties properties) {
        DeferredBlock<Block> made = BLOCKS.register(name, () -> new Block(properties));
        ITEMS.register(name, () -> new BlockItem(made.get(), new Item.Properties()));
        return made;
    }

    private static Map<String, DeferredBlock<Block>> registerStations() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (String station : KitchenRules.STATIONS) {
            String name = STATION_BLOCK.get(station);
            if (name == null) {
                throw new IllegalStateException("meals are cooked on a " + station
                        + " and there is no block for one");
            }
            made.put(station, block(name,
                    KitchenRules.BOARD.equals(station) ? timber() : iron()));
        }
        if (made.size() != KitchenRules.STATIONS.size()) {
            throw new IllegalStateException("there are "
                    + KitchenRules.STATIONS.size() + " stations and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }

    private static Map<String, DeferredBlock<Block>> registerCabinets() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (String wood : WOODS) {
            made.put(wood, block(wood + "_cabinet", timber()));
        }
        if (made.size() != WOODS.size()) {
            throw new IllegalStateException("there are " + WOODS.size()
                    + " woods and " + made.size() + " cabinet(s) were registered");
        }
        return Map.copyOf(made);
    }

    private static Map<String, DeferredBlock<Block>> registerCrates() {
        Map<String, DeferredBlock<Block>> made = new LinkedHashMap<>();
        for (String crop : CRATED) {
            made.put(crop, block(crop + "_crate", timber()));
        }
        if (made.size() != CRATED.size()) {
            throw new IllegalStateException("there are " + CRATED.size()
                    + " crated crops and " + made.size()
                    + " crate(s) were registered");
        }
        return Map.copyOf(made);
    }
}
