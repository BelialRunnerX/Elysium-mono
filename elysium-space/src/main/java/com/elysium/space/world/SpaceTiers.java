package com.elysium.space.world;

import com.elysium.lib.tier.ElysiumTiers;
import com.elysium.lib.tier.ElysiumWorldTier;
import com.elysium.space.ElysiumSpace;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What standing each of these worlds takes to be standing on.
 *
 * <h2>Read off the map, not invented</h2>
 *
 * The worlds are already ordered by how far they are from home, and that order
 * is the difficulty order — the fuel to reach Frostfall is the reason nobody
 * arrives there early. So the tier of a world is its place in that order, and
 * it cannot drift from the route table: the validator reads both.
 *
 * <p>The Overworld is not here. Being at home is not an achievement.
 */
public final class SpaceTiers {

    private SpaceTiers() {
    }

    public static final Map<String, Integer> WORLDS;

    static {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("high_orbit", ElysiumWorldTier.ASCENT);
        m.put("pale_moon", ElysiumWorldTier.ASCENT);
        m.put("cinderveil", ElysiumWorldTier.SUMMIT);
        m.put("frostfall", ElysiumWorldTier.PINNACLE);
        m.put("emerald_bastion", ElysiumWorldTier.APOTHEOSIS);
        WORLDS = java.util.Collections.unmodifiableMap(m);
    }

    /** Tells the library what these worlds are worth. Called once, at mod construction. */
    public static void declare() {
        for (Map.Entry<String, Integer> entry : WORLDS.entrySet()) {
            ElysiumTiers.declareWorld(
                    ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, entry.getKey()),
                    entry.getValue());
        }
    }
}
