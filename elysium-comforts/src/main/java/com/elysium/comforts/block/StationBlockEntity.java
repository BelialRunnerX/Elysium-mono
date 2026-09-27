package com.elysium.comforts.block;

import com.elysium.comforts.item.TraderItem;
import com.elysium.comforts.registry.ComfortBlockEntities;
import com.elysium.comforts.registry.ComfortComponents;
import com.elysium.comforts.registry.ComfortItems;
import com.elysium.comforts.rules.StationRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one villager a station is holding, and nothing else.
 *
 * <h2>Why it holds the saved form rather than the villager</h2>
 *
 * Because that is what {@link TraderItem} already does, and the two have to
 * agree exactly: a villager carried in a pocket and a villager standing in a
 * trading station are the same villager with the same ledger and the same
 * restock clock, or picking one up and putting it in a block is a way of
 * laundering it into a fresh one. The tag goes in and comes out untouched; the
 * clock is never read and written back, because a clock that is read and
 * written back is a clock somebody will one day "helpfully" refresh.
 */
public class StationBlockEntity extends BlockEntity {

    /** Where the saved villager lives in this block's own data. */
    public static final String HELD = "ElysiumHeld";

    /** And how many goes it has had, for the jobs that count them. */
    public static final String GOES = "ElysiumGoes";

    private CompoundTag held = new CompoundTag();
    private int goes;

    public StationBlockEntity(BlockPos pos, BlockState state) {
        super(ComfortBlockEntities.STATION.get(), pos, state);
    }

    /** Whether there is nobody in it. */
    public boolean empty() {
        return held.isEmpty();
    }

    /** How many goes it has had. */
    public int goes() {
        return goes;
    }

    /**
     * Take the villager out of this stack, if it has one.
     *
     * @return whether it did
     */
    public boolean take(ItemStack from) {
        if (!empty() || !(from.getItem() instanceof TraderItem)
                || !TraderItem.occupied(from)) {
            return false;
        }
        CompoundTag written = from.get(ComfortComponents.TRADER.get());
        if (written == null || written.isEmpty()) {
            return false;
        }
        held = written.copy();
        from.remove(ComfortComponents.TRADER.get());
        setChanged();
        return true;
    }

    /**
     * Hand the villager back, in the crate it came in.
     *
     * <p>Empties this block in the same breath, so there is never a moment
     * when the villager is in two places. Every mod that has ever duplicated
     * something did it by writing the copy before deleting the original.
     */
    public ItemStack handBack() {
        ItemStack back = new ItemStack(ComfortItems.TRADER.get());
        if (!held.isEmpty()) {
            back.set(ComfortComponents.TRADER.get(), held.copy());
        }
        held = new CompoundTag();
        setChanged();
        return back;
    }

    /**
     * One go of whatever this station does.
     *
     * <p>How long a go takes and what it hands back are the station's own row
     * and are not decided here — this counts the goes and leaves the numbers
     * to {@link StationRules}. A station with nobody in it does nothing, which
     * is the difference between a station and a machine.
     */
    public void aGoAt(ServerLevel level, BlockPos pos, BlockState state,
                      StationRules.Station station) {
        if (empty()) {
            return;
        }
        goes += station.gives();
        setChanged();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        held = tag.getCompound(HELD).copy();
        goes = tag.getInt(GOES);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.put(HELD, held.copy());
        tag.putInt(GOES, goes);
    }
}
