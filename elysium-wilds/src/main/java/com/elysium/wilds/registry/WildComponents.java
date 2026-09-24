package com.elysium.wilds.registry;

import com.elysium.wilds.ElysiumWilds;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** What a compass remembers: one place, and what is there. */
public final class WildComponents {

    private WildComponents() {
    }

    /**
     * Where the compass last found something.
     *
     * @param where which world, and where in it
     * @param biome what it went looking for
     */
    public record Bearing(GlobalPos where, String biome) {

        public static final Codec<Bearing> CODEC = RecordCodecBuilder.create(one -> one.group(
                GlobalPos.CODEC.fieldOf("where").forGetter(Bearing::where),
                Codec.STRING.fieldOf("biome").forGetter(Bearing::biome)
        ).apply(one, Bearing::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Bearing> STREAM =
                StreamCodec.composite(
                        GlobalPos.STREAM_CODEC, Bearing::where,
                        ByteBufCodecs.STRING_UTF8, Bearing::biome,
                        Bearing::new);
    }

    public static final DeferredRegister<DataComponentType<?>> TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ElysiumWilds.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Bearing>>
            BEARING = TYPES.register("bearing", () ->
            DataComponentType.<Bearing>builder()
                    .persistent(Bearing.CODEC)
                    .networkSynchronized(Bearing.STREAM)
                    .build());
}
