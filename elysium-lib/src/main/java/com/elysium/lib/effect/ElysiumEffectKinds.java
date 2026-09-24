package com.elysium.lib.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The vocabulary of item effects: everything an item can be made to do by
 * something attached to it.
 *
 * <h2>Why there is only one list</h2>
 *
 * Silent Gear traits and Apotheosis affixes are the same idea wearing
 * different words — a conditional effect attached to an item — and Elysium's
 * gems and runes are a third and fourth telling of it. Four implementations
 * means four answers to "does bonus damage stack with itself", four sets of
 * bugs, and a player who cannot reason about any of it.
 *
 * <p>So the kinds are named once, here, and everything that can be attached to
 * an item states its effects in these words. What reads them is
 * {@link ElysiumEffectHandlers}, also once.
 *
 * <h2>Two sorts of kind</h2>
 *
 * Most are <b>live</b>: they are read as the game runs — on a hit, on a tick,
 * when a block breaks. A few are <b>build</b> kinds, read once while a piece of
 * gear is being computed from its parts, and they mean nothing to something
 * bolted onto a finished item: an affix cannot change what a part contributes,
 * because by the time the affix exists the parts have already been added up.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ElysiumEffectKinds {

    private ElysiumEffectKinds() {
    }

    /** Behaviour that was never data in the first place, and is named in Java. */
    public static final String CODE = "code";

    private static final Map<String, Boolean> KINDS;

    static {
        Map<String, Boolean> m = new LinkedHashMap<>();
        // kind                      live?
        m.put("attribute",           true);
        m.put("block_filler",        true);
        m.put("block_placer",        true);
        m.put("bonus_drops",         true);
        m.put("cancel_effects",      true);
        m.put("components",          true);
        m.put("durability",          true);
        m.put("enchantment",         true);
        m.put("extra_damage",        true);
        m.put("fireproof",           true);
        m.put("item_magnet",         true);
        m.put("mining_speed",        true);
        m.put("negate_damage",       true);
        m.put("self_repair",         true);
        m.put("target_effect",       true);
        m.put("wielder_effect",      true);
        m.put("wind_blast",          true);
        // Build kinds: read while a piece is computed from its parts.
        m.put("property",            false);
        m.put("synergy",             false);
        // Behaviour in Java, named so the data still says the thing exists.
        m.put(CODE,                  false);
        KINDS = Collections.unmodifiableMap(m);
    }

    /** Every kind, in a stable order. */
    public static List<String> all() {
        return List.copyOf(KINDS.keySet());
    }

    public static boolean exists(String kind) {
        return kind != null && KINDS.containsKey(kind);
    }

    /**
     * Whether a kind is read as the game runs, rather than once while a piece
     * of gear is built. Only live kinds mean anything on an affix, a gem or a
     * rune — the parts were added up before any of those were attached.
     */
    public static boolean live(String kind) {
        return Boolean.TRUE.equals(KINDS.get(kind));
    }

    /** The live kinds: what anything bolted onto a finished item may state. */
    public static List<String> live() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : KINDS.entrySet()) {
            if (entry.getValue()) {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    /** The kinds that are read only while gear is built from parts. */
    public static List<String> build() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : KINDS.entrySet()) {
            if (!entry.getValue() && !entry.getKey().equals(CODE)) {
                out.add(entry.getKey());
            }
        }
        return out;
    }
}
