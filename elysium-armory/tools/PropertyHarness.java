package com.elysium.armory.property;

import java.util.ArrayList;
import java.util.List;

/**
 * The property engine, executed. Compiled against the JDK alone — which is
 * itself the check that the engine is pure.
 */
public final class PropertyHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean ok, String message) {
        checked++;
        if (!ok) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /** Doubles compared as doubles: equal to within a part in a billion. */
    private static boolean near(double a, double b) {
        return Math.abs(a - b) <= 1e-9 * Math.max(1.0D, Math.abs(b));
    }

    private static List<ArmoryModifier> avg(double... values) {
        List<ArmoryModifier> out = new ArrayList<>();
        for (double v : values) {
            out.add(ArmoryModifier.average(v));
        }
        return out;
    }

    public static void main(String[] args) {
        // 1. The reference example: a stronger material pulls the average up.
        double ironDiamond = ArmoryCompute.weightedAverage(new double[] {250, 1561});
        check(Math.abs(ironDiamond - 1277.0D) < 1.0D,
                "iron 250 + diamond 1561 averaged to " + ironDiamond + ", not about 1277");
        check(near(ArmoryCompute.weightedAverage(new double[] {40}), 40.0D),
                "one value did not average to itself");
        check(Math.abs(ArmoryCompute.weightedAverage(new double[] {7, 7, 7}) - 7.0D) < 1e-9,
                "equal values did not average to themselves");
        for (double[] pair : new double[][] {{250, 1561}, {1561, 250}, {3, 9}, {-2, 5}}) {
            double a = ArmoryCompute.weightedAverage(pair);
            check(a >= Math.min(pair[0], pair[1]) - 1e-9 && a <= Math.max(pair[0], pair[1]) + 1e-9,
                    "the average of " + pair[0] + " and " + pair[1] + " left the range: " + a);
        }

        // 2. The pipeline, in order: average, max, x base, x total, + add.
        List<ArmoryModifier> all = new ArrayList<>(avg(10));
        all.add(new ArmoryModifier(ArmoryOperation.MULTIPLY_BASE, 0.5));
        all.add(new ArmoryModifier(ArmoryOperation.MULTIPLY_TOTAL, 1.0));
        all.add(ArmoryModifier.add(5));
        check(near(ArmoryCompute.compute(0, all, ArmoryCompute.NO_FLOOR), 35.0D),
                "10, +50% of base, x2, +5 gave " + ArmoryCompute.compute(0, all, ArmoryCompute.NO_FLOOR)
                        + ", not 35: the operations are out of order");
        List<ArmoryModifier> withMax = new ArrayList<>(avg(3));
        withMax.add(new ArmoryModifier(ArmoryOperation.MAX, 8));
        check(ArmoryCompute.compute(0, withMax, ArmoryCompute.NO_FLOOR) == 8.0D,
                "a MAX of 8 over an average of 3 did not raise it to 8");
        check(ArmoryCompute.compute(4, List.of(), ArmoryCompute.NO_FLOOR) == 4.0D,
                "no modifiers did not return the base");

        // 3. Floors hold; nothing has a ceiling.
        check(ArmoryProperty.ATTACK_SPEED.compute(avg(-50)) == -3.9D,
                "attack speed went below -3.9, where a weapon never finishes a swing");
        check(ArmoryProperty.DURABILITY.compute(avg(-5)) == 0.0D,
                "durability went negative");
        double huge = ArmoryProperty.DURABILITY.compute(avg(1.0E12));
        check(huge >= 1.0E12, "durability of a trillion came back as " + huge
                + " — something caps it; the owner ruled no ceilings");
        double reach = ArmoryProperty.BLOCK_REACH.compute(avg(10_000));
        check(reach >= 10_000, "block reach was capped at " + reach);
        for (ArmoryProperty p : ArmoryProperty.ALL) {
            check(p.floor() <= p.defaultValue(),
                    p.id() + " starts below its own floor");
            check(ArmoryProperty.byId(p.id()) == p, p.id() + " cannot be found by its id");
        }
        check(ArmoryProperty.KNOCKBACK_RESISTANCE.share(),
                "knockback resistance is a percentage and is not marked as one");

        // 4. More of a material never makes a property worse.
        double last = Double.NEGATIVE_INFINITY;
        for (double v = 0; v <= 5000; v += 37) {
            double r = ArmoryProperty.ATTACK_DAMAGE.compute(avg(100, v));
            check(r >= last - 1e-9, "a better second material lowered attack damage at " + v);
            last = r;
        }

        // 5. Compress: at most one modifier per operation, MAX kept as the max.
        List<ArmoryModifier> mixed = new ArrayList<>(avg(4, 12));
        mixed.add(new ArmoryModifier(ArmoryOperation.MAX, 3));
        mixed.add(new ArmoryModifier(ArmoryOperation.MAX, 9));
        mixed.add(ArmoryModifier.add(1));
        mixed.add(ArmoryModifier.add(2));
        List<ArmoryModifier> packed = ArmoryCompute.compress(mixed);
        check(packed.size() == 3, "compress left " + packed.size() + " modifiers, not one per operation");
        for (ArmoryModifier m : packed) {
            if (m.operation() == ArmoryOperation.MAX) {
                check(m.value() == 9.0D, "compressed MAX is " + m.value() + ", not 9");
            }
        }

        // 6. Nonsense is ignored, not propagated.
        List<ArmoryModifier> nan = new ArrayList<>(avg(10));
        nan.add(ArmoryModifier.add(Double.NaN));
        nan.add(null);
        check(near(ArmoryCompute.compute(0, nan, ArmoryCompute.NO_FLOOR), 10.0D),
                "a NaN or null modifier changed the result");


        // 7. Gear types and per-type keys (SG-30, SG-4).
        check(ArmoryGearType.BUILTIN.size() == 44, "gear types: " + ArmoryGearType.BUILTIN.size() + ", not 44");
        for (ArmoryGearType g : ArmoryGearType.BUILTIN.values()) {
            check(g.parent() == null ? g.name().equals("all") : ArmoryGearType.named(g.parent()) != null,
                    g.name() + " has a missing parent " + g.parent());
            java.util.List<ArmoryGearType> line = g.lineage();
            check(line.get(line.size() - 1).name().equals("all"), g.name() + " does not reach all");
            check(new java.util.HashSet<>(line).size() == line.size(), g.name() + " lineage repeats");
        }
        check(ArmoryGearType.named("hammer").lineage().stream().map(ArmoryGearType::name).toList()
                .equals(List.of("hammer", "pickaxe", "harvest_tool", "tool", "all")), "hammer lineage");
        check(ArmoryGearType.named("helmet").is("armor") && !ArmoryGearType.named("helmet").is("tool"),
                "helmet is armour, not a tool");
        check(ArmoryGearType.named("trident").is("weapon"), "trident is a weapon");

        java.util.Map<ArmoryPropertyKey, Double> keyed = new java.util.HashMap<>();
        keyed.put(ArmoryPropertyKey.parse("harvest_speed"), 1.0D);
        keyed.put(ArmoryPropertyKey.parse("harvest_speed/harvest_tool"), 2.0D);
        keyed.put(ArmoryPropertyKey.parse("harvest_speed/pickaxe"), 3.0D);
        keyed.put(ArmoryPropertyKey.parse("harvest_speed/hammer"), 4.0D);
        check(ArmoryPropertyKey.resolve(keyed, "harvest_speed", ArmoryGearType.named("hammer"), 0.0D) == 4.0D, "own key wins");
        check(ArmoryPropertyKey.resolve(keyed, "harvest_speed", ArmoryGearType.named("prospector_hammer"), 0.0D) == 3.0D, "parent key next");
        check(ArmoryPropertyKey.resolve(keyed, "harvest_speed", ArmoryGearType.named("axe"), 0.0D) == 2.0D, "grandparent key next");
        check(ArmoryPropertyKey.resolve(keyed, "harvest_speed", ArmoryGearType.named("sword"), 0.0D) == 1.0D, "plain key last");
        check(ArmoryPropertyKey.resolve(keyed, "harvest_speed", null, 0.0D) == 1.0D, "no type reads the plain key");
        check(ArmoryPropertyKey.resolve(keyed, "attack_speed", ArmoryGearType.named("sword"), -9.0D) == -9.0D, "absent is the fallback");
        check("armor/helmet".equals(String.valueOf(ArmoryPropertyKey.parse("armor/helmet"))), "round trip");
        for (String bad : new String[] {"", "/x", "x/", "a/b/c", "Armor", "armor/Helmet", null}) {
            check(ArmoryPropertyKey.parse(bad) == null, "malformed key parsed: " + bad);
        }

        // Silent Gear's silver: armour 11, split 2/5/3/1 by slot keys.
        java.util.Map<ArmoryPropertyKey, Double> silver = new java.util.HashMap<>();
        silver.put(ArmoryPropertyKey.parse("armor"), 11.0D);
        silver.put(ArmoryPropertyKey.parse("armor/helmet"), 2.0D);
        silver.put(ArmoryPropertyKey.parse("armor/chestplate"), 5.0D);
        silver.put(ArmoryPropertyKey.parse("armor/leggings"), 3.0D);
        silver.put(ArmoryPropertyKey.parse("armor/boots"), 1.0D);
        double[] want = {2, 5, 3, 1};
        String[] slots = {"helmet", "chestplate", "leggings", "boots"};
        for (int i = 0; i < 4; i++) {
            check(ArmoryPropertyKey.pieceArmour(silver, ArmoryGearType.named(slots[i])) == want[i], "silver " + slots[i]);
        }
        // A total only: vanilla's shares, which add back to the whole set.
        java.util.Map<ArmoryPropertyKey, Double> total = java.util.Map.of(ArmoryPropertyKey.parse("armor"), 20.0D);
        double[] vanilla = {3, 8, 6, 3};
        double sumPieces = 0.0D;
        for (int i = 0; i < 4; i++) {
            double v = ArmoryPropertyKey.pieceArmour(total, ArmoryGearType.named(slots[i]));
            check(near(v, vanilla[i]), "total-only " + slots[i] + " is " + v);
            sumPieces += v;
        }
        check(near(sumPieces, 20.0D), "pieces do not add back to the set");
        check(near(ArmoryPropertyKey.pieceArmour(java.util.Map.of(ArmoryPropertyKey.parse("armor"), 4000.0D),
                ArmoryGearType.named("chestplate")), 1600.0D), "a huge set is not capped per piece");
        check(ArmoryPropertyKey.pieceArmour(total, ArmoryGearType.named("sword")) == 0.0D, "a sword has no armour share");
        check(near(ArmoryPropertyKey.pieceToughness(12.0D), 3.0D), "toughness is a quarter per piece");

        // ----------------------------------------------------------------
        // The harvest ladder (SG-5) and what the properties mean to the game (SG-6).
        // ----------------------------------------------------------------
        check(ArmoryTier.named("iron").levelHint() > ArmoryTier.named("stone").levelHint()
                && ArmoryTier.named("copper").levelHint() > ArmoryTier.named("stone").levelHint()
                && ArmoryTier.named("iron").levelHint() > ArmoryTier.named("copper").levelHint(),
                "copper does not sit between stone and iron");
        check(ArmoryTier.named("copper").levelHint() >= ArmoryTier.COPPER_LEVEL,
                "copper no longer satisfies what needs a copper tool");
        check(ArmoryTier.best(ArmoryTier.named("wood"), ArmoryTier.named("netherite"))
                == ArmoryTier.named("netherite"), "the best of two tiers is not the better one");
        check(ArmoryTier.best(null, ArmoryTier.named("wood")) == ArmoryTier.named("wood"),
                "a part with no tier hid the one that had it");
        check(ArmoryTier.named("netherite").canMine(ArmoryTier.named("diamond"))
                && !ArmoryTier.named("stone").canMine(ArmoryTier.named("diamond")),
                "what a tier can mine is not its level");
        check(ArmoryTier.list().size() >= 31, "the ladder has lost rungs: " + ArmoryTier.list().size());

        check(ArmoryVanilla.attackSpeed(4.0D, false) == 0.0D, "an attack speed of 4 is not vanilla's own");
        check(ArmoryVanilla.attackSpeed(2.4D, true) > ArmoryVanilla.attackSpeed(2.4D, false),
                "a broken weapon does not swing faster");
        check(ArmoryVanilla.attackDamage(-5.0D, false) == 0.0D, "negative attack damage healed the target");
        check(ArmoryVanilla.attackDamage(20.0D, true) == ArmoryVanilla.BROKEN_ATTACK_DAMAGE,
                "a broken weapon still hits for its full damage");
        check(ArmoryVanilla.armourDurability(15.0D, "chestplate") == 240
                && ArmoryVanilla.armourDurability(15.0D, "helmet") == 165,
                "armour durability is not the slot multiplier");
        check(ArmoryVanilla.armourDurability(15.0D, "sword") == 0, "a sword was given armour durability");
        check(near(ArmoryVanilla.toughnessAttribute(12.0D), 3.0D), "toughness is not a quarter");
        check(near(ArmoryVanilla.knockbackResistanceAttribute(5.0D), 0.5D), "knockback resistance is not a tenth");
        check(ArmoryVanilla.knockbackResistanceAttribute(-1.0D) == 0.0D, "negative knockback resistance pulled");
        double magic20 = ArmoryVanilla.magicArmourShare(20.0D);
        check(magic20 > 0.3D && magic20 < 0.7D, "twenty magic armour removes " + magic20);
        check(ArmoryVanilla.magicArmourShare(1.0E9D) < 1.0D, "magic armour reached total immunity");
        check(ArmoryVanilla.magicArmourShare(200.0D) > magic20, "magic armour stopped paying past twenty");
        check(ArmoryVanilla.rarity(0.0D, false).equals("COMMON")
                && ArmoryVanilla.rarity(60.0D, false).equals("UNCOMMON")
                && ArmoryVanilla.rarity(60.0D, true).equals("RARE")
                && ArmoryVanilla.rarity(1000.0D, false).equals("EPIC"), "the rarity bands moved");
        check(ArmoryVanilla.enchantmentValue(14.9D, true) == 14 && ArmoryVanilla.enchantmentValue(14.9D, false) == 0,
                "enchantability is not the property, or cannot be turned off");
        check(ArmoryVanilla.drawDelay(1.0D) == 20, "a bow with no draw stat does not draw in a second");
        check(ArmoryVanilla.drawDelay(4.0D) == ArmoryVanilla.MIN_DRAW_TICKS,
                "a very fast bow draws faster than the tick loop can see");
        check(ArmoryVanilla.drawDelay(0.0D) == 20, "a bow with no draw speed at all broke the arithmetic");
        check(near(ArmoryVanilla.arrowVelocity(20, 20), 1.0D), "a full draw does not loose at full speed");
        check(ArmoryVanilla.arrowVelocity(40, 20) == 1.0D, "holding a bow longer than full draw kept adding speed");
        check(ArmoryVanilla.arrowVelocity(0, 20) == 0.0D, "an arrow left the bow before it was drawn");
        check(ArmoryVanilla.crossbowCharge(1.0D) == 25 && ArmoryVanilla.crossbowCharge(10.0D) == 5,
                "crossbow charge time is not the draw stat");
        check(ArmoryVanilla.harvestSpeed(9.0D, false, false) == 1.0D, "a wrong tool mined at its full speed");
        check(ArmoryVanilla.harvestSpeed(9.0D, true, true) == ArmoryVanilla.BROKEN_HARVEST_SPEED,
                "a broken tool mined at its full speed");
        check(ArmoryVanilla.damageFactor(0, 1000) == 0 && ArmoryVanilla.damageFactor(999, 1000) == 9,
                "wear is not measured in ten steps");
        check(!ArmoryVanilla.needsRecompute(10, 11, 1000) && ArmoryVanilla.needsRecompute(99, 100, 1000),
                "gear recomputes on every hit, or never");
        check(ArmoryVanilla.damageFactor(5, 0) >= 0, "a piece with no durability broke the arithmetic");

        // ----------------------------------------------------------------
        // Traits: conditions, and how several grants become one level (SG-60).
        // ----------------------------------------------------------------
        ArmoryTraitRules.Context sword = new ArmoryTraitRules.Context(0.5D, 2, "sword",
                java.util.List.of("sword", "melee_weapon", "weapon", "tool", "all"));
        check(ArmoryTraitRules.holds("ratio>=0.5", sword) && !ArmoryTraitRules.holds("ratio>=0.75", sword),
                "a ratio condition no longer reads the share of the part");
        check(ArmoryTraitRules.holds("count>=2", sword) && !ArmoryTraitRules.holds("count>=3", sword),
                "a count condition no longer counts");
        check(ArmoryTraitRules.holds("on:sword", sword) && ArmoryTraitRules.holds("on:tool", sword)
                && !ArmoryTraitRules.holds("on:armor", sword),
                "a gear-type condition does not follow the type's parents");
        check(!ArmoryTraitRules.holds("not:on:tool", sword) && ArmoryTraitRules.holds("not:on:armor", sword),
                "a negated condition is not the opposite of the condition");
        check(!ArmoryTraitRules.holds("nonsense", sword),
                "a condition nobody understands passed; a trait would be granted by accident");
        check(ArmoryTraitRules.granted(new ArmoryTraitRules.Instance("sharp", 2,
                        java.util.List.of("ratio>=0.5", "on:tool")), sword),
                "a grant whose conditions all hold was refused");
        check(!ArmoryTraitRules.granted(new ArmoryTraitRules.Instance("sharp", 2,
                        java.util.List.of("ratio>=0.5", "on:armor")), sword),
                "a grant with one failing condition was allowed");

        // One part in five keeps most of its level; five parts in five average.
        java.util.List<ArmoryTraitRules.Instance> rare = java.util.List.of(
                new ArmoryTraitRules.Instance("sharp", 5, java.util.List.of()));
        java.util.List<ArmoryTraitRules.Instance> everywhere = java.util.List.of(
                new ArmoryTraitRules.Instance("sharp", 5, java.util.List.of()),
                new ArmoryTraitRules.Instance("sharp", 1, java.util.List.of()),
                new ArmoryTraitRules.Instance("sharp", 1, java.util.List.of()),
                new ArmoryTraitRules.Instance("sharp", 1, java.util.List.of()));
        int rareLevel = ArmoryTraitRules.combine(rare, 5).get("sharp");
        int commonLevel = ArmoryTraitRules.combine(everywhere, 4).get("sharp");
        check(rareLevel == 5, "one part carrying a trait at 5 gave level " + rareLevel);
        check(commonLevel < rareLevel && commonLevel >= 1,
                "a trait spread over four parts gave " + commonLevel + ", which is not an average");
        check(ArmoryTraitRules.combine(java.util.List.of(), 0).isEmpty(), "a piece with no traits has some");
        check(ArmoryTraitRules.capped(9, 5) == 5 && ArmoryTraitRules.capped(3, 5) == 3
                && ArmoryTraitRules.capped(9, 0) == 9, "a trait level is not capped where its effects are read");
        salvage();
        science();
        check(ArmoryTraitRules.roman(4).equals("IV") && ArmoryTraitRules.roman(9).equals("IX")
                && ArmoryTraitRules.roman(14).equals("XIV") && ArmoryTraitRules.roman(0).isEmpty(),
                "the levels a player reads are not roman numerals");

        if (failures > 0) {
            System.out.println(failures + " of " + checked + " checks failed");
            System.exit(1);
        }
        System.out.println(checked + " checks: iron+diamond averages to "
                + Math.round(ironDiamond) + ", operations in order, floors kept, no ceilings, "
                + ArmoryProperty.ALL.size() + " properties; 44 gear types, most specific key wins, armour splits back to its set; "
                + ArmoryTier.list().size() + " harvest rungs and the vanilla mapping; trait conditions and levels");
    }

