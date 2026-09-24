package com.elysium.lib.medical;

/**
 * The four lasting injuries, each named by what it costs.
 *
 * <h2>One kind, one attribute</h2>
 *
 * Every kind takes something different, so that two wounds are always two
 * problems rather than one problem twice, and so a player can tell from how
 * they feel which one they are carrying: slow legs are a fracture, a short
 * health bar is blood loss. The attribute each one touches is decided in
 * {@link ElysiumMedical}, which is allowed to know about Minecraft; this file
 * only holds the numbers, so a harness can read them without a game.
 *
 * <h2>Why each cap is where it is</h2>
 *
 * The cap is the whole defence against a death spiral — the wound that makes
 * you easier to wound. At every kind's cap a player is weakened, not crippled:
 * fourteen hearts of twenty, seventy per cent of their pace. The harness walks
 * every combination of caps and holds that line.
 *
 * <p>Imports nothing, and must not: it is in {@code EXECUTED_PURE}.
 */
public enum ElysiumWoundKind {

    /** Weapons and arrows. Blood loss: two points of maximum health a wound. */
    LACERATION("laceration", 5.0F, 20, -2.0D, 3),

    /** Falls. A leg that will not take weight: ten per cent of pace a wound. */
    FRACTURE("fracture", 6.0F, 30, -0.10D, 3),

    /** Fire and lava. Blistered hands: twelve per cent of mining speed a wound. */
    BURN("burn", 4.0F, 25, -0.12D, 3),

    /** Explosions. A ringing head: ten per cent of attack speed a wound. */
    CONCUSSION("concussion", 6.0F, 20, -0.10D, 2);

    private final String id;
    private final float threshold;
    private final int healMinutes;
    private final double penalty;
    private final int stack;

    ElysiumWoundKind(String id, float threshold, int healMinutes,
                     double penalty, int stack) {
        this.id = id;
        this.threshold = threshold;
        this.healMinutes = healMinutes;
        this.penalty = penalty;
        this.stack = stack;
    }

    public String id() {
        return id;
    }

    /** The landed damage at which this kind can begin. Below it, nothing lasts. */
    public float threshold() {
        return threshold;
    }

    /** How long one wound of this kind takes to heal on its own, in game minutes. */
    public int healMinutes() {
        return healMinutes;
    }

    /** What one wound costs its attribute. Negative: a wound only ever takes. */
    public double penalty() {
        return penalty;
    }

    /** The most wounds of this kind that count at once. */
    public int stack() {
        return stack;
    }

    /** The kind with this id, or null. For the codec, which must not throw on an old save. */
    public static ElysiumWoundKind byId(String id) {
        for (ElysiumWoundKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
