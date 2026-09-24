package com.elysium.space.world;

/**
 * The jet suit, as arithmetic (Stellaris's JetSuit: normal and hover modes).
 * Pure; run by WorldHarness.
 *
 * <p>Normal: holding jump climbs, {@link #LIFT} a tick up to {@link #MAX_CLIMB}.
 * Hover: the same climb, and when jump is let go the fall is held to
 * {@link #HOVER_SINK} (Stellaris's -0.05). Both burn fuel from the chestplate's
 * own tank; a suit with no fuel is just a space suit.
 */
public final class JetRules {

    private JetRules() {
    }

    public enum Mode { OFF, NORMAL, HOVER }

    public static final int TANK = 2000;
    public static final int THRUST_MB_PER_TICK = 1;
    /** Hovering burns one mB every this many ticks. */
    public static final int HOVER_TICKS_PER_MB = 2;
    public static final double LIFT = 0.1D;
    public static final double MAX_CLIMB = 0.6D;
    public static final double HOVER_SINK = -0.05D;

    public static Mode next(Mode mode) {
        return Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
    }

    /** The vertical speed after one tick. */
    public static double vertical(Mode mode, double vy, boolean thrusting, boolean onGround, int fuel) {
        if (mode == Mode.OFF || fuel <= 0) {
            return vy;
        }
        if (thrusting) {
            return Math.min(MAX_CLIMB, vy + LIFT);
        }
        if (mode == Mode.HOVER && !onGround) {
            return Math.max(HOVER_SINK, vy);
        }
        return vy;
    }

    /** Fuel burnt this tick. */
    public static int burn(Mode mode, boolean thrusting, boolean onGround, int fuel, long tick) {
        if (mode == Mode.OFF || fuel <= 0) {
            return 0;
        }
        if (thrusting) {
            return Math.min(fuel, THRUST_MB_PER_TICK);
        }
        if (mode == Mode.HOVER && !onGround && tick % HOVER_TICKS_PER_MB == 0) {
            return 1;
        }
        return 0;
    }

    /** Whether the suit is holding the wearer up, which cancels fall damage. */
    public static boolean flying(Mode mode, boolean thrusting, boolean onGround, int fuel) {
        return mode != Mode.OFF && fuel > 0 && !onGround && (thrusting || mode == Mode.HOVER);
    }
}