    /** Salvage and reroll (SPEC_CLONE P6.2, P6.3, P6.11). */
    private static void salvage() {
        check(ArmorySalvageRules.chance(0.0D, 1.0D) == ArmorySalvageRules.BASE_CHANCE,
                "an untouched piece did not salvage at the base chance");
        check(ArmorySalvageRules.chance(1.0D, 1.0D) == ArmorySalvageRules.WORN_FLOOR,
                "a piece at its last point did not salvage at the floor");
        check(ArmorySalvageRules.chance(0.5D, 1.0D) < ArmorySalvageRules.BASE_CHANCE
                && ArmorySalvageRules.chance(0.5D, 1.0D) > ArmorySalvageRules.WORN_FLOOR,
                "wear does not lower the salvage chance between the two");
        check(ArmorySalvageRules.chance(0.0D, 2.0D) > ArmorySalvageRules.chance(0.0D, 1.0D),
                "a material that repairs well does not survive being taken apart any better");
        for (int wear = 0; wear <= 100; wear++) {
            for (int efficiency = 0; efficiency <= 40; efficiency++) {
                double chance = ArmorySalvageRules.chance(wear / 100.0D, efficiency / 10.0D);
                check(chance > 0.0D && chance < 1.0D,
                        "salvage was certain or impossible at wear " + wear + " efficiency " + efficiency);
                if (chance <= 0.0D || chance >= 1.0D) {
                    return;
                }
            }
        }
        check(ArmorySalvageRules.chance(-1.0D, 1.0D) == ArmorySalvageRules.BASE_CHANCE,
                "a negative wear was not read as none");
        check(ArmorySalvageRules.chance(2.0D, 1.0D) == ArmorySalvageRules.WORN_FLOOR,
                "wear past the maximum went below the floor");
        check(ArmorySalvageRules.essence(0) == 0, "a piece with no affixes yielded essence");
        check(ArmorySalvageRules.essence(7) == 7, "essence is not one per level of affix");
        check(ArmorySalvageRules.essence(-3) == 0, "a negative level yielded negative essence");
        check(ArmorySalvageRules.rerollCost(0) == 1,
                "a reroll to nothing was free, so a player could roll forever");
        check(ArmorySalvageRules.rerollCost(5) == 5, "a reroll's price is not its rarity");
        check(ArmorySalvageRules.CHOICES == 3,
                "a reforge offers " + ArmorySalvageRules.CHOICES + " results; three is what makes it "
                        + "a decision rather than a coin flip");
    }

