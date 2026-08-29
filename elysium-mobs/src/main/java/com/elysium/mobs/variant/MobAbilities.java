package com.elysium.mobs.variant;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The abilities the thirty sub-variants are built from.
 *
 * <h2>Why these are parameterised rather than thirty classes</h2>
 *
 * Thirty bespoke abilities would be thirty places to get the same arithmetic
 * subtly wrong. Most of what makes a variant distinct is one idea with a
 * number attached — how much it shields, how angry it gets, how far its aura
 * reaches — so the idea is written once and the number varies.
 *
 * Where a variant genuinely needs its own idea, it gets its own class. The line
 * is whether two variants would share the code or merely resemble each other.
 *
 * <h2>Everything here scales with the mob's level</h2>
 *
 * A flat "+4 absorption" is meaningful at level 5 and invisible at level 60,
 * which turns a variant into a variant only for the first hour of a save. Each
 * ability below takes the mob level it was handed and scales against it, on the
 * same square-root curve the library uses for health — so an ability keeps the
 * same *relative* weight at every level rather than fading out.
 */
public final class MobAbilities {

    private MobAbilities() {
    }

    /** The shared curve: an ability's strength relative to level 1. */
    private static float curve(int mobLevel) {
        return 1.0F + 0.30F * (float) Math.sqrt(Math.max(0, mobLevel - 1));
    }

    // ==================================================================
    // Defensive
    // ==================================================================

    /**
     * Bulwark: carries absorption that rebuilds while it is not being spent.
     *
     * A shield rather than more health, because the two feel different in a
     * fight: health is a number going down, a shield is an obstacle you have to
     * get through first, and it comes back if you stop.
     */
    public static MobAbility bulwark(float perLevel) {
        return new MobAbility() {
            @Override
            public void onServerTick(Mob mob, int mobLevel) {
                float cap = perLevel * curve(mobLevel);
                float current = mob.getAbsorptionAmount();
                if (current < cap) {
                    mob.setAbsorptionAmount(Math.min(cap, current + cap * 0.10F));
                }
            }

            @Override
            public String descriptionKey() {
                return "elysiummobs.ability.bulwark";
            }
        };
    }

    /**
     * Hardened: takes proportionally less from one kind of harm and more from
     * another.
     *
     * A trade rather than a bonus. A variant that is simply tougher is the
     * variant everyone dreads meeting; one that is tough in a particular way is
     * a reason to change weapons.
     */
    public static MobAbility hardened(float physicalScale, float elementalScale) {
        return new MobAbility() {
            @Override
            public float incomingScale(Mob mob, DamageSource source, int mobLevel) {
                boolean elemental = source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                        || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
                return elemental ? elementalScale : physicalScale;
            }

            @Override
            public String descriptionKey() {
                return "elysiummobs.ability.hardened";
            }
        };
    }

    /** Regenerating: heals steadily, so a slow fight is a losing one. */
    public static MobAbility knitting(float fraction) {
        return new MobAbility() {
            @Override
            public void onServerTick(Mob mob, int mobLevel) {
                if (mob.getHealth() < mob.getMaxHealth()) {
                    mob.heal(mob.getMaxHealth() * fraction);
                }
            }

            @Override
            public String descriptionKey() {
                return "elysiummobs.ability.knitting";
            }
        };
    }

    // ==================================================================
    // Offensive
    // ==================================================================

    /**
     * Cornered: hits harder the closer it is to dying.
     *
     * The mirror of the Druun's Cold Blood, and it does the same job for a
     * fight — the last third of a health bar is the dangerous part, so
     * finishing something off is a decision rather than a formality.
     */
    public static MobAbility cornered(float maximumBonus) {
        return new MobAbility() {
            @Override
            public float outgoingScale(Mob mob, LivingEntity victim, int mobLevel) {
                float missing = 1.0F - mob.getHealth() / Math.max(1.0F, mob.getMaxHealth());
                return 1.0F + maximumBonus * missing;
            }

            @Override
            public String descriptionKey() {
                return "elysiummobs.ability.cornered";
            }
        };
    }

    /** Venomous: leaves something behind on every hit. */
    public static MobAbility venomous(int seconds, int amplifier) {
        return new MobAbility() {
            @Override
            public float outgoingScale(Mob mob, LivingEntity victim, int mobLevel) {
                victim.addEffect(new MobEffectInstance(MobEffects.POISON,
                        seconds * 20, amplifier, false, true));
                return 1.0F;
            }

            @Override
            public String descriptionKey() {
                return "elysiummobs.ability.venomous";
            }
        };
    }

