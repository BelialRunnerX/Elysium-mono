package com.elysium.lib.ability;

import com.elysium.lib.attribute.ElysiumAttributeValues;
import net.minecraft.world.entity.LivingEntity;

/**
 * The two numbers an active ability reads off its wielder. The library owns
 * them so that a dash on a katana and a dash on a relic are the same dash,
 * scaled by the same attribute, and a trinket that lengthens one lengthens
 * both.
 *
 * <p>{@code dash_distance} is blocks; {@code strike_radius} is blocks around
 * the wielder a radial strike reaches. Both are flat, both start at zero, and
 * the item that carries the ability supplies the base as a modifier on itself,
 * the way Valoria's scythes put three blocks of radius on their own mainhand
 * slot.
 */
public final class ElysiumAbilityValues {

    private ElysiumAbilityValues() {
    }

    public static double dashDistance(LivingEntity wielder) {
        return ElysiumAttributeValues.flat(wielder, "dash_distance");
    }

    public static double strikeRadius(LivingEntity wielder) {
        return ElysiumAttributeValues.flat(wielder, "strike_radius");
    }
}
