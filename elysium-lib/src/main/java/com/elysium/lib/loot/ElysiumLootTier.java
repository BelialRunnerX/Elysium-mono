package com.elysium.lib.loot;

import com.elysium.lib.tier.ElysiumTiers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Whose standing a drop is being measured against.
 *
 * <h2>The two shapes of loot</h2>
 *
 * A mob drop has somebody who killed it, and that somebody is who the loot is
 * for. A chest has nobody: it is generated when the chunk is, long before any
 * player is near it, and opened at some later date by whoever found it.
 *
 * <p>So the question is asked twice. If there is a player in the context, it
 * is theirs. If there is only a place, it belongs to whoever is standing
 * there — the highest tier among them, so helping someone never costs them
 * loot.
 */
public final class ElysiumLootTier {

    private ElysiumLootTier() {
    }

    /** How far from a chest a player counts as the one who found it. */
    public static final double RANGE = 32.0D;

    /** The world tier this drop should be built for. */
    public static int of(LootContext context) {
        Player player = player(context);
        if (player != null) {
            return ElysiumTiers.of(player);
        }
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null || context.getLevel() == null) {
            return 0;
        }
        List<Player> nearby = context.getLevel().getEntitiesOfClass(Player.class,
                new AABB(origin, origin).inflate(RANGE));
        return ElysiumTiers.of(nearby);
    }

    /** The finder's luck, which is the other half of what decides a rarity. */
    public static double luck(LootContext context) {
        return context.getLuck();
    }

    /** The player this drop is for, or null when it is nobody's in particular. */
    public static Player player(LootContext context) {
        for (var param : List.of(LootContextParams.LAST_DAMAGE_PLAYER,
                LootContextParams.ATTACKING_ENTITY, LootContextParams.THIS_ENTITY)) {
            Object value = context.getParamOrNull(param);
            if (value instanceof Player found) {
                return found;
            }
            if (value instanceof Entity entity && entity instanceof Player found) {
                return found;
            }
        }
        return null;
    }
}
