package com.elysium.agronomy.rules;

import com.elysium.industry.rules.IndustryRules;

import java.util.List;

/**
 * Things that grow, in numbers. The JDK and Age II's grid, and nothing else.
 *
 * <h2>The one question this module has to answer honestly</h2>
 *
 * Growing resources is the feature that most often ruins a modpack. A seed
 * that yields iron turns a world into a field: mining stops, the ore ladder
 * that three ages were built around stops mattering, and everything priced
 * against scarce metal becomes free. Every pack that has shipped Mystical
 * Agriculture unbalanced has learned this the same way.
 *
 * <p>So the rule is written down and <em>executed</em>: an acre of seeds must
 * be worse than a mine. Not forbidden, not nerfed into pointlessness — worse,
 * by a stated margin, per block of space and per hour, against the best
 * processing the ages offer. {@link #farmedPerHour} and {@link #minedPerHour}
 * exist so the harness can compare them on every build, and if somebody
 * retunes a growth time or a yield the build stops rather than the pack
 * quietly becoming a farming simulator.
 *
 * <h2>What else is in here</h2>
 *
 * A pot, which is farming without a field; a hive, which is farming without
 * either; a rock generator, which is the oldest cheat in Minecraft made
 * explicit; and a diet, which is the one mechanic in this module that changes
 * how a player <em>plays</em> rather than what they own.
 */
public final class AgronomyRules {

    private AgronomyRules() {
    }

    // ==================================================================
    // Growing
    // ==================================================================

    /** Ticks in an hour of play, which is the unit the comparisons use. */
    public static final int TICKS_PER_HOUR = 72_000;

    /**
     * How fast a crop grows, as a fraction of its whole per tick.
     *
     * @param tier     which rung of the essence ladder it sits on
     * @param ticks    how long one growth takes in ideal conditions
     * @param yield    how many essences one harvest gives
     */
    public record Crop(int tier, int ticks, int yield) {

        public Crop {
            if (tier < 1) {
                throw new IllegalArgumentException("a crop below the first tier is not a "
                        + "crop, it is a plant");
            }
            if (ticks <= 0) {
                throw new IllegalArgumentException("a crop that grows instantly is a "
                        + "crafting recipe");
            }
            if (yield <= 0) {
                throw new IllegalArgumentException("a crop that yields nothing is a "
                        + "decoration");
            }
        }

        /** Harvests an hour, in ideal conditions. */
        public float perHour() {
            return (float) TICKS_PER_HOUR / ticks;
        }
    }

    /** How many tiers of essence there are. */
    public static final int TIERS = 5;

    /**
     * How much slower each tier grows than the one below.
     *
     * <p>Growth time is what the ladder is made of, not yield: a tier five
     * crop gives the same handful of essence as a tier one, it simply takes an
     * age. That way a bigger farm is always the answer to wanting more, and a
     * bigger farm costs space and power — both of which are things the rest of
     * the pack can price.
     */
    public static final float TIER_SLOWER = 2.0F;

    /**
     * The first rung: something you can grow on day two.
     *
     * <p>Twelve thousand ticks is half a Minecraft day, which is slow enough
     * that nobody stands and watches it and fast enough that a player who
     * plants a row before going mining comes back to something. The number was
     * arrived at from the other end: the harness compares a farm against a
     * mine and this is what keeps a room full of the first tier under the
     * margin, so it is a consequence of the design rule rather than a taste.
     */
    public static final Crop FIRST_CROP = new Crop(1, 12_000, 1);

    /** How long a crop of a given tier takes, in ticks. */
    public static int ticksAt(int tier) {
        float ticks = FIRST_CROP.ticks();
        for (int step = 1; step < Math.max(1, Math.min(TIERS, tier)); step++) {
            ticks *= TIER_SLOWER;
        }
        return Math.round(ticks);
    }

    /** The crop at a given tier. */
    public static Crop cropAt(int tier) {
        return new Crop(Math.max(1, Math.min(TIERS, tier)), ticksAt(tier),
                FIRST_CROP.yield());
    }

    /** How many essences of one tier make a seed of the next. */
    public static final int ESSENCE_PER_STEP = 4;

    /** How many essences it takes to make one of the material itself. */
    public static final int ESSENCE_PER_INGOT = 8;

    /**
     * How much of a material an acre of seeds yields in an hour.
     *
     * @param tier    the tier the material sits at
     * @param plots   how many growing spaces the farm has
     */
    public static float farmedPerHour(int tier, int plots) {
        Crop crop = cropAt(tier);
        float essence = crop.perHour() * crop.yield() * Math.max(0, plots);
        return essence / ESSENCE_PER_INGOT;
    }

