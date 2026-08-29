package com.elysium.lib;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The few things the engine needs from a content mod that do not deserve a
 * registry of their own.
 *
 * Each is optional. A library with no content mod installed runs: standing
 * still accrues from combat, stats still apply, characters still level. It
 * simply has no ore to mine and no codex to hand out, because nothing has
 * offered any.
 */
public final class ElysiumHooks {

    private ElysiumHooks() {
    }

    // ------------------------------------------------------------------
    // Ore
    // ------------------------------------------------------------------

    /**
     * Ore is registered as a <em>supplier</em> and resolved on first query.
     *
     * <h2>Why not the Block</h2>
     *
     * A content mod registers its ore from its constructor, because that is
     * where registration has to happen. But a {@code DeferredHolder} has no
     * value during construction — the registry events have not fired — so
     * {@code holder.get()} there throws:
     *
     * <pre>NullPointerException: Trying to access unbound value:
     * ResourceKey[minecraft:block / elysium:neutronium_ore]</pre>
     *
     * which is precisely how the second launch of this project died. Taking a
     * supplier lets the call stay in the constructor, where it belongs, and
     * moves the resolution to the first time anybody breaks a block — long
     * after every registry is full.
     *
     * <h2>Resolve once, then freeze</h2>
     *
     * The same shape as {@link com.elysium.lib.registry.ElysiumRegistry}: the
     * pending suppliers are drained into a set on first read, and registering
     * after that throws rather than silently arriving too late to count.
     * {@link #isOre} runs on every block break, so it has to be a set lookup
     * and not a walk over suppliers.
     */
    private static final List<Supplier<Block>> PENDING_ORES = new ArrayList<>();
    private static final List<Supplier<Block>> PENDING_RICH = new ArrayList<>();
    private static final Set<Block> ORES = new LinkedHashSet<>();
    private static final Set<Block> RICH_ORES = new LinkedHashSet<>();
    private static boolean oresResolved;

    /**
     * Registers a block as Elysium ore: breaking it earns character experience
     * and Suspicion, and a passive that doubles ore applies to it.
     *
     * Pass the {@code DeferredHolder} itself — it already <em>is</em> a
     * {@code Supplier} — rather than calling {@code get()} on it. The field
     * javadoc above says what happens if you do not.
     *
     * @param rich pays the higher Suspicion rate — for the scarce ore a
     *             faction would actually notice you taking
     */
    public static void registerOre(Supplier<Block> block, boolean rich) {
        if (oresResolved) {
            throw new IllegalStateException("ore registered after the ore set was first read. "
                    + "Register ore from your mod's constructor, not from a later event.");
        }
        PENDING_ORES.add(block);
        if (rich) {
            PENDING_RICH.add(block);
        }
    }

    private static void resolveOres() {
        if (oresResolved) {
            return;
        }
        oresResolved = true;
        for (Supplier<Block> pending : PENDING_ORES) {
            Block block = pending.get();
            if (block != null) {
                ORES.add(block);
            }
        }
        for (Supplier<Block> pending : PENDING_RICH) {
            Block block = pending.get();
            if (block != null) {
                RICH_ORES.add(block);
            }
        }
    }

    public static boolean isOre(Block block) {
        resolveOres();
        return ORES.contains(block);
    }

    public static boolean isRichOre(Block block) {
        resolveOres();
        return RICH_ORES.contains(block);
    }

    // ------------------------------------------------------------------
    // The character codex
    // ------------------------------------------------------------------

    private static Supplier<ItemStack> codex;

    /**
     * The item handed to a player on their first join, which reopens the
     * character sheet. Optional — without one, the sheet is reachable through
     * {@code /elysium sheet} and nothing else changes.
     */
    public static void setCodex(Supplier<ItemStack> supplier) {
        codex = supplier;
    }

    /** @return the codex stack, or empty when no content mod supplied one */
    public static ItemStack codex() {
        return codex == null ? ItemStack.EMPTY : codex.get();
    }

    public static boolean hasCodex() {
        return codex != null;
    }
}
