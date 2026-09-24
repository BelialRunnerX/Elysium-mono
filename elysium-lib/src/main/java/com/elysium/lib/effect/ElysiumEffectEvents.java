package com.elysium.lib.effect;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.item.ElysiumLootCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * When the item effects happen.
 *
 * <h2>Why these are in the library</h2>
 *
 * These were the armory's, and every one of them opened with
 * {@code stack.getItem() instanceof ArmoryGear}. That is a test only modular
 * gear can pass, so an affix rolled onto a <em>vanilla iron sword</em> stated
 * its effects, registered them with the engine, showed them in the tooltip —
 * and did nothing, because no event ever asked that sword anything.
 *
 * <p>It is the same mistake {@code ElysiumGear} was written to end one layer
 * down, and it has the same fix: ask a question any item can answer. The gate
 * here is {@link ElysiumLootCategory} — is this gear at all — so a trait on a
 * modular hammer and an affix on a stone pickaxe reach the same handler.
 *
 * <p>The coded traits stay with the armory. Those are behaviours of particular
 * traits rather than kinds of effect, and they read things only that module
 * knows about.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumEffectEvents {

    private ElysiumEffectEvents() {
    }

    /** How often worn-gear effects are refreshed; their durations are longer than this. */
    public static final int WIELDER_INTERVAL = 10;

    /** Whether a stack is something effects can be attached to at all. */
    private static boolean carries(ItemStack stack) {
        return !stack.isEmpty() && ElysiumLootCategory.of(stack) != null;
    }

    /** Extra damage, and what a hit leaves behind. */
    @SubscribeEvent
    public static void onAttack(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()
                || !(event.getSource().getDirectEntity() instanceof LivingEntity attacker)) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (!carries(weapon)) {
            return;
        }
        // The attacker is who the weapon is on, which is what a set bonus needs
        // and what a stack cannot say for itself.
        com.elysium.lib.set.ElysiumGearSetWearers.with(attacker, () -> {
            float bonus = ElysiumEffectHandlers.extraDamage(weapon, target);
            if (bonus > 0.0F) {
                event.setAmount(event.getAmount() + bonus);
            }
            ElysiumEffectHandlers.onHit(weapon, target, attacker);
        });
    }

    /** What worn gear turns aside, before armour has its say. */
    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        com.elysium.lib.set.ElysiumGearSetWearers.set(entity);
        try {
            float amount = event.getAmount();
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (carries(stack)) {
                    amount = ElysiumEffectHandlers.negate(stack, event.getSource(), amount);
                }
            }
            // And whatever is in an accessory slot, which is worn in every
            // sense that matters here: a ring that turns damage aside is a
            // ring that turns damage aside whichever mod holds the slot.
            for (ItemStack stack : accessories(entity)) {
                if (carries(stack)) {
                    amount = ElysiumEffectHandlers.negate(stack, event.getSource(), amount);
                }
            }
            if (amount != event.getAmount()) {
                event.setAmount(amount);
            }
        } finally {
            com.elysium.lib.set.ElysiumGearSetWearers.clear();
        }
    }

    /** The worn and held effects, and the two that tick: mending and magnetism. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        long time = player.level().getGameTime();
        com.elysium.lib.set.ElysiumGearSetWearers.with(player, () -> {
            if (time % WIELDER_INTERVAL == 0) {
                ElysiumEffectHandlers.wielderEffects(player);
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = player.getItemBySlot(slot);
                if (!carries(stack)) {
                    continue;
                }
                ElysiumEffectHandlers.selfRepair(stack, player.getRandom(), time);
                ElysiumEffectHandlers.cancelEffects(player, stack);
                if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
                    ElysiumEffectHandlers.magnet(player, stack);
                }
            }
            for (ItemStack stack : accessories(player)) {
                if (!carries(stack)) {
                    continue;
                }
                ElysiumEffectHandlers.selfRepair(stack, player.getRandom(), time);
                ElysiumEffectHandlers.cancelEffects(player, stack);
            }
        });
    }

    /**
     * What this entity has in accessory slots, or nothing.
     *
     * Asked through the library's own seam, so nothing here knows which
     * accessory mod is installed or whether one is. A player with no accessory
     * mod gets an empty list and the loops above do exactly what they did
     * before this existed.
     */
    private static java.util.List<ItemStack> accessories(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return java.util.List.of();
        }
        return com.elysium.lib.trinket.ElysiumTrinkets.wornStacks(player);
    }

    /** Mining speed on the blocks an effect is written for. */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack held = event.getEntity().getMainHandItem();
        if (carries(held)) {
            com.elysium.lib.set.ElysiumGearSetWearers.with(event.getEntity(), () ->
                    event.setNewSpeed(ElysiumEffectHandlers.miningSpeed(held, event.getState(),
                            event.getNewSpeed())));
        }
    }

    /** The extra drops an effect shakes loose. */
    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!carries(held)) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>();
        event.getDrops().forEach(entity -> drops.add(entity.getItem()));
        com.elysium.lib.set.ElysiumGearSetWearers.set(player);
        List<ItemStack> extras;
        try {
            extras = ElysiumEffectHandlers.bonusDrops(held, drops, player.getRandom());
        } finally {
            com.elysium.lib.set.ElysiumGearSetWearers.clear();
        }
        for (ItemStack extra : extras) {
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(event.getLevel(),
                    event.getPos().getX() + 0.5D, event.getPos().getY() + 0.5D,
                    event.getPos().getZ() + 0.5D, extra));
        }
    }
}
