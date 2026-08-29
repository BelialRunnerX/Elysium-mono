package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.stats.ElysiumStat;
import com.elysium.lib.stats.ElysiumStats;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Turns the three stats that vanilla already has a number for into real
 * attribute modifiers, and runs the two that need a heartbeat.
 *
 * <h2>Why transient, and why re-applied</h2>
 *
 * A character's totals move constantly — swap a helmet, spend a point, gain a
 * level — and a permanent modifier would have to be removed exactly as often
 * as it is added, which is how a player ends up with four hundred stacked
 * copies of the same armour bonus in their save file. Transient modifiers are
 * not saved at all, so re-deriving them from the stat block once a second is
 * both cheaper and impossible to get permanently wrong: the worst case is that
 * a value is stale for under a second.
 *
 * {@code addOrUpdateTransientModifier} replaces by id, so re-applying is a
 * no-op when nothing changed.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumStatHandler {

    private ElysiumStatHandler() {
    }

    /** Once a second is often enough for numbers a player changes by hand. */
    private static final int INTERVAL = 20;

    /** Health regeneration and shields tick on the same cadence. */
    private static final int REGEN_INTERVAL = 40;

    private static final ResourceLocation ARMOUR_ID =
            ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, "stat/fortitude");
    private static final ResourceLocation SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, "stat/agility");
    private static final ResourceLocation HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, "stat/vitality");

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        if (player.tickCount % INTERVAL == 0) {
            applyAttributes(player);
        }

        if (player.tickCount % REGEN_INTERVAL == 0) {
            regenerate(player);
            rebuildShield(player);
            ElysiumPassives.serverTick(player);
        }
    }

    // ------------------------------------------------------------------

    private static void applyAttributes(Player player) {
        set(player, Attributes.ARMOR, ARMOUR_ID,
                ElysiumStats.baseArmour(player), AttributeModifier.Operation.ADD_VALUE);

        set(player, Attributes.MOVEMENT_SPEED, SPEED_ID,
                ElysiumStats.speedBonus(player), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        set(player, Attributes.MAX_HEALTH, HEALTH_ID,
                ElysiumStats.bonusHealth(player), AttributeModifier.Operation.ADD_VALUE);
    }

    /**
     * Applies one modifier, or removes it when the stat has fallen to nothing.
     *
     * Removing at zero matters: a modifier of +0.0 is still a modifier, and a
     * player who unequips everything should see a clean attribute rather than
     * a list of zeroes in F3.
     */
    private static void set(Player player, Holder<Attribute> attribute, ResourceLocation id,
                            double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (amount <= 0.0D) {
            instance.removeModifier(id);
            return;
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }

    // ------------------------------------------------------------------

    /**
     * Vitality's regeneration.
     *
     * Deliberately not a Regeneration effect: vanilla regeneration costs
     * hunger and fights with saturation healing. This is a flat heal that does
     * neither, which is what "passive health regen" should mean.
     */
    private static void regenerate(Player player) {
        if (player.getHealth() >= player.getMaxHealth() || player.getHealth() <= 0.0F) {
            return;
        }
        float amount = ElysiumStats.regenPerTick(player);

        amount *= ElysiumPassives.regenScale(player);

        player.heal(amount);
    }

    /**
     * Willpower's shield.
     *
     * Only ever raises absorption and only up to its own cap, so it can never
     * quietly delete a bigger shield from a golden apple or a totem — the same
     * rule the Barrier rune follows.
     */
    private static void rebuildShield(Player player) {
        float cap = ElysiumStats.shieldCapacity(player) * ElysiumPassives.shieldScale(player);
        if (cap <= 0.0F) {
            return;
        }
        float current = player.getAbsorptionAmount();
        if (current < cap) {
            player.setAbsorptionAmount(Math.min(cap, current + 1.0F));
        }
    }


}
