package com.elysium.colonies.registry;

import com.elysium.colonies.ElysiumColonies;
import com.elysium.colonies.port.PortAnchorBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The three blocks a spaceport is made of.
 *
 * <h2>Why three and not one</h2>
 *
 * Because the building is the point. A spaceport could have been a hut with a
 * sign on it — MineColonies has a hundred of those and adding a hundred and
 * first would have taught a player nothing. What this module wanted was a
 * piece of infrastructure: something you can see from the next valley, that
 * took a real quantity of iron to raise, and that somebody could cut in half
 * while you were away.
 *
 * <p>That needs an anchor you talk to, a tether that is the same block
 * repeated until you are sick of placing it, and a counterweight that says
 * where the top is. Every one of them is load-bearing; none of them does
 * anything on its own. A tether block in a wall is decoration.
 *
 * <h2>Why they are plain blocks</h2>
 *
 * No block entities. A column of two hundred ticking blocks is two hundred
 * things a server does every tick for a building that acts once a day, and the
 * whole structure is read by walking it at the moment somebody asks. The only
 * state a port has lives in {@code port/PortRegistry}, which is one entry per
 * colony rather than one per block.
 */
public final class PortBlocks {

    private PortBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumColonies.MODID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumColonies.MODID);

    private static BlockBehaviour.Properties metal(MapColor colour) {
        return BlockBehaviour.Properties.of()
                .mapColor(colour)
                .strength(5.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    /**
     * The bottom of the elevator, and the only part of it that talks.
     *
     * Lit, because a player who has built a two-hundred-block column should be
     * able to find the bottom of it at night.
     */
    public static final DeferredBlock<Block> ELEVATOR_ANCHOR = BLOCKS.register(
            "elevator_anchor",
            () -> new PortAnchorBlock(metal(MapColor.COLOR_GRAY)
                    .lightLevel(state -> 7)));

    /**
     * The column. Eighty of these at the very least, and that is the price.
     *
     * A pillar rather than a cube so that the cable reads as running along it:
     * laid on its side it is a gantry, which is the one decorative use of it
     * this module is happy to allow.
     */
    public static final DeferredBlock<Block> ELEVATOR_TETHER = BLOCKS.register(
            "elevator_tether",
            () -> new RotatedPillarBlock(metal(MapColor.METAL)));

    /** The top, and the reason the column stays up. */
    public static final DeferredBlock<Block> ELEVATOR_COUNTERWEIGHT = BLOCKS.register(
            "elevator_counterweight",
            () -> new Block(metal(MapColor.DEEPSLATE).strength(7.0F, 9.0F)));

    // --- the flare ------------------------------------------------------
    //
    // None of these three does anything on its own, and that is the point: a
    // spaceport that worked because you placed the right block would be a
    // machine, and this is a building. They are what the legs, the rings and
    // the platform in the blueprint are made of, and a port with legs around
    // it ships more because the legs are what carry the column.

    /** A leg. Pale cast pier, the thing the whole flare is built out of. */
    public static final DeferredBlock<Block> ELEVATOR_PYLON = BLOCKS.register(
            "elevator_pylon",
            () -> new Block(metal(MapColor.QUARTZ).strength(4.0F, 6.0F)));

    /** A ring that ties the legs together, and is lit, because it is high up. */
    public static final DeferredBlock<Block> ELEVATOR_COLLAR = BLOCKS.register(
            "elevator_collar",
            () -> new Block(metal(MapColor.COLOR_GRAY).lightLevel(state -> 5)));

    /** The platform it all stands on. */
    public static final DeferredBlock<Block> ELEVATOR_DECK = BLOCKS.register(
            "elevator_deck",
            () -> new Block(metal(MapColor.COLOR_LIGHT_GRAY)));

    public static final DeferredItem<Item> ELEVATOR_ANCHOR_ITEM =
            ITEMS.register("elevator_anchor",
                    () -> new BlockItem(ELEVATOR_ANCHOR.get(), new Item.Properties()));
    public static final DeferredItem<Item> ELEVATOR_TETHER_ITEM =
            ITEMS.register("elevator_tether",
                    () -> new BlockItem(ELEVATOR_TETHER.get(), new Item.Properties()));
    public static final DeferredItem<Item> ELEVATOR_COUNTERWEIGHT_ITEM =
            ITEMS.register("elevator_counterweight",
                    () -> new BlockItem(ELEVATOR_COUNTERWEIGHT.get(),
                            new Item.Properties()));

    public static final DeferredItem<Item> ELEVATOR_PYLON_ITEM =
            ITEMS.register("elevator_pylon",
                    () -> new BlockItem(ELEVATOR_PYLON.get(), new Item.Properties()));
    public static final DeferredItem<Item> ELEVATOR_COLLAR_ITEM =
            ITEMS.register("elevator_collar",
                    () -> new BlockItem(ELEVATOR_COLLAR.get(), new Item.Properties()));
    public static final DeferredItem<Item> ELEVATOR_DECK_ITEM =
            ITEMS.register("elevator_deck",
                    () -> new BlockItem(ELEVATOR_DECK.get(), new Item.Properties()));

    /**
     * Where they appear in creative.
     *
     * The functional tab rather than a tab of this module's own. Three blocks
     * do not justify a tab, and a player looking for a spaceport is looking
     * beside the other things that do something rather than under a mod's
     * name.
     */
    @EventBusSubscriber(modid = ElysiumColonies.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Tabs {

        private Tabs() {
        }

        @SubscribeEvent
        public static void onTab(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() != CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                return;
            }
            event.accept(ELEVATOR_ANCHOR_ITEM.get());
            event.accept(ELEVATOR_TETHER_ITEM.get());
            event.accept(ELEVATOR_COUNTERWEIGHT_ITEM.get());
            event.accept(ELEVATOR_PYLON_ITEM.get());
            event.accept(ELEVATOR_COLLAR_ITEM.get());
            event.accept(ELEVATOR_DECK_ITEM.get());
        }
    }
}
