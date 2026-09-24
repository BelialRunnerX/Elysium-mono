package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two small things the game has never done (Apotheosis' AP-31).
 *
 * <h2>Where you died</h2>
 *
 * The one piece of information every player wants at exactly the moment the
 * game stops showing it to them. Printed to chat, with the dimension, because
 * a coordinate without a dimension has sent people to the wrong world.
 *
 * <h2>Before it breaks</h2>
 *
 * A tool breaking is the loss players actually mind, and vanilla's warning is
 * a bar that has been getting shorter for an hour in a corner nobody looks at.
 * This says so once, in the action bar, with a sound — and then leaves the
 * player alone: a warning that repeats is a warning that gets muted.
 *
 * <h2>Why neither is configurable</h2>
 *
 * Both are things a player either wants or does not notice. A config option
 * for "tell me where I died" would be a line in a file nobody reads to turn
 * off a message nobody minds.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumQualityOfLife {

    private ElysiumQualityOfLife() {
    }

    /** How worn a piece has to be before it is worth saying so. */
    public static final float WARN_BELOW = 0.10F;

    /** Ticks between one look at a player's gear and the next. */
    public static final int INTERVAL = 20;

    /** And how long before the same piece may be mentioned again. */
    public static final int QUIET_FOR = 20 * 60;

    /** When each player was last told about each kind of item. */
    private static final Map<UUID, Map<String, Integer>> TOLD = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos where = player.blockPosition();
        player.sendSystemMessage(Component.translatable("elysium.qol.died",
                        where.getX(), where.getY(), where.getZ(),
                        Component.translatable(
                                player.level().dimension().location().toLanguageKey("dimension")))
                .withStyle(ChatFormatting.GRAY));
        TOLD.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % INTERVAL != 0) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!worn(stack)) {
                continue;
            }
            if (!quiet(player, stack)) {
                continue;
            }
            player.displayClientMessage(Component.translatable("elysium.qol.wearing_out",
                    stack.getHoverName()).withStyle(ChatFormatting.RED), true);
            player.playNotifySound(SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
    }

    /** Whether a piece is close enough to breaking to be worth a word. */
    private static boolean worn(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return false;
        }
        int left = stack.getMaxDamage() - stack.getDamageValue();
        return left <= Math.max(1, Math.round(stack.getMaxDamage() * WARN_BELOW));
    }

    /**
     * Whether this player has been left alone about this kind of item for long
     * enough.
     *
     * Keyed on the item rather than the stack because a stack has no identity:
     * a player who repairs a pickaxe and wears it down again is told again,
     * and one who is simply still holding the same nearly-broken pickaxe is
     * not told every second.
     */
    private static boolean quiet(ServerPlayer player, ItemStack stack) {
        Map<String, Integer> told = TOLD.computeIfAbsent(player.getUUID(),
                id -> new ConcurrentHashMap<>());
        String key = stack.getItem().getDescriptionId();
        Integer last = told.get(key);
        if (last != null && player.tickCount - last < QUIET_FOR) {
            return false;
        }
        told.put(key, player.tickCount);
        return true;
    }
}
