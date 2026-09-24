package com.elysium.armory.client;

import com.elysium.armory.menu.ArmorySmithingMenu;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.lib.client.ElysiumUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Gear Smithing Table's screen: the list of shapes on the left, paper in,
 * the plan out.
 *
 * <p>Drawn in the interface's own chrome ({@link ElysiumUI}), like the
 * workstation and character screens. The shape names come from the server with
 * the menu, so this screen needs no data of its own.
 */
public class ArmorySmithingScreen extends AbstractContainerScreen<ArmorySmithingMenu> {

    private static final int LIST_X = 8;
    private static final int LIST_Y = 22;
    private static final int LIST_W = 100;
    private static final int ROW = 11;
    private static final int ROWS = 5;

    private int scroll;

    public ArmorySmithingScreen(ArmorySmithingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = ArmorySmithingMenu.INVENTORY_Y - 11;
    }

    private static Component name(String partId) {
        String path = partId.substring(partId.indexOf(':') + 1);
        return Component.translatable("item.elysiumarmory." + path);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ElysiumUI.scrim(g, width, height);
        ElysiumUI.panel(g, leftPos, topPos, imageWidth, imageHeight);
        int x = leftPos + ElysiumPalette.GAP;
        ElysiumUI.accentRule(g, x, topPos + 18, 24, ElysiumPalette.ACCENT);
        ElysiumUI.ruleSoft(g, x + 24, topPos + 18, imageWidth - ElysiumPalette.GAP * 2 - 24);
        ElysiumUI.rule(g, x, topPos + ArmorySmithingMenu.INVENTORY_Y - 14, imageWidth - ElysiumPalette.GAP * 2);

        for (int i = 0; i < ROWS; i++) {
            int index = scroll + i;
            if (index >= menu.parts().size()) {
                break;
            }
            int y = topPos + LIST_Y + i * ROW;
            boolean hovered = ElysiumUI.within(mouseX, mouseY, leftPos + LIST_X, y, LIST_W, ROW);
            if (index == menu.selected()) {
                ElysiumUI.selection(g, leftPos + LIST_X, y, LIST_W, ROW, ElysiumPalette.ACCENT);
            } else if (hovered) {
                ElysiumUI.hover(g, leftPos + LIST_X, y, LIST_W, ROW);
            }
        }
        slot(g, ArmorySmithingMenu.INPUT_X, ArmorySmithingMenu.INPUT_Y, mouseX, mouseY);
        slot(g, ArmorySmithingMenu.OUTPUT_X, ArmorySmithingMenu.OUTPUT_Y, mouseX, mouseY);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, 8 + col * 18, ArmorySmithingMenu.INVENTORY_Y + row * 18, mouseX, mouseY);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, 8 + col * 18, ArmorySmithingMenu.INVENTORY_Y + 58, mouseX, mouseY);
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
        ElysiumUI.heading(g, ElysiumUI.truncate(title, imageWidth - 16), ElysiumPalette.GAP, ElysiumPalette.GAP);
        for (int i = 0; i < ROWS; i++) {
            int index = scroll + i;
            if (index >= menu.parts().size()) {
                break;
            }
            Component label = ElysiumUI.truncate(name(menu.parts().get(index)), LIST_W - 6);
            if (index == menu.selected()) {
                ElysiumUI.value(g, label, LIST_X + 3, LIST_Y + i * ROW + 2, ElysiumPalette.TEXT);
            } else {
                ElysiumUI.faint(g, label, LIST_X + 3, LIST_Y + i * ROW + 2);
            }
        }
        ElysiumUI.faint(g, Component.translatable("container.inventory"),
                ElysiumPalette.GAP, inventoryLabelY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < ROWS; i++) {
            int index = scroll + i;
            if (index >= menu.parts().size()) {
                break;
            }
            if (ElysiumUI.within(mouseX, mouseY, leftPos + LIST_X, topPos + LIST_Y + i * ROW, LIST_W, ROW)) {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, menu.parts().size() - ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
