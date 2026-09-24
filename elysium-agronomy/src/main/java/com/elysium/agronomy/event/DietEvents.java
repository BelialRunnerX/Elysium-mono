package com.elysium.agronomy.event;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.registry.DietAttachments;
import com.elysium.agronomy.rules.AgronomyRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * What eating something new is worth, applied where the player can feel it.
 *
 * <h2>Why the bonus is an attribute modifier and not a heal</h2>
 *
 * Extra hearts have to survive a relog, a dimension change and a death, and
 * they have to <em>go away</em> if the rule is ever retuned downwards. An
 * attribute modifier with a stable id does all three: it is replaced rather
 * than stacked, it is written into the player's own data, and changing the
 * number in the rules changes every player's total the next time they tick.
 *
 * <p>A one-off heal would do none of that, and the failure mode is a player
 * with ninety hearts who cannot say why.
 */
@EventBusSubscriber(modid = ElysiumAgronomy.MODID)
public final class DietEvents {

    private DietEvents() {
    }

    /** The one id this bonus ever uses, so it replaces itself rather than stacking. */
    public static final ResourceLocation VARIETY =
            ResourceLocation.fromNamespaceAndPath(ElysiumAgronomy.MODID, "varied_diet");

    /** How often the bonus is re-checked, in ticks. */
    public static final int EVERY = 40;

    @SubscribeEvent
    public static void onFinishEating(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        ItemStack stack = event.getItem();
        if (!stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
            return;
        }
        String food = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (DietAttachments.ate(player, food)) {
            apply(player);
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % EVERY != 0) {
            return;
        }
        apply(player);
    }

    /** On respawn the attributes are fresh, so the bonus has to be put back. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.getEntity().level().isClientSide) {
            apply(event.getEntity());
        }
    }

    /**
     * Set the bonus to whatever the rules say it is now.
     *
     * <p>Removed and re-added rather than added: a modifier added twice is a
     * player who ate the same apple twice and got paid for it, and that is the
     * single bug this mechanic is most likely to have.
     */
    public static void apply(Player player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        float wanted = AgronomyRules.extraHealth(DietAttachments.variety(player));
        AttributeModifier had = health.getModifier(VARIETY);
        if (had != null && had.amount() == wanted) {
            return;
        }
        health.removeModifier(VARIETY);
        if (wanted > 0.0F) {
            health.addPermanentModifier(new AttributeModifier(VARIETY, wanted,
                    AttributeModifier.Operation.ADD_VALUE));
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }
}
