package com.elysium.core.compat;

import com.elysium.core.Elysium;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * What a converted piece remembers about where it came from.
 *
 * <h2>Why one id is worth a whole component</h2>
 *
 * Most of a conversion is recoverable by derivation: a Silent Gear helmet made
 * of Neutronium maps back to the Elysium Neutronium Helmet without being told.
 * Most, not all. Elysium has shapes Silent Gear has no counterpart for — a
 * Broadaxe becomes a Silent Gear axe, and an axe is what comes back.
 *
 * The alternatives were to refuse to convert those shapes at all, or to accept
 * that some pieces change on the round trip. The first takes a feature away
 * from exactly the players who built the rarest gear; the second is a
 * conversion that quietly destroys something. Remembering the item id costs one
 * string and makes the round trip exact for every shape.
 *
 * <h3>Why it is not read blindly</h3>
 *
 * A stamped id is a hint, not an instruction. The bridge checks that the
 * remembered item still exists and is still Elysium gear before using it —
 * otherwise a piece converted in a pack that has since removed that item would
 * come back as air.
 */
public final class ConvergenceComponents {

    private ConvergenceComponents() {
    }

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Elysium.MODID);

    /** The Elysium item this piece was before it was converted away. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>>
            ORIGIN = COMPONENTS.registerComponentType("convergence_origin",
                    builder -> builder
                            .persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC));

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
