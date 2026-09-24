package com.elysium.armory.material;

import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Parsing a set of material files and folding every parent chain in.
 *
 * <p>Separate from the loader, and importing nothing from the game, so the
 * whole of it — parsing, inheritance, the loops and dangling parents that have
 * to drop one material without taking the rest with them — is executed by
 * tools/MaterialHarness.java against the files the mod actually ships.
 *
 * <p>A problem is reported to the {@code onProblem} callback with the id and
 * the reason; the loader logs it, the harness fails on it.
 */
public final class ArmoryMaterialTree {

    private ArmoryMaterialTree() {
    }

    /**
     * Every material, resolved. A file that cannot be parsed, names a parent
     * nobody defined, or inherits from itself is left out, with one problem
     * reported for it.
     */
    public static Map<String, ArmoryMaterial> resolve(Map<String, JsonObject> files,
                                                      BiConsumer<String, String> onProblem) {
        Map<String, ArmoryMaterial> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> entry : files.entrySet()) {
            try {
                parsed.put(entry.getKey(), ArmoryMaterial.parse(entry.getKey(), entry.getValue()));
            } catch (RuntimeException e) {
                onProblem.accept(entry.getKey(), e.getMessage());
            }
        }
        Map<String, ArmoryMaterial> out = new LinkedHashMap<>();
        for (ArmoryMaterial material : parsed.values()) {
            ArmoryMaterial done = fold(material, parsed, new HashSet<>(), onProblem);
            if (done != null) {
                out.put(done.id(), done);
            }
        }
        return out;
    }

    private static ArmoryMaterial fold(ArmoryMaterial material, Map<String, ArmoryMaterial> parsed,
                                       Set<String> seen, BiConsumer<String, String> onProblem) {
        if (material.parent() == null) {
            return material;
        }
        if (!seen.add(material.id())) {
            onProblem.accept(material.id(), "inherits from itself");
            return null;
        }
        ArmoryMaterial parent = parsed.get(material.parent());
        if (parent == null) {
            onProblem.accept(material.id(), "names parent " + material.parent() + ", which no pack defines");
            return null;
        }
        ArmoryMaterial folded = fold(parent, parsed, seen, onProblem);
        return folded == null ? null : material.inherit(folded);
    }
}
