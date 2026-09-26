package com.elysium.bestiary.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Where each beast is, and whether it can be there at all.
 *
 * <h2>What was missing</h2>
 *
 * {@link BeastRules} prices nine beasts against the zombie, proves none of
 * them is worth more per unit of danger than the game's own, and shares out
 * how often each is chosen. All of which is about <em>whether you should
 * fight one</em>, and none of which is about whether you ever meet one.
 *
 * <p>A mob with a spawn that cannot happen is the quietest bug a pack has. It
 * is registered, it renders, its loot table is right, the harness passes, and
 * the only symptom is that in four hundred hours nobody has seen it. So a
 * haunt says four things — where, in what light, at what height, and how many
 * — and every one of them is checked against a world that really exists.
 *
 * <h2>And how many fit</h2>
 *
 * The one that is easy to get wrong in the other direction: a pack of eight
 * is fine for something the size of a wolf and impossible for something the
 * size of a house, and the failure is not that it looks silly — it is that
 * the game gives up trying to place them and the mob spawns at a fraction of
 * the rate its weight says it should. So the group size is checked against
 * the room the beast takes up.
 */
public final class HauntRules {

    private HauntRules() {
    }

    // ==================================================================
    // The world a haunt has to fit in
    // ==================================================================

    /** The lowest block of the overworld. */
    public static final int BEDROCK = -64;

    /** The highest. */
    public static final int SKY = 320;

    /** The darkest a block can be. */
    public static final int PITCH_DARK = 0;

    /** The brightest. */
    public static final int FULL_LIGHT = 15;

    /**
     * How much floor one spawn attempt has to play with, in square blocks.
     *
     * <p>Not a rule the game states; a floor on how cramped a pack may be.
     * The game tries a handful of positions near the first one and gives up,
     * so a group that cannot physically fit in that area spawns at a fraction
     * of the rate its weight promises — which reads to a player as a mob that
     * is rarer than the wiki says, and to a designer as a weight that does
     * not work.
     */
    public static final double ROOM_FOR_A_PACK = 144.0D;

    // ==================================================================
    // A haunt
    // ==================================================================

    /**
     * Where one beast is found.
     *
     * @param beast    which beast
     * @param where    the biome tag it is allowed in
     * @param darkest  the lowest light level it will spawn at
     * @param brightest the highest
     * @param lowest   the lowest height
     * @param highest  the highest
     * @param least    the smallest group
     * @param most     the largest
     */
    public record Haunt(String beast, String where, int darkest, int brightest,
                        int lowest, int highest, int least, int most) {

        public Haunt {
            Objects.requireNonNull(beast, "a haunt of nothing");
            Objects.requireNonNull(where, "a haunt nowhere");
            if (where.isEmpty()) {
                throw new IllegalArgumentException(beast + " haunts nowhere");
            }
            if (least <= 0 || most < least) {
                throw new IllegalArgumentException(beast + " comes in groups of "
                        + least + " to " + most + ", which is not a group");
            }
        }

        /** How many blocks of light it tolerates. */
        public int lightBand() {
            return brightest - darkest + 1;
        }

        /** How many blocks of height it has. */
        public int heightBand() {
            return highest - lowest + 1;
        }
    }

    /**
     * Where the nine are.
     *
     * <p>Read as a table: prowlers are everywhere and shallow, brutes are deep
     * and few, wights are in the cold and the dark. Each family reads as one
     * sentence, which is what makes a bestiary something a player can hold in
     * their head rather than a list they look up.
     *
     * <p>Every tag here is one the game itself ships, checked against its own
     * jar by {@code validate.py}. A tag nothing fills is a mob that never
     * spawns, and it looks exactly like a mob that spawns rarely — which is
     * why {@code #c:is_cold} is not here: it reads like a tag and the game
     * does not have one.
     *
     * <p>And one beast of each family is somewhere a player certainly goes.
     * A roster where every mob wants a particular biome is a roster most
     * players never meet; a roster where every mob is everywhere is nine
     * strangers with no sense of place. One of each, and the rest have
     * somewhere to be.
     */
    public static final List<Haunt> HAUNTS = List.of(
            new Haunt("shardling", "#minecraft:is_overworld", 0, 7, 0, SKY, 2, 4),
            new Haunt("ripper", "#minecraft:is_forest", 0, 7, 0, SKY, 2, 3),
            new Haunt("stalker", "#minecraft:is_mountain", 0, 4, 60, SKY, 1, 2),
            new Haunt("husk_ox", "#minecraft:is_badlands", 0, 15, BEDROCK, 96, 1, 3),
            new Haunt("breaker", "#minecraft:is_overworld", 0, 4, BEDROCK, 32, 1, 2),
            new Haunt("siege_beast", "#minecraft:is_nether", 0, 11, 0, 128, 1, 1),
            new Haunt("mourner", "#minecraft:spawns_snow_foxes", 0, 7, 0, SKY, 1, 3),
            new Haunt("keener", "#minecraft:is_taiga", 0, 4, 0, SKY, 1, 2),
            new Haunt("hollow_lord", "#minecraft:is_overworld", 0, 0, BEDROCK, 0, 1, 1));

    /** The haunt of one beast, or null. */
    public static Haunt haunt(String beast) {
        for (Haunt one : HAUNTS) {
            if (one.beast().equals(beast)) {
                return one;
            }
        }
        return null;
    }

