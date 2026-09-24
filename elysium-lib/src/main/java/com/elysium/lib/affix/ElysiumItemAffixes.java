package com.elysium.lib.affix;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The affixes rolled onto one item, and how strongly.
 *
 * A map rather than a list so that an affix cannot land on the same item
 * twice, and so that re-rolling one replaces it rather than stacking a second
 * copy of the same bonus the player cannot see.
 */
public record ElysiumItemAffixes(Map<String, Integer> levels) {

    public static final ElysiumItemAffixes NONE = new ElysiumItemAffixes(Map.of());

    public static final Codec<ElysiumItemAffixes> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .xmap(ElysiumItemAffixes::new, ElysiumItemAffixes::levels);

    public static final StreamCodec<RegistryFriendlyByteBuf, ElysiumItemAffixes> STREAM_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, String, Integer, Map<String, Integer>>map(
                            LinkedHashMap::new, ByteBufCodecs.STRING_UTF8,
                            ByteBufCodecs.VAR_INT)
                    .map(ElysiumItemAffixes::new, ElysiumItemAffixes::levels);

    public boolean isEmpty() {
        return levels.isEmpty();
    }

    public int level(String affix) {
        return levels.getOrDefault(affix, 0);
    }

    /** This set with one more affix on it, replacing any roll it already had. */
    public ElysiumItemAffixes with(String affix, int level) {
        Map<String, Integer> out = new LinkedHashMap<>(levels);
        out.put(affix, level);
        return new ElysiumItemAffixes(out);
    }

    public ElysiumItemAffixes without(String affix) {
        if (!levels.containsKey(affix)) {
            return this;
        }
        Map<String, Integer> out = new LinkedHashMap<>(levels);
        out.remove(affix);
        return new ElysiumItemAffixes(out);
    }
}
