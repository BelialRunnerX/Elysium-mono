package com.elysium.armory.material;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a mixture of materials is called (Silent Gear's SG-15 and SG-17).
 *
 * <h2>The gap this closes</h2>
 *
 * Five materials in the pack are named mixtures — Metal Alloy, Crude Alloy,
 * Super Alloy, Hybrid Gem, Mixed Fabric — and for a long time nothing read
 * them. They were five names sitting in five files while every mixed part went
 * on being called after whichever material happened to be laid down first, so
 * a blade of iron, copper and tin was an "Iron Blade" and a player had no way
 * to see that it was anything else.
 *
 * <h2>The rule</h2>
 *
 * A part of one material is that material. A part of several is the compound
 * they all belong to, if there is one:
 *
 * <ul>
 *   <li>all metals, and one of them from the deep end — a <b>Super Alloy</b>;</li>
 *   <li>all metals, and all of them from the shallow end — a <b>Crude Alloy</b>;</li>
 *   <li>all metals otherwise — a <b>Metal Alloy</b>;</li>
 *   <li>all gems — a <b>Hybrid Gem</b>;</li>
 *   <li>all cloth or fibre — <b>Mixed Fabric</b>.</li>
 * </ul>
 *
 * A mixture that fits none of them keeps the first material's name, which is
 * what every mixture used to do. Nothing is taken away; a name is added where
 * there was one to give.
 *
 * <p>This is a naming and classification rule, not a property one. What a
 * mixture is <em>worth</em> is still the average of what went into it, times
 * whatever synergy the Alloy Forge granted — those are separate questions and
 * they stay separate.
 */
public final class ArmoryCompounds {

    private ArmoryCompounds() {
    }

    public static final String METAL_ALLOY = "elysiumarmory:metal_alloy";
    public static final String CRUDE_ALLOY = "elysiumarmory:crude_alloy";
    public static final String SUPER_ALLOY = "elysiumarmory:super_alloy";
    public static final String HYBRID_GEM = "elysiumarmory:hybrid_gem";
    public static final String MIXED_FABRIC = "elysiumarmory:mixed_fabric";

    /** Every compound this rule can name, so a validator can check none is unreachable. */
    public static final List<String> ALL =
            List.of(METAL_ALLOY, CRUDE_ALLOY, SUPER_ALLOY, HYBRID_GEM, MIXED_FABRIC);

    /**
     * The compound a set of materials makes, or null when they make none.
     *
     * @param materials the distinct materials in one part, in the order laid down
     */
    public static String classify(List<ArmoryMaterial> materials) {
        List<ArmoryMaterial> distinct = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ArmoryMaterial material : materials) {
            if (material != null && seen.add(material.id())) {
                distinct.add(material);
            }
        }
        if (distinct.size() < 2) {
            return null;                    // one material is that material
        }
        if (all(distinct, ArmoryCategory.METAL)) {
            if (any(distinct, ArmoryCategory.ENDGAME)) {
                return SUPER_ALLOY;
            }
            return all(distinct, ArmoryCategory.BASIC) ? CRUDE_ALLOY : METAL_ALLOY;
        }
        if (all(distinct, ArmoryCategory.GEM)) {
            return HYBRID_GEM;
        }
        if (allOfEither(distinct, ArmoryCategory.CLOTH, ArmoryCategory.FIBER)) {
            return MIXED_FABRIC;
        }
        return null;
    }

    /** The compound's own material, for its name and colour, or null. */
    public static ArmoryMaterial named(List<ArmoryMaterial> materials) {
        String id = classify(materials);
        return id == null ? null : ArmoryMaterials.get().byId(id);
    }

    private static boolean all(List<ArmoryMaterial> materials, ArmoryCategory category) {
        for (ArmoryMaterial material : materials) {
            if (!material.categories().contains(category)) {
                return false;
            }
        }
        return true;
    }

    private static boolean any(List<ArmoryMaterial> materials, ArmoryCategory category) {
        for (ArmoryMaterial material : materials) {
            if (material.categories().contains(category)) {
                return true;
            }
        }
        return false;
    }

    private static boolean allOfEither(List<ArmoryMaterial> materials,
                                       ArmoryCategory first, ArmoryCategory second) {
        for (ArmoryMaterial material : materials) {
            if (!material.categories().contains(first) && !material.categories().contains(second)) {
                return false;
            }
        }
        return true;
    }
}