    /** Mixing, grading and charging (SPEC_CLONE P8). */
    private static void science() {
        // Synergy: more materials with more in common is worth more, and
        // materials with nothing in common are worth exactly the average.
        java.util.Set<String> metal = java.util.Set.of("metal", "basic");
        java.util.Set<String> metal2 = java.util.Set.of("metal", "intermediate");
        java.util.Set<String> wood = java.util.Set.of("wood", "basic");
        java.util.Set<String> gem = java.util.Set.of("gem", "advanced");
        check(ArmoryMaterialScience.synergy(java.util.List.of(metal)) == 1.0D,
                "one material had a synergy of its own, which is a bonus for doing nothing");
        check(ArmoryMaterialScience.synergy(java.util.List.of()) == 1.0D,
                "a part of no materials had a synergy");
        check(ArmoryMaterialScience.synergy(java.util.List.of(metal, gem)) == 1.0D,
                "two materials with nothing in common gained something anyway");
        double pair = ArmoryMaterialScience.synergy(java.util.List.of(metal, metal2));
        check(pair > 1.0D, "two metals sharing a category gained nothing");
        // The exact value, not merely the direction: harmony is what they share
        // over everything between them, and a version that measured it against
        // something else would still rise and still be wrong.
        check(Math.abs(pair - (1.0D + ArmoryMaterialScience.SYNERGY_RATE * (1.0D / 3.0D))) < 1e-9,
                "two metals sharing one category of three came to " + pair
                        + ", which is not one third of the rate");
        double trio = ArmoryMaterialScience.synergy(
                java.util.List.of(metal, metal2, java.util.Set.of("metal", "endgame")));
        check(trio > pair, "a third material that also shares a category was worth nothing");
        check(ArmoryMaterialScience.synergy(java.util.List.of(metal, wood))
                        < ArmoryMaterialScience.synergy(java.util.List.of(metal, metal)),
                "materials with less in common were worth as much as identical ones");
        check(ArmoryMaterialScience.synergy(java.util.List.of(
                        java.util.Set.of(), java.util.Set.of())) == 1.0D,
                "materials that say nothing about themselves produced a synergy from nothing");
        for (int count = 2; count < 12; count++) {
            java.util.List<java.util.Set<String>> all = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                all.add(java.util.Set.of("metal"));
            }
            double value = ArmoryMaterialScience.synergy(all);
            check(value > 1.0D, count + " identical materials gained nothing");
            check(Math.abs(value - (1.0D + ArmoryMaterialScience.SYNERGY_RATE * (count - 1))) < 1e-9,
                    "perfect harmony at " + count + " materials is not the full rate per material");
        }

