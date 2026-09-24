package com.elysium.lib.hazard;

/**
 * The hazard arithmetic, executed outside a world.
 *
 * <h2>What is worth running here</h2>
 *
 * Not the damage tick — that needs a level, a biome and an entity, and mocking
 * those would be testing the mock. What can be checked is the part that decides
 * how the system <em>feels</em>, and that is pure:
 *
 * <ul>
 *   <li>the combination rule approaching total protection and never reaching
 *       it, however many sources are stacked;</li>
 *   <li>a full set of the right element being worth having and not being
 *       immunity;</li>
 *   <li>the ring answer being real but worse than the matching answer, which is
 *       the whole reason there are two of them;</li>
 *   <li>the five hazards being survivable unprotected for long enough to walk
 *       out of, which is a claim about damage rates that nothing else states.</li>
 * </ul>
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/hh elysium-lib/tools/HazardHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/hh" \
 *         com.elysium.lib.hazard.HazardHarness
 */
public final class HazardHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /** The rule the specification asks for, and the one it must never break. */
    private static void combinationRule() {
        check(ElysiumResistance.combine() == 0.0F,
                "no sources at all removed something");
        check(Math.abs(ElysiumResistance.combine(0.5F) - 0.5F) < 1e-6,
                "one source of a half did not remove a half");
        // And at the top of the range, where a clamp would bite: one source of
        // 99% alone is 99%. A per-source cap of 95% once passed every other
        // check in this file, because nothing fed a single source above it.
        for (float high : new float[] {0.96F, 0.99F, 0.999F}) {
            check(Math.abs(ElysiumResistance.combine(high) - high) < 1e-5,
                    "one source of " + high + " alone removed "
                            + ElysiumResistance.combine(high)
                            + " — something clamps a single source");
        }

        // Two quarters are two fifths: each quarter is a stat of 1/3, the stats
        // add to 2/3, and 2/3 / (2/3 + 1) is 0.4. Shares add as the stats
        // behind them — stat / (stat + b), the owner's ruling — not as
        // percentages, which would be a half and would reach 100% at four.
        check(Math.abs(ElysiumResistance.combine(0.25F, 0.25F) - 0.4F) < 1e-6,
                "two quarters did not combine to 2/5, so shares are not adding as stats");

        // Order cannot matter, because multiplication does not care.
        check(Math.abs(ElysiumResistance.combine(0.1F, 0.6F, 0.3F)
                        - ElysiumResistance.combine(0.3F, 0.1F, 0.6F)) < 1e-6,
                "the same sources in a different order gave a different answer");

        // Monotonic: another source never makes a player worse off.
        float previous = 0.0F;
        for (int count = 0; count <= 40; count++) {
            float[] shares = new float[count];
            java.util.Arrays.fill(shares, 0.2F);
            float total = ElysiumResistance.combine(shares);
            check(total >= previous, "adding a source lowered protection at " + count);
            check(total < 1.0F,
                    count + " sources of a fifth reached total immunity (" + total + ")");
            previous = total;
        }

        // Nothing is clamped any more, so a source that literally claims 100%
        // is taken at its word. What must hold instead is that no source the
        // game produces can: every one is derived from a finite stat, and a
        // finite stat — however large — never arrives. Asked of the real
        // per-piece formula at a tier no world will reach.
        check(ElysiumResistance.pieceShare(ElysiumProtection.BASE_SHARE,
                        ElysiumProtection.TIER_STEP, 1_000_000, 1.0F) < 1.0F,
                "a piece at tier one million removes everything; a finite stat "
                        + "must never reach 100%");
        // And no ceiling short of it either: enough tiers on one piece must go
        // past anything a cap would have stopped at. This used to be 95%.
        check(ElysiumResistance.pieceShare(ElysiumProtection.BASE_SHARE,
                        ElysiumProtection.TIER_STEP, 5_000, 1.0F) > 0.99F,
                "a piece at tier 5000 removes no more than 99% — something caps "
                        + "a single piece's share; it removes more than 99% "
                        + "only if nothing does");
        check(ElysiumResistance.combine(fill(10_000, 0.9F)) < 1.0F,
                "ten thousand sources of 90% reached total immunity");
        check(ElysiumResistance.combine(Float.NaN) == 0.0F,
                "a NaN share poisoned the product instead of counting as nothing");
        check(ElysiumResistance.combine(-0.5F) == 0.0F,
                "a negative share added protection");

        System.out.println("  stacking: 1 piece " + pct(ElysiumResistance.combine(0.2F))
                + ", 4 pieces " + pct(ElysiumResistance.combine(0.2F, 0.2F, 0.2F, 0.2F))
                + ", 40 pieces " + pct(ElysiumResistance.combine(fill(40, 0.2F))));
    }

    private static float[] fill(int count, float value) {
        float[] shares = new float[count];
        java.util.Arrays.fill(shares, value);
        return shares;
    }

    private static String pct(float share) {
        // Three decimals, not zero. The first version rounded to whole percent
        // and printed "40 pieces 100%" directly above the line claiming no
        // amount of stacking reaches immunity — the assertion was passing and
        // the output said the opposite. A number that rounds away the thing it
        // is reporting is worse than no number.
        return String.format("%.3f%%", share * 100.0F);
    }

    /** A full set of the right element has to be worth wearing. */
    private static void armourIsWorthIt() {
        float onePiece = ElysiumResistance.pieceShare(ElysiumProtection.BASE_SHARE, ElysiumProtection.TIER_STEP, 0, 1.0F);
        float fullSet = ElysiumResistance.combine(fill(4, onePiece));

        check(fullSet > 0.35F,
                "a full matching set at tier 0 removes only " + pct(fullSet)
                        + ", which is not worth the trouble of assembling");
        check(fullSet < 0.75F,
                "a full matching set at tier 0 already removes " + pct(fullSet)
                        + ", leaving nothing for tier, food or shelter to do");

        // Tier has to matter more than piece count eventually, or the answer to
        // every hazard is four cheap pieces forever.
        float highTierPiece = ElysiumResistance.pieceShare(ElysiumProtection.BASE_SHARE, ElysiumProtection.TIER_STEP, 8, 1.0F);
        // "Doubles" is about the stat behind the share. Shares follow
        // stat / (stat + b) and so cannot double once they are large — twice
        // 37% is not a share this formula ever lands on by doubling anything.
        check(com.elysium.lib.stats.ElysiumShares.odds(highTierPiece)
                        > com.elysium.lib.stats.ElysiumShares.odds(onePiece) * 2,
                "eight tiers of progression less than doubles a piece's stat, so "
                        + "late gear is not an answer to anything early gear was not");

        System.out.println("  armour: one piece " + pct(onePiece) + " at tier 0, "
                + pct(highTierPiece) + " at tier 8; a full tier-0 set " + pct(fullSet));
    }

    /**
     * The ring answer must be real, and must be worse.
     *
     * If it were equal there would be no reason to seek the matching element;
     * if it were negligible there would be no reason to learn the ring.
     */
    private static void ringAnswerIsSecondBest() {
        float matching = ElysiumResistance.pieceShare(ElysiumProtection.BASE_SHARE, ElysiumProtection.TIER_STEP, 0, 1.0F);
        float viaRing = ElysiumResistance.pieceShare(ElysiumProtection.BASE_SHARE, ElysiumProtection.TIER_STEP, 0, ElysiumProtection.RING_FRACTION);

        check(viaRing > 0.0F,
                "gear that beats the hazard's element gives nothing, so the "
                        + "counter ring stops existing outside combat");
        check(viaRing < matching,
                "gear that beats the hazard's element is as good as gear that "
                        + "matches it, so the matching element is pointless");

        float ringSet = ElysiumResistance.combine(fill(4, viaRing));
        float matchedSet = ElysiumResistance.combine(fill(4, matching));
        check(ringSet < matchedSet,
                "a full set of the ring answer matches a full set of the right one");
        check(ringSet > 0.2F,
                "a full set of the ring answer removes only " + pct(ringSet)
                        + " — not enough to be the thing a player reaches for");

        System.out.println("  the ring: a full set of the right element "
                + pct(matchedSet) + ", of one that beats it " + pct(ringSet));
    }

    /**
     * Unprotected, a hazard has to be survivable long enough to leave.
     *
     * The design claim is "fatal to anybody who stands in it, survivable for
     * anybody who walks out". That is a statement about seconds of life, and no
     * constant in the mod states it — it falls out of five damage rates and
     * vanilla's twenty hearts.
     */
    private static void survivableLongEnoughToLeave() {
        final float playerHealth = 20.0F;
        for (ElysiumHazard hazard : ElysiumHazard.REGISTRY.all()) {
            float dps = hazard.getDamagePerSecond();
            check(dps > 0.0F, hazard + " does no damage at all");
            float seconds = playerHealth / dps;

            check(seconds >= 6.0F,
                    hazard + " kills an unprotected player in " + round(seconds)
                            + "s, which is not long enough to read the message and "
                            + "walk out");
            check(seconds <= 30.0F,
                    hazard + " takes " + round(seconds) + "s to kill an unprotected "
                            + "player, which is long enough to ignore");

            // ...and with the right gear on, long enough to work in.
            float armoured = ElysiumResistance.after(dps,
                    fill(4, ElysiumResistance.pieceShare(ElysiumProtection.BASE_SHARE, ElysiumProtection.TIER_STEP, 0, 1.0F)));
            float armouredSeconds = playerHealth / armoured;
            check(armouredSeconds > seconds * 1.5F,
                    hazard + " is barely improved by a full matching set");
        }

        StringBuilder line = new StringBuilder("  unprotected: ");
        for (ElysiumHazard hazard : ElysiumHazard.REGISTRY.all()) {
            line.append(hazard.getId().getPath()).append(' ')
                .append(round(playerHealth / hazard.getDamagePerSecond())).append("s  ");
        }
        System.out.println(line.toString().trim());
    }

    private static String round(float value) {
        return String.valueOf(Math.round(value));
    }

