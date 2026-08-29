package com.elysium.core.tooltip;

import com.elysium.core.Elysium;
import net.neoforged.fml.ModList;

/**
 * Legendary Tooltips integration.
 *
 * Legendary Tooltips is configured through its own config file, keyed on item
 * ids and rarities, so nothing has to be registered from code for Elysium gear
 * to pick up a fancy border. Elysium's tier line is drawn by
 * {@code ElysiumArmorItem#appendHoverText} and shows up inside that border.
 *
 * The previous version called {@code ModList} without importing it, so it never
 * compiled.
 */
public final class ElysiumLegendaryTooltips {

    private ElysiumLegendaryTooltips() {
    }

    public static void register() {
        if (!ModList.get().isLoaded("legendarytooltips")) {
            Elysium.LOGGER.debug("Legendary Tooltips not detected - skipping integration");
            return;
        }

        Elysium.LOGGER.info("Legendary Tooltips detected - Elysium tier lines will render "
                + "inside its frames");
    }
}
