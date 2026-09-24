package com.elysium.lib.client;

import com.elysium.lib.ElysiumLib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Food, armour and experience, as bars in the mod's own palette.
 *
 * <h2>Why these two went the same way as health</h2>
 *
 * The health readout became a bar because hearts stop working above about forty
 * maximum health and this mod's characters carry several hundred. Food never
 * had that problem — it is twenty points and always will be — and it was
 * restyled anyway, because a screen with one bar and two rows of icons on it
 * does not read as a designed interface, it reads as a mod that changed one
 * thing. The three meters a player watches are now one visual system.
 *
 * Armour <em>does</em> have the problem. Vanilla's ARMOR attribute stops paying
 * at 30 and its ten icons stop at 20 points; Fortitude fills both and then
 * keeps going into toughness, which the icons cannot show at all. The bar is
 * split by the two ceilings the same way the health bar is split by health and
 * shield — see {@link ElysiumMeter#split} — so the back half of the stat has
 * somewhere to appear.
 *
 * <h2>The layout, and what it is measured against</h2>
 *
 * Vanilla's own. Health keeps the left half of the hotbar's width and food
 * takes the right, both on the row the hearts used to occupy; armour is a
 * slimmer bar above the health readout, which is where the armour icons were.
 * Nothing moved anywhere a player was not already looking.
 *
 * <h2>Why the attack indicator is not here</h2>
 *
 * Because it has no layer of its own — vanilla draws it inside the hotbar and
 * inside the crosshair, and cancelling either of those to reach it would take
 * the hotbar or the crosshair with it. It is restyled as a texture instead, by
 * elysium-lib/tools/gen_gui.py, which is also how the inventory is done.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID, value = Dist.CLIENT)
public final class ElysiumStatusBars {

    private ElysiumStatusBars() {
    }

    /**
     * Where the food bar starts, measured right from the centre of the screen.
     *
     * The mirror of the health bar's own inset, with a channel left down the
     * middle for the experience level to sit in. Fourteen rather than ten
     * because a three-digit level is eighteen pixels wide and a number that
     * touches the readout on either side of it reads as a rendering fault.
     */
    public static final int RIGHT_OF_CENTRE = 14;

    /** Armour's bar is slimmer than health's: it is read less often. */
    public static final int ARMOUR_HEIGHT = 3;

    /** How far above the health bar's own text row the armour bar sits. */
    public static final int ARMOUR_LIFT = 5;

    /** Vanilla stops counting armour points here, whatever is added. */
    public static final float ARMOUR_CEILING = 30.0F;

    /** ...and toughness here. */
    public static final float TOUGHNESS_CEILING = 20.0F;

    /** Food and saturation are both twenty points, and always will be. */
    public static final float FOOD_CEILING = 20.0F;

    /** Below this share of a full stomach, the number turns. */
    public static final float HUNGRY = 0.30F;

    /** Saturation, drawn as a reserve sitting on top of the food. */
    private static final int SATURATION_COLOUR = ElysiumPalette.SATURATION;

    /** The experience bar spans the hotbar, which is what it measures against. */
    public static final int EXPERIENCE_WIDTH = 182;

    /** Slim: it is a progress bar, not a resource. */
    public static final int EXPERIENCE_HEIGHT = 3;

    /** Its bottom, measured up from the bottom of the screen. */
    public static final int EXPERIENCE_INSET = 23;

