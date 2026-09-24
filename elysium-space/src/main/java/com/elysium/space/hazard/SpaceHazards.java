package com.elysium.space.hazard;

import com.elysium.lib.element.ElysiumElements;
import com.elysium.lib.hazard.ElysiumHazard;
import com.elysium.space.ElysiumSpace;
import net.minecraft.resources.ResourceLocation;

/**
 * Vacuum: no air to breathe. The one hazard the space module adds. The others a
 * world can have (heat, cold, corrosion, radiation, pressure) are the library's
 * own.
 *
 * <p>Void is its element, so a complete Void armour set seals it, as with every
 * hazard. Standing in a room an Oxygen Distributor fills takes vacuum to
 * nothing (see {@link SpaceAtmosphere}). The damage is the highest of any
 * hazard: suffocation is fast, and the point of a space base is that its air is
 * worth building.
 */
public final class SpaceHazards {

    private SpaceHazards() {
    }

    public static final ElysiumHazard VACUUM = ElysiumHazard.register(
            ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, "vacuum"),
            ElysiumElements.VOID, 3.0F);

    /** Touching the class registers the hazard. */
    public static void bootstrap() {
    }
}
