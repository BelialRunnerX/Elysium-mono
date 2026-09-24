package com.elysium.lib.client;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.network.CharacterSheet;
import com.elysium.lib.stats.ElysiumStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The health readout, as a bar rather than a row of hearts.
 *
 * <h2>Why vanilla's hearts had to go</h2>
 *
 * Not for style. A heart is two points of health, and vanilla draws ten of them
 * and then starts stacking rows behind each other — which works up to about
 * forty health and degrades from there into a number you count by squinting. A
 * character in this mod who has put their levels into Vitality is carrying two
 * hundred and seventy maximum health and a shield on top of it, and there is no
 * arrangement of hearts that reads that at a glance.
 *
 * A bar is a fraction, and a fraction does not care how large its denominator
 * is. The exact numbers are printed above it for anybody who wants them.
 *
 * <h2>The shield is part of the same bar</h2>
 *
 * Willpower's absorption is not "extra hearts tacked on the end" — it is the
 * other half of what stands between a player and death, and the two are read
 * together or not at all. So the bar is split once, by
 * {@link #healthWidth}: the left region is health against maximum health, the
 * right is the shield against its capacity.
 *
 * The split is fixed by the two <em>capacities</em> rather than by what is
 * currently in them, which is the property that makes the bar stable. Losing
 * health shortens the red fill inside a region that does not move; a bar scaled
 * to {@code current + absorption} would have the red length change when the
 * shield recharged, and a player would read that as having been hurt.
 *
 * <h2>Why it is a cancelled layer and not a registered one</h2>
 *
 * Registering another layer would draw this <em>beside</em> the hearts, not
 * instead of them. Cancelling vanilla's own layer and drawing in its place is
 * the only arrangement where there is exactly one health readout on the screen,
 * and it leaves every other vanilla layer — armour, food, air, the vehicle
 * health it is easy to break by accident — exactly as it was.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID, value = Dist.CLIENT)
public final class ElysiumHealthBar {

    private ElysiumHealthBar() {
    }

    /** Where vanilla's first row of hearts starts, relative to the centre. */
    public static final int LEFT_OF_CENTRE = 91;

    /**
     * How wide each of the two paired bars is.
     *
     * Vanilla's ten-heart row is 81, and this was 81 to match it. It is 77 now
     * because the experience level moved into the gap between the health and
     * food readouts — four pixels off each bar buys a 28-pixel channel down the
     * middle of the screen, which is enough for a three-digit level without it
     * touching either number.
     */
    public static final int WIDTH = 77;

    /** Height of the bar itself. */
    public static final int HEIGHT = 5;

    /**
     * The bottom of the bar, measured up from the bottom of the screen.
     *
     * Was 23, one pixel clear of the hotbar — which is also exactly where
     * vanilla draws the experience bar, so the two were on top of each other
     * the moment the experience bar was restyled rather than left alone. The
     * whole stack moved up seven pixels to give it the row it has always had.
     */
    public static final int BOTTOM_INSET = 30;

    /** Below this fraction the fill brightens, because vanilla's shake is gone. */
    public static final float LOW = 0.25F;

    /**
     * The brighter half of the low-health alternation.
     *
     * Local rather than in ElysiumPalette because that file is generated from
     * the art system's own table, and a colour added to it by hand is a colour
     * the next regeneration deletes.
     */
    private static final int HEALTH_LOW = ElysiumPalette.HEALTH_LOW;

    /**
     * How much of the bar belongs to health rather than to the shield.
     *
     * <p>{@link ElysiumMeter#split} does the arithmetic; this names it. The
     * rule it states — that the split is fixed by the two capacities and never
     * by what is currently in them — is what stops a recharging shield from
     * looking like a wound, and HudHarness checks it through this name.
     *
     * @param width          the whole bar
     * @param maxHealth      the player's maximum health, however large
     * @param shieldCapacity what the shield holds when full, or 0 for no shield
     */
    public static int healthWidth(int width, float maxHealth, float shieldCapacity) {
        return ElysiumMeter.split(width, maxHealth, shieldCapacity);
    }

    /** A fraction of a capacity, safe at any capacity including none. */
    public static float fraction(float value, float capacity) {
        return ElysiumMeter.fraction(value, capacity);
    }

