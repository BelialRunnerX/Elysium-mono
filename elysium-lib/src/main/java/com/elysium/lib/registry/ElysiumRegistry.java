package com.elysium.lib.registry;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A small typed registry, keyed by {@link ResourceLocation}.
 *
 * <h2>Why not a Minecraft registry</h2>
 *
 * NeoForge can build real registries, and they buy three things: automatic
 * client sync, datapack-defined entries, and tags. None of the four things
 * registered here needs any of them. A race is a Java object with behaviour
 * attached — it cannot be expressed as JSON without inventing a scripting
 * language first, and behaviour cannot be synced to a client that does not
 * have the mod that defines it.
 *
 * What they would cost is real: a registration lifecycle to get wrong, a sync
 * packet per registry, and an ordering constraint between mods. This is a map
 * with a freeze on it, which is the amount of machinery the problem actually
 * has.
 *
 * <h2>Order and stability</h2>
 *
 * Insertion-ordered, so a character screen lists entries in the order mods
 * registered them and the built-ins always come first. Ids are the persistence
 * key — a player's race is stored as {@code "elysium:imperial"} — so renaming
 * an id orphans every character that had it. Treat an id as permanent once
 * released.
 *
 * <h2>Freezing</h2>
 *
 * Registration is only legal during mod construction. After the first read the
 * registry locks, and a later {@code register} throws rather than silently
 * adding an entry that half the game has already cached. That is deliberate:
 * an add-on that registers too late is a bug with a clear message here, and an
 * intermittent mystery otherwise.
 */
public final class ElysiumRegistry<T> {

    private final String what;
    private final String defaultNamespace;
    private final Map<ResourceLocation, T> entries = new LinkedHashMap<>();
    private boolean frozen;

    public ElysiumRegistry(String what) {
        this(what, "elysiumlib");
    }

    /**
     * @param defaultNamespace assumed when a stored id has no namespace of its
     *                         own. Values were once persisted as bare names —
     *                         {@code "vitality"} rather than
     *                         {@code "elysiumlib:vitality"} — and a save from
     *                         then should still load rather than silently
     *                         losing the character's stats.
     */
    public ElysiumRegistry(String what, String defaultNamespace) {
        this.what = what;
        this.defaultNamespace = defaultNamespace;
    }

    /**
     * Adds an entry. Call from your mod's constructor.
     *
     * @throws IllegalStateException if the registry has already been read, or
     *                               if the id is already taken
     */
    public T register(ResourceLocation id, T value) {
        if (frozen) {
            throw new IllegalStateException(
                    "Elysium " + what + " registry is frozen: '" + id + "' was registered after "
                    + "the first read. Register during mod construction, before anything asks "
                    + "the registry a question.");
        }
        if (entries.containsKey(id)) {
            throw new IllegalStateException(
                    "Elysium " + what + " '" + id + "' is already registered. Two mods have "
                    + "chosen the same id; one of them needs its own namespace.");
        }
        entries.put(id, value);
        return value;
    }

    /** @return the entry, or null when nothing is registered under that id */
    public T get(ResourceLocation id) {
        freeze();
        return id == null ? null : entries.get(id);
    }

    /**
     * @return the entry for a string id, or null — used when reading a value
     *         off a player, where the saved string may name something no longer
     *         installed
     */
    public T get(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        // A bare name is read as belonging to the default namespace, so data
        // written before ids were namespaced still resolves.
        String qualified = id.indexOf(':') < 0 ? defaultNamespace + ":" + id : id;
        ResourceLocation key = ResourceLocation.tryParse(qualified);
        return key == null ? null : get(key);
    }

    public ResourceLocation idOf(T value) {
        freeze();
        for (Map.Entry<ResourceLocation, T> entry : entries.entrySet()) {
            if (entry.getValue() == value) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Every entry, in registration order. */
    public Collection<T> all() {
        freeze();
        return java.util.Collections.unmodifiableCollection(entries.values());
    }

    public Collection<ResourceLocation> ids() {
        freeze();
        return java.util.Collections.unmodifiableCollection(entries.keySet());
    }

    public int size() {
        freeze();
        return entries.size();
    }

    public boolean isFrozen() {
        return frozen;
    }

    private void freeze() {
        frozen = true;
    }
}
