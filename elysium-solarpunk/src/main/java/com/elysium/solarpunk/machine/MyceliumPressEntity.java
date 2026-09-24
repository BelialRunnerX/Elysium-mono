package com.elysium.solarpunk.machine;

import com.elysium.solarpunk.recipe.SolarpunkProcessRecipe;
import com.elysium.solarpunk.recipe.SolarpunkRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Presses plant matter and mycelium into panels and fibre.
 *
 * All of the behaviour is in {@link ProcessingMachineEntity}; what makes this
 * a distinct machine to a player is which recipe book it reads, and that is the
 * only thing stated here. If this class ever grows a second method, the shape
 * has stopped being shared and the split is worth revisiting rather than
 * papering over.
 */
public class MyceliumPressEntity extends ProcessingMachineEntity {

    public static final int CAPACITY = 16_000;
    public static final int MAX_RECEIVE = 200;

    public MyceliumPressEntity(BlockPos pos, BlockState state) {
        super(SolarpunkMachines.MYCELIUM_PRESS_ENTITY.get(),
                pos, state, CAPACITY, MAX_RECEIVE);
    }

    @Override
    protected SolarpunkProcessRecipe.Kind kind() {
        return SolarpunkRecipes.PRESSING;
    }
}
