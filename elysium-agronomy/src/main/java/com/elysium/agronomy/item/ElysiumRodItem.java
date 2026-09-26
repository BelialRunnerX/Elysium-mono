package com.elysium.agronomy.item;

import com.elysium.agronomy.entity.ElysiumHook;
import com.elysium.agronomy.registry.AgronomyComponents;
import com.elysium.agronomy.registry.FishItems;
import com.elysium.agronomy.rules.FishRules;
import com.elysium.agronomy.rules.TackleRules;
import com.elysium.agronomy.rules.WaterRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/**
 * One rung of the rod ladder, and the tackle fitted to it.
 *
 * <p>A rod is the game's rod with two differences. It casts {@link
 * ElysiumHook}, which carries the rung, the fitted hook and bobber, and the
 * bait, so the catch is a draw from {@link TackleRules#table} with luck the
 * rung plus the hook. And it can be fitted: use it sneaking with a hook, a
 * bobber or a bait in the other hand and the part goes onto the rod, into
 * the data components {@link AgronomyComponents} declares, and the part it
 * replaces comes back to the player.
 */
public class ElysiumRodItem extends FishingRodItem {

    private final int rung;

    public ElysiumRodItem(int rung, Properties properties) {
        super(properties);
        this.rung = rung;
    }

    /** Which rung of {@link FishRules#RODS} this is. */
    public int rung() {
        return rung;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack rod = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && hand == InteractionHand.MAIN_HAND
                && player.fishing == null) {
            ItemStack part = player.getOffhandItem();
            if (isPart(AgronomyComponents.ourId(part))) {
                if (!level.isClientSide) {
                    fit(rod, part, player);
                }
                return InteractionResultHolder.sidedSuccess(rod, level.isClientSide);
            }
        }
        if (player.fishing != null) {
            if (!level.isClientSide) {
                int damage = player.fishing.retrieve(rod);
                rod.hurtAndBreak(damage, player, LivingEntity.getSlotForHand(hand));
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F,
                    0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
            player.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F,
                    0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
            if (level instanceof ServerLevel server) {
                int lure = (int) (EnchantmentHelper.getFishingTimeReduction(server, rod,
                        player) * 20.0F);
                server.addFreshEntity(new ElysiumHook(player, level, lure, rung,
                        AgronomyComponents.tackleOn(rod), AgronomyComponents.baitOn(rod)));
            }
            player.awardStat(Stats.ITEM_USED.get(this));
            player.gameEvent(GameEvent.ITEM_INTERACT_START);
        }
        return InteractionResultHolder.sidedSuccess(rod, level.isClientSide);
    }

    // ------------------------------------------------------------------
    // Fitting
    // ------------------------------------------------------------------

    /** The hook of that name, or null. */
    public static TackleRules.Hook hookNamed(String id) {
        for (TackleRules.Hook one : TackleRules.HOOKS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** The bobber of that name, or null. */
    public static TackleRules.Bobber bobberNamed(String id) {
        for (TackleRules.Bobber one : TackleRules.BOBBERS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** The family a bait of that name is for, or empty. */
    public static String baitFamily(String id) {
        for (FishRules.Family family : FishRules.FAMILIES) {
            if (id.equals(family.name() + "_bait")) {
                return family.name();
            }
        }
        return "";
    }

    /** Whether an item of ours of that name goes on a rod. */
    public static boolean isPart(String id) {
        return hookNamed(id) != null || bobberNamed(id) != null || !baitFamily(id).isEmpty();
    }

    /**
     * Put a part on a rod, giving back whatever it replaces. Returns whether
     * anything changed.
     */
    public static boolean fit(ItemStack rod, ItemStack part, Player player) {
        String id = AgronomyComponents.ourId(part);
        if (hookNamed(id) != null) {
            String old = rod.get(AgronomyComponents.HOOK.get());
            if (id.equals(old)) {
                return false;
            }
            rod.set(AgronomyComponents.HOOK.get(), id);
            part.shrink(1);
            giveBack(player, old);
            return true;
        }
        if (bobberNamed(id) != null) {
            String old = rod.get(AgronomyComponents.BOBBER.get());
            if (id.equals(old)) {
                return false;
            }
            rod.set(AgronomyComponents.BOBBER.get(), id);
            part.shrink(1);
            giveBack(player, old);
            return true;
        }
        String family = baitFamily(id);
        if (family.isEmpty()) {
            return false;
        }
        String had = rod.get(AgronomyComponents.BAIT.get());
        int count = AgronomyComponents.baitCount(rod);
        if (had != null && !had.equals(family) && count > 0) {
            player.getInventory().placeItemBackInInventory(
                    new ItemStack(FishItems.BAITS.get(had).get(), count));
            count = 0;
        }
        int taken = Math.min(part.getCount(), WaterRules.MOST_BAIT_ON_ROD - count);
        if (taken <= 0) {
            return false;
        }
        rod.set(AgronomyComponents.BAIT.get(), family);
        rod.set(AgronomyComponents.BAIT_COUNT.get(), count + taken);
        part.shrink(taken);
        return true;
    }

    private static void giveBack(Player player, String old) {
        if (old != null && FishItems.TACKLE.containsKey(old)) {
            player.getInventory().placeItemBackInInventory(
                    new ItemStack(FishItems.TACKLE.get(old).get()));
        }
    }

    // ------------------------------------------------------------------
    // What the tooltip says
    // ------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack rod, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(rod, context, lines, flag);
        lines.add(Component.translatable("elysiumagronomy.rod.rung",
                String.valueOf(rung + 1), String.valueOf(FishRules.RODS))
                .withStyle(ChatFormatting.GRAY));
        TackleRules.Tackle tackle = AgronomyComponents.tackleOn(rod);
        String bait = AgronomyComponents.baitOn(rod);
        if (!tackle.hook().id().isEmpty()) {
            lines.add(Component.translatable("elysiumagronomy.rod.hook",
                    Component.translatable("item.elysiumagronomy." + tackle.hook().id()))
                    .withStyle(ChatFormatting.BLUE));
        }
        if (!tackle.bobber().id().isEmpty()) {
            lines.add(Component.translatable("elysiumagronomy.rod.bobber",
                    Component.translatable("item.elysiumagronomy." + tackle.bobber().id()))
                    .withStyle(ChatFormatting.BLUE));
        }
        if (!FishRules.NOTHING.equals(bait)) {
            lines.add(Component.translatable("elysiumagronomy.rod.bait",
                    Component.translatable("item.elysiumagronomy." + bait + "_bait"),
                    String.valueOf(AgronomyComponents.baitCount(rod)))
                    .withStyle(ChatFormatting.BLUE));
        }
        if (tackle.bare() && FishRules.NOTHING.equals(bait)) {
            lines.add(Component.translatable("elysiumagronomy.rod.bare")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
