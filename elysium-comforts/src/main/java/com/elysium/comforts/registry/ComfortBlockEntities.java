package com.elysium.comforts.registry;

import com.elysium.comforts.ElysiumComforts;
import com.elysium.comforts.block.GraveBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One block entity: what is in the grave. */
public final class ComfortBlockEntities {

    private ComfortBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumComforts.MODID);

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<GraveBlock.GraveBlockEntity>>
            GRAVE = TYPES.register("grave", () -> BlockEntityType.Builder.of(
                    GraveBlock.GraveBlockEntity::new, ComfortBlocks.GRAVE.get())
            .build(null));
}
