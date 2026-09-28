package com.elysium.bestiary.entity;

import com.elysium.bestiary.entity.act.CompanionGifts;
import com.elysium.bestiary.rules.CompanionRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import com.elysium.lib.sound.ElysiumVoices;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;

/**
 * One class for all eleven, driven by its row.
 *
 * <p>None of these is a fight, so none of them is a {@code Monster}. What each
 * one does is in {@link CompanionGifts}, one branch per gift and no creature
 * named in any of them — the same arrangement the beasts' mechanics and the
 * myths' banes both use, and for the same reason: eleven classes would be
 * eleven places for one bug, and a twelfth creature would be a twelfth file.
 *
 * <h2>Tamed, and what that costs</h2>
 *
 * Three of the eleven have to be earned before they do anything. That is not a
 * different class either: it is a flag on the row and a check before the gift,
 * and {@link CompanionRules#faultsInCost} is what stops the other eight being
 * free — because a companion that costs nothing a block does not is a companion
 * that ends the block.
 */
public class CompanionEntity extends Animal {

    /** Where being yours is written down. */
    public static final String OWNED = "ElysiumOwned";

    /** And how long since it last did its one thing. */
    public static final String SINCE = "ElysiumSince";

    private boolean owned;
    private int since;

    public CompanionEntity(EntityType<? extends CompanionEntity> type, Level level) {
        super(type, level);
    }

    /** The row this one is, by the name it was registered under. */
    public CompanionRules.Companion companion() {
        String name = EntityType.getKey(getType()).getPath();
        CompanionRules.Companion found = CompanionRules.companion(name);
        return found == null ? CompanionRules.COMPANIONS.get(0) : found;
    }

    /** Whether it has been earned, for the ones that must be. */
    public boolean owned() {
        return owned;
    }

    /** Whether it will do its one thing at all. */
    public boolean willing() {
        return !companion().tamed() || owned;
    }

    /**
     * What the game needs to know about one before it exists.
     *
     * <p>Modest and the same for all eleven. None of these is a fight, so what
     * differs between them is what they do rather than what they can take — and
     * a stat line per companion would be eleven numbers nothing in the rules
     * holds to anything.
     */
    public static AttributeSupplier.Builder attributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.FOLLOW_RANGE, CompanionRules.MOST_REACH);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.3D));
        goalSelector.addGoal(3, new TemptGoal(this, 1.1D,
                Ingredient.of(Items.WHEAT_SEEDS), false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.WHEAT_SEEDS);
    }

    @Override
    public AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level,
                                        AgeableMob mate) {
        return com.elysium.bestiary.registry.CompanionEntities.ALL
                .get(companion().id()).get().create(level);
    }

    /**
     * Being earned.
     *
     * <p>One item, once, and it is yours. What it costs to keep is the row's
     * own upkeep and is spent by {@link CompanionGifts}; what it costs to get
     * is this.
     */
    @Override
    public net.minecraft.world.InteractionResult mobInteract(Player player,
                                                             net.minecraft.world.InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (companion().tamed() && !owned && isFood(held)) {
            if (!level().isClientSide()) {
                owned = true;
                if (!player.isCreative()) {
                    held.shrink(1);
                }
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(
                    level().isClientSide());
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }
        since++;
        if (since >= CompanionGifts.BETWEEN) {
            since = 0;
            if (willing()) {
                CompanionGifts.give(this, companion());
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(OWNED, owned);
        tag.putInt(SINCE, since);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owned = tag.getBoolean(OWNED);
        since = tag.getInt(SINCE);
    }

    // --- what it says (elysium-lib sound table, POLISH.md P5) ---------------

    /** The voice this one speaks with, a name in ElysiumSoundRules. */
    public String voice() {
        return "forager";
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ElysiumVoices.ambient(voice(), super.getAmbientSound());
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ElysiumVoices.hurt(voice(), super.getHurtSound(source));
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ElysiumVoices.death(voice(), super.getDeathSound());
    }

    @Override
    public float getVoicePitch() {
        return ElysiumVoices.pitchOf(this);
    }
}
