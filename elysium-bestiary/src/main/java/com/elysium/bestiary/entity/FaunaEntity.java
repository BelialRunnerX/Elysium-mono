package com.elysium.bestiary.entity;

import com.elysium.bestiary.registry.FaunaEntities;
import com.elysium.bestiary.rules.FaunaRules;
import com.elysium.bestiary.rules.HauntRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * One class for everything that does not attack you.
 *
 * <h2>Why there is one entity class and not twelve</h2>
 *
 * The same argument {@link BeastEntity} makes: twelve animals differ in nine
 * numbers and a set of goals, and twelve classes would be twelve places for
 * one bug. Which kind an animal is decides how it behaves; everything else it
 * is comes out of its own row in {@link FaunaRules#ANIMALS}, looked up by the
 * name it was registered under so there is nothing to store and nothing that
 * can be stored wrong.
 *
 * <h2>And why the breeding item is not a tag</h2>
 *
 * Because the rules have to be able to say what every animal eats, all at
 * once, in order to prove that nothing a pen produces is anything a pen
 * consumes. A tag is a file the rules cannot see, and a pen that feeds itself
 * is a pack where hunger stopped happening and nobody could say when.
 */
public class FaunaEntity extends Animal {

    /** How far one will notice you holding what it eats. */
    public static final double NOTICES_FROM = 10.0D;

    public FaunaEntity(EntityType<? extends FaunaEntity> type, Level level) {
        super(type, level);
    }

    /** The row this animal is, by the name it was registered under. */
    public FaunaRules.Animal animal() {
        String name = EntityType.getKey(getType()).getPath();
        FaunaRules.Animal found = FaunaRules.animal(name);
        return found == null ? FaunaRules.ANIMALS.get(0) : found;
    }

    /** What the game needs to know about one of these before it exists. */
    public static AttributeSupplier.Builder attributes(FaunaRules.Animal animal) {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, animal.health())
                .add(Attributes.MOVEMENT_SPEED, animal.speed())
                .add(Attributes.FOLLOW_RANGE, NOTICES_FROM);
    }

    /**
     * How each kind behaves, and nothing else differs.
     *
     * <p>A swimmer swims and never strolls; everything else strolls and panics.
     * All four are tempted by what they eat and all four breed on it, because
     * an animal a player cannot keep is a decoration with a loot table.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.5D));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        goalSelector.addGoal(3, new TemptGoal(this, 1.1D, food(), false));
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.1D));
        if (FaunaRules.SWIMMER.equals(animal().kind())) {
            goalSelector.addGoal(5, new RandomSwimmingGoal(this, 1.0D, 40));
        } else {
            goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        }
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** What this one is bred with, out of its own row. */
    public Ingredient food() {
        return Ingredient.of(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.ResourceLocation.parse(animal().eats())));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return food().test(stack);
    }

    /**
     * How long before it will breed again, out of the arithmetic.
     *
     * <p>Never typed here. {@link FaunaRules#patienceOf} works it out from how
     * much the animal leaves, which is the only thing that stops one row of
     * the roster being the answer to every pen.
     */
    @Override
    public void setInLoveTime(int ticks) {
        super.setInLoveTime(ticks);
    }

    // No step that puts a grown animal back on its cooldown whenever it is
    // not in love: that left a player one tick in which to feed it, and no
    // animal of ours ever bred (the mechanics survey, 27 September 2026).
    // The cooldown is set where the game sets its own, in resetLove, after a
    // breeding, and lasts the row's patience instead of the game's number.

    @Override
    public void resetLove() {
        super.resetLove();
        setAge(FaunaRules.patienceOf(animal()));
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob mate) {
        return FaunaEntities.ALL.get(animal().id()).get().create(level);
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(
            net.minecraft.world.entity.Pose pose) {
        FaunaRules.Animal animal = animal();
        return net.minecraft.world.entity.EntityDimensions.scalable(animal.width(),
                animal.height());
    }

    /**
     * Whether one may appear here.
     *
     * <p>The same haunt the bestiary uses, and the same reason: a light band
     * and a height band in a table nothing reads are a table that might as
     * well not exist. A land animal that spawns in the pitch dark is a land
     * animal in a cave, which is where the things that eat it live.
     */
    public static boolean canSpawn(EntityType<? extends Animal> type,
                                   ServerLevelAccessor level,
                                   net.minecraft.world.entity.MobSpawnType reason,
                                   BlockPos pos,
                                   net.minecraft.util.RandomSource random) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(type).getPath();
        HauntRules.Haunt haunt = HauntRules.haunt(id);
        if (haunt == null) {
            return true;
        }
        if (pos.getY() < haunt.lowest() || pos.getY() > haunt.highest()) {
            return false;
        }
        int light = level.getMaxLocalRawBrightness(pos);
        return light >= haunt.darkest() && light <= haunt.brightest();
    }
}
