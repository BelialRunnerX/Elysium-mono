package com.elysium.arsenal.item;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.registry.DigItems;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A lump of something, until it is not.
 *
 * <p>Identifying is a roll and not a choice, and that is the only reason there
 * are three artefacts rather than one. Given the choice a player takes the one
 * that suits what they are doing, every time, and the other two exist in a
 * wiki. Given a roll, they end up with one they did not pick — which is the
 * only way the second-best one is ever used, and why the roster refuses an
 * artefact that is a worse version of another.
 */
public class ArtifactItem extends Item {

    public ArtifactItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(held, true);
        }
        ArtifactRules.Artifact found =
                ArtifactRules.identify(level.getRandom().nextInt());
        if (found == null) {
            return InteractionResultHolder.fail(held);
        }
        var made = DigItems.IDENTIFIED.get(found.name());
        if (made == null) {
            return InteractionResultHolder.fail(held);
        }
        held.shrink(1);
        ItemStack given = new ItemStack(made.get());
        if (!player.addItem(given)) {
            player.drop(given, false);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.0F);
        return InteractionResultHolder.sidedSuccess(held, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(ElysiumArsenal.MODID + ".artifact.unknown",
                        ArtifactRules.ARTIFACTS.size())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
