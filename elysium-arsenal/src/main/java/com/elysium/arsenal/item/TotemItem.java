package com.elysium.arsenal.item;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.rules.TotemRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A totem you carry and spend.
 *
 * <p>Allowed to do much more than a placed one for exactly one reason: it is
 * gone afterwards. {@link TotemRules#LEAST_HELD} is the floor, and it is a
 * floor rather than a ceiling — a held totem worth less than a minute of a
 * placed one is a held totem nobody carries, because placing the other and
 * walking away is free.
 */
public class TotemItem extends Item {

    private final TotemRules.Totem totem;

    public TotemItem(TotemRules.Totem totem, Properties properties) {
        super(properties);
        this.totem = totem;
    }

    /** Which totem this is. */
    public TotemRules.Totem totem() {
        return totem;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide) {
            switch (totem.name()) {
                case "torrent_totem" -> dash(player);
                case "soul_totem" -> drain(level, player);
                default -> { }
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.2F);
        if (!player.hasInfiniteMaterials()) {
            held.shrink(1);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    /** How long before another totem of any sort may be spent. */
    public static final int COOLDOWN = 40;

    /** Throw the player the way they are looking. */
    private void dash(Player player) {
        Vec3 way = player.getLookAngle().normalize().scale(totem.power() * 0.5D);
        player.setDeltaMovement(player.getDeltaMovement().add(way.x,
                Math.max(0.25D, way.y), way.z));
        player.hurtMarked = true;
        player.resetFallDistance();
    }

    /**
     * Take from what is near and give it to the player.
     *
     * <p>Capped at what one target is worth however many there are: a totem
     * that healed for its power times the size of the crowd would be a totem
     * whose value is set by the room rather than by the totem, and the
     * arithmetic that says a held totem is worth about four has only ever
     * been about one target.
     */
    private void drain(Level level, Player player) {
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(totem.reach()),
                other -> other != player && other.isAlive() && other.attackable());
        float taken = 0.0F;
        for (LivingEntity living : near) {
            living.hurt(level.damageSources().magic(), totem.power());
            taken = totem.power();
            if (taken > 0.0F) {
                break;
            }
        }
        if (taken > 0.0F) {
            player.heal(taken);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(
                        ElysiumArsenal.MODID + ".totem." + totem.name())
                .withStyle(ChatFormatting.GRAY));
    }
}
