package com.elysium.wilds.item;

import com.elysium.wilds.block.ValePortal;
import com.elysium.wilds.network.WildNetwork;
import com.elysium.wilds.registry.WildBlocks;
import com.elysium.wilds.registry.WildComponents;
import com.elysium.wilds.rules.PortalRules;
import com.elysium.wilds.rules.WildRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A compass that finds a kind of place rather than a direction.
 *
 * <h2>Why the cooldown is not a balance decision</h2>
 *
 * Finding the nearest instance of a biome is a synchronous scan on the server
 * thread whose cost grows with the square of how far it looks. A compass
 * without a bound on that is a compass that, held down, is a server that
 * stutters — and what gets reported is "the server lags", so nobody ever
 * connects it to the compass.
 *
 * <p>So the search here is a fixed size: it looks as far as it is ever allowed
 * to, and coarsely enough that doing so costs one stated number of samples.
 * Every search costs the same, for every biome, however rare. The cooldown
 * then turns that into a bound per player per second, which
 * {@link WildRules#tooExpensive()} checks and the harness sweeps.
 *
 * <p>The blurriness that buys this is real and is safe for a reason rather
 * than by luck: the grid is about a tenth of the width of a patch of biome, so
 * a scan cannot step over one.
 *
 * <h2>And the one place it finds by being built</h2>
 *
 * Used on a Vale Frame, the compass lights the ring the frame is part of, and
 * the ring is the way into the Vale. It is not used up doing it: it is the
 * wilds' way-finding tool, and a ring is already three crafts of frames.
 */
public class BiomeCompassItem extends Item {

    public BiomeCompassItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sent)) {
            return InteractionResultHolder.success(held);
        }
        List<String> places = new ArrayList<>();
        for (var biome : level.registryAccess().registryOrThrow(Registries.BIOME).keySet()) {
            places.add(biome.toString());
        }
        places.sort(String::compareTo);
        WildNetwork.offer(sent, places);
        player.getCooldowns().addCooldown(this, WildRules.COOLDOWN_TICKS);
        return InteractionResultHolder.success(held);
    }

    /**
     * Lights a ring of Vale Frames, and passes on anything else.
     *
     * <p>Passing is what keeps the compass a compass: a block that is not a
     * frame goes on to {@link #use}, which opens the search as it always did.
     * A frame that is not a finished ring says what a ring has to be, out
     * loud, because one frame short looks exactly like a ring that works.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(WildBlocks.VALE_FRAME.get())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            // Swing now; the server decides for real a moment later.
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        ValePortal.Frame frame = ValePortal.findFrame(level, pos);
        if (frame == null) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("elysiumwilds.gate.no_frame",
                        PortalRules.LEAST_ACROSS, PortalRules.LEAST_UP)
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        ValePortal.light(level, frame);
        level.playSound(null, frame.anchor(), SoundEvents.END_PORTAL_FRAME_FILL,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        WildComponents.Bearing bearing = stack.get(WildComponents.BEARING.get());
        if (bearing == null) {
            lines.add(Component.translatable("elysiumwilds.compass.idle"));
        } else {
            lines.add(Component.translatable("elysiumwilds.compass.holding",
                    bearing.biome(),
                    String.valueOf(bearing.where().pos().getX()),
                    String.valueOf(bearing.where().pos().getZ())));
        }
        lines.add(Component.translatable("elysiumwilds.compass.gate")
                .withStyle(ChatFormatting.GRAY));
    }
}
