package com.elysium.transit.registry;

import com.elysium.transit.ElysiumTransit;
import com.elysium.transit.block.WaystoneBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One block entity: the stone that holds a charge. */
public final class TransitBlockEntities {

    private TransitBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumTransit.MODID);

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<WaystoneBlock.WaystoneBlockEntity>>
            WAYSTONE = TYPES.register("waystone", () -> BlockEntityType.Builder.of(
                    WaystoneBlock.WaystoneBlockEntity::new,
                    TransitBlocks.WAYSTONE.get())
            .build(null));
}
