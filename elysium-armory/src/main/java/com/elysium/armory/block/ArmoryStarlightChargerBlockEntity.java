package com.elysium.armory.block;

import com.elysium.armory.craft.ArmoryGrading;
import com.elysium.armory.property.ArmoryMaterialScience;
import com.elysium.armory.registry.ArmoryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the Starlight Charger is holding, and how much of the night it has had.
 *
 * <p>The count only rises when the sky is out and the sun is not: a charger
 * indoors, or one working through the afternoon, makes no progress at all. The
 * progress is kept rather than reset, so a part left out over several nights
 * charges in the end — it is a wait, not a window.
 */
public class ArmoryStarlightChargerBlockEntity extends BlockEntity {

    private ItemStack held = ItemStack.EMPTY;
    private int charged;

    public ArmoryStarlightChargerBlockEntity(BlockPos pos, BlockState state) {
        super(ArmoryBlockEntities.STARLIGHT_CHARGER.get(), pos, state);
    }

    public ItemStack held() {
        return held;
    }

    /** How far through the wait this part is, 0 to 1. */
    public float progress() {
        return Math.min(1.0F, (float) charged / ArmoryMaterialScience.CHARGE_TICKS);
    }

    public void put(ItemStack stack) {
        held = stack;
        charged = 0;
        setChanged();
    }

    /** Takes what is on it, charged if the wait is over. */
    public ItemStack take() {
        ItemStack out = held;
        if (!out.isEmpty() && charged >= ArmoryMaterialScience.CHARGE_TICKS) {
            ItemStack marked = ArmoryGrading.charge(out);
            if (!marked.isEmpty()) {
                out = marked;
            }
        }
        held = ItemStack.EMPTY;
        charged = 0;
        setChanged();
        return out;
    }

    public static void tick(Level level, BlockPos pos, BlockState state,
                            ArmoryStarlightChargerBlockEntity charger) {
        if (charger.held.isEmpty() || charger.charged >= ArmoryMaterialScience.CHARGE_TICKS) {
            return;
        }
        if (!level.canSeeSky(pos.above()) || level.isDay()) {
            return;                         // no sky, or no stars in it
        }
        charger.charged++;
        if (charger.charged >= ArmoryMaterialScience.CHARGE_TICKS) {
            charger.setChanged();
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        charged = tag.getInt("charged");
        held = tag.contains("held")
                ? ItemStack.parse(registries, tag.getCompound("held")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("charged", charged);
        if (!held.isEmpty()) {
            tag.put("held", held.save(registries));
        }
    }
}
