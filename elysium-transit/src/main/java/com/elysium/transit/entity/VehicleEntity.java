package com.elysium.transit.entity;

import com.elysium.transit.registry.TransitEntities;
import com.elysium.transit.registry.TransitItems;
import com.elysium.transit.rules.TransitRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Something you ride, and the thing the roads are for.
 *
 * <h2>One class, three rungs</h2>
 *
 * A cart, a truck and a hauler differ in four numbers and nothing else, so
 * they are one class and three entity types rather than three classes. Which
 * rung a given vehicle is comes from its own entity type, so a fourth is four
 * numbers and a texture rather than a file.
 *
 * <h2>Why it is slower than a player on rough ground</h2>
 *
 * Because otherwise roads are decoration. A fully laden cart over scree loses
 * to walking; the same cart on paving beats it comfortably. That is the whole
 * argument for laying a road, and it is proved as arithmetic in
 * {@link TransitRules} before any of this exists.
 *
 * <h2>And why it runs on coal</h2>
 *
 * Anything that burns, drives. A vehicle with a fuel item of its own would be
 * a crafting chain nobody asked for, and "it runs on coal" is a sentence that
 * needs no wiki.
 */
public class VehicleEntity extends Entity {

    private static final EntityDataAccessor<Float> FUEL =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED =
            SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);

    /** How far it may climb without a ramp: one block, like a horse. */
    public static final float STEP = 1.0F;

    /** What it carries. Sized from the rung, so it has to be made lazily. */
    private ItemStackHandler cargo;

    public VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    /**
     * One block, like a horse.
     *
     * <p>A vehicle that could not get over a kerb would be a vehicle nobody
     * drives anywhere except the one flat road they built for it.
     */
    @Override
    public float maxUpStep() {
        return STEP;
    }

    /** Which rung this one is. */
    public TransitRules.Vehicle rung() {
        return TransitEntities.rungOf(getType());
    }

    public ItemStackHandler cargo() {
        if (cargo == null) {
            cargo = new ItemStackHandler(Math.max(1, rung().slots()));
        }
        return cargo;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FUEL, 0.0F);
        builder.define(SPEED, 0.0F);
    }

    public float fuel() {
        return entityData.get(FUEL);
    }

    public void setFuel(float fuel) {
        entityData.set(FUEL, Math.max(0.0F, Math.min(TransitRules.TANK, fuel)));
    }

    /** How fast it is going, in blocks a second, for the readout and the model. */
    public float speed() {
        return entityData.get(SPEED);
    }

    /** How full it is, as the fraction the speed and thirst rules want. */
    public float load() {
        int filled = 0;
        for (int slot = 0; slot < cargo().getSlots(); slot++) {
            if (!cargo().getStackInSlot(slot).isEmpty()) {
                filled++;
            }
        }
        return TransitRules.loadOf(filled, rung().slots());
    }

    /**
     * What it is driving over.
     *
     * <p>Read from the block under the wheels, and deliberately only three
     * things are special: what we lay as road, what we lay as path, and the
     * handful of vanilla blocks anybody would call rough. Everything else is
     * ground. A surface table with forty entries in it is a table nobody can
     * hold, and the rules only have four rungs to sort into.
     */
    public TransitRules.Surface surface() {
        BlockPos under = BlockPos.containing(getX(), getBoundingBox().minY - 0.2, getZ());
        BlockState state = level().getBlockState(under);
        if (state.is(com.elysium.transit.registry.TransitBlocks.PAVING.get())) {
            return TransitRules.Surface.ROAD;
        }
        if (state.is(com.elysium.transit.registry.TransitBlocks.GRAVEL_PATH.get())
                || state.is(Blocks.DIRT_PATH)) {
            return TransitRules.Surface.PATH;
        }
        if (state.is(BlockTags.ICE) || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK)
                || state.is(BlockTags.SAND) || state.is(Blocks.POWDER_SNOW)) {
            return TransitRules.Surface.ROUGH;
        }
        return TransitRules.Surface.GROUND;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof LivingEntity driver ? driver : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // The client moves it from the server's own updates; driving is
            // decided in one place, which is the other side.
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        drive();
    }

    /** The whole of driving, on the server. */
    private void drive() {
        LivingEntity driver = getControllingPassenger();
        float target = 0.0F;
        if (driver != null && fuel() > 0.0F) {
            // Steering, then how fast it wants to be going.
            setYRot(getYRot() - driver.xxa * TransitRules.TURN_RATE);
            setRot(getYRot(), getXRot());
            yRotO = getYRot();
            if (driver.zza > 0.0F) {
                target = TransitRules.speedOn(rung(), surface(), load());
            } else if (driver.zza < 0.0F) {
                target = -TransitRules.speedOn(rung(), surface(), load()) / 3.0F;
            }
        }
        float was = speed();
        float now = TransitRules.approach(was, target);
        if (Math.abs(now) < 0.02F) {
            now = 0.0F;
        }
        entityData.set(SPEED, now);

        double perTick = now / 20.0D;
        double radians = Math.toRadians(getYRot());
        Vec3 wanted = new Vec3(-Math.sin(radians) * perTick, 0.0D, Math.cos(radians) * perTick);
        setDeltaMovement(wanted.x, onGround() ? 0.0D : getDeltaMovement().y - 0.08D,
                wanted.z);
        double before = getX();
        double beforeZ = getZ();
        move(MoverType.SELF, getDeltaMovement());
        double went = Math.sqrt(Math.pow(getX() - before, 2) + Math.pow(getZ() - beforeZ, 2));

        if (went > 0.0D && driver != null) {
            // Billed on what it actually covered, not on what it tried to do.
            // A vehicle held against a wall burning fuel is the one way this
            // could quietly empty somebody's tank.
            float burnt = (float) (went * TransitRules.thirstOn(rung(), surface(), load()));
            setFuel(fuel() - burnt);
        }
        if (horizontalCollision && Math.abs(now) > 1.0F) {
            entityData.set(SPEED, 0.0F);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            if (!level().isClientSide) {
                player.displayClientMessage(says(), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        ItemStack held = player.getItemInHand(hand);
        int burns = held.getBurnTime(null);
        if (burns > 0 && fuel() < TransitRules.TANK) {
            if (!level().isClientSide) {
                setFuel(TransitRules.fuelled(fuel(), TransitRules.fuelFrom(burns)));
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level().playSound(null, this, SoundEvents.BUCKET_FILL, SoundSource.NEUTRAL,
                        0.6F, 1.2F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide && getPassengers().size() < rung().seats()) {
            return player.startRiding(this) ? InteractionResult.CONSUME
                    : InteractionResult.PASS;
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** What it would say if it could talk. */
    public Component says() {
        TransitRules.Surface on = surface();
        return Component.translatable("elysiumtransit.vehicle.status",
                Component.translatable("elysiumtransit.surface."
                        + on.name().toLowerCase(java.util.Locale.ROOT)),
                String.format("%.0f", fuel()),
                String.format("%.0f", TransitRules.range(rung(), fuel(), on, load())),
                String.format("%.1f", TransitRules.speedOn(rung(), on, load())));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.getEntity() instanceof Player player && !player.getAbilities().instabuild) {
            spill();
        }
        discard();
        return true;
    }

    /** Everything in it, and itself, on the floor. */
    private void spill() {
        if (!(level() instanceof ServerLevel)) {
            return;
        }
        for (int slot = 0; slot < cargo().getSlots(); slot++) {
            ItemStack stack = cargo().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                spawnAtLocation(stack);
            }
        }
        spawnAtLocation(asItem());
    }

    public ItemStack asItem() {
        return new ItemStack(TransitItems.itemFor(getType()));
    }

    @Override
    public ItemStack getPickResult() {
        return asItem();
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < rung().seats();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions,
                                               float scale) {
        int index = getPassengers().indexOf(passenger);
        return new Vec3(0.0D, 0.6D, index <= 0 ? 0.2D : -0.5D);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setFuel(tag.getFloat("Fuel"));
        cargo = new ItemStackHandler(Math.max(1, rung().slots()));
        if (tag.contains("Cargo") && level() != null) {
            cargo.deserializeNBT(level().registryAccess(), tag.getCompound("Cargo"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Fuel", fuel());
        if (level() != null) {
            tag.put("Cargo", cargo().serializeNBT(level().registryAccess()));
        }
    }
}
