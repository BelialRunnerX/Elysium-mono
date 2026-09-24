package com.elysium.armory.property;

import com.elysium.lib.item.ElysiumGearTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The gear-type tree (Silent Gear's SG-30, which SG-4's keys depend on). Each
 * type has a parent up to {@code all}, and a property key for a type also
 * applies to its descendants unless a closer key overrides it. This covers the
 * tree only; tool actions and part requirements come with the items (P3).
 *
 * <h2>Where the rows come from</h2>
 *
 * Not from here. The tree is {@link ElysiumGearTree}, in the library, because
 * traits are not the only system that needs to say "this applies to swords" —
 * affixes, gems and runes say it too, and a second copy of the tree is a
 * second opinion about what a sword is. This class is the armory's reading of
 * that one tree, kept so the property engine's own vocabulary
 * ({@code ArmoryGearType}) does not change under everything that uses it.
 *
 * <p>Still pure: {@link ElysiumGearTree} imports the JDK and nothing else, so
 * the harness compiles and runs both together without a game.
 */
public record ArmoryGearType(String name, String parent) {

    public static final Map<String, ArmoryGearType> BUILTIN;

    static {
        Map<String, ArmoryGearType> m = new LinkedHashMap<>();
        for (ElysiumGearTree.Kind kind : ElysiumGearTree.ALL.values()) {
            m.put(kind.name(), new ArmoryGearType(kind.name(), kind.parent()));
        }
        BUILTIN = Collections.unmodifiableMap(m);
    }

    public static ArmoryGearType named(String name) {
        return BUILTIN.get(name);
    }

    /**
     * This type, then its parent, up to {@code all}. Stops if a parent is
     * missing or the chain loops, so bad data cannot hang a lookup.
     */
    public List<ArmoryGearType> lineage() {
        List<ArmoryGearType> out = new ArrayList<>();
        for (String step : ElysiumGearTree.lineage(name)) {
            ArmoryGearType type = BUILTIN.get(step);
            if (type != null) {
                out.add(type);
            }
        }
        return out;
    }

    public boolean is(String ancestor) {
        return ElysiumGearTree.is(name, ancestor);
    }
}
