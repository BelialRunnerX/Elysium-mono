package com.elysium.lib.nihil;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.attribute.ElysiumAttributeValues;
import com.elysium.lib.attribute.ElysiumDamageTypes;
import com.elysium.lib.hazard.ElysiumHazardField;
import com.elysium.lib.hazard.ElysiumProtection;
import com.elysium.lib.hazard.ElysiumResistance;
import com.elysium.lib.hazard.ElysiumWards;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where {@link ElysiumNullRules} meets the player: the accrual and decay
 * ticks, the field's damage past half, its voice past three quarters, the
 * collapse at the top, and the ward's shield with its leak.
 *
 * <p>Protection is the library's, gathered rather than recomputed: a sealed
 * set, a filed claim or a ward stops everything; otherwise the worn shares, the
 * room and the null_resistance attribute combine the way every other hazard's
 * answers do. The one thing this tick adds is that the result is a gauge
 * rather than a hit.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumNullHandler {

    private ElysiumNullHandler() {
    }

    /** Which band each player was last told about, so a threshold speaks once. */
    private static final Map<UUID, Integer> TOLD = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide() || !(player instanceof ServerPlayer server)) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            return;
        }
        BlockPos pos = player.blockPosition();
        int difficulty = level.getDifficulty().getId();
        float intensity = ElysiumHazardField.intensity(level, pos, ElysiumNull.NULL);
        if (intensity > 0.0F) {
            int every = ElysiumNullRules.resilienceTicks(ElysiumAttributeValues.flat(player, "null_resilience"));
            if (player.tickCount % every == 0 && !ElysiumWards.isWarded(player, ElysiumNull.NULL)) {
                float protection = protection(player, level, pos);
                float gained = ElysiumNullRules.accrual(protection, difficulty) * intensity;
                if (gained > 0.0F) {
                    ElysiumNull.add(player, gained);
                }
            }
        } else if (player.tickCount % (ElysiumNullRules.DECAY_SECONDS * 20) == 0
                && ElysiumNull.saturation(player) > 0.0F) {
            ElysiumNull.add(player, -ElysiumNullRules.decay(difficulty));
        }

        float ratio = ElysiumNull.ratio(player);
        tell(server, ratio);
        if (ratio < ElysiumNullRules.DAMAGING || player.tickCount % ElysiumNullRules.damageInterval(ratio) != 0) {
            return;
        }
        if (ElysiumNullRules.collapses(ratio)) {
            collapse(server);
            return;
        }
        player.hurt(ElysiumDamageTypes.source(level, ElysiumDamageTypes.NULL, null),
                ElysiumNullRules.damage(ratio, 1.0F));
        if (ElysiumNullRules.speaks(ratio) && player.getRandom().nextFloat() < 0.05F) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.8F);
        }
    }

    /**
     * Everything that stops the field, as one share: a seal, a claim or a ward
     * stop all of it; otherwise gear, room and the null_resistance attribute
     * combine, each removing a share of what the others let through.
     */
    private static float protection(Player player, Level level, BlockPos pos) {
        if (ElysiumProtection.isSealed(player, ElysiumNull.NULL)
                || ElysiumProtection.inShelteredClaim(level, pos)) {
            return 1.0F;
        }
        float[] worn = ElysiumProtection.allShares(player, ElysiumNull.NULL);
        float[] all = new float[worn.length + 1];
        System.arraycopy(worn, 0, all, 0, worn.length);
        all[worn.length] = (float) ElysiumAttributeValues.chance(player, "null_resistance");
        return ElysiumResistance.combine(all);
    }

    private static void tell(ServerPlayer player, float ratio) {
        int band = ratio >= ElysiumNullRules.SPEAKING ? 2 : ratio >= ElysiumNullRules.DAMAGING ? 1 : 0;
        Integer before = TOLD.put(player.getUUID(), band);
        if (before == null || before == band || band == 0) {
            return;
        }
        player.displayClientMessage(Component.translatable(
                band == 2 ? "elysium.null.speaks" : "elysium.null.rising"), true);
    }

    /** The top: the field reclaims the player, or the Nullspire failsafe extracts them with the gauge cleared. */
    private static void collapse(ServerPlayer player) {
        if (ElysiumNull.failsafe() == ElysiumNullRules.Failsafe.EXTRACT) {
            DimensionTransition transition = player.findRespawnPositionAndUseSpawnBlock(true,
                    DimensionTransition.DO_NOTHING);
            ServerLevel target = transition.newLevel();
            if (target != null) {
                Vec3 at = transition.pos();
                player.teleportTo(target, at.x, at.y, at.z, player.getYRot(), player.getXRot());
                ElysiumNull.set(player, 0.0F);
                player.displayClientMessage(Component.translatable("elysium.null.extracted"), true);
                return;
            }
        }
        player.hurt(ElysiumDamageTypes.source(player.level(), ElysiumDamageTypes.NULL_COLLAPSE, null),
                Float.MAX_VALUE);
    }

    /**
     * The ward's shield. A warded player loses a share of every blow that
     * armour would have seen, and the gauge moves by what came in less one and
     * a half times what was turned aside — so a low ward drains it and a high
     * one feeds it, exactly as Valoria's did from level three.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }
        MobEffectInstance ward = player.getEffect(ElysiumNull.WARD);
        if (ward == null) {
            return;
        }
        float incoming = event.getAmount();
        float reduced = incoming * (1.0F - ElysiumNullRules.wardBlock(ward.getAmplifier() + 1));
        event.setAmount(reduced);
        if (!player.isCreative() && !player.isSpectator()) {
            ElysiumNull.add(player, ElysiumNullRules.wardLeak(incoming, reduced));
        }
    }
}
