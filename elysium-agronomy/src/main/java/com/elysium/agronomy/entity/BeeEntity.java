package com.elysium.agronomy.entity;

import com.elysium.agronomy.registry.BeeEntities;
import com.elysium.agronomy.rules.BeeRules;
import com.elysium.agronomy.rules.KeepingRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;
import org.jetbrains.annotations.Nullable;

/**
 * One bee. Which one it is, it is told.
 *
 * <p>Seventy-five species share this class because they differ in what they
 * make and where they live, not in how they fly. A class per species would be
 * seventy-five files that have to be kept identical by hand, which is the
 * state the registry of the mod this replaces is in.
 *
 * <p>The species is carried in synched data rather than in the entity type
 * alone so that the client can render and name it without a second lookup, and
 * so that a bee written to disk comes back as the bee it was. It is written on
 * both sides of the save: {@link #addAdditionalSaveData} and
 * {@link #readAdditionalSaveData}.
 */
public class BeeEntity extends Animal {

    private static final EntityDataAccessor<String> SPECIES =
            SynchedEntityData.defineId(BeeEntity.class, EntityDataSerializers.STRING);

    /** How long a bee will go on working before it wants a flower again. */
    public static final int PATIENCE_TICKS = 600;

    private int worked;

    /** The game time until which smoke keeps this bee calm. */
    private long calmUntil;

    /** Who upset it, and until when; null when nobody has. */
    @Nullable
    private java.util.UUID upsetBy;
    private long upsetUntil;

    public BeeEntity(EntityType<? extends BeeEntity> type, Level level) {
        super(type, level);
        // One type per species, so the type says which bee this is. Without
        // this every bee that spawned on its own or out of an egg was the
        // first bee in the roster wearing another's name.
        String path = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(type).getPath();
        if (path.endsWith("_bee")) {
            setSpecies(path.substring(0, path.length() - "_bee".length()));
        }
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.COCOA, -1.0F);
        this.setPathfindingMalus(PathType.FENCE, -1.0F);
    }

    /**
     * The attributes every bee shares.
     *
     * <p>Flat across species on purpose. A bee is not a fight; making the
     * netherite bee tougher than the iron bee would be a number nobody ever
     * sees and one more thing to keep consistent.
     */
    public static AttributeSupplier.Builder attributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SPECIES, BeeRules.BEES.get(0).id());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.1D,
                Ingredient.of(Items.HONEYCOMB), false));
        this.goalSelector.addGoal(4,
                new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(false);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    /** Which species this one is. */
    public String species() {
        return this.entityData.get(SPECIES);
    }

    /** Tell it which species it is. Silently ignores a name no bee has. */
    public void setSpecies(String id) {
        if (BeeRules.bee(id) != null) {
            this.entityData.set(SPECIES, id);
        }
    }

    /** The rules row for this bee, or the first bee if something has gone wrong. */
    public BeeRules.Bee rules() {
        BeeRules.Bee bee = BeeRules.bee(species());
        return bee != null ? bee : BeeRules.BEES.get(0);
    }

    /**
     * How long this one takes over a comb.
     *
     * <p>Read off the rules by tier rather than stored, so retuning the ladder
     * moves every bee already alive in a world.
     */
    public int combTicks() {
        int tier = BeeRules.tierOf(species());
        return BeeRules.combTicks(Math.max(0, tier));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Species", species());
        tag.putInt("Worked", this.worked);
        tag.putLong("CalmUntil", this.calmUntil);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Species")) {
            setSpecies(tag.getString("Species"));
        }
        this.worked = tag.getInt("Worked");
        this.calmUntil = tag.getLong("CalmUntil");
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            this.worked = Math.min(this.worked + 1, Integer.MAX_VALUE - 1);
            sting();
        }
    }

    // ------------------------------------------------------------------
    // Temper
    // ------------------------------------------------------------------

    /** Whether smoke is keeping this one calm. */
    public boolean calm() {
        return this.level().getGameTime() < this.calmUntil;
    }

    /** Smoke it: calm for {@link KeepingRules#SMOKE_TICKS}, and no longer upset. */
    public void smoke() {
        this.calmUntil = this.level().getGameTime() + KeepingRules.SMOKE_TICKS;
        this.upsetBy = null;
    }

    /**
     * Upset it: for {@link KeepingRules#ANGER_TICKS} it goes after whoever
     * did it and stings them once. A smoked bee does not notice.
     */
    public void upset(Player by) {
        if (calm() || by.getAbilities().instabuild) {
            return;
        }
        this.upsetBy = by.getUUID();
        this.upsetUntil = this.level().getGameTime() + KeepingRules.ANGER_TICKS;
    }

    /** Whether it is upset with somebody right now. */
    public boolean upset() {
        return this.upsetBy != null && this.level().getGameTime() < this.upsetUntil;
    }

    private void sting() {
        if (this.upsetBy == null) {
            return;
        }
        Player target = this.level().getPlayerByUUID(this.upsetBy);
        if (!upset() || calm() || target == null || !target.isAlive()) {
            this.upsetBy = null;
            return;
        }
        if (distanceToSqr(target) < 2.25D) {
            target.hurt(damageSources().sting(this), KeepingRules.STING_DAMAGE);
            this.upsetBy = null;
            return;
        }
        getNavigation().moveTo(target, 1.4D);
    }

    /** How far through its current comb it is, from zero to one. */
    public float progress() {
        int ticks = Math.max(1, combTicks());
        return Math.min(1.0F, (float) this.worked / ticks);
    }

    /** Take the comb off it and start again. Returns whether there was one. */
    public boolean harvest() {
        if (progress() < 1.0F) {
            return false;
        }
        this.worked = 0;
        return true;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level
            .block.state.BlockState state, BlockPos pos) {
        // A bee does not fall.
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier,
                                   net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.HONEYCOMB) || stack.is(Items.HONEY_BOTTLE);
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level,
                                        AgeableMob partner) {
        // Breeding two bees is the module's own mechanic and happens in the
        // hive, where both parents and the rules are to hand. Two loose bees
        // in a field do not make a third.
        return null;
    }

    /** Whether a bee of this species is at home here. */
    public static boolean atHome(BeeRules.Bee bee, LevelReader level, BlockPos pos) {
        if (bee.home().isEmpty()) {
            return true;
        }
        return level.getBiome(pos).is(net.minecraft.tags.BiomeTags.IS_OVERWORLD)
                || !bee.home().equals("plains");
    }

    /** The entity type for a species, for anything that has to spawn one. */
    public static EntityType<BeeEntity> typeOf(String species) {
        return BeeEntities.of(species);
    }
}