    @SubscribeEvent
    public static void onRenderHealth(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.PLAYER_HEALTH.equals(event.getName())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gameMode == null) {
            return;
        }
        // The same question vanilla asks before drawing hearts at all. A
        // creative player has never had a health readout and should not be
        // given one by a mod that only meant to restyle it.
        if (!mc.gameMode.canHurtPlayer()) {
            return;
        }
        event.setCanceled(true);
        if (mc.options != null && mc.options.hideGui) {
            return;
        }
        render(event.getGuiGraphics(), mc, mc.player);
    }

    private static void render(GuiGraphics g, Minecraft mc, LocalPlayer player) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int x = screenWidth / 2 - LEFT_OF_CENTRE;
        int barY = screenHeight - BOTTOM_INSET - HEIGHT;
        int textY = barY - ElysiumUI.lineHeight() - 1;

        float health = Math.max(0.0F, player.getHealth());
        float maxHealth = Math.max(1.0F, player.getMaxHealth());
        float absorption = Math.max(0.0F, player.getAbsorptionAmount());
        float capacity = shieldCapacity(absorption);

        int healthWidth = healthWidth(WIDTH, maxHealth, capacity);
        int shieldWidth = WIDTH - healthWidth;

        float healthFraction = fraction(health, maxHealth);
        int colour = fillColour(player, healthFraction);

        ElysiumUI.bar(g, x, barY, healthWidth, HEIGHT, healthFraction, colour);
        if (shieldWidth > 0) {
            // One pixel of the divider is taken out of the shield region rather
            // than drawn over either fill, so neither bar is ever reading a
            // pixel short of what it holds.
            g.fill(x + healthWidth, barY, x + healthWidth + 1, barY + HEIGHT,
                    ElysiumPalette.SURFACE_SUNKEN);
            ElysiumUI.bar(g, x + healthWidth + 1, barY, shieldWidth - 1, HEIGHT,
                    fraction(absorption, capacity), ElysiumPalette.KINETIC);
        }

        // The numbers, because a bar answers "how close am I" and a player
        // choosing whether to drink a potion is asking "how many points".
        ElysiumUI.value(g, Component.literal(Integer.toString(Math.round(health))),
                x, textY, healthFraction < LOW ? ElysiumPalette.BAD : ElysiumPalette.TEXT);
        Component ceiling = absorption > 0.0F
                ? Component.literal("/ " + Math.round(maxHealth)
                        + "  +" + Math.round(absorption))
                : Component.literal("/ " + Math.round(maxHealth));
        ElysiumUI.valueRight(g, ceiling, x + WIDTH, textY, ElysiumPalette.TEXT_FAINT);
    }

    /**
     * What the shield's bar is measured against.
     *
     * Willpower's capacity when the character sheet has arrived, because that
     * is the number the shield is actually rebuilding toward and a bar that
     * fills as it approaches it is the point. Before the sheet arrives — the
     * first seconds after joining — the current absorption is used instead, so
     * the region reads full rather than reading a confident zero.
     */
    private static float shieldCapacity(float absorption) {
        if (ElysiumClientState.hasSheet()) {
            CharacterSheet.Parsed sheet = ElysiumClientState.sheet();
            float capacity = ElysiumStats.shieldFor(sheet.get(ElysiumStats.WILLPOWER));
            if (capacity > 0.0F) {
                return Math.max(capacity, absorption);
            }
        }
        return absorption;
    }

    /**
     * The fill colour, which is the only thing carrying what vanilla's tinted
     * and shaking hearts used to say.
     *
     * Poison, wither and freezing all change what a health bar means — the
     * first two because it is going down on its own, the third because it is
     * about to. Losing that on the way to a nicer bar would be a downgrade
     * wearing a redesign.
     */
    private static int fillColour(LocalPlayer player, float fraction) {
        if (player.hasEffect(MobEffects.WITHER)) {
            return ElysiumPalette.TEXT_FAINT;
        }
        if (player.hasEffect(MobEffects.POISON)) {
            return ElysiumPalette.NEURAL;
        }
        if (player.isFullyFrozen()) {
            return ElysiumPalette.DIMENSIONAL;
        }
        if (fraction < LOW) {
            // Alternates rather than flashes: a bar that blinks off is a bar a
            // player glances at during the off frame.
            return (player.tickCount / 5) % 2 == 0 ? ElysiumPalette.BAD : HEALTH_LOW;
        }
        return ElysiumPalette.BAD;
    }
}
