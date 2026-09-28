package com.elysium.kinetics.block;

import com.elysium.kinetics.rules.KineticRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.elysium.lib.sound.ElysiumVoices;

/**
 * What every block on a line has in common.
 *
 * <h2>Solving once, not once per block</h2>
 *
 * A line is worked out by one member walking the whole thing and telling
 * everyone else. So a block that has been disturbed does not solve; it sets a
 * flag, and on the next tick <em>whichever</em> flagged block ticks first
 * solves for all of them and clears the rest. A hundred shafts in a row are
 * one walk per change, not a hundred.
 *
 * <p>The consequence worth stating: {@link #driven} is called on every member
 * every time the line is solved, including with a stopped reading. Nothing has
 * to notice that it has stopped being told, which is the failure that leaves a
 * millstone grinding after its water wheel has been mined.
 *
 * <h2>What is saved</h2>
 *
 * Nothing about the line. Speed is worked out from what is in the world, and a
 * saved speed is a speed that can be wrong the moment a chunk loads with half
 * the line still unloaded. On load a block is simply unsettled, and the first
 * tick sorts it out.
 */
public abstract class KineticBlockEntity extends BlockEntity implements KineticNode {

    /** This block's own speed, signed. Client-side too, for the animation. */
    private float rpm;

    /** Whether the line as a whole is turning and intact. */
    private boolean running;

    /** Set when something changed; cleared when the line has been solved. */
    private boolean unsettled = true;

    protected KineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ------------------------------------------------------------------
    // KineticNode
    // ------------------------------------------------------------------

    @Override
    public Direction.Axis axis() {
        return Direction.Axis.Y;
    }

    @Override
    public KineticRules.Shaft material() {
        return KineticRules.Shaft.TIMBER;
    }

    @Override
    public void driven(float speed, boolean lineRunning) {
        boolean changed = this.running != lineRunning
                || Math.abs(this.rpm - speed) > KineticRules.STOPPED;
        this.rpm = speed;
        this.running = lineRunning;
        this.unsettled = false;
        if (changed) {
            told();
            if (level != null && !level.isClientSide) {
                setChanged();
                // Clients only. The default flags notify neighbours as well,
                // and a neighbour update disturbs the line, which re-solves,
                // which can change a speed, which sends another update: a
                // whole machine hall re-walking itself every tick because it
                // told somebody it had sped up.
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void unsettle() {
        this.unsettled = true;
    }

    /** This block's speed, signed. Zero when the line is not running. */
    public float rpm() {
        return running ? rpm : 0.0F;
    }

    /** Whether the line this block is on is turning and intact. */
    public boolean running() {
        return running;
    }

    /** Called when the reading changed. Override to react; the default does nothing. */
    protected void told() {
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    /**
     * The shared tick: solve if anybody on this line asked for it, then do
     * whatever this particular block does with the answer.
     */
    public static void tick(Level level, BlockPos pos, BlockState state, KineticBlockEntity be) {
        if (level.isClientSide) {
            // A client holds whatever chunks it has been sent, which may be
            // half a line, so anything it worked out for itself would be
            // wrong and would overwrite the speed the server told it. It
            // takes the number and draws the shaft turning.
            be.unsettled = false;
            return;
        }
        if (be.unsettled) {
            KineticLine.solve(level, pos);
        }
        be.serverTick(level, pos, state);
        if (be.running && be.hum() != null) {
            ElysiumVoices.hum(level, pos, be.hum());
        }
    }

    /** What this block sounds like while the line turns, or null for a shaft that says nothing. */
    protected String hum() {
        return null;
    }

    /** What this block does each tick once it knows how fast it is turning. */
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            KineticLine.disturb(level, getBlockPos());
        }
    }

    // ------------------------------------------------------------------
    // Saving and syncing
    // ------------------------------------------------------------------
    //
    // The speed is synced but not saved: the client cannot work out the line
    // for itself (it may not have the chunks) and needs the number to draw the
    // shaft turning, while the server works it out from the world every time.

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.rpm = tag.getFloat("rpm");
        this.running = tag.getBoolean("running");
        this.unsettled = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("rpm", rpm);
        tag.putBoolean("running", running);
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
