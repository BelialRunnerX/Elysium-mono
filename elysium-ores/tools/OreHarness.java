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

    /** How much of one complaint is worth reading. */
    private static final int ENOUGH = 220;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + trimmed(complaint));
        }
    }

    /**
     * One failure, cut to something a reader gets through.
     *
     * <p>A check that lists every place it went wrong can run to thousands of
     * characters. Three failures then show as one and a half, and the most
     * informative of them — usually the short one saying a constant is zero —
     * is the one that falls off the end.
     */
    private static String trimmed(String complaint) {
        if (complaint.length() <= ENOUGH) {
            return complaint;
        }
        return complaint.substring(0, ENOUGH) + " … (" + complaint.length()
                + " characters in all)";
    }

    public static void main(String[] args) {
        priority();
        canonicals();
        drops();
        guard();
        bands();
        tiers();
        fortune();
        cordon();
        sources();
        harvest();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: one steel out of five, chosen the same way "
                + "every load, a vein that pays more the further from home it is "
                + "without ever paying always, and " + CordonMaterials.SETS.size()
                + " Cordoned gear sets each standing on a material that is dug, made or found, "
                + "its " + CordonSources.seeds().size() + " roots each left somewhere in vanilla's world");
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

        // The metal modules. The agronomy is unlisted and tags a chunk into
        // every mined metal's c:raw_materials family, so an unlisted foundry
        // lost every tie to it on the alphabet.
        check("elysiumfoundry:raw_tin".equals(UnifyRules.canonical(
                        List.of("elysiumagronomy:tin_chunk", "elysiumfoundry:raw_tin"))),
                "the agronomy's tin chunk outranks the foundry's raw tin, so every tin ore drops a "
                        + "chunk and raw tin is a thing nobody can hold");
        check("elysiumatomics:raw_uranium".equals(UnifyRules.canonical(
                        List.of("elysiumagronomy:uranium_chunk", "elysiumatomics:raw_uranium"))),
                "the agronomy's uranium chunk outranks atomics' raw uranium, so every uranium ore "
                        + "drops a chunk and raw uranium is a thing nobody can hold");
        check(UnifyRules.rank("elysiumores") < UnifyRules.rank("elysiumfoundry")
                        && UnifyRules.rank("elysiumfoundry") < UnifyRules.rank("mekanism")
                        && UnifyRules.rank("elysiumatomics") < UnifyRules.rank("mekanism"),
                "the foundry and atomics do not sit between this module and the other mods, so a "
                        + "pack that has one of those mods drops its metal from our ores");
    }

    /** What an ore drops: UnifyRules.DROP_ORDER, and drop() walking it. */
    private static void drops() {
        List<String> order = UnifyRules.DROP_ORDER;
        check(!order.isEmpty() && "raw_materials".equals(order.get(0)),
                "the drop looks for something before the raw form, so breaking an ore hands a "
                        + "player a thing that has already been smelted");
        check("ingots".equals(order.get(order.size() - 1)),
                "the drop looks for an ingot before something less worked");
        check(order.contains(UnifyRules.VANILLA) && order.contains("dusts")
                        && order.indexOf(UnifyRules.VANILLA) < order.indexOf("dusts")
                        && order.indexOf(UnifyRules.VANILLA) < order.indexOf("ingots"),
                "the base game's item comes after a dust or an ingot, so the Nether's coal ore "
                        + "drops the foundry's coal dust rather than coal");
        check(order.size() == Set.copyOf(order).size(),
                "a step of the drop order is listed twice");

        // Coal: the foundry tags its coal dust into c:dusts/coal.
        String coal = UnifyRules.drop(Map.of("dusts", List.of("elysiumfoundry:coal_dust")),
                "minecraft:coal");
        check("minecraft:coal".equals(coal),
                "a coal ore drops " + coal + " rather than coal, because a dust a crusher makes "
                        + "came before the game's own item");
        // Tin: the foundry's raw tin and the agronomy's chunk, and an ingot.
        String tin = UnifyRules.drop(Map.of(
                "raw_materials", List.of("elysiumagronomy:tin_chunk", "elysiumfoundry:raw_tin"),
                "ingots", List.of("elysiumfoundry:tin_ingot")), null);
        check("elysiumfoundry:raw_tin".equals(tin),
                "a tin ore drops " + tin + " rather than the foundry's raw tin");
        // Lapis: the agronomy's chunk is raw, and raw comes before the gem.
        String lapis = UnifyRules.drop(Map.of(
                "raw_materials", List.of("elysiumagronomy:lapis_chunk"),
                "gems", List.of("minecraft:lapis_lazuli")), "minecraft:lapis_lazuli");
        check("elysiumagronomy:lapis_chunk".equals(lapis),
                "a lapis ore drops " + lapis + " while a raw form of lapis exists");
        String ruby = UnifyRules.drop(Map.of("gems", List.of("elysiumores:ruby")), null);
        check("elysiumores:ruby".equals(ruby), "a ruby ore drops " + ruby + " rather than the ruby");
        String steel = UnifyRules.drop(Map.of("ingots", List.of("zzz_mod:steel_ingot",
                "elysiumspace:steel_ingot")), null);
        check("elysiumspace:steel_ingot".equals(steel),
                "a family with nothing but ingots drops " + steel + " rather than its canonical ingot");
        check(UnifyRules.drop(Map.of(), null) == null,
                "a family nothing supplies drops something");
        check(UnifyRules.drop(Map.of("dusts", List.of()), "") == null,
                "an empty fallback was dropped as an item");
        check("minecraft:coal".equals(UnifyRules.drop(Map.of(), "minecraft:coal")),
                "a family nothing tags did not fall back on the game's own item");
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

    private static void cordon() {
        check(CordonMaterials.SETS.size() == CordonMaterials.VALORIA_SETS,
                "Valoria had " + CordonMaterials.VALORIA_SETS + " gear sets and the roster carries "
                        + CordonMaterials.SETS.size());
        java.util.Set<String> valoria = new java.util.HashSet<>();
        for (CordonMaterials.Set set : CordonMaterials.SETS) {
            check(valoria.add(set.valoria()), set.valoria() + " is listed twice, so two rungs share one set");
            CordonMaterials.Material material = CordonMaterials.named(set.family());
            check(material != null, set.valoria() + " stands on " + set.family() + ", which is not a material");
            if (material == null) {
                continue;
            }
            check(!set.tools() || material.tool() != null,
                    set.valoria() + " had tools and " + set.family() + " has no tool tier to make them from");
            check(!set.armour() || material.armour() != null,
                    set.valoria() + " had armour and " + set.family() + " has no armour material");
        }
        java.util.Set<String> families = new java.util.HashSet<>();
        for (CordonMaterials.Material material : CordonMaterials.MATERIALS) {
            check(families.add(material.family()), material.family() + " is two materials");
            check(material.family().matches("[a-z_]+"), material.family() + " is not an id");
            check(material.colour().matches("#[0-9a-f]{6}"), material.family() + " has no colour to draw from");
            check(material.band() >= OreRules.COMMON && material.band() <= OreRules.EXOTIC,
                    material.family() + " is on no band");
            check(material.dug() == (material.kind() == CordonMaterials.Kind.METAL
                            || material.kind() == CordonMaterials.Kind.GEM),
                    material.family() + " is dug and not a metal or a gem, or the other way round");
            check(material.shipped() != material.items().isEmpty(),
                    material.family() + " ships nothing, or ships something while being another module's");
            if (material.kind() == CordonMaterials.Kind.ALLOY) {
                check(!material.madeOf().isEmpty(), material.family() + " is an alloy of nothing");
                check(material.yield() >= 1, material.family() + " makes no ingots");
            } else {
                check(material.madeOf().isEmpty(), material.family() + " is not an alloy and is made of something");
            }
            if (material.tool() != null) {
                check(material.tool().uses() > 0 && material.tool().speed() > 0.0F
                                && material.tool().damage() >= 0.0F && material.tool().enchant() > 0,
                        material.family() + "'s tool tier has a number that is not a number");
            }
            if (material.armour() != null) {
                check(material.armour().protection() > 0 && material.armour().durability() > 0
                                && material.armour().enchant() > 0 && material.armour().knockback() >= 0.0F
                                && material.armour().nullDefence() >= 0.0F,
                        material.family() + "'s armour has a number that is not a number");
            }
        }
        check(CordonMaterials.unmakeable().isEmpty(),
                "an alloy is made of something above it or of nothing here: " + CordonMaterials.unmakeable());
        java.util.Set<String> items = new java.util.HashSet<>(CordonMaterials.items());
        check(items.size() == CordonMaterials.items().size(), "two materials ship one item id");
        java.util.Set<String> blocks = new java.util.HashSet<>(CordonMaterials.blocks());
        check(blocks.size() == CordonMaterials.blocks().size(), "two materials ship one block id");
        for (CordonMaterials.Drop drop : CordonMaterials.DROPS) {
            boolean eaten = false;
            for (CordonMaterials.Material material : CordonMaterials.MATERIALS) {
                eaten |= material.madeOf().containsKey(drop.name());
            }
            check(eaten, drop.name() + " is dropped and nothing here is made of it, so it is a thing with no use");
            check(!drop.from().isEmpty(), drop.name() + " does not say who leaves it");
        }
        for (CordonMaterials.Part part : CordonMaterials.PARTS) {
            CordonMaterials.Material of = CordonMaterials.named(part.family());
            check(of != null && of.shipped(), part.name() + " is riveted from a material nothing ships");
            check(part.takes() > 0, part.name() + " is made of nothing");
        }
        for (String vanilla : CordonMaterials.CLOTH_MADE_OF.keySet()) {
            check(vanilla.startsWith("minecraft:"), "the cloth is woven from " + vanilla + ", which is not vanilla's");
        }
        check(CordonMaterials.CLOTH_YIELD >= 1, "weaving makes nothing");
        // The roster is a ladder for the arsenal: harvest levels climb with the tier's damage, on the whole.
        int best = -1;
        for (CordonMaterials.Material material : CordonMaterials.MATERIALS) {
            if (material.tool() != null) {
                best = Math.max(best, material.tool().level());
            }
        }
        check(best >= 5, "no Cordoned tool tier reaches past netherite, so the rungs add nothing above the game's own");
    }

    /** The roots' sources (CordonSources): every seed left somewhere, nowhere twice. */
    private static void sources() {
        List<String> seeds = CordonSources.seeds();
        check(seeds.size() == CordonMaterials.DROPS.size() + 3,
                "the seeds are not the six drops and the three shards: " + seeds);
        check(seeds.size() == Set.copyOf(seeds).size(), "a seed is listed twice: " + seeds);
        for (CordonMaterials.Drop drop : CordonMaterials.DROPS) {
            check(seeds.contains(drop.name()), drop.name() + " is dropped and is not a seed, so nothing sources it");
        }
        for (CordonMaterials.Material material : CordonMaterials.MATERIALS) {
            boolean shard = material.kind() == CordonMaterials.Kind.SHARD;
            check(seeds.contains(material.family()) == shard,
                    material.family() + (shard ? " is a shard and is not a seed" : " is not a shard and is a seed"));
        }
        check(CordonSources.unsourced().isEmpty(),
                "a root the world does not give: " + CordonSources.unsourced());
        check(CordonSources.duplicated().isEmpty(),
                "a source written twice, so its rate is doubled: " + CordonSources.duplicated());
        check(CordonSources.orphans().isEmpty(),
                "a source for something the roster does not ship: " + CordonSources.orphans());
        check(CordonSources.foreign().isEmpty(),
                "a source on another mod's table; this module names only vanilla: " + CordonSources.foreign());
        check(!CordonSources.SOURCES.isEmpty(), "there are no sources at all");
        // Positive controls: the arithmetic can say no.
        check(CordonSources.of("nothing_of_the_sort").isEmpty(), "an unknown seed has sources");
        check(!CordonSources.of("ember_slag").isEmpty(), "the first drop has no source");
        check(CordonSources.KILL_CHANCE > 0.0F && CordonSources.KILL_CHANCE <= CordonSources.MAX_CHANCE,
                "the kill chance is zero or past the ceiling");
        check(CordonSources.CHEST_CHANCE > 0.0F && CordonSources.CHEST_CHANCE <= CordonSources.MAX_CHANCE,
                "the chest chance is zero or past the ceiling");
        check(CordonSources.CHEST_CHANCE > CordonSources.KILL_CHANCE,
                "a chest, opened once, pays no better than a kill, repeated forever");
        check(CordonSources.MAX_CHANCE <= 0.25F, "the ceiling lets a root be a common drop");
        Set<String> modifiers = new java.util.HashSet<>();
        for (CordonSources.Source source : CordonSources.SOURCES) {
            check(modifiers.add(source.modifier()), source.modifier() + " is two modifiers' name");
            check(source.chance() > 0.0F && source.chance() <= CordonSources.MAX_CHANCE,
                    source.modifier() + " rolls at " + source.chance());
            check(source.kind() == (source.table().startsWith("minecraft:entities/")
                            ? CordonSources.Kind.KILL : CordonSources.Kind.CHEST),
                    source.modifier() + " mistakes a kill for a chest or the other way round");
            check(source.tableName().matches("[a-z_]+") && !source.tableName().contains("/"),
                    source.modifier() + " has no usable table name");
            check(source.kind() == CordonSources.Kind.KILL
                            ? source.chance() == CordonSources.KILL_CHANCE
                            : source.chance() == CordonSources.CHEST_CHANCE,
                    source.modifier() + " rolls at a chance nobody decided");
        }
        check(CordonSources.tables().size() == Set.copyOf(CordonSources.tables()).size(),
                "tables() repeats a table");
        for (String seed : seeds) {
            boolean chest = false;
            for (CordonSources.Source source : CordonSources.of(seed)) {
                chest |= source.kind() == CordonSources.Kind.CHEST;
            }
            check(chest || seed.equals("chitin"),
                    seed + " is left by no chest, so a player who cannot find the creature cannot find it at all");
        }
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

    /**
     * What mines a block: every kind of material that registers a block gets
     * one answer, and the rule that reads a tag reports the three ways a list
     * can be wrong. A block in no tool's tag drops nothing if it asks for one.
     */
    private static void harvest() {
        check(HarvestRules.TOOLS.contains(HarvestRules.ORE_TOOL), "the ores are mined by "
                + HarvestRules.ORE_TOOL + ", which is no tool the game keeps a list for");
        for (CordonMaterials.Kind kind : CordonMaterials.Kind.values()) {
            if (kind == CordonMaterials.Kind.EXTERNAL) {
                continue;
            }
            boolean decided = HarvestRules.PICKAXE_KINDS.contains(kind.name())
                    || HarvestRules.BY_HAND_KINDS.contains(kind.name());
            check(decided, kind + " registers blocks and the harvest rules say nothing "
                    + "about what mines them");
            check(!(HarvestRules.PICKAXE_KINDS.contains(kind.name())
                    && HarvestRules.BY_HAND_KINDS.contains(kind.name())),
                    kind + " is both mined by a pickaxe and broken by hand");
        }
        check(HarvestRules.toolForKind("CLOTH").isEmpty(), "cloth is given a tool, and wool "
                + "breaks by hand");
        check(HarvestRules.toolForKind("METAL").equals("pickaxe"), "a metal block is not a "
                + "pickaxe's");
        boolean refused = false;
        try {
            HarvestRules.toolForKind("EXTERNAL");
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a kind nothing decides a tool for is given one anyway");
        // The controls on the tag reader: a block no tool is decided for, one
        // decided twice, and a name that is no block of ours are each reported.
        List<String> blocks = List.of("tin_block", "raw_tin_block", "mireweave_block");
        Map<String, List<String>> minedBy = Map.of("pickaxe", List.of("tin_block", "raw_tin_block"));
        List<String> byHand = List.of("mireweave_block");
        check(HarvestRules.untooled(blocks, minedBy, byHand).isEmpty(),
                "a list that answers once for every block is reported anyway");
        check(!HarvestRules.untooled(List.of("tin_block", "raw_tin_block", "mireweave_block",
                "peat_block"), minedBy, byHand).isEmpty(),
                "a block no tool is decided for is not reported");
        check(!HarvestRules.untooled(blocks, minedBy, List.of("mireweave_block", "tin_block"))
                .isEmpty(), "a block mined by a tool and broken by hand is not reported");
        check(!HarvestRules.untooled(blocks, Map.of("pickaxe", List.of("tin_block",
                "raw_tin_block", "peat_block")), byHand).isEmpty(),
                "a tool given to a block nobody registers is not reported");
    }
}