/**
     * A fully equipped player takes nothing. That is the design.
     *
     * <h3>Why this check is the reverse of the one it replaced</h3>
     *
     * The first version enforced the specification's line — "meaningful
     * stacking without immunity" — and failed any loadout that removed more
     * than 92% of a hazard. The project owner decided the opposite: a fully
     * equipped player should be immune.
     *
     * That is not the specification being ignored. Its own authority table puts
     * "later explicit project-owner decisions" at priority 1 and its own
     * inherited text at priority 3, and says the newest explicit decision wins.
     * This is the document working as designed.
     *
     * What changed with it is the shape of immunity. A percentage threshold
     * would have made it an accident of arithmetic — a line crossed somewhere
     * inside a kit, with "am I safe yet" answerable only by reading the source.
     * A complete set of the hazard's own element is a thing a player can look
     * at and know.
     */
    private static void aFullSetSeals() {
        // The rule is about slots and elements, not about numbers, so what is
        // checkable here is that the *partial* arithmetic never quietly does the
        // job the seal is supposed to do. An incomplete kit has to stay short of
        // total protection however it is assembled, or the seal stops being the
        // thing that grants immunity and becomes decoration on top of a
        // threshold nobody declared.
        final float highTierPiece = ElysiumResistance.pieceShare(ElysiumProtection.BASE_SHARE, ElysiumProtection.TIER_STEP, 12, 1.0F);

        float threePieces = ElysiumResistance.combine(fill(3, highTierPiece));
        check(threePieces < 1.0F,
                "three pieces already remove everything, so the fourth is not "
                        + "what seals a player and the rule is a lie");
        check(threePieces < 0.95F,
                "three high-tier pieces remove " + pct(threePieces)
                        + " — so close to sealed that the complete set is a "
                        + "formality rather than a goal");

        // The ring answer must never approach the seal either. Half shares are
        // the point: knowing the ring is a shortcut, not a substitute.
        float ringSet = ElysiumResistance.combine(
                fill(4, ElysiumResistance.pieceShare(ElysiumProtection.BASE_SHARE, ElysiumProtection.TIER_STEP, 12, ElysiumProtection.RING_FRACTION)));
        check(ringSet < threePieces,
                "a full set of gear that merely beats the hazard's element "
                        + "protects better than three pieces of the right one, "
                        + "so there is no reason to seek the right one");

        System.out.println("  partial kits, tier 12: three matching pieces "
                + pct(threePieces) + ", four of the ring answer " + pct(ringSet)
                + " — and a complete matching set seals outright");
    }

