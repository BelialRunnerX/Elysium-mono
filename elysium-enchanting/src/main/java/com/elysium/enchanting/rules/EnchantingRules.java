package com.elysium.enchanting.rules;

import java.util.function.DoubleSupplier;

/**
 * What an enchanting table can offer, and how it decides.
 *
 * <h2>Three numbers instead of one</h2>
 *
 * Vanilla's table has one input — how many bookshelves are around it — and one
 * output shape: three offers whose levels rise with the shelves. Everything a
 * player can do about an enchantment is therefore "build more shelves", and
 * the ceiling arrives about an hour into a world and never moves again.
 *
 * <p>This asks three questions instead, and different blocks answer different
 * ones:
 *
 * <ul>
 *   <li><b>Eterna</b> — how much power the table has. It is what the offered
 *       levels are made of, and it is the one a player spends.</li>
 *   <li><b>Quanta</b> — how uncertain the result is. High quanta is a wider
 *       spread in both directions: more of the good outcomes and more of the
 *       bad ones.</li>
 *   <li><b>Arcana</b> — how rare what is offered may be. It does not make an
 *       enchantment stronger; it changes which ones are on the table at
 *       all.</li>
 * </ul>
 *
 * A room built for eterna and a room built for arcana are different rooms, and
 * that is the whole design: the thing a player builds around the table is a
 * choice rather than a count.
 *
 * <h2>Rectification</h2>
 *
 * Quanta's spread is symmetric by default, which makes high quanta a gamble
 * nobody sensible takes. Rectification tilts it: it is the share of the
 * downside that is removed, so a table with full rectification keeps all the
 * upside of its uncertainty and none of the risk. It is deliberately the
 * hardest of the four to raise.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class EnchantingRules {

    private EnchantingRules() {
    }

    /**
     * The most eterna a player's tables may hold, until they raise it.
     *
     * This is a ceiling, not a floor. A room's own cap is the best cap any
     * block in it declares — a shelf of books says fifteen, a block of
     * netherite says a good deal more — and this is the separate limit the
     * player brings with them. The table holds whichever of the two is
     * smaller, so a room built past what its owner can carry is a room built
     * for later, and the {@code max_eterna} attribute is the thing that makes
     * later arrive.
     */
    public static final float BASE_MAX_ETERNA = 50.0F;

    /** What a level of eterna is worth as an enchanting level. */
    public static final float ETERNA_PER_LEVEL = 1.0F;

    /** How many offers a table shows. Three, as vanilla does, because the shape is familiar. */
    public static final int OFFERS = 3;

    /** The share of the table's power the weakest offer gets. */
    public static final float WEAKEST_SHARE = 0.34F;

    /** And the middle one. The strongest gets all of it. */
    public static final float MIDDLE_SHARE = 0.67F;

    /**
     * What a table's four numbers come to.
     *
     * A record rather than four parameters everywhere: they travel together,
     * they are read together, and a method that took them in the wrong order
     * would compile.
     */
    public record Stats(float eterna, float quanta, float arcana, float rectification) {

        public static final Stats NONE = new Stats(0.0F, 0.0F, 0.0F, 0.0F);

        public Stats {
            // Clamped on the way in rather than at every read: a shelf with a
            // negative eterna in a datapack is a mistake, and the table should
            // behave rather than go backwards.
            eterna = Math.max(0.0F, eterna);
            quanta = Math.max(0.0F, quanta);
            arcana = Math.max(0.0F, arcana);
            rectification = Math.max(0.0F, Math.min(1.0F, rectification));
        }

        public Stats plus(Stats other) {
            return new Stats(eterna + other.eterna, quanta + other.quanta,
                    arcana + other.arcana, rectification + other.rectification);
        }

        /** The same stats with eterna held to a cap, which is what a table actually offers. */
        public Stats cappedAt(float maxEterna) {
            return new Stats(Math.min(eterna, Math.max(0.0F, maxEterna)),
                    quanta, arcana, rectification);
        }

        public boolean isEmpty() {
            return eterna <= 0.0F;
        }
    }

    /**
     * What a world tier adds to what a player may hold.
     *
     * The one place this module joins the rest of Elysium. A world tier is the
     * library's measure of how far a player has come, and it is what every
     * other system in this mod scales against; an enchanting ceiling that
     * ignored it would be the one number in the game that stops mattering.
     *
     * <p>Flat per tier rather than a share, because it is a ceiling and not a
     * chance: shares are for percentile effects, and "how much power may this
     * table hold" is an amount. Uncapped, like everything else a player
     * raises.
     */
    public static final float ETERNA_PER_TIER = 6.0F;

    public static float tierBonus(int worldTier) {
        return ETERNA_PER_TIER * Math.max(0, worldTier);
    }

    /**
     * How much eterna a table may actually hold.
     *
     * Two caps, and they are different questions. The room's is what the
     * blocks around the table declare and is a property of the build: a
     * bookshelf room holds fifteen however good the player is. The player's is
     * {@link #BASE_MAX_ETERNA} and whatever their gear has raised it to, and
     * is a property of the player: a master enchanter standing in a bookshelf
     * room still has a bookshelf room.
     *
     * <p>So the table holds the smaller of the two, which is what makes both
     * worth improving and neither sufficient on its own.
     *
     * @param roomCap   the best cap any block around the table declares
     * @param playerMax what this player may hold at all
     */
    public static float ceiling(float roomCap, float playerMax) {
        return Math.min(Math.max(0.0F, roomCap), Math.max(0.0F, playerMax));
    }

    /**
     * The enchanting level one of the three offers is worth.
     *
     * @param slot 0 for the weakest, 2 for the strongest
     */
    public static int offerLevel(Stats stats, int slot) {
        float share = switch (Math.max(0, Math.min(OFFERS - 1, slot))) {
            case 0 -> WEAKEST_SHARE;
            case 1 -> MIDDLE_SHARE;
            default -> 1.0F;
        };
        return Math.max(1, Math.round(stats.eterna() * share * ETERNA_PER_LEVEL));
    }

    /**
     * What the table charges for an offer, in experience levels.
     *
     * The slot's number, as vanilla does — one, two, three — so the cheapest
     * offer is always affordable and the price a player reads is the price
     * they have always read.
     */
    public static int cost(int slot) {
        return Math.max(1, Math.min(OFFERS, slot + 1));
    }

    /**
     * The level an offer actually enchants at, once quanta has had its say.
     *
     * The spread is quanta either side of the offered level, less whatever
     * rectification removes from the downside. So a table with no quanta
     * enchants at exactly what it said, and one with a lot is a gamble the
     * player chose to set up.
     *
     * @param rolls a source of numbers in [0, 1)
     */
    public static int rolledLevel(Stats stats, int offered, DoubleSupplier rolls) {
        if (stats.quanta() <= 0.0F) {
            return Math.max(1, offered);
        }
        double spread = stats.quanta();
        double roll = rolls.getAsDouble() * 2.0D - 1.0D;    // -1 .. 1
        if (roll < 0.0D) {
            roll *= 1.0D - stats.rectification();           // less of the downside
        }
        return Math.max(1, (int) Math.round(offered + roll * spread));
    }

    /**
     * How much rarity arcana buys, as a share.
     *
     * Through {@code x / (x + b)} like every other percentile thing in this
     * mod, so no amount of arcana makes a rare enchantment certain — it only
     * ever makes it possible.
     */
    public static final double ARCANA_PACE = 25.0D;

    public static double rarityShare(Stats stats) {
        double at = Math.max(0.0D, stats.arcana());
        return at / (at + ARCANA_PACE);
    }

    /**
     * Whether an enchantment of this rarity weight is on the table at all.
     *
     * A weight of one is the rarest thing a pack can declare; a weight of ten
     * is ordinary. An ordinary enchantment is always available; a rare one
     * needs arcana to have been built for.
     */
    public static boolean available(Stats stats, int weight) {
        if (weight >= 10) {
            return true;
        }
        double needed = (10 - Math.max(1, weight)) / 10.0D;
        return rarityShare(stats) >= needed * 0.9D;
    }

    /**
     * How much of an offer a player is told before they buy it.
     *
     * One clue at the weakest offer and one more for each slot up, which is
     * the honest shape: the cheap offer is the mystery and the expensive one
     * is the decision.
     */
    public static int clues(Stats stats, int slot) {
        return Math.max(1, Math.min(OFFERS, slot + 1));
    }
}
