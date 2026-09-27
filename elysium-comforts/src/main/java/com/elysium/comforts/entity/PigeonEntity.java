package com.elysium.comforts.entity;

import com.elysium.comforts.block.PigeonholeBlock;
import com.elysium.comforts.registry.ComfortEntities;
import com.elysium.comforts.rules.PostRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * The bird that carries it, and the one the Nether makes of it.
 *
 * <h2>One class, two birds, and a loop that has to close</h2>
 *
 * A pigeon carried below turns into a charred pigeon, and a charred pigeon
 * carried back turns into a pigeon. That the loop closes is not decoration: an
 * open one would mean a player who took a bird through a portal had destroyed
 * it, and nothing in the game would ever say so. {@link PostRules#faultsInLoop}
 * is the rule that holds it shut, and this class reads the answer rather than
 * deciding it — {@link #becomes()} is a lookup, not a branch, so a third bird
 * is a row in the rules and nothing here changes.
 *
 * <h2>Tired</h2>
 *
 * A bird that has just delivered rests. Which is the only reason a player keeps
 * more than one, and therefore the only reason a pigeonhole is a block worth
 * building rather than a block worth seeing once.
 */
public class PigeonEntity extends Animal {

    /** How long it has been where it does not belong. */
    public static final String ELSEWHERE = "ElysiumElsewhere";

    /** And how long it is resting for. */
    public static final String TIRED = "ElysiumTired";

    /** Which colouring it is. */
    public static final String VARIANT = "ElysiumVariant";

    private int elsewhere;
    private int tired;
    private int variant;

    public PigeonEntity(EntityType<? extends PigeonEntity> type, Level level) {
        super(type, level);
    }

    /** The row this bird is, by the name it was registered under. */
    public PostRules.Bird bird() {
        String name = EntityType.getKey(getType()).getPath();
        PostRules.Bird found = PostRules.bird(name);
        return found == null ? PostRules.BIRDS.get(0) : found;
    }

    /** What this one turns into somewhere it does not belong. */
    public PostRules.Bird becomes() {
        String next = bird().becomes();
        return next == null ? null : PostRules.bird(next);
    }

    /** Whether it is resting off a delivery. */
    public boolean tired() {
        return tired > 0;
    }

    /** Send it to rest. */
    public void rest() {
        tired = PostRules.TIRED_FOR;
    }

    /** Which colouring it is. */
    public int variant() {
        return variant;
    }

    public static AttributeSupplier.Builder attributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                // MineColonies sizes every animal for its herders by reading this;
                // a bird without it is an error in its log at every start.
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.FLYING_SPEED, 0.4D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.4D));
        goalSelector.addGoal(2, new TemptGoal(this, 1.1D,
                Ingredient.of(Items.WHEAT_SEEDS), false));
        goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.WHEAT_SEEDS);
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob mate) {
        return ComfortEntities.ALL.get(bird().id()).get().create(level);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,
                                        DifficultyInstance difficulty,
                                        MobSpawnType reason,
                                        SpawnGroupData data) {
        variant = level.getRandom().nextInt(Math.max(1, bird().variants()));
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    /**
     * The clock: resting, and being somewhere it does not belong.
     *
     * <p>Two independent counters and two independent tests, rather than one
     * counter and an {@code if/else}. A bird that is both tired and in the
     * wrong dimension has to do both things, and a branch that picks one of
     * them is a bird that never turns or never rests depending on which arm was
     * written first.
     */
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }
        if (tired > 0) {
            tired--;
        }
        PostRules.Bird mine = bird();
        boolean home = mine.home().equals(where());
        if (home) {
            elsewhere = 0;
        }
        if (!home) {
            elsewhere++;
        }
        if (!home && elsewhere >= mine.afterTicks()) {
            turn();
        }
    }

    /** Which of the rules' dimensions this bird is standing in. */
    private String where() {
        return level().dimensionType().ultraWarm() ? PostRules.BELOW : PostRules.ABOVE;
    }

    /**
     * Become the other bird.
     *
     * <p>The new one is placed and only then is the old one discarded, in that
     * order, because the other order is a bird that is briefly nowhere and a
     * crash in between is a bird gone for good.
     */
    private void turn() {
        PostRules.Bird next = becomes();
        if (next == null || !(level() instanceof ServerLevel server)) {
            return;
        }
        PigeonEntity made = ComfortEntities.ALL.get(next.id()).get().create(server);
        if (made == null) {
            return;
        }
        made.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
        made.setHealth(getHealth());
        made.variant = 0;
        server.addFreshEntity(made);
        discard();
    }

    /** Somewhere for a bird to have been. */
    public void cameOutOf(BlockPos hole) {
        PigeonholeBlock.used(level(), hole, tired());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(ELSEWHERE, elsewhere);
        tag.putInt(TIRED, tired);
        tag.putInt(VARIANT, variant);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        elsewhere = tag.getInt(ELSEWHERE);
        tired = tag.getInt(TIRED);
        variant = tag.getInt(VARIANT);
    }
}
