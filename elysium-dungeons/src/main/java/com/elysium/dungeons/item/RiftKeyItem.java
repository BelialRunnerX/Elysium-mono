package com.elysium.dungeons.item;

import com.elysium.dungeons.block.RiftPortal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Strike a Rift Frame with this to open it.
 *
 * <h2>Why it reports failure out loud</h2>
 *
 * A frame that is one block short, or has a hole in it, looks exactly like a
 * frame that works. Silently doing nothing leaves a player striking a wall and
 * guessing, so a failed attempt says what a frame has to be. The message is
 * an action bar line rather than chat: it is a correction, not a conversation.
 */
public class RiftKeyItem extends Item {

    public RiftKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level.isClientSide()) {
            // Assume success on the client so the swing animation plays; the
            // server decides for real a moment later.
            return InteractionResult.SUCCESS;
        }

        RiftPortal.Frame frame = RiftPortal.findFrame(level, pos);
        if (frame == null) {
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("elysiumdungeons.message.no_frame",
                                        RiftPortal.MIN_INNER_WIDTH, RiftPortal.MIN_INNER_HEIGHT)
                                .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }

        RiftPortal.light(level, frame);
        level.playSound(null, frame.anchor(), SoundEvents.END_PORTAL_FRAME_FILL,
                SoundSource.BLOCKS, 1.0F, 1.0F);

        if (player != null && !player.getAbilities().instabuild) {
            context.getItemInHand().hurtAndBreak(1, player,
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("elysiumdungeons.tooltip.rift_key")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("elysiumdungeons.tooltip.rift_key_reroll")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