        // Grades: Crude is worse than plain, every grade above it is better,
        // and nothing outside the range the rules were written for survives.
        check(ArmoryMaterialScience.gradeFactor(ArmoryMaterialScience.UNGRADED) == 1.0D,
                "an ungraded part was not plain");
        check(ArmoryMaterialScience.gradeFactor(ArmoryMaterialScience.CRUDE) < 1.0D,
                "Crude was not worse than plain, so a failed grading costs nothing");
        double previous = 0.0D;
        for (int grade = ArmoryMaterialScience.CRUDE; grade <= ArmoryMaterialScience.MAX_GRADE; grade++) {
            double factor = ArmoryMaterialScience.gradeFactor(grade);
            check(factor > previous, "grade " + grade + " was not better than the one below it");
            previous = factor;
        }
        check(ArmoryMaterialScience.clampGrade(99) == ArmoryMaterialScience.MAX_GRADE
                        && ArmoryMaterialScience.clampGrade(-99) == ArmoryMaterialScience.CRUDE,
                "a grade from a hand-edited save was read outside the range the rules cover");
        check(!ArmoryMaterialScience.gradeName(ArmoryMaterialScience.CRUDE).isEmpty()
                        && ArmoryMaterialScience.gradeName(ArmoryMaterialScience.UNGRADED).isEmpty(),
                "Crude has no name, or plain has one it does not need");

