package com.elysium.ores.ore;

import com.elysium.ores.data.OreCatalogue;
import com.elysium.ores.rules.OreRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;

import java.util.List;

/**
 * One ore, of one material, in one stone.
 *
 * <h2>What makes it different from vanilla's</h2>
 *
 * Two things, and the second is the reason the module exists.
 *
 * <ol>
 *   <li>It does not know what it drops. The material is a {@code c:} tag and
 *       the item is whatever this pack unified that tag onto — see
 *       {@link OreDrops}.</li>
 *   <li>It pays more the further from home it is. The amount climbs with the
 *       breaker's world tier, linearly and without a cap, on a chance that
 *       approaches certainty and never arrives at it. {@link OreRules} owns
 *       that arithmetic; nothing is decided here.</li>
 * </ol>
 *
 * <h2>Silk touch</h2>
 *
 * Falls through to the loot table, which drops the block. That is the right
 * answer and it is also the only one available: an ore whose drop is resolved
 * at runtime cannot be picked up and re-placed as a different material.
 */
public class ElysiumOreBlock extends DropExperienceBlock {

    private final OreCatalogue.Entry entry;

    public ElysiumOreBlock(OreCatalogue.Entry entry, Properties properties) {
        super(experienceFor(entry.band()), properties);
        this.entry = entry;
    }

    /**
     * How much experience the band is worth.
     *
     * The commonest band pays nothing, exactly as iron and copper pay nothing:
     * experience from ore is vanilla's way of marking the ores you do not
     * smelt, and inventing a different rule would make Elysium's copper feel
     * unlike everyone else's.
     */
    private static UniformInt experienceFor(int band) {
        return switch (band) {
            case OreRules.PRECIOUS -> UniformInt.of(3, 7);
            case OreRules.EXOTIC -> UniformInt.of(5, 11);
            default -> UniformInt.of(0, 2);
        };
    }

    /** The row this block was built from. */
    public OreCatalogue.Entry entry() {
        return entry;
    }

    /** The material family this ore belongs to. */
    public String family() {
        return entry.family();
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        if (tool != null && silked(tool, params)) {
            return super.getDrops(state, params);
        }
        ItemStack material = OreDrops.stack(entry.family(), entry.vanilla(), 1);
        if (material.isEmpty()) {
            // The pack has no such material after all. Dropping the block is
            // better than dropping nothing: the player keeps what they mined
            // and nobody has to explain an ore that vanishes.
            return super.getDrops(state, params);
        }
        RandomSource random = params.getLevel().getRandom();
        int amount = OreRules.yield(tierOf(params), random.nextDouble());
        amount = OreRules.fortuned(amount, fortune(tool, params), random.nextDouble());
        material.setCount(amount);
        return List.of(material);
    }

    /** The world tier of whoever is breaking this, or zero for a machine. */
    private static int tierOf(LootParams.Builder params) {
        Entity breaker = params.getOptionalParameter(LootContextParams.THIS_ENTITY);
        return breaker instanceof Player player
                ? com.elysium.lib.tier.ElysiumTiers.of(player)
                : 0;
    }

    private static boolean silked(ItemStack tool, LootParams.Builder params) {
        return level(tool, params, net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH) > 0;
    }

    private static int fortune(ItemStack tool, LootParams.Builder params) {
        return level(tool, params, Enchantments.FORTUNE);
    }

    private static int level(ItemStack tool, LootParams.Builder params,
                             net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> which) {
        var lookup = params.getLevel().registryAccess()
                .lookup(net.minecraft.core.registries.Registries.ENCHANTMENT).orElse(null);
        if (lookup == null) {
            return 0;
        }
        return lookup.get(which)
                .map(holder -> net.minecraft.world.item.enchantment.EnchantmentHelper
                        .getItemEnchantmentLevel(holder, tool))
                .orElse(0);
    }
}
