package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.medical.ElysiumInjury;
import com.elysium.lib.medical.ElysiumMedical;
import com.elysium.lib.medical.ElysiumMedicine;
import com.elysium.lib.medical.ElysiumWoundKind;
import com.elysium.lib.room.ElysiumRoom;
import com.elysium.lib.room.ElysiumRoomCache;
import com.elysium.lib.room.ElysiumRooms;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Taking wounds and mending them. The numbers are in {@link ElysiumMedicine}
 * and executed by a harness; this file is the wiring.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumMedicalHandler {

    private ElysiumMedicalHandler() {
    }

    /**
     * Which kind a blow of this source can leave, or null for none.
     *
     * Order matters and is most-specific first: a fire arrow is a projectile
     * and fire both, and what it leaves is a burn. Anything this does not
     * name — drowning, starvation, the void, a command — wounds nothing: a
     * wound is something that was done to you, not a way of dying.
     */
    @Nullable
    public static ElysiumWoundKind kindOf(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return ElysiumWoundKind.BURN;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return ElysiumWoundKind.CONCUSSION;
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return ElysiumWoundKind.FRACTURE;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.getEntity() != null) {
            return ElysiumWoundKind.LACERATION;
        }
        return null;
    }

    /** On what landed: after armour, after everything. See ElysiumMedicine. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)
                || player.level().isClientSide()
                || player.isCreative() || player.isSpectator()
                || !player.isAlive()) {
            return;
        }
        ElysiumWoundKind kind = kindOf(event.getSource());
        if (kind == null) {
            return;
        }
        float chance = ElysiumMedicine.chanceFor(kind, event.getNewDamage());
        if (chance <= 0.0F || player.getRandom().nextFloat() >= chance) {
            return;
        }
        ElysiumInjury before = ElysiumMedical.of(player);
        ElysiumInjury after = before.wounded(kind);
        ElysiumMedical.set(player, after);
        player.displayClientMessage(Component.translatable(
                        "elysium.wound.taken",
                        Component.translatable("elysium.wound." + kind.id()),
                        Component.translatable("elysium.wound." + kind.id() + ".effect"),
                        kind.healMinutes())
                .withStyle(ChatFormatting.RED), false);
    }

    /** Players who have already been told they are in a Med bay, so it is said once per visit. */
    private static final Set<UUID> IN_MED_BAY = new HashSet<>();

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()
                || player.tickCount % ElysiumMedicine.TICK_INTERVAL != 0) {
            return;
        }
        ElysiumInjury before = ElysiumMedical.of(player);
        if (before.isEmpty()) {
            IN_MED_BAY.remove(player.getUUID());
            return;
        }

        ElysiumRoom room = ElysiumRoomCache.at(player.level(), player.blockPosition());
        boolean medBay = room == ElysiumRooms.MED_BAY;
        int rate = ElysiumMedicine.rate(medBay, player.isSleeping());

        if (medBay && IN_MED_BAY.add(player.getUUID())) {
            player.displayClientMessage(Component.translatable(
                            "elysium.wound.med_bay", ElysiumMedicine.RATE_MED_BAY)
                    .withStyle(ChatFormatting.AQUA), true);
        } else if (!medBay) {
            IN_MED_BAY.remove(player.getUUID());
        }

        ElysiumInjury after = before.recovered(ElysiumMedicine.TICK_INTERVAL, rate);
        ElysiumMedical.set(player, after);
        for (ElysiumWoundKind healed : before.healedBy(after)) {
            player.displayClientMessage(Component.translatable(
                            "elysium.wound.healed",
                            Component.translatable("elysium.wound." + healed.id()))
                    .withStyle(ChatFormatting.GREEN), false);
        }
    }

    /** Transient modifiers are not saved; put them back the moment a player arrives. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ElysiumMedical.apply(event.getEntity(), ElysiumMedical.of(event.getEntity()));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        IN_MED_BAY.remove(event.getEntity().getUUID());
    }
}
