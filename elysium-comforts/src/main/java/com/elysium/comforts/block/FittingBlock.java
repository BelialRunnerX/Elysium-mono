package com.elysium.comforts.block;

import com.elysium.comforts.rules.LightRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One fitting: something you put in a room that changes how dark the room is.
 *
 * <p>The block holds its name and nothing else. Everything a player can
 * measure about it — how much light it gives, what it costs — is in
 * {@link LightRules} and is read back out through {@link #fitting()}, because
 * a light level typed into a registration is a number no rule can see and no
 * validator can check, and the failure it causes is a room that is quietly
 * two levels brighter than the bestiary was written for.
 *
 * <p>The shape is the one thing decided here, because a shape is not a claim
 * about anything: it is what the block looks like to stand next to.
 */
public class FittingBlock extends Block {

    /** Standing on a surface, holding candles. */
    private static final VoxelShape HELD_SHAPE = box(5.0D, 0.0D, 5.0D, 11.0D, 7.0D, 11.0D);

    /** Hanging, or set down where it would hang. */
    private static final VoxelShape HUNG_SHAPE = box(5.0D, 3.0D, 5.0D, 11.0D, 15.0D, 11.0D);

    /** A fire you sit round rather than one you fall in. */
    private static final VoxelShape HEARTH_SHAPE = box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);

    /** Flat enough to walk over without noticing. */
    private static final VoxelShape UNDERFOOT_SHAPE = box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    private final String name;

    public FittingBlock(Properties properties, String name) {
        super(properties);
        this.name = name;
    }

    /** What this block is called. */
    public String fittingName() {
        return this.name;
    }

    /**
     * Its row, or null if somebody registered a block the rules do not have.
     *
     * <p>Null rather than a thrown exception, because the thing that reads this
     * is a shape lookup on every frame and a crash there is a crash in the
     * renderer. {@code validate.py} is what makes it impossible instead.
     */
    public LightRules.Fitting fitting() {
        return LightRules.fitting(this.name);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        LightRules.Fitting row = fitting();
        if (row == null) {
            return Shapes.block();
        }
        return switch (row.kind()) {
            case LightRules.HELD -> HELD_SHAPE;
            case LightRules.HUNG -> HUNG_SHAPE;
            case LightRules.HEARTH -> HEARTH_SHAPE;
            case LightRules.UNDERFOOT -> UNDERFOOT_SHAPE;
            default -> Shapes.block();
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level,
                                        BlockPos pos, CollisionContext context) {
        LightRules.Fitting row = fitting();
        if (row != null && LightRules.UNDERFOOT.equals(row.kind())) {
            return Shapes.empty();
        }
        return getShape(state, level, pos, context);
    }
}
