package com.elysium.ores.rules;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The two rule classes, executed.
 *
 * The failures worth pinning are the ones that would cost a player something
 * they cannot get back: a unifier that converts a material into a different
 * material, a canonical that moves between sessions, and a tier curve that
 * either stops paying or arrives at certainty.
 */
public final class OreHarness {

    private static int checks;
    private static int failures;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + complaint);
        }
    }

    public static void main(String[] args) {
        priority();
        canonicals();
        guard();
        bands();
        tiers();
        fortune();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: one steel out of five, chosen the same way "
                + "every load, and a vein that pays more the further from home it is "
                + "without ever paying always");
    }

    // ------------------------------------------------------------------

    private static void priority() {
        check(UnifyRules.rank("minecraft") == 0,
                "vanilla is not first, so a pack could end up with an iron ingot that is "
                        + "not the iron ingot");
        check(UnifyRules.rank("mekanism") < UnifyRules.rank("createbigcannons"),
                "a mod whose machines eat a material by the thousand does not outrank a "
                        + "mod that uses it for one recipe");
        check(UnifyRules.rank("some_mod_nobody_listed") == UnifyRules.PRIORITY.size(),
                "an unlisted namespace was not last");
        check(UnifyRules.rank(null) == UnifyRules.PRIORITY.size(),
                "a null namespace did not fall to the back");
        check(UnifyRules.namespaceOf("mekanism:ingot_steel").equals("mekanism"),
                "the namespace of an id was read wrong");
        check(UnifyRules.namespaceOf("no_colon_here").isEmpty(),
                "an id with no namespace claimed one");
        check(UnifyRules.namespaceOf(null).isEmpty(), "a null id claimed a namespace");
        check(UnifyRules.PRIORITY.contains("minecraft") && UnifyRules.PRIORITY.indexOf("minecraft") == 0,
                "vanilla is absent from the priority list");
        check(UnifyRules.PRIORITY.size() == Set.copyOf(UnifyRules.PRIORITY).size(),
                "a namespace is listed twice, so its rank depends on which copy is found");
    }

    private static void canonicals() {
        List<String> steel = List.of("createbigcannons:steel_ingot", "elysiumspace:steel_ingot",
                "immersiveengineering:ingot_steel", "mekanism:ingot_steel",
                "oritech:steel_ingot", "tfmg:steel_ingot");
        check("elysiumspace:steel_ingot".equals(UnifyRules.canonical(steel)),
                "five kinds of steel did not resolve to the one this mod can promise "
                        + "exists");

        List<String> iron = List.of("minecraft:iron_ingot", "mekanism:ingot_iron");
        check("minecraft:iron_ingot".equals(UnifyRules.canonical(iron)),
                "iron resolved to something other than iron");

        // Determinism. The same set in a different order must answer the same,
        // or a player's items move between sessions.
        check(UnifyRules.canonical(steel).equals(UnifyRules.canonical(steel.reversed())),
                "the canonical depends on the order the ids were found in, so it can "
                        + "change between loads and move a player's items");

        List<String> unlisted = List.of("zzz_mod:thing", "aaa_mod:thing");
        check("aaa_mod:thing".equals(UnifyRules.canonical(unlisted)),
                "two unlisted mods did not break their tie by name, so the winner is "
                        + "whichever loaded first");

        check(UnifyRules.canonical(List.of()) == null,
                "a family nothing supplies claimed a canonical");
        check(UnifyRules.canonical(java.util.Arrays.asList(new String[]{null})) == null,
                "a null id became a canonical");

        check(UnifyRules.better("minecraft:x", "mekanism:x"),
                "vanilla did not beat a mod");
        check(!UnifyRules.better("mekanism:x", "minecraft:x"),
                "preference is not antisymmetric, so two items each beat the other");

        Map<String, String> all = UnifyRules.canonicals(Map.of(
                "steel", steel, "iron", iron, "nothing", List.of()));
        check(all.size() == 2, "a family with nothing in it produced an entry");
        check(!all.containsKey("nothing"),
                "an empty family was mapped, so a caller has to check for null");
    }

    private static void guard() {
        check(UnifyRules.ambiguous(List.of("steel", "biosteel")),
                "an item in two families of the same kind was not flagged, so biosteel "
                        + "would be convertible into steel and destroyed");
        check(!UnifyRules.ambiguous(List.of("steel")),
                "an item in exactly one family was flagged");
        check(!UnifyRules.ambiguous(List.of()),
                "an item in no family at all was flagged");

        List<String> members = List.of("mekanism:ingot_steel", "oritech:biosteel_ingot",
                "elysiumspace:steel_ingot", "tfmg:steel_ingot");
        List<String> convert = UnifyRules.conversions(members, "elysiumspace:steel_ingot",
                Set.of("oritech:biosteel_ingot"));
        check(!convert.contains("oritech:biosteel_ingot"),
                "an ambiguous item was offered a conversion, which is the one way this "
                        + "module can cost a player a material");
        check(!convert.contains("elysiumspace:steel_ingot"),
                "the canonical was offered a conversion into itself");
        check(convert.size() == 2, "the wrong number of conversions was produced");
        check(convert.equals(convert.stream().sorted().toList()),
                "conversions come out in an unstable order, so the generated recipe ids "
                        + "would differ between loads");

        check(UnifyRules.worthUnifying(List.of("a:x", "b:x")),
                "two mods supplying one material is not worth unifying");
        check(!UnifyRules.worthUnifying(List.of("a:x")),
                "a material with one supplier was unified, which is work for nothing");
        check(!UnifyRules.worthUnifying(List.of("a:x", "a:y")),
                "one mod's two spellings of its own material were treated as a "
                        + "duplication problem between mods");
        check(!UnifyRules.worthUnifying(List.of()),
                "an empty family was worth unifying");
    }

    private static void bands() {
        check(OreRules.veinSize(OreRules.COMMON) > OreRules.veinSize(OreRules.EXOTIC),
                "the rarest material comes in the largest veins");
        for (int band = 0; band < OreRules.BANDS; band++) {
            check(OreRules.veinSize(band) >= OreRules.MINIMUM_SIZE,
                    "a vein at band " + band + " is too small to be a vein");
            check(OreRules.tries(band) >= 1,
                    "a band never attempts to generate at all, at " + band);
        }
        for (int band = 1; band < OreRules.BANDS; band++) {
            check(OreRules.tries(band) < OreRules.tries(band - 1),
                    "band " + band + " is no rarer than the band above it, so the bands "
                            + "say nothing");
            check(OreRules.veinSize(band) <= OreRules.veinSize(band - 1),
                    "band " + band + " has larger veins than the band above it");
        }
        check(OreRules.veinSize(-5) == OreRules.veinSize(OreRules.COMMON),
                "a band below the scale was not clamped");
        check(OreRules.veinSize(99) == OreRules.veinSize(OreRules.EXOTIC),
                "a band above the scale was not clamped");
        check(OreRules.tries(99) == OreRules.tries(OreRules.EXOTIC),
                "a band above the scale attempted a different number of veins");
        check(OreRules.DISCARD_ON_AIR > 0.0F && OreRules.DISCARD_ON_AIR < 1.0F,
                "exposed ore is either never or always discarded");
    }

    private static void tiers() {
        check(OreRules.bonus(0) == 0, "the first tier already paid a bonus");
        check(OreRules.bonus(OreRules.TIERS_PER_EXTRA) == 1,
                "the first extra did not arrive at the tier it was promised for");
        check(OreRules.bonus(-4) == 0, "a negative tier paid a bonus");

        // Linear and uncapped: there is no tier at which a deeper world stops
        // paying better.
        int last = -1;
        for (int tier = 0; tier <= 400; tier++) {
            int now = OreRules.bonus(tier);
            check(now >= last, "the bonus fell as the tier rose, at " + tier);
            last = now;
        }
        check(OreRules.bonus(300) > OreRules.bonus(30),
                "the bonus stopped climbing, so a deep world stops paying");
        check(OreRules.bonus(300) == 300 / OreRules.TIERS_PER_EXTRA,
                "the bonus is not linear in the tier");

        // The chance approaches one and never arrives.
        check(OreRules.bonusChance(0) == 0.0F, "tier zero was already sometimes lucky");
        float previous = -1.0F;
        for (int tier = 0; tier <= 1000; tier++) {
            float share = OreRules.bonusChance(tier);
            check(share >= 0.0F && share < 1.0F,
                    "the bonus became certain at tier " + tier);
            check(share >= previous, "the chance fell as the tier rose, at " + tier);
            previous = share;
        }
        check(OreRules.bonusChance((int) OreRules.BONUS_PACE) == 0.5F,
                "the pace does not mean what it says: half the time at the pace");
        check(OreRules.bonusChance(OreRules.TIERS_PER_EXTRA) > 0.2F,
                "the first tier that has a bonus at all almost never lands it, so the "
                        + "reward arrives as a rumour");

        // Yield: never zero, never more than the bonus promises.
        for (int tier = 0; tier <= 60; tier++) {
            check(OreRules.yield(tier, 0.0D) >= 1, "a block of ore yielded nothing at " + tier);
            check(OreRules.yield(tier, 0.999999D) == 1,
                    "the unluckiest roll still paid a bonus at " + tier);
            check(OreRules.yield(tier, 0.0D) == 1 + OreRules.bonus(tier),
                    "the luckiest roll did not pay the whole bonus at " + tier);
        }
        check(OreRules.yield(0, 0.0D) == 1,
                "tier zero paid more than one, so the curve starts above vanilla");
    }

    private static void fortune() {
        check(OreRules.fortuned(1, 0, 0.99D) == 1, "no fortune changed the drop");
        check(OreRules.fortuned(0, 3, 0.0D) == 0, "fortune conjured something from nothing");
        check(OreRules.fortuned(-3, 3, 0.0D) == 0, "a negative drop came out negative");
        for (int fortune = 1; fortune <= 10; fortune++) {
            int worst = OreRules.fortuned(2, fortune, 0.0D);
            check(worst == 2, "fortune " + fortune + " made the worst roll worse than none");
            for (double roll = 0.0D; roll < 1.0D; roll += 0.01D) {
                int got = OreRules.fortuned(2, fortune, roll);
                check(got >= 2, "fortune " + fortune + " dropped below the unenchanted "
                        + "amount at roll " + roll);
                check(got <= 2 * (fortune + 1), "fortune " + fortune + " paid more than "
                        + "its own ceiling at roll " + roll);
            }
        }
        check(OreRules.fortuned(1, 3, 0.99D) > OreRules.fortuned(1, 1, 0.99D),
                "a higher fortune did not reach further on the same roll");
    }
}
