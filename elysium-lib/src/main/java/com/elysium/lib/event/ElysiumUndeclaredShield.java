package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.character.ElysiumCharacter;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * A player who has not declared yet cannot be hurt.
 *
 * <p>The declaration screen is modal and refuses ESC: from the first tick of a
 * new world until Confirm, the player can neither move nor fight. Whatever
 * finds them in that minute has them helpless, and on 28 September 2026 a
 * beast did, at a night spawn, under the screen. So until a race and a calling
 * are recorded, incoming damage is cancelled at the source. The shield ends
 * the moment the choice is made; nothing else about the player is changed,
 * and creative-mode and command damage are not this event's concern.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumUndeclaredShield {

    private ElysiumUndeclaredShield() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!ElysiumCharacter.hasChosen(player)) {
            event.setCanceled(true);
        }
    }
}
