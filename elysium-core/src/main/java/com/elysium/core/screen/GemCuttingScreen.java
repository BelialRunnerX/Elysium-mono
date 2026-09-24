package com.elysium.core.screen;

import com.elysium.core.menu.GemCuttingMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Gem Cutting Table's screen: the price, and nothing else to decide. */
public class GemCuttingScreen extends TwoInputBenchScreen<GemCuttingMenu> {

    public GemCuttingScreen(GemCuttingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected Component note() {
        int cost = menu.cost();
        return cost <= 0 ? Component.translatable("elysium.cutting.no_rune")
                : Component.translatable("elysium.cutting.cost",
                        Component.literal(String.valueOf(cost)));
    }
}
