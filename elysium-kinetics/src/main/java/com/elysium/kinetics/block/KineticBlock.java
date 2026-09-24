package com.elysium.kinetics.block;

import com.elysium.kinetics.rules.KineticRules;
import com.elysium.kinetics.rules.LineRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * What every block on a line has in common, on the block side.
 *
 * <h2>Everything has an axis</h2>
 *
 * Including the machines. A millstone turns about the shaft under it and a
 * water wheel about the pin through its middle, and giving them all the same
 * property means {@link KineticLine} can ask one question — "do you two turn
 * about the same axis" — instead of knowing what each of them is.
 *
 * <h2>Disturbing, not solving</h2>
 *
 * Placing or breaking anything tells its neighbours the line changed; it does
 * not work the line out. A player placing a row of shafts places several in a
 * second, and solving on each placement would walk the growing line once per
 * block. The next tick solves it once.
 */
public abstract class KineticBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    protected KineticBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The face you clicked is the way the shaft runs, which is what a
        // player expects from every pillar-shaped block in the game.
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        KineticLine.disturb(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            KineticLine.disturb(level, pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        KineticLine.disturb(level, pos);
    }

    /**
     * Prodding any part of a line says what the whole line is doing.
     *
     * <h2>Why this is on the block and not on an item</h2>
     *
     * Create puts this behind a pair of goggles, and the goggles are an
     * excellent item that a player does not have on the day they place their
     * first water wheel and cannot work out why nothing is grinding. The
     * information the model *needs* a player to have — it is turning, it is
     * turning too slowly, it has stalled, two gear paths disagree — is the
     * information that makes the first hour comprehensible, so it costs
     * nothing and works on every block.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(reading(level, pos), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * What the line through this block is doing, in one sentence.
     *
     * The order matters: jammed and sheared are faults and come first, because
     * a jammed line also reads as stopped and being told it is stopped would
     * send a player looking for a source they already have.
     */
    public static Component reading(Level level, BlockPos pos) {
        LineRules.Assembly assembly = KineticLine.solve(level, pos);
        if (assembly.jammed()) {
            return Component.translatable("elysiumkinetics.line.jammed");
        }
        KineticNode node = KineticLine.nodeAt(level, pos);
        float here = node instanceof KineticBlockEntity entity ? entity.rpm() : 0.0F;
        if (assembly.root().sheared()) {
            return Component.translatable("elysiumkinetics.line.sheared",
                    String.format("%.0f", Math.abs(assembly.root().rpm())),
                    String.format("%.0f", weakest(level, pos).rating()));
        }
        if (assembly.root().stalled()) {
            return Component.translatable("elysiumkinetics.line.stalled");
        }
        if (!assembly.running()) {
            return Component.translatable("elysiumkinetics.line.still");
        }
        // The case the effort gauge cannot report: a line turning comfortably
        // below its capacity with every machine on it stopped for want of
        // speed. Without this the reading says "12 rpm, 25%" while nothing
        // works, which is true and useless.
        float short_ = shortfall(level, pos, assembly);
        if (short_ > 0.0F) {
            return Component.translatable("elysiumkinetics.line.waiting",
                    String.format("%.1f", short_));
        }
        return Component.translatable("elysiumkinetics.line.reading",
                String.format("%.1f", Math.abs(here)),
                String.format("%.0f%%", assembly.root().effort() * 100.0F));
    }

    /**
     * How many rpm short the fussiest stopped machine on this line is.
     *
     * Each machine is judged at its own speed rather than the root's, so a saw
     * behind a step-up is not reported as too slow while it is happily
     * cutting.
     */
    private static float shortfall(Level level, BlockPos pos, LineRules.Assembly assembly) {
        float worst = 0.0F;
        for (KineticLine.Member member : KineticLine.walk(level, pos).members()) {
            KineticNode node = KineticLine.nodeAt(level, member.pos());
            if (node == null) {
                continue;
            }
            KineticRules.Load load = node.load();
            if (load == null) {
                continue;
            }
            float speed = Math.abs(assembly.speedOf(member.at()));
            if (!load.working(speed)) {
                worst = Math.max(worst, load.leastRpm() - speed);
            }
        }
        return worst;
    }

    /** The weakest material on the line through this block. */
    private static KineticRules.Shaft weakest(Level level, BlockPos pos) {
        KineticRules.Shaft found = KineticRules.Shaft.ASCENDANT;
        for (KineticLine.Member member : KineticLine.walk(level, pos).members()) {
            KineticNode node = KineticLine.nodeAt(level, member.pos());
            if (node != null && node.material().rating() < found.rating()) {
                found = node.material();
            }
        }
        return found;
    }

    /**
     * The ticker every kinetic block shares.
     *
     * Both sides: the client needs one to keep its own copy of the speed for
     * the animation, and to notice when a chunk arrives with the line already
     * turning.
     */
    @Nullable
    protected static <T extends net.minecraft.world.level.block.entity.BlockEntity>
            BlockEntityTicker<T> ticker(BlockEntityType<T> given, BlockEntityType<?> wanted) {
        return given == wanted
                ? (level, pos, state, entity) ->
                        KineticBlockEntity.tick(level, pos, state, (KineticBlockEntity) entity)
                : null;
    }

    /** Subclasses give their own codec; none of them carry extra fields. */
    @Override
    protected abstract MapCodec<? extends KineticBlock> codec();
}
