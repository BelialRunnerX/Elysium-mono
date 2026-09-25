package com.elysium.arsenal.event;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.forge.ForgeComponents;
import com.elysium.arsenal.rules.ArtifactRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerFlyableFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * What the three artefacts do.
 *
 * <h2>Why the three are so unalike</h2>
 *
 * Because three artefacts that all made a sword hit harder would be one
 * artefact, and identifying a lump would be a formality with a colour. One is
 * about armour, one about flying and one about digging, and they sit on
 * different gear — so a player who rolls the one they were not hoping for has
 * still found something they will use, which is the whole reason identifying
 * is a roll.
 */
@EventBusSubscriber(modid = ElysiumArsenal.MODID)
public final class ArtifactEffects {

    private ArtifactEffects() {
    }

    /**
     * Which artefact is in a stack, at what level, or null.
     *
     * <p>Checked against the roster rather than trusted: a component saying
     * the gear carries an artefact this version no longer has would otherwise
     * apply whatever the lookup happened to return.
     */
    public static ArtifactRules.Artifact inside(ItemStack stack) {
        String name = ForgeComponents.artifactIn(stack);
        return name == null ? null : ArtifactRules.artifact(name);
    }

    /** How much that artefact is doing on that stack. */
    public static float strengthOn(ItemStack stack, String wanted) {
        ArtifactRules.Artifact found = inside(stack);
        if (found == null || !found.name().equals(wanted)) {
            return 0.0F;
        }
        return found.at(ForgeComponents.artifactLevel(stack));
    }

    /**
     * Penetrating Strike: a share of armour that is not there.
     *
     * <p>Applied by raising the blow rather than by editing the armour value,
     * because armour is worked out from four pieces, enchantments, effects and
     * whatever else the pack has installed, and reaching into that would be
     * this module having an opinion about all of it.
     */
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        float share = strengthOn(attacker.getMainHandItem(), "penetrating_strike");
        if (share <= 0.0F) {
            return;
        }
        event.setAmount(event.getAmount() * (1.0F + share));
    }

    /**
     * Soaring Winds: a shove at the moment an elytra opens.
     *
     * <p>At the moment it opens and not while it is open: a push applied every
     * tick is a rocket with no fuel, and the arithmetic that says an artefact
     * is worth half a point has only ever been about one shove.
     */
    @SubscribeEvent
    public static void onFly(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack wings = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!wings.is(Items.ELYTRA) || !player.isFallFlying()) {
            return;
        }
        float boost = strengthOn(wings, "soaring_winds");
        if (boost <= 0.0F) {
            return;
        }
        if (player.getCooldowns().isOnCooldown(Items.ELYTRA)) {
            return;
        }
        Vec3 way = player.getLookAngle().normalize().scale(boost);
        player.setDeltaMovement(player.getDeltaMovement().add(way));
        player.hurtMarked = true;
        player.getCooldowns().addCooldown(Items.ELYTRA, GUST_COOLDOWN);
    }

    /** How long between two gusts, in ticks. */
    public static final int GUST_COOLDOWN = 200;

    /**
     * Tunnelling: the block under the one you broke.
     *
     * <p>Only when the tool would have taken that block anyway, and only one
     * block down. Without the first, a pickaxe takes dirt; without the second
     * this is a drill, and there is already a hammer in this module for taking
     * more than one block at a time.
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (strengthOn(tool, "tunneling") <= 0.0F) {
            return;
        }
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        BlockPos below = event.getPos().below();
        BlockState under = level.getBlockState(below);
        if (under.isAir() || under.getDestroySpeed(level, below) < 0.0F) {
            return;
        }
        if (!tool.isCorrectToolForDrops(under)) {
            return;
        }
        // Guarded, because breaking a block is what got us here: without this
        // the second block breaks a third, and so on to the bedrock.
        if (DIGGING.get()) {
            return;
        }
        DIGGING.set(true);
        try {
            if (player instanceof ServerPlayer server) {
                server.gameMode.destroyBlock(below);
            } else {
                level.destroyBlock(below, true, player);
            }
        } finally {
            DIGGING.set(false);
        }
    }

    /** Whether this thread is already inside a tunnelling break. */
    private static final ThreadLocal<Boolean> DIGGING =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ArtifactRules.Artifact found = inside(event.getItemStack());
        if (found == null) {
            return;
        }
        int level = ForgeComponents.artifactLevel(event.getItemStack());
        event.getToolTip().add(1, Component.translatable(
                        ElysiumArsenal.MODID + ".artifact.set",
                        Component.translatable(ElysiumArsenal.MODID + ".artifact."
                                + found.name()), level)
                .withStyle(ChatFormatting.AQUA));
    }
}
