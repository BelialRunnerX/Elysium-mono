package com.elysium.dungeons;

import com.elysium.dungeons.block.RiftFrameBlock;
import com.elysium.dungeons.block.RiftPortalBlock;
import com.elysium.dungeons.item.RiftKeyItem;
import com.elysium.dungeons.room.DungeonRooms;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Elysium Dungeons — a portal to a dungeon that is never the same twice.
 *
 * <h2>The loop</h2>
 *
 * Build a frame of Rift Frame blocks, strike it with a Rift Key, and step
 * through. Inside is a dungeon assembled from tiles: filler rooms, one or more
 * loot rooms and a boss room, every one of them a sealed box connected only by
 * doorways. Walk back out through the return rift and, once the last player has
 * left, that dungeon is finished forever — step through the same portal again
 * and a completely new one is built.
 *
 * <h2>How "completely new" is actually achieved</h2>
 *
 * Not by deleting anything. Each dungeon is built at its own cell in a void
 * dimension, a thousand blocks from its neighbours, from a seed derived from a
 * counter that never repeats. The old dungeon is simply left behind in chunks
 * nothing will load again. See {@code DungeonInstances} for why every other
 * approach is worse.
 *
 * <h2>What this mod adds to the library</h2>
 *
 * A room registry, so another mod can register a filler, a loot room or a boss
 * without touching this one. Everything else it consumes: loot comes from the
 * library's reward providers, so a pack with elysium-core installed finds
 * Elysium gear in dungeon chests and a pack without it finds whatever else is
 * registered.
 */
@Mod(ElysiumDungeons.MODID)
public class ElysiumDungeons {

    public static final String MODID = "elysiumdungeons";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** The dungeon dimension. Declared in data/elysiumdungeons/dimension/dungeon.json. */
    public static final ResourceKey<Level> DUNGEON_LEVEL = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(MODID, "dungeon"));

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.BLOCK, MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.ITEM, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(
                    net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB, MODID);

    private static final List<DeferredHolder<Item, Item>> TAB_ORDER = new ArrayList<>();

    // ------------------------------------------------------------------
    // Blocks
    // ------------------------------------------------------------------

    /**
     * The frame. Ordinary to mine, but it is what a portal is anchored to, so
     * breaking one closes the rift.
     */
    public static final DeferredHolder<Block, Block> RIFT_FRAME = BLOCKS.register("rift_frame",
            () -> new RiftFrameBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.0F, 12.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));

    /**
     * The portal surface itself.
     *
     * Not obtainable and not craftable on purpose: a portal block a player can
     * carry is a portal with no frame, no anchor and no instance, and every
     * assumption in this mod about "the portal you came from" stops holding.
     */
    public static final DeferredHolder<Block, Block> RIFT_PORTAL = BLOCKS.register("rift_portal",
            () -> new RiftPortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .noCollission()
                    .noLootTable()
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 11)
                    .pushReaction(PushReaction.BLOCK)));

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    public static final DeferredHolder<Item, Item> RIFT_FRAME_ITEM =
            register("rift_frame", () -> new BlockItem(RIFT_FRAME.get(), new Item.Properties()));

    /**
     * Strike a frame with this to open it.
     *
     * Damageable rather than consumed. A key that vanished on every use would
     * make the fifth dungeon a crafting chore rather than a decision, and the
     * durability still keeps a rift from being free.
     */
    public static final DeferredHolder<Item, Item> RIFT_KEY =
            register("rift_key", () -> new RiftKeyItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(64)
                    .rarity(Rarity.RARE)));

    // ------------------------------------------------------------------
    // Creative tab
    // ------------------------------------------------------------------

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_TABS.register("elysiumdungeons", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumdungeons"))
                    .icon(() -> new ItemStack(RIFT_KEY.get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredHolder<Item, Item> entry : TAB_ORDER) {
                            output.accept(entry.get());
                        }
                    })
                    .build());

    // ------------------------------------------------------------------

    public ElysiumDungeons(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        // Touching the class runs its registrations. Without this the rooms
        // exist in the source and not in the game, and every dungeon comes out
        // as a set of empty boxes with a warning per room.
        DungeonRooms.bootstrap();

        LOGGER.info("Elysium Dungeons ready: {} room types",
                com.elysium.dungeons.room.DungeonRoom.REGISTRY.size());
    }

    private static DeferredHolder<Item, Item> register(String name,
                                                       Supplier<? extends Item> supplier) {
        DeferredHolder<Item, Item> holder = ITEMS.register(name, supplier);
        TAB_ORDER.add(holder);
        return holder;
    }
}
