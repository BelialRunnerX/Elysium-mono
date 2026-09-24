package com.elysium.enchanting.client;

import com.elysium.enchanting.menu.ArcaneTableMenu;
import com.elysium.enchanting.rules.EnchantingRules;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.lib.client.ElysiumUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.List;

/**
 * The Arcane Table's screen: the four numbers on the left, the three offers on
 * the right, and what the table is prepared to tell you about each.
 *
 * <h2>The clues are the interface</h2>
 *
 * Vanilla shows a word in a language nobody reads and one enchantment name on
 * hover. This shows what it knows: one enchantment on the cheap offer, two on
 * the middle, all of them on the expensive one. That is the decision the table
 * exists to pose — pay less and find out, or pay more and know.
 */
public class ArcaneTableScreen extends AbstractContainerScreen<ArcaneTableMenu> {

    private static final int OFFER_X = 78;
    private static final int OFFER_Y = 22;
    private static final int OFFER_H = 20;
    private static final int OFFER_W = 90;

    public ArcaneTableScreen(ArcaneTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = ArcaneTableMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ElysiumUI.scrim(g, width, height);
        ElysiumUI.panel(g, leftPos, topPos, imageWidth, imageHeight);
        int x = leftPos + ElysiumPalette.GAP;
        ElysiumUI.accentRule(g, x, topPos + 18, 24, ElysiumPalette.ACCENT);
        ElysiumUI.ruleSoft(g, x + 24, topPos + 18, imageWidth - ElysiumPalette.GAP * 2 - 24);
        ElysiumUI.rule(g, x, topPos + ArcaneTableMenu.INVENTORY_Y - 14,
                imageWidth - ElysiumPalette.GAP * 2);

        slot(g, ArcaneTableMenu.ITEM_X, ArcaneTableMenu.ITEM_Y, mouseX, mouseY);
        slot(g, ArcaneTableMenu.LAPIS_X, ArcaneTableMenu.LAPIS_Y, mouseX, mouseY);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(g, 8 + column * 18, ArcaneTableMenu.INVENTORY_Y + row * 18, mouseX, mouseY);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(g, 8 + column * 18, ArcaneTableMenu.INVENTORY_Y + 58, mouseX, mouseY);
        }

        for (int slot = 0; slot < EnchantingRules.OFFERS; slot++) {
            int y = topPos + OFFER_Y + slot * OFFER_H;
            boolean live = menu.offeredLevel(slot) > 0;
            boolean hovered = ElysiumUI.within(mouseX, mouseY, leftPos + OFFER_X, y,
                    OFFER_W, OFFER_H - 2);
            if (live && hovered) {
                ElysiumUI.selection(g, leftPos + OFFER_X, y, OFFER_W, OFFER_H - 2,
                        ElysiumPalette.ACCENT);
            } else {
                ElysiumUI.sunken(g, leftPos + OFFER_X, y, OFFER_W, OFFER_H - 2);
            }
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
        // The four numbers, which are what a player built the room for.
        String[] keys = {"eterna", "quanta", "arcana", "rectification"};
        for (int index = 0; index < keys.length; index++) {
            ElysiumUI.faint(g, Component.translatable("elysiumenchanting.stat." + keys[index],
                            Component.literal(String.format("%.1f", menu.stat(index)))),
                    ElysiumPalette.GAP, 22 + index * (ElysiumUI.lineHeight() + 1));
        }
        for (int slot = 0; slot < EnchantingRules.OFFERS; slot++) {
            int level = menu.offeredLevel(slot);
            int y = OFFER_Y + slot * OFFER_H + 2;
            if (level <= 0) {
                continue;
            }
            ElysiumUI.value(g, Component.translatable("elysiumenchanting.offer.level",
                            Component.literal(String.valueOf(level)),
                            Component.literal(String.valueOf(EnchantingRules.cost(slot)))),
                    OFFER_X + 4, y, ElysiumPalette.TEXT);
            List<EnchantmentInstance> clues = menu.clues(slot);
            if (clues.isEmpty()) {
                continue;
            }
            Component first = clues.get(0).enchantment.value().description();
            Component line = clues.size() > 1
                    ? Component.translatable("elysiumenchanting.offer.and", first,
                            Component.literal(String.valueOf(clues.size() - 1)))
                    : first;
            ElysiumUI.faint(g, ElysiumUI.truncate(line.copy().withStyle(ChatFormatting.GRAY),
                    OFFER_W - 8), OFFER_X + 4, y + ElysiumUI.lineHeight());
        }
        ElysiumUI.faint(g, Component.translatable("container.inventory"),
                ElysiumPalette.GAP, inventoryLabelY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int slot = 0; slot < EnchantingRules.OFFERS; slot++) {
            int y = topPos + OFFER_Y + slot * OFFER_H;
            if (menu.offeredLevel(slot) > 0
                    && ElysiumUI.within(mouseX, mouseY, leftPos + OFFER_X, y, OFFER_W, OFFER_H - 2)) {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, slot);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
