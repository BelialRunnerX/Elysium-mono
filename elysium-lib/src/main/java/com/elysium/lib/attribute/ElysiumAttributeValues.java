package com.elysium.lib.attribute;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * Reading Elysium's combat attributes off an entity, safely.
 *
 * <p>Any entity may lack any of these: they are added to players, and to
 * anything else only by whatever gave it the attribute. Every reader here
 * answers the spec's default for an entity that does not have the attribute,
 * so a caller never has to ask first, and a mob without the attribute behaves
 * exactly as it did before the mod.
 *
 * <p>{@link #chance} is the one to use for CHANCE attributes: they store odds,
 * and the number a rule wants is the share those odds stand for.
 */
public final class ElysiumAttributeValues {

    private ElysiumAttributeValues() {
    }

    /** The raw stored value: odds for a chance, the amount for anything else. */
    public static double raw(LivingEntity entity, String name) {
        ElysiumAttributeSpec spec = ElysiumAttributeSpec.named(name);
        if (spec == null) {
            throw new IllegalArgumentException("no Elysium attribute " + name);
        }
        if (entity == null) {
            return spec.storedDefault();
        }
        Holder<Attribute> holder = ElysiumAttributes.get(name);
        AttributeInstance instance = entity.getAttribute(holder);
        return instance == null ? spec.storedDefault() : instance.getValue();
    }

    /** A CHANCE attribute as the share it stands for, never reaching one. */
    public static double chance(LivingEntity entity, String name) {
        return ElysiumAttributeSpec.chance(raw(entity, name));
    }

    /** A FLAT attribute, never below zero. */
    public static double flat(LivingEntity entity, String name) {
        return Math.max(0.0D, raw(entity, name));
    }

    /** A MULTIPLIER attribute, never below zero; 1 means unchanged. */
    public static double multiplier(LivingEntity entity, String name) {
        return Math.max(0.0D, raw(entity, name));
    }

    /** A TOGGLE attribute: on above zero. */
    public static boolean on(LivingEntity entity, String name) {
        return raw(entity, name) > 0.0D;
    }

    /** Whether the entity carries the attribute at all (so a rule can skip work for most mobs). */
    public static boolean has(LivingEntity entity, String name) {
        return entity != null && entity.getAttribute(ElysiumAttributes.get(name)) != null;
    }
}
