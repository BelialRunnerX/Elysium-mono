package com.elysium.lib.stats;

import com.elysium.lib.ElysiumLib;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla's attribute ceilings, lifted.
 *
 * <h2>Two ways, and why both</h2>
 *
 * 1. <b>Raise the ceiling.</b> Every vanilla attribute has a maximum —
 *    maximum health 1024, armour 30, toughness 20 — and the value is clamped
 *    to it. An access transformer (META-INF/accesstransformer.cfg) makes
 *    {@code RangedAttribute.maxValue} writable, and {@link #raise} writes it.
 *    Done by reflection so the code compiles against the untransformed jar
 *    the local toolchain uses; at runtime NeoForge has applied the transformer
 *    and the field is public and non-final.
 *
 * 2. <b>Read past it.</b> {@link #uncapped} recomputes an attribute from its
 *    base and modifiers without the clamp. Armour and toughness are read this
 *    way for the damage formula, so they are uncapped even if (1) failed.
 *
 * (1) is guarded: if the transformer is missing or the field has changed, it
 * logs one line and leaves vanilla's ceilings in place. A stat ceiling is not
 * worth a crash on startup — the lesson of the tooltip crash, applied early.
 */
public final class ElysiumAttributeLimits {

    private ElysiumAttributeLimits() {
    }

    /**
     * The new ceiling. Not infinity, because an infinite maximum health turns
     * the first multiplication into NaN; a thousand trillion is past anything
     * reachable and still finite in a float, which is where health is stored.
     */
    public static final double UNCAPPED_MAX = 1.0E15D;

    /**
     * The attributes Elysium's stats and gear feed. Deliberately a list and
     * not "every attribute with a maximum": scale, gravity and step height are
     * physics, and lifting them would let a stat build walk through walls.
     */
    public static List<Holder<Attribute>> lifted() {
        List<Holder<Attribute>> out = new ArrayList<>();
        out.add(Attributes.MAX_HEALTH);
        out.add(Attributes.MAX_ABSORPTION);
        out.add(Attributes.ARMOR);
        out.add(Attributes.ARMOR_TOUGHNESS);
        out.add(Attributes.ATTACK_DAMAGE);
        out.add(Attributes.ATTACK_SPEED);
        out.add(Attributes.ATTACK_KNOCKBACK);
        out.add(Attributes.MOVEMENT_SPEED);
        out.add(Attributes.LUCK);
        out.add(Attributes.BLOCK_BREAK_SPEED);
        out.add(Attributes.MINING_EFFICIENCY);
        return out;
    }

    /** Lift every ceiling in {@link #lifted}. Call once, at common setup. */
    public static int raise() {
        int raised = 0;
        try {
            Field max = RangedAttribute.class.getDeclaredField("maxValue");
            max.setAccessible(true);
            for (Holder<Attribute> holder : lifted()) {
                if (holder.value() instanceof RangedAttribute ranged
                        && ranged.getMaxValue() < UNCAPPED_MAX) {
                    max.setDouble(ranged, UNCAPPED_MAX);
                    raised++;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            ElysiumLib.LOGGER.warn("Elysium: could not lift vanilla attribute ceilings "
                    + "({}); they stay at vanilla's. Armour and toughness are read "
                    + "uncapped regardless.", failure.toString());
        }
        return raised;
    }

    /** An attribute's value from its base and modifiers, with no ceiling. See ElysiumArmourRules.attributeValue. */
    public static double uncapped(LivingEntity entity, Holder<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return 0.0D;
        }
        List<Double> add = new ArrayList<>();
        List<Double> base = new ArrayList<>();
        List<Double> total = new ArrayList<>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            switch (modifier.operation()) {
                case ADD_VALUE -> add.add(modifier.amount());
                case ADD_MULTIPLIED_BASE -> base.add(modifier.amount());
                case ADD_MULTIPLIED_TOTAL -> total.add(modifier.amount());
            }
        }
        return ElysiumArmourRules.attributeValue(instance.getBaseValue(),
                unbox(add), unbox(base), unbox(total));
    }

    private static double[] unbox(List<Double> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
