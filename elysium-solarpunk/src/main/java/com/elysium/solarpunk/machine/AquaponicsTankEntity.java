package com.elysium.solarpunk.machine;

import com.elysium.solarpunk.power.PowerClass;
import com.elysium.solarpunk.recipe.SolarpunkProcessRecipe;
import com.elysium.solarpunk.recipe.SolarpunkRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Grows crops and fish together off one supply of water.
 *
 * <h2>Why it is not a second bioreactor</h2>
 *
 * Both want water and both want light, and that is where the resemblance ends.
 * The bioreactor asks for <em>sky</em> — it is an outdoor machine, and a roof
 * stops it. This asks for a <em>light level</em>, from any source. So a player
 * can put a tank in a cellar if they are willing to light it, and cannot do
 * that with a bioreactor at any price.
 *
 * That is a deliberate difference in where the two want to live, not a
 * difference in their numbers. A set of machines that all want the same site is
 * a set that produces one building; machines that want different sites produce
 * a base with rooms in it.
 *
 * <h2>Why the fish are not in the recipe</h2>
 *
 * A recipe here says one input becomes one output, and every machine that reads
 * one gets exactly that. The fish are not the seed turning into something —
 * they are what living in a tank of water produces, whatever is being grown in
 * it. Putting them in the recipe format would mean a second output column that
 * only one machine ever fills, and every recipe of every other machine carrying
 * an empty one.
 *
 * So the crop comes from the recipe and the fish come from the tank, on their
 * own counter, into their own tray. A pack that adds a new crop gets fish from
 * it for free and does not have to know that fish exist.
 */
public class AquaponicsTankEntity extends ProcessingMachineEntity {

    public static final int CAPACITY = 16_000;
    public static final int MAX_RECEIVE = 200;

    /** The tray the fish go in, beyond the recipe's own output. */
    public static final int FISH_SLOT = OUTPUT_SLOT + 1;

    /**
     * The dimmest light the tank will grow in.
     *
     * Nine, which is one above the level hostile mobs spawn at and the level
     * vanilla crops need to grow. Borrowed rather than invented: a player who
     * has lit a room well enough to farm in has lit it well enough for this,
     * and they already know what that looks like.
     */
    public static final int MIN_LIGHT = 9;

    /** How many crops are grown per fish. */
    public static final int CROPS_PER_FISH = 4;

    /** How many fish the tray holds before the tank stops. */
    public static final int FISH_STOCK = 16;

    /** Crops grown since the last fish. Not saved; see the base class. */
    private int sinceFish;

    public AquaponicsTankEntity(BlockPos pos, BlockState state) {
        super(SolarpunkMachines.AQUAPONICS_TANK_ENTITY.get(), pos, state,
                CAPACITY, MAX_RECEIVE, 1);
    }

    @Override
    protected SolarpunkProcessRecipe.Kind kind() {
        return SolarpunkRecipes.FARMING;
    }

    /** Whether this site is right, given the two facts the promise names. */
    public static boolean sited(boolean touchingWater, int light) {
        return touchingWater && light >= MIN_LIGHT;
    }

    /**
     * Critical, for the same reason as the reclaimer: this is where food comes
     * from. A tank that stops is a larder that stops, and the player finds out
     * several in-game days later when there is nothing in it.
     */
    @Override
    public PowerClass powerClass() {
        return PowerClass.CRITICAL;
    }

    @Override
    protected boolean sited() {
        return sited(SolarpunkWater.touching(level, worldPosition),
                level.getBrightness(LightLayer.BLOCK, worldPosition.above()));
    }

    /**
     * A fish every so many crops, into its own tray.
     *
     * Counted in crops rather than in ticks so that a tank fed slowly produces
     * fish slowly. Tying it to time instead would make an unfed tank sitting in
     * water a fish farm, which is a machine nobody has to supply.
     */
    @Override
    protected void onCycleComplete() {
        if (++sinceFish < CROPS_PER_FISH) {
            return;
        }
        sinceFish = 0;
        ItemStack held = getInventory().getStackInSlot(FISH_SLOT);
        if (held.isEmpty()) {
            getInventory().setStackInSlot(FISH_SLOT, new ItemStack(Items.COD));
        } else if (held.is(Items.COD) && held.getCount() < FISH_STOCK) {
            held.grow(1);
        }
        // A full fish tray does not stop the crops. The tank is a crop machine
        // that also gives fish, and stalling the whole thing because nobody
        // emptied the side tray would make the fish a chore rather than a
        // bonus.
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Saved, unlike progress, because it is not a few seconds of work: at
        // four crops to a fish a player can be most of the way to one, and
        // losing that on a chunk unload is a loss they would notice without
        // ever being able to prove.
        sinceFish = tag.getInt("since_fish");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("since_fish", sinceFish);
    }
}
