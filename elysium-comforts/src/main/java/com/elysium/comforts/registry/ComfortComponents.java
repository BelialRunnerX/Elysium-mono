package com.elysium.comforts.registry;

import com.elysium.comforts.ElysiumComforts;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One component: a villager's own saved form, kept whole.
 *
 * <p>Whole rather than picked apart into fields, because a villager is a
 * ledger and a ledger copied field by field is a ledger with a field missing.
 * The game already knows how to write one down; this keeps what it wrote.
 */
public final class ComfortComponents {

    private ComfortComponents() {
    }

    public static final DeferredRegister<DataComponentType<?>> TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE,
                    ElysiumComforts.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>>
            TRADER = TYPES.register("trader", () ->
            DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
                    .build());
}
