package com.elysium.lib.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Ledger Web: what the passive tree is made of, before any of it is placed.
 *
 * <p>Dylan, 28 Sep 2026: "make it more expansive path of exile style". So: one
 * web every player shares, hundreds of nodes, small nodes that are one number,
 * notables at the heart of a cluster, keystones that commit a player to a
 * doctrine, and a start on the rim for every origin and calling. This class is
 * the table of it — the subjects, the rewards, the keystones, the starts and
 * the point rule — and nothing here has a position. {@link ElysiumTree} lays
 * the table out; a rule can change without a point moving by hand.
 *
 * <p>Pure Java on purpose: the harness executes this table on the JDK, the
 * validator holds the language file to it, and the screen and the server both
 * draw from the tree it builds, so the three cannot disagree.
 *
 * <h2>Rewards</h2>
 * A small node is one stat and a number. A notable is two. A keystone is a
 * doctrine (a tag {@code ElysiumDoctrines} already reads, so every reader in the
 * pack keeps working) and a trade of stats, large one way and smaller the
 * other. A start is a few points of what its origin or calling is about. A
 * road (travel node) is one point of the stat its ring is walking through.
 *
 * <h2>Points</h2>
 * One per character level, separate from the ten stat points a level already
 * gives: the web is the second thing a level buys. Refunds cost a Ledger
 * Erasure each (the Empire sells and drops them), so a build can be walked
 * back but not for free.
 */
public final class ElysiumTreeRules {

    private ElysiumTreeRules() {
    }

    // ------------------------------------------------------------------
    // The point rule
    // ------------------------------------------------------------------

    /**
     * Passive points per character level.
     *
     * <p>Three, not one (Dylan, 28 September 2026: "tie skill points to the
     * character elysium level, 3 per level"). One point a level made a level a
     * small event on a web of four hundred nodes; three makes every level a
     * step across a cluster, and means a level-fifty character holds around a
     * third of the web rather than a tenth — which is the argument for the web
     * growing, and the harness holds the web to at least twice what fifty
     * levels buy.
     */
    public static final int POINTS_PER_LEVEL = 3;

    /** The first level that grants a point: level 1 has its start allocated for free and nothing else. */
    public static final int FIRST_POINT_LEVEL = 2;

    /** What a small node gives, a notable gives of each of its two stats, a road gives, a start gives. */
    public static final int SMALL = 2;
    public static final int NOTABLE = 5;
    public static final int ROAD = 1;
    public static final int START = 3;
    /** A keystone's trade: this much of one stat gained, this much of another lost. */
    public static final int KEYSTONE_GAIN = 12;
    public static final int KEYSTONE_LOSS = 6;

    // ------------------------------------------------------------------
    // The twelve stats, as the tree names them (ElysiumStats' serialised names)
    // ------------------------------------------------------------------

    public static final List<String> STATS = List.of(
            "vitality", "fortitude", "resilience", "strength", "agility", "accuracy",
            "reflexes", "retribution", "intellect", "willpower", "luck", "presence");

    // ------------------------------------------------------------------
    // Rewards
    // ------------------------------------------------------------------

    /** One thing a node gives: {@code stat} (id is a stat name) or {@code doctrine} (id is a doctrine id). */
    public record Reward(String kind, String id, int amount) {
        public static Reward stat(String stat, int amount) {
            return new Reward("stat", stat, amount);
        }

        public static Reward doctrine(String doctrine) {
            return new Reward("doctrine", doctrine, 1);
        }
    }

    // ------------------------------------------------------------------
    // Subjects: the clusters
    // ------------------------------------------------------------------

    /**
     * A cluster: what it is about, the ring it sits on, the stats its small
     * nodes rotate through, its notable's name and the two stats the notable
     * gives, and the keystone hung off it (or none).
     */
    public record Subject(String id, int ring, List<String> stats, String notable, String keystone) {
    }

