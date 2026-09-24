package com.elysium.armory.client;

import com.elysium.armory.menu.ArmoryReforgingMenu;
import com.elysium.armory.property.ArmorySalvageRules;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.lib.client.ElysiumUI;
import com.elysium.lib.item.ElysiumRarities;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Reforging Table's screen: the piece and the price on the left, the three
 * futures across the top.
 *
 * <p>The price is written out rather than implied by a slot count, because the
 * player is choosing how much to spend and a number they have to count is a
 * number they will get wrong.
 */
public class ArmoryReforgingScreen extends AbstractContainerScreen<ArmoryReforgingMenu> {

    public ArmoryReforgingScreen(ArmoryReforgingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = ArmoryReforgingMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ElysiumUI.scrim(g, width, height);
        ElysiumUI.panel(g, leftPos, topPos, imageWidth, imageHeight);
        int x = leftPos + ElysiumPalette.GAP;
        ElysiumUI.accentRule(g, x, topPos + 18, 24, ElysiumPalette.ACCENT);
        ElysiumUI.ruleSoft(g, x + 24, topPos + 18, imageWidth - ElysiumPalette.GAP * 2 - 24);
        ElysiumUI.rule(g, x, topPos + ArmoryReforgingMenu.INVENTORY_Y - 14,
                imageWidth - ElysiumPalette.GAP * 2);

        slot(g, ArmoryReforgingMenu.GEAR_X, ArmoryReforgingMenu.GEAR_Y, mouseX, mouseY);
        slot(g, ArmoryReforgingMenu.ESSENCE_X, ArmoryReforgingMenu.ESSENCE_Y, mouseX, mouseY);
        for (int index = 0; index < ArmorySalvageRules.CHOICES; index++) {
            slot(g, ArmoryReforgingMenu.CHOICE_X + index * 22, ArmoryReforgingMenu.CHOICE_Y,
                    mouseX, mouseY);
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(g, 8 + column * 18, ArmoryReforgingMenu.INVENTORY_Y + row * 18, mouseX, mouseY);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(g, 8 + column * 18, ArmoryReforgingMenu.INVENTORY_Y + 58, mouseX, mouseY);
        }
    }

    private void slot(GuiGraphics g, int sx, int sy, int mouseX, int mouseY) {
        int x = leftPos + sx - 1;
        int y = topPos + sy - 1;
        ElysiumUI.slot(g, x, y, ElysiumUI.within(mouseX, mouseY, x, y,
                ElysiumPalette.SLOT_SIZE, ElysiumPalette.SLOT_SIZE));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        ElysiumUI.heading(g, ElysiumUI.truncate(title, imageWidth - 16),
                ElysiumPalette.GAP, ElysiumPalette.GAP);
        int y = ArmoryReforgingMenu.CHOICE_Y + 22;
        if (menu.target() <= 0) {
            ElysiumUI.faint(g, ElysiumUI.truncate(
                    Component.translatable("elysiumarmory.reforge.no_essence"),
                    imageWidth - ArmoryReforgingMenu.CHOICE_X - 8),
                    ArmoryReforgingMenu.CHOICE_X, y);
        } else {
            ElysiumUI.value(g, ElysiumUI.truncate(
                    Component.translatable("elysiumarmory.reforge.target",
                            ElysiumRarities.getTierComponent(menu.target())),
                    imageWidth - ArmoryReforgingMenu.CHOICE_X - 8),
                    ArmoryReforgingMenu.CHOICE_X, y, ElysiumPalette.TEXT);
            ElysiumUI.faint(g, Component.translatable("elysiumarmory.reforge.cost",
                            Component.literal(String.valueOf(menu.cost()))),
                    ArmoryReforgingMenu.CHOICE_X, y + ElysiumUI.lineHeight() + 1);
        }
        ElysiumUI.faint(g, Component.translatable("container.inventory"),
                ElysiumPalette.GAP, inventoryLabelY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
