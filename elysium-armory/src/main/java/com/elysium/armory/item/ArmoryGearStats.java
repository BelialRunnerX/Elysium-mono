package com.elysium.armory.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A piece of gear's computed properties, as they ride on the item.
 *
 * <p>Materials and parts are server-side data; the client has neither, and a
 * tooltip has to say what the sword does. So the numbers are computed once on
 * the server, stored here and synced with the stack — which is also what keeps
 * a sword in a chest working after the pack that defined its material is
 * removed.
 *
 * <p>{@code step} is the wear step the numbers were computed at
 * ({@link com.elysium.armory.property.ArmoryVanilla#damageFactor}), so a piece
 * knows when its "as damaged" numbers have gone stale.
 */
public record ArmoryGearStats(Map<String, Double> values, String tier, int step) {

    public static final ArmoryGearStats EMPTY = new ArmoryGearStats(Map.of(), "", -1);

    public static final Codec<ArmoryGearStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("values").forGetter(ArmoryGearStats::values),
            Codec.STRING.optionalFieldOf("tier", "").forGetter(ArmoryGearStats::tier),
            Codec.INT.optionalFieldOf("step", 0).forGetter(ArmoryGearStats::step)
    ).apply(instance, ArmoryGearStats::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmoryGearStats> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.DOUBLE),
            ArmoryGearStats::values,
            ByteBufCodecs.STRING_UTF8, ArmoryGearStats::tier,
            ByteBufCodecs.VAR_INT, ArmoryGearStats::step,
            ArmoryGearStats::new);

    public ArmoryGearStats {
        values = Map.copyOf(values);
    }

    public double get(String property, double fallback) {
        Double value = values.get(property);
        return value == null ? fallback : value;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
