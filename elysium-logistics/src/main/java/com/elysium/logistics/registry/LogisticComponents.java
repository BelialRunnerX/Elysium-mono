package com.elysium.logistics.registry;

import com.elysium.logistics.ElysiumLogistics;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * What a carried pack keeps on itself.
 *
 * <p>Three components and no more: what is inside, what is fitted, and which
 * page it opens on next. Everything else a pack <em>is</em> — how much it
 * holds, what it may take — is worked out from those by the rules every time
 * it is asked, so there is no stored number that can disagree with them.
 */
public final class LogisticComponents {

    private LogisticComponents() {
    }

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE,
                    ElysiumLogistics.MODID);

    /** What is inside, slot by slot, up to the pack's capacity. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>>
            CONTENTS = COMPONENTS.registerComponentType("pack_contents",
                    builder -> builder.persistent(ItemContainerContents.CODEC)
                            .networkSynchronized(ItemContainerContents.STREAM_CODEC)
                            .cacheEncoding());

    /** The parts fitted, by name, in the order they went in. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>>
            FITTED = COMPONENTS.registerComponentType("pack_parts",
                    builder -> builder.persistent(Codec.STRING.listOf())
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8
                                    .apply(ByteBufCodecs.list())));

    /** The page the pack opens on next, for a pack bigger than one screen. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>>
            PAGE = COMPONENTS.registerComponentType("pack_page",
                    builder -> builder.persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT));
}
