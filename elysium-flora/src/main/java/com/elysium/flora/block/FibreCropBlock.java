package com.elysium.flora.block;

import com.elysium.flora.rules.FloraRules;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A crop that gives fibre, and gives more of it to somebody who has been
 * somewhere.
 *
 * <h2>Why the drops are Java and not a loot table</h2>
 *
 * Because the amount depends on who broke it. A loot table can read fortune —
 * vanilla's crops do — but it cannot read the breaker's world tier, and the
 * whole reason this crop exists at a tier scale is that a player who has
 * reached Cinderveil should not be walking the same length of field for the
 * same string as one who has never left the valley.
 *
 * <p>Everything else is vanilla's crop exactly: eight stages, farmland, light,
 * bone meal, trampling. A crop that behaved differently from wheat in any way
 * a player did not ask about would be a crop they have to learn.
 */
public class FibreCropBlock extends CropBlock {

    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, FloraRules.MATURE);

    private final Supplier<ItemLike> seed;
    private final Supplier<ItemLike> fibre;

    public FibreCropBlock(Properties properties, Supplier<ItemLike> seed,
                          Supplier<ItemLike> fibre) {
        super(properties);
        this.seed = seed;
        this.fibre = fibre;
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return FloraRules.MATURE;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return seed.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>();
        int age = state.getValue(AGE);
        if (!FloraRules.ripe(age)) {
            // Broken early: the seed back and nothing else, which is vanilla's
            // rule and the one that makes trampling a loss rather than a theft.
            drops.add(new ItemStack(seed.get()));
            return drops;
        }
        RandomSource random = params.getLevel().getRandom();
        ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        int fortune = tool == null ? 0 : fortuneOn(tool, params);

        int amount = FloraRules.harvest(tierOf(params), random.nextDouble());
        amount = FloraRules.fortuned(amount, fortune, random.nextDouble());
        drops.add(new ItemStack(fibre.get(), amount));

        // One seed back, so a field replants itself, and sometimes a second so
        // a field can grow. Fortune helps here too, on the same curve.
        int seeds = FloraRules.fortuned(1, fortune, random.nextDouble());
        drops.add(new ItemStack(seed.get(), seeds));
        return drops;
    }

    private static int tierOf(LootParams.Builder params) {
        Entity breaker = params.getOptionalParameter(LootContextParams.THIS_ENTITY);
        return breaker instanceof Player player
                ? com.elysium.lib.tier.ElysiumTiers.of(player)
                : 0;
    }

    private static int fortuneOn(ItemStack tool, LootParams.Builder params) {
        var lookup = params.getLevel().registryAccess()
                .lookup(net.minecraft.core.registries.Registries.ENCHANTMENT).orElse(null);
        if (lookup == null) {
            return 0;
        }
        return lookup.get(Enchantments.FORTUNE)
                .map(holder -> net.minecraft.world.item.enchantment.EnchantmentHelper
                        .getItemEnchantmentLevel(holder, tool))
                .orElse(0);
    }

    /** Where a crop may be planted, so the wild form and the farmed one agree. */
    @Override
    protected boolean mayPlaceOn(BlockState state, net.minecraft.world.level.BlockGetter level,
                                 BlockPos pos) {
        return state.is(net.minecraft.world.level.block.Blocks.FARMLAND);
    }
}
