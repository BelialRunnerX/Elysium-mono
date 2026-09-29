package com.elysium.lib.character;

import java.util.ArrayList;
import java.util.List;

/**
 * What every ordinary action pays in character experience.
 *
 * <h2>Why every action</h2>
 *
 * Dylan, 28 September 2026: "make sure that all game actions (mining digging,
 * combat etc give skill point xp)". The character level is the currency the
 * stat points and the Ledger Web's points are bought with, and until now only
 * kills and ore paid into it (the standing handler's 4, 14 and 6, which stay
 * where they are). A player who spends an evening building, farming and
 * fishing should come out of it a level or two on, the same as one who spent
 * it hunting; a level track that only the fighter can climb is a track for one
 * calling.
 *
 * <h2>Why a cap</h2>
 *
 * Every one of these can be farmed: a dirt block placed and broken pays twice a
 * second for ever. So every source has a ceiling per window of
 * {@link #WINDOW_TICKS}: beyond it the action still happens and pays nothing
 * until the window turns. The caps are set so that ordinary play never meets
 * them and a machine does at once. Nothing here clamps the level itself; the
 * curve ({@link ElysiumLevelling}) already grows.
 *
 * <p>The JDK and nothing else, so the harness can hold every number.
 */
public final class ElysiumXpRules {

    private ElysiumXpRules() {
    }

    /** What was done. Kills and ore are the standing handler's and are not here. */
    public enum Source {
        /** A hard block broken: stone and anything harder. */
        MINE,
        /** A soft block broken: dirt, sand, gravel, wood. */
        DIG,
        /** A block placed. */
        BUILD,
        /** Something taken out of a crafting grid or bench. */
        CRAFT,
        /** Something taken out of a furnace. */
        SMELT,
        /** Damage dealt to something that is not a player: a point per {@link #DAMAGE_PER_POINT}. */
        STRIKE,
        /** A ripe crop broken. */
        HARVEST,
        /** Something fished up. */
        FISH,
        /** An animal tamed. */
        TAME,
        /** Two animals bred. */
        BREED,
        /** A biome stood in for the first time this session. */
        EXPLORE,
        /** A trade made with a villager. */
        TRADE,
        /** Something enchanted at a table. */
        ENCHANT,
        /** Something eaten. */
        EAT
    }

    /** The window a source's cap is counted over: a minute. */
    public static final int WINDOW_TICKS = 1200;

    /** The destroy speed at and above which breaking a block is mining rather than digging. */
    public static final float HARD_STONE = 1.5F;

    /** Damage dealt per point of experience from a strike. */
    public static final float DAMAGE_PER_POINT = 4.0F;

    /** What one of a source pays. */
    public static int base(Source source) {
        return switch (source) {
            case MINE -> 3;
            case DIG -> 1;
            case BUILD -> 1;
            case CRAFT -> 2;
            case SMELT -> 2;
            case STRIKE -> 1;
            case HARVEST -> 2;
            case FISH -> 5;
            case TAME -> 15;
            case BREED -> 6;
            case EXPLORE -> 12;
            case TRADE -> 5;
            case ENCHANT -> 8;
            case EAT -> 1;
        };
    }

    /** The most a source pays in one window. Nought would mean no ceiling, and none has none. */
    public static int cap(Source source) {
        return switch (source) {
            case MINE -> 60;
            case DIG -> 30;
            case BUILD -> 30;
            case CRAFT -> 40;
            case SMELT -> 40;
            case STRIKE -> 60;
            case HARVEST -> 40;
            case FISH -> 30;
            case TAME -> 30;
            case BREED -> 36;
            case EXPLORE -> 36;
            case TRADE -> 30;
            case ENCHANT -> 24;
            case EAT -> 6;
        };
    }

    /** Which source a broken block is: a ripe crop is a harvest, hard is mining, soft is digging. */
    public static Source forBreak(float destroySpeed, boolean ripeCrop) {
        if (ripeCrop) {
            return Source.HARVEST;
        }
        return destroySpeed >= HARD_STONE ? Source.MINE : Source.DIG;
    }

    /** Points for damage dealt: none under a heart, then one per {@link #DAMAGE_PER_POINT}. */
    public static int forDamage(float dealt) {
        if (dealt < 1.0F) {
            return 0;
        }
        return Math.max(1, (int) Math.floor(dealt / DAMAGE_PER_POINT));
    }

    /** How much of a wanted award the cap lets through, given what the window has already paid. */
    public static int allow(int spentInWindow, int cap, int wanted) {
        if (cap <= 0) {
            return Math.max(0, wanted);
        }
        return Math.max(0, Math.min(wanted, cap - spentInWindow));
    }

    /** Everything every source could pay in one window, if every cap were met. */
    public static int mostPerWindow() {
        int most = 0;
        for (Source source : Source.values()) {
            most += cap(source);
        }
        return most;
    }

    // ------------------------------------------------------------------
    // Faults
    // ------------------------------------------------------------------

    public static List<String> faults() {
        List<String> faults = new ArrayList<>();
        for (Source source : Source.values()) {
            if (base(source) < 1) {
                faults.add(source + " pays nothing, so it is not a source");
            }
            if (cap(source) < base(source)) {
                faults.add(source + " is capped below what one of it pays, so the first one is refused");
            }
            if (cap(source) > 120) {
                faults.add(source + " may pay " + cap(source) + " a minute, which is a level a minute");
            }
        }
        if (base(Source.MINE) <= base(Source.DIG)) {
            faults.add("digging dirt pays as much as mining stone");
        }
        if (base(Source.EXPLORE) < 2 * base(Source.MINE)) {
            faults.add("a new biome is worth less than two blocks of stone");
        }
        if (base(Source.TAME) < base(Source.BREED)) {
            faults.add("taming pays less than breeding, and taming is the rarer act");
        }
        if (WINDOW_TICKS < 600) {
            faults.add("a window under half a minute is a cap a player meets by playing");
        }
        if (HARD_STONE <= 0.5F || HARD_STONE > 3.0F) {
            faults.add("the line between digging and mining is off the blocks: dirt is 0.5, stone 1.5, obsidian 50");
        }
        if (mostPerWindow() > 600) {
            faults.add("every cap met at once pays " + mostPerWindow() + " a minute, which is several levels a minute");
        }
        if (allow(0, 0, 5) != 5 || allow(58, 60, 5) != 2 || allow(60, 60, 5) != 0 || allow(0, 60, 5) != 5) {
            faults.add("the cap arithmetic does not let through what is left and no more");
        }
        if (forBreak(0.5F, false) != Source.DIG || forBreak(1.5F, false) != Source.MINE
                || forBreak(50.0F, true) != Source.HARVEST) {
            faults.add("dirt is not digging, stone is not mining, or a ripe crop is not a harvest");
        }
        if (forDamage(0.5F) != 0 || forDamage(4.0F) != 1 || forDamage(9.0F) != 2 || forDamage(1.0F) != 1) {
            faults.add("damage does not pay a point per " + DAMAGE_PER_POINT + " and nothing under a heart");
        }
        return faults;
    }
}
