package com.elysium.lib.ability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Abilities: what a unique weapon or a relic actually <em>does</em>.
 *
 * <h2>Why this is one system and not two</h2>
 *
 * Two modules were waiting on this. The arsenal's uniques are, underneath,
 * ordinary weapons that do something extra; arcana's relics are ordinary
 * trinkets that do something extra. Written twice they would be two sets of
 * cooldowns that drift apart, two balance models, and an ability that behaves
 * differently depending on which module happened to implement it.
 *
 * <p>So it lives in the library, below both, and both read it.
 *
 * <h2>The rule that makes thirty abilities into thirty abilities</h2>
 *
 * Nothing is dominated. No ability may be at least as strong as another,
 * available at least as often and cost no more, and be better in one of
 * those — because if it is, the weaker one is a line in a tooltip nobody ever
 * chooses.
 *
 * <p>That cannot be satisfied by numbers alone, and trying gave eleven
 * dominated passives on the first run. What makes it satisfiable is the
 * <em>domain</em>: two abilities are only comparable if they do the same kind
 * of thing. A movement-speed passive and a night-vision passive are not two
 * strengths of the same idea, and a checker that says they are will be
 * switched off. Eighteen domains across thirty abilities is what it took.
 *
 * <h2>And why a rescue is not measured by how often it fires</h2>
 *
 * The obvious worth model is power times how often the trigger comes round.
 * Applied to something that saves your life at low health, it says a thing
 * that fires twice an hour is worth a fifth of a walking-speed bonus. It is
 * not: it is worth exactly as much as the death it prevented. So an always-on
 * and a rescue are valued by magnitude, and everything else by throughput.
 */
public final class ElysiumAbilityRules {

    private ElysiumAbilityRules() {
    }

    // ==================================================================
    // What an ability is
    // ==================================================================

    /** Always on. No cooldown, nothing to pay. */
    public static final String PASSIVE = "passive";

    /** Fires when something happens. A cooldown, nothing to pay. */
    public static final String TRIGGERED = "triggered";

    /** You press a button. A cooldown and a price. */
    public static final String ACTIVE = "active";

    /** Every kind there is. */
    public static final List<String> KINDS = List.of(PASSIVE, TRIGGERED, ACTIVE);

    /** What each kind of ability is allowed to fire on. */
    public static final Map<String, Set<String>> TRIGGERS = Map.of(
            PASSIVE, Set.of("always"),
            TRIGGERED, Set.of("on_hit", "on_hurt", "on_kill", "on_low_health",
                    "periodic"),
            ACTIVE, Set.of("on_use", "on_sneak_use"));

    /**
     * How often each trigger comes round, in ticks, when it is not the
     * cooldown that decides.
     *
     * <p>Zero means the cooldown is the only thing pacing it.
     */
    public static final Map<String, Integer> FREQUENCY = Map.of(
            "always", 1,
            "on_hit", 20,
            "on_hurt", 60,
            "on_kill", 120,
            "on_low_health", 2_400,
            "periodic", 0,
            "on_use", 0,
            "on_sneak_use", 0);

    /**
     * The triggers valued by how much they do rather than how often.
     *
     * <p>A passive is always on. A rescue fires exactly when it matters.
     * Neither is sensibly priced by throughput, and pricing them that way was
     * the first thing the harness caught.
     */
    public static final Set<String> BY_MAGNITUDE = Set.of("always", "on_low_health");

    /** Ticks in a minute, which is the unit the worth model uses. */
    public static final int TICKS_A_MINUTE = 1_200;

    /** How many firings a minute count as the reference rate. */
    public static final float REFERENCE_RATE = 6.0F;

    /** The most any one ability may be worth. */
    public static final float CEILING = 4.0F;

    /** The least it may be worth and still be worth carrying. */
    public static final float FLOOR = 0.25F;

