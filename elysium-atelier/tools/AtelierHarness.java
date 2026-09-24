package com.elysium.atelier.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Every number in {@link AtelierRules}, executed.
 *
 * Two hundred blocks generated from twelve shapes and sixteen materials, and
 * the one claim worth making about them: no arrangement of crafting and
 * breaking ever comes back with more wood than it started with.
 */
public final class AtelierHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + complaint);
            failures++;
        }
    }

    public static void main(String[] args) {
        theCatalogue();
        thePrices();
        theLoops();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: every shape in every material, every "
                + "recipe costing at least what it makes, and over a million random "
                + "runs of crafting and breaking not one that came back with more wood "
                + "than it started with");
    }

    // ==================================================================

    private static void theCatalogue() {
        check(AtelierRules.SHAPES.size() >= 10,
                "there are only " + AtelierRules.SHAPES.size() + " shapes, which is not "
                        + "a decoration module, it is a block");
        check(AtelierRules.MATERIALS.size() >= 12,
                "there are only " + AtelierRules.MATERIALS.size() + " materials");

        Set<String> names = new HashSet<>();
        for (AtelierRules.Shape shape : AtelierRules.SHAPES) {
            check(names.add(shape.name()), shape.name() + " is declared twice");
            check(shape.name().matches("[a-z_]+"),
                    shape.name() + " is not a name a block id can be made of");
        }
        Set<String> materials = new HashSet<>();
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            check(materials.add(material.name()), material.name() + " is declared twice");
            check(material.block().startsWith("minecraft:"),
                    material.name() + " is made of " + material.block() + ", which is "
                            + "not vanilla — and a decoration module that needs another "
                            + "mod installed is a decoration module nobody can use");
            check(material.face().startsWith("minecraft:block/"),
                    material.name() + " wears " + material.face() + ", which is not a "
                            + "vanilla texture");
        }

        List<String> catalogue = AtelierRules.catalogue();
        check(catalogue.size() == AtelierRules.howMany(),
                "the catalogue has " + catalogue.size() + " entries and says it has "
                        + AtelierRules.howMany());
        check(new HashSet<>(catalogue).size() == catalogue.size(),
                "two blocks in the catalogue have the same id");
        check(catalogue.size() >= 150,
                "the catalogue is " + catalogue.size() + " blocks, which is fewer than "
                        + "any one of the thirty jars this replaces");
        System.out.println("  catalogue: " + AtelierRules.SHAPES.size() + " shapes x "
                + AtelierRules.MATERIALS.size() + " materials = " + catalogue.size()
                + " blocks, all named, all vanilla-made");
    }

    private static void thePrices() {
        for (AtelierRules.Shape shape : AtelierRules.SHAPES) {
            check(shape.units() > 0 && shape.units() <= AtelierRules.WHOLE,
                    shape.name() + " costs " + shape.units() + " eighths, which is "
                            + "either free or more than the block it is cut from");
            check(!shape.profitable(),
                    shape.name() + " makes " + shape.yields() + " units out of "
                            + shape.spends() + ", which is a loop a player can ride");
            check(shape.spends() - shape.yields() <= AtelierRules.WHOLE,
                    shape.name() + " wastes " + (shape.spends() - shape.yields())
                            + " units a craft, which is more than a whole block and "
                            + "nobody will ever make one");
            check(shape.made() <= 16,
                    shape.name() + " makes " + shape.made() + " at a time, which is "
                            + "more than fits a thought");
        }
        check(AtelierRules.profitable().isEmpty(),
                "profitable shapes: " + AtelierRules.profitable());
        check(AtelierRules.recoverable().isEmpty(),
                "shapes you could craft and break for a profit: "
                        + AtelierRules.recoverable());

        Map<String, Integer> priced = AtelierRules.priced();
        check(priced.size() == AtelierRules.howMany(),
                "only " + priced.size() + " of " + AtelierRules.howMany()
                        + " blocks have a price");
        for (Map.Entry<String, Integer> entry : priced.entrySet()) {
            check(entry.getValue() > 0, entry.getKey() + " is worth nothing");
        }
        // The same shape costs the same whatever it is cut from. A cherry
        // chair that is cheaper than an oak one is a cherry farm.
        for (AtelierRules.Shape shape : AtelierRules.SHAPES) {
            int first = -1;
            for (AtelierRules.Material material : AtelierRules.MATERIALS) {
                int price = priced.get(material.name() + "_" + shape.name());
                if (first < 0) {
                    first = price;
                }
                check(price == first,
                        shape.name() + " costs " + price + " in " + material.name()
                                + " and " + first + " elsewhere, so one material is a "
                                + "cheaper way of having the same block");
            }
        }
        System.out.println("  prices: every shape costing at least what it makes, "
                + "wasting less than a block, and costing the same in every material");
    }

    /**
     * The one the module exists for.
     *
     * <p>Start with a pile of blocks, craft and break at random for a while,
     * and count the wood. It may fall. It may not rise.
     */
    private static void theLoops() {
        Random random = new Random(8_675_310L);
        long crafts = 0;
        long breaks = 0;
        for (int life = 0; life < 2_000; life++) {
            Map<String, Integer> held = new HashMap<>();
            int blocks = 64 + random.nextInt(512);
            long units = (long) blocks * AtelierRules.WHOLE;
            long was = units;

            for (int act = 0; act < 600; act++) {
                if (random.nextBoolean()) {
                    AtelierRules.Shape shape = AtelierRules.SHAPES.get(
                            random.nextInt(AtelierRules.SHAPES.size()));
                    if (blocks < shape.of()) {
                        continue;
                    }
                    blocks -= shape.of();
                    held.merge(shape.name(), shape.made(), Integer::sum);
                    units = units - shape.spends() + shape.yields();
                    crafts++;
                } else {
                    List<String> have = new ArrayList<>(held.keySet());
                    if (have.isEmpty()) {
                        continue;
                    }
                    String name = have.get(random.nextInt(have.size()));
                    AtelierRules.Shape shape = AtelierRules.shape(name);
                    int count = held.get(name);
                    if (shape == null || count <= 0) {
                        continue;
                    }
                    int broken = 1 + random.nextInt(count);
                    held.merge(name, -broken, Integer::sum);
                    if (held.get(name) <= 0) {
                        held.remove(name);
                    }
                    int back = shape.recovers() * broken;
                    blocks += back;
                    units = units - (long) broken * shape.units()
                            + (long) back * AtelierRules.WHOLE;
                    breaks++;
                }
                check(units <= was,
                        "a pile of wood grew from " + was + " units to " + units
                                + " on act " + act + " of life " + life);
                check(units >= 0, "a pile of wood went negative");
                was = units;
            }
        }
        check(crafts > 100_000 && breaks > 100_000,
                "the walk did " + crafts + " crafts and " + breaks + " breaks, so one "
                        + "of the two directions is untested");
        System.out.println("  loops: 2,000 lives, " + crafts + " crafts and " + breaks
                + " breaks, and the pile never grew once");
    }
}
