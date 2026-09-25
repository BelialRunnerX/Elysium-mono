package com.elysium.arsenal.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Shards, an unidentified lump, and the three things it might turn out to be.
 *
 * <h2>Why identifying is a gamble and not a choice</h2>
 *
 * If a player picked which artefact they wanted, there would be one artefact:
 * whichever is best for what they are doing, every time, and the other two
 * would exist only in a wiki. Making it a roll means a player ends up with an
 * artefact they did not choose, which is the only way the second-best one ever
 * gets used — and it is also why no artefact may be a strictly better version
 * of another, because then the roll has a loser and finding one is a
 * disappointment rather than a surprise.
 *
 * <h2>Why the odds are stated as a count and not as percentages</h2>
 *
 * Every artefact is equally likely, and "equally likely" is written down as
 * <em>nothing at all</em>: the chance is one over however many there are. A
 * table of percentages would be three numbers that have to be edited together
 * every time a fourth artefact is added, and the failure when somebody forgets
 * is that the new one can never be found.
 */
public final class ArtifactRules {

    private ArtifactRules() {
    }

    /** How many shards make one unidentified artefact. */
    public static final int SHARDS_PER_ARTIFACT = 9;

    /** What an unidentified artefact is called. */
    public static final String UNIDENTIFIED = "unidentified_artifact";

    /** And a shard. */
    public static final String SHARD = "artifact_shard";

    /**
     * One artefact.
     *
     * @param name     what it is called
     * @param affects  the sort of thing it changes, for comparing
     * @param power    how much it does
     * @param levels   how far it can be pushed
     * @param onGear   the sort of gear it can sit on
     */
    public record Artifact(String name, String affects, float power, int levels,
                           String onGear) {

        public Artifact {
            Objects.requireNonNull(name, "an artefact with no name");
            Objects.requireNonNull(affects, name + " changes nothing");
            Objects.requireNonNull(onGear, name + " sits on nothing");
            if (power <= 0.0F) {
                throw new IllegalArgumentException(name + " does nothing at all");
            }
            if (levels < 1 || levels > MOST_LEVELS) {
                throw new IllegalArgumentException(name + " goes to " + levels
                        + " and the band is 1 to " + MOST_LEVELS);
            }
        }

        /** What it does at a level. */
        public float at(int level) {
            int clamped = Math.max(1, Math.min(levels, level));
            return power * clamped;
        }

        /** Whether this makes that one pointless. */
        public boolean dominates(Artifact other) {
            if (!affects.equals(other.affects()) || !onGear.equals(other.onGear())) {
                return false;
            }
            boolean asGood = power >= other.power() && levels >= other.levels();
            boolean better = power > other.power() || levels > other.levels();
            return asGood && better;
        }
    }

    /** The most levels any artefact may go to. */
    public static final int MOST_LEVELS = 3;

    /** Every artefact there is. */
    public static final List<Artifact> ARTIFACTS = List.of(
            new Artifact("penetrating_strike", "armour_ignored", 0.12F, 3, "melee"),
            new Artifact("soaring_winds", "flight_started", 0.50F, 3, "elytra"),
            new Artifact("tunneling", "blocks_taken", 1.0F, 1, "tool"));

    private static final Map<String, Artifact> BY_NAME = new LinkedHashMap<>();

    static {
        for (Artifact one : ARTIFACTS) {
            BY_NAME.put(one.name(), one);
        }
    }

    /** The artefact of that name, or null. */
    public static Artifact artifact(String name) {
        return BY_NAME.get(name);
    }

    /**
     * The chance any one artefact comes out of an identification.
     *
     * <p>One over the count, worked out rather than written down, so a fourth
     * artefact is findable the moment it is declared.
     */
    public static double chanceOfEach() {
        return ARTIFACTS.isEmpty() ? 0.0D : 1.0D / ARTIFACTS.size();
    }

    /** Which artefact a roll lands on. */
    public static Artifact identify(int pick) {
        if (ARTIFACTS.isEmpty()) {
            return null;
        }
        return ARTIFACTS.get(Math.floorMod(pick, ARTIFACTS.size()));
    }

    // ==================================================================
    // Whether three artefacts are three artefacts
    // ==================================================================

    /** Every artefact another makes pointless. */
    public static List<String> dominated() {
        List<String> bad = new ArrayList<>();
        for (Artifact better : ARTIFACTS) {
            for (Artifact worse : ARTIFACTS) {
                if (better != worse && better.dominates(worse)) {
                    bad.add(worse.name() + " is a worse " + better.name()
                            + ", so identifying one is a disappointment rather than "
                            + "a surprise");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every pair of artefacts that change the same thing. */
    public static List<String> sharingAnEffect() {
        Map<String, String> seen = new LinkedHashMap<>();
        List<String> bad = new ArrayList<>();
        for (Artifact one : ARTIFACTS) {
            String had = seen.putIfAbsent(one.affects() + "/" + one.onGear(),
                    one.name());
            if (had != null) {
                bad.add(one.name() + " and " + had + " both change "
                        + one.affects() + " on the same gear");
            }
        }
        return List.copyOf(bad);
    }

    /** Every artefact declared twice. */
    public static List<String> repeated() {
        Set<String> seen = new TreeSet<>();
        List<String> bad = new ArrayList<>();
        for (Artifact one : ARTIFACTS) {
            if (!seen.add(one.name())) {
                bad.add(one.name() + " is declared twice");
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Every way the chain from shard to artefact could stop working.
     *
     * <p>Every one of these has the same shape: a number that makes the
     * mechanic technically present and practically absent.
     */
    public static List<String> brokenChain() {
        List<String> bad = new ArrayList<>();
        if (SHARDS_PER_ARTIFACT < 2) {
            bad.add("an artefact takes " + SHARDS_PER_ARTIFACT + " shard(s), so a "
                    + "shard is an artefact and there is no collecting to do");
        }
        if (SHARDS_PER_ARTIFACT > 16) {
            bad.add("an artefact takes " + SHARDS_PER_ARTIFACT + " shards, which will "
                    + "not fit in a crafting grid and cannot be made at all");
        }
        if (ARTIFACTS.isEmpty()) {
            bad.add("there is nothing to identify into, so an unidentified artefact "
                    + "is an item that can never become anything");
        }
        double each = chanceOfEach();
        if (!ARTIFACTS.isEmpty() && Math.abs(each * ARTIFACTS.size() - 1.0D) > 1e-9D) {
            bad.add("the chances of the artefacts do not come to one, so some roll "
                    + "lands on nothing");
        }
        return List.copyOf(bad);
    }

    /** Every name this needs on the way into a game. */
    public static List<String> catalogue() {
        List<String> all = new ArrayList<>();
        all.add(SHARD);
        all.add(UNIDENTIFIED);
        for (Artifact one : ARTIFACTS) {
            all.add("identified_" + one.name());
        }
        return List.copyOf(all);
    }
}
