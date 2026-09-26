package com.elysium.colonies.charter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * What a settlement builds next, and when it is ready to want it.
 *
 * <h2>Why this is data</h2>
 *
 * Because what a town ought to build is an opinion, and a pack's opinion is
 * better than this module's. MineColonies ships dozens of buildings; a
 * medieval pack, a space pack and a hardcore pack would all order them
 * differently and none of them would thank a mod that hard-coded one order.
 *
 * <h2>The shape of a plan</h2>
 *
 * A list, in the order a town wants things, each with the population at which
 * it becomes worth wanting. A town builds the first thing on the list it does
 * not have and is big enough for. That is deliberately simple — a dependency
 * graph would be more expressive and much harder for a pack author to get
 * right, and the ordering already encodes the dependencies that matter.
 *
 * <p>The builder comes first in the shipped plan for a reason that is not
 * taste: a colony with no builder cannot raise anything at all, including the
 * builder.
 */
public record SettlementPlan(List<SettlementPlan.Want> wants) {

    /**
     * One thing a town would like.
     *
     * @param building the building's registry id
     * @param from     the population at which it becomes worth having
     */
    public record Want(String building, int from) {

        public static final Codec<Want> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf("building").forGetter(Want::building),
                        Codec.INT.optionalFieldOf("from", 0).forGetter(Want::from)
                ).apply(instance, Want::new));
    }

    public static final Codec<SettlementPlan> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Want.CODEC.listOf().fieldOf("wants").forGetter(SettlementPlan::wants)
            ).apply(instance, SettlementPlan::new));

    /** A plan with nothing in it: a town that never founds anything by itself. */
    public static final SettlementPlan EMPTY = new SettlementPlan(List.of());

    /**
     * The first thing this town wants and has not got.
     *
     * @param citizens how many people live there
     * @param has      answers whether the town already has a building
     * @return the building id, or null when the town wants nothing yet
     */
    /** What a town wants before it has anybody: the buildings listed from zero. */
    public java.util.List<String> firstWants() {
        java.util.List<String> first = new java.util.ArrayList<>();
        for (Want want : wants) {
            if (want.from() == 0) {
                first.add(want.building());
            }
        }
        return first;
    }

    public String next(int citizens, java.util.function.Predicate<String> has) {
        for (Want want : wants) {
            if (citizens < want.from()) {
                continue;
            }
            if (!has.test(want.building())) {
                return want.building();
            }
        }
        return null;
    }
}
