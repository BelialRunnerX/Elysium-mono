package com.elysium.arsenal.block;

import com.elysium.arsenal.rules.TotemRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A totem you set down and leave.
 *
 * <h2>Why it is allowed to do so little</h2>
 *
 * Because it never stops. A placed totem costs nothing to keep running, so
 * whatever it does in a minute it does for the rest of the world's life, and
 * {@link TotemRules#MOST_PER_MINUTE} is the only thing between "helpful
 * ornament" and "win button". Both totems here sit under it by construction:
 * the rules refuse one that does not.
 *
 * <h2>Why it ticks rather than being ticked</h2>
 *
 * A scheduled tick that reschedules itself is how a block does something
 * periodically without every block in the world being asked every tick. The
 * period is the rules' own, so retuning the roster retunes the block.
 */
public class TotemBlock extends Block {

    private final TotemRules.Totem totem;

    public TotemBlock(TotemRules.Totem totem, Properties properties) {
        super(properties);
        this.totem = totem;
    }

    /** Which totem this is. */
    public TotemRules.Totem totem() {
        return totem;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState was,
                        boolean moving) {
        super.onPlace(state, level, pos, was, moving);
        level.scheduleTick(pos, this, totem.every());
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos,
                     RandomSource random) {
        level.scheduleTick(pos, this, totem.every());
        switch (totem.name()) {
            case "growth_totem" -> grow(level, pos, random);
            case "radiance_totem" -> shine(level, pos);
            default -> { }
        }
    }

    /**
     * Push one growable block along.
     *
     * <p>One, chosen at random inside the reach, rather than all of them: a
     * totem that grew every crop in its radius every time it ticked would do
     * its radius cubed of work per tick and would also finish a farm in one
     * go, which is the difference between speeding a farm up and replacing it.
     */
    private void grow(ServerLevel level, BlockPos middle, RandomSource random) {
        int reach = totem.reach();
        BlockPos at = middle.offset(
                random.nextInt(reach * 2 + 1) - reach,
                random.nextInt(3) - 1,
                random.nextInt(reach * 2 + 1) - reach);
        BlockState state = level.getBlockState(at);
        if (!(state.getBlock() instanceof BonemealableBlock growable)) {
            return;
        }
        if (!growable.isValidBonemealTarget(level, at, state)) {
            return;
        }
        if (growable.isBonemealSuccess(level, random, at, state)) {
            growable.performBonemeal(level, random, at, state);
        }
    }

    /** Light the room, and make it uncomfortable for what is in it. */
    private void shine(ServerLevel level, BlockPos middle) {
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(middle).inflate(totem.reach()));
        for (LivingEntity living : near) {
            if (!(living instanceof Enemy)) {
                continue;
            }
            living.hurt(level.damageSources().magic(), totem.power());
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING,
                    totem.every() * 2, 0, true, false));
        }
    }
}
