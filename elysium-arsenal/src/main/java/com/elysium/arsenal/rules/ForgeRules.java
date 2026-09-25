package com.elysium.arsenal.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reforging: the same sword, rolled again.
 *
 * <h2>What this replaces, and what is worth keeping about it</h2>
 *
 * Reforged gives every piece of gear a hidden grade when a player first gets
 * it — Dull, Keen, Legendary — and three smithing hammers that roll it again
 * for experience and durability. The idea is good and rare: it makes the
 * hundredth iron sword a player picks up different from the ninety-ninth
 * without adding a hundred-and-first sword to the game.
 *
 * <h2>Why a reroll needs different arithmetic from a weapon</h2>
 *
 * Everywhere else in this module the rule is that nothing may be beaten on
 * every axis by another thing. Here that rule is wrong, and following it would
 * delete the mechanic. A grade is not something a player <em>picks</em>; it is
 * something that <em>happens</em> to them. A strictly worse grade is not dead
 * content, it is the risk — without it there is nothing at stake and the
 * hammer is a button that says "make my sword better".
 *
 * <p>So the properties that matter are about the gamble rather than the
 * catalogue:
 *
 * <ol>
 *   <li>Losing has to be possible, and it has to be <em>common</em>. A bad
 *       outcome that is rare is a cruel jackpot; a bad outcome that is likely
 *       is a decision.
 *   <li>Winning has to be possible, and it has to be <em>rare</em>.
 *   <li>The best grade may not be worth as much as a step up the tier ladder,
 *       or reforging an iron sword replaces making a diamond one — and the
 *       ladder is the thing the rest of this module is built on. This one is
 *       checked against {@link WeaponRules} itself rather than against a
 *       number typed here, so retuning the ladder retunes the ceiling.
 *   <li>Rolling away from a good grade has to cost more than rolling away from
 *       a bad one, or the right play is to spam the hammer, and a mechanic
 *       whose right play is "hold down the button" is not a mechanic.
 *   <li>Every kind of gear is reforged by exactly one hammer. None is a
 *       hammer nobody needs; none is gear nobody can reforge; and no gear is
 *       reforgeable by two, which would make which hammer you used matter for
 *       no reason anybody could see.
 * </ol>
 */
public final class ForgeRules {

    private ForgeRules() {
    }

    // ==================================================================
    // What a grade is
    // ==================================================================

    /** One attribute a grade changes, and by how much. */
    public record Change(String attribute, boolean share, float amount) {

        public Change {
            Objects.requireNonNull(attribute, "a change to nothing");
            if (amount == 0.0F) {
                throw new IllegalArgumentException(attribute + " is changed by nothing, "
                        + "which is a line in a tooltip and nothing else");
            }
            if (share && (amount <= -1.0F)) {
                throw new IllegalArgumentException(attribute + " is reduced by "
                        + amount + ", which takes it to nothing or below");
            }
        }

        /**
         * What this is worth, in damage-percent.
         *
         * <p>Two conversions, and the first draft of this file did neither.
         * It added the raw percentages together, so a grade giving five per
         * cent damage and twenty-five per cent knockback came out worth thirty
         * per cent — three times what the best grade in the game is allowed to
         * be worth — and the ceiling check reported it as outclassing a tier
         * of the ladder. It was not; knockback is not damage. A share has to
         * be multiplied by how much the thing it is a share <em>of</em>
         * matters, and a flat amount has to become a share first.
         */
        public float weight() {
            float asShare = share ? amount : amount * FLAT_AS_SHARE;
            return asShare * matters(attribute);
        }
    }

    /**
     * What one point of a flat attribute counts as, against a percentage.
     *
     * <p>Luck, the only flat change any grade makes, is worth roughly a tenth
     * of a decent percentage bonus in practice. Stating it here means the
     * arithmetic below never silently adds a percentage to a raw number.
     */
    public static final float FLAT_AS_SHARE = 0.1F;

