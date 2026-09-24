package com.elysium.lib.medical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One wound: what kind, and how many ticks of natural recovery it still needs.
 */
public record ElysiumWound(ElysiumWoundKind kind, int remaining) {

    /**
     * Read by id. An id this version does not know — a kind removed, a save
     * from a newer build — is an error for that one wound, which the injury
     * codec drops, rather than an exception that loses the player's data.
     */
    private static final Codec<ElysiumWoundKind> KIND = Codec.STRING.comapFlatMap(
            id -> {
                ElysiumWoundKind kind = ElysiumWoundKind.byId(id);
                return kind == null
                        ? DataResult.error(() -> "unknown wound " + id)
                        : DataResult.success(kind);
            },
            ElysiumWoundKind::id);

    public static final Codec<ElysiumWound> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    KIND.fieldOf("kind").forGetter(ElysiumWound::kind),
                    Codec.INT.fieldOf("remaining").forGetter(ElysiumWound::remaining)
            ).apply(instance, ElysiumWound::new));

    /** A wound just taken. */
    public static ElysiumWound fresh(ElysiumWoundKind kind) {
        return new ElysiumWound(kind, ElysiumMedicine.fullTicks(kind));
    }
}