    /** Every biome tag any of them names. */
    public static List<String> everywhere() {
        Set<String> found = new LinkedHashSet<>();
        for (Haunt one : HAUNTS) {
            found.add(one.where());
        }
        return List.copyOf(found);
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the haunts. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInCover(BeastRules.BEASTS, HAUNTS));
        bad.addAll(faultsInBands(HAUNTS));
        bad.addAll(faultsInRoom(BeastRules.BEASTS, HAUNTS));
        bad.addAll(faultsInDominance(BeastRules.BEASTS));
        return List.copyOf(bad);
    }

    /**
     * Whether every beast is somewhere and everything somewhere is a beast.
     *
     * <p>Handed both sides so a control can pass a pair built to be caught.
     */
    public static List<String> faultsInCover(List<BeastRules.Beast> beasts,
                                             List<Haunt> haunts) {
        List<String> bad = new ArrayList<>();
        Set<String> named = new LinkedHashSet<>();
        for (BeastRules.Beast beast : beasts) {
            named.add(beast.id());
            boolean any = false;
            for (Haunt one : haunts) {
                any |= one.beast().equals(beast.id());
            }
            if (!any) {
                bad.add(beast.id() + " is registered, rendered, priced and has "
                        + "a loot table, and nothing says where it is — which "
                        + "is a mob nobody meets in four hundred hours and "
                        + "nothing anywhere reports");
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Haunt one : haunts) {
            if (!seen.add(one.beast())) {
                bad.add(one.beast() + " is in two places, so one of the two is "
                        + "a rule the other overwrites");
            }
            if (!named.contains(one.beast())) {
                bad.add(one.beast() + " is somewhere and is not a beast");
            }
        }
        return bad;
    }

    /**
     * Whether each band is a band a world really has.
     *
     * <p>The check that catches the quietest bug there is: a light band that
     * is empty, or a height band above the sky, is a mob that never spawns
     * and never says so.
     */
    public static List<String> faultsInBands(List<Haunt> haunts) {
        List<String> bad = new ArrayList<>();
        for (Haunt one : haunts) {
            if (one.lightBand() <= 0) {
                bad.add(one.beast() + " spawns between light " + one.darkest()
                        + " and " + one.brightest() + ", which is no light at "
                        + "all, so it never spawns and nothing says so");
            }
            if (one.darkest() < PITCH_DARK || one.brightest() > FULL_LIGHT) {
                bad.add(one.beast() + " wants light between " + one.darkest()
                        + " and " + one.brightest() + " and a block is between "
                        + PITCH_DARK + " and " + FULL_LIGHT);
            }
            if (one.heightBand() <= 0) {
                bad.add(one.beast() + " spawns between " + one.lowest()
                        + " and " + one.highest() + ", which is no height at all");
            }
            if (one.lowest() < BEDROCK || one.highest() > SKY) {
                bad.add(one.beast() + " spawns between " + one.lowest()
                        + " and " + one.highest() + " and the world is between "
                        + BEDROCK + " and " + SKY);
            }
            if (!one.where().startsWith("#")) {
                bad.add(one.beast() + " haunts " + one.where() + ", which is "
                        + "one biome rather than a tag — a mob tied to one "
                        + "biome is a mob that vanishes when somebody adds a "
                        + "world preset");
            }
        }
        return bad;
    }

    /**
     * Whether a group of them fits where it is asked to appear.
     *
     * <p>The failure this catches is not that a pack of eight siege beasts
     * looks silly. It is that the game tries a few positions and gives up, so
     * the beast spawns at a fraction of the rate its weight promises — which
     * reads as a mob rarer than it is meant to be and cannot be diagnosed
     * from anything a player can see.
     */
    public static List<String> faultsInRoom(List<BeastRules.Beast> beasts,
                                            List<Haunt> haunts) {
        List<String> bad = new ArrayList<>();
        for (Haunt one : haunts) {
            BeastRules.Beast beast = null;
            for (BeastRules.Beast candidate : beasts) {
                if (candidate.id().equals(one.beast())) {
                    beast = candidate;
                }
            }
            if (beast == null) {
                continue;
            }
            double room = one.most() * beast.width() * beast.width();
            if (room > ROOM_FOR_A_PACK) {
                bad.add(one.beast() + " comes " + one.most() + " at a time and "
                        + "each is " + beast.width() + " across, which wants "
                        + room + " square blocks of floor where there are "
                        + ROOM_FOR_A_PACK + "; the game gives up placing them "
                        + "and it spawns rarer than its weight says");
            }
        }
        return bad;
    }

    /**
     * Whether any beast is a strictly worse version of another.
     *
     * <p>Within a family, because a prowler and a brute are not two strengths
     * of one idea and a checker that says they are reports nine times and
     * gets switched off — the same lesson the ability system learned about
     * domains.
     *
     * <p>Worse means: no harder to kill, no more dangerous, no faster, and
     * worse at something. A player who meets one of those has met a weaker
     * version of a fight they have already had, and the only thing it adds is
     * a different model.
     */
    public static List<String> faultsInDominance(List<BeastRules.Beast> beasts) {
        List<String> bad = new ArrayList<>();
        for (BeastRules.Beast worse : beasts) {
            for (BeastRules.Beast better : beasts) {
                if (worse == better || !worse.family().equals(better.family())) {
                    continue;
                }
                boolean everywhere = better.health() >= worse.health()
                        && better.damage() >= worse.damage()
                        && better.speed() >= worse.speed()
                        && better.armour() >= worse.armour();
                boolean somewhere = better.health() > worse.health()
                        || better.damage() > worse.damage()
                        || better.speed() > worse.speed()
                        || better.armour() > worse.armour();
                if (everywhere && somewhere && better.tier() == worse.tier()) {
                    bad.add(worse.id() + " is at best as good as " + better.id()
                            + " on every axis and worse on one, at the same "
                            + "tier of the same family — so it is a weaker "
                            + "version of a fight a player has already had "
                            + "with a different model on it");
                }
            }
        }
        return bad;
    }
}
