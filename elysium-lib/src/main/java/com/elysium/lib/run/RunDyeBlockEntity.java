package com.elysium.lib.run;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity a run has when it needs nothing else: it remembers the
 * dye and tells the client. A bus, a coolant channel and a power conduit use
 * it; a pipe or a wire, which already has a block entity of its own, carries
 * {@link RunDyed} itself.
 */
public class RunDyeBlockEntity extends BlockEntity implements RunDyed {

    private String dye = RunRules.UNDYED;

    public RunDyeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public String runDye() {
        return dye;
    }

    @Override
    public void setRunDye(String dye) {
        this.dye = RunRules.isDye(dye) ? dye : RunRules.UNDYED;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        Runs.save(tag, dye);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String before = dye;
        dye = Runs.load(tag);
        // A chunk loads its block entities before they have a level; a /data
        // merge or a structure's block entity data arrives with one. Only the
        // second kind is a change the neighbours have to hear about.
        if (level != null && !level.isClientSide && !before.equals(dye)) {
            Runs.relinkAround(level, worldPosition);
        }
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
