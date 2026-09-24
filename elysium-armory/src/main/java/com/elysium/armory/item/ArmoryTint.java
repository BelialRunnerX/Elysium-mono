package com.elysium.armory.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The two colours a sprite is tinted with: the working metal, and the handle.
 *
 * <p>Sprites are drawn in grey. A material's colour is datapack data the client
 * does not have, so it is resolved when the piece is built and carried with it
 * — which also means a piece keeps its colour after the pack that defined the
 * material is gone.
 */
public record ArmoryTint(int primary, int secondary) {

    public static final ArmoryTint PLAIN = new ArmoryTint(0xFFFFFF, 0xFFFFFF);

    public static final Codec<ArmoryTint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("primary").forGetter(ArmoryTint::primary),
            Codec.INT.optionalFieldOf("secondary", 0xFFFFFF).forGetter(ArmoryTint::secondary)
    ).apply(instance, ArmoryTint::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmoryTint> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ArmoryTint::primary,
            ByteBufCodecs.INT, ArmoryTint::secondary,
            ArmoryTint::new);

    /** The colour for one layer of the sprite; anything past the second is untinted. */
    public int layer(int index) {
        return switch (index) {
            case 0 -> primary;
            case 1 -> secondary;
            default -> 0xFFFFFF;
        };
    }
}