/**
     * Nothing short of the seal may amount to a seal.
     *
     * The owner's decision is that a fully equipped player is immune, and there
     * are three named states that mean it: a complete matching set, standing
     * in a filed claim, and a ration still in effect. That definition only holds if immunity cannot also be
     * *assembled* out of partial sources — if shelter, food and a trinket
     * stacked onto the ring answer took a player to 99%, the named states would
     * be ceremony on top of a threshold nobody declared, and a player would be
     * immune without ever learning why.
     *
     * The three non-armour numbers below are not implemented yet. They are the
     * budget those features have to fit inside, written where the total is
     * checked, so shelter cannot quietly ship at 0.8 and make the seal
     * pointless between them.
     */
    private static void nothingElseAmountsToASeal() {
        final float trinket = 0.12F;

        // Shelter was a reserved literal here too, alongside the meal, and it
        // is a real constant now for the same reason: §8.5's rooms filled the
        // slot, so the budget line becomes a check that what shipped fits what
        // was promised. Literal on one side, as ever.
        final float shelter = com.elysium.lib.room.ElysiumRooms.bestShare();
        check(shelter <= 0.35F,
                "the best room removes " + pct(shelter) + ", above the 0.35 this "
                        + "budget reserved for a sealed, treated room — the "
                        + "architecture is now doing work the armour set was "
                        + "supposed to be the answer to");
        check(shelter > 0.0F,
                "no room removes anything, so §10.2's sealed shelter is a line "
                        + "of tooltip text with nothing behind it");

        // The meal was here too, first as a reserved literal and then as the
        // real constant it filled. It is gone because the project owner made a
        // ration *immunity* — the third named state — and a named state is not
        // a share and does not combine with anything. What is left in the
        // budget is what still stacks: armour, a room, a trinket.

        // The best a player can do *without* committing all four slots to the
        // hazard's own element: the ring answer, plus everything else there is.
        final float ringPiece = ElysiumResistance.pieceShare(ElysiumProtection.BASE_SHARE, ElysiumProtection.TIER_STEP, 12, ElysiumProtection.RING_FRACTION);
        float[] unsealed = {
            ringPiece, ringPiece, ringPiece, ringPiece, shelter, trinket,
        };
        float total = ElysiumResistance.combine(unsealed);
        float through = 1.0F - total;

        check(through >= 0.10F,
                "an unsealed player can reach " + pct(total) + ", leaving "
                        + pct(through) + " — that is "
                        + Math.round(20.0F / ElysiumResistance.after(
                                ElysiumHazards.PRESSURE.getDamagePerSecond(), unsealed))
                        + "s in the worst hazard without ever assembling the set "
                        + "that is supposed to be what immunity means");

        // ...and it still has to be worth doing, or the only path is the seal
        // and the partial layer is decoration.
        check(total >= 0.5F,
                "everything short of the seal combines to only " + pct(total)
                        + ", so there is no reason to wear anything until the "
                        + "full set is finished");

        System.out.println("  short of the seal: " + pct(total) + " removed, "
                + pct(through) + " still lands — immunity comes from a named "
                + "state, never from stacking");
    }

    /**
     * Heat needs a reason: height, or a fire.
     *
     * <h3>What was wrong with it</h3>
     *
     * Any biome above 1.5 base temperature burned, at any height, all day —
     * which is every desert, badlands and savanna plateau in the world, at the
     * altitude players actually walk around at. The owner reported it as
     * triggering too aggressively, and they were describing a hazard that is
     * always on, which is not a hazard but a tax with a message attached.
     *
     * The fix is two gates, and a change to how often something triggers is
     * exactly the kind that is invisible except by playing — unless it is
     * written down as arithmetic.
     */
    private static void heatNeedsAReason() {
        final float desert = 2.0F;
        final float temperate = 0.8F;

        // The bug, stated: a desert at the height people live at, at noon.
        check(ElysiumHazardField.sunIntensity(desert, true, 64) == 0.0F,
                "a desert at y=64 at noon is still a thermal hazard — which is "
                        + "most of the overworld's warm half, most of the time");
        check(ElysiumHazardField.sunIntensity(desert, true, 120) == 0.0F,
                "a desert at y=120 is a thermal hazard, which is a hill");

        // ...and the gate, stated: high enough up, it is.
        check(ElysiumHazardField.sunIntensity(desert, true,
                        ElysiumHazardField.THERMAL_ALTITUDE + 60) > 0.0F,
                "a scorching biome is not a hazard even well above "
                        + ElysiumHazardField.THERMAL_ALTITUDE
                        + ", so the altitude gate closes it entirely");
        check(ElysiumHazardField.sunIntensity(desert, true,
                        ElysiumHazardField.THERMAL_CEILING) > 0.9F,
                "the sun never reaches full strength, even at the build limit");

        // Night still answers it, which is the specification's cheap counter.
        check(ElysiumHazardField.sunIntensity(desert, false, 300) == 0.0F,
                "a desert at midnight at y=300 is as hot as at noon, so "
                        + "travelling at night stops being an answer");

        // A cold biome is never a sun hazard however high it gets — that
        // altitude belongs to the cold, and both firing at once is two
        // messages, two damage sources and one very confused player.
        check(ElysiumHazardField.sunIntensity(temperate, true, 300) == 0.0F,
                "a temperate mountaintop burns, though the same height is "
                        + "already a cryogenic hazard");
        check(ElysiumHazardField.THERMAL_ALTITUDE
                        >= ElysiumHazardField.COLD_ALTITUDE,
                "heat begins lower than cold does, so there is a band where a "
                        + "scorching biome is both");

        // Monotonic in height, so a player climbing feels it getting worse.
        float previous = -1.0F;
        for (int y = 0; y <= 400; y += 8) {
            float value = ElysiumHazardField.sunIntensity(desert, true, y);
            check(value >= previous, "the sun got weaker between y=" + (y - 8)
                    + " and y=" + y);
            check(value <= 1.0F, "the sun passed full strength at y=" + y);
            previous = value;
        }

        // The other gate: a fire nearby, counted rather than measured to the
        // nearest block, so one lava source in a cave is a twelfth of it and a
        // lake is all of it.
        check(ElysiumHazardField.fireIntensity(0) == 0.0F,
                "a position with no fire near it is a thermal hazard");
        check(ElysiumHazardField.fireIntensity(1) > 0.0F,
                "a lava block within reach is worth nothing, so the second half "
                        + "of the owner's decision does nothing");
        check(ElysiumHazardField.fireIntensity(1) < 0.25F,
                "one lava block in a cave is worth "
                        + pct(ElysiumHazardField.fireIntensity(1))
                        + " of the hazard, so ordinary mining is a hazard");
        check(ElysiumHazardField.fireIntensity(1000) == 1.0F,
                "a lava lake does not reach full strength");
        float fire = -1.0F;
        for (int burning = 0; burning <= 40; burning++) {
            float value = ElysiumHazardField.fireIntensity(burning);
            check(value >= fire, "more fire was worth less at " + burning);
            fire = value;
        }

        // And the cost of asking, because the answer is a block scan and the
        // hazard layer asks twice a second for every player.
        check(ElysiumHazardField.fireSamples() <= 500,
                "one fire scan reads " + ElysiumHazardField.fireSamples()
                        + " blocks; at two a second per player that is a cost "
                        + "which only appears on somebody else's server");
        check(ElysiumHeatCache.TTL >= 20,
                "the fire scan is cached for " + ElysiumHeatCache.TTL
                        + " ticks, so it runs nearly every time it is asked");
        check(ElysiumHeatCache.CAPACITY > 0 && ElysiumHeatCache.CAPACITY <= 4096,
                "the fire cache holds " + ElysiumHeatCache.CAPACITY
                        + " positions, which is a leak with a limit rather "
                        + "than a cache");

        System.out.println("  heat: nothing below y="
                + ElysiumHazardField.THERMAL_ALTITUDE + " and nothing at night; "
                + ElysiumHazardField.HEAT_FOR_FULL + " burning blocks within "
                + ElysiumHazardField.HEAT_RADIUS + " for full, read as "
                + ElysiumHazardField.fireSamples() + " samples every "
                + (ElysiumHeatCache.TTL / 20) + "s");
    }

    /**
     * A ward is a state, and a second helping is not a second state.
     *
     * <h3>What changed, and what did not</h3>
     *
     * The ward shipped as a tenth and the project owner made it immunity. What
     * survived that decision is the shape: present or absent, with the
     * amplifier — the one thing an effect carries that could have made this a
     * meter — deliberately not read. That is now the whole of what keeps a
     * stack of rations from being anything but a longer clock, so it is the
     * whole of what is checked.
     */
    private static void aWardIsAState() {
        check(!ElysiumWards.wardedBy(false, 0),
                "an entity with no ward at all is warded");
        check(ElysiumWards.wardedBy(true, 0),
                "a ward at level one is not a ward");
        for (int amplifier = 0; amplifier <= 255; amplifier++) {
            check(ElysiumWards.wardedBy(true, amplifier),
                    "a ward at level " + (amplifier + 1) + " stops counting, so "
                            + "a modded potion that stacks the effect quietly "
                            + "unwards the player");
        }

        check(ElysiumWards.DURATION >= 20 * 60,
                "a ward lasts " + (ElysiumWards.DURATION / 20) + "s, which is "
                        + "not long enough to cross anything, so a player eats "
                        + "on the far side of a hazard they already survived");
        // The ceiling matters more than it did: this is immunity now, and the
        // clock is the only thing that makes a meal cheaper than a set.
        check(ElysiumWards.DURATION <= 20 * 300,
                "a ward lasts " + (ElysiumWards.DURATION / 20 / 60) + " minutes "
                        + "of outright immunity, long enough that one meal "
                        + "covers a whole expedition and the armour set has "
                        + "nothing left to be for");

        System.out.println("  the ward: immunity while it lasts — "
                + (ElysiumWards.DURATION / 20 / 60) + " minutes a meal, at "
                + "every level, never a share");
    }

        /**
     * Immunity to one hazard is a decision to be ordinary in the others.
     *
     * This is what stops the seal being a straight power-up. All four slots go
     * to one element, so the other four hazards are met with nothing at all —
     * and the arithmetic has to confirm that, because if unmatched gear gave a
     * meaningful share anyway, a single set would quietly answer everything.
     */
    private static void sealingOneLeavesFourOpen() {
        for (ElysiumHazard sealed : ElysiumHazard.REGISTRY.all()) {
            for (ElysiumHazard other : ElysiumHazard.REGISTRY.all()) {
                if (other == sealed) {
                    continue;
                }
                // Four pieces of the sealed hazard's element, met by another
                // hazard. Either they beat that hazard's element and get half
                // shares, or they do nothing at all. Never a seal.
                boolean ringHelps = sealed.getElement()
                        .isStrongAgainst(other.getElement());
                float share = ringHelps
                        ? ElysiumResistance.pieceShare(ElysiumProtection.BASE_SHARE, ElysiumProtection.TIER_STEP, 12, ElysiumProtection.RING_FRACTION)
                        : 0.0F;
                float total = ElysiumResistance.combine(fill(4, share));
                check(total < 0.9F,
                        "a set sealed against " + sealed.getId().getPath()
                                + " removes " + pct(total) + " of "
                                + other.getId().getPath()
                                + " as a side effect, so one set answers two "
                                + "worlds and the choice is not a choice");
            }
        }

        // And the counter ring has to leave at least one hazard each set is
        // simply bad at, or "commit to an element" costs nothing.
        for (ElysiumHazard sealed : ElysiumHazard.REGISTRY.all()) {
            long helpless = ElysiumHazard.REGISTRY.all().stream()
                    .filter(other -> other != sealed)
                    .filter(other -> !sealed.getElement()
                            .isStrongAgainst(other.getElement()))
                    .count();
            check(helpless >= 2,
                    "a set sealed against " + sealed.getId().getPath()
                            + " has some answer to all but " + helpless
                            + " of the other hazards, so committing four slots "
                            + "to one element costs almost nothing");
        }
        System.out.println("  the trade: each sealed set is on half shares "
                + "against two hazards and has nothing at all for the other two");
    }

