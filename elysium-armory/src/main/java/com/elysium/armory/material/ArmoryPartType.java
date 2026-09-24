package com.elysium.armory.material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The twelve kinds of part a piece of gear is built from (Silent Gear's SG-20
 * and SG-25), and what each one is for.
 *
 * <p>{@code main} is the head, blade or plates and is what the gear mostly is;
 * everything else adjusts it. A removable part can be taken back out with a mod
 * kit; {@code misc_upgrade} is the only one a piece can hold more than one of.
 *
 * <p>Imports nothing, and must not: it is executed by a harness.
 */
public record ArmoryPartType(String name, boolean removable, boolean paintable, boolean upgrade,
                             int maxPerItem, String what) {

    public static final Map<String, ArmoryPartType> ALL;

    static {
        Map<String, ArmoryPartType> m = new LinkedHashMap<>();
        Object[][] rows = {
                {"main", false, true, false, 1, "the head, blade or plates: most of what the gear is"},
                {"rod", false, true, false, 1, "what it is held by: durability, reach and swing"},
                {"tip", true, true, false, 1, "the working edge: harvest tier and speed"},
                {"cord", false, true, false, 1, "a bow's string: draw speed and ranged damage"},
                {"fletching", false, true, false, 1, "an arrow's feathers: accuracy and flight"},
                {"binding", true, true, false, 1, "what holds the head on: repair and magic armour"},
                {"coating", true, true, false, 1, "what it is plated with: rarity, and the plating's own traits"},
                {"grip", true, true, false, 1, "what the hand meets: attack speed and comfort"},
                {"lining", true, true, false, 1, "what armour is padded with: toughness and warmth"},
                {"setting", true, true, false, 1, "a jewel's mount: what a curio carries"},
                {"misc_upgrade", true, false, true, 256, "an upgrade bolted on afterwards"},
                {"none", false, false, false, 0, "no part at all: the empty slot"},
        };
        for (Object[] r : rows) {
            m.put((String) r[0], new ArmoryPartType((String) r[0], (Boolean) r[1], (Boolean) r[2],
                    (Boolean) r[3], (Integer) r[4], (String) r[5]));
        }
        ALL = Map.copyOf(m);
    }

    public static ArmoryPartType named(String name) {
        return ALL.get(name);
    }

    /** Every part type but {@code none}, in declaration order. */
    public static List<ArmoryPartType> real() {
        List<ArmoryPartType> out = new java.util.ArrayList<>(ALL.values());
        out.removeIf(t -> t.name().equals("none"));
        return List.copyOf(out);
    }

    /** Whether a piece of gear may hold more than one of these. */
    public boolean stacks() {
        return maxPerItem > 1;
    }
}
