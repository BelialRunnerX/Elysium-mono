package com.elysium.space.entity;

import com.elysium.space.network.SpacePayloads;
import com.elysium.space.registry.SpaceItems;
import com.elysium.space.world.SpaceArrival;
import com.elysium.space.world.SpaceRoute;
import com.elysium.space.world.SpaceWorld;
import com.elysium.space.world.SpaceWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * A tier-one rocket (Stellaris's RocketEntity, rebuilt).
 *
 * <p>Fuel it with canisters, sit in it, and press the Launch key (G by
 * default). The server decides and flies it; the pilot's client only asks. It counts down for ten seconds (Stellaris's 200 ticks),
 * climbs, and above the world asks its pilot where to go, offering only what
 * the fuel reaches ({@link SpaceRoute}). It then arrives high over the same
 * spot in the chosen world and lands. Nobody chooses within a minute: it
 * lands where it is.
 *
 * <p>Everything the client could lie about is checked on the server: the
 * pilot must be riding this rocket while it is waiting, and the destination
 * must be reachable on the fuel the server holds.
 */
public class RocketEntity extends Entity {

    public enum Phase { IDLE, COUNTDOWN, ASCENT, AWAITING, LANDING }

    public static final int COUNTDOWN_TICKS = 200;
    public static final int ASCENT_TICKS = 140;
    public static final int WAIT_TICKS = 1200;
    public static final int CANISTER_MB = 250;
    public static final double MAX_CLIMB = 0.8D;
    public static final double LANDING_SPEED = 0.5D;
    public static final String FUEL_TAG = "elysiumspace_fuel";

