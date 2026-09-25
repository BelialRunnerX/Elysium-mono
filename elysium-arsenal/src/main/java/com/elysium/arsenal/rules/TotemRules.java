package com.elysium.arsenal.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Four totems: two you put down and two you hold.
 *
 * <h2>The difference that matters</h2>
 *
 * A totem you place works for ever and costs nothing to keep working, so what
 * it does has to be small and local. A totem you hold is used once and gone,
 * so what it does can be large. Getting this the wrong way round gives you
 * either a placed block that wins the game or a consumable nobody bothers
 * carrying — and both of those look reasonable when you write them down one at
 * a time, which is why the rule is checked rather than remembered.
 */
public final class TotemRules {

    private TotemRules() {
    }

    /** A totem you set down and leave. */
    public static final String PLACED = "placed";

    /** A totem you carry and spend. */
    public static final String HELD = "held";

    /**
     * One totem.
     *
     * @param name  what it is called
     * @param kind  placed or held
     * @param power how much it does
     * @param reach how far, in blocks; a held totem reaches its user
     * @param every how often a placed totem acts, in ticks; nothing for a held one
     */
    public record Totem(String name, String kind, float power, int reach, int every) {

        public Totem {
            Objects.requireNonNull(name, "a totem with no name");
            if (!PLACED.equals(kind) && !HELD.equals(kind)) {
                throw new IllegalArgumentException(name + " is a " + kind
                        + " totem, and a totem is placed or held");
            }
            if (power <= 0.0F) {
                throw new IllegalArgumentException(name + " does nothing");
            }
            if (reach < 1) {
                throw new IllegalArgumentException(name + " reaches nothing");
            }
            if (PLACED.equals(kind)) {
                if (reach > FURTHEST_PLACED) {
                    throw new IllegalArgumentException(name + " reaches " + reach
                            + " blocks and a placed totem may reach " + FURTHEST_PLACED
                            + " — past that it is not a thing in a room, it is a rule "
                            + "about the world");
                }
                if (every < 1) {
                    throw new IllegalArgumentException(name + " acts every tick, "
                            + "which is twenty times a second for ever");
                }
            } else if (every != 0) {
                throw new IllegalArgumentException(name + " is held and has a period, "
                        + "so something is confused about which sort it is");
            }
        }

        /** What a placed totem does in a minute. */
        public float perMinute() {
            return PLACED.equals(kind) ? power * (1_200.0F / every) : power;
        }
    }

    /** The furthest a placed totem may work. */
    public static final int FURTHEST_PLACED = 12;

    /**
     * The most a placed totem may do in a minute.
     *
     * <p>A placed totem never stops and never costs anything, so whatever it
     * does in a minute it does for the rest of the world's life. This is the
     * only number keeping "helpful ornament" from becoming "win button".
     */
    public static final float MOST_PER_MINUTE = 6.0F;

    /**
     * The least a held totem may do.
     *
     * <p>A held totem is used once and gone. If it does less than a placed one
     * manages in a minute, nobody carries it — they place the other and walk
     * away.
     */
    public static final float LEAST_HELD = 2.0F;

    /** Every totem there is. */
    public static final List<Totem> TOTEMS = List.of(
            new Totem("growth_totem", PLACED, 1.0F, 6, 200),
            new Totem("radiance_totem", PLACED, 1.5F, 12, 400),
            new Totem("torrent_totem", HELD, 3.0F, 1, 0),
            new Totem("soul_totem", HELD, 4.0F, 8, 0));

    private static final Map<String, Totem> BY_NAME = new LinkedHashMap<>();

    static {
        for (Totem one : TOTEMS) {
            BY_NAME.put(one.name(), one);
        }
    }

    /** The totem of that name, or null. */
    public static Totem totem(String name) {
        return BY_NAME.get(name);
    }

    /** Every totem of a sort. */
    public static List<Totem> of(String kind) {
        List<Totem> found = new ArrayList<>();
        for (Totem one : TOTEMS) {
            if (one.kind().equals(kind)) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    // ==================================================================
    // Whether the two sorts stay two sorts
    // ==================================================================

    /** Every placed totem doing more in a minute than a placed totem may. */
    public static List<String> placedTooStrong() {
        List<String> bad = new ArrayList<>();
        for (Totem one : of(PLACED)) {
            if (one.perMinute() > MOST_PER_MINUTE) {
                bad.add(one.name() + " does " + one.perMinute() + " a minute for ever "
                        + "and costs nothing to keep doing it, and the most a placed "
                        + "totem may do is " + MOST_PER_MINUTE);
            }
        }
        return List.copyOf(bad);
    }

    /** Every held totem worth less than leaving a placed one running. */
    public static List<String> heldTooWeak() {
        List<String> bad = new ArrayList<>();
        for (Totem one : of(HELD)) {
            if (one.power() < LEAST_HELD) {
                bad.add(one.name() + " is spent to do " + one.power()
                        + ", which is less than a placed totem does in a minute for "
                        + "nothing, so nobody would carry one");
            }
        }
        return List.copyOf(bad);
    }

    /** Every sort with no totems in it. */
    public static List<String> missingASort() {
        List<String> bad = new ArrayList<>();
        for (String kind : List.of(PLACED, HELD)) {
            if (of(kind).isEmpty()) {
                bad.add("there are no " + kind + " totems, so the comparison between "
                        + "the two sorts has nothing to compare and passes by never "
                        + "running");
            }
        }
        return List.copyOf(bad);
    }

    /** Every totem declared twice. */
    public static List<String> repeated() {
        Set<String> seen = new TreeSet<>();
        List<String> bad = new ArrayList<>();
        for (Totem one : TOTEMS) {
            if (!seen.add(one.name())) {
                bad.add(one.name() + " is declared twice");
            }
        }
        return List.copyOf(bad);
    }

    /** Every name this needs on the way into a game. */
    public static List<String> catalogue() {
        List<String> all = new ArrayList<>();
        for (Totem one : TOTEMS) {
            all.add(one.name());
        }
        return List.copyOf(all);
    }
}