/**
     * The seal rule, executed.
     *
     * Every combination of four slots that matters: the complete set, each way
     * of being one short, an empty slot, the ring answer, and a mixed bag.
     */
    private static void theSealRule() {
        for (ElysiumHazard hazard : ElysiumHazard.REGISTRY.all()) {
            com.elysium.lib.element.ElysiumElement right = hazard.getElement();

            check(ElysiumProtection.sealedBy(
                            java.util.List.of(right, right, right, right), hazard),
                    "a complete set of " + right.getId().getPath()
                            + " does not seal against " + hazard.getId().getPath()
                            + ", which is the one thing the rule exists to do");

            // One short, in every position.
            for (int gap = 0; gap < 4; gap++) {
                java.util.List<com.elysium.lib.element.ElysiumElement> worn =
                        new java.util.ArrayList<>(
                                java.util.List.of(right, right, right, right));
                worn.set(gap, null);
                check(!ElysiumProtection.sealedBy(worn, hazard),
                        "an empty slot " + gap + " still sealed against "
                                + hazard.getId().getPath());
            }

            // One piece of something else, in every position.
            for (ElysiumHazard other : ElysiumHazard.REGISTRY.all()) {
                if (other == hazard) {
                    continue;
                }
                for (int swapped = 0; swapped < 4; swapped++) {
                    java.util.List<com.elysium.lib.element.ElysiumElement> worn =
                            new java.util.ArrayList<>(
                                    java.util.List.of(right, right, right, right));
                    worn.set(swapped, other.getElement());
                    check(!ElysiumProtection.sealedBy(worn, hazard),
                            "a set with one piece of "
                                    + other.getElement().getId().getPath()
                                    + " still sealed against "
                                    + hazard.getId().getPath());
                }

                // A complete set of the wrong element never seals, even when it
                // beats the hazard's element on the ring.
                check(!ElysiumProtection.sealedBy(java.util.List.of(
                                other.getElement(), other.getElement(),
                                other.getElement(), other.getElement()), hazard),
                        "a complete set of " + other.getElement().getId().getPath()
                                + " sealed against " + hazard.getId().getPath());
            }

            check(!ElysiumProtection.sealedBy(java.util.List.of(), hazard),
                    "wearing nothing at all sealed against "
                            + hazard.getId().getPath());
            check(!ElysiumProtection.sealedBy(null, hazard),
                    "a null loadout sealed against " + hazard.getId().getPath());
        }
        System.out.println("  the seal: four of the right element and nothing else, "
                + "checked against every one-slot failure for all five hazards");
    }

        /** Every hazard is bound to a real element, and no two share one. */
    private static void hazardsCoverTheRing() {
        java.util.Set<String> elements = new java.util.HashSet<>();
        for (ElysiumHazard hazard : ElysiumHazard.REGISTRY.all()) {
            check(hazard.getElement() != null && hazard.getElement().isElemental(),
                    hazard + " is bound to no element, so nothing resists it");
            check(elements.add(hazard.getElement().getId().toString()),
                    hazard + " shares an element with another hazard, so one set of "
                            + "armour answers two worlds and one element answers none");
        }
        check(ElysiumHazard.REGISTRY.size() == 5,
                "there are " + ElysiumHazard.REGISTRY.size() + " hazards, not the "
                        + "five the specification names");
    }

    public static void main(String[] args) {
        ElysiumHazards.bootstrap();
        combinationRule();
        armourIsWorthIt();
        ringAnswerIsSecondBest();
        survivableLongEnoughToLeave();
        aFullSetSeals();
        nothingElseAmountsToASeal();
        aWardIsAState();
        heatNeedsAReason();
        sealingOneLeavesFourOpen();
        theSealRule();
        hazardsCoverTheRing();

        System.out.println("hazards: the stacking rule, the armour shares, the ring "
                + "answer, the ward, the seal and five survival windows — "
                + checked + " assertions");
        // Outside sources (a planet's atmosphere): read into 0..1, NaN as nothing.
        check(ElysiumHazardField.unit(Float.NaN) == 0.0F, "a NaN source became a hazard");
        check(ElysiumHazardField.unit(-2.0F) == 0.0F, "a negative source became a hazard");
        check(ElysiumHazardField.unit(7.0F) == 1.0F, "a source above 1 went past full intensity");
        check(ElysiumHazardField.unit(Float.POSITIVE_INFINITY) == 1.0F, "an infinite source");
        check(ElysiumHazardField.unit(0.4F) == 0.4F, "a sane source was changed");

        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("a complete set of the right element seals, and so "
                + "do a filed claim and a meal still in effect; everything "
                + "short of those three is arithmetic");
    }
}
