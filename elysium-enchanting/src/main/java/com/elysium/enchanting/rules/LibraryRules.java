package com.elysium.enchanting.rules;

/**
 * The Scriptorium's arithmetic: books in, one better book out.
 *
 * <h2>What a library is for</h2>
 *
 * A player who plays for a month accumulates a chest of enchanted books they
 * will never use — Bane of Arthropods II, four copies of Respiration I, a
 * Knockback that came off a zombie. None of it is worth anything and none of
 * it is worth throwing away either, so it sits there.
 *
 * <p>This turns that chest into a currency. Filing a book credits pages
 * against that one enchantment; a plain book and enough pages buys a copy back
 * at whatever level the pages cover. Nothing crosses between enchantments —
 * a shelf of Respiration will never become Mending — because a library that
 * converts anything into anything is a shop, and a shop makes every book in
 * the world the same book.
 *
 * <h2>Four make the next one up</h2>
 *
 * A book of level L is worth {@code 2^(L-1)} pages and a copy costs twice what
 * it is worth. So four books of a level buy exactly one of the level above,
 * and two books of a level buy one back at the same level. That factor of two
 * is the whole economy: without it the library duplicates books, and with it
 * the library is a way to spend the ones you were never going to use.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class LibraryRules {

    private LibraryRules() {
    }

    /** What a copy costs over what filing one is worth. */
    public static final int COPY_TAX = 2;

    /** Experience levels charged per level of the book copied. */
    public static final int EXPERIENCE_PER_LEVEL = 3;

    /** The highest level this arithmetic is asked to work at. */
    public static final int MAX_LEVEL = 30;

    /** Pages one shelf can hold for one enchantment. */
    public static final int SHELF_CAPACITY = 1024;

    /** And the great one, which is why it is worth building. */
    public static final int GRAND_CAPACITY = 65536;

    /**
     * What a book of this level is worth, in pages.
     *
     * Doubling, which is the shape every player already has an intuition for
     * from the anvil: two of a thing make one of the next thing.
     */
    public static int pages(int level) {
        int held = Math.max(1, Math.min(MAX_LEVEL, level));
        long value = 1L << (held - 1);
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    /** What a copy at this level costs, in pages. */
    public static int cost(int level) {
        long value = (long) pages(level) * COPY_TAX;
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    /** And in experience levels, which is what stops a full shelf being a vending machine. */
    public static int experience(int level) {
        return Math.max(1, EXPERIENCE_PER_LEVEL * Math.max(1, level));
    }

    /**
     * The best copy these pages will buy, held to what the enchantment allows.
     *
     * @param ceiling the enchantment's own maximum level
     * @return a level, or 0 when there are not even enough pages for the first
     */
    public static int highest(int stored, int ceiling) {
        int best = 0;
        for (int level = 1; level <= Math.max(1, Math.min(MAX_LEVEL, ceiling)); level++) {
            if (cost(level) <= stored) {
                best = level;
            }
        }
        return best;
    }

    /** Whether there is room to file a book of this level without losing any of it. */
    public static boolean room(int stored, int level, int capacity) {
        return (long) stored + pages(level) <= capacity;
    }

    /**
     * The shelf after a book is filed.
     *
     * Never past the capacity: a shelf that silently swallowed the overflow
     * would take a player's best book and give them most of it, which is worse
     * than refusing.
     */
    public static int deposit(int stored, int level, int capacity) {
        if (!room(stored, level, capacity)) {
            return stored;
        }
        return stored + pages(level);
    }

    /** The shelf after a copy is taken, or the shelf unchanged when it could not be. */
    public static int withdraw(int stored, int level) {
        int price = cost(level);
        return price > stored ? stored : stored - price;
    }
}
