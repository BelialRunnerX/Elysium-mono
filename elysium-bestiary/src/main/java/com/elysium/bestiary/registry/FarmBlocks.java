package com.elysium.bestiary.registry;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.block.FarmBlock;
import com.elysium.bestiary.block.FarmBlockEntity;
import com.elysium.bestiary.rules.FarmRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nine machines, registered from the rules rather than typed.
 *
 * <p>A tenth is a line in {@link FarmRules#MACHINES} and nothing here changes —
 * including its item, which is walked from the same list. A register that names
 * its blocks by hand is a register with the ninth missing, and the symptom is
 * one recipe nobody can make anything with.
 */
public final class FarmBlocks {

    private FarmBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ElysiumBestiary.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumBestiary.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumBestiary.MODID);

    /** Every machine, in the order the rules name them. */
    public static final Map<String, DeferredBlock<FarmBlock>> ALL = register();

    /** And the item that places each one. */
    public static final Map<String, DeferredItem<Item>> ITEMS_OF = items();

    private static Map<String, DeferredBlock<FarmBlock>> register() {
        Map<String, DeferredBlock<FarmBlock>> made = new LinkedHashMap<>();
        for (FarmRules.Machine one : FarmRules.MACHINES) {
            made.put(one.id(), BLOCKS.register(one.id(),
                    () -> new FarmBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                            .sound(SoundType.METAL)
                            .strength(3.0F, 6.0F)
                            .noOcclusion(), one.id())));
        }
        if (made.size() != FarmRules.MACHINES.size()) {
            throw new IllegalStateException("the rules name "
                    + FarmRules.MACHINES.size() + " machine(s) and " + made.size()
                    + " block(s) were registered");
        }
        return Map.copyOf(made);
    }

    private static Map<String, DeferredItem<Item>> items() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (FarmRules.Machine one : FarmRules.MACHINES) {
            made.put(one.id(), ITEMS.register(one.id(),
                    () -> new BlockItem(ALL.get(one.id()).get(), new Item.Properties())));
        }
        if (made.size() != FarmRules.MACHINES.size()) {
            throw new IllegalStateException("the rules name "
                    + FarmRules.MACHINES.size() + " machine(s) and " + made.size()
                    + " item(s) were registered");
        }
        return Map.copyOf(made);
    }

    /**
     * One block entity type for all nine.
     *
     * <p>Built from the whole register rather than a list of names, so that a
     * tenth machine is valid the moment its row exists — a type that names
     * eight of nine blocks throws the first time somebody places the ninth.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FarmBlockEntity>>
            MACHINE_ENTITY = BLOCK_ENTITIES.register("machine",
            () -> BlockEntityType.Builder.of(
                            (where, state) -> new FarmBlockEntity(where, state,
                                    nameOf(state.getBlock())),
                            ALL.values().stream().map(DeferredBlock::get)
                                    .toArray(Block[]::new))
                    .build(null));

    /** Which machine a block is, asked of the block rather than of a list here. */
    public static String nameOf(Block block) {
        return block instanceof FarmBlock farm ? farm.machine().id()
                : FarmRules.MACHINES.get(0).id();
    }
}
