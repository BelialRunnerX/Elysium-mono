package com.elysium.arsenal.block;

import com.elysium.arsenal.rules.FossilRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;

/**
 * One half of a fossil, or a whole one.
 *
 * <p>Nothing happens in here. What a fossil does happens in
 * {@link com.elysium.arsenal.event.FossilEffects}, which looks for the halves
 * around a player rather than having each block push an effect outwards. A
 * block that pushed would have to tick, and twenty-one fossil blocks each
 * ticking and searching for players is a server that slows down in a room
 * somebody decorated.
 */
public class FossilBlock extends Block {

    private final FossilRules.Fossil fossil;
    private final String part;

    public FossilBlock(FossilRules.Fossil fossil, String part, Properties properties) {
        super(properties);
        this.fossil = fossil;
        this.part = part;
    }

    /** Which creature this was. */
    public FossilRules.Fossil fossil() {
        return fossil;
    }

    /** Which part of it this is. */
    public String part() {
        return part;
    }

    /** Whether this block counts as the skull half. */
    public boolean isSkull() {
        return FossilRules.SKULL.equals(part) || FossilRules.WHOLE.equals(part);
    }

    /** Whether it counts as the body half. */
    public boolean isBody() {
        return FossilRules.BODY.equals(part) || FossilRules.WHOLE.equals(part);
    }
}
