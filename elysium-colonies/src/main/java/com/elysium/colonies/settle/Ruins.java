package com.elysium.colonies.settle;

import com.elysium.colonies.ElysiumColonies;
import com.elysium.colonies.charter.Charter;
import com.elysium.colonies.charter.Charters;
import com.elysium.colonies.colony.Colonies;
import com.elysium.colonies.rules.RuinRules;
import com.elysium.colonies.rules.SettleRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

/**
 * Abandoned ruins: one building of the world's style pack, raised as the world
 * left it.
 *
 * <p>The living towns ({@link Settlements}) found a real colony through the
 * seam. A ruin founds nothing: it asks the seam only for the blocks a
 * blueprint holds ({@link Colonies#blueprintOf}), then places them itself with
 * {@link RuinRules}' decay — floors kept, walls thinning as they rise, roofs
 * mostly gone, wood rotted, stone weathered, nothing of glass or cloth or
 * light left — skips the host's own blocks (a hut in a ruin would be a colony
 * waiting to happen), and leaves one chest of the loot table the rules name.
 * No colony data is written anywhere, which is the whole difference between
 * a ruin and a town.
 */
public final class Ruins {

    private Ruins() {
    }

    /** Raises the ruin the marker stood for. False, with a line in the log, when it cannot. */
    public static boolean raise(ServerLevel level, BlockPos marker) {
        Charter charter = Charters.of(level.dimension().location());
        if (!charter.settles()) {
            return declined(level, marker, "this world's charter does not settle");
        }
        BlockPos spawn = level.getSharedSpawnPos();
        int dx = (marker.getX() - spawn.getX()) >> 4;
        int dz = (marker.getZ() - spawn.getZ()) >> 4;
        if (Math.max(Math.abs(dx), Math.abs(dz)) < RuinRules.FROM_SPAWN) {
            return declined(level, marker, "too near the world spawn");
        }
        BlockPos ground = Settlements.dryGroundNear(level, marker);
        if (ground == null) {
            return declined(level, marker, "no dry, level ground within reach");
        }
        String pack = charter.hasStyle() ? charter.style() : SettleRules.DEFAULT_PACK;
        long seed = SettleRules.mix(level.getSeed() ^ ground.asLong());
        String kind = RuinRules.KINDS.get((int) Math.floorMod(seed, (long) RuinRules.KINDS.size()));
        List<Colonies.Placed> blocks = Colonies.blueprintOf(level, pack, kind);
        if (blocks.isEmpty()) {
            return declined(level, marker, "no blueprint for " + kind + " in " + pack);
        }
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (Colonies.Placed placed : blocks) {
            lowest = Math.min(lowest, placed.at().getY());
            highest = Math.max(highest, placed.at().getY());
        }
        int height = highest - lowest + 1;
        int kept = 0;
        for (Colonies.Placed placed : blocks) {
            BlockState state = placed.state();
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String name = id.toString();
            // The host's own blocks — huts, racks, its decorations that carry a
            // colony's data — are what a ruin has none of.
            if (id.getNamespace().equals("minecolonies") || RuinRules.flimsy(name)) {
                continue;
            }
            BlockPos at = ground.offset(placed.at());
            int row = placed.at().getY() - lowest;
            if (!RuinRules.keep(seed, at.getX(), at.getY(), at.getZ(), height, RuinRules.wooden(name))) {
                continue;
            }
            String after = RuinRules.weathered(seed, at.getX(), at.getY(), at.getZ(), name);
            if (!after.equals(name)) {
                Block into = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(after));
                if (into != Blocks.AIR) {
                    state = carryOver(state, into.defaultBlockState());
                }
            }
            if (level.setBlock(at, state, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS)) {
                kept++;
            }
            if (row == 0 && kept == 1) {
                // nothing: the chest goes where the floor is, below
            }
        }
        // The chest: on the floor, at the anchor's own column, in whatever
        // air is left there; the loot table is the rules'.
        BlockPos chest = ground.above(lowest < 0 ? 0 : 1);
        for (int tries = 0; tries < 6 && !level.getBlockState(chest).isAir(); tries++) {
            chest = chest.above();
        }
        if (level.getBlockState(chest).isAir()) {
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
            RandomizableContainer.setBlockEntityLootTable(level, level.random, chest,
                    ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(RuinRules.LOOT)));
        }
        ElysiumColonies.LOGGER.info("a ruin of a {} stands at {} in {}: {} of {} blocks left, {} rows",
                kind, ground, level.dimension().location(), kept, blocks.size(), height);
        return true;
    }

    /** The weathered block in the placed one's shape: stairs stay turned, slabs stay top or bottom. */
    private static BlockState carryOver(BlockState from, BlockState into) {
        BlockState out = into;
        for (net.minecraft.world.level.block.state.properties.Property<?> property : from.getProperties()) {
            if (into.hasProperty(property)) {
                out = copy(out, from, property);
            }
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState into, BlockState from,
                                                             net.minecraft.world.level.block.state.properties.Property<T> property) {
        return into.setValue(property, from.getValue(property));
    }

    private static boolean declined(ServerLevel level, BlockPos marker, String why) {
        ElysiumColonies.LOGGER.info("no ruin at {} in {}: {}", marker, level.dimension().location(), why);
        return false;
    }
}
