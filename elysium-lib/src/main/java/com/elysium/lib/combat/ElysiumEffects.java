package com.elysium.lib.combat;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.attribute.ElysiumAttributes;
import com.elysium.lib.attribute.ElysiumDamageTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Apothic Attributes' status effects (AA-9), rebuilt.
 *
 * <p>Two carry their work as attribute modifiers, which is all they ever were:
 * Bursting Vitality raises healing received, Ancient Knowledge raises
 * experience gained, and Flight grants NeoForge's creative flight. Three are
 * read by the handlers where they matter — Sundering by the damage pipeline,
 * Grievous Wounds by the healing one — because each of those is a share of a
 * number the handler already has. Bleeding and Flaming Detonation tick here.
 *
 * <p>Per the "no caps" ruling, Grievous Wounds no longer blocks all healing at
 * level three: it is a share of an uncapped level (ElysiumCombatRules.levelShare),
 * so it climbs forever and never quite reaches all of it.
 *
 * <p>Valoria's seven follow (docs/SPEC_VALORIA.md, "Valoria: effects"). Salve
 * ticks here. Stunned, Tipsy, Foresight and Overload are their modifiers.
 * Exhaustion and Renewal are read by the healing handler through
 * ElysiumCombatRules, where Exhaustion's floor of half is gone and Renewal's
 * ceiling with it. Valoria's Nihility Protection is the Null ward, registered
 * beside the Null field in {@code com.elysium.lib.nihil.ElysiumNull}.
 */
public final class ElysiumEffects {

