package com.elysium.enchanting.client;

import com.elysium.enchanting.menu.InfusionMenu;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.lib.client.ElysiumUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Infusion Basin's screen: what it would do, what it would cost, and — if
 * it will not — which of the four reasons it is.
 *
 * <h2>The refusal is the interface</h2>
 *
 * A bench that simply does nothing when pressed teaches a player nothing. The
 * four refusals are four different jobs: build a better room, build for
 * rarity, bring a different tool, or take something off the one you have.
 */
public class InfusionScreen extends AbstractContainerScreen<InfusionMenu> {

    private static final int BUTTON_X = 80;
    private static final int BUTTON_Y = 46;
    private static final int BUTTON_W = 86;
    private static final int BUTTON_H = 24;   // two lines of a wrapped reason

    public InfusionScreen(InfusionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = InfusionMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ElysiumUI.scrim(g, width, height);
        ElysiumUI.panel(g, leftPos, topPos, imageWidth, imageHeight);
        int x = leftPos + ElysiumPalette.GAP;
        ElysiumUI.accentRule(g, x, topPos + 18, 24, ElysiumPalette.ACCENT);
        ElysiumUI.ruleSoft(g, x + 24, topPos + 18, imageWidth - ElysiumPalette.GAP * 2 - 24);
        ElysiumUI.rule(g, x, topPos + InfusionMenu.INVENTORY_Y - 14,
                imageWidth - ElysiumPalette.GAP * 2);

        slot(g, InfusionMenu.GEAR_X, InfusionMenu.GEAR_Y, mouseX, mouseY);
        slot(g, InfusionMenu.BOOK_X, InfusionMenu.BOOK_Y, mouseX, mouseY);
        slot(g, InfusionMenu.LAPIS_X, InfusionMenu.LAPIS_Y, mouseX, mouseY);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(g, 8 + column * 18, InfusionMenu.INVENTORY_Y + row * 18, mouseX, mouseY);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(g, 8 + column * 18, InfusionMenu.INVENTORY_Y + 58, mouseX, mouseY);
        }

        boolean ready = menu.number(InfusionMenu.STATUS) == InfusionMenu.READY;
        if (ready && ElysiumUI.within(mouseX, mouseY, leftPos + BUTTON_X, topPos + BUTTON_Y,
                BUTTON_W, BUTTON_H)) {
            ElysiumUI.selection(g, leftPos + BUTTON_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H,
                    ElysiumPalette.ACCENT);
        } else {
            ElysiumUI.sunken(g, leftPos + BUTTON_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H);
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
        ElysiumUI.faint(g, Component.translatable("elysiumenchanting.infusion.ceiling",
                        Component.literal(String.valueOf(menu.number(InfusionMenu.HIGHEST)))),
                ElysiumPalette.GAP, 22);
        int status = menu.number(InfusionMenu.STATUS);
        if (status == InfusionMenu.READY) {
            ElysiumUI.value(g, Component.translatable("elysiumenchanting.infusion.price",
                            Component.literal(String.valueOf(menu.number(InfusionMenu.COST))),
                            Component.literal(String.valueOf(menu.number(InfusionMenu.LAPIS)))),
                    BUTTON_X + 4, BUTTON_Y + 6, ElysiumPalette.TEXT);
        } else {
            // Wrapped rather than cut: "A book, and some.." told the player
            // nothing (the client stage, 27 September 2026; POLISH.md P14).
            ElysiumUI.wrapped(g, Component.translatable(reason(status)),
                    BUTTON_X + 4, BUTTON_Y + 3, BUTTON_W - 8, ElysiumPalette.TEXT_FAINT,
                    BUTTON_Y + BUTTON_H - 3);
        }
        ElysiumUI.faint(g, Component.translatable("container.inventory"),
                ElysiumPalette.GAP, inventoryLabelY);
    }

    private static String reason(int status) {
        return switch (status) {
            case InfusionMenu.TOO_WEAK -> "elysiumenchanting.infusion.too_weak";
            case InfusionMenu.TOO_RARE -> "elysiumenchanting.infusion.too_rare";
            case InfusionMenu.WRONG_ITEM -> "elysiumenchanting.infusion.wrong_item";
            case InfusionMenu.CONFLICT -> "elysiumenchanting.infusion.conflict";
            default -> "elysiumenchanting.infusion.nothing";
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.number(InfusionMenu.STATUS) == InfusionMenu.READY
                && ElysiumUI.within(mouseX, mouseY, leftPos + BUTTON_X, topPos + BUTTON_Y,
                BUTTON_W, BUTTON_H)) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
