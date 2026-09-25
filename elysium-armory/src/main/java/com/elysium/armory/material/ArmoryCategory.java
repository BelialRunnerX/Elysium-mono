package com.elysium.armory.material;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a material is made of (Silent Gear's SG-11). Categories decide what a
 * recipe will accept — a ring wants metal, an elytra wing wants cloth or
 * sheet — and how well two materials in the same part work together (synergy,
 * which lands with compound materials).
 *
 * <p>Kept as an enum rather than free strings: a typo in a material file is
 * then a load error naming the file, not a material that silently matches
 * nothing for the rest of the game.
 *
 * <p>Imports nothing, and must not: it is executed by a harness.
 */
public enum ArmoryCategory {
    METAL, ROCK, GEM, ORGANIC, WOOD, CLOTH, FIBER, DUST, SHEET, SLIME,
    BASIC, INTERMEDIATE, ADVANCED, ENDGAME, INTANGIBLE,
    /** The Cordoned Planet's (docs/SPEC_VALORIA.md): what Valoria's gear sets are made of, as a group a recipe can ask for. */
    CORDON;

    /** The lower-case id used in material files. */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static ArmoryCategory byId(String id) {
        for (ArmoryCategory c : values()) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        return null;
    }

    /** The four rungs of progression, in order; a material carries at most one. */
    public static final List<ArmoryCategory> TIERS = List.of(BASIC, INTERMEDIATE, ADVANCED, ENDGAME);

    /** The union of two materials' categories, which is what a compound of them has. */
    public static Set<ArmoryCategory> union(Set<ArmoryCategory> a, Set<ArmoryCategory> b) {
        Set<ArmoryCategory> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return out;
    }
}
