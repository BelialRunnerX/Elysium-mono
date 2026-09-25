package com.elysium.arsenal.registry;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.block.entity.SiteBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The one block entity the digging needs.
 *
 * <p>Built from every site in the roster rather than from the three named
 * here: a fourth site added to the rules and left out of this list is a block
 * that drops its block entity the moment a player looks at it, which shows up
 * as a crash with nothing in it about sites.
 */
public final class DigBlockEntities {

    private DigBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,
                    ElysiumArsenal.MODID);

    public static final DeferredHolder<BlockEntityType<?>,
            BlockEntityType<SiteBlockEntity>> SITE = TYPES.register("site",
            () -> BlockEntityType.Builder.of(SiteBlockEntity::new, everySite())
                    .build(null));

    /** Every block a site block entity may sit in. */
    public static Block[] everySite() {
        return DigBlocks.SITES.values().stream()
                .map(DeferredHolder::get).toArray(Block[]::new);
    }
}
