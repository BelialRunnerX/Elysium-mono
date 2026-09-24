package com.elysium.armory.material;

import com.elysium.armory.property.ArmoryCompute;
import com.elysium.armory.property.ArmoryModifier;
import com.elysium.armory.property.ArmoryProperty;
import com.elysium.armory.property.ArmoryPropertyKey;
import com.elysium.armory.property.ArmoryTier;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Every material the mod ships, parsed, inherited and computed, outside a game.
 *
 * <p>The files are data, and data is where a gear system goes wrong quietly: a
 * property misspelled is a stat that silently does nothing, a parent misnamed
 * is sixteen wools with no properties at all, a tier misnamed is a pickaxe that
 * mines nothing. All three are load-time errors here, against the real files,
 * before anyone starts a world.
 *
 * <p>It also states the shape of the ladder rather than only its syntax: iron
 * has to out-last stone, diamond out-last iron, and every material that can be
 * a main part has to give a piece of gear some durability and a tier to mine
 * with.
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-armory" \
 *         -d /tmp/sh elysium-armory/tools/MaterialHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-armory:/tmp/sh" \
 *         com.elysium.armory.material.MaterialHarness [materials directory]
 */
public final class MaterialHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    private static Map<String, JsonObject> read(Path root) throws IOException {
        return read(root, "elysiumarmory:");
    }

    private static Map<String, JsonObject> read(Path root, String namespace) throws IOException {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(p -> p.toString().endsWith(".json")).toList()) {
                String id = namespace + root.relativize(path).toString()
                        .replace('\\', '/').replaceAll("\\.json$", "");
                files.put(id, JsonParser.parseString(
                        Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
        return files;
    }

    /** What one material gives a part, computed: the number gear would see from it alone. */
    private static double value(ArmoryMaterial material, String part, ArmoryProperty property) {
        List<ArmoryModifier> modifiers = material.forPart(part).modifiers()
                .get(new ArmoryPropertyKey(property.id(), null));
        return modifiers == null ? property.base() : property.compute(modifiers);
    }

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0]
                : "src/main/resources/data/elysiumarmory/elysium_materials");
        Map<String, JsonObject> files = read(root);
        check(files.size() >= 132, "only " + files.size() + " material file(s) found under " + root);

        List<String> problems = new ArrayList<>();
        Map<String, ArmoryMaterial> materials =
                ArmoryMaterialTree.resolve(files, (id, why) -> problems.add(id + ": " + why));
        check(problems.isEmpty(), "material files did not load: " + problems);
        check(materials.size() == files.size(),
                materials.size() + " of " + files.size() + " materials resolved");

        int main = 0;
        int withTier = 0;
        int tools = 0;
        int additives = 0;
        for (ArmoryMaterial material : materials.values()) {
            check(material.parent() == null, material.id() + " still has a parent after resolving");
            for (String part : material.properties().keySet()) {
                check(ArmoryPartType.named(part) != null, material.id() + " has properties for " + part);
            }
            if (!material.supports("main")) {
                continue;
            }
            main++;
            ArmoryMaterial.PartProperties properties = material.forPart("main");
            if (value(material, "main", ArmoryProperty.ADDITIVE) > 0.0D) {
                // An additive (redstone, glowstone, a crushed shulker shell)
                // is never a part on its own, so it needs neither durability
                // nor a tier: it only ever adds to what it is mixed with.
                additives++;
                continue;
            }
            check(!properties.modifiers().isEmpty(), material.id() + " is a main part with no properties");
            double durability = value(material, "main", ArmoryProperty.DURABILITY);
            double armour = value(material, "main", ArmoryProperty.ARMOR);
            // A main material makes tools, armour or both. Feathers and wool
            // have no durability and are armour only; sheet metal is processed
            // down to none on purpose. What none of them may be is nothing.
            boolean armourPiece = armour > 0.0D || properties.modifiers().keySet().stream()
                    .anyMatch(k -> k.property().startsWith("armor"));
            // A processed material is all multipliers — sheet metal exists to
            // take durability away — so it states no numbers of its own.
            check(durability > 0.0D || armourPiece || material.kind() == ArmoryMaterial.Kind.PROCESSED,
                    material.id() + " would make gear with neither durability nor armour");
            if (durability > 0.0D) {
                tools++;
                check(properties.tier() != null,
                        material.id() + " makes tools but has no harvest tier, so they would mine nothing");
            }
            if (properties.tier() != null) {
                withTier++;
                check(ArmoryTier.named(properties.tier().name()) != null,
                        material.id() + " has a tier that is not on the ladder");
            }
        }
        check(main >= 72, "only " + main + " material(s) can be a main part");
        check(withTier >= tools, (tools - withTier) + " tool material(s) have no harvest tier");

        // The ladder: the materials a player climbs have to out-last each other.
        ArmoryMaterial wood = materials.get("elysiumarmory:wood");
        ArmoryMaterial stone = materials.get("elysiumarmory:stone");
        ArmoryMaterial iron = materials.get("elysiumarmory:iron");
        ArmoryMaterial diamond = materials.get("elysiumarmory:diamond");
        ArmoryMaterial netherite = materials.get("elysiumarmory:netherite");
        check(wood != null && stone != null && iron != null && diamond != null && netherite != null,
                "one of the vanilla rungs is missing from the material list");
        if (iron != null && diamond != null && netherite != null && stone != null && wood != null
                && iron.forPart("main").tier() != null && stone.forPart("main").tier() != null
                && netherite.forPart("main").tier() != null && diamond.forPart("main").tier() != null) {
            double w = value(wood, "main", ArmoryProperty.DURABILITY);
            double s = value(stone, "main", ArmoryProperty.DURABILITY);
            double i = value(iron, "main", ArmoryProperty.DURABILITY);
            double d = value(diamond, "main", ArmoryProperty.DURABILITY);
            check(w < s && s < i && i < d, "durability does not climb wood < stone < iron < diamond: "
                    + w + ", " + s + ", " + i + ", " + d);
            check(iron.forPart("main").tier().levelHint() > stone.forPart("main").tier().levelHint(),
                    "iron does not mine more than stone");
            check(netherite.forPart("main").tier().levelHint()
                    > diamond.forPart("main").tier().levelHint(), "netherite does not mine more than diamond");
            check(ArmoryTier.best(stone.forPart("main").tier(), netherite.forPart("main").tier())
                    == netherite.forPart("main").tier(), "the best of two tiers is not the better one");
        }

        // Inheritance did what it is for: the wools differ only in their item and name.
        ArmoryMaterial wool = materials.get("elysiumarmory:wool");
        ArmoryMaterial red = materials.get("elysiumarmory:wool/red");
        check(wool != null && red != null, "the wool family is missing");
        if (wool != null && red != null) {
            check(red.properties().keySet().equals(wool.properties().keySet()),
                    "red wool did not inherit wool's part types");
            check(!red.ingredient().equals(wool.ingredient()), "red wool is crafted from plain wool's item");
        }

        // Nothing states a property the engine does not have, and averages still average.
        for (ArmoryMaterial material : materials.values()) {
            for (Map.Entry<String, ArmoryMaterial.PartProperties> part : material.properties().entrySet()) {
                for (ArmoryPropertyKey key : part.getValue().modifiers().keySet()) {
                    check(ArmoryProperty.byId(key.property()) != null,
                            material.id() + "/" + part.getKey() + " states unknown property " + key);
                }
            }
        }
        check(ArmoryCompute.weightedAverage(new double[] {250.0D, 1561.0D}) > 1000.0D,
                "the weighted average no longer favours the better material");

        check(additives >= 3, "the additive materials are no longer marked additive");
        // ----------------------------------------------------------------
        // Part definitions, and whole pieces of gear built from both.
        // ----------------------------------------------------------------
        Path partRoot = root.resolveSibling("elysium_parts");
        Map<String, ArmoryPart> parts = ArmoryParts.parseAll(read(partRoot, "elysiumarmory:"));
        check(parts.size() >= 48, "only " + parts.size() + " part definition(s) found under " + partRoot);
        int cores = 0;
        for (ArmoryPart part : parts.values()) {
            check(ArmoryPartType.named(part.partType()) != null, part.id() + " is of no known part type");
            if (part.isUpgrade()) {
                check(!part.upgrade().gearTypes().isEmpty(), part.id() + " is an upgrade that fits nothing");
                continue;
            }
            cores++;
            if (part.partType().equals("main")) {
                check(part.crafting().materials() > 0,
                        part.id() + " is a main part whose recipe takes no materials");
            }
        }
        check(cores >= 40, "only " + cores + " core part(s)");

        ArmoryPart swordBlade = parts.get("elysiumarmory:sword_blade");
        ArmoryPart rod = parts.get("elysiumarmory:rod");
        ArmoryPart tip = parts.get("elysiumarmory:tip");
        ArmoryPart hammerHead = parts.get("elysiumarmory:hammer_head");
        check(swordBlade != null && rod != null && tip != null && hammerHead != null,
                "one of the parts every build below needs is missing");
        if (swordBlade != null && rod != null && tip != null && hammerHead != null) {
            ArmoryMaterial wood2 = materials.get("elysiumarmory:wood");
            ArmoryGearBuild ironSword = new ArmoryGearBuild("sword", List.of(
                    new ArmoryGearBuild.Piece(swordBlade, List.of(iron, iron)),
                    new ArmoryGearBuild.Piece(rod, List.of(wood2))));
            ArmoryGearBuild diamondSword = new ArmoryGearBuild("sword", List.of(
                    new ArmoryGearBuild.Piece(swordBlade, List.of(diamond, diamond)),
                    new ArmoryGearBuild.Piece(rod, List.of(wood2))));
            check(ironSword.complete() && diamondSword.complete(), "a two-material sword is not a complete build");
            check(ironSword.property(ArmoryProperty.ATTACK_DAMAGE) > 3.0D,
                    "an iron sword hits for no more than its blade's own offset");
            check(diamondSword.property(ArmoryProperty.ATTACK_DAMAGE)
                    > ironSword.property(ArmoryProperty.ATTACK_DAMAGE), "diamond hits no harder than iron");
            check(diamondSword.property(ArmoryProperty.DURABILITY)
                    > ironSword.property(ArmoryProperty.DURABILITY), "diamond lasts no longer than iron");
            check(ironSword.tier() == iron.forPart("main").tier(), "an iron sword does not mine at iron");

            // A tip is the whole point of tips: it raises what the tool mines.
            ArmoryGearBuild tipped = new ArmoryGearBuild("sword", List.of(
                    new ArmoryGearBuild.Piece(swordBlade, List.of(iron, iron)),
                    new ArmoryGearBuild.Piece(rod, List.of(wood2)),
                    new ArmoryGearBuild.Piece(tip, List.of(materials.get("elysiumarmory:crimson_steel")))));
            check(tipped.tier().levelHint() > ironSword.tier().levelHint(),
                    "a crimson steel tip did not raise an iron tool's tier");
            check(ArmoryGearBuild.matchesRecipe(new ArmoryGearBuild.Piece(swordBlade, List.of(iron, iron)))
                    && !ArmoryGearBuild.matchesRecipe(new ArmoryGearBuild.Piece(swordBlade, List.of(iron))),
                    "a blade's recipe no longer asks for the materials it says");

            // The shapes differ from each other, not only the materials.
            ArmoryGearBuild hammer = new ArmoryGearBuild("hammer", List.of(
                    new ArmoryGearBuild.Piece(hammerHead, List.of(iron, iron, iron, iron, iron, iron)),
                    new ArmoryGearBuild.Piece(rod, List.of(wood2))));
            check(hammer.property(ArmoryProperty.ATTACK_DAMAGE)
                    > ironSword.property(ArmoryProperty.ATTACK_DAMAGE), "a hammer hits no harder than a sword");
            check(hammer.property(ArmoryProperty.HARVEST_SPEED)
                    < 6.0D * 2.0D, "a hammer mines as fast as a plain tool despite its penalty");
            check(hammer.property(ArmoryProperty.ATTACK_SPEED)
                    < ironSword.property(ArmoryProperty.ATTACK_SPEED), "a hammer swings as fast as a sword");
            check(!hammer.traits().isEmpty() || !ironSword.traits().isEmpty(),
                    "no build carried a single trait through the pipeline");

            // An incomplete build is caught rather than computed.
            check(!new ArmoryGearBuild("sword", List.of(
                            new ArmoryGearBuild.Piece(swordBlade, List.of()))).complete(),
                    "a sword blade made of nothing counted as complete");
            check(!new ArmoryGearBuild("sword", List.of(
                            new ArmoryGearBuild.Piece(rod, List.of(wood2, wood2)))).complete(),
                    "a build with no main part counted as complete");
        }

        // ----------------------------------------------------------------
        // Traits: every file loads, every effect is a kind the code acts on,
        // and every trait a material or part grants is one that exists.
        // ----------------------------------------------------------------
        Path traitRoot = root.resolveSibling("elysium_traits");
        Map<String, ArmoryTraitDefinition> traits = ArmoryTraits.parseAll(read(traitRoot, "elysiumarmory:"));
        check(traits.size() >= 75, "only " + traits.size() + " trait(s) found under " + traitRoot);
        java.util.Set<String> KNOWN = java.util.Set.of("property", "attribute", "wielder_effect", "extra_damage",
                "durability", "self_repair", "synergy", "enchantment", "block_placer", "block_filler",
                "bonus_drops", "item_magnet", "mining_speed", "negate_damage", "cancel_effects",
                "target_effect", "fireproof", "wind_blast", "components", "code");
        int coded = 0;
        for (ArmoryTraitDefinition trait : traits.values()) {
            check(trait.maxLevel() > 0, trait.id() + " can never be granted: its maximum level is zero");
            check(!trait.name().isEmpty(), trait.id() + " has no name");
            coded += trait.coded() ? 1 : 0;
            for (com.google.gson.JsonObject effect : trait.effects()) {
                String type = effect.has("type") ? effect.get("type").getAsString() : "";
                check(KNOWN.contains(type), trait.id() + " has an effect of kind '" + type
                        + "', which nothing in the mod acts on");
            }
        }
        java.util.Set<String> granted = new java.util.HashSet<>();
        for (ArmoryMaterial material : materials.values()) {
            for (ArmoryMaterial.PartProperties properties : material.properties().values()) {
                for (ArmoryTrait trait : properties.traits()) {
                    granted.add(trait.trait());
                }
            }
        }
        for (ArmoryPart part : parts.values()) {
            for (ArmoryTrait trait : part.traits()) {
                granted.add(trait.trait());
            }
        }
        for (String trait : granted) {
            check(traits.containsKey("elysiumarmory:" + trait),
                    "a material or part grants '" + trait + "', which no trait file defines");
        }
        check(granted.size() >= 40, "only " + granted.size() + " trait(s) are granted by anything");

        System.out.println("materials: " + materials.size() + " files, " + main + " main materials ("
                + additives + " additive, " + tools + " making tools), "
                + ArmoryTier.list().size() + " harvest rungs, " + ArmoryPartType.real().size()
                + " part types, " + parts.size() + " part definitions, " + traits.size() + " traits ("
                + coded + " with behaviour in code) — " + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("every material and part file loads, and whole pieces of gear compute from both");
    }
}
