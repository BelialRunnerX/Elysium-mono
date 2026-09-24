package com.elysium.core.block;

import com.elysium.core.Elysium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * What a rogue spawner is counting, and what it sends.
 *
 * <h2>Whose creatures</h2>
 *
 * Vanilla's, chosen from a short list. Not this mod's own: a spawner that sent
 * Elysium creatures would be a spawner that does nothing in a pack running the
 * library without the content, and the interesting thing about these was never
 * which mob it is — it is how many and how promoted. The world tier decides
 * that, through the same promotion every naturally spawned creature goes
 * through.
 */
public class RogueSpawnerBlockEntity extends BlockEntity {

    /** How hard it sends them. */
    public enum Mode {
        /** Few, and each one promoted on purpose. */
        BRUTAL(2, 4, 200, 24.0D),
        /** Many, and none of them special. */
        SWARM(6, 6, 80, 20.0D);

        public final int perWave;
        public final int waves;
        public final int interval;
        public final double range;

        Mode(int perWave, int waves, int interval, double range) {
            this.perWave = perWave;
            this.waves = waves;
            this.interval = interval;
            this.range = range;
        }
    }

    /**
     * What it can send.
     *
     * Vanilla monsters that exist in every pack. A list rather than a tag
     * because a tag of "monsters" includes things that cannot path, cannot be
     * fought indoors, or explode on arrival, and a spawner that rolled a ghast
     * in a cave would be a joke rather than a fight.
     */
    public static final List<ResourceLocation> POOL = List.of(
            ResourceLocation.withDefaultNamespace("zombie"),
            ResourceLocation.withDefaultNamespace("skeleton"),
            ResourceLocation.withDefaultNamespace("spider"),
            ResourceLocation.withDefaultNamespace("husk"),
            ResourceLocation.withDefaultNamespace("stray"));

    private final Mode mode;
    private int wavesLeft;
    private int cooldown;

    public RogueSpawnerBlockEntity(BlockPos pos, BlockState state, Mode mode) {
        super(Elysium.ROGUE_SPAWNER_ENTITY.get(), pos, state);
        this.mode = mode;
        this.wavesLeft = mode.waves;
    }

    public RogueSpawnerBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, Mode.BRUTAL);
    }

    public Mode mode() {
        return mode;
    }

    public int wavesLeft() {
        return wavesLeft;
    }

    public static void tick(Level level, BlockPos pos, BlockState state,
                            RogueSpawnerBlockEntity spawner) {
        if (!(level instanceof ServerLevel server) || spawner.wavesLeft <= 0) {
            return;
        }
        if (spawner.cooldown > 0) {
            spawner.cooldown--;
            return;
        }
        Player nearest = level.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D, spawner.mode.range, false);
        if (nearest == null) {
            return;                         // nobody to send them at
        }
        for (int sent = 0; sent < spawner.mode.perWave; sent++) {
            spawner.send(server, pos);
        }
        spawner.cooldown = spawner.mode.interval;
        spawner.wavesLeft--;
        spawner.setChanged();
        if (spawner.wavesLeft <= 0) {
            // Spent. It breaks rather than lingering as scenery, and the break
            // is what drops what it was guarding.
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS,
                    0.8F, 0.6F);
            level.destroyBlock(pos, true);
        }
    }

    private void send(ServerLevel level, BlockPos pos) {
        ResourceLocation id = POOL.get(level.random.nextInt(POOL.size()));
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) {
            return;
        }
        BlockPos where = pos.offset(level.random.nextInt(5) - 2, 0, level.random.nextInt(5) - 2);
        if (!level.getBlockState(where).isAir() || !level.getBlockState(where.above()).isAir()) {
            return;                         // nowhere to put it
        }
        Entity spawned = type.create(level);
        if (!(spawned instanceof Mob mob)) {
            return;
        }
        mob.moveTo(where.getX() + 0.5D, where.getY(), where.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(where), MobSpawnType.SPAWNER,
                null);
        if (mode == Mode.BRUTAL) {
            // The brutal one promotes on purpose rather than waiting for the
            // world tier's roll: that is the whole difference between the two.
            com.elysium.lib.elite.ElysiumElites.promote(mob,
                    Math.max(1, com.elysium.lib.tier.ElysiumTierMobs.tierAround(level, mob)));
        }
        level.addFreshEntity(mob);
        level.levelEvent(2004, where, 0);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        wavesLeft = tag.contains("waves") ? tag.getInt("waves") : mode.waves;
        cooldown = tag.getInt("cooldown");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("waves", wavesLeft);
        tag.putInt("cooldown", cooldown);
    }
}
