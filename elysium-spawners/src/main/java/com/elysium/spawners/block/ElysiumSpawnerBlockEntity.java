package com.elysium.spawners.block;

import com.elysium.spawners.data.SpawnerTuning;
import com.elysium.spawners.registry.SpawnerBlockEntities;
import com.elysium.spawners.registry.SpawnerComponents;
import com.elysium.spawners.rules.SpawnerRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A spawner that remembers what was done to it.
 *
 * <h2>Vanilla's spawning, this module's numbers</h2>
 *
 * The actual work — picking a spot, checking it, building the mob, the flame
 * particles, the spinning mob in the cage — is {@link BaseSpawner}, vanilla's
 * own and unmodified. What this adds is the numbers it runs on: the tuning is
 * written into the spawner's own tag and handed to it, so every behaviour a
 * player already understands is preserved exactly and only the figures change.
 *
 * <p>That is also why the tuning is pushed rather than read: {@code BaseSpawner}
 * keeps its fields private and takes them from a tag, which is the interface it
 * actually offers. Reaching past that would mean a mixin, and a mixin here
 * would mean this module owning every spawner in every other mod.
 */
public class ElysiumSpawnerBlockEntity extends BlockEntity implements Spawner {

    /**
     * The cage itself, and the reason it is a named class.
     *
     * NeoForge's position-check event hands a listener the {@link BaseSpawner}
     * and nothing else, so a spawner that wants to say "ignore the light here"
     * has to be recognisable from that object alone. An anonymous subclass
     * cannot be; this one can, and it knows which block entity it belongs to.
     */
    public final class Cage extends BaseSpawner {

        @Override
        public void broadcastEvent(Level level, BlockPos pos, int event) {
            if (level != null) {
                level.blockEvent(pos, getBlockState().getBlock(), event, 0);
            }
        }

        @Override
        public void setNextSpawnData(@Nullable Level level, BlockPos pos,
                                     net.minecraft.world.level.SpawnData data) {
            super.setNextSpawnData(level, pos, data);
            if (level != null) {
                level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3);
            }
        }

        /** The spawner this cage belongs to. */
        public ElysiumSpawnerBlockEntity owner() {
            return ElysiumSpawnerBlockEntity.this;
        }
    }

    private final Cage spawner = new Cage();

    private SpawnerTuning tuning = SpawnerTuning.NONE;

    public ElysiumSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(SpawnerBlockEntities.SPAWNER.get(), pos, state);
    }

    public SpawnerTuning tuning() {
        return tuning;
    }

    public Cage spawner() {
        return spawner;
    }

    /**
     * Take a new tuning, push it into the spawner and tell everybody.
     *
     * The push is the whole trick: the numbers live in {@link SpawnerRules},
     * the tuning holds only how many steps were taken, and this is the one
     * place the two meet.
     */
    public void retune(SpawnerTuning updated) {
        this.tuning = updated;
        apply();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    private void apply() {
        CompoundTag tag = spawner.save(new CompoundTag());
        tag.putShort("MinSpawnDelay", (short) SpawnerRules.minDelay(tuning.delaySteps()));
        tag.putShort("MaxSpawnDelay", (short) SpawnerRules.maxDelay(tuning.delaySteps()));
        tag.putShort("SpawnCount", (short) SpawnerRules.spawnCount(tuning.countSteps()));
        tag.putShort("MaxNearbyEntities", (short) SpawnerRules.maxNearby(tuning.nearbySteps()));
        tag.putShort("RequiredPlayerRange", (short) requiredRange());
        tag.putShort("SpawnRange", (short) SpawnerRules.spawnRange(tuning.spreadSteps()));
        if (level != null) {
            spawner.load(level, getBlockPos(), tag);
        }
    }

    /**
     * How far a player may be.
     *
     * A spawner told to ignore players is given a range large enough that one
     * is always in it, rather than a flag {@code BaseSpawner} has no field for.
     * The number is what vanilla itself uses for "effectively always": a
     * chunk's worth in every direction, which is as far as a spawner can act
     * anyway.
     */
    private int requiredRange() {
        return tuning.ignoresPlayers() ? SpawnerRules.ALWAYS_AWAKE
                : SpawnerRules.playerRange(tuning.rangeSteps());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ElysiumSpawnerBlockEntity entity) {
        if (entity.tuning.redstone() && !level.hasNeighborSignal(pos)) {
            // Told to wait for a signal, and there is none. Not a pause: the
            // spawner simply is not ticked, so its own countdown does not move
            // and a player can leave one switched off for a week.
            return;
        }
        if (level instanceof ServerLevel server) {
            entity.spawner.serverTick(server, pos);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  ElysiumSpawnerBlockEntity entity) {
        entity.spawner.clientTick(level, pos);
    }

    @Override
    public void setEntityId(EntityType<?> type, RandomSource random) {
        spawner.setEntityId(type, level, random, getBlockPos());
        setChanged();
    }

    // ------------------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        spawner.load(level, getBlockPos(), tag);
        tuning = tag.contains("tuning")
                ? SpawnerTuning.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,
                        tag.get("tuning")).result().orElse(SpawnerTuning.NONE)
                : SpawnerTuning.NONE;
        apply();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        spawner.save(tag);
        SpawnerTuning.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, tuning)
                .result().ifPresent(written -> tag.put("tuning", written));
    }

    /** What the item made from this block carries. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(SpawnerComponents.TUNING.get(), tuning);
    }

    /** And what a placed item puts back. */
    @Override
    protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        tuning = components.getOrDefault(SpawnerComponents.TUNING.get(), SpawnerTuning.NONE);
        apply();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        // The spawn potentials are large and the client never reads them; the
        // rest of the tag is what draws the spinning mob in the cage.
        tag.remove("SpawnPotentials");
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public boolean triggerEvent(int id, int value) {
        return level != null && spawner.onEventTriggered(level, id) || super.triggerEvent(id, value);
    }
}
