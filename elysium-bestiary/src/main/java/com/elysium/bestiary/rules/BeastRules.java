package com.elysium.bestiary.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Things that hunt you. The JDK and nothing else at all.
 *
 * <h2>What a bestiary is for, and the three ways it fails</h2>
 *
 * Seven jars in this pack exist to put more things in the dark. Between them
 * they add dozens of mobs and three problems that nobody ever reports as bugs,
 * because all three look like the game working.
 *
 * <ol>
 *   <li><b>A mob that is harder and worth less.</b> Every player works this
 *       out within a week and then avoids it for the rest of the save, and the
 *       mob may as well not exist. It is never noticed by whoever added it,
 *       because they were balancing the <em>fight</em>.</li>
 *   <li><b>A mob that is far and away the best thing to farm.</b> One mob with
 *       twice the reward per unit of danger turns every grinder in the pack
 *       into a grinder for that one mob, and the other thirty are scenery.</li>
 *   <li><b>A bestiary the base game outfarms.</b> Rarer, and worse: if a
 *       vanilla zombie pays better per unit of danger than anything added,
 *       then every mob farm in the pack is a zombie farm and the whole module
 *       is decoration. Nobody checks this, because it means comparing your own
 *       numbers against a mob you did not write.</li>
 * </ol>
 *
 * <p>All three are ratios over the whole set, which is not a thing anybody
 * checks by reading a list of mobs. So every beast here has a <b>threat</b>
 * worked out from its own stats, its reward is <b>derived from that threat
 * rather than chosen</b> (see {@link #derive}), and the harness proves that
 * worth rises with threat everywhere, that the reward per unit of danger sits
 * inside a band across the whole bestiary, and that the band contains the
 * game's own zombie.
 */
public final class BeastRules {

    private BeastRules() {
    }

    /**
     * One thing that hunts you.
     *
     * <p>Note what is <em>not</em> here: how much experience it gives, and how
     * much it drops. Those are not stored, because a number stored in two
     * places is a number that disagrees with itself eventually. They are
     * worked out from these ten by {@link #derive} and looked up with
     * {@link #earned}.
     *
     * @param id      what it is called
     * @param family  which of the three shapes it is
     * @param tier    how far up its family it sits, from one
     * @param health  hit points
     * @param damage  what one hit takes off you
     * @param speed   movement speed, in the game's own units
     * @param armour  how much of a hit it shrugs off
     * @param weight  how often it is chosen when something spawns
     * @param width   how wide it is, in blocks
     * @param height  and how tall
     */
    public record Beast(String id, String family, int tier, double health, double damage,
                        double speed, double armour, int weight, float width, float height) {

        public Beast {
            if (health <= 0.0D || damage < 0.0D || speed <= 0.0D) {
                throw new IllegalArgumentException("a beast with no health, negative "
                        + "reach or no legs is not a beast");
            }
            if (weight < 1) {
                throw new IllegalArgumentException("a beast nothing ever spawns is a "
                        + "beast nobody meets");
            }
            if (tier < 1) {
                throw new IllegalArgumentException("a beast below the bottom of its "
                        + "own ladder");
            }
        }

        /**
         * How dangerous it is, in one number.
         *
         * <p>What it can take times what it can give, plus what its armour
         * saves it — which is the shape of every "how long does this fight
         * last and how much does it cost me" question, and is deliberately
         * crude. A precise threat number would be a number nobody could check
         * against their own memory of being chased.
         */
        public double threat() {
            return health * (1.0D + damage) * (1.0D + armour / 10.0D) * speed / 0.25D;
        }
    }

    /**
     * What killing one leaves behind.
     *
     * @param experience the orbs
     * @param drops      how many items, on average, it leaves
     */
    public record Reward(int experience, int drops) {

        public Reward {
            if (experience < 1) {
                throw new IllegalArgumentException("a mob worth no experience is a mob "
                        + "worth walking around");
            }
            if (drops < 0) {
                throw new IllegalArgumentException("a mob that takes items off the "
                        + "floor when it dies");
            }
        }

        /** The whole reward on one scale, so it can be compared to danger. */
        public double worth() {
            return experience + (double) drops * WORTH_OF_A_DROP;
        }
    }

    // ==================================================================
    // The three families
    // ==================================================================

    /** Fast, fragile, and never alone. */
    public static final String PROWLER = "prowler";

    /** Slow, heavy, and hits like a door. */
    public static final String BRUTE = "brute";

    /** Thin, patient, and already dead. */
    public static final String WIGHT = "wight";

    public static final List<String> FAMILIES = List.of(PROWLER, BRUTE, WIGHT);

    /**
     * The bestiary.
     *
     * <p>Three families of three, which is as many as a player can hold and
     * enough that the dark is different in three ways. Every number is
     * measured against the game's own mobs — see {@link #ZOMBIE} — because a
     * bestiary on its own scale is a bestiary that does not fit the game it
     * is in.
     *
     * <h2>The stats are chosen and the rewards are not</h2>
     *
     * Health, damage, speed and armour are a design decision: they are what a
     * fight <em>feels</em> like. Experience and drops do not appear in this
     * table at all. They are worked out from the threat those four come to,
     * tilted a little by where the beast sits in the order, so that a harder
     * thing is always worth more and the reward per unit of danger spans
     * {@link #WIDEST_SPREAD} across the whole bestiary rather than four times
     * over.
     *
     * <p>That is not how it was written first. The first table chose both
     * halves independently, and the harness found a brute that was harder than
     * a prowler and worth less, a best-thing-to-farm four times better than
     * the worst, and two beasts less dangerous than a zombie. The second table
     * derived the rewards but wrote them back here as literals, and the
     * literals promptly disagreed with the arithmetic that made them. All of
     * that is invisible to whoever wrote it, because all of it is ratios over
     * the whole set and nobody reads a list of mobs that way.
     */
    public static final List<Beast> BEASTS = List.of(
            //          id            family  tier   hp     dmg    speed   arm    wt   w      h
            new Beast("shardling", PROWLER, 1, 18.0D, 3.0D, 0.34D, 0.0D, 80, 0.6F, 1.1F),
            new Beast("ripper", PROWLER, 2, 26.0D, 5.0D, 0.38D, 2.0D, 45, 0.9F, 1.4F),
            new Beast("stalker", PROWLER, 3, 38.0D, 8.0D, 0.42D, 4.0D, 20, 1.1F, 1.8F),
            new Beast("husk_ox", BRUTE, 1, 30.0D, 4.0D, 0.22D, 2.0D, 55, 1.3F, 1.7F),
            new Beast("breaker", BRUTE, 2, 54.0D, 8.0D, 0.24D, 6.0D, 28, 1.6F, 2.3F),
            new Beast("siege_beast", BRUTE, 3, 90.0D, 12.0D, 0.26D, 10.0D, 10, 2.0F, 3.0F),
            new Beast("mourner", WIGHT, 1, 22.0D, 3.0D, 0.28D, 0.0D, 70, 0.6F, 1.95F),
            new Beast("keener", WIGHT, 2, 32.0D, 6.0D, 0.30D, 3.0D, 36, 0.7F, 2.2F),
            new Beast("hollow_lord", WIGHT, 3, 50.0D, 10.0D, 0.32D, 7.0D, 14, 0.9F, 2.6F));

    // ==================================================================
    // Working the reward out from the danger
    // ==================================================================

    /**
     * What one dropped item is worth, counted in experience.
     *
     * <p>A unit of account and nothing more. It exists so that "twenty
     * experience" and "seven items" can be added together into one number that
     * a farm would optimise, because a farm does not care which of the two it
     * is getting.
     */
    public static final int WORTH_OF_A_DROP = 3;

    /**
     * Reward per unit of danger at the bottom of the ladder.
     *
     * <p>Chosen so that the band the whole bestiary lands in <em>contains the
     * zombie's own rate</em>. Below it, every mob farm in the pack is a zombie
     * farm; far above it, the base game's mobs stop being worth fighting at
     * all and the early game loses its floor.
     */
    public static final double BASE_RATE = 0.088D;

    /**
     * How much better the reward per unit of danger gets one tier up.
     *
     * <p>Small, and deliberately so. It has to be positive, or there is no
     * reason to ever fight the harder thing when the easier thing is on the
     * same rate. It has to stay small, or {@link #WIDEST_SPREAD} breaks and
     * the top of the ladder becomes the only thing anybody farms.
     */
    public static final double TILT_PER_TIER = 0.06D;

    /** Roughly how much of a reward arrives as items rather than orbs. */
    public static final double DROP_SHARE = 0.30D;

    /** Everything in the dark leaves something. */
    public static final int LEAST_DROPS = 1;

    /** And gives at least one orb, or the experience bar lies about the kill. */
    public static final int LEAST_EXPERIENCE = 1;

    /**
     * The least anything can be worth, which is not a decision.
     *
     * <p>It is the two above added together at the price of a drop, because a
     * reward worth less than that cannot be split into an orb and an item at
     * all — and the first version of {@link #derive} did not check, so a beast
     * weaker than anything we ship would have been handed a negative number of
     * orbs. Nothing in this bestiary is anywhere near it. The next one might
     * be.
     */
    public static final int LEAST_WORTH = LEAST_EXPERIENCE + LEAST_DROPS * WORTH_OF_A_DROP;

    /**
     * What every beast earns, worked out from what it costs to kill.
     *
     * <p>Walks the bestiary in order of danger and hands each beast a reward
     * of about {@code threat * rate(tier)}, rounded to a whole number — and
     * then, if that rounds onto the reward of the thing below it, adds one.
     * That last clause is the whole reason this is a loop rather than a
     * formula: rounding two nearly equally dangerous beasts to the same whole
     * number is exactly the "harder and worth no more" failure, and it appears
     * at the bottom of the ladder where the numbers are small and the rounding
     * is coarse. Forcing the order here means {@link #notWorthIt()} is empty
     * by construction rather than by luck.
     *
     * <p>The whole number is then split: about {@link #DROP_SHARE} of it into
     * items at {@link #WORTH_OF_A_DROP} each, the remainder into orbs — but
     * never more items than the reward can pay for and still leave an orb,
     * which is what {@link #LEAST_WORTH} is for.
     */
    public static Map<String, Reward> derive(List<Beast> beasts) {
        List<Beast> order = new ArrayList<>(beasts);
        order.sort((first, second) -> Double.compare(first.threat(), second.threat()));
        Map<String, Reward> found = new LinkedHashMap<>();
        long below = 0L;
        for (Beast beast : order) {
            double rate = BASE_RATE * (1.0D + TILT_PER_TIER * (beast.tier() - 1));
            long worth = Math.max(Math.max(Math.round(beast.threat() * rate), below + 1L),
                    LEAST_WORTH);
            below = worth;
            long affordable = (worth - LEAST_EXPERIENCE) / WORTH_OF_A_DROP;
            long wanted = (long) (worth * DROP_SHARE / WORTH_OF_A_DROP);
            int drops = (int) Math.max(LEAST_DROPS, Math.min(affordable, wanted));
            int experience = (int) (worth - (long) drops * WORTH_OF_A_DROP);
            found.put(beast.id(), new Reward(experience, drops));
        }
        return Map.copyOf(found);
    }

    private static final Map<String, Reward> REWARDS = derive(BEASTS);

    /** What this beast earns. */
    public static Reward earned(Beast beast) {
        Reward found = REWARDS.get(beast.id());
        if (found == null) {
            throw new IllegalArgumentException(beast.id() + " is not in the bestiary, "
                    + "so nothing has worked out what it is worth");
        }
        return found;
    }

    /** What the beast with this id earns, or null if there is no such beast. */
    public static Reward earned(String id) {
        return REWARDS.get(id);
    }

    /** Reward per unit of danger, which is the number a farm optimises. */
    public static double rate(Beast beast, Reward reward) {
        return reward.worth() / beast.threat();
    }

    // ==================================================================
    // The game's own scale
    // ==================================================================

    /**
     * The game's own mob, for scale.
     *
     * <p>Every beast here has to be at least as dangerous as a zombie —
     * otherwise it is a thing in the dark that nobody has to think about —
     * and none may be more than {@link #MOST_TIMES_A_ZOMBIE} times as
     * dangerous, because at that point it is not a mob, it is an event.
     */
    public static final Beast ZOMBIE =
            new Beast("zombie", BRUTE, 1, 20.0D, 3.0D, 0.23D, 2.0D, 100, 0.6F, 1.95F);

    /**
     * And what the game pays for it.
     *
     * <p>Five orbs and about one piece of rotten flesh. Written out rather
     * than derived, because it is a measurement of somebody else's game and
     * not a decision of ours.
     */
    public static final Reward ZOMBIE_EARNS = new Reward(5, 1);

    /** How much worse than a zombie anything here is allowed to be. */
    public static final double MOST_TIMES_A_ZOMBIE = 30.0D;

    /**
     * How far apart the best and worst thing to farm may be.
     *
     * <p>One and a half. Below about that a player cannot tell, and above it
     * every grinder in the pack becomes a grinder for one mob and the rest of
     * the bestiary is scenery. It is a band rather than a point because
     * identical rates would make the choice of what to farm meaningless in
     * the other direction.
     */
    public static final double WIDEST_SPREAD = 1.5D;

    // ==================================================================
    // The properties
    // ==================================================================

    /**
     * Every beast that is harder than another and worth less.
     *
     * <p>Must be empty. A mob like this is one every player works out inside a
     * week and then never fights again.
     */
    public static List<String> notWorthIt() {
        List<String> caught = new ArrayList<>();
        for (Beast beast : BEASTS) {
            for (Beast other : BEASTS) {
                if (beast.threat() > other.threat()
                        && earned(beast).worth() <= earned(other).worth()) {
                    caught.add(beast.id() + " is harder than " + other.id()
                            + " and worth no more");
                }
            }
        }
        return caught;
    }

    /** How far apart the best and worst reward per unit of danger are. */
    public static double spread() {
        double least = Double.MAX_VALUE;
        double most = 0.0D;
        for (Beast beast : BEASTS) {
            double rate = rate(beast, earned(beast));
            least = Math.min(least, rate);
            most = Math.max(most, rate);
        }
        return least <= 0.0D ? Double.MAX_VALUE : most / least;
    }

    /**
     * Whether the game's own zombie pays better, or worse, than everything we
     * added.
     *
     * <p>Empty means the zombie's rate sits inside the band the bestiary
     * spans, which is the only arrangement in which a player picks what to
     * farm for a reason other than arithmetic.
     */
    public static List<String> outfarmed() {
        double least = Double.MAX_VALUE;
        double most = 0.0D;
        for (Beast beast : BEASTS) {
            double rate = rate(beast, earned(beast));
            least = Math.min(least, rate);
            most = Math.max(most, rate);
        }
        double zombie = rate(ZOMBIE, ZOMBIE_EARNS);
        List<String> caught = new ArrayList<>();
        if (zombie > most) {
            caught.add("a zombie pays better per unit of danger than anything in the "
                    + "bestiary, so every mob farm in the pack is a zombie farm");
        }
        if (zombie < least) {
            caught.add("a zombie pays worse per unit of danger than everything in the "
                    + "bestiary, so the game's own mobs stop being worth fighting");
        }
        return caught;
    }

    /**
     * Whether climbing the ladders pays at all.
     *
     * <p>{@link #spread()} proves no rung is so much the best to farm that the
     * others are scenery. It says nothing about the other direction, and the
     * other direction is worse: a bestiary where the top of every ladder pays
     * the same per unit of danger as the bottom is a bestiary where getting
     * stronger buys you nothing, and every player settles on the safest thing
     * that drops anything and never looks up again.
     *
     * <p>It is an average over a tier rather than a rung-by-rung comparison
     * on purpose. Rounding a small reward to a whole number moves its rate by
     * several per cent, and inside a band this tight that is enough to put one
     * tier-one beast above one tier-two beast without anything being wrong.
     * What must not happen is the tiers as a whole failing to rise.
     */
    public static List<String> noReasonToClimb() {
        int highest = 1;
        for (Beast beast : BEASTS) {
            highest = Math.max(highest, beast.tier());
        }
        double bottom = averageRate(1);
        double top = averageRate(highest);
        List<String> caught = new ArrayList<>();
        if (top <= bottom) {
            caught.add("the top of the ladders pays " + top + " per unit of danger and "
                    + "the bottom pays " + bottom + ", so getting stronger buys nothing "
                    + "and every player settles on the safest thing that drops anything");
        }
        return caught;
    }

    /** What one rung of every ladder pays, per unit of danger, on average. */
    public static double averageRate(int tier) {
        double total = 0.0D;
        int many = 0;
        for (Beast beast : BEASTS) {
            if (beast.tier() == tier) {
                total += rate(beast, earned(beast));
                many++;
            }
        }
        return many == 0 ? 0.0D : total / many;
    }

    /** Every beast outside the game's own scale. */
    public static List<String> offTheScale() {
        List<String> caught = new ArrayList<>();
        for (Beast beast : BEASTS) {
            if (beast.threat() < ZOMBIE.threat()) {
                caught.add(beast.id() + " is less dangerous than a zombie");
            }
            if (beast.threat() > ZOMBIE.threat() * MOST_TIMES_A_ZOMBIE) {
                caught.add(beast.id() + " is more than " + MOST_TIMES_A_ZOMBIE
                        + " zombies, which is not a mob, it is an event");
            }
        }
        return caught;
    }

    /** Every family's beasts, in tier order. */
    public static List<Beast> family(String family) {
        List<Beast> found = new ArrayList<>();
        for (Beast beast : BEASTS) {
            if (beast.family().equals(family)) {
                found.add(beast);
            }
        }
        found.sort((first, second) -> Integer.compare(first.tier(), second.tier()));
        return List.copyOf(found);
    }

    /** What share of spawns each beast takes. */
    public static Map<String, Double> shares() {
        int total = 0;
        for (Beast beast : BEASTS) {
            total += beast.weight();
        }
        Map<String, Double> found = new LinkedHashMap<>();
        for (Beast beast : BEASTS) {
            found.put(beast.id(), (double) beast.weight() / total);
        }
        return Map.copyOf(found);
    }

    /** The most any one beast may be of what a player meets. */
    public static final double MOST_OF_THE_DARK = 0.40D;

    /** Every beast that is too much, or too little, of what you meet. */
    public static List<String> lopsided() {
        List<String> caught = new ArrayList<>();
        for (Map.Entry<String, Double> share : shares().entrySet()) {
            if (share.getValue() > MOST_OF_THE_DARK) {
                caught.add(share.getKey() + " is " + Math.round(share.getValue() * 100)
                        + "% of everything you meet");
            }
            if (share.getValue() < 0.01D) {
                caught.add(share.getKey() + " is under one per cent of what you meet, "
                        + "so most players never see one");
            }
        }
        return caught;
    }

    /** Every id the module registers. */
    public static List<String> catalogue() {
        List<String> names = new ArrayList<>(BEASTS.size());
        for (Beast beast : BEASTS) {
            names.add(beast.id());
        }
        return List.copyOf(names);
    }

    // ==================================================================
    // What each family leaves behind
    // ==================================================================

    /**
     * The three things a family drops.
     *
     * @param staple  the common one, which every tier leaves
     * @param essence the one that says which family it was
     * @param rare    one per tier, hardest last
     */
    public record Leavings(String staple, String essence, List<String> rare) {

        public Leavings {
            Objects.requireNonNull(staple, "a family that leaves nothing common");
            Objects.requireNonNull(essence, "a family with no essence");
            Objects.requireNonNull(rare, "a family with no rare drop at any tier");
            rare = List.copyOf(rare);
        }

        /** Everything this family can leave, at any tier. */
        public List<String> everything() {
            List<String> all = new ArrayList<>();
            all.add(staple);
            all.add(essence);
            all.addAll(rare);
            return List.copyOf(all);
        }
    }

    /**
     * Which things each family leaves.
     *
     * <p>These lived in the generator until the fauna arrived. That was fine
     * while nothing else in the pack cared what a beast dropped, and stopped
     * being fine the moment something did: a rule that says "no animal you can
     * keep in a pen leaves what something in the dark leaves" cannot be
     * written against a table in a Python file, and a drop nothing can reason
     * about is a drop that can quietly become the thing that makes a whole
     * ladder pointless.
     *
     * <p>They are also the only place in this module where an item is named by
     * hand, which is why {@code validate.py} looks every one of them up in the
     * game's own language file.
     */
    public static final Map<String, Leavings> LEFT_BY = Map.of(
            PROWLER, new Leavings("minecraft:string", "minecraft:spider_eye",
                    List.of("minecraft:iron_nugget", "minecraft:iron_ingot",
                            "minecraft:diamond")),
            BRUTE, new Leavings("minecraft:bone", "minecraft:leather",
                    List.of("minecraft:flint", "minecraft:gold_ingot",
                            "minecraft:emerald")),
            WIGHT, new Leavings("minecraft:rotten_flesh", "minecraft:glowstone_dust",
                    List.of("minecraft:coal", "minecraft:amethyst_shard",
                            "minecraft:echo_shard")));

    /** The three things this beast leaves, in the order the pools are written. */
    public static List<String> leaves(Beast beast) {
        Leavings left = LEFT_BY.get(beast.family());
        if (left == null) {
            return List.of();
        }
        List<String> rare = left.rare();
        int at = Math.max(0, Math.min(rare.size() - 1, beast.tier() - 1));
        return List.of(left.staple(), left.essence(), rare.get(at));
    }

    /**
     * Whether the three families leave three different sets of things.
     *
     * <p>Handed the table so a control can pass one built to be caught. Two
     * families that leave the same thing are two families a player farms the
     * same way, and the whole reason there are three of them is that they are
     * three different reasons to go out at night.
     */
    public static List<String> faultsInLeavings(Map<String, Leavings> left) {
        List<String> bad = new ArrayList<>();
        for (String family : FAMILIES) {
            if (!left.containsKey(family)) {
                bad.add("nothing says what a " + family + " leaves, so every "
                        + "one of them drops whatever the generator felt like "
                        + "on the day and no rule anywhere can see it");
            }
        }
        int rungs = family(FAMILIES.get(0)).size();
        for (Map.Entry<String, Leavings> entry : left.entrySet()) {
            if (entry.getValue().rare().size() != rungs) {
                bad.add(entry.getKey() + " has " + entry.getValue().rare().size()
                        + " rare drop(s) for " + rungs + " tier(s), so one tier "
                        + "leaves what another leaves and climbing buys nothing");
            }
        }
        Map<String, String> seen = new LinkedHashMap<>();
        for (Map.Entry<String, Leavings> entry : left.entrySet()) {
            for (String thing : entry.getValue().everything()) {
                String already = seen.put(thing, entry.getKey());
                if (already != null && !already.equals(entry.getKey())) {
                    bad.add(thing + " is left by a " + already + " and by a "
                            + entry.getKey() + ", so two of the three families "
                            + "are one family as far as a player farming them "
                            + "is concerned");
                }
            }
        }
        return bad;
    }

    /** The beast with this id, or null. */
    public static Beast beast(String id) {
        for (Beast beast : BEASTS) {
            if (beast.id().equals(id)) {
                return beast;
            }
        }
        return null;
    }
}
