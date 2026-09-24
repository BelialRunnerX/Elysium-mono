package com.elysium.core.screen;

import com.elysium.core.menu.AugmentingMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Augmenting Table's screen: what the sigil in the slot would do. */
public class AugmentingScreen extends TwoInputBenchScreen<AugmentingMenu> {

    public AugmentingScreen(AugmentingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected Component note() {
        String sigil = menu.sigil();
        return sigil == null ? Component.translatable("elysium.augmenting.no_sigil")
                : Component.translatable("elysium.sigil." + sigil + ".desc");
    }
}