    public static final int RING_STATS = 1;
    public static final int RING_SYSTEMS = 2;

    /** Small nodes in a stat cluster and in a system cluster. */
    public static final int SMALLS_STAT = 8;
    public static final int SMALLS_SYSTEM = 14;

    public static final List<Subject> SUBJECTS;

    static {
        List<Subject> s = new ArrayList<>();
        // Ring 1: one cluster per stat, its smalls all that stat, its notable that
        // stat and the one beside it on the sheet.
        String[] notables = {"Ironblood", "Unbowed", "Weathered Hide", "Oxthew", "Quickstep", "True Eye",
                             "Catch the Blade", "Answer in Kind", "Long Study", "Iron Will", "Charmed", "Bearing"};
        for (int i = 0; i < STATS.size(); i++) {
            String stat = STATS.get(i);
            String next = STATS.get((i + 1) % STATS.size());
            s.add(new Subject(stat, RING_STATS, List.of(stat, next), notables[i], null));
        }
        // Ring 2: the systems the pack has and the five elements, in the order
        // they sit round the ring — chosen so that each keystone's opposite
        // hangs off the cluster seven places on, the far side of the web.
        s.add(new Subject("seam", RING_SYSTEMS, List.of("strength", "fortitude", "luck"), "Master of the Seam", "sanctioned"));
        s.add(new Subject("line", RING_SYSTEMS, List.of("accuracy", "reflexes", "retribution"), "Master of the Line", "ordered"));
        s.add(new Subject("weather", RING_SYSTEMS, List.of("resilience", "vitality", "willpower"), "Stormcoat", "acclimatised"));
        s.add(new Subject("empire", RING_SYSTEMS, List.of("presence", "intellect", "luck"), "Imperial Favour", "warranted"));
        s.add(new Subject("works", RING_SYSTEMS, List.of("strength", "intellect", "fortitude"), "Foreman", "bonded"));
        s.add(new Subject("plasma", RING_SYSTEMS, List.of("strength", "retribution", "vitality"), "Cinderheart", "stormborn"));
        s.add(new Subject("dimensional", RING_SYSTEMS, List.of("reflexes", "agility", "willpower"), "Riftwalker", null));
        s.add(new Subject("kitchen", RING_SYSTEMS, List.of("vitality", "presence", "fortitude"), "Quartermaster", "clandestine"));
        s.add(new Subject("road", RING_SYSTEMS, List.of("agility", "reflexes", "resilience"), "Wayfarer", "irregular"));
        s.add(new Subject("deep", RING_SYSTEMS, List.of("willpower", "resilience", "intellect"), "Deepwalker", "deep_lunged"));
        s.add(new Subject("void", RING_SYSTEMS, List.of("willpower", "intellect", "luck"), "Voidtouched", "unsworn_writ"));
        s.add(new Subject("bench", RING_SYSTEMS, List.of("intellect", "accuracy", "luck"), "Master Artificer", "freehold"));
        s.add(new Subject("neural", RING_SYSTEMS, List.of("intellect", "presence", "accuracy"), "Neural Lattice", "surfacer"));
        s.add(new Subject("kinetic", RING_SYSTEMS, List.of("agility", "strength", "accuracy"), "Kinetic Mass", null));
        SUBJECTS = Collections.unmodifiableList(s);
    }

    public static Subject subject(String id) {
        for (Subject subject : SUBJECTS) {
            if (subject.id().equals(id)) {
                return subject;
            }
        }
        return null;
    }

