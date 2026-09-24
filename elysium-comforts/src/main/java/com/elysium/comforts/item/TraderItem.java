package com.elysium.comforts.item;

import com.elysium.comforts.registry.ComfortComponents;
import com.elysium.comforts.rules.TraderRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A villager, in your pocket, with its whole ledger intact.
 *
 * <h2>The two things this has to get right</h2>
 *
 * A villager is not the mob standing there — it is a ledger. Profession,
 * level, experience, and every offer with its uses, its demand and the
 * discount a player has earned on it. Writing that down by hand, field by
 * field, is how one field goes missing and nobody notices for three weeks, so
 * this does not write it down by hand: it keeps the entity's own saved form,
 * whole, and hands it back untouched.
 *
 * <h2>And the clock, which is the exploit</h2>
 *
 * The restock timer travels <em>inside</em> that saved form, and nothing here
 * touches it. That is the entire anti-exploit, and it is worth saying out loud
 * because the natural way to write this is the broken way: a freshly placed
 * mob has no history, so its last-restock time is zero, so the game believes a
 * day has passed and restocks it on the spot. One villager, two hands,
 * infinite emeralds — and it never gets reported as an exploit, only as
 * "trading feels a bit easy in this pack".
 *
 * <p>{@link TraderRules} proves the shape of that over twelve million
 * generated ledgers; this class is where the shape meets the game.
 */
public class TraderItem extends Item {

    /** The keys inside a villager's own saved form that the clock lives in. */
    public static final String LAST_RESTOCK = "LastRestock";
    public static final String RESTOCKS_TODAY = "RestocksToday";

    public TraderItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Whether this one is carrying somebody. */
    public static boolean occupied(ItemStack stack) {
        CompoundTag held = stack.get(ComfortComponents.TRADER.get());
        return held != null && !held.isEmpty();
    }

    /**
     * Pick one up.
     *
     * <p>The whole saved form, including the clock. Nothing is read out and
     * written back; nothing is defaulted.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target,
                                                  InteractionHand hand) {
        if (player.level().isClientSide || occupied(stack)) {
            return InteractionResult.PASS;
        }
        if (!(target instanceof Villager villager)) {
            return InteractionResult.PASS;
        }
        CompoundTag written = new CompoundTag();
        villager.saveWithoutId(written);
        stack.set(ComfortComponents.TRADER.get(), written);
        villager.discard();
        return InteractionResult.SUCCESS;
    }

    /**
     * Put one down.
     *
     * <p>Loaded from exactly what was written, and <b>the clock is not
     * touched</b>. A villager that was owed no restock when it went into the
     * pocket is owed none coming out, however long it sat there in a chest and
     * however many times it has been picked up before.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        if (level.isClientSide || !occupied(stack)) {
            return InteractionResult.PASS;
        }
        CompoundTag written = stack.get(ComfortComponents.TRADER.get());
        if (written == null) {
            return InteractionResult.PASS;
        }
        BlockPos where = context.getClickedPos().relative(context.getClickedFace());
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            return InteractionResult.PASS;
        }
        villager.load(written);
        villager.setPos(where.getX() + 0.5D, where.getY(), where.getZ() + 0.5D);
        villager.setUUID(java.util.UUID.randomUUID());
        level.addFreshEntity(villager);
        stack.remove(ComfortComponents.TRADER.get());
        return InteractionResult.SUCCESS;
    }

    /**
     * What it says on the tin, including the one number a player wants.
     *
     * <p>How long until it restocks, which is the question somebody carrying a
     * librarian around is actually asking, and which {@link TraderRules} works
     * out from the clock that travelled with it.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        CompoundTag written = stack.get(ComfortComponents.TRADER.get());
        if (written == null || written.isEmpty()) {
            lines.add(Component.translatable("elysiumcomforts.trader.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        lines.add(Component.translatable("elysiumcomforts.trader.carrying",
                professionOf(written), String.valueOf(levelOf(written)))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("elysiumcomforts.trader.restocked",
                String.valueOf(written.getInt(RESTOCKS_TODAY)))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String professionOf(CompoundTag written) {
        CompoundTag data = written.getCompound("VillagerData");
        String full = data.getString("profession");
        int colon = full.indexOf(':');
        return colon < 0 ? full : full.substring(colon + 1);
    }

    private static int levelOf(CompoundTag written) {
        return Math.max(1, written.getCompound("VillagerData").getInt("level"));
    }
}
