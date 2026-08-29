package com.elysium.mobs.entity;

import com.elysium.lib.entity.ElysiumFaction;
import com.elysium.lib.entity.ElysiumScaling;
import com.elysium.mobs.ElysiumMobs;
import com.elysium.mobs.variant.MobAbility;
import com.elysium.mobs.variant.MobVariant;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * The base every Elysium creature is built on.
 *
 * <h2>What this class owns</h2>
 *
 * The three things every one of the thirty has in common, and nothing else:
 *
 * <ol>
 *   <li><b>A faction.</b> Which side it is on, which decides whether killing it
 *       earns Favor or Suspicion. Answered by the subclass, because it is a
 *       property of the family rather than of the individual.</li>
 *   <li><b>A sub-variant</b>, held as synced entity data so the client knows
 *       which texture to draw, and written to NBT so it survives a reload.</li>
 *   <li><b>A level</b>, fixed at spawn. Everything about how dangerous this
 *       creature is follows from it.</li>
 * </ol>
 *
 * <h2>Why the level is stored rather than recomputed</h2>
 *
 * A mob is scaled once, when it spawns, from the players who were nearby then.
 * Recomputing it later would mean a mob that got stronger because a high-level
 * player walked past, and weaker when they left — a fight whose difficulty
 * changes while you are having it. Storing it also means the number survives
 * the chunk unloading with the mob still in it.
 */
public abstract class ElysiumMob extends Monster {

    /**
     * The variant's id, as a string.
     *
     * A string rather than an index, because an index is a promise that the
     * registration order never changes — and the first add-on that registers a
     * variant breaks that promise silently, turning every saved mob into a
     * different one.
     */
    private static final EntityDataAccessor<String> VARIANT =
            SynchedEntityData.defineId(ElysiumMob.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<Integer> LEVEL =
            SynchedEntityData.defineId(ElysiumMob.class, EntityDataSerializers.INT);

    /** Cached so the ability is not looked up from the registry every tick. */
    private MobVariant cachedVariant;

    protected ElysiumMob(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    // ------------------------------------------------------------------
    // What a family decides
    // ------------------------------------------------------------------

    /** Which side this family is on. */
    public abstract ElysiumFaction getFaction();

    /** The family id, which is what its sub-variants are registered against. */
    public abstract ResourceLocation getFamilyId();

    // ------------------------------------------------------------------
    // Synced data
    // ------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, "");
        builder.define(LEVEL, 1);
    }

    public MobVariant getVariant() {
        if (cachedVariant == null) {
            cachedVariant = MobVariant.byName(this.entityData.get(VARIANT));
        }
        return cachedVariant;
    }

    public void setVariant(MobVariant variant) {
        cachedVariant = variant;
        this.entityData.set(VARIANT, variant == null ? "" : variant.getSerialisedName());
    }

    public int getMobLevel() {
        return this.entityData.get(LEVEL);
    }

    public void setMobLevel(int level) {
        this.entityData.set(LEVEL, Math.max(1, level));
    }

    /**
     * The ability of this mob's variant, or a do-nothing one.
     *
     * Never null, so every call site is a plain call rather than a null check —
     * an unvariant mob is a legal thing to be (a command spawned it, or a
     * variant's mod was removed) and it simply has no ability.
     */
    public MobAbility ability() {
        MobVariant variant = getVariant();
        return variant == null ? NO_ABILITY : variant.getAbility();
    }

    private static final MobAbility NO_ABILITY = new MobAbility() {
    };

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    /**
     * Rolls a variant and scales to a level. Call once, after creating.
     *
     * Order matters and is not obvious: the variant's multipliers are applied
     * on top of the level scaling rather than before it, so a "tough" variant
     * is proportionally tough at every level rather than only at low ones.
     */
    public void initialise(int mobLevel, net.minecraft.util.RandomSource random) {
        MobVariant variant = MobVariant.random(getFamilyId(), random);
        setVariant(variant);
        setMobLevel(mobLevel);

        ElysiumScaling.apply(this, mobLevel);

        if (variant != null) {
            multiply(Attributes.MAX_HEALTH, variant.getHealthScale());
            multiply(Attributes.ATTACK_DAMAGE, variant.getDamageScale());
            multiply(Attributes.MOVEMENT_SPEED, variant.getSpeedScale());
            variant.getAbility().onSpawn(this, mobLevel);
        }

        // After every multiplier, for the same reason the library sets it last:
        // raising max health does not refill, and a mob that arrived at a
        // fraction of its bar would die to the first hit.
        setHealth(getMaxHealth());

        if (variant != null) {
            setCustomName(variant.getDisplayName());
        }
    }

    private void multiply(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                          float factor) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * factor);
        }
    }

    // ------------------------------------------------------------------
    // Behaviour
    // ------------------------------------------------------------------

    /** Ability ticks run on the regeneration cadence, not every tick. */
    private static final int ABILITY_INTERVAL = 10;

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount % ABILITY_INTERVAL == 0) {
            ability().onServerTick(this, getMobLevel());
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        float scale = ability().incomingScale(this, source, getMobLevel());
        boolean hurt = super.hurt(source, amount * scale);
        if (hurt && isAlive()) {
            ability().onHurt(this, source, amount * scale, getMobLevel());
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        // The ability's multiplier is applied by temporarily raising the
        // attribute rather than by intercepting the damage, so that enchantment
        // and effect maths downstream see the real number - intercepting after
        // the fact would silently ignore Strength, weakness and everything else
        // vanilla layers on top.
        float scale = target instanceof LivingEntity victim
                ? ability().outgoingScale(this, victim, getMobLevel())
                : 1.0F;
        if (scale == 1.0F) {
            return super.doHurtTarget(target);
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance damage =
                getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage == null) {
            return super.doHurtTarget(target);
        }
        double base = damage.getBaseValue();
        damage.setBaseValue(base * scale);
        try {
            return super.doHurtTarget(target);
        } finally {
            damage.setBaseValue(base);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide()) {
            ability().onDeath(this, source, getMobLevel());
        }
        super.die(source);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("ElysiumVariant", this.entityData.get(VARIANT));
        tag.putInt("ElysiumLevel", getMobLevel());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(VARIANT, tag.getString("ElysiumVariant"));
        cachedVariant = null;
        setMobLevel(tag.getInt("ElysiumLevel"));

        // Attributes are saved by vanilla, so they are NOT reapplied here.
        // Scaling again on every load would compound: a mob that survived ten
        // chunk reloads would have ten times the intended health, and the bug
        // would only show up on a long-running server.
    }

    /** The name shown above the mob: its variant, or its family. */
    @Override
    public Component getDisplayName() {
        MobVariant variant = getVariant();
        if (variant == null) {
            return super.getDisplayName();
        }
        return Component.translatable("elysiummobs.mob.levelled",
                variant.getDisplayName(), getMobLevel());
    }

    /**
     * Elysium creatures never despawn on their own.
     *
     * They are placed deliberately — by a dungeon room or by a standing
     * dispatch — and a mob that vanished while the player was walking to it
     * would make the boss room empty about a third of the time.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    static {
        ElysiumMobs.LOGGER.debug("ElysiumMob base loaded");
    }
}
