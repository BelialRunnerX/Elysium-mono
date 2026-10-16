package com.elysium.arcana.event;

import com.elysium.arcana.ElysiumArcana;
import com.elysium.arcana.registry.RelicItems;
import com.elysium.arcana.rules.RelicRules;
import com.elysium.lib.ability.ElysiumAbilityRules;
import com.elysium.lib.ability.act.ElysiumAbilityUse;
import com.elysium.lib.trinket.ElysiumTrinkets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * What a relic does while it is merely carried.
 *
 * <p>A relic's active ability runs when it is used ({@link RelicItems.RelicItem});
 * the rest of its list was written on the tooltip and nowhere else, so a
 * wanderer's charm never made anyone swift (the mechanics survey, 27 September
 * 2026). This keeps the passives up on whoever wears or holds a relic, fires the
 * periodic ones on their clock, and fires the on-hit, on-hurt, on-kill and
 * low-health ones from the events that mean those words, through the library's
 * own {@link ElysiumAbilityUse}, so a relic ability and any other ability are the
 * same thing done the same way.
 */
@EventBusSubscriber(modid = ElysiumArcana.MODID)
public final class RelicPassives {

    private RelicPassives() {
    }

    /** How often the passives are renewed, and how long each renewal lasts. */
    public static final int EVERY = 20;
    public static final int LASTS = 45;
    /** Below this share of health, a low-health ability fires. */
    public static final float LOW = 0.3F;

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % EVERY != 0) {
            return;
        }
        long now = player.level().getGameTime();
        boolean low = player.getHealth() <= player.getMaxHealth() * LOW;
        for (Carried one : carried(player)) {
            ElysiumAbilityRules.Ability ability = one.ability();
            switch (ability.kind()) {
                case ElysiumAbilityRules.PASSIVE -> ElysiumAbilityUse.sustain(ability, player, LASTS);
                case ElysiumAbilityRules.TRIGGERED -> {
                    if ("periodic".equals(ability.trigger()) && ability.cooldown() > 0
                            && now % ability.cooldown() < EVERY) {
                        ElysiumAbilityUse.perform(ability, player);
                    } else if ("on_low_health".equals(ability.trigger()) && low
                            && now % Math.max(EVERY, ability.cooldown()) < EVERY) {
                        ElysiumAbilityUse.perform(ability, player);
                    }
                }
                default -> {
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            fire(attacker, "on_hit");
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            fire(victim, "on_hurt");
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer && event.getEntity() != killer) {
            fire(killer, "on_kill");
        }
    }

    /**
     * Each player and trigger being fired right now, as "uuid trigger".
     *
     * <p>An on-hit ability that hurts is itself a hit: perform's own blow posts
     * LivingIncomingDamageEvent, which comes straight back into fire("on_hit")
     * before perform has returned. With the cooldown written after perform, the
     * first melee hit with an emberbrand belt, a stormcoil, a sunder seal or a
     * cleaving band went round until the server's stack ran out (the review of
     * 16 October 2026). The cooldown now goes on before the call, as the
     * arsenal's uniques do it, and this refuses a trigger fired from inside
     * itself, so an ability with no cooldown at all cannot go round either.
     * Events reach it on the server thread only.
     */
    private static final Set<String> FIRING = new HashSet<>();

    /** Fires every carried triggered ability with this trigger that is off its cooldown. */
    private static void fire(ServerPlayer player, String trigger) {
        String firing = player.getStringUUID() + " " + trigger;
        if (!FIRING.add(firing)) {
            return;
        }
        try {
            for (Carried one : carried(player)) {
                ElysiumAbilityRules.Ability ability = one.ability();
                Item item = one.stack().getItem();
                if (ElysiumAbilityRules.TRIGGERED.equals(ability.kind()) && trigger.equals(ability.trigger())
                        && !player.getCooldowns().isOnCooldown(item)) {
                    // Started before it fires, and taken back if nothing happened.
                    if (ability.cooldown() > 0) {
                        player.getCooldowns().addCooldown(item, ability.cooldown());
                    }
                    // Still the two-argument perform, so an on-hit lands on whatever
                    // the library picks, not on the entity this hit struck. That waits
                    // for Quill's ElysiumAbilityUse.perform(ability, user, struck) to
                    // land on main; until then who is struck is unchanged here.
                    if (!ElysiumAbilityUse.perform(ability, player) && ability.cooldown() > 0) {
                        player.getCooldowns().removeCooldown(item);
                    }
                }
            }
        } finally {
            FIRING.remove(firing);
        }
    }

    /** A relic being carried, and one of its abilities. */
    public record Carried(ItemStack stack, ElysiumAbilityRules.Ability ability) {
    }

    /** Every ability of every relic the player wears or holds, actives excepted. */
    public static List<Carried> carried(Player player) {
        List<ItemStack> stacks = new ArrayList<>(ElysiumTrinkets.wornStacks(player));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            stacks.add(player.getItemBySlot(slot));
        }
        List<Carried> found = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!(stack.getItem() instanceof RelicItems.RelicItem relicItem)) {
                continue;
            }
            RelicRules.Relic relic = relicItem.relic();
            for (String name : relic.abilities()) {
                for (ElysiumAbilityRules.Ability ability : ElysiumAbilityRules.ABILITIES) {
                    if (ability.id().equals(name) && !ElysiumAbilityRules.ACTIVE.equals(ability.kind())) {
                        found.add(new Carried(stack, ability));
                    }
                }
            }
        }
        return found;
    }
}