    // ==================================================================
    // The mine it has to lose to
    // ==================================================================

    /**
     * Ores of one particular material a player breaks in an hour.
     *
     * <p>Of <em>one</em> material, not of all of them: a farm grows a chosen
     * thing, so the mine it has to lose to is an hour spent looking for that
     * same thing. Counting every ore a player walks past would be comparing a
     * copper farm against a mine's worth of everything and would let a farm
     * win while appearing to lose.
     */
    public static final int ORES_PER_HOUR = 80;

    /**
     * How much of a material an hour of mining yields, through the best
     * processing the ages offer.
     *
     * The multiplier is {@code elysium-atomics}' — three — but this module
     * does not depend on Age III, so it is stated here as the ceiling the
     * spine has already committed to and checked against Age II's, which this
     * module does see. The harness asserts the two agree.
     */
    public static final int BEST_MULTIPLIER = 3;

    public static float minedPerHour() {
        return (float) ORES_PER_HOUR * BEST_MULTIPLIER;
    }

    /**
     * How much worse a farm must be than a mine, for the same hour.
     *
     * <p>Not a nerf — a <em>margin</em>. A farm is automatic, it needs no
     * caves and no risk, and it runs while a player is asleep, so it is
     * allowed to be attractive. What it is not allowed to be is <b>better</b>,
     * because the moment it is, three ages of ore ladder stop being a ladder.
     *
     * <p>Two thirds, which leaves a farm genuinely worth building for anything
     * a player does not want to go and find.
     */
    public static final float FARM_AT_MOST = 0.66F;

    /**
     * The largest farm of a given tier that stays under that margin.
     *
     * What the number is <em>for</em>: not to stop a player building a bigger
     * one, but so the harness can say out loud how big "too big" is, and so a
     * later change to a growth time makes that number move where somebody can
     * see it.
     */
    public static int plotsBefore(int tier) {
        float allowed = minedPerHour() * FARM_AT_MOST;
        float each = farmedPerHour(tier, 1);
        return each <= 0.0F ? Integer.MAX_VALUE : (int) Math.floor(allowed / each);
    }

    // ==================================================================
    // A pot, which is farming without a field
    // ==================================================================

    /** The light a pot wants before anything in it grows at all. */
    public static final int LEAST_LIGHT = 9;

    /** How much faster a pot is than the ground, for the trouble of building one. */
    public static final float POT_FASTER = 1.5F;

    /**
     * What an accelerator draws to hurry a pot along.
     *
     * A machine on Age II's grid like everything else in this pack: constant
     * draw, share of full speed, slow rather than stopped.
     */
    public static final IndustryRules.Machine ACCELERATOR =
            new IndustryRules.Machine(48.0F, IndustryRules.Voltage.LOW);

    /** How much faster an accelerator at a full share makes a pot. */
    public static final float ACCELERATOR_FASTER = 4.0F;

    /**
     * How fast one pot grows, all told.
     *
     * @param light      the light it sits in
     * @param accelerated whether an accelerator is on it
     * @param share      what the grid can afford, if it is
     */
    public static float potSpeed(int light, boolean accelerated, float share) {
        if (light < LEAST_LIGHT) {
            return 0.0F;
        }
        float speed = POT_FASTER;
        if (accelerated) {
            float fed = Math.max(0.0F, Math.min(1.0F, share));
            speed *= 1.0F + (ACCELERATOR_FASTER - 1.0F) * fed;
        }
        return speed;
    }

    /**
     * Work done in one tick by a pot growing at speed one.
     *
     * The same device Age II uses for its machines, and for the same reason: a
     * speed that changes halfway through — a grid browning out under an
     * accelerator, a torch being mined — should neither invent progress nor
     * throw it away, and a countdown cannot express that.
     */
    public static final int WORK_FULL = 100;

    /** The whole of a growth that takes this many ticks at speed one. */
    public static int workFor(int ticks) {
        return Math.max(1, ticks) * WORK_FULL;
    }

    /** What a pot growing at this speed gets through in one tick. */
    public static int workPerTick(float speed) {
        if (speed <= 0.0F) {
            return 0;
        }
        return Math.max(1, Math.round(speed * WORK_FULL));
    }

    /** The same thing said as a countdown, for anything that has to show one. */
    public static int ticksAtSpeed(int ticks, float speed) {
        int perTick = workPerTick(speed);
        if (perTick <= 0) {
            return Integer.MAX_VALUE;
        }
        int work = workFor(ticks);
        return (work + perTick - 1) / perTick;
    }