    /**
     * How much a per cent of each attribute is worth, against a per cent of
     * damage.
     *
     * <p>Not decoration and not a tuning knob: it is what makes
     * {@link Grade#worth()} a number two grades can be compared on at all.
     * Without it the arithmetic is adding percentages of incomparable things,
     * which is how a grade that gives a little damage and a lot of knockback
     * came out looking like a tier of the weapon ladder.
     *
     * <p>An attribute missing from here is refused rather than defaulted:
     * defaulting would make the new attribute worth whatever the default is,
     * which is a number nobody chose.
     */
    public static final Map<String, Float> MATTERS = Map.ofEntries(
            Map.entry("attack_damage", 1.0F),
            Map.entry("armor", 1.0F),
            Map.entry("max_health", 1.0F),
            Map.entry("attack_speed", 0.8F),
            Map.entry("mining_efficiency", 0.8F),
            Map.entry("block_break_speed", 0.8F),
            Map.entry("movement_speed", 0.6F),
            Map.entry("armor_toughness", 0.5F),
            Map.entry("knockback_resistance", 0.4F),
            Map.entry("entity_interaction_range", 0.3F),
            Map.entry("block_interaction_range", 0.3F),
            Map.entry("attack_knockback", 0.2F),
            Map.entry("movement_efficiency", 0.2F),
            Map.entry("luck", 1.0F));

    /** How much a per cent of that attribute is worth. */
    public static float matters(String attribute) {
        Float found = MATTERS.get(attribute);
        if (found == null) {
            throw new IllegalArgumentException(attribute + " has no stated worth, so "
                    + "a grade changing it would be worth whatever the default was "
                    + "rather than something somebody decided");
        }
        return found;
    }

    /**
     * One grade a piece of gear can be.
     *
     * @param id         its name
     * @param kind       the sort of gear it can land on
     * @param weight     how likely it is, against the other grades of its kind
     * @param experience what it costs to roll <em>away</em> from
     * @param durability what rolling away from it costs the hammer
     * @param changes    what it does to the gear
     */
    public record Grade(String id, String kind, int weight, int experience,
                        int durability, List<Change> changes) {

        public Grade {
            Objects.requireNonNull(id, "a grade with no name");
            if (!KINDS.contains(kind)) {
                throw new IllegalArgumentException(id + " lands on " + kind
                        + ", and gear comes in " + KINDS);
            }
            if (weight < 1) {
                throw new IllegalArgumentException(id + " has a weight of " + weight
                        + ", so it can never come up and is a grade that does not "
                        + "exist");
            }
            if (experience < 0 || durability < 0) {
                throw new IllegalArgumentException(id + " pays the player to reforge");
            }
            changes = List.copyOf(Objects.requireNonNull(changes,
                    "a grade that changes nothing at all"));
        }

        /**
         * What the grade is worth altogether, positive or negative.
         *
         * <p>Signed on purpose: a grade that gives ten per cent damage and
         * takes ten per cent speed is worth nothing, and that is exactly what
         * a side-grade should come to.
         */
        public float worth() {
            float total = 0.0F;
            for (Change change : changes) {
                total += change.weight();
            }
            return total;
        }

        /** Whether this grade leaves the gear worse than it found it. */
        public boolean aLoss() {
            return worth() < 0.0F;
        }

        /** Whether it leaves it better. */
        public boolean aWin() {
            return worth() > 0.0F;
        }
    }

    // ==================================================================
    // The gear, and the hammers
    // ==================================================================

    /** The sorts of gear that can carry a grade. */
    public static final List<String> KINDS =
            List.of("melee", "tool", "armour", "bow", "shield");

    /** Which kinds each hammer rolls. Exactly one hammer per kind. */
    public static final Map<String, List<String>> HAMMERS = Map.of(
            "weaponsmiths_hammer", List.of("melee", "bow"),
            "toolsmiths_hammer", List.of("tool"),
            "armourers_hammer", List.of("armour", "shield"));

    /** The hammer that rolls this kind, or null if nothing does. */
    public static String hammerFor(String kind) {
        for (Map.Entry<String, List<String>> row : new TreeMap<>(HAMMERS).entrySet()) {
            if (row.getValue().contains(kind)) {
                return row.getKey();
            }
        }
        return null;
    }

    // ==================================================================
    // The grades
    // ==================================================================

    private static Change share(String attribute, float amount) {
        return new Change(attribute, true, amount);
    }

    private static Change flat(String attribute, float amount) {
        return new Change(attribute, false, amount);
    }

    private static Grade grade(String id, String kind, int weight, int experience,
                               int durability, Change... changes) {
        return new Grade(id, kind, weight, experience, durability, List.of(changes));
    }