    private static final EntityDataAccessor<Integer> FUEL =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TANK =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOTOR =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);

    private int timer;

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FUEL, 0);
        builder.define(TANK, SpaceRoute.BASIC_TANK_MB);
        builder.define(MOTOR, SpaceRoute.Fuel.FUEL.ordinal());
        builder.define(PHASE, Phase.IDLE.ordinal());
    }

    public int fuel() {
        return entityData.get(FUEL);
    }

    public void setFuel(int mb) {
        entityData.set(FUEL, Math.max(0, Math.min(tank(), mb)));
    }

    /** Tank size in mB: basic, or large once the Large Fuel Tank is fitted. */
    public int tank() {
        return entityData.get(TANK);
    }

    public void setTank(int mb) {
        entityData.set(TANK, mb >= SpaceRoute.LARGE_TANK_MB ? SpaceRoute.LARGE_TANK_MB : SpaceRoute.BASIC_TANK_MB);
    }

    public static final String TANK_TAG = "elysiumspace_tank";
    public static final String MOTOR_TAG = "elysiumspace_motor";

    /** What the engine burns: fuel (the rocket engine) or hydrogen (the Hydrogen Motor). */
    public SpaceRoute.Fuel motor() {
        int i = entityData.get(MOTOR);
        return i >= 0 && i < SpaceRoute.Fuel.values().length ? SpaceRoute.Fuel.values()[i] : SpaceRoute.Fuel.FUEL;
    }

    public void setMotor(SpaceRoute.Fuel fuel) {
        entityData.set(MOTOR, fuel.ordinal());
    }

    /** How much of what this stack puts in the tank, for this rocket's motor; 0 if it does not fit the motor. */
    private int fuelIn(ItemStack held) {
        if (motor() == SpaceRoute.Fuel.FUEL) {
            if (held.is(SpaceItems.FUEL_CANISTER.get())) {
                return CANISTER_MB;
            }
            if (held.is(com.elysium.space.registry.SpaceFluids.FUEL.bucket.get())) {
                return 1000;
            }
        } else if (motor() == SpaceRoute.Fuel.HYDROGEN && held.is(SpaceItems.HYDROGEN_CANISTER.get())) {
            return CANISTER_MB;
        }
        return 0;
    }

    public Phase phase() {
        int i = entityData.get(PHASE);
        return i >= 0 && i < Phase.values().length ? Phase.values()[i] : Phase.IDLE;
    }

    public void setPhase(Phase phase) {
        entityData.set(PHASE, phase.ordinal());
        timer = 0;
    }

    /** This world's name on the route map, or null where rockets cannot fly (the Nether, the End). */
    public static String routeName(Level level) {
        SpaceWorld world = SpaceWorlds.of(level);
        if (world != null) {
            return world.path();
        }
        return level.dimension() == Level.OVERWORLD ? "overworld" : null;
    }

    // ------------------------------------------------------------------
    // Flight
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        Phase phase = phase();
        boolean server = !level().isClientSide;
        switch (phase) {
            case IDLE -> setDeltaMovement(onGround() ? Vec3.ZERO : getDeltaMovement().add(0.0D, -0.04D, 0.0D));
            case COUNTDOWN -> {
                setDeltaMovement(Vec3.ZERO);
                if (!server) {
                    smoke(2);
                } else if (getPassengers().isEmpty()) {
                    setPhase(Phase.IDLE);        // the pilot got out: stand down
                } else if (++timer >= COUNTDOWN_TICKS) {
                    setPhase(Phase.ASCENT);
                }
            }
            case ASCENT -> {
                setDeltaMovement(0.0D, Math.min(getDeltaMovement().y + 0.02D, MAX_CLIMB), 0.0D);
                if (!server) {
                    flame();
                } else if (++timer >= ASCENT_TICKS || getY() > level().getMaxBuildHeight() + 32) {
                    reachedSpace();
                }
            }
            case AWAITING -> {
                setDeltaMovement(Vec3.ZERO);
                if (server && (++timer >= WAIT_TICKS || getPassengers().isEmpty())) {
                    setPhase(Phase.LANDING);
                }
            }
            case LANDING -> {
                setDeltaMovement(0.0D, -LANDING_SPEED, 0.0D);
                if (!server) {
                    flame();
                } else if (onGround() || verticalCollisionBelow) {
                    setPhase(Phase.IDLE);
                }
            }
        }
        move(MoverType.SELF, getDeltaMovement());
    }

    private void reachedSpace() {
        setPhase(Phase.AWAITING);
        String from = routeName(level());
        if (from != null && getFirstPassenger() instanceof ServerPlayer pilot) {
            List<String> options = SpaceRoute.reachable(from, fuel(), motor());
            PacketDistributor.sendToPlayer(pilot, new SpacePayloads.OpenDestinations(from, fuel(), tank(), motor().name(), options));
        } else {
            setPhase(Phase.LANDING);
        }
    }

    /**
     * The pilot's choice, from the destination screen. An empty destination
     * means "land here". Every condition is re-checked here.
     */
    public void travelTo(ServerPlayer pilot, String destination) {
        if (phase() != Phase.AWAITING || pilot.getVehicle() != this) {
            return;
        }
        if (destination.isEmpty()) {
            setPhase(Phase.LANDING);
            return;
        }
        String from = routeName(level());
        if (from == null || !SpaceRoute.canTravel(from, destination, fuel(), motor())) {
            return;
        }
        ServerLevel target = targetLevel(pilot, destination);
        if (target == null) {
            return;
        }
        int left = fuel() - SpaceRoute.fuelFor(from, destination, motor());
        int tankSize = tank();
        SpaceRoute.Fuel engine = motor();
        // A pilot with a station lands on its pad, which every station template leaves open to the sky.
        BlockPos station = com.elysium.space.station.SpaceStations.arrival(pilot, target);
        BlockPos ground = station != null ? station : SpaceArrival.groundAt(target, getBlockX(), getBlockZ());
        double startY = Math.min(target.getMaxBuildHeight() - 8, ground.getY() + 80);
        Vec3 at = new Vec3(ground.getX() + 0.5D, startY, ground.getZ() + 0.5D);

        pilot.stopRiding();
        pilot.changeDimension(new DimensionTransition(target, at, Vec3.ZERO,
                pilot.getYRot(), pilot.getXRot(), DimensionTransition.DO_NOTHING));
        Entity moved = changeDimension(new DimensionTransition(target, at, Vec3.ZERO,
                getYRot(), getXRot(), DimensionTransition.DO_NOTHING));
        if (moved instanceof RocketEntity rocket) {
            rocket.setTank(tankSize);
            rocket.setMotor(engine);
            rocket.setFuel(left);
            rocket.setPhase(Phase.LANDING);
            pilot.startRiding(rocket, true);
        }
    }

    private static ServerLevel targetLevel(ServerPlayer pilot, String destination) {
        if (destination.equals("overworld")) {
            return pilot.server.getLevel(Level.OVERWORLD);
        }
        SpaceWorld world = SpaceWorld.named(destination);
        return world == null ? null : pilot.server.getLevel(SpaceWorlds.key(world));
    }

    private void smoke(int n) {
        for (int i = 0; i < n; i++) {
            level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX() + (random.nextDouble() - 0.5D),
                    getY() + 0.2D, getZ() + (random.nextDouble() - 0.5D), 0.0D, 0.02D, 0.0D);
        }
    }

    private void flame() {
        for (int i = 0; i < 4; i++) {
            level().addParticle(ParticleTypes.FLAME, getX() + (random.nextDouble() - 0.5D) * 0.6D,
                    getY() - 0.2D, getZ() + (random.nextDouble() - 0.5D) * 0.6D, 0.0D, -0.3D, 0.0D);
        }
        smoke(1);
    }

    // ------------------------------------------------------------------
    // Launching: the pilot asks (SpacePayloads.Launch), the rocket decides
    // ------------------------------------------------------------------

    /** Start the countdown if the asker is the pilot, the rocket is idle, and the fuel reaches somewhere. */
    public void tryLaunch(ServerPlayer asker) {
        if (phase() != Phase.IDLE || getFirstPassenger() != asker) {
            return;
        }
        String from = routeName(level());
        if (from == null) {
            asker.displayClientMessage(Component.translatable("elysiumspace.rocket.nowhere"), true);
            return;
        }
        if (SpaceRoute.reachable(from, fuel(), motor()).isEmpty()) {
            asker.displayClientMessage(Component.translatable("elysiumspace.rocket.no_fuel"), true);
            return;
        }
        setPhase(Phase.COUNTDOWN);
    }

    // ------------------------------------------------------------------
    // Using it
    // ------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        int add = fuelIn(held);
        if (add > 0) {
            if (phase() != Phase.IDLE || fuel() + add > tank()) {
                return InteractionResult.FAIL;
            }
            if (!level().isClientSide) {
                setFuel(fuel() + add);
                if (!player.getAbilities().instabuild) {
                    ItemStack empty = held.is(com.elysium.space.registry.SpaceFluids.FUEL.bucket.get())
                            ? new ItemStack(net.minecraft.world.item.Items.BUCKET) : ItemStack.EMPTY;
                    held.shrink(1);
                    if (!empty.isEmpty() && !player.addItem(empty)) {
                        player.drop(empty, false);
                    }
                }
                player.displayClientMessage(Component.translatable("elysiumspace.rocket.fuel",
                        fuel(), tank()), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (held.is(SpaceItems.HYDROGEN_MOTOR.get())) {
            // A motor change needs an empty tank: fuel and hydrogen do not share one.
            if (phase() != Phase.IDLE || motor() == SpaceRoute.Fuel.HYDROGEN || fuel() > 0) {
                if (!level().isClientSide && fuel() > 0) {
                    player.displayClientMessage(Component.translatable("elysiumspace.rocket.empty_first"), true);
                }
                return InteractionResult.FAIL;
            }
            if (!level().isClientSide) {
                setMotor(SpaceRoute.Fuel.HYDROGEN);
                held.consume(1, player);
                player.displayClientMessage(Component.translatable("elysiumspace.rocket.motor",
                        Component.translatable("elysiumspace.fuel.hydrogen")), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (held.is(SpaceItems.LARGE_FUEL_TANK.get())) {
            if (phase() != Phase.IDLE || tank() >= SpaceRoute.LARGE_TANK_MB) {
                return InteractionResult.FAIL;
            }
            if (!level().isClientSide) {
                setTank(SpaceRoute.LARGE_TANK_MB);
                held.consume(1, player);
                player.displayClientMessage(Component.translatable("elysiumspace.rocket.fuel",
                        fuel(), tank()), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (phase() != Phase.IDLE || !getPassengers().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            if (!level().isClientSide) {
                spawnAtLocation(asItem());
                discard();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide && player.startRiding(this)) {
            player.displayClientMessage(Component.translatable("elysiumspace.rocket.how",
                    Component.keybind("key.elysiumspace.launch")), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || level().isClientSide || phase() != Phase.IDLE
                || !getPassengers().isEmpty() || !(source.getEntity() instanceof Player player)) {
            return false;
        }
        if (!player.getAbilities().instabuild) {
            spawnAtLocation(asItem());
        }
        discard();
        return true;
    }

    /** The rocket as an item, carrying its fuel. */
    public ItemStack asItem() {
        ItemStack stack = new ItemStack(SpaceItems.ROCKET.get());
        if (fuel() > 0 || tank() > SpaceRoute.BASIC_TANK_MB || motor() != SpaceRoute.Fuel.FUEL) {
            CompoundTag tag = new CompoundTag();
            tag.putInt(FUEL_TAG, fuel());
            tag.putInt(TANK_TAG, tank());
            tag.putString(MOTOR_TAG, motor().name());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }

    public static SpaceRoute.Fuel parseMotor(String name) {
        try {
            return name.isEmpty() ? SpaceRoute.Fuel.FUEL : SpaceRoute.Fuel.valueOf(name);
        } catch (IllegalArgumentException e) {
            return SpaceRoute.Fuel.FUEL;
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(SpaceItems.ROCKET.get());
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
        return getPassengers().isEmpty();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0D, 1.1D, 0.0D);        // seated in the capsule
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setTank(tag.getInt("Tank"));
        setMotor(parseMotor(tag.getString("Motor")));
        setFuel(tag.getInt("Fuel"));
        try {
            entityData.set(PHASE, Phase.valueOf(tag.getString("Phase")).ordinal());
        } catch (IllegalArgumentException e) {
            entityData.set(PHASE, Phase.IDLE.ordinal());
        }
        // A rocket saved mid-flight lands when it is loaded again.
        if (phase() == Phase.COUNTDOWN || phase() == Phase.ASCENT || phase() == Phase.AWAITING) {
            entityData.set(PHASE, Phase.LANDING.ordinal());
        }
        timer = tag.getInt("Timer");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Fuel", fuel());
        tag.putInt("Tank", tank());
        tag.putString("Motor", motor().name());
        tag.putString("Phase", phase().name());
        tag.putInt("Timer", timer);
    }
}