    // ==================================================================
    // A hive, which is farming without either
    // ==================================================================

    /** The most bees one hive will hold. */
    public static final int MOST_BEES = 12;

    /** Flowers a bee wants to itself before the hive slows down. */
    public static final int FLOWERS_PER_BEE = 2;

    /** How long one bee takes to make one comb, in ticks, with flowers enough. */
    public static final int COMB_TICKS = 3_000;

    /**
     * How many combs an hour a hive makes.
     *
     * <p>Crowding is a fraction rather than a stop, for the reason everything
     * in this pack is: a player who adds one bee too many should see their
     * hive get slower, notice, and plant something — not find it switched off
     * with no explanation.
     */
    public static float combsPerHour(int bees, int flowers) {
        int working = Math.max(0, Math.min(MOST_BEES, bees));
        if (working == 0) {
            return 0.0F;
        }
        float wanted = (float) working * FLOWERS_PER_BEE;
        float crowding = wanted <= 0.0F ? 1.0F
                : Math.min(1.0F, Math.max(0, flowers) / wanted);
        return working * ((float) TICKS_PER_HOUR / COMB_TICKS) * crowding;
    }

    // ==================================================================
    // Rock, which is the oldest cheat in the game
    // ==================================================================



    /** How many blocks of stone a generator makes an hour at each tier. */
    public static final int[] ROCK_PER_HOUR = {900, 3_600, 14_400};

    /** What a rock generator of a given tier draws, or nothing at tier one. */
    public static IndustryRules.Machine rockDraw(int tier) {
        if (tier <= 0) {
            return new IndustryRules.Machine(0.0F, IndustryRules.Voltage.LOW);
        }
        return new IndustryRules.Machine(32.0F * (1 << tier), IndustryRules.Voltage.LOW);
    }

    /** How much rock a generator of this tier makes an hour, at this share. */
    public static float rockPerHour(int tier, float share) {
        int rung = Math.max(0, Math.min(ROCK_PER_HOUR.length - 1, tier));
        float fed = rung == 0 ? 1.0F : Math.max(0.0F, Math.min(1.0F, share));
        return ROCK_PER_HOUR[rung] * fed;
    }

    /**
     * A generator tuned to one material.
     *
     * <p>The mod this replaces ships seven of these — stone, copper, iron,
     * gold, diamond, emerald, netherite — and a generator that hands you
     * netherite is the single most direct way there is to make three ages of
     * ore ladder pointless. So each one carries the share of a mine it is
     * allowed to be worth, and {@link #veinsOutmining} refuses the build if
     * any of them exceeds it.
     *
     * @param material what it makes
     * @param tier     which rung of generator it needs
     * @param share    how much of a mine's hourly output it may be worth
     */
    public record Vein(String material, int tier, float share) {

        public Vein {
            if (material == null || material.isEmpty()) {
                throw new IllegalArgumentException("a vein of nothing");
            }
            if (tier < 0 || tier >= ROCK_PER_HOUR.length) {
                throw new IllegalArgumentException(material + " needs a generator at "
                        + "tier " + tier + " and there are only "
                        + ROCK_PER_HOUR.length);
            }
            if (share <= 0.0F || share > 1.0F) {
                throw new IllegalArgumentException(material + " is worth " + share
                        + " of a mine, and the band is above nothing and at most all "
                        + "of one");
            }
        }

        /** What this vein makes in an hour at a full share of the grid. */
        public float perHour() {
            return minedPerHour() * share;
        }
    }

    /**
     * Every vein there is.
     *
     * <p>Stone is free and worth nothing, which is the honest version of the
     * oldest trick in the game. Everything above it costs power, needs a
     * dearer generator, and is worth a fraction of going to find it — a
     * fraction that shrinks as the material gets better, because the whole
     * point of a deep mine is that the good things are deep.
     */
    public static final List<Vein> VEINS = List.of(
            new Vein("stone", 0, 1.00F),
            new Vein("copper", 1, 0.30F),
            new Vein("iron", 1, 0.22F),
            new Vein("gold", 2, 0.15F),
            new Vein("diamond", 2, 0.06F),
            new Vein("emerald", 2, 0.05F),
            new Vein("netherite_scrap", 2, 0.02F));

    /** How much of a mine any vein above stone may be worth. */
    public static final float VEIN_AT_MOST = 0.35F;

