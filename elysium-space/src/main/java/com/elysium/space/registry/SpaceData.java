package com.elysium.space.registry;

import com.elysium.space.ElysiumSpace;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Saved data the machines need: a chunk's remaining oil, and a tank item's contents. */
public final class SpaceData {

    private SpaceData() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ElysiumSpace.MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ElysiumSpace.MODID);

    /** Oil left under a chunk; -1 until the chunk is first pumped (then its deposit is rolled). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> CHUNK_OIL =
            ATTACHMENTS.register("chunk_oil", () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).build());

    /** The fluid a Fluid Tank held when it was broken, so it keeps it. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> TANK_FLUID =
            COMPONENTS.register("tank_fluid", () -> DataComponentType.<SimpleFluidContent>builder()
                    .persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC).build());
}
