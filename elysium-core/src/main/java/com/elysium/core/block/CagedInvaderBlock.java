package com.elysium.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * The Caged Invader (Apotheosis' AP-23): a fight a player chooses to start.
 *
 * <h2>Breaking it is the trigger</h2>
 *
 * Not a button, not a right-click, not a redstone pulse: the block has to be
 * destroyed, which means a player who finds one underground and wants nothing
 * to do with it can walk away, and one who mines it has decided. There is no
 * way to open it by accident and no way to open it from safety.
 */
public class CagedInvaderBlock extends Block {

    /** What is in the cage. The same short list a rogue spawner draws from. */
    public static final java.util.List<ResourceLocation> POOL = RogueSpawnerBlockEntity.POOL;

    public CagedInvaderBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(8.0F, 12.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server) {
            release(server, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Lets out what was in it, as an invader whatever the world tier says. */
    public static void release(ServerLevel level, BlockPos pos) {
        ResourceLocation id = POOL.get(level.random.nextInt(POOL.size()));
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) {
            return;
        }
        Entity spawned = type.create(level);
        if (!(spawned instanceof Mob mob)) {
            return;
        }
        mob.moveTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.SPAWNER, null);
        // An invader regardless of tier: the cage is the reason it is one, and
        // a cage that let out an ordinary zombie because the finder was early
        // in the game would be a disappointment rather than a decision.
        com.elysium.lib.elite.ElysiumElites.release(mob,
                Math.max(1, com.elysium.lib.tier.ElysiumTierMobs.tierAround(level, mob)));
        level.addFreshEntity(mob);
        level.playSound(null, pos, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.0F, 0.8F);
    }
}