        // Grading: a perfect roll climbs, a bad one only ever costs an
        // ungraded part, and the top is the top.
        check(ArmoryMaterialScience.graded(ArmoryMaterialScience.UNGRADED, 0.999D) == 1,
                "a good roll on an ungraded part did not grade it");
        check(ArmoryMaterialScience.graded(ArmoryMaterialScience.UNGRADED, 0.0D)
                        == ArmoryMaterialScience.CRUDE,
                "a bad roll on an ungraded part did not make it Crude");
        check(ArmoryMaterialScience.graded(3, 0.0D) == 3,
                "a bad roll undid work already done, which is a punishment rather than a risk");
        check(ArmoryMaterialScience.graded(ArmoryMaterialScience.CRUDE, 0.999D)
                        == ArmoryMaterialScience.UNGRADED,
                "a Crude part could not be worked back to plain");
        check(ArmoryMaterialScience.graded(ArmoryMaterialScience.MAX_GRADE, 0.999D)
                        == ArmoryMaterialScience.MAX_GRADE,
                "the best grade climbed past itself");
        check(ArmoryMaterialScience.failureChance(ArmoryMaterialScience.UNGRADED) > 0.0D,
                "the first grading cannot fail, so Crude is a state nothing in the game "
                        + "produces and grading is a free ratchet");
        for (int grade = 0; grade <= ArmoryMaterialScience.MAX_GRADE; grade++) {
            double chance = ArmoryMaterialScience.failureChance(grade);
            check(chance < 1.0D, "grading at " + grade + " was certain to fail");
            check(grade == 0 || chance > ArmoryMaterialScience.failureChance(grade - 1),
                    "grading got no harder at " + grade);
        }

        // Starcharged stacks with a grade, because they are different things.
        check(ArmoryMaterialScience.factor(ArmoryMaterialScience.UNGRADED, false) == 1.0D,
                "a plain uncharged part was worth more or less than itself");
        check(ArmoryMaterialScience.factor(ArmoryMaterialScience.UNGRADED, true)
                        == ArmoryMaterialScience.STARCHARGE_FACTOR,
                "the charge on a plain part is not the charge");
        check(ArmoryMaterialScience.factor(5, true)
                        > ArmoryMaterialScience.factor(5, false),
                "charging the best-graded part was worth nothing");
        check(ArmoryMaterialScience.CHARGE_TICKS > 0, "a charge takes no time at all");
    }
}
