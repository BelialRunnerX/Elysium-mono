package com.elysium.spawners.block;

import com.elysium.spawners.data.SpawnerModifiers;
import com.elysium.spawners.data.SpawnerTuning;
import com.elysium.spawners.registry.SpawnerBlockEntities;
import com.elysium.spawners.rules.SpawnerRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The spawner a player can own.
 *
 * <h2>A block beside vanilla's, not instead of it</h2>
 *
 * Vanilla's spawner is named by structures, advancements, loot tables, the
 * {@code /setblock} command and a decade of other mods. This one stands beside
 * it, and vanilla's turns into this one the moment somebody mines it with silk
 * touch — which is the only time a player was ever going to care.
 *
 * <h2>Tuning is a right-click</h2>
 *
 * No menu. A rune in the hand and a click is the whole interface, and the cost
 * is taken from the stack. A screen would be a list of five numbers and a
 * button, and the numbers are already on the tooltip of the thing in your hand.
 */
public class ElysiumSpawnerBlock extends BaseEntityBlock {

    public static final MapCodec<ElysiumSpawnerBlock> CODEC =
            simpleCodec(properties -> new ElysiumSpawnerBlock());

    public ElysiumSpawnerBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(5.0F)
                .sound(SoundType.METAL)
                .noOcclusion()
                .requiresCorrectToolForDrops());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElysiumSpawnerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, SpawnerBlockEntities.SPAWNER.get(),
                level.isClientSide() ? ElysiumSpawnerBlockEntity::clientTick
                        : ElysiumSpawnerBlockEntity::serverTick);
    }

    /**
     * A rune in the hand, applied.
     *
     * The cost climbs with the steps already taken and comes out of the stack,
     * so a player watching their runes disappear faster each time is watching
     * the price they are paying.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player,
                                              net.minecraft.world.InteractionHand hand,
                                              BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ElysiumSpawnerBlockEntity spawner)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        SpawnerModifiers.Modifier modifier = SpawnerModifiers.get().forItem(held);
        if (modifier == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        SpawnerTuning tuning = spawner.tuning();
        if (modifier.flag() != null) {
            if (tuning.flag(modifier.flag())) {
                refuse(level, pos, player, "elysiumspawners.refused.already");
                return ItemInteractionResult.CONSUME;
            }
            if (!spend(player, held, SpawnerRules.stepCost(0))) {
                refuse(level, pos, player, "elysiumspawners.refused.cost");
                return ItemInteractionResult.CONSUME;
            }
            spawner.retune(tuning.withFlag(modifier.flag(), true));
        } else {
            int steps = tuning.steps(modifier.stat());
            if (modifier.maxSteps() > 0 && steps >= modifier.maxSteps()) {
                refuse(level, pos, player, "elysiumspawners.refused.enough");
                return ItemInteractionResult.CONSUME;
            }
            if (!spend(player, held, SpawnerRules.stepCost(steps))) {
                refuse(level, pos, player, "elysiumspawners.refused.cost");
                return ItemInteractionResult.CONSUME;
            }
            spawner.retune(tuning.stepped(modifier.stat()));
        }
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS,
                0.8F, 1.0F);
        if (player instanceof ServerPlayer server) {
            // Fired here rather than relied on: the game raises this for an
            // ordinary use, and a block that answers with CONSUME from its own
            // method is not an ordinary use. An advancement that only sometimes
            // triggers is worse than none.
            CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(server, pos, held);
        }
        return ItemInteractionResult.CONSUME;
    }

    private static boolean spend(Player player, ItemStack held, int cost) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        if (held.getCount() < cost) {
            return false;
        }
        held.shrink(cost);
        return true;
    }

    private static void refuse(Level level, BlockPos pos, Player player, String why) {
        player.displayClientMessage(Component.translatable(why), true);
        level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    /**
     * An unstable spawner going off.
     *
     * Not a punishment for mining it: an unstable spawner is placed by a
     * structure and says so on the outside, and a player who takes a pickaxe
     * to one has been told. What it drops is a loot table's business.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
                                        Player player) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof ElysiumSpawnerBlockEntity spawner
                && spawner.tuning().unstable()) {
            level.explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    SpawnerRules.UNSTABLE_POWER, Level.ExplosionInteraction.BLOCK);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos,
                                   ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        if (dropExperience) {
            // Vanilla's spawner gives 15 to 43 for being broken. Kept, because
            // the number is not the interesting part and changing it would
            // only surprise somebody.
            popExperience(level, pos, 15 + level.random.nextInt(29));
        }
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id,
                                   int value) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity != null && entity.triggerEvent(id, value);
    }
}
