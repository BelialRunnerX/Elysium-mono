package com.elysium.colonies.event;

import com.elysium.colonies.colony.Colonies;
import com.elysium.colonies.rules.ColonyRules;
import com.elysium.lib.standing.ElysiumStanding;

/**
 * A town is conspicuous.
 *
 * <h2>What this says</h2>
 *
 * The library already refuses to let a player's Suspicion decay below a floor
 * set by what they have planted a claim beacon on — you cannot be quietly
 * forgotten while standing on something with your name on it. A colony is the
 * other way of putting your name on a place, and a rather louder one: a beacon
 * is a block, and a town of thirty is thirty people who live somewhere with
 * your name on it.
 *
 * <p>So this registers a second source of that floor. The library takes the
 * highest of all its sources, so a player with both a beacon and a town is
 * filed under whichever is more conspicuous rather than the sum — the same
 * reading the claim floor itself uses across dimensions, for the same reason.
 *
 * <h2>Why a registration and not a listener</h2>
 *
 * Because the floor is a question the library asks, not an event that happens.
 * A listener would have to guess when to push a number in and would be wrong
 * the first time a player joined with a town already built. Installed from the
 * mod constructor, before any player exists.
 */
public final class ColonyStanding {

    private ColonyStanding() {
    }

    /** Whether the source has been installed, so the validator and a test can ask. */
    private static boolean installed;

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        ElysiumStanding.addFloorSource(player -> {
            java.util.List<Colonies.Settlement> towns = Colonies.memberOf(player);
            if (towns.isEmpty()) {
                return 0;
            }
            java.util.List<Integer> sizes = new java.util.ArrayList<>();
            for (Colonies.Settlement town : towns) {
                sizes.add(town.citizens());
            }
            return ColonyRules.noticeFloor(sizes);
        });
    }

    public static boolean installed() {
        return installed;
    }
}