    /**
     * One ability.
     *
     * @param id      what it is called
     * @param kind    one of {@link #KINDS}
     * @param trigger what makes it happen
     * @param cooldown ticks between firings, zero for a passive
     * @param cost    focus it takes, zero for anything but an active
     * @param power   how much it does
     * @param scales  the stat it grows with, or empty for flat
     * @param domain  the kind of thing it does, which is what makes two
     *                abilities comparable at all
     */
    public record Ability(String id, String kind, String trigger, int cooldown,
                          int cost, float power, String scales, String domain) {

        public Ability {
            Objects.requireNonNull(id, "an ability with no name");
            Objects.requireNonNull(scales, "null scaling");
            Objects.requireNonNull(domain, "an ability that does nothing in particular");
            if (!KINDS.contains(kind)) {
                throw new IllegalArgumentException("no such kind of ability: " + kind);
            }
            if (!TRIGGERS.get(kind).contains(trigger)) {
                throw new IllegalArgumentException(id + " is a " + kind
                        + " ability and cannot fire on " + trigger);
            }
            if ((cooldown == 0) != PASSIVE.equals(kind)) {
                throw new IllegalArgumentException(id + "'s cooldown and its kind "
                        + "disagree: a passive has none and everything else has one");
            }
            if ((cost == 0) != !ACTIVE.equals(kind)) {
                throw new IllegalArgumentException(id + "'s cost and its kind "
                        + "disagree: only an active costs anything");
            }
            if (power <= 0.0F) {
                throw new IllegalArgumentException(id + " does nothing at all");
            }
            if (domain.isEmpty()) {
                throw new IllegalArgumentException(id + " has no domain, so nothing "
                        + "can be compared with it and it can never be dominated");
            }
        }

        /** What this is worth, in the one unit the ceiling and floor are in. */
        public float worth() {
            if (BY_MAGNITUDE.contains(trigger)) {
                return power;
            }
            int gap = Math.max(cooldown,
                    FREQUENCY.getOrDefault(trigger, 0) == 0 ? cooldown
                            : FREQUENCY.get(trigger));
            float perMinute = (float) TICKS_A_MINUTE / Math.max(1, gap);
            return power * perMinute / REFERENCE_RATE;
        }

        /**
         * Whether this makes another ability pointless.
         *
         * <p>Only within one trigger and one domain. A movement passive and a
         * vision passive are not two strengths of the same idea, and a checker
         * that says they are reports eleven times and gets switched off.
         */
        public boolean dominates(Ability other) {
            if (!trigger.equals(other.trigger()) || !domain.equals(other.domain())) {
                return false;
            }
            boolean asGood = power >= other.power()
                    && cooldown <= other.cooldown()
                    && cost <= other.cost();
            boolean better = power > other.power()
                    || cooldown < other.cooldown()
                    || cost < other.cost();
            return asGood && better;
        }
    }

    private static Ability made(String id, String kind, String trigger, int cooldown,
                                int cost, float power, String scales, String domain) {
        return new Ability(id, kind, trigger, cooldown, cost, power, scales, domain);
    }

