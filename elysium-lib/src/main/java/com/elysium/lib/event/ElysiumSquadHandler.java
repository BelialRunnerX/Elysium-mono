package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.entity.ElysiumSquads;
import com.elysium.lib.standing.ElysiumStanding;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * The clock that sends roaming bands.
 *
 * Split from {@link ElysiumSquads} the same way {@code ElysiumStandingHandler}
 * is split from {@code ElysiumDispatch}: one of them knows <em>what a band is
 * and how to place one</em>, and is callable by a command, a structure or a
 * content mod that wants a band for its own reasons; this one knows only
 * <em>when</em>. Neither has to grow a flag when the other changes.
 *
 * <h2>One long per player</h2>
 *
 * The whole scheduler is a single stored tick: when the next band is due. It is
 * persisted rather than kept in a server-side map for two reasons — a map keyed
 * by UUID has to be cleaned up on logout and never quite is, and an unpersisted
 * clock means logging out and back in resets the timer, which turns a two-minute
 * gap into whatever the player is willing to click through.
 *
 * <h2>Two things the stored tick has to survive</h2>
 *
 * <b>A long absence.</b> Game time runs on a server while a player is away, so
 * somebody who logs back in after an hour is an hour overdue. Firing on that
 * would drop a band on them during the login tick, before they have finished
 * loading in. A grace period on {@code tickCount} — which is not saved, and so
 * is genuinely "how long since this player arrived" — holds everything off for
 * the first few seconds.
 *
 * <b>A clock that went backwards.</b> A save opened in a different world, or
 * restored from a backup, can have a game time well behind the stored tick,
 * which would park the next arrival years away and silently switch the feature
 * off for that player. Anything further out than the longest legal gap is
 * treated as nonsense and rescheduled.
 *
 * Neither of these is hypothetical enough to leave out: both produce a feature
 * that looks like it is simply not working, with nothing logged.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumSquadHandler {

    private ElysiumSquadHandler() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    /**
     * The game tick the next band is due.
     *
     * Zero means "never scheduled", which is what a new player and an old save
     * both look like, and both are handled by scheduling one.
     *
     * {@code copyOnDeath} on purpose: dying is not a way to buy two quiet
     * minutes. It is already the most expensive thing that can happen to you.
     */
    public static final Supplier<AttachmentType<Long>> NEXT_SQUAD =
            ATTACHMENTS.register("next_squad",
                    () -> AttachmentType.<Long>builder(() -> 0L)
                            .serialize(Codec.LONG)
                            .copyOnDeath()
                            .build());

    /** How often the stored tick is compared against the clock. */
    private static final int CHECK_INTERVAL = 20;

    /** Nothing arrives in a player's first few seconds in the world. */
    private static final int GRACE_TICKS = 20 * 6;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.tickCount < GRACE_TICKS || player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }

        long now = level.getGameTime();
        long next = player.getData(NEXT_SQUAD.get());
        RandomSource random = level.getRandom();

        // Unscheduled, or scheduled further out than any legal gap — the second
        // being a clock that moved backwards under us. Both mean "there is no
        // usable appointment", and both are answered by making one.
        if (next <= 0L || next > now + ElysiumSquads.GAP_BASE + ElysiumSquads.GAP_SPREAD) {
            reschedule(player, now, random);
            return;
        }
        if (now < next) {
            return;
        }

        // Whether or not anything was actually placed, the appointment is kept
        // and the next one is made. A refusal — suppressed, crowded, nowhere to
        // stand — must not leave the clock in the past, or the player is
        // retried every second until the refusal lifts and then meets a band
        // the instant it does.
        ElysiumSquads.send(level, player, random);
        reschedule(player, now, random);
    }

    /**
     * The gap is drawn from the higher of the two meters.
     *
     * Not the sum, and not the one whose faction turns up. A player at Exalted
     * and clean, and a player at Hunted and unknown, are both people the world
     * has opinions about — they should be visited at the same rate, and by
     * different people. Which faction arrives is {@code factionFor}'s job, and
     * it reads the meters separately.
     */
    private static void reschedule(Player player, long now, RandomSource random) {
        int band = Math.max(
                ElysiumStanding.bandOf(ElysiumStanding.getFavor(player)),
                ElysiumStanding.bandOf(ElysiumStanding.getSuspicion(player)));
        player.setData(NEXT_SQUAD.get(), now + ElysiumSquads.gapFor(band, random));
    }
}
