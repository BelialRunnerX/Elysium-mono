package com.elysium.arsenal.item;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.forge.ForgeComponents;
import com.elysium.arsenal.forge.ForgeKinds;
import com.elysium.arsenal.rules.ArtifactRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * An artefact, set into a piece of gear.
 *
 * <p>Held in one hand with the gear in the other, the way the smith's hammers
 * work — one interaction shape for everything in this module that changes a
 * piece of gear you already own, so a player who has found one thing knows how
 * to use the other.
 *
 * <p>Applying the same artefact again deepens it, to the level its own rules
 * allow and no further. A cap that lived here rather than in the rules would
 * be a second opinion about how far an artefact goes, and the arithmetic that
 * checks no artefact outclasses another would be checking the other one.
 */
public class IdentifiedArtifactItem extends Item {

    private final ArtifactRules.Artifact artifact;

    public IdentifiedArtifactItem(ArtifactRules.Artifact artifact,
                                  Properties properties) {
        super(properties);
        this.artifact = artifact;
    }

    /** Which artefact this is. */
    public ArtifactRules.Artifact artifact() {
        return artifact;
    }

    /** Whether this artefact will go into that stack. */
    public boolean fits(ItemStack gear) {
        if ("elytra".equals(artifact.onGear())) {
            return gear.is(Items.ELYTRA);
        }
        return artifact.onGear().equals(ForgeKinds.of(gear));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(held);
        }
        ItemStack gear = player.getOffhandItem();
        if (!fits(gear)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        ElysiumArsenal.MODID + ".artifact.wrong_gear"), true);
            }
            return InteractionResultHolder.fail(held);
        }
        String already = ForgeComponents.artifactIn(gear);
        if (already != null && !already.equals(artifact.name())) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        ElysiumArsenal.MODID + ".artifact.already"), true);
            }
            return InteractionResultHolder.fail(held);
        }
        int level_ = ForgeComponents.artifactLevel(gear) + 1;
        if (level_ > artifact.levels()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        ElysiumArsenal.MODID + ".artifact.full", artifact.levels()),
                        true);
            }
            return InteractionResultHolder.fail(held);
        }
        if (!level.isClientSide) {
            ForgeComponents.setArtifact(gear, artifact.name(), level_);
            if (!player.hasInfiniteMaterials()) {
                held.shrink(1);
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 0.8F);
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(
                        ElysiumArsenal.MODID + ".artifact." + artifact.name())
                .withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable(ElysiumArsenal.MODID + ".artifact.fits",
                        Component.translatable(ElysiumArsenal.MODID + ".gear."
                                + artifact.onGear()), artifact.levels())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
