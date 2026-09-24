package com.elysium.armory.material;

import com.elysium.armory.property.ArmoryGearType;
import com.elysium.armory.property.ArmoryModifier;
import com.elysium.armory.property.ArmoryProperty;
import com.elysium.armory.property.ArmoryPropertyKey;
import com.elysium.armory.property.ArmoryTier;
import com.elysium.armory.property.ArmoryTraitRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A piece of gear as the sum of its parts (Silent Gear's SG-3 pipeline).
 *
 * <p>The order is material, then part, then gear, then traits:
 *
 * <ol>
 *   <li>every material in every part contributes its modifiers for that part
 *       type, narrowed to this gear type where it states a narrower key
 *       ({@code attack_speed/axe} on an axe);</li>
 *   <li>each part definition adds the offsets of its shape;</li>
 *   <li>{@link ArmoryProperty#compute} folds them in one order — averages,
 *       floors, base multipliers, total multipliers, then flat additions;</li>
 *   <li>trait bonuses, when P4's effect engine resolves them, are more
 *       modifiers on the same list.</li>
 * </ol>
 *
 * <p>Materials within a part average toward the stronger one, which is
 * {@link com.elysium.armory.property.ArmoryCompute}'s weighted mean and the
 * reason one good ingot in a hammer head is worth putting there.
 *
 * <p>The harvest tier is the exception: gear takes the <em>best</em> tier among
 * its parts, never the average, so a netherite tip mines what netherite mines.
 */
public record ArmoryGearBuild(String gearType, List<Piece> pieces) {

    /** One part of the piece, and what it was made of. */
    public record Piece(ArmoryPart part, List<ArmoryMaterial> materials) {

        public Piece {
            materials = List.copyOf(materials);
        }
    }

    public ArmoryGearBuild {
        pieces = List.copyOf(pieces);
    }

    private ArmoryGearType type() {
        return ArmoryGearType.BUILTIN.get(gearType);
    }

    /** Every modifier that bears on one property, in pipeline order. */
    public List<ArmoryModifier> modifiers(ArmoryProperty property) {
        List<ArmoryModifier> out = new ArrayList<>();
        for (Piece piece : pieces) {
            String partType = piece.part().partType();
            for (ArmoryMaterial material : piece.materials()) {
                List<ArmoryModifier> found = ArmoryPropertyKey.resolve(
                        material.forPart(partType).modifiers(), property.id(), type(), null);
                if (found != null) {
                    out.addAll(found);
                }
            }
            List<ArmoryModifier> shape = ArmoryPropertyKey.resolve(
                    piece.part().modifiers(), property.id(), type(), null);
            if (shape != null) {
                out.addAll(shape);
            }
        }
        return out;
    }

    /** What this piece of gear has of a property. */
    public double property(ArmoryProperty property) {
        return property.compute(modifiers(property));
    }

    /** Every property the build states a value for, computed. */
    public Map<ArmoryProperty, Double> properties() {
        Map<ArmoryProperty, Double> out = new LinkedHashMap<>();
        for (ArmoryProperty property : ArmoryProperty.ALL) {
            out.put(property, property(property));
        }
        return out;
    }

    /** The best tier among the parts: what this gear can mine. */
    public ArmoryTier tier() {
        ArmoryTier best = null;
        for (Piece piece : pieces) {
            for (ArmoryMaterial material : piece.materials()) {
                best = ArmoryTier.best(best, material.forPart(piece.part().partType()).tier());
            }
        }
        return best;
    }

    /**
     * Every trait grant on this piece, with the ones whose conditions the part
     * does not meet already dropped.
     *
     * <p>A material's grant is judged against the part it is in: how much of
     * that part is this material (the ratio) and how many of its slots it fills
     * (the count). A part's own grant is unconditional on the part and judged
     * only on the gear type.
     */
    public List<ArmoryTraitRules.Instance> traitGrants() {
        List<String> lineage = new ArrayList<>();
        ArmoryGearType type = type();
        if (type != null) {
            for (ArmoryGearType parent : type.lineage()) {
                lineage.add(parent.name());
            }
        }
        List<ArmoryTraitRules.Instance> out = new ArrayList<>();
        for (Piece piece : pieces) {
            int size = Math.max(1, piece.materials().size());
            for (ArmoryMaterial material : piece.materials()) {
                int count = 0;
                for (ArmoryMaterial other : piece.materials()) {
                    if (other.id().equals(material.id())) {
                        count++;
                    }
                }
                ArmoryTraitRules.Context context = new ArmoryTraitRules.Context(
                        (double) count / size, count, gearType, lineage);
                for (ArmoryTrait trait : material.forPart(piece.part().partType()).traits()) {
                    ArmoryTraitRules.Instance instance = new ArmoryTraitRules.Instance(
                            trait.trait(), trait.level(), trait.conditions());
                    if (ArmoryTraitRules.granted(instance, context)) {
                        out.add(instance);
                    }
                }
            }
            ArmoryTraitRules.Context shapeContext = new ArmoryTraitRules.Context(1.0D, 1, gearType, lineage);
            for (ArmoryTrait trait : piece.part().traits()) {
                ArmoryTraitRules.Instance instance = new ArmoryTraitRules.Instance(
                        trait.trait(), trait.level(), trait.conditions());
                if (ArmoryTraitRules.granted(instance, shapeContext)) {
                    out.add(instance);
                }
            }
        }
        return out;
    }

    /** How many grants there were in all, before conditions: the divisor the levels are averaged by. */
    public int traitGrantCount() {
        int total = 0;
        for (Piece piece : pieces) {
            for (ArmoryMaterial material : piece.materials()) {
                total += material.forPart(piece.part().partType()).traits().size();
            }
            total += piece.part().traits().size();
        }
        return total;
    }

    /** Every trait the materials and shapes grant, with the conditions they were granted under. */
    public List<ArmoryTrait> traits() {
        List<ArmoryTrait> out = new ArrayList<>();
        for (Piece piece : pieces) {
            for (ArmoryMaterial material : piece.materials()) {
                out.addAll(material.forPart(piece.part().partType()).traits());
            }
            out.addAll(piece.part().traits());
        }
        return out;
    }

    /** The piece's main part, which is what it mostly is; null for a build with none. */
    public Piece main() {
        for (Piece piece : pieces) {
            if (piece.part().partType().equals("main")) {
                return piece;
            }
        }
        return null;
    }

    /**
     * Whether this is a piece of gear at all: it has a main part, every part
     * holds material, and no material is in a part or on a gear type it is not
     * allowed in.
     *
     * <p>How many materials a part <em>takes</em> is a recipe question, not a
     * question about a finished piece — a rod's recipe turns two materials into
     * four rods, and a stick counts as a whole rod on its own. That check is
     * {@link #matchesRecipe}.
     */
    public boolean complete() {
        if (main() == null) {
            return false;
        }
        for (Piece piece : pieces) {
            if (piece.materials().isEmpty() && !piece.part().isUpgrade()) {
                return false;
            }
            for (ArmoryMaterial material : piece.materials()) {
                if (!material.supports(piece.part().partType())
                        || material.gearTypeBlacklist().contains(gearType)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether a part was made of exactly the number of materials its recipe calls for. */
    public static boolean matchesRecipe(Piece piece) {
        int needed = piece.part().crafting().materials();
        return needed <= 0 || piece.materials().size() == needed;
    }
}
