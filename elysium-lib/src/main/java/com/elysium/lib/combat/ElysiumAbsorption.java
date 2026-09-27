package com.elysium.lib.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Absorption that arrives.
 *
 * <p>Since 1.20.5 the game clamps a living thing's absorption to its
 * {@code max_absorption} attribute, which is 0 on everything nobody gave one:
 * the potion raises it with a modifier, and nothing else does. Every shield,
 * bulwark and barrier in the pack set absorption straight and so set it to 0,
 * silently, until the mechanics survey of 27 September 2026 read a bulwark
 * drone's 0.0 back. This raises the ceiling first, then sets the amount, and
 * is the one way the pack grants absorption.
 */
public final class ElysiumAbsorption {

    private ElysiumAbsorption() {
    }

    /** Lets {@code who} hold at least {@code amount} absorption. */
    public static void allow(LivingEntity who, float amount) {
        AttributeInstance most = who.getAttribute(Attributes.MAX_ABSORPTION);
        if (most != null && most.getBaseValue() < amount) {
            most.setBaseValue(amount);
        }
    }

    /** Sets absorption to {@code amount}, raising the ceiling so it takes. */
    public static void set(LivingEntity who, float amount) {
        allow(who, amount);
        who.setAbsorptionAmount(amount);
    }
}
