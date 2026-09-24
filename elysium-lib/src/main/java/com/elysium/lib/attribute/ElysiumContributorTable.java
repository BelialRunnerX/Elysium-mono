package com.elysium.lib.attribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A registry of named contributors, with no game types, so a harness can run
 * it. {@link ElysiumStackModifiers} is built on it.
 *
 * <ul>
 *   <li><b>One name, one contributor.</b> Registering a name twice throws.
 *       Silently replacing would let one module switch off another's affixes
 *       without anything saying so.</li>
 *   <li><b>A stable order.</b> Contributors run sorted by name, never in
 *       registration order. Class-loading order changes between the merged jar
 *       and the separate jars, and the same stack must come out with the same
 *       modifiers either way.</li>
 *   <li><b>Each contributor stays in its own namespace.</b> A contributor
 *       registered as {@code ns:path} may only add modifiers whose id is in
 *       {@code ns}. Modifier ids are how vanilla de-duplicates, so a stray one
 *       could remove another module's modifier.</li>
 * </ul>
 */
public final class ElysiumContributorTable<C> {

    private final Map<String, C> byName = new TreeMap<>();

    /** Names are "namespace:path", lower case. */
    public synchronized void register(String name, C contributor) {
        if (name == null || !name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("contributor name must be namespace:path, got " + name);
        }
        if (contributor == null) {
            throw new IllegalArgumentException("contributor " + name + " is null");
        }
        if (byName.putIfAbsent(name, contributor) != null) {
            throw new IllegalStateException("contributor " + name + " is already registered");
        }
    }

    /** Every contributor, sorted by name. The result is a copy. */
    public synchronized List<Map.Entry<String, C>> ordered() {
        return Collections.unmodifiableList(new ArrayList<>(byName.entrySet()));
    }

    public synchronized int size() {
        return byName.size();
    }

    /** Whether the contributor named {@code contributor} may add a modifier with id {@code modifierId}. */
    public static boolean owns(String contributor, String modifierId) {
        int a = contributor.indexOf(':');
        int b = modifierId.indexOf(':');
        return a > 0 && b > 0 && contributor.substring(0, a).equals(modifierId.substring(0, b));
    }
}
