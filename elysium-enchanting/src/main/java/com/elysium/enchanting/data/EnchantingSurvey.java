package com.elysium.enchanting.data;

import com.elysium.enchanting.registry.EnchantingAttributes;
import com.elysium.enchanting.rules.EnchantingRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reading the room around a table.
 *
 * <h2>Why the shape is what it is</h2>
 *
 * Vanilla scans a hollow 5x5 ring two blocks high and needs the space between
 * the table and each shelf to be clear. Everyone already knows how to build
 * that room, so this keeps it and only widens it by one: a 7x7 ring, three
 * high, which is a room a player can walk around in rather than a cross they
 * have to memorise.
 *
 * <p>The line-of-sight rule stays too. A shelf walled off from the table does
 * nothing, which is what makes the room a build rather than a warehouse.
 */
public final class EnchantingSurvey {

    private EnchantingSurvey() {
    }

    /** How far out the table looks. */
    public static final int RADIUS = 3;

    /** How far up and down. */
    public static final int HEIGHT = 2;

    /**
     * What the room around this table comes to, already capped.
     *
     * @param player whose cap applies; null for the table's own
     */
    public static EnchantingRules.Stats survey(Level level, BlockPos table, Player player) {
        EnchantingRules.Stats total = EnchantingRules.Stats.NONE;
        // The room's own cap starts at nothing: a table in an empty room is a
        // table that offers nothing, and everything it can offer is something
        // a player put there.
        float roomCap = 0.0F;
        float playerMax = EnchantingRules.BASE_MAX_ETERNA;
        if (player != null && player.getAttribute(EnchantingAttributes.MAX_ETERNA) != null) {
            playerMax = (float) player.getAttributeValue(EnchantingAttributes.MAX_ETERNA);
        }
        if (player != null) {
            // The one place enchanting joins the rest of Elysium: a player's
            // world tier is the library's measure of how far they have come,
            // and a ceiling that ignored it would be the one number in the
            // game that stops mattering.
            playerMax += EnchantingRules.tierBonus(
                    com.elysium.lib.tier.ElysiumTiers.of(player));
        }
        for (int y = -1; y <= HEIGHT; y++) {
            for (int x = -RADIUS; x <= RADIUS; x++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    if (Math.abs(x) < 2 && Math.abs(z) < 2) {
                        continue;           // the space the player stands in
                    }
                    BlockPos at = table.offset(x, y, z);
                    BlockState state = level.getBlockState(at);
                    EnchantingStatBlocks.Shelf shelf =
                            EnchantingStatBlocks.get().shelfFor(state);
                    if (shelf == null || !visible(level, table, x, y, z)) {
                        continue;
                    }
                    total = total.plus(shelf.stats());
                    // The best cap in the room, not the sum of them: otherwise
                    // the answer to every question is one more bookshelf, which
                    // is the vanilla table this module exists to replace.
                    roomCap = Math.max(roomCap, shelf.maxEterna());
                }
            }
        }
        return total.cappedAt(EnchantingRules.ceiling(roomCap, playerMax));
    }

    /**
     * Whether the table can see a shelf.
     *
     * The block one step from the table toward the shelf has to be clear —
     * vanilla's own rule, kept because every player already builds around it
     * and because a shelf behind a wall doing nothing is what makes the room a
     * build.
     */
    private static boolean visible(Level level, BlockPos table, int x, int y, int z) {
        int stepX = Integer.signum(x);
        int stepZ = Integer.signum(z);
        BlockPos between = table.offset(stepX, y, stepZ);
        return level.getBlockState(between).getCollisionShape(level, between).isEmpty();
    }
}
