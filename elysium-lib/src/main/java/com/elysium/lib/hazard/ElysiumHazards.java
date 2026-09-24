package com.elysium.lib.hazard;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.element.ElysiumElements;
import net.minecraft.resources.ResourceLocation;

/**
 * The five hazards of the specification, and nothing else.
 *
 * <h2>Why these live in the library</h2>
 *
 * For the same reason the five elements do. The resistance engine resolves
 * every hazard against every piece of gear, and a content mod that invented its
 * own Thermal would be describing a different world. Add-ons may register a
 * sixth and bind it to any element they like; these five are the Empire's.
 *
 * <h2>Damage rates</h2>
 *
 * All five are set against the same yardstick: unprotected, at full intensity,
 * a hazard should be survivable for long enough to walk out of it and fatal to
 * anybody who stands in it. The differences between them are deliberate rather
 * than decorative — Radiological is the slowest because it is the one a player
 * cannot see, and a fast invisible killer is a death with no lesson in it.
 */
public final class ElysiumHazards {

    private ElysiumHazards() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, path);
    }

    /** Daylight on a scorched world, lava fields, firestorms. */
    public static final ElysiumHazard THERMAL = ElysiumHazard.register(
            id("thermal"), ElysiumElements.PLASMA, 2.0F);

    /** Night on a frozen world, altitude, whiteout. */
    public static final ElysiumHazard CRYOGENIC = ElysiumHazard.register(
            id("cryogenic"), ElysiumElements.KINETIC, 1.8F);

    /** Toxic air, acid rain, spore bloom. Constant rather than peaking. */
    public static final ElysiumHazard CORROSIVE = ElysiumHazard.register(
            id("corrosive"), ElysiumElements.NEURAL, 1.5F);

    /**
     * Fallout and reactor leakage.
     *
     * The slowest of the five on purpose. It is the only one with no visible
     * source, so a player has to be given time to notice the damage, find the
     * cause and leave — a fast invisible killer teaches nothing.
     */
    public static final ElysiumHazard RADIOLOGICAL = ElysiumHazard.register(
            id("radiological"), ElysiumElements.VOID, 1.0F);

    /** Depth. The one hazard that is purely a function of where you are. */
    public static final ElysiumHazard PRESSURE = ElysiumHazard.register(
            id("pressure"), ElysiumElements.DIMENSIONAL, 2.4F);

    /** Touching this class registers all five. Called from the library's setup. */
    public static void bootstrap() {
        // The static initialisers above are the registration.
    }
}
