package com.elysium.lib.scar;

import com.elysium.lib.hazard.ElysiumProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finding something for a heavy blow to take out of the world.
 *
 * <h2>Everything here is a reason to do nothing</h2>
 *
 * That is the shape of the method on purpose. A feature that breaks blocks is
 * one bad condition away from being the reason somebody uninstalls the mod, so
 * each guard is written as its own early return with its own comment, and
 * adding a guard is cheap while removing one is visible.
 *
 * <h2>The claim guard is the important one</h2>
 *
 * Nothing is ever broken inside a filed claim. A claim is the mod's existing
 * statement that a place belongs to somebody — it already shelters them from
 * the weather and already costs Suspicion to file — so it is the answer that
 * was already in the game to the question "how do I stop this happening to my
 * base", and a player who has filed one has already been told what it does.
 *
 * Inventing a separate protection setting would have meant two answers to the
 * same question, and the one a player found first would be the wrong one.
 */
public final class ElysiumCover {

    private ElysiumCover() {
    }

    /**
     * Blocks a datapack has named as breakable cover.
     *
     * Nothing outside this tag is ever broken. The library ships it holding the
     * things a fight plausibly wrecks — leaves, plants, glass, snow — and a
     * pack widens or empties it without touching any code. Emptying it turns
     * the whole feature off, which is a supported thing to want.
     */
    public static final TagKey<Block> FRAGILE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("elysiumlib", "fragile_cover"));

    /**
     * Scar the world near a blow, or do nothing.
     *
     * @param level  the world the blow landed in
     * @param victim what was hit; the search is around them, because that is
     *               where the cover they were standing behind is
     * @param damage how hard, before armour — only announced blows qualify
     */
    public static void scar(Level level, Entity victim, float damage) {
        if (!(level instanceof ServerLevel server) || victim == null) {
            return;                     // the client breaks nothing
        }
        if (!ElysiumScarring.heavyEnough(damage)) {
            return;                     // an ordinary hit marks nothing
        }
        if (server.getRandom().nextFloat() >= ElysiumScarring.CHANCE) {
            return;                     // most heavy blows still mark nothing
        }
        BlockPos centre = victim.blockPosition();
        if (ElysiumProtection.inShelteredClaim(server, centre)) {
            return;                     // somebody filed for this place
        }

        int broken = 0;
        // Walked in a fixed order rather than sampled, so the same arrangement
        // gives the same answer — a player who rebuilds the same cover twice
        // should not find it behaves differently.
        for (int dy = ElysiumScarring.RADIUS; dy >= -ElysiumScarring.RADIUS; dy--) {
            for (int dx = -ElysiumScarring.RADIUS; dx <= ElysiumScarring.RADIUS; dx++) {
                for (int dz = -ElysiumScarring.RADIUS; dz <= ElysiumScarring.RADIUS; dz++) {
                    if (broken >= ElysiumScarring.MOST_PER_BLOW) {
                        return;
                    }
                    BlockPos at = centre.offset(dx, dy, dz);
                    if (mayBreak(server, at)) {
                        // Dropped rather than deleted. A block that vanishes is
                        // a block a player has lost; one that falls at their
                        // feet is a fight that moved the scenery.
                        server.destroyBlock(at, true);
                        broken++;
                    }
                }
            }
        }
    }

    /** Every reason this particular block stays where it is. */
    public static boolean mayBreak(ServerLevel level, BlockPos at) {
        if (!level.isLoaded(at)) {
            return false;               // never reach into an unloaded chunk
        }
        BlockState state = level.getBlockState(at);
        if (state.isAir()) {
            return false;
        }
        if (!state.is(FRAGILE)) {
            return false;               // nothing untagged, ever
        }
        if (state.hasBlockEntity()) {
            // A chest, a machine, a bench. Tagging one would be a mistake
            // somebody makes once; losing its contents to a skeleton is the
            // sort of mistake they do not get back.
            return false;
        }
        if (state.is(BlockTags.PORTALS) || state.is(BlockTags.BEDS)) {
            return false;               // respawn and travel are not cover
        }
        float hardness = state.getDestroySpeed(level, at);
        return ElysiumScarring.breakable(hardness, true);
    }

    /** Whether an attacker's blow is one that may scar. For the tests. */
    public static boolean qualifies(LivingEntity attacker, float damage) {
        return attacker != null && ElysiumScarring.heavyEnough(damage);
    }
}