    /** Every vein that out-earns going to find the stuff. */
    public static List<String> veinsOutmining() {
        List<String> bad = new java.util.ArrayList<>();
        for (Vein vein : VEINS) {
            if ("stone".equals(vein.material())) {
                continue;
            }
            if (vein.share() > VEIN_AT_MOST) {
                bad.add(vein.material() + " is worth " + vein.share()
                        + " of a mine, and the line is " + VEIN_AT_MOST);
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Every pair of veins where the better material is not the rarer one.
     *
     * <p>The ordering is the rule, not the numbers: whatever the shares are,
     * a generator must never make a dearer thing faster than a cheaper one, or
     * the cheap ones are decoration.
     */
    public static List<String> veinsOutOfOrder() {
        List<String> bad = new java.util.ArrayList<>();
        for (int index = 1; index < VEINS.size(); index++) {
            Vein above = VEINS.get(index);
            Vein below = VEINS.get(index - 1);
            if (above.share() >= below.share()) {
                bad.add(above.material() + " comes out as fast as " + below.material()
                        + " or faster, so the cheaper vein is decoration");
            }
            if (above.tier() < below.tier()) {
                bad.add(above.material() + " needs a cheaper generator than "
                        + below.material() + ", so the ladder runs backwards");
            }
        }
        return List.copyOf(bad);
    }

    /** Every vein the generator ladder has no rung for. */
    public static List<String> veinsWithNoGenerator() {
        List<String> bad = new java.util.ArrayList<>();
        for (Vein vein : VEINS) {
            if (vein.tier() >= ROCK_PER_HOUR.length) {
                bad.add(vein.material() + " needs a generator nobody can build");
            }
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // A diet, which changes how a player plays
    // ==================================================================

    /**
     * How many different foods it takes to reach the whole bonus.
     *
     * <p>The one mechanic here that is about the player rather than their
     * base. Thirty is chosen so that a player who cooks reaches it over a
     * playthrough and a player who lives on bread never does — and so that the
     * cooking half of this module has something to be for beyond flavour.
     */
    public static final int FOODS_FOR_FULL = 30;

    /** The most extra hearts a varied diet is worth. */
    public static final int MOST_EXTRA_HEARTS = 10;

    /**
     * The extra hearts a player who has eaten this many different foods has.
     *
     * <h2>Why it curves</h2>
     *
     * A straight line would make the thirtieth food worth as much as the
     * first, which is the opposite of how anybody experiences it: the first
     * few new things are a discovery and the last few are a checklist. A
     * square root front-loads the reward, so a player who tries ten things has
     * most of what is on offer and the last twenty are for completionists.
     *
     * <p>It never goes down and it never exceeds the cap, and both of those
     * are checked over every count rather than assumed — a health bonus that
     * could shrink is a player losing hearts for eating.
     */
    public static float extraHearts(int foods) {
        int tried = Math.max(0, Math.min(FOODS_FOR_FULL, foods));
        float through = (float) Math.sqrt((double) tried / FOODS_FOR_FULL);
        return through * MOST_EXTRA_HEARTS;
    }

    /** The same thing in half-hearts, which is what an attribute wants. */
    public static float extraHealth(int foods) {
        return extraHearts(foods) * 2.0F;
    }

    // ==================================================================
    // The frontier
    // ==================================================================

    /**
     * Which worlds grow what, by the air they have.
     *
     * <p>The space crops the pack was asked for. A crop that grew anywhere
     * would make the frontier worlds decoration; one that grew nowhere but its
     * own world would make them a chore. So each is tied to a condition a
     * player can read off the world they are standing on, and the list lives
     * here rather than in a data file so the harness can check nothing grows
     * everywhere.
     */
    public record Frontier(String world, int leastLight, int mostLight,
                           boolean needsVacuum) {

        /**
         * Whether this crop would grow absolutely anywhere.
         *
         * Which is the one thing a frontier crop may not do: a crop with no
         * condition on it is not a reason to go to another world, it is a
         * crop, and the worlds go back to being scenery.
         */
        public boolean growsAnywhere() {
            return leastLight <= 0 && mostLight >= 15 && !needsVacuum;
        }

        /** Whether it would grow in the light and the air of this reading. */
        public boolean happy(int light, boolean vacuum) {
            return light >= leastLight && light <= mostLight
                    && (vacuum || !needsVacuum);
        }
    }

    public static final List<Frontier> FRONTIERS = List.of(
            new Frontier("luna", 0, 15, true),
            new Frontier("cinder", 12, 15, false),
            new Frontier("frost", 4, 15, false),
            new Frontier("pale", 0, 4, false),
            new Frontier("bastion", 9, 15, false));
}