    /**
     * Overcharged: a burst of speed and damage when it takes a hit.
     *
     * Triggered by being hurt rather than by a timer, so it fires when the
     * player engages rather than at some moment they cannot see coming.
     */
    public static MobAbility overcharged(int seconds) {
        return new MobAbility() {
            @Override
            public void onHurt(Mob mob, DamageSource source, float amount, int mobLevel) {
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                        seconds * 20, 1, false, true));
                mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                        seconds * 20, 0, false, true));
            }

            @Override
            public String descriptionKey() {
                return "elysiummobs.ability.overcharged";
            }
        };
    }

    /** Sundering: strips a share of the target's armour value on hit. */
    public static MobAbility sundering(int seconds) {
        return new MobAbility() {
            @Override
            public float outgoingScale(Mob mob, LivingEntity victim, int mobLevel) {
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                        seconds * 20, 0, false, true));
                return 1.0F;
            }

            @Override
            public String descriptionKey() {
                return "elysiummobs.ability.sundering";
            }
        };
    }

    // ==================================================================
    // Group
    // ==================================================================

    /**
     * Standard bearer: strengthens every other Elysium mob nearby.
     *
     * The only ability that touches something other than itself and its target,
     * and the reason a room of mobs is worse than the same mobs one at a time.
     * Deliberately does not buff itself — a bearer that made itself the biggest
     * threat would be killed first for the wrong reason, and the interesting
     * decision is whether to fight past the escort to reach it.
     */
    public static MobAbility standard(double radius, int amplifier) {
        return new MobAbility() {
            @Override
            public void onServerTick(Mob mob, int mobLevel) {
                List<Mob> nearby = mob.level().getEntitiesOfClass(Mob.class,
                        new AABB(mob.blockPosition()).inflate(radius));
                for (Mob other : nearby) {
                    if (other == mob) {
                        continue;
                    }
                    other.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                            60, amplifier, false, false));
                }
            }

            @Override
            public String descriptionKey() {
                return "elysiummobs.ability.standard";
            }
        };
    }

    /**
     * Warden's echo: passes a share of the harm it takes to its neighbours.
     *
     * So a crowd dies together rather than one at a time, which changes how a
     * room is fought — focusing one target stops being obviously correct.
     */
    public static MobAbility echo(double radius, float share) {
        return new MobAbility() {
            @Override
            public void onHurt(Mob mob, DamageSource source, float amount, int mobLevel) {
                List<Mob> nearby = mob.level().getEntitiesOfClass(Mob.class,
                        new AABB(mob.blockPosition()).inflate(radius));
                for (Mob other : nearby) {
                    if (other != mob) {
                        other.hurt(mob.damageSources().magic(), amount * share);
                    }
                }
            }

            @Override
            public String descriptionKey() {
                return "elysiummobs.ability.echo";
            }
        };
    }

    // ==================================================================
    // On death
    // ==================================================================

    /**
     * Deadfall: harms whatever killed it.
     *
     * Scaled off the mob's own damage rather than a flat number, so it stays
     * proportionate to the fight it belonged to.
     */
    public static MobAbility deadfall(float multiplier) {
        return new MobAbility() {
            @Override
            public void onDeath(Mob mob, DamageSource source, int mobLevel) {
                Entity killer = source.getEntity();
                if (killer instanceof LivingEntity living) {
                    living.hurt(mob.damageSources().magic(),
                            2.0F * multiplier * curve(mobLevel));
                }
            }

            @Override
            public String descriptionKey() {
                return "elysiummobs.ability.deadfall";
            }
        };
    }

    /** Blinding: leaves the killer unable to see for a moment. */
    public static MobAbility blinding(int seconds) {
        return new MobAbility() {
            @Override
            public void onDeath(Mob mob, DamageSource source, int mobLevel) {
                if (source.getEntity() instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                            seconds * 20, 0, false, true));
                }
            }

            @Override
            public String descriptionKey() {
                return "elysiummobs.ability.blinding";
            }
        };
    }

    // ==================================================================
    // Movement
    // ==================================================================

    /** Swift: quick, and quicker still when it has nothing to fight. */
    public static MobAbility swift(int amplifier) {
        return new MobAbility() {
            @Override
            public void onServerTick(Mob mob, int mobLevel) {
                if (mob.getTarget() == null) {
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                            40, amplifier, false, false));
                }
            }

            @Override
            public String descriptionKey() {
                return "elysiummobs.ability.swift";
            }
        };
    }

    /** Unshaken: cannot be pushed around. */
    public static MobAbility unshaken() {
        return new MobAbility() {
            @Override
            public void onSpawn(Mob mob, int mobLevel) {
                net.minecraft.world.entity.ai.attributes.AttributeInstance knockback =
                        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes
                                .KNOCKBACK_RESISTANCE);
                if (knockback != null) {
                    knockback.setBaseValue(1.0D);
                }
            }

            @Override
            public String descriptionKey() {
                return "elysiummobs.ability.unshaken";
            }
        };
    }

    /** Plain: no ability at all, and deliberately so. */
    public static MobAbility none() {
        return new MobAbility() {
            @Override
            public String descriptionKey() {
                return "elysiummobs.ability.none";
            }
        };
    }
}