    /** Every ability there is. */
    public static final List<Ability> ABILITIES = List.of(
            // Always on. No cooldown, no cost, and the whole of what they do is how much.
            made("steadfast", PASSIVE, "always", 0, 0, 0.8F, "armour", "poise"),
            made("swiftfoot", PASSIVE, "always", 0, 0, 0.7F, "", "mobility"),
            made("keen_edge", PASSIVE, "always", 0, 0, 0.9F, "damage", "offence"),
            made("deep_lungs", PASSIVE, "always", 0, 0, 0.5F, "", "breath"),
            made("sure_grip", PASSIVE, "always", 0, 0, 0.6F, "", "handling"),
            made("night_eyes", PASSIVE, "always", 0, 0, 0.5F, "", "vision"),
            made("ironhide", PASSIVE, "always", 0, 0, 1.0F, "armour", "defence"),
            made("scholar", PASSIVE, "always", 0, 0, 0.6F, "", "knowledge"),

            // They fire on something happening. A cooldown, and nothing to pay.
            made("riposte", TRIGGERED, "on_hurt", 100, 0, 1.2F, "damage", "offence"),
            made("thornmail", TRIGGERED, "on_hurt", 60, 0, 0.9F, "armour", "defence"),
            made("second_wind", TRIGGERED, "on_low_health", 1200, 0, 2.4F, "", "rescue"),
            made("bloodscent", TRIGGERED, "on_kill", 200, 0, 1.1F, "damage", "offence"),
            made("soul_harvest", TRIGGERED, "on_kill", 400, 0, 1.6F, "", "knowledge"),
            made("cleave", TRIGGERED, "on_hit", 80, 0, 1.0F, "damage", "offence"),
            made("sunder", TRIGGERED, "on_hit", 140, 0, 1.3F, "damage", "sundering"),
            made("chill_touch", TRIGGERED, "on_hit", 120, 0, 1.1F, "", "control"),
            made("ember_brand", TRIGGERED, "on_hit", 120, 0, 1.1F, "damage", "burning"),
            made("static_arc", TRIGGERED, "on_hit", 160, 0, 1.25F, "", "chaining"),
            made("lifeline", TRIGGERED, "on_low_health", 1600, 0, 2.8F, "armour", "rescue"),
            made("mender", TRIGGERED, "periodic", 300, 0, 0.9F, "", "healing"),
            made("gleaner", TRIGGERED, "periodic", 600, 0, 1.1F, "", "gathering"),
            made("ward_pulse", TRIGGERED, "periodic", 400, 0, 1.0F, "armour", "defence"),

            // You press a button. A cooldown and a price in focus.
            made("blink", ACTIVE, "on_use", 200, 20, 1.4F, "", "mobility"),
            made("shockwave", ACTIVE, "on_use", 300, 30, 1.9F, "damage", "offence"),
            made("bulwark", ACTIVE, "on_use", 400, 35, 2.1F, "armour", "defence"),
            made("hunters_mark", ACTIVE, "on_use", 240, 22, 1.5F, "damage", "marking"),
            made("veilstep", ACTIVE, "on_sneak_use", 360, 28, 1.8F, "", "stealth"),
            made("gravewake", ACTIVE, "on_sneak_use", 900, 60, 3.2F, "", "summoning"),
            made("stormcall", ACTIVE, "on_use", 800, 55, 3.0F, "damage", "offence"),
            made("sanctuary", ACTIVE, "on_sneak_use", 1200, 70, 3.6F, "armour", "rescue"));

    private static final Map<String, Ability> BY_ID = new LinkedHashMap<>();

    static {
        for (Ability ability : ABILITIES) {
            BY_ID.put(ability.id(), ability);
        }
    }

    /** The ability of that name, or null. */
    public static Ability ability(String id) {
        return BY_ID.get(id);
    }

    /** Every ability of one kind. */
    public static List<Ability> ofKind(String kind) {
        List<Ability> found = new ArrayList<>();
        for (Ability ability : ABILITIES) {
            if (ability.kind().equals(kind)) {
                found.add(ability);
            }
        }
        return List.copyOf(found);
    }

    /** Every domain there is. */
    public static List<String> domains() {
        Set<String> seen = new TreeSet<>();
        for (Ability ability : ABILITIES) {
            seen.add(ability.domain());
        }
        return List.copyOf(seen);
    }

    // ==================================================================
    // How many an item may carry
    // ==================================================================

    /** The most abilities one item may carry. */
    public static final int MOST_ON_AN_ITEM = 3;

    /** The most those abilities may be worth together. */
    public static final float MOST_ON_AN_ITEM_WORTH = 6.0F;

