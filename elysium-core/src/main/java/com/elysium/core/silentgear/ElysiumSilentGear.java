package com.elysium.core.silentgear;

import com.elysium.core.Elysium;
import net.neoforged.fml.ModList;

/**
 * Silent Gear integration.
 *
 * Elysium ships material definitions under
 * {@code data/elysium/silentgear_materials/}, which is the datapack route and
 * needs no code dependency at all - Silent Gear loads them itself when it is
 * installed. This class only reports on that and gives a place to hang
 * API-level registration later.
 *
 * Two things were wrong before. The class called {@code ModList} without
 * importing it, so it never compiled; and the material files were in the
 * 1.16-1.20 format, under the 1.16-1.20 folder, which Silent Gear 1.21 does not
 * read - so even once it compiled, the integration did nothing at all.
 */
public final class ElysiumSilentGear {

    private ElysiumSilentGear() {
    }

    public static void register() {
        if (!ModList.get().isLoaded("silentgear")) {
            Elysium.LOGGER.debug("Silent Gear not detected - skipping integration");
            return;
        }

        Elysium.LOGGER.info("Silent Gear detected - Elysium materials are supplied via "
                + "data/elysium/silentgear_materials (neutronium, aetherium)");

        // Anything deeper - registering traits or stats through Silent Gear's
        // Java API - has to live in a separate class that is only loaded from
        // inside this branch, otherwise Silent Gear becomes a hard dependency.
    }
}