    private ElysiumEffects() {
    }

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, ElysiumLib.MODID);

    /** Damage taken per level of Sundering, as Apotheosis had it. */
    public static final double SUNDERING_PER_LEVEL = 0.2D;

    /** Grievous Wounds: the level is the stat, and this is its b. Level one blocks about 40% of healing. */
    public static final double GRIEVOUS_POINTS_FOR_ALL = 6.0D;

    /** Bursting Vitality's healing bonus per level. */
    public static final double VITALITY_PER_LEVEL = 0.2D;

    /** Ancient Knowledge's experience multiplier per level (Apotheosis's config default). */
    public static final double KNOWLEDGE_PER_LEVEL = 4.0D;

    /** Bleeding ticks this often, for 1 + level damage. */
    public static final int BLEED_INTERVAL = 40;

    /** Flaming Detonation's divisor: burning ticks become damage at the end. */
    public static final double DETONATION_DIVISOR = 14.0D;

    /** Salve heals this much a tick a level while below full health (Valoria's Aloe Regen). */
    public static final float SALVE_PER_TICK = 0.025F;

    /** Stunned: flat speed removed, so a slow creature stops outright. */
    public static final double STUNNED_SPEED = -0.5D;

    /** Tipsy: a fifth more damage dealt, a tenth of armour gone, both as totals. */
    public static final double TIPSY_DAMAGE = 0.2D;
    public static final double TIPSY_ARMOUR = -0.10D;

    /** Foresight (Valoria's Sinister Prediction): four points of damage and a heart of health. */
    public static final double FORESIGHT_DAMAGE = 4.0D;
    public static final double FORESIGHT_HEALTH = 2.0D;

    /** Overload (Valoria's Soul Burst): a quarter more damage and a twentieth more speed, as totals. */
    public static final double OVERLOAD_DAMAGE = 0.25D;
    public static final double OVERLOAD_SPEED = 0.05D;

    public static final DeferredHolder<MobEffect, MobEffect> SUNDERING =
            EFFECTS.register("sundering", () -> new Simple(MobEffectCategory.HARMFUL, 0xB03030));

    public static final DeferredHolder<MobEffect, MobEffect> BLEEDING =
            EFFECTS.register("bleeding", Bleeding::new);

    public static final DeferredHolder<MobEffect, MobEffect> GRIEVOUS =
            EFFECTS.register("grievous", () -> new Simple(MobEffectCategory.HARMFUL, 0x7A1020));

    public static final DeferredHolder<MobEffect, MobEffect> VITALITY =
            EFFECTS.register("vitality", () -> new Simple(MobEffectCategory.BENEFICIAL, 0x50D090)
                    .addAttributeModifier(ElysiumAttributes.get("healing_received"),
                            id("vitality"), VITALITY_PER_LEVEL,
                            AttributeModifier.Operation.ADD_VALUE));

    public static final DeferredHolder<MobEffect, MobEffect> KNOWLEDGE =
            EFFECTS.register("knowledge", () -> new Simple(MobEffectCategory.BENEFICIAL, 0x60C0E0)
                    .addAttributeModifier(ElysiumAttributes.get("experience_gained"),
                            id("knowledge"), KNOWLEDGE_PER_LEVEL,
                            AttributeModifier.Operation.ADD_VALUE));

    public static final DeferredHolder<MobEffect, MobEffect> DETONATION =
            EFFECTS.register("detonation", Detonation::new);

    public static final DeferredHolder<MobEffect, MobEffect> FLYING =
            EFFECTS.register("flying", () -> new Simple(MobEffectCategory.BENEFICIAL, 0xC0C8F0)
                    .addAttributeModifier(NeoForgeMod.CREATIVE_FLIGHT, id("flying"), 1.0D,
                            AttributeModifier.Operation.ADD_VALUE));

    public static final DeferredHolder<MobEffect, MobEffect> SALVE =
            EFFECTS.register("salve", Salve::new);

    public static final DeferredHolder<MobEffect, MobEffect> STUNNED =
            EFFECTS.register("stunned", () -> new Simple(MobEffectCategory.HARMFUL, 0xD8C860)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, id("stunned"), STUNNED_SPEED,
                            AttributeModifier.Operation.ADD_VALUE));

    public static final DeferredHolder<MobEffect, MobEffect> TIPSY =
            EFFECTS.register("tipsy", () -> new Simple(MobEffectCategory.NEUTRAL, 0xB08040)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, id("tipsy_damage"), TIPSY_DAMAGE,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ARMOR, id("tipsy_armour"), TIPSY_ARMOUR,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    public static final DeferredHolder<MobEffect, MobEffect> EXHAUSTION =
            EFFECTS.register("exhaustion", () -> new Simple(MobEffectCategory.HARMFUL, 0x606060));

    public static final DeferredHolder<MobEffect, MobEffect> RENEWAL =
            EFFECTS.register("renewal", () -> new Simple(MobEffectCategory.BENEFICIAL, 0x70E0A0));

    public static final DeferredHolder<MobEffect, MobEffect> FORESIGHT =
            EFFECTS.register("foresight", () -> new Simple(MobEffectCategory.BENEFICIAL, 0x8040C0)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, id("foresight_damage"), FORESIGHT_DAMAGE,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.MAX_HEALTH, id("foresight_health"), FORESIGHT_HEALTH,
                            AttributeModifier.Operation.ADD_VALUE));

    public static final DeferredHolder<MobEffect, MobEffect> OVERLOAD =
            EFFECTS.register("overload", () -> new Simple(MobEffectCategory.BENEFICIAL, 0x40C0F0)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, id("overload_damage"), OVERLOAD_DAMAGE,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, id("overload_speed"), OVERLOAD_SPEED,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    /** Salve: a slow mend, every tick, only while there is something to mend. */
    private static final class Salve extends MobEffect {

        private Salve() {
            super(MobEffectCategory.BENEFICIAL, 0x60C060);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return true;
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            if (!entity.level().isClientSide() && entity.getHealth() < entity.getMaxHealth()) {
                entity.heal(SALVE_PER_TICK * (1 + amplifier));
            }
            return true;
        }
    }

    /** An effect whose whole work is its attribute modifiers, or is read elsewhere. */
    private static class Simple extends MobEffect {

        Simple(MobEffectCategory category, int colour) {
            super(category, colour);
        }
    }

    /** Bleeding: a wound that keeps opening, through armour. */
    private static final class Bleeding extends MobEffect {

        private Bleeding() {
            super(MobEffectCategory.HARMFUL, 0x8B1A1A);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % BLEED_INTERVAL == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            if (!entity.level().isClientSide()) {
                entity.hurt(ElysiumDamageTypes.source(entity.level(), ElysiumDamageTypes.BLEEDING, null),
                        1.0F + amplifier);
            }
            return true;
        }
    }

    /**
     * Flaming Detonation: the fire on a target is spent all at once when the
     * effect ends. A target that is not burning loses nothing, which is what
     * makes the effect a follow-up rather than damage of its own.
     */
    private static final class Detonation extends MobEffect {

        private Detonation() {
            super(MobEffectCategory.HARMFUL, 0xE07020);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration <= 1;
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            if (entity.level().isClientSide()) {
                return true;
            }
            int burning = Math.max(0, entity.getRemainingFireTicks());
            if (burning > 0) {
                entity.clearFire();
                float damage = (float) ((1 + amplifier) * burning / DETONATION_DIVISOR);
                entity.hurt(ElysiumDamageTypes.source(entity.level(), ElysiumDamageTypes.DETONATION, null), damage);
            }
            return true;
        }
    }

    /**
     * Where the ids these effects use come from, so a rename cannot leave a
     * stale modifier behind.
     *
     * <p>This method read {@code return id(name);} in a shipped jar: infinite
     * recursion, a StackOverflowError inside RegisterEvent, and the game gone
     * before the title screen. It compiled, every validator passed it, and the
     * whole merged jar was built and delivered. check_recursion.py exists
     * because of this line.
     */
    static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, name);
    }
}