    /**
     * Whether a set of abilities may sit on one item.
     *
     * <p>Two copies of the same ability is the case worth naming: it is not
     * twice as good, it is a bug, and the honest place to refuse it is here
     * rather than in whichever module happens to build the item.
     */
    public static List<String> cannotShareAnItem(List<String> ids) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        float total = 0.0F;
        for (String id : ids) {
            Ability ability = BY_ID.get(id);
            if (ability == null) {
                bad.add(id + " is not an ability");
                continue;
            }
            if (!seen.add(id)) {
                bad.add(id + " is on the same item twice, which is not twice as good, "
                        + "it is a bug");
            }
            total += ability.worth();
        }
        if (ids.size() > MOST_ON_AN_ITEM) {
            bad.add(ids.size() + " abilities on one item, and the most is "
                    + MOST_ON_AN_ITEM);
        }
        if (total > MOST_ON_AN_ITEM_WORTH) {
            bad.add("those abilities are worth " + total + " together, and the most "
                    + "one item may carry is " + MOST_ON_AN_ITEM_WORTH);
        }
        return List.copyOf(bad);
    }

    // ==================================================================
    // Whether the roster hangs together
    // ==================================================================

    /** Every ability another ability makes pointless. */
    public static List<String> dominated() {
        List<String> bad = new ArrayList<>();
        for (Ability better : ABILITIES) {
            for (Ability worse : ABILITIES) {
                if (!better.id().equals(worse.id()) && better.dominates(worse)) {
                    bad.add(worse.id() + " is made pointless by " + better.id()
                            + " — same trigger, same domain, and no better in any way");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every pair of abilities that are the same ability under two names. */
    public static List<String> indistinguishable() {
        List<String> bad = new ArrayList<>();
        for (int first = 0; first < ABILITIES.size(); first++) {
            for (int second = first + 1; second < ABILITIES.size(); second++) {
                Ability one = ABILITIES.get(first);
                Ability other = ABILITIES.get(second);
                if (one.trigger().equals(other.trigger())
                        && one.domain().equals(other.domain())
                        && one.power() == other.power()
                        && one.cooldown() == other.cooldown()
                        && one.cost() == other.cost()) {
                    bad.add(one.id() + " and " + other.id() + " are the same ability "
                            + "under two names");
                }
            }
        }
        return List.copyOf(bad);
    }

    /** Every ability worth too much to carry or too little to bother with. */
    public static List<String> badValue() {
        List<String> bad = new ArrayList<>();
        for (Ability ability : ABILITIES) {
            float worth = ability.worth();
            if (worth > CEILING) {
                bad.add(ability.id() + " is worth " + worth + " and the ceiling is "
                        + CEILING);
            }
            if (worth < FLOOR) {
                bad.add(ability.id() + " is worth " + worth + ", which is not worth "
                        + "the line it takes up in a tooltip");
            }
        }
        return List.copyOf(bad);
    }

    /** Every trigger nothing ever fires on. */
    public static List<String> unusedTriggers() {
        List<String> spare = new ArrayList<>();
        for (String kind : KINDS) {
            for (String trigger : new TreeSet<>(TRIGGERS.get(kind))) {
                boolean any = false;
                for (Ability ability : ABILITIES) {
                    if (ability.trigger().equals(trigger)) {
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    spare.add("nothing fires on " + trigger);
                }
            }
        }
        return List.copyOf(spare);
    }

    /** Every kind with too few abilities to be a kind. */
    public static List<String> thinKinds() {
        List<String> bad = new ArrayList<>();
        for (String kind : KINDS) {
            int count = ofKind(kind).size();
            if (count < 2) {
                bad.add(kind + " has " + count + " ability, which is not a kind, it "
                        + "is one thing with a category around it");
            }
        }
        return List.copyOf(bad);
    }

    /** Every ability sharing a name with another. */
    public static List<String> repeated() {
        Set<String> seen = new TreeSet<>();
        List<String> twice = new ArrayList<>();
        for (Ability ability : ABILITIES) {
            if (!seen.add(ability.id())) {
                twice.add(ability.id());
            }
        }
        return List.copyOf(twice);
    }
}
