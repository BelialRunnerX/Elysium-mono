package com.elysium.industry.block;

import com.elysium.industry.rules.IndustryRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What every block on a grid has in common.
 *
 * <p>The same arrangement as Age I's, and for the same reasons: one member
 * walks the whole grid and tells everyone, so a hundred blocks of wire are one
 * walk per change rather than a hundred; the client is told the answer and
 * never works it out, because it may hold half the chunks; and nothing about
 * the grid is saved, because a saved share is a share that can be wrong the
 * moment a chunk loads.
 */
public abstract class GridBlockEntity extends BlockEntity implements GridNode {

    private float share;
    private boolean burned;
    private float supply;
    private float demand;
    private int blocks;
    private boolean unsettled = true;

    protected GridBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void fed(IndustryRules.Reading grid, int nowBlocks) {
        float nowShare = grid.share();
        boolean changed = burned != grid.burned() || blocks != nowBlocks
                || Math.abs(share - nowShare) > 0.001F
                || Math.abs(demand - grid.demand()) > 0.001F
                || Math.abs(supply - grid.supply()) > 0.001F;
        share = nowShare;
        burned = grid.burned();
        supply = grid.supply();
        demand = grid.demand();
        blocks = nowBlocks;
        unsettled = false;
        if (changed) {
            told();
            if (level != null && !level.isClientSide) {
                setChanged();
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(),
                        Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void unsettle() {
        unsettled = true;
    }

    /** The fraction of full speed this block runs at. Zero if the grid burned. */
    public float share() {
        return burned ? 0.0F : share;
    }

    /** Whether a wire on this grid was carrying more than it could. */
    public boolean burned() {
        return burned;
    }

    /** How far this block is from the nearest generator, in blocks of wire. */
    public int blocks() {
        return blocks;
    }

    /** FE per tick arriving on this grid, after the wires have taken their cut. */
    public float supply() {
        return supply;
    }

    /** FE per tick everything on this grid would like. */
    public float demand() {
        return demand;
    }

    /**
     * Whether anything on this grid wants power.
     *
     * What a generator asks before burning anything. A grid with nothing on it
     * is a grid that costs nothing to leave running, which is the only version
     * of this a player will not resent.
     */
    public boolean wanted() {
        return demand > 0.0F;
    }

    /** Called when the reading changed. Override to react. */
    protected void told() {
    }

    /** What this node asked of the grid when it was last solved, so a change re-solves it. */
    private float asked = -1.0F;

    public static void tick(Level level, BlockPos pos, BlockState state, GridBlockEntity be) {
        if (level.isClientSide) {
            be.unsettled = false;
            return;
        }
        // A machine whose want depends on what it holds (a centrifuge wants
        // nothing until a comb arrives) changes the line's demand without any
        // block changing, and nothing else would re-solve it: the turbine
        // never lit and the comb sat there (live test, 27 September 2026).
        IndustryRules.Machine machine = be.machine();
        float asking = machine == null ? 0.0F : machine.draw();
        if (asking != be.asked) {
            be.asked = asking;
            be.unsettled = true;
        }
        if (be.unsettled) {
            GridLine.solve(level, pos);
        }
        be.serverTick(level, pos, state);
    }

    protected void serverTick(Level level, BlockPos pos, BlockState state) {
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            GridLine.disturb(level, getBlockPos());
        }
    }

    @Override
    public IndustryRules.Voltage voltage() {
        return IndustryRules.Voltage.LOW;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        share = tag.getFloat("share");
        burned = tag.getBoolean("burned");
        supply = tag.getFloat("supply");
        demand = tag.getFloat("demand");
        blocks = tag.getInt("blocks");
        unsettled = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("share", share);
        tag.putBoolean("burned", burned);
        tag.putFloat("supply", supply);
        tag.putFloat("demand", demand);
        tag.putInt("blocks", blocks);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