    public static List<Subject> onRing(int ring) {
        List<Subject> out = new ArrayList<>();
        for (Subject subject : SUBJECTS) {
            if (subject.ring() == ring) {
                out.add(subject);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Keystones
    // ------------------------------------------------------------------

    /**
     * A keystone: a doctrine committed to, and a trade of stats. The name is
     * the doctrine's; the pairs are {@code ElysiumDoctrine.OPPOSED}, restated
     * here so the harness can hold the two lists to each other without a
     * registry.
     */
    public record Keystone(String doctrine, String gain, String loss) {
    }

    public static final List<Keystone> KEYSTONES = List.of(
            new Keystone("sanctioned", "presence", "agility"),
            new Keystone("clandestine", "agility", "presence"),
            new Keystone("warranted", "retribution", "luck"),
            new Keystone("unsworn_writ", "luck", "retribution"),
            new Keystone("acclimatised", "resilience", "accuracy"),
            new Keystone("deep_lunged", "willpower", "vitality"),
            new Keystone("ordered", "fortitude", "reflexes"),
            new Keystone("irregular", "reflexes", "fortitude"),
            new Keystone("bonded", "strength", "intellect"),
            new Keystone("freehold", "intellect", "strength"),
            new Keystone("stormborn", "vitality", "willpower"),
            new Keystone("surfacer", "accuracy", "resilience"));

    /** The six pairs, each holdable one at a time. */
    public static final String[][] OPPOSED = {
            {"sanctioned", "clandestine"},
            {"warranted", "unsworn_writ"},
            {"acclimatised", "deep_lunged"},
            {"ordered", "irregular"},
            {"bonded", "freehold"},
            {"stormborn", "surfacer"},
    };

    public static Keystone keystone(String doctrine) {
        for (Keystone keystone : KEYSTONES) {
            if (keystone.doctrine().equals(doctrine)) {
                return keystone;
            }
        }
        return null;
    }

    public static String opposedTo(String doctrine) {
        for (String[] pair : OPPOSED) {
            if (pair[0].equals(doctrine)) {
                return pair[1];
            }
            if (pair[1].equals(doctrine)) {
                return pair[0];
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Starts
    // ------------------------------------------------------------------

    /** A start: an origin or a calling, the ring-two subject it enters the web beside, and its stat. */
    public record Start(String id, String kind, String subject, String stat) {
    }

    public static final List<Start> STARTS = List.of(
            new Start("imperial", "origin", "empire", "presence"),
            new Start("druun", "origin", "weather", "resilience"),
            new Start("veylari", "origin", "void", "willpower"),
            new Start("korrath", "origin", "plasma", "strength"),
            new Start("lumari", "origin", "neural", "intellect"),
            new Start("unsworn", "origin", "road", "agility"),
            new Start("medicae", "calling", "kitchen", "vitality"),
            new Start("factor", "calling", "empire", "luck"),
            new Start("artificer", "calling", "bench", "intellect"),
            new Start("enforcer", "calling", "line", "retribution"),
            new Start("psion", "calling", "neural", "willpower"),
            new Start("voidrunner", "calling", "deep", "reflexes"),
            new Start("reclaimer", "calling", "seam", "fortitude"),
            new Start("warden", "calling", "works", "strength"),
            new Start("marksman", "calling", "dimensional", "accuracy"));

    public static Start start(String id) {
        for (Start start : STARTS) {
            if (start.id().equals(id)) {
                return start;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Faults
    // ------------------------------------------------------------------

    /** What is wrong with the table, in words; empty when nothing is. */
    public static List<String> faults() {
        List<String> faults = new ArrayList<>();
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (Subject subject : SUBJECTS) {
            seen.merge(subject.id(), 1, Integer::sum);
            if (subject.stats().isEmpty() || subject.stats().size() > 3) {
                faults.add("the cluster \"" + subject.id() + "\" rotates through " + subject.stats().size()
                        + " stat(s); one to three is a subject, more is a list");
            }
            for (String stat : subject.stats()) {
                if (!STATS.contains(stat)) {
                    faults.add("the cluster \"" + subject.id() + "\" gives \"" + stat + "\", which is not a stat");
                }
            }
            if (subject.notable() == null || subject.notable().isBlank()) {
                faults.add("the cluster \"" + subject.id() + "\" has no notable, so nothing names it");
            }
            if (subject.keystone() != null && keystone(subject.keystone()) == null) {
                faults.add("the cluster \"" + subject.id() + "\" hangs a keystone \"" + subject.keystone()
                        + "\" that the keystone list does not have");
            }
        }
        for (Map.Entry<String, Integer> entry : seen.entrySet()) {
            if (entry.getValue() > 1) {
                faults.add("the cluster \"" + entry.getKey() + "\" is listed " + entry.getValue() + " times");
            }
        }
        for (String stat : STATS) {
            if (subject(stat) == null || subject(stat).ring() != RING_STATS) {
                faults.add("the stat \"" + stat + "\" has no cluster of its own on the first ring");
            }
        }
        List<String> hung = new ArrayList<>();
        for (Subject subject : SUBJECTS) {
            if (subject.keystone() != null) {
                hung.add(subject.keystone());
            }
        }
        for (Keystone keystone : KEYSTONES) {
            if (!hung.contains(keystone.doctrine())) {
                faults.add("the keystone \"" + keystone.doctrine() + "\" hangs off no cluster, so it is on the web nowhere");
            }
            if (opposedTo(keystone.doctrine()) == null) {
                faults.add("the keystone \"" + keystone.doctrine() + "\" has no opposite, and a commitment nothing "
                        + "excludes is a bonus");
            }
            if (!STATS.contains(keystone.gain()) || !STATS.contains(keystone.loss())) {
                faults.add("the keystone \"" + keystone.doctrine() + "\" trades in a stat that does not exist");
            }
            if (keystone.gain().equals(keystone.loss())) {
                faults.add("the keystone \"" + keystone.doctrine() + "\" gains and loses the same stat, which is nothing");
            }
        }
        for (String[] pair : OPPOSED) {
            Keystone a = keystone(pair[0]);
            Keystone b = keystone(pair[1]);
            if (a == null || b == null) {
                faults.add("the pair " + pair[0] + "/" + pair[1] + " names a keystone that does not exist");
                continue;
            }
            Subject sa = subjectOfKeystone(pair[0]);
            Subject sb = subjectOfKeystone(pair[1]);
            if (sa != null && sb != null && sa.id().equals(sb.id())) {
                faults.add("the pair " + pair[0] + "/" + pair[1] + " hangs off the same cluster, so both are one step apart");
            }
        }
        Map<String, Integer> startKinds = new LinkedHashMap<>();
        for (Start start : STARTS) {
            startKinds.merge(start.kind(), 1, Integer::sum);
            Subject subject = subject(start.subject());
            if (subject == null || subject.ring() != RING_SYSTEMS) {
                faults.add("the start \"" + start.id() + "\" enters beside \"" + start.subject()
                        + "\", which is not a cluster on the second ring");
            }
            if (!STATS.contains(start.stat())) {
                faults.add("the start \"" + start.id() + "\" gives \"" + start.stat() + "\", which is not a stat");
            }
        }
        if (startKinds.getOrDefault("origin", 0) != 6) {
            faults.add("there are " + startKinds.getOrDefault("origin", 0) + " origin starts; the pack has six origins");
        }
        if (startKinds.getOrDefault("calling", 0) != 9) {
            faults.add("there are " + startKinds.getOrDefault("calling", 0) + " calling starts; the pack has nine callings");
        }
        if (POINTS_PER_LEVEL < 1) {
            faults.add("a level buys no point, so the web is a picture");
        }
        if (KEYSTONE_LOSS >= KEYSTONE_GAIN) {
            faults.add("a keystone loses as much as it gains, which is a commitment to nothing");
        }
        return faults;
    }

    public static Subject subjectOfKeystone(String doctrine) {
        for (Subject subject : SUBJECTS) {
            if (doctrine.equals(subject.keystone())) {
                return subject;
            }
        }
        return null;
    }
}
