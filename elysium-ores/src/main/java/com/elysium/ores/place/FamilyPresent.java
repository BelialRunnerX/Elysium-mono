package com.elysium.ores.place;

import com.elysium.ores.ore.OreDrops;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * "Only if this pack has that material at all."
 *
 * <h2>The problem</h2>
 *
 * This module adds an osmium ore to end stone. Osmium is Mekanism's, and
 * Mekanism is optional. Without it there is no osmium item, so an osmium ore
 * would generate through the whole End, be mined, and drop the block back —
 * a rock that costs a diamond pickaxe and gives nothing.
 *
 * <p>Deleting the feature is not possible: worldgen is data, loaded before
 * anything knows which mods are here, and a feature file that referred to a
 * missing block would be a datapack error rather than a quiet absence.
 *
 * <h2>The answer</h2>
 *
 * A placement filter, which is the one point in worldgen that runs at
 * generation time and is allowed to say no. It asks {@link OreDrops} whether
 * the family resolves to anything, and an unanswered family places nothing,
 * anywhere, forever — at the cost of one map lookup per attempted vein.
 *
 * <p>The block stays registered either way, because this module's own blocks
 * are not optional; it simply never appears in the ground.
 */
public class FamilyPresent extends PlacementFilter {

    public static final MapCodec<FamilyPresent> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    com.mojang.serialization.Codec.STRING.fieldOf("family")
                            .forGetter(FamilyPresent::family),
                    com.mojang.serialization.Codec.STRING.optionalFieldOf("vanilla", "")
                            .forGetter(FamilyPresent::vanilla)
            ).apply(instance, FamilyPresent::new));

    private final String family;
    private final String vanilla;

    public FamilyPresent(String family, String vanilla) {
        this.family = family;
        this.vanilla = vanilla;
    }

    public String family() {
        return family;
    }

    public String vanilla() {
        return vanilla;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        return OreDrops.present(family, vanilla.isEmpty() ? null : vanilla);
    }

    @Override
    public PlacementModifierType<?> type() {
        return com.elysium.ores.registry.OrePlacement.FAMILY_PRESENT.get();
    }
}
