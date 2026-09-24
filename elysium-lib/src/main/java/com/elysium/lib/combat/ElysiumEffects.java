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

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
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
