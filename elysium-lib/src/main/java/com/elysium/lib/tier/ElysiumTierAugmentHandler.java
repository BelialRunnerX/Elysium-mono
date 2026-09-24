package com.elysium.lib.tier;

import com.elysium.lib.ElysiumLib;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Applying what a world tier is worth.
 *
 * <p>The player's augments are attribute modifiers with fixed ids, re-asserted
 * rather than added: a modifier with an id that is already present replaces
 * the old value, so a player whose tier just rose gets the new number and not
 * a second copy of the old one. That is the same trick the rune affixes use,
 * and for the same reason.
 *
 * <p>Re-asserted on a slow tick rather than only when the tier changes,
 * because a tier can change for reasons this class never hears about — a level
 * gained, a datapack that declares a world the player is already standing in.
 * Once every five seconds is far cheaper than the check is worth arguing over.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumTierAugmentHandler {

    private ElysiumTierAugmentHandler() {
    }

    /** How often the augments are re-asserted, in ticks. */
    public static final int INTERVAL = 100;

    private static final ResourceLocation HEALTH =
            ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, "tier_augment/health");
    private static final ResourceLocation LUCK =
            ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, "tier_augment/luck");

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % INTERVAL != 0) {
            return;
        }
        apply(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            apply(player);
        }
    }

    /** The augments this player's tier has earned them. */
    public static void apply(Player player) {
        int tier = ElysiumTiers.of(player);
        assertModifier(player, Attributes.MAX_HEALTH, HEALTH,
                ElysiumTierAugments.augments(tier) ? ElysiumTierAugments.health(tier) : 0.0D);
        assertModifier(player, Attributes.LUCK, LUCK,
                ElysiumTierAugments.augments(tier) ? ElysiumTierAugments.luck(tier) : 0.0D);
    }

    /**
     * How much extra experience this player's tier is worth, as a multiplier.
     * Read where experience is granted rather than applied as a modifier,
     * because experience is not an attribute.
     */
    public static double experienceMultiplier(Player player) {
        return 1.0D + ElysiumTierAugments.experience(ElysiumTiers.of(player));
    }

    private static void assertModifier(LivingEntity entity, Holder<Attribute> attribute,
                                       ResourceLocation id, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier present = instance.getModifier(id);
        if (amount == 0.0D) {
            if (present != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (present != null && present.amount() == amount) {
            return;                        // already exactly right; nothing to do
        }
        instance.removeModifier(id);
        instance.addTransientModifier(
                new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }
}
