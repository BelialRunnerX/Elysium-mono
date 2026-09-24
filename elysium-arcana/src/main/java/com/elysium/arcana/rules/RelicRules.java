package com.elysium.arcana.rules;

import com.elysium.lib.ability.ElysiumAbilityRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Thirty relics, and what each of them actually does.
 *
 * <h2>Why this file is short</h2>
 *
 * Because the hard part is not here. What a relic <em>does</em> is an ability,
 * and the abilities live in the library with their own balance model, their
 * own domination rule and their own harness. A relic is a slot, a rarity and a
 * short list of ability names — and every one of those lists is checked
 * against {@link ElysiumAbilityRules#cannotShareAnItem}, which is the same
 * check the arsenal's uniques will pass through.
 *
 * <p>That is the whole reason the ability system was written first. Two
 * modules were about to grow two sets of cooldowns that would drift apart.
 *
 * <h2>What rarity means here</h2>
 *
 * How much a relic does when it fires, not how much it does per minute. A
 * sixty-second ultimate is not stronger per minute than an always-on passive —
 * that is exactly what the worth ceiling in the library is for, and both are
 * under it — it is stronger per press. Sorting rarity by throughput said the
 * rarest relics in the game were the weakest, which is how this was found.
 */
public final class RelicRules {

    private RelicRules() {
    }

    /** Where a relic is worn. */
    public static final List<String> SLOTS = List.of("charm", "ring", "belt",
            "cloak", "amulet", "mask", "boots", "relic");

    /** The commonest a relic can be, and the rarest. */
    public static final int COMMONEST = 1;
    public static final int RAREST = 4;

    /**
     * One relic.
     *
     * @param id        what it is called
     * @param slot      where it is worn
     * @param rarity    how much it does when it fires
     * @param abilities what it does, named from the library's roster
     */
    public record Relic(String id, String slot, int rarity, List<String> abilities) {

        public Relic {
            Objects.requireNonNull(id, "a relic with no name");
            abilities = List.copyOf(Objects.requireNonNull(abilities,
                    "a relic that does nothing"));
            if (!SLOTS.contains(slot)) {
                throw new IllegalArgumentException(id + " is worn in a " + slot
                        + ", and there is no such slot");
            }
            if (rarity < COMMONEST || rarity > RAREST) {
                throw new IllegalArgumentException(id + " has a rarity of " + rarity
                        + " and the band is " + COMMONEST + " to " + RAREST);
            }
            if (abilities.isEmpty()) {
                throw new IllegalArgumentException(id + " does nothing at all, which "
                        + "makes it a trinket rather than a relic");
            }
        }

        /** What this relic's abilities are worth together. */
        public float worth() {
            float total = 0.0F;
            for (String name : abilities) {
                ElysiumAbilityRules.Ability ability =
                        ElysiumAbilityRules.ability(name);
                if (ability != null) {
                    total += ability.worth();
                }
            }
            return total;
        }

        /** How much it does when it fires, which is what rarity tracks. */
        public float power() {
            float total = 0.0F;
            for (String name : abilities) {
                ElysiumAbilityRules.Ability ability =
                        ElysiumAbilityRules.ability(name);
                if (ability != null) {
                    total += ability.power();
                }
            }
            return total;
        }
    }

    private static Relic relic(String id, String slot, int rarity,
                               List<String> abilities) {
        return new Relic(id, slot, rarity, abilities);
    }

    /** Every relic there is. */
    public static final List<Relic> RELICS = List.of(
            // Rarity 1
            relic("wanderers_charm", "charm", 1, List.of("swiftfoot")),

            // Rarity 2
            relic("stonehide_ring", "ring", 2, List.of("ironhide")),

            // Rarity 1
            relic("hunters_belt", "belt", 1, List.of("bloodscent")),
            relic("leafy_mantle", "cloak", 1, List.of("mender")),
            relic("golden_tooth", "charm", 1, List.of("keen_edge")),
            relic("diving_bell", "amulet", 1, List.of("deep_lungs")),
            relic("gravekeepers_lamp", "charm", 1, List.of("night_eyes")),
            relic("scholars_lens", "charm", 1, List.of("scholar")),
            relic("sure_hands", "ring", 1, List.of("sure_grip")),

            // Rarity 2
            relic("bulwark_brooch", "charm", 2, List.of("steadfast", "thornmail")),
            relic("riposte_ring", "ring", 2, List.of("riposte")),
            relic("emberbrand_belt", "belt", 2, List.of("ember_brand")),
            relic("frostbitten_ring", "ring", 2, List.of("chill_touch")),
            relic("stormcoil", "amulet", 2, List.of("static_arc")),
            relic("reapers_tally", "charm", 2, List.of("soul_harvest")),
            relic("gleaners_glove", "ring", 2, List.of("gleaner")),
            relic("wardstone", "amulet", 2, List.of("ward_pulse")),
            relic("sunder_seal", "ring", 2, List.of("sunder")),
            relic("cleaving_band", "ring", 2, List.of("cleave")),

            // Rarity 3
            relic("phoenix_down", "amulet", 3, List.of("second_wind")),
            relic("blinkstep_boots", "boots", 3, List.of("blink", "swiftfoot")),
            relic("veiled_mask", "mask", 3, List.of("veilstep")),
            relic("hunters_mark_ring", "ring", 3, List.of("hunters_mark")),
            relic("aegis_plate", "relic", 3, List.of("bulwark", "steadfast")),
            relic("shockwave_gauntlet", "relic", 3, List.of("shockwave")),
            relic("lifeline_pendant", "amulet", 3, List.of("lifeline")),

            // Rarity 4
            relic("gravewake_censer", "relic", 4, List.of("gravewake")),
            relic("stormcallers_rod", "relic", 4, List.of("stormcall")),
            relic("sanctuary_bell", "relic", 4, List.of("sanctuary")),
            relic("wardens_regalia", "relic", 4, List.of("ward_pulse", "ironhide", "mender")));

    private static final Map<String, Relic> BY_ID = new LinkedHashMap<>();

    static {
        for (Relic one : RELICS) {
            BY_ID.put(one.id(), one);
        }
    }

    /** The relic of that name, or null. */
    public static Relic relic(String id) {
        return BY_ID.get(id);
    }

    /** Every relic worn in one slot. */
    public static List<Relic> inSlot(String slot) {
        List<Relic> found = new ArrayList<>();
        for (Relic one : RELICS) {
            if (one.slot().equals(slot)) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    // ==================================================================
    // Whether the roster hangs together
    // ==================================================================

    /** Every relic carrying a set of abilities no item is allowed to carry. */
    public static List<String> illegal() {
        List<String> bad = new ArrayList<>();
        for (Relic one : RELICS) {
            for (String trouble
                    : ElysiumAbilityRules.cannotShareAnItem(one.abilities())) {
                bad.add(one.id() + ": " + trouble);
            }
        }
        return List.copyOf(bad);
    }

    /** Every ability in the library that no relic carries. */
    public static List<String> unworn() {
        Set<String> carried = new TreeSet<>();
        for (Relic one : RELICS) {
            carried.addAll(one.abilities());
        }
        List<String> spare = new ArrayList<>();
        for (ElysiumAbilityRules.Ability ability : ElysiumAbilityRules.ABILITIES) {
            if (!carried.contains(ability.id())) {
                spare.add(ability.id() + " is an ability nothing in the world "
                        + "carries, so it exists only in a table");
            }
        }
        return List.copyOf(spare);
    }

    /** Every pair of relics that are the same relic under two names. */
    public static List<String> indistinguishable() {
        Map<String, String> seen = new LinkedHashMap<>();
        List<String> bad = new ArrayList<>();
        for (Relic one : RELICS) {
            List<String> sorted = new ArrayList<>(one.abilities());
            sorted.sort(String::compareTo);
            String key = one.slot() + "/" + String.join("+", sorted);
            String had = seen.putIfAbsent(key, one.id());
            if (had != null) {
                bad.add(one.id() + " and " + had + " are the same relic under two "
                        + "names");
            }
        }
        return List.copyOf(bad);
    }

    /** Every band of rarity whose relics do less than the band below. */
    public static List<String> rarityBackwards() {
        List<String> bad = new ArrayList<>();
        float below = -1.0F;
        int belowBand = -1;
        for (int rarity = COMMONEST; rarity <= RAREST; rarity++) {
            float total = 0.0F;
            int count = 0;
            for (Relic one : RELICS) {
                if (one.rarity() == rarity) {
                    total += one.power();
                    count++;
                }
            }
            if (count == 0) {
                bad.add("nothing at all is rarity " + rarity);
                continue;
            }
            float mean = total / count;
            if (below >= 0.0F && mean < below) {
                bad.add("rarity " + rarity + " relics do less when they fire ("
                        + mean + ") than rarity " + belowBand + " ones (" + below
                        + "), so the rarer relic is the weaker one");
            }
            below = mean;
            belowBand = rarity;
        }
        return List.copyOf(bad);
    }

    /** Every slot nothing is worn in. */
    public static List<String> emptySlots() {
        List<String> bad = new ArrayList<>();
        for (String slot : SLOTS) {
            if (inSlot(slot).isEmpty()) {
                bad.add("nothing at all is worn in the " + slot + " slot");
            }
        }
        return List.copyOf(bad);
    }

    /** Every relic sharing a name with another. */
    public static List<String> repeated() {
        Set<String> seen = new TreeSet<>();
        List<String> twice = new ArrayList<>();
        for (Relic one : RELICS) {
            if (!seen.add(one.id())) {
                twice.add(one.id());
            }
        }
        return List.copyOf(twice);
    }
}
