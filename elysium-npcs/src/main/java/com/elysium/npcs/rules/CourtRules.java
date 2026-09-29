package com.elysium.npcs.rules;

import java.util.Arrays;

/**
 * The court's decisions, with nothing of Minecraft in them.
 *
 * <h2>Why the court has a rules class at all</h2>
 *
 * Who arrives used to be decided inside EnvoyScheduler, against a live player,
 * where nothing but a running game could ask it a question. It had a hole
 * nobody saw for that reason: a player with Favor between 1 and 24 and less
 * Suspicion was visited by nobody, not even the Sentinel. Here the choice is a
 * function of two meters and the court's table, and tools/CourtHarness runs it
 * over every Favor and Suspicion a player can have.
 *
 * <h2>What it does not know</h2>
 *
 * The court itself. Which office reads which meter, and at what band it deals,
 * stays in EnvoyKind; this class is handed the answers as arrays. The harness
 * is handed the same table by the validator, read out of EnvoyKind, so what is
 * proved is the real court rather than a copy kept beside the proof.
 */
public final class CourtRules {

    private CourtRules() {
    }

    /**
     * Writs of audience one visit sells: one.
     *
     * Every envoy sells its own writ, and it is the one way to have an envoy
     * come to you rather than wait for one. Whoever a writ summons stays until
     * they are dealt with, so one a visit makes each writ the price of one
     * audience. More would let a single good visit stock a player with
     * standing invitations.
     */
    public static final int AUDIENCE_USES = 1;

    /**
     * Who may arrive, as positions in the court's table.
     *
     * The meter the player is further up picks the half of the court, and a
     * tie goes to Suspicion. Only those in that half who would deal with the
     * player are candidates, so an envoy never arrives to refuse you.
     *
     * When nobody in that half would deal, the candidates are everybody who
     * would, from either half. That is what closes the dead zone: Favor 1 to
     * 24 over less Suspicion picks the Favor half, whose lowest gate is Favor
     * 25, and Favor does not decay at 25 or below, so such a player could sit
     * there for as long as they liked and never be visited. The Sentinel deals
     * at band 0, so with it on the table somebody can always come.
     *
     * @param favor      the player's Favor
     * @param suspicion  the player's Suspicion
     * @param readsFavor for each member of the court, whether they read Favor
     *                   rather than Suspicion
     * @param willDeal   for each member, whether they would deal with this
     *                   player now
     * @return the candidates in table order; empty only when nobody at all
     *         would deal with the player
     */
    public static int[] arrivals(int favor, int suspicion,
                                 boolean[] readsFavor, boolean[] willDeal) {
        boolean favorLeads = favor > suspicion;
        int[] half = willing(readsFavor, willDeal, favorLeads, true);
        if (half.length > 0) {
            return half;
        }
        return willing(readsFavor, willDeal, favorLeads, false);
    }

    /** Those who would deal: in the leading half only, or in the whole court. */
    private static int[] willing(boolean[] readsFavor, boolean[] willDeal,
                                 boolean favorLeads, boolean halfOnly) {
        int[] found = new int[willDeal.length];
        int count = 0;
        for (int i = 0; i < willDeal.length; i++) {
            if (willDeal[i] && (!halfOnly || readsFavor[i] == favorLeads)) {
                found[count++] = i;
            }
        }
        return Arrays.copyOf(found, count);
    }
}