    @SubscribeEvent
    public static void onRenderFood(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.FOOD_LEVEL.equals(event.getName())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gameMode == null) {
            return;
        }
        // The same question vanilla asks. A creative player has never had a
        // food readout and should not be given one by a restyle.
        if (!mc.gameMode.canHurtPlayer()) {
            return;
        }
        event.setCanceled(true);
        if (mc.options != null && mc.options.hideGui) {
            return;
        }
        renderFood(event.getGuiGraphics(), mc, mc.player);
    }

    /**
     * Vanilla draws the bar and the level as two layers, so both are cancelled
     * and both are redrawn from one method — they are one readout, and letting
     * the number survive on its own would leave vanilla's green digits floating
     * over an Elysium bar.
     */
    @SubscribeEvent
    public static void onRenderExperience(RenderGuiLayerEvent.Pre event) {
        boolean bar = VanillaGuiLayers.EXPERIENCE_BAR.equals(event.getName());
        boolean level = VanillaGuiLayers.EXPERIENCE_LEVEL.equals(event.getName());
        if (!bar && !level) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gameMode == null) {
            return;
        }
        // Vanilla's own question before it draws either. A creative player has
        // no experience bar and should not be given one by a restyle.
        if (!mc.gameMode.hasExperience()) {
            return;
        }
        event.setCanceled(true);
        if (mc.options != null && mc.options.hideGui) {
            return;
        }
        // Drawn once, on the bar's pass. The level's pass only cancels.
        if (bar) {
            renderExperience(event.getGuiGraphics(), mc, mc.player);
        }
    }

    @SubscribeEvent
    public static void onRenderArmour(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.ARMOR_LEVEL.equals(event.getName())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gameMode == null) {
            return;
        }
        if (!mc.gameMode.canHurtPlayer()) {
            return;
        }
        event.setCanceled(true);
        if (mc.options != null && mc.options.hideGui) {
            return;
        }
        renderArmour(event.getGuiGraphics(), mc, mc.player);
    }

    // ------------------------------------------------------------------

    private static void renderExperience(GuiGraphics g, Minecraft mc, LocalPlayer player) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int x = screenWidth / 2 - EXPERIENCE_WIDTH / 2;
        int barY = screenHeight - EXPERIENCE_INSET - EXPERIENCE_HEIGHT;

        ElysiumUI.bar(g, x, barY, EXPERIENCE_WIDTH, EXPERIENCE_HEIGHT,
                ElysiumMeter.fraction(player.experienceProgress, 1.0F),
                ElysiumPalette.ACCENT);

        // The level sits in the channel between the health and food readouts,
        // on their own text row, because that is the only horizontal space on
        // the screen that belongs to nothing else. Vanilla put it over the bar;
        // there is no room for a nine-pixel font over a three-pixel bar.
        int textY = screenHeight - ElysiumHealthBar.BOTTOM_INSET
                - ElysiumHealthBar.HEIGHT - ElysiumUI.lineHeight() - 1;
        ElysiumUI.centred(g, Component.literal(Integer.toString(player.experienceLevel)),
                screenWidth / 2, textY, ElysiumPalette.ACCENT_BRIGHT);
    }

    private static void renderFood(GuiGraphics g, Minecraft mc, LocalPlayer player) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int x = screenWidth / 2 + RIGHT_OF_CENTRE;
        int barY = screenHeight - ElysiumHealthBar.BOTTOM_INSET - ElysiumHealthBar.HEIGHT;
        int textY = barY - ElysiumUI.lineHeight() - 1;

        float food = Math.max(0.0F, player.getFoodData().getFoodLevel());
        float saturation = Math.max(0.0F, player.getFoodData().getSaturationLevel());
        float share = ElysiumMeter.fraction(food, FOOD_CEILING);

        ElysiumUI.bar(g, x, barY, ElysiumHealthBar.WIDTH, ElysiumHealthBar.HEIGHT,
                share, foodColour(player, share));

        // Saturation is a reserve that sits on top of food and is spent first,
        // so it is drawn as a line along the top of the bar rather than as a
        // bar of its own. A player who has never thought about saturation reads
        // it as "how full the full part is", which is exactly what it is.
        int reserve = Math.round(ElysiumHealthBar.WIDTH
                * ElysiumMeter.fraction(saturation, FOOD_CEILING));
        if (reserve > 0) {
            g.fill(x, barY, x + reserve, barY + 1, SATURATION_COLOUR);
        }

        // Left-aligned number, right-aligned ceiling: the mirror of the health
        // readout across the centre of the screen.
        ElysiumUI.value(g, Component.literal(Integer.toString(Math.round(food))),
                x, textY, share < HUNGRY ? ElysiumPalette.WARN : ElysiumPalette.TEXT);
        ElysiumUI.valueRight(g, Component.literal("/ " + Math.round(FOOD_CEILING)),
                x + ElysiumHealthBar.WIDTH, textY, ElysiumPalette.TEXT_FAINT);
    }

    private static void renderArmour(GuiGraphics g, Minecraft mc, LocalPlayer player) {
        float armour = Math.max(0.0F, player.getArmorValue());
        float toughness = Math.max(0.0F,
                (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        if (armour <= 0.0F && toughness <= 0.0F) {
            return;             // vanilla hides the row too, and for the same reason
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int x = screenWidth / 2 - ElysiumHealthBar.LEFT_OF_CENTRE;
        int healthBarY = screenHeight - ElysiumHealthBar.BOTTOM_INSET
                - ElysiumHealthBar.HEIGHT;
        int barY = healthBarY - ElysiumUI.lineHeight() - 1 - ARMOUR_LIFT - ARMOUR_HEIGHT;

        int armourWidth = ElysiumMeter.split(ElysiumHealthBar.WIDTH,
                ARMOUR_CEILING, toughness > 0.0F ? TOUGHNESS_CEILING : 0.0F);
        int toughWidth = ElysiumHealthBar.WIDTH - armourWidth;

        ElysiumUI.bar(g, x, barY, armourWidth, ARMOUR_HEIGHT,
                ElysiumMeter.fraction(armour, ARMOUR_CEILING), ElysiumPalette.TEXT_MUTED);
        if (toughWidth > 0) {
            g.fill(x + armourWidth, barY, x + armourWidth + 1, barY + ARMOUR_HEIGHT,
                    ElysiumPalette.SURFACE_SUNKEN);
            ElysiumUI.bar(g, x + armourWidth + 1, barY, toughWidth - 1, ARMOUR_HEIGHT,
                    ElysiumMeter.fraction(toughness, TOUGHNESS_CEILING),
                    ElysiumPalette.ACCENT_DIM);
        }
    }

    /**
     * The fill colour, carrying what vanilla's shaking and greying drumsticks
     * used to say.
     *
     * Hunger is the one status effect that changes what the bar <em>means</em>
     * — it is going down on its own — so it gets the warning colour whatever
     * the level. Losing that on the way to a nicer bar would be a downgrade
     * wearing a redesign.
     */
    private static int foodColour(LocalPlayer player, float share) {
        if (player.hasEffect(MobEffects.HUNGER)) {
            return ElysiumPalette.NEURAL;
        }
        if (share < HUNGRY) {
            return ElysiumPalette.WARN;
        }
        return ElysiumPalette.KINETIC;
    }
}
