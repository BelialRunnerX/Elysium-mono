package com.elysium.lib.client;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.network.CharacterSheet;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
// NeoForge's, not Minecraft's. LayeredDraw and DeltaTracker are vanilla; the
// table of ids for vanilla's own layers is a NeoForge addition, and the stub
// tree had it under net.minecraft.client.gui — which compiled locally and
// failed on the first real build. See FIXES.md 18.
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The in-world readout: level, progress, and either meter that is saying
 * something.
 *
 * <h2>What it refuses to show</h2>
 *
 * A HUD is read peripherally and believed without checking, so this one only
 * draws what it can stand behind.
 *
 * It draws nothing at all until the server has sent a sheet — see
 * {@link ElysiumClientState}, where the numbers all live in unsynced
 * attachments and a local read would return a confident zero. A HUD that is
 * briefly absent is honest; a HUD that briefly reads "FAVOR 0" is not.
 *
 * It also hides a meter that is below {@link ElysiumStanding#NOTICE}, because
 * under that threshold the Empire is genuinely not paying attention either way
 * and a bar sitting at 4/100 is three pixels of nothing occupying a permanent
 * corner of the screen. The meters appear when they start to matter, which is
 * also the moment a player wants to know about them.
 *
 * <h2>Where it sits</h2>
 *
 * Bottom left, above the hotbar layer, hugging the same margin vanilla's own
 * elements use. Not centred (the hotbar owns that), not top left (F3 and every
 * other mod own that), and not over the crosshair.
 *
 * <h2>Why it is a layer and not a render event</h2>
 *
 * A registered layer is ordered against vanilla's own by name, so it draws
 * above the hotbar and below the chat and inventory without guessing at a
 * priority number, and it inherits vanilla's F1 and death-screen handling.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ElysiumHud {

    private ElysiumHud() {
    }

    public static final ResourceLocation LAYER =
            ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, "character");

    /** The margin from the screen edge. Matches vanilla's own hotbar inset. */
    private static final int EDGE = 4;

    /** How wide the readout is. Fixed, so the numbers do not shuffle sideways. */
    private static final int WIDTH = 90;

    /** The padding inside the plate, top and bottom. */
    private static final int INSET = 3;

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER, ElysiumHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.options == null || mc.options.hideGui) {
            return;
        }
        // Nothing to say yet. Drawing zeroes here is the failure this whole
        // sync path exists to avoid.
        if (!ElysiumClientState.hasSheet()) {
            return;
        }

        CharacterSheet.Parsed sheet = ElysiumClientState.sheet();
        if (!sheet.chosen()) {
            return;
        }

        boolean showFavor = sheet.favor() >= ElysiumStanding.NOTICE;
        boolean showSuspicion = sheet.suspicion() >= ElysiumStanding.NOTICE;

        int line = ElysiumUI.lineHeight();

        // Every block here — the level line and each meter — is a line of text,
        // a one-pixel gap, a two-pixel bar, and four pixels of air before the
        // next. So the plate is that advance times the number of blocks, less
        // the trailing air the last block does not need, plus the inset twice.
        //
        // Derived rather than written down, because the written-down version
        // was three pixels short and the bottom bar sat exactly on the plate's
        // edge — which reads as a plate that is cut off, not as a tight layout.
        int advance = line + 5;
        int blocks = 1 + (showFavor ? 1 : 0) + (showSuspicion ? 1 : 0);
        int height = INSET * 2 + advance * blocks - 4;

        int x = EDGE;
        int y = mc.getWindow().getGuiScaledHeight() - EDGE - height;

        ElysiumUI.plate(g, x, y, WIDTH, height);

        int tx = x + 5;
        int tw = WIDTH - 10;
        int ty = y + INSET;

        // Level, with the progress toward the next one under the text rather
        // than beside it — a 90-pixel plate has no room for a label, a number
        // and a bar on one line without all three being cramped.
        ElysiumUI.value(g, Component.translatable("elysium.screen.level", sheet.level()),
                tx, ty, ElysiumPalette.TEXT);
        ElysiumUI.valueRight(g, ElysiumUI.percent(sheet.levelProgress()),
                tx + tw, ty, ElysiumPalette.TEXT_FAINT);
        ty += line + 1;
        ElysiumUI.bar(g, tx, ty, tw, 2, sheet.levelProgress(), ElysiumPalette.ACCENT);
        ty += 4;

        if (showFavor) {
            ty = meter(g, Component.translatable("elysium.screen.favor"), sheet.favor(),
                    ElysiumPalette.FAVOR, tx, ty, tw);
        }
        if (showSuspicion) {
            meter(g, Component.translatable("elysium.screen.suspicion"), sheet.suspicion(),
                    ElysiumPalette.SUSPICION, tx, ty, tw);
        }
    }

    /** One meter: a short label, the number, and a two-pixel bar. */
    private static int meter(GuiGraphics g, Component name, int value, int colour,
                             int x, int y, int w) {
        ElysiumUI.faint(g, name, x, y);
        ElysiumUI.valueRight(g, Component.literal(Integer.toString(value)),
                x + w, y, colour);
        int line = ElysiumUI.lineHeight();
        ElysiumUI.bar(g, x, y + line + 1, w, 2, (float) value / ElysiumStanding.MAX, colour);
        return y + line + 5;
    }
}
