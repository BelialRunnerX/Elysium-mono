package com.elysium.core.block;

import com.elysium.lib.claim.ElysiumClaim;
import com.elysium.lib.claim.ElysiumClaims;
import com.elysium.lib.standing.ElysiumFile;
import com.elysium.lib.contract.ElysiumContract;
import com.elysium.lib.contract.ElysiumContracts;
import com.elysium.lib.event.ElysiumPassives;
import com.elysium.lib.research.ElysiumResearch;
import com.elysium.lib.research.ElysiumUnlock;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Imperial Archive: what the Empire currently knows about you, and why.
 *
 * <h2>What the specification asks for</h2>
 *
 * §14.5, among the expanded Imperial infrastructure:
 *
 * <blockquote>Regional archives that reveal how much the Empire currently knows
 * about a system.</blockquote>
 *
 * and §21's HUD notes say what a standing readout contains: <blockquote>Favor,
 * current-system Suspicion, bands, recent causes, claim floors.</blockquote>
 * All five are here, which is the point of building it now rather than earlier
 * — until claims existed there was no floor to show, and until
 * {@link ElysiumFile} existed there were no causes.
 *
 * <h2>Why this is the most useful block in the mod right now</h2>
 *
 * Every system built against §14 so far changes a number, and the player is
 * told when a <em>band</em> moves and nothing else — deliberately, because a
 * meter that narrates every point stops being read. The cost of that decision
 * is that a player who finds themselves Marked has no way to learn what did it:
 * the ore, the enforcer, the ascension, the beacon they planted an hour ago, or
 * simply not enough time passing.
 *
 * So this is not a convenience readout. It is the other half of a design that
 * was already committed to being quiet.
 *
 * <h2>Chat, not a screen</h2>
 *
 * Eight lines in the chat log rather than a GUI. Partly because the readout is
 * text and a window around text is a window; mostly because chat lines
 * <em>persist</em> — a player can open the archive, close it, walk to their
 * beacon and still read the numbers, which is exactly the workflow this is for.
 * A screen would have to be held open to be useful.
 */
public class ImperialArchiveBlock extends Block {

    public ImperialArchiveBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(4.0F, 12.0F)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> 7)
                .sound(SoundType.DEEPSLATE_TILES));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer)) {
            return InteractionResult.SUCCESS;
        }

        // Sneaking at the counter is business rather than paperwork: settle
        // anything that is done, and take one if there is room. Chosen as the
        // gesture for the same reason the villager readout uses it — it is the
        // one action that cannot be confused with wanting to read your file.
        if (player.isShiftKeyDown()) {
            contracts(server, pos, player);
            return InteractionResult.CONSUME;
        }

        int favor = ElysiumStanding.getFavor(player);
        int suspicion = ElysiumStanding.getSuspicion(player);

        // Reading your own file teaches you how the filing works. Once, ever:
        // the second reading teaches nothing, and a field that paid for
        // right-clicking would be a field farmed by right-clicking.
        com.elysium.lib.research.ElysiumResearch.credit(
                player, com.elysium.lib.research.ElysiumDeed.READ_THE_ARCHIVE,
                "archive");

        say(player, Component.translatable("elysium.archive.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        say(player, ElysiumStanding.report(player));

        // What Imperial Systems research buys: the edges of the bands.
        //
        // A player without it is told which band they are in, which is what the
        // Empire would tell them. A player who has studied the filing knows
        // where the next line is and how fast the file forgets — the two facts
        // that turn Suspicion from weather into something you can plan around,
        // and the two the game had no way of stating before.
        if (ElysiumResearch.has(player, ElysiumUnlock.LEDGER_DETAIL)) {
            int toBand = ElysiumStanding.toNextBand(suspicion);
            say(player, Component.translatable("elysium.archive.detail",
                    favor, suspicion,
                    toBand > 0 ? toBand : 0,
                    ElysiumPassives.decayRate(player),
                    ElysiumStanding.DECAY_INTERVAL / 20)
                    .withStyle(ChatFormatting.AQUA));
        }

        // The floor, and the claim it comes from. Reported as a separate line
        // rather than folded into the Suspicion line because it answers a
        // different question: not "how much", but "how much of this will never
        // go away".
        int floor = ElysiumStanding.floorFor(player);
        if (floor > 0) {
            say(player, Component.translatable("elysium.archive.floor", floor,
                    holdings(server, player)).withStyle(ChatFormatting.YELLOW));
        } else {
            say(player, Component.translatable("elysium.archive.no_claim")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        // §21's "recent causes".
        ElysiumFile file = ElysiumFile.get(player);
        if (file.isEmpty()) {
            say(player, Component.translatable("elysium.archive.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            long now = level.getGameTime();
            for (ElysiumFile.Entry entry : file.entries()) {
                say(player, entry.describe(now));
            }
        }

        // Deliberately unsuppressed even when nothing has happened: the sound
        // is how a player knows the block worked. A machine that is silent when
        // it has nothing to say is a machine that looks broken exactly when a
        // new player first tries it.
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.6F, 1.4F);
        return InteractionResult.CONSUME;
    }

    /**
     * The counter: settle what is finished, then offer new work.
     *
     * Settling first, always. A player who walks in with a finished delivery
     * and no room for another contract must be able to hand it over — doing it
     * the other way round would mean a full board could never be emptied, and
     * the only way out would be to let something lapse.
     */
    private static void contracts(ServerLevel level, BlockPos pos, Player player) {
        int settled = ElysiumContracts.settle(player, true);
        java.util.List<ElysiumContract> holding = ElysiumContracts.held(player);

        if (settled > 0) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.7F, 1.8F);
        }

        if (ElysiumContracts.hasRoom(player)) {
            ElysiumContract offered = ElysiumContracts.offer(
                    player, true, level.getRandom());
            if (ElysiumContracts.take(player, offered)) {
                say(player, Component.translatable("elysium.contract.issued",
                                ElysiumContracts.describe(offered),
                                offered.pay(), offered.standing(),
                                offered.deadline() / 20 / 60)
                        .withStyle(ChatFormatting.AQUA));
                holding = ElysiumContracts.held(player);
            }
        } else if (settled == 0) {
            say(player, Component.translatable("elysium.contract.full")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        // The whole board, every time. A contract a player cannot see the
        // progress of is one they will assume is broken, and the deadline is
        // the half that decides whether it is worth going back for.
        long now = level.getGameTime();
        for (ElysiumContract contract : holding) {
            say(player, Component.translatable("elysium.contract.line",
                            ElysiumContracts.describe(contract),
                            contract.done(), contract.quantity(),
                            contract.remaining(now) / 20 / 60)
                    .withStyle(contract.complete() ? ChatFormatting.GREEN
                            : ChatFormatting.GRAY));
        }
    }

    /**
     * How many claims this player holds in this dimension, and the largest.
     *
     * This dimension only, unlike the floor itself, which is the maximum across
     * all of them. The asymmetry is on purpose and is the honest one: the floor
     * has to be global because the meter is, and a <em>regional</em> archive
     * that claimed to enumerate another region's holdings would be overstating
     * what a regional archive is.
     */
    private static Component holdings(ServerLevel level, Player player) {
        ElysiumClaims claims = ElysiumClaims.of(level);
        int here = 0;
        int largest = 0;
        for (ElysiumClaim claim : claims.all()) {
            if (claim.owner().equals(player.getUUID())) {
                here++;
                largest = Math.max(largest, claim.structures());
            }
        }
        return Component.translatable("elysium.archive.holdings", here, largest);
    }

    private static void say(Player player, Component line) {
        player.displayClientMessage(line, false);
    }
}
