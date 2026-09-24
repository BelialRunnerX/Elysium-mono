package com.elysium.lib.set;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Who is wearing the piece the engine is currently asking about.
 *
 * <h2>Why this exists</h2>
 *
 * The effect engine asks one question — "what does this stack do" — and that
 * is the right question for a trait, an affix, a gem and a rune, all of which
 * are properties of the stack alone. A set bonus is not: it depends on what
 * else is on the same body, and a stack has no way of naming its wearer.
 *
 * <p>The alternative was to widen the engine's question for every attachment
 * so that one of them could answer it, which would put an entity nobody else
 * needs into every call. This is narrower: the event layer, which does know
 * whose tick or whose blow it is, says so for the duration of the work, and
 * the set source is the only thing that reads it.
 *
 * <p>Per thread, because the server ticks on one and the client renders
 * tooltips on another, and a tooltip must never be told about the wrong body.
 * Absent by default: a piece in a chest has no wearer and grants nothing,
 * which is right — a set bonus is for wearing.
 */
public final class ElysiumGearSetWearers {

    private ElysiumGearSetWearers() {
    }

    private static final ThreadLocal<LivingEntity> CURRENT = new ThreadLocal<>();

    /** Runs a piece of work with a wearer in scope, and takes it back out afterwards. */
    public static void with(LivingEntity wearer, Runnable work) {
        LivingEntity previous = CURRENT.get();
        CURRENT.set(wearer);
        try {
            work.run();
        } finally {
            // Restored rather than cleared: one handler may call another, and
            // the inner one finishing is not the outer one finishing.
            CURRENT.set(previous);
        }
    }

    /** The wearer in scope, whether or not it is holding this stack. */
    public static LivingEntity wearerOf(ItemStack stack) {
        return CURRENT.get();
    }

    /** Puts a wearer in scope until {@link #clear()}. For a caller that is not a block. */
    public static void set(LivingEntity wearer) {
        CURRENT.set(wearer);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