    /**
     * Every grade there is.
     *
     * <p>Read the weights down a kind and the shape of the thing is visible:
     * the losses are the commonest outcomes, the plain grade is next, and the
     * wins thin out sharply. That is what makes a hammer a decision rather
     * than a button.
     */
    public static final List<Grade> GRADES = List.of(
            // ---- melee ----------------------------------------------------
            grade("dull", "melee", 30, 1, 1, share("attack_damage", -0.030F)),
            grade("unwieldy", "melee", 24, 1, 1, share("attack_speed", -0.042F)),
            grade("plain_blade", "melee", 20, 1, 1),
            grade("sharp", "melee", 14, 3, 2, share("attack_damage", 0.030F)),
            grade("swift", "melee", 11, 4, 2, share("attack_speed", 0.056F)),
            grade("keen", "melee", 8, 5, 3, share("attack_damage", 0.060F)),
            grade("heavy", "melee", 5, 7, 4, share("attack_damage", 0.100F),
                    share("attack_speed", -0.031F)),
            grade("savage", "melee", 3, 9, 5, share("attack_damage", 0.060F),
                    share("attack_knockback", 0.150F)),
            grade("fabled", "melee", 1, 12, 6, share("attack_damage", 0.070F),
                    share("attack_speed", 0.030F), flat("luck", 0.11F)),

            // ---- tool -----------------------------------------------------
            grade("blunted", "tool", 30, 1, 1, share("mining_efficiency", -0.0375F)),
            grade("brittle", "tool", 24, 1, 1, share("block_break_speed", -0.0425F)),
            grade("plain_tool", "tool", 20, 1, 1),
            grade("honed", "tool", 14, 3, 2, share("mining_efficiency", 0.0375F)),
            grade("hasteful", "tool", 11, 4, 2, share("block_break_speed", 0.056F)),
            grade("tempered", "tool", 8, 5, 3, share("mining_efficiency", 0.075F)),
            grade("far_reaching", "tool", 5, 7, 4,
                    share("block_interaction_range", 0.300F),
                    share("mining_efficiency", -0.019F)),
            grade("tireless", "tool", 3, 9, 5, share("block_break_speed", 0.075F),
                    share("movement_efficiency", 0.150F)),
            grade("masterwrought", "tool", 1, 12, 6,
                    share("mining_efficiency", 0.0875F),
                    share("block_break_speed", 0.025F), flat("luck", 0.11F)),

            // ---- armour ---------------------------------------------------
            grade("dented", "armour", 30, 1, 1, share("armor", -0.030F)),
            grade("cumbersome", "armour", 24, 1, 1, share("movement_speed", -0.057F)),
            grade("plain_plate", "armour", 20, 1, 1),
            grade("reinforced", "armour", 14, 3, 2, share("armor", 0.030F)),
            grade("unchained", "armour", 11, 4, 2, share("movement_speed", 0.075F)),
            grade("resilient", "armour", 8, 5, 3, share("armor_toughness", 0.120F)),
            grade("heavy_plate", "armour", 5, 7, 4, share("armor", 0.095F),
                    share("movement_speed", -0.033F)),
            grade("fortified", "armour", 3, 9, 5, share("armor", 0.050F),
                    share("knockback_resistance", 0.100F)),
            grade("wardens_own", "armour", 1, 12, 6, share("armor", 0.050F),
                    share("armor_toughness", 0.060F), share("max_health", 0.025F)),

            // ---- bow ------------------------------------------------------
            grade("warped", "bow", 30, 1, 1, share("attack_damage", -0.030F)),
            grade("stiff", "bow", 24, 1, 1, share("attack_speed", -0.0425F)),
            grade("plain_bow", "bow", 20, 1, 1),
            grade("strung", "bow", 14, 3, 2, share("attack_damage", 0.030F)),
            grade("elastic", "bow", 11, 4, 2, share("attack_speed", 0.056F)),
            grade("truesighted", "bow", 8, 5, 3,
                    share("entity_interaction_range", 0.200F)),
            grade("longstrung", "bow", 5, 7, 4, share("attack_damage", 0.100F),
                    share("attack_speed", -0.031F)),
            grade("windcut", "bow", 3, 9, 5, share("attack_damage", 0.060F),
                    share("movement_speed", 0.050F)),
            grade("stormstrung", "bow", 1, 12, 6, share("attack_damage", 0.070F),
                    share("attack_speed", 0.030F), flat("luck", 0.11F)),

            // ---- shield ---------------------------------------------------
            grade("split", "shield", 30, 1, 1, share("armor", -0.030F)),
            grade("weighted", "shield", 24, 1, 1, share("movement_speed", -0.057F)),
            grade("plain_shield", "shield", 20, 1, 1),
            grade("banded", "shield", 14, 3, 2, share("armor", 0.030F)),
            grade("light_shield", "shield", 11, 4, 2, share("movement_speed", 0.075F)),
            grade("braced", "shield", 8, 5, 3, share("armor_toughness", 0.120F)),
            grade("towering", "shield", 5, 7, 4,
                    share("knockback_resistance", 0.250F),
                    share("movement_speed", -0.042F)),
            grade("immovable", "shield", 3, 9, 5,
                    share("knockback_resistance", 0.150F), share("armor", 0.030F)),
            grade("bulwark_of_ages", "shield", 1, 12, 6, share("armor", 0.050F),
                    share("armor_toughness", 0.060F), share("max_health", 0.025F)));

