package com.elysium.armory.item;

import com.elysium.armory.property.ArmoryMaterialScience;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What has been done to a part beyond making it (SPEC_CLONE P8.1).
 *
 * <p>Three marks, and they are separate on purpose: how well it was worked,
 * what it was left out in, and whether the bench that made it was a bench at
 * all. A part can be all three, and what they come to together is one
 * multiplier on everything it contributes.
 *
 * @param grade       {@code -1} Crude, {@code 0} plain, up to
 *                    {@link ArmoryMaterialScience#MAX_GRADE}
 * @param starcharged whether it has taken the charge, which happens once
 * @param synergy     the multiplier its materials earned by getting along —
 *                    1 for a part nobody forged
 */
public record ArmoryMarks(int grade, boolean starcharged, double synergy) {

    public static final ArmoryMarks PLAIN =
            new ArmoryMarks(ArmoryMaterialScience.UNGRADED, false, 1.0D);

    public static final Codec<ArmoryMarks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("grade", ArmoryMaterialScience.UNGRADED)
                    .forGetter(ArmoryMarks::grade),
            Codec.BOOL.optionalFieldOf("starcharged", false).forGetter(ArmoryMarks::starcharged),
            Codec.DOUBLE.optionalFieldOf("synergy", 1.0D).forGetter(ArmoryMarks::synergy)
    ).apply(instance, ArmoryMarks::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmoryMarks> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ArmoryMarks::grade,
                    ByteBufCodecs.BOOL, ArmoryMarks::starcharged,
                    ByteBufCodecs.DOUBLE, ArmoryMarks::synergy,
                    ArmoryMarks::new);

    public ArmoryMarks {
        // A grade from a hand-edited save is read as the nearest one the rules
        // were written for, and a synergy below one is no synergy: a mark can
        // make a part better and must never quietly make it worse than the
        // materials it is made of.
        grade = ArmoryMaterialScience.clampGrade(grade);
        synergy = Math.max(1.0D, synergy);
    }

    /** Everything these marks come to: one multiplier on what the part contributes. */
    public double factor() {
        return ArmoryMaterialScience.factor(grade, starcharged) * synergy;
    }

    public boolean isPlain() {
        return grade == ArmoryMaterialScience.UNGRADED && !starcharged && synergy <= 1.0D;
    }

    public ArmoryMarks withGrade(int newGrade) {
        return new ArmoryMarks(newGrade, starcharged, synergy);
    }

    public ArmoryMarks charged() {
        return new ArmoryMarks(grade, true, synergy);
    }

    public ArmoryMarks withSynergy(double newSynergy) {
        return new ArmoryMarks(grade, starcharged, newSynergy);
    }
}
