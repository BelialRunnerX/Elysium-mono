package com.elysium.comforts.block;

import com.elysium.comforts.rules.ChimeRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A chime, and the arithmetic that keeps a room of them quiet.
 *
 * <p>The only thing this class decides for itself is what it is made of. How
 * often it sounds is {@link ChimeRules#chanceOf}, handed how many chimes are
 * within {@link ChimeRules#EARSHOT} — which is the whole feature: the roll is
 * divided by the company it keeps, so a row of twelve is a chord and not a
 * crowd.
 */
public class ChimeBlock extends Block {

    public static final com.mojang.serialization.MapCodec<ChimeBlock> CODEC =
            simpleCodec(properties -> new ChimeBlock(properties,
                    ChimeRules.MATERIALS.get(0)));

    private static final VoxelShape SHAPE = box(4.0D, 4.0D, 4.0D, 12.0D, 16.0D, 12.0D);

    private final String material;

    public ChimeBlock(Properties properties, String material) {
        super(properties);
        this.material = material;
    }

    /** Which of the six this is. */
    public String material() {
        return material;
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    /** It hangs from something. A chime standing on the ground is a bell. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(),
                Direction.DOWN);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction,
                                     BlockState neighbour, net.minecraft.world.level.LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.UP && !canSurvive(state, level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /**
     * It wakes on its own cadence, looks at the sky, and sometimes rings.
     *
     * <p>A scheduled tick rather than a random one, because the rules say how
     * long it should wait and a random tick is whatever the game feels like.
     * Counting the neighbours happens here — once per waking, which is at
     * most {@link ChimeRules#MOST_A_SECOND} times a second for the whole room
     * however many chimes are in it, because the wait is multiplied by the
     * count. That is the same arithmetic that keeps the room quiet, doing
     * double duty as the thing that keeps it cheap.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState was, boolean moving) {
        super.onPlace(state, level, pos, was, moving);
        level.scheduleTick(pos, this, ChimeRules.waitOf(1, 1.0D));
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level,
                        BlockPos pos, RandomSource random) {
        double wind = ChimeRules.windAt(pos.getY(), level.canSeeSky(pos.above()));
        int nearby = nearby(level, pos);
        if (wind > 0.0D
                && random.nextDouble() < ChimeRules.chanceOnWaking(nearby, wind)) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, (float) (0.25D + wind * 0.35D),
                    ChimeRules.pitchOf(material));
        }
        level.scheduleTick(pos, this, ChimeRules.waitOf(nearby, wind));
    }

    /**
     * How many chimes are within earshot of this one, itself included.
     *
     * <p>Across rather than up: a row of them hangs along an eave, so the
     * count is a slab {@link ChimeRules#EARSHOT} wide and
     * {@link ChimeRules#LAYERS} deep. A cube of the same reach would be
     * thirty-five thousand lookups for the same answer.
     */
    public static int nearby(Level level, BlockPos pos) {
        int many = 0;
        int reach = ChimeRules.EARSHOT;
        int layers = ChimeRules.LAYERS;
        for (BlockPos where : BlockPos.betweenClosed(
                pos.offset(-reach, -layers, -reach),
                pos.offset(reach, layers, reach))) {
            if (level.getBlockState(where).getBlock() instanceof ChimeBlock) {
                many++;
            }
        }
        return Math.max(1, many);
    }

    /** The box a player hears it in, for anything that needs it. */
    public static AABB earshot(BlockPos pos) {
        return new AABB(pos).inflate(ChimeRules.EARSHOT, ChimeRules.LAYERS,
                ChimeRules.EARSHOT);
    }
}
