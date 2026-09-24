package com.elysium.colonies.rules;

/**
 * What a colony is worth, in Elysium's units.
 *
 * <h2>The problem this solves</h2>
 *
 * Two progressions in one world that do not know about each other. A player
 * who has spent forty hours building a town of thirty citizens is, to Elysium,
 * whatever their character level happens to be — possibly nothing. A colony
 * that has grown into a small city is raided by the same barbarians that raided
 * it when it was four huts, however far its owner has otherwise come.
 *
 * <p>Neither system is wrong; they simply have no sentence in common. This is
 * that sentence, and it is deliberately short: a colony's <em>size</em> says
 * how far its owner has come, and how far its owner has come says how hard its
 * raiders hit.
 *
 * <h2>Why size and not level</h2>
 *
 * A colony has no single number called its level. It has citizens, buildings,
 * research and a raid level, and of those the citizen count is the one a
 * player feels, can see on the town hall, and cannot inflate without doing the
 * work — a hut with no bed houses nobody.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ColonyRules {

    private ColonyRules() {
    }

    /**
     * Citizens per world tier.
     *
     * Five, which is roughly a tier per real evening of colony work and lines
     * up with the ten character levels a tier otherwise costs: a mayor is not
     * meant to out-run an adventurer, only to stop being invisible to the
     * system the adventurer lives in.
     */
    public static final int CITIZENS_PER_TIER = 5;

    /** The most tiers a colony alone will ever be worth. */
    public static final int MAX_COLONY_TIER = 6;

    /**
     * The world floor a colony of this size justifies.
     *
     * A floor rather than a tier: it is combined with the player's own level by
     * {@code ElysiumWorldTier}, and the higher of the two wins. So a colony
     * lifts a player who has not adventured much and does nothing at all for
     * one who has already gone further, which is the correct answer to "does
     * my town make me stronger" — no, it makes you *noticed*.
     */
    public static int worldFloor(int citizens) {
        int earned = Math.max(0, citizens) / CITIZENS_PER_TIER;
        return Math.min(MAX_COLONY_TIER, earned);
    }

    /**
     * What several settlements together are worth: the largest, never the sum.
     *
     * A player may hold a stake in as many towns as they like — the host has
     * never said otherwise — and the obvious reading of that is to add them
     * up. It is also the wrong one. Ten hamlets of four are ten times as easy
     * to build as one town of forty and would be worth eight tiers instead of
     * one; the frontier would fill with abandoned four-hut farms whose only
     * purpose was arithmetic.
     *
     * <p>So it is the largest, which says the true thing: what you are worth
     * is the greatest thing you have built, and joining a friend's capital
     * counts for exactly as much as building it would have.
     */
    public static int worldFloor(java.util.Collection<Integer> citizenCounts) {
        int best = 0;
        for (Integer citizens : citizenCounts) {
            if (citizens != null) {
                best = Math.max(best, worldFloor(citizens));
            }
        }
        return best;
    }

    /** Suspicion below which a settlement's owner cannot fall. */
    public static final int NOTICE_PER_CITIZEN = 2;

    /** And the most a settlement alone can put on your file. */
    public static final int MAX_NOTICE = 60;

    /**
     * How noticed the owner of a colony this size is.
     *
     * The Empire's files do not record that you built a town and then forget
     * it. This is the same statement the library already makes about a claim
     * beacon — you cannot be quietly forgotten while standing on something
     * with your name on it — made about the other way of putting your name on
     * a place.
     */
    public static int noticeFloor(int citizens) {
        return Math.min(MAX_NOTICE, Math.max(0, citizens) * NOTICE_PER_CITIZEN);
    }

    /** The same reading for the Empire's files: the most conspicuous, not the total. */
    public static int noticeFloor(java.util.Collection<Integer> citizenCounts) {
        int worst = 0;
        for (Integer citizens : citizenCounts) {
            if (citizens != null) {
                worst = Math.max(worst, noticeFloor(citizens));
            }
        }
        return worst;
    }

    /**
     * The tier a colony's raiders are built at.
     *
     * The owner's world tier, which already includes whatever the colony
     * itself contributed through {@link #worldFloor}. Not the raid level:
     * MineColonies already scales its raids against the colony's defences, and
     * multiplying one difficulty curve by another produces a number neither
     * mod's author would recognise.
     *
     * <p>Capped, and the cap is the point. A raid is a scheduled event a
     * colony is meant to survive, not a boss fight; promoting its raiders at
     * an uncapped tier would eventually send something that walks through a
     * town of level-five guards.
     */
    public static final int MAX_RAIDER_TIER = 4;

    public static int raiderTier(int ownerTier) {
        return Math.max(0, Math.min(MAX_RAIDER_TIER, ownerTier));
    }

    /**
     * Whether a raid at this tier should promote anything at all.
     *
     * Below the first tier nothing is promoted, so a new player's first raid
     * is MineColonies' raid exactly as its author wrote it. The two systems
     * meet when there is something to meet about.
     */
    public static boolean promotes(int ownerTier) {
        return raiderTier(ownerTier) > 0;
    }
}
