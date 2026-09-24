package com.elysium.space.world;

/**
 * The air in a space suit, as arithmetic. Pure; run by WorldHarness.
 *
 * <p>A full space suit is a Void set, so it seals vacuum and radiation through
 * the library's matching-set rule. That seal is only as good as the air in the
 * suit: the chestplate holds {@link #CAPACITY} mB (Stellaris's 3000). Outside
 * in vacuum it drains {@link #DRAIN_PER_SECOND} a second (ten minutes from
 * full). Standing in a room an Oxygen Distributor fills refills it
 * {@link #REFILL_PER_SECOND} a second, and an Oxygen Canister adds
 * {@link #CANISTER}. Empty and still outside, the wearer suffocates.
 */
public final class SuitAir {

    private SuitAir() {
    }

    public static final int CAPACITY = 3000;
    public static final int DRAIN_PER_SECOND = 5;
    public static final int REFILL_PER_SECOND = 100;
    public static final int CANISTER = 1000;
    public static final float SUFFOCATION_DAMAGE = 2.0F;

    /** One second later. {@code exposed}: vacuum here and no filled room. */
    public static int step(int air, boolean exposed, boolean inFilledRoom) {
        int now = clamp(air);
        if (inFilledRoom) {
            return clamp(now + REFILL_PER_SECOND);
        }
        return exposed ? clamp(now - DRAIN_PER_SECOND) : now;
    }

    public static boolean suffocating(int air, boolean exposed, boolean inFilledRoom) {
        return exposed && !inFilledRoom && clamp(air) == 0;
    }

    /** Adding a canister; returns the new amount, never past capacity. */
    public static int addCanister(int air) {
        return clamp(clamp(air) + CANISTER);
    }

    public static int clamp(int air) {
        return air < 0 ? 0 : Math.min(CAPACITY, air);
    }
}
