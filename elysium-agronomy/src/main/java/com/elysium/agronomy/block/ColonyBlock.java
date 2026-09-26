package com.elysium.agronomy.block;

import com.elysium.agronomy.rules.CropRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A mushroom colony: a block of earth that grows mushrooms on its top.
 *
 * <h2>How it is used</h2>
 *
 * It is crafted bare. A brown or a red mushroom used on it plants it with that
 * kind, for good; after that it grows one mushroom at a time on the game's
 * random tick, up to {@link CropRules#COLONY_MOST}, and an empty hand takes
 * whatever it has grown. Its blockstate carries both what it is a colony of
 * and how many are ready, so a cellar of them reads from the door.
 *
 * <h2>What it decides for itself</h2>
 *
 * Nothing. The two kinds are {@link CropRules#MUSHROOMS}, walked rather than
 * listed, so the {@code kind} property is as wide as that list; how likely one
 * random tick is to grow a mushroom is {@link CropRules#colonyAfter}, which
 * turns the rules' {@link CropRules#COLONY_TICKS} into a chance on the game's
 * clock and is proved by the harness to average out to it. A colony that
 * advanced on every random tick would grow a mushroom every minute rather than
 * every three and a bit, and nothing in play would say so.
 */
public class ColonyBlock extends Block {

    public static final MapCodec<ColonyBlock> CODEC = simpleCodec(ColonyBlock::new);

    /** How many mushrooms are ready, from none to the most it holds. */
    public static final IntegerProperty AGE =
            IntegerProperty.create("age", 0, CropRules.COLONY_MOST);

    /** What it is a colony of: nothing yet, or one of the rules' mushrooms, by place. */
    public static final IntegerProperty KIND =
            IntegerProperty.create("kind", 0, CropRules.MUSHROOMS.size());

    public ColonyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(KIND, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, KIND);
    }

    /** The mushroom a colony of this kind grows, or null for a bare one. */
    @Nullable
    public static Item mushroomOf(int kind) {
        if (kind <= 0 || kind > CropRules.MUSHROOMS.size()) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(
                CropRules.MUSHROOMS.get(kind - 1)));
    }

    /** The kind a mushroom plants, or zero when it is not one of the rules'. */
    public static int kindOf(ItemStack stack) {
        String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return CropRules.MUSHROOMS.indexOf(name) + 1;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(KIND) > 0 && state.getValue(AGE) < CropRules.COLONY_MOST;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
                              RandomSource random) {
        if (state.getValue(KIND) <= 0) {
            return;
        }
        int age = state.getValue(AGE);
        int next = CropRules.colonyAfter(age, random.nextFloat());
        if (next != age) {
            level.setBlock(pos, state.setValue(AGE, next), Block.UPDATE_ALL);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        int kind = kindOf(held);
        if (kind <= 0 || state.getValue(KIND) > 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(KIND, kind).setValue(AGE, 0), Block.UPDATE_ALL);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Item mushroom = mushroomOf(state.getValue(KIND));
        if (mushroom == null) {
            player.displayClientMessage(
                    Component.translatable("elysiumagronomy.colony.bare"), true);
            return InteractionResult.CONSUME;
        }
        int ready = state.getValue(AGE);
        if (ready <= 0) {
            player.displayClientMessage(Component.translatable(
                    "elysiumagronomy.colony.growing", mushroom.getDescription(),
                    String.valueOf(ready), String.valueOf(CropRules.COLONY_MOST)), true);
            return InteractionResult.CONSUME;
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(mushroom, ready));
        level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_ALL);
        return InteractionResult.CONSUME;
    }
}
