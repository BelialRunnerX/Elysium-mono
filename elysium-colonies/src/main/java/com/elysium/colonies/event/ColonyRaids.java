package com.elysium.colonies.event;

import com.elysium.colonies.ElysiumColonies;
import com.elysium.colonies.colony.Colonies;
import com.elysium.colonies.rules.ColonyRules;
import com.elysium.lib.elite.ElysiumElites;
import com.elysium.lib.tier.ElysiumTiers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.UUID;

/**
 * A town is worth raiding properly.
 *
 * <h2>What this says</h2>
 *
 * A colony's raiders are promoted at the tier of the town they are raiding —
 * which, because of {@link ColonyProgress}, already includes what the town
 * itself was worth. So a capital is attacked by things a capital deserves, and
 * a new player's first raid is MineColonies' raid exactly as its author wrote
 * it.
 *
 * <h2>Promoted, not scaled</h2>
 *
 * Only a rank and its augments. MineColonies already scales a raid's numbers
 * against the colony's own defences, and multiplying one difficulty curve by
 * another produces a fight neither mod's author would recognise. What this
 * adds is variety and the occasional thing worth remembering, on top of a
 * fight whose size is still that mod's decision.
 *
 * <h2>Why the first tick and not the moment it appears</h2>
 *
 * Because at the moment a raider appears it is not finished. MineColonies puts
 * it in the world, <em>then</em> tells it which colony it belongs to, and that
 * last step is what sets its attributes. A promotion applied before that would
 * be multiplied away by base values written afterwards, and the creature would
 * arrive at a fraction of a health bar it never had. One tick later everything
 * about it is decided.
 *
 * <p>It also means the colony is known by then, so the tier is the tier of the
 * town actually being raided rather than of whatever is nearest.
 */
@EventBusSubscriber(modid = ElysiumColonies.MODID)
public final class ColonyRaids {

    private ColonyRaids() {
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        Mob raider = Colonies.raiderOf(event.getEntity());
        if (raider == null || raider.level().isClientSide) {
            return;
        }
        if (ElysiumElites.decided(raider)) {
            // Already looked at. This is the common case by a very long way,
            // so it is the first thing asked after the type check.
            return;
        }
        Colonies.Settlement town = Colonies.around(raider);
        if (town == null) {
            return;
        }
        int tier = tierOf(raider.level().getServer(), town.owner());
        if (!ColonyRules.promotes(tier)) {
            // Marked decided anyway, so a raid on a new player's four huts is
            // not re-examined on every tick of every raider for its whole life.
            ElysiumElites.promote(raider, 0);
            return;
        }
        ElysiumElites.promote(raider, ColonyRules.raiderTier(tier));
    }

    /**
     * The world tier of the town's owner.
     *
     * An owner who is offline cannot be asked, and a raid can perfectly well
     * happen while they are: the town keeps running. The fallback is what the
     * colony itself is worth, which is the part of that player's tier the
     * colony contributed and the only part this module can work out without
     * them — so an absent mayor's town is raided at the strength the town
     * earned, rather than at nothing.
     */
    private static int tierOf(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) {
            return 0;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        return player == null ? 0 : ElysiumTiers.of(player);
    }
}
