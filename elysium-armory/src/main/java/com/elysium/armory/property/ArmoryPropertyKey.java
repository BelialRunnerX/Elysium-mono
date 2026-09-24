package com.elysium.armory.property;

import java.util.Map;

/**
 * A property, optionally narrowed to a gear type: {@code armor} or
 * {@code armor/helmet} (Silent Gear's SG-4). A material can give an axe a
 * different attack speed from a sword while stating everything else once.
 *
 * <p>{@link #resolve} picks the most specific key that applies. It checks the
 * type itself, then each parent, and only then the plain key. On a hammer,
 * {@code harvest_speed/hammer} beats {@code harvest_speed/pickaxe}, which
 * beats {@code harvest_speed/harvest_tool}, which beats {@code harvest_speed}.
 */
public record ArmoryPropertyKey(String property, String gearType) {

    /** Parses "armor" or "armor/helmet". Returns null for anything malformed. */
    public static ArmoryPropertyKey parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int slash = text.indexOf('/');
        if (slash < 0) {
            return text.matches("[a-z0-9_]+") ? new ArmoryPropertyKey(text, null) : null;
        }
        String p = text.substring(0, slash);
        String g = text.substring(slash + 1);
        if (!p.matches("[a-z0-9_]+") || !g.matches("[a-z0-9_]+")) {
            return null;
        }
        return new ArmoryPropertyKey(p, g);
    }

    @Override
    public String toString() {
        return gearType == null ? property : property + "/" + gearType;
    }

    /** The value for {@code property} on {@code type}: most specific key wins, else {@code fallback}. */
    public static <V> V resolve(Map<ArmoryPropertyKey, V> values, String property,
                                ArmoryGearType type, V fallback) {
        if (type != null) {
            for (ArmoryGearType t : type.lineage()) {
                V v = values.get(new ArmoryPropertyKey(property, t.name()));
                if (v != null) {
                    return v;
                }
            }
        }
        V plain = values.get(new ArmoryPropertyKey(property, null));
        return plain != null ? plain : fallback;
    }

    // ------------------------------------------------------------------
    // Armour: the plain key is the whole set, as in Silent Gear's material
    // book. A piece uses its own armor/<slot> key when the material gives
    // one. Otherwise it takes vanilla's share of the set (3/8/6/3 out of 20),
    // so a material that states only a total still makes sensible pieces.
    // Toughness is split evenly, a quarter per piece, as Silent Gear does.
    // ------------------------------------------------------------------

    public static double armourShare(String slot) {
        return switch (slot) {
            case "helmet", "boots" -> 3.0D / 20.0D;
            case "chestplate" -> 8.0D / 20.0D;
            case "leggings" -> 6.0D / 20.0D;
            default -> 0.0D;
        };
    }

    public static double pieceArmour(Map<ArmoryPropertyKey, Double> values, ArmoryGearType piece) {
        Double own = piece == null ? null
                : values.get(new ArmoryPropertyKey(ArmoryProperty.ARMOR.id(), piece.name()));
        if (own != null) {
            return own;
        }
        Double set = values.get(new ArmoryPropertyKey(ArmoryProperty.ARMOR.id(), null));
        return set == null || piece == null ? 0.0D : set * armourShare(piece.name());
    }

    public static double pieceToughness(double setToughness) {
        return setToughness / 4.0D;
    }
}
