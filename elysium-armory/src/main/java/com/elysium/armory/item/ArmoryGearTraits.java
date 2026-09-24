package com.elysium.armory.item;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The traits a built piece ended up with, and at what level, as they ride on
 * the stack.
 *
 * <p>Resolved once, when the piece is built or recomputed, for the same reason
 * its numbers are: traits come from datapack data the client does not have, and
 * a tooltip has to be able to say "Sharp III" without it.
 */
public record ArmoryGearTraits(Map<String, Integer> levels) {

    public static final ArmoryGearTraits NONE = new ArmoryGearTraits(Map.of());

    public static final Codec<ArmoryGearTraits> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(ArmoryGearTraits::new, ArmoryGearTraits::levels);

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmoryGearTraits> STREAM_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, String, Integer, Map<String, Integer>>map(
                            LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT)
                    .map(ArmoryGearTraits::new, ArmoryGearTraits::levels);

    public ArmoryGearTraits {
        levels = Map.copyOf(levels);
    }

    public int level(String trait) {
        Integer level = levels.get(trait);
        return level == null ? 0 : level;
    }

    public boolean has(String trait) {
        return level(trait) > 0;
    }

    public boolean isEmpty() {
        return levels.isEmpty();
    }
}
