package com.elysium.agronomy.entity;

import com.elysium.agronomy.registry.FishItems;
import com.elysium.agronomy.rules.FishRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * One fish. Which one it is, it is told.
 *
 * <p>A hundred and five species and one class, for the reason the bees have
 * one: they differ in where they live and what they are worth, not in how they
 * swim. What varies is carried in the species, and the species is read out of
 * {@link FishRules} rather than stored twice.
 */
public class FishEntity extends AbstractFish {

    private static final EntityDataAccessor<String> SPECIES =
            SynchedEntityData.defineId(FishEntity.class, EntityDataSerializers.STRING);

    public FishEntity(EntityType<? extends FishEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SPECIES, FishRules.FISH.get(0).id());
    }

    /** Which species this one is. */
    public String species() {
        return this.entityData.get(SPECIES);
    }

    /** Tell it which species it is. A name no fish has is ignored. */
    public void setSpecies(String id) {
        if (FishRules.fish(id) != null) {
            this.entityData.set(SPECIES, id);
        }
    }

    /** The rules row, or the first fish if something has gone wrong. */
    public FishRules.Fish rules() {
        FishRules.Fish one = FishRules.fish(species());
        return one != null ? one : FishRules.FISH.get(0);
    }

    /**
     * Whether water hurts it.
     *
     * <p>A fish that lives in lava is not helped by water, so what counts as
     * the wrong liquid is read off the family rather than assumed. Breathing
     * itself is left to the game: {@code canBreatheUnderwater} is final on the
     * class this extends, which is the game saying a fish breathes water and
     * that is that.
     */
    @Override
    public boolean isSensitiveToWater() {
        return FishRules.LAVA.equals(rules().family().water());
    }

    @Override
    public boolean fireImmune() {
        return FishRules.LAVA.equals(rules().family().water());
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(FishItems.bucketOf(species()));
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.COD_FLOP;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Species", species());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Species")) {
            setSpecies(tag.getString("Species"));
        }
    }

    @Override
    public void saveToBucketTag(ItemStack stack) {
        super.saveToBucketTag(stack);
        // The species travels in the bucket. A bucket that forgets what is in
        // it pours out the first fish in the roster, which is the kind of bug
        // somebody only finds after carrying a rare one home.
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.translatable(
                        "item.elysiumagronomy." + species() + "_bucket"));
    }
}