    private static final Map<String, Grade> BY_ID = new LinkedHashMap<>();

    static {
        for (Grade one : GRADES) {
            BY_ID.put(one.id(), one);
        }
    }

    /** The grade of that name, or null. */
    public static Grade grade(String id) {
        return BY_ID.get(id);
    }

    /** Every grade a piece of gear of this kind can be. */
    public static List<Grade> of(String kind) {
        List<Grade> found = new ArrayList<>();
        for (Grade one : GRADES) {
            if (one.kind().equals(kind)) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    // ==================================================================
    // The gamble
    // ==================================================================

    /** The weights of one kind's grades, added up. */
    public static int weightOf(String kind) {
        int total = 0;
        for (Grade one : of(kind)) {
            total += one.weight();
        }
        return total;
    }

    /**
     * Which grade a roll of {@code pick} lands on.
     *
     * @param pick anything from zero up to (not including) {@link #weightOf}
     */
    public static Grade roll(String kind, int pick) {
        List<Grade> grades = of(kind);
        if (grades.isEmpty()) {
            throw new IllegalArgumentException("nothing can be rolled for " + kind);
        }
        int total = weightOf(kind);
        // Wrapping rather than throwing: the caller is a random number and a
        // roll that lands outside the table would be a crash in a player's
        // hand rather than a wrong number on a screen.
        int landed = Math.floorMod(pick, total);
        for (Grade one : grades) {
            landed -= one.weight();
            if (landed < 0) {
                return one;
            }
        }
        return grades.get(grades.size() - 1);
    }

    /** How likely a grade is, as a share of its kind's table. */
    public static double chanceOf(Grade one) {
        return (double) one.weight() / weightOf(one.kind());
    }

    /** The chance a roll of this kind leaves the gear worse than plain. */
    public static double chanceOfLoss(String kind) {
        double share = 0.0D;
        for (Grade one : of(kind)) {
            if (one.aLoss()) {
                share += chanceOf(one);
            }
        }
        return share;
    }

    /** The chance a roll of this kind leaves it better. */
    public static double chanceOfWin(String kind) {
        double share = 0.0D;
        for (Grade one : of(kind)) {
            if (one.aWin()) {
                share += chanceOf(one);
            }
        }
        return share;
    }

    /** What a roll of this kind is worth on average. */
    public static double expected(String kind) {
        double total = 0.0D;
        for (Grade one : of(kind)) {
            total += chanceOf(one) * one.worth();
        }
        return total;
    }

    /** The best grade of a kind, by what it is worth. */
    public static Grade bestOf(String kind) {
        Grade best = null;
        for (Grade one : of(kind)) {
            if (best == null || one.worth() > best.worth()) {
                best = one;
            }
        }
        return best;
    }

    /** The worst. */
    public static Grade worstOf(String kind) {
        Grade worst = null;
        for (Grade one : of(kind)) {
            if (worst == null || one.worth() < worst.worth()) {
                worst = one;
            }
        }
        return worst;
    }

    /**
     * How many rolls it takes on average to see the best grade of a kind.
     *
     * <p>A geometric expectation, which is the whole reason the best grade is
     * allowed to be as good as it is: at a weight of one in a hundred, seeing
     * it costs a hundred rolls, and a hundred rolls of a hammer is a real
     * amount of experience and a hammer worn most of the way out.
     */
    public static double rollsToBest(String kind) {
        Grade best = bestOf(kind);
        return best == null ? Double.POSITIVE_INFINITY : 1.0D / chanceOf(best);
    }

    /** What chasing the best grade of a kind costs in experience, on average. */
    public static double experienceToBest(String kind) {
        double perRoll = 0.0D;
        for (Grade one : of(kind)) {
            perRoll += chanceOf(one) * one.experience();
        }
        return perRoll * rollsToBest(kind);
    }

    // ==================================================================
    // The ceiling, which is the ladder's
    // ==================================================================

    /**
     * The smallest step up the weapon ladder, as a share.
     *
     * <p>Not a number typed here. The point of the ceiling is that reforging
     * must never be a cheaper way to get what making the next tier of weapon
     * gets you, and "the next tier of weapon" is something {@link WeaponRules}
     * already knows. Typing the number would mean retuning the ladder and
     * leaving the ceiling where it was — and the failure would be silent in
     * the direction that matters.
     */
    public static float smallestLadderStep() {
        float smallest = Float.MAX_VALUE;
        for (WeaponRules.Weapon kind : WeaponRules.KINDS) {
            for (int rung = 1; rung < WeaponRules.LADDER.size(); rung++) {
                float below = WeaponRules.at(kind, WeaponRules.LADDER.get(rung - 1))
                        .damage();
                float above = WeaponRules.at(kind, WeaponRules.LADDER.get(rung))
                        .damage();
                if (below > 0.0F) {
                    smallest = Math.min(smallest, (above - below) / below);
                }
            }
        }
        return smallest == Float.MAX_VALUE ? 0.0F : smallest;
    }

    // ==================================================================
    // Whether any of it hangs together
    // ==================================================================

    /** Every kind no hammer rolls, and every kind two hammers roll. */
    public static List<String> unhammered() {
        List<String> bad = new ArrayList<>();
        for (String kind : KINDS) {
            int hammers = 0;
            for (List<String> rolls : HAMMERS.values()) {
                if (rolls.contains(kind)) {
                    hammers++;
                }
            }
            if (hammers == 0) {
                bad.add(kind + " is gear no hammer can reforge, so a third of the "
                        + "mechanic is missing and nothing says so");
            }
            if (hammers > 1) {
                bad.add(kind + " is reforged by " + hammers + " hammers, so which one "
                        + "a player used matters for no reason anybody can see");
            }
        }
        for (Map.Entry<String, List<String>> row : new TreeMap<>(HAMMERS).entrySet()) {
            if (row.getValue().isEmpty()) {
                bad.add(row.getKey() + " rolls nothing at all");
            }
            for (String kind : row.getValue()) {
                if (!KINDS.contains(kind)) {
                    bad.add(row.getKey() + " rolls " + kind + ", which is not a kind "
                            + "of gear");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every kind with nothing to roll, or with no losses or no wins in it. */
    public static List<String> noGamble() {
        List<String> bad = new ArrayList<>();
        for (String kind : KINDS) {
            if (of(kind).isEmpty()) {
                bad.add(kind + " has no grades at all");
                continue;
            }
            if (chanceOfLoss(kind) <= 0.0D) {
                bad.add(kind + " cannot come out worse, so its hammer is a button "
                        + "that says 'make my gear better' and there is nothing at "
                        + "stake in pressing it");
            }
            if (chanceOfWin(kind) <= 0.0D) {
                bad.add(kind + " cannot come out better, so nobody would ever reforge "
                        + "anything of that kind");
            }
            if (chanceOfLoss(kind) < chanceOfWin(kind)) {
                bad.add(kind + " is likelier to improve than to spoil ("
                        + chanceOfWin(kind) + " against " + chanceOfLoss(kind)
                        + "), so the right play is to hold the hammer down");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * How far above break-even a kind's table is allowed to sit.
     *
     * <p>Slightly positive, and only slightly: a table worth nothing is one
     * nobody touches, and a table worth much is one everybody empties their
     * experience into on the first day.
     */
    public static final double MOST_EXPECTED = 0.02D;

    /** Every kind whose table pays too well, or not at all. */
    public static List<String> badOdds() {
        List<String> bad = new ArrayList<>();
        for (String kind : KINDS) {
            double mean = expected(kind);
            if (mean > MOST_EXPECTED) {
                bad.add(kind + " is worth " + mean + " a roll on average, and the most "
                        + "is " + MOST_EXPECTED + " — a table that pays is a table "
                        + "every player empties their experience into on day one");
            }
            if (mean <= -0.05D) {
                bad.add(kind + " is worth " + mean + " a roll on average, which is a "
                        + "machine for destroying gear and nobody would use it twice");
            }
        }
        return List.copyOf(bad);
    }

    /** Every kind whose best grade is worth as much as climbing the ladder. */
    public static List<String> outclassingTheLadder() {
        float step = smallestLadderStep();
        List<String> bad = new ArrayList<>();
        for (String kind : KINDS) {
            Grade best = bestOf(kind);
            if (best == null) {
                continue;
            }
            if (best.worth() >= step) {
                bad.add(best.id() + " is worth " + best.worth() + " and the smallest "
                        + "step up the tier ladder is " + step + ", so reforging is a "
                        + "cheaper way to get what making the next tier gets you and "
                        + "the ladder stops mattering");
            }
        }
        return List.copyOf(bad);
    }

    /** Every grade that costs no more to leave than a worse one. */
    public static List<String> costsThatDoNotClimb() {
        List<String> bad = new ArrayList<>();
        for (String kind : KINDS) {
            for (Grade one : of(kind)) {
                for (Grade other : of(kind)) {
                    if (one == other) {
                        continue;
                    }
                    if (one.worth() > other.worth()
                            && one.experience() < other.experience()) {
                        bad.add(one.id() + " is better than " + other.id()
                                + " and cheaper to roll away from, so a player holding "
                                + "the good one is paid to gamble it away");
                    }
                }
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Every bad outcome that is rarer than the plain one.
     *
     * <p>A loss is the risk, and a risk has to be likely enough to be felt. A
     * loss rarer than the neutral grade is a cruel jackpot: it almost never
     * happens, and when it does the player has no way of knowing it was ever
     * likely.
     */
    public static List<String> cruelJackpots() {
        List<String> bad = new ArrayList<>();
        for (String kind : KINDS) {
            for (Grade one : of(kind)) {
                if (!one.aLoss()) {
                    continue;
                }
                for (Grade other : of(kind)) {
                    if (other.aWin() && other.weight() > one.weight()) {
                        bad.add(one.id() + " is a loss at weight " + one.weight()
                                + " and " + other.id() + " is a win at weight "
                                + other.weight() + ", so spoiling the gear is rarer "
                                + "than improving it");
                    }
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every pair of grades that are one grade under two names. */
    public static List<String> indistinguishable() {
        Map<String, String> seen = new LinkedHashMap<>();
        List<String> bad = new ArrayList<>();
        for (Grade one : GRADES) {
            List<String> parts = new ArrayList<>();
            for (Change change : one.changes()) {
                parts.add(change.attribute() + (change.share() ? "%" : "+")
                        + change.amount());
            }
            parts.sort(String::compareTo);
            String key = one.kind() + "/" + String.join(",", parts);
            String had = seen.putIfAbsent(key, one.id());
            if (had != null) {
                bad.add(one.id() + " and " + had + " are the same grade under two "
                        + "names, so one of them is a word and nothing else");
            }
        }
        return List.copyOf(bad);
    }

    /** Every grade declared twice, and every one whose name collides. */
    public static List<String> repeated() {
        Set<String> seen = new TreeSet<>();
        List<String> bad = new ArrayList<>();
        for (Grade one : GRADES) {
            if (!seen.add(one.id())) {
                bad.add(one.id() + " is declared twice");
            }
        }
        Set<String> made = new TreeSet<>(WeaponRules.catalogue());
        made.addAll(RangedRules.catalogue());
        made.addAll(ArsenalRules.catalogue());
        for (Grade one : GRADES) {
            if (made.contains(one.id())) {
                bad.add(one.id() + " is also the name of a piece of gear, so one of "
                        + "the two registrations will lose");
            }
        }
        for (String hammer : HAMMERS.keySet()) {
            if (made.contains(hammer)) {
                bad.add(hammer + " is also the name of a piece of gear");
            }
        }
        return List.copyOf(bad);
    }

    /** Every name a grade needs on the way into a game. */
    public static List<String> catalogue() {
        List<String> all = new ArrayList<>();
        for (Grade one : GRADES) {
            all.add(one.id());
        }
        return List.copyOf(all);
    }
}
