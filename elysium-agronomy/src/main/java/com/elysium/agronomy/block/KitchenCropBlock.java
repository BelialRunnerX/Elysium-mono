package com.elysium.agronomy.block;

import com.elysium.agronomy.registry.KitchenItems;
import com.elysium.agronomy.rules.CropRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One of the kitchen's crops: the game's own crop with a roster behind it.
 *
 * <p>Not a crop of our own. The game's crop is trampled, bone-mealed, grown
 * by random tick, harvested by every automation mod there is and understood
 * by every farming machine in the pack; a class of our own would have to be
 * all of that again before it was a crop rather than a block that changes
 * colour.
 *
 * <p>What is ours is <b>how fast</b>. {@link CropRules} puts every crop on a
 * frontier of time against yield and the harness proves no crop on it is as
 * good as another on every axis — and that proof is about nothing at all if
 * every crop in the world grows at wheat's speed. So the chance a random tick
 * advances one is scaled by the crop's own time against wheat's, which is the
 * one number this class is for.
 */
public class KitchenCropBlock extends CropBlock {

    public static final MapCodec<KitchenCropBlock> CODEC =
            simpleCodec(properties -> new KitchenCropBlock(properties,
                    CropRules.CROPS.get(0).id()));

    /**
     * Vanilla's own chance-per-random-tick constant.
     *
     * <p>Taken from {@code CropBlock.randomTick}, where the roll is
     * {@code random.nextInt((int) (25.0F / speed) + 1) == 0}. Restated here
     * because this class multiplies it rather than replacing it: a crop of
     * ours in a well-hydrated field still gets the benefit of the field.
     */
    public static final float VANILLA_CHANCE = 25.0F;

    private final String crop;

    public KitchenCropBlock(Properties properties, String crop) {
        super(properties);
        this.crop = crop;
    }

    /** Which crop this is. */
    public String crop() {
        return crop;
    }

    /** What the rules say about it, or null. */
    public CropRules.Crop rules() {
        return CropRules.crop(crop);
    }

    @Override
    public MapCodec<? extends CropBlock> codec() {
        return CODEC;
    }

    /**
     * How many stages it has, out of the rules.
     *
     * <p>The state itself is always vanilla's eight, because a block cannot
     * choose its own properties — they are asked for inside the constructor
     * of the class above this one, before anything here has been set. The
     * states past the last one simply never happen, and the generator writes
     * a model for each of them all the same so that a crop somebody
     * bone-meals in creative is never a purple cube.
     */
    /**
     * Farmland, or any dirt: the wild patches the world grows put a ripe crop
     * on grass, and a crop that stands only on farmland made every patch fail
     * to place (the mechanics survey, 27 September 2026). On dirt it still
     * grows, slowly, the way the game grows a crop on dry farmland.
     */
    @Override
    protected boolean mayPlaceOn(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return state.is(net.minecraft.world.level.block.Blocks.FARMLAND)
                || state.is(net.minecraft.tags.BlockTags.DIRT);
    }

    @Override
    public int getMaxAge() {
        CropRules.Crop found = rules();
        return found == null ? super.getMaxAge()
                : Math.max(1, Math.min(super.getMaxAge(), found.stages() - 1));
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return KitchenItems.SEEDS.get(crop).get();
    }

    /**
     * One random tick, at this crop's own speed.
     *
     * <p>Vanilla's roll with one more factor in it: the crop's time per stage
     * against wheat's. A crop the rules say takes twice as long takes twice
     * as long, and the frontier the roster sits on is a thing in the world
     * rather than a table in a comment.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
                              RandomSource random) {
        if (!level.isAreaLoaded(pos, 1)) {
            return;
        }
        if (level.getRawBrightness(pos, 0) < 9) {
            return;
        }
        int age = getAge(state);
        if (age >= getMaxAge()) {
            return;
        }
        float speed = getGrowthSpeed(state, level, pos);
        CropRules.Crop found = rules();
        float ours = found == null ? 1.0F
                : (float) found.ticks() / CropRules.WHEAT.ticks();
        int chance = Math.max(1, Math.round(VANILLA_CHANCE * ours / speed));
        if (random.nextInt(chance + 1) == 0) {
            level.setBlock(pos, getStateForAge(age + 1), 2);
        }
    }
}
