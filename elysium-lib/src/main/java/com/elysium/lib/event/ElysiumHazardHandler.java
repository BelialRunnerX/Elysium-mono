package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.hazard.ElysiumHazard;
import com.elysium.lib.hazard.ElysiumHazardField;
import com.elysium.lib.research.ElysiumResearch;
import com.elysium.lib.research.ElysiumUnlock;
import com.elysium.lib.hazard.ElysiumHazards;
import com.elysium.lib.hazard.ElysiumProtection;
import com.elysium.lib.hazard.ElysiumResistance;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Applies the five hazards to a player standing in them.
 *
 * <h2>Why a player tick and not an attribute</h2>
 *
 * A hazard is not a debuff a player carries; it is a property of where they are
 * standing, and it changes the moment they step indoors. Reading the field each
 * cadence means walking into a cave stops the burning immediately, with nothing
 * to expire and no state to get stuck.
 *
 * <h2>Being told why</h2>
 *
 * The first time a hazard starts hurting, the player is told which one and what
 * to do about it, and they are not told again until it stops and starts anew.
 * That is the whole reason the counters are on {@link ElysiumHazard}: damage
 * with no name teaches a player nothing except that the game is unfair, and
 * they cannot see a radiation field.
 *
 * Creative and spectator are exempt, as is any player already dead. Peaceful is
 * not exempt: these are environmental, and a player who has chosen no monsters
 * has not chosen no weather.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumHazardHandler {

    private ElysiumHazardHandler() {
    }

    /**
     * How often the field is sampled.
     *
     * A second. Damage rates are quoted per second, so this is the rate at
     * which they are meant. Sampling faster would make the numbers on the
     * tooltip wrong; slower would let a player cross a lava field untouched.
     */
    public static final int INTERVAL = 20;

    /** Below this, a hazard is present but not worth interrupting anybody for. */
    public static final float NOTICE_THRESHOLD = 0.05F;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide() || player.tickCount % INTERVAL != 0) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            return;
        }
        BlockPos pos = player.blockPosition();
        // Asked once for all five rather than per hazard: a claim lookup walks
        // every claim in the dimension, and doing that five times a second per
        // player is the kind of cost that only shows up on a full server.
        boolean sheltered = ElysiumProtection.inShelteredClaim(level, pos);
        // Told once per room, whether or not a hazard is biting. A room's whole
        // design claim is that a player can name what they built and improve it
        // on purpose, and that only holds if the game says which room it is —
        // otherwise a bunker and a broom cupboard feel identical right up until
        // the moment one of them fails to save somebody.
        announceRoom(player, com.elysium.lib.room.ElysiumRoomCache.at(level, pos));
        // Whether this player is out in anything at all this second, for the
        // Environmental research track. Gathered across the loop rather than
        // inside it, because a minute in two hazards at once is still a minute
        // — crediting per hazard would make a thunderstorm over a lava field
        // teach twice as fast as either one alone, which is the wrong lesson
        // about what is hard.
        String exposedTo = null;
        for (ElysiumHazard hazard : ElysiumHazard.REGISTRY.all()) {
            float intensity = ElysiumHazardField.intensity(level, pos, hazard);
            if (intensity <= 0.0F) {
                continue;
            }
            boolean armoured = ElysiumProtection.isSealed(player, hazard);
            boolean warded = com.elysium.lib.hazard.ElysiumWards.isWarded(player, hazard);
            // Counted before any of the answers are considered, except the
            // claim. Surviving a desert in the right plate is still surviving
            // the desert; standing in a filed claim is being indoors. See
            // ElysiumExposure for why an unhurt player has to count.
            if (!sheltered && exposedTo == null) {
                exposedTo = hazard.getId().toString();
            }
            if (sheltered || armoured || warded) {
                // Either named answer: standing in a filed claim, or wearing a
                // complete set of the hazard's own element. The world is still
                // hostile; this player has answered it.
                //
                // Which one is named, because they do not travel together. A
                // player told "sealed" who believes it is their armour, and who
                // is actually standing in their own claim, walks out of the
                // claim and dies — and has been told nothing that would have
                // warned them. Armour is reported in preference to the claim
                // when both apply, because armour is the one that stays with
                // them.
                reassure(player, hazard, armoured ? Answer.GEAR
                        : sheltered ? Answer.CLAIM : Answer.MEAL);
                continue;
            }
            float raw = hazard.getDamagePerSecond() * intensity;
            // Everything partial the player has: the armour they are wearing
            // and the room they are standing in. Gathered by ElysiumProtection
            // rather than here, so a source added later is felt without this
            // tick being edited again.
            float landed = ElysiumResistance.after(raw,
                    ElysiumProtection.allShares(player, hazard));
            if (landed < NOTICE_THRESHOLD) {
                continue;               // protected, or barely in it at all
            }
            announce(player, hazard, intensity, raw, landed);
            player.hurt(level.damageSources().source(damageTypeFor(hazard)), landed);
        }
        if (exposedTo != null) {
            com.elysium.lib.research.ElysiumExposure.second(player, exposedTo);
        }
    }

    /**
     * The vanilla damage type closest to each hazard.
     *
     * Reused rather than registered, so that every piece of vanilla and modded
     * behaviour keyed on "this was fire damage" — fire resistance, the death
     * message, immunity frames, a mod's own listener — keeps working. A private
     * Elysium damage type would have been more precise and would have made
     * Fire Resistance stop meaning anything in a burning world.
     */
    private static ResourceKey<DamageType> damageTypeFor(ElysiumHazard hazard) {
        if (hazard == ElysiumHazards.THERMAL) {
            return DamageTypes.HOT_FLOOR;
        }
        if (hazard == ElysiumHazards.CRYOGENIC) {
            return DamageTypes.FREEZE;
        }
        // Corrosive, radiological and pressure have no vanilla analogue that
        // carries the right connotations; magic is the neutral one that no
        // armour already resists for the wrong reason.
        return DamageTypes.MAGIC;
    }

    /**
     * Tell the player what is happening, once per exposure.
     *
     * Keyed on the hazard rather than on a timer, so stepping out and back in
     * says it again — which is the case where a player is still working out
     * what the boundary is.
     */
    private static void announce(Player player, ElysiumHazard hazard,
                                 float intensity, float raw, float landed) {
        // The two research lines come before the once-only guard on purpose.
        // The warning is a thing a player is told once, ever, because a
        // repeated one is noise; telemetry is a reading, and a reading that
        // only appears the first time you were ever cold is not an instrument.
        telemetry(player, hazard, intensity, raw, landed);

        String key = "elysium.hazard.warned." + hazard.getId().getPath();
        if (player.getPersistentData().getBoolean(key)) {
            return;
        }
        player.getPersistentData().putBoolean(key, true);
        player.displayClientMessage(Component.translatable(
                "elysium.hazard.exposed", hazard.getDisplayName())
                .withStyle(ChatFormatting.RED), false);
        player.displayClientMessage(hazard.getCheapCounter()
                .copy().withStyle(ChatFormatting.GRAY), false);
    }

    /**
     * What Environmental research buys: the numbers behind the warning.
     *
     * Both lines are on the action bar rather than in chat, because they are a
     * gauge rather than an event — a player glances at them and they are
     * replaced a second later, which is what an instrument reading should do
     * and what a chat line very much should not.
     *
     * Telemetry gives the field's own strength and what it is actually costing.
     * The audit adds how much of it is being stopped, which is the number that
     * tells somebody whether the two pieces of armour they are wearing are
     * worth the third — and there is no other way in the game to find that out.
     */
    private static void telemetry(Player player, ElysiumHazard hazard,
                                  float intensity, float raw, float landed) {
        if (!ElysiumResearch.has(player, ElysiumUnlock.HAZARD_TELEMETRY)) {
            return;
        }
        Component line = Component.translatable("elysium.hazard.telemetry",
                hazard.getDisplayName(), Math.round(intensity * 100.0F),
                String.format(java.util.Locale.ROOT, "%.1f", landed));
        if (ElysiumResearch.has(player, ElysiumUnlock.PROTECTION_AUDIT)) {
            int stopped = raw <= 0.0F ? 0
                    : Math.round((1.0F - landed / raw) * 100.0F);
            line = line.copy().append(Component.translatable(
                    "elysium.hazard.audit", stopped));
        }
        player.displayClientMessage(line.copy()
                .withStyle(ChatFormatting.GOLD), true);
    }

    /**
     * Tell a sealed player that they are sealed, once.
     *
     * Because otherwise the reward for assembling a full set is that nothing
     * happens, and nothing happening is indistinguishable from not being in the
     * hazard at all. A player who has just walked into a lava field in full
     * Plasma deserves to be told the field is there and that their armour is
     * the reason they are fine — that is the moment the four pieces they
     * committed to pay off, and it is the only moment they can be told.
     */
    /**
     * The three named answers, in the order they are reported.
     *
     * Gear before claim before meal, because that is the order they stay with
     * a player: armour is worn until it breaks, a claim stands until it is
     * struck, a meal runs out in three minutes. A player told the reason that
     * lasts longest is a player who is not surprised when the shorter ones
     * end.
     */
    private enum Answer {
        GEAR("elysium.hazard.sealed", ".gear"),
        CLAIM("elysium.hazard.sheltered", ".claim"),
        MEAL("elysium.hazard.warded", ".meal");

        final String message;
        final String flag;

        Answer(String message, String flag) {
            this.message = message;
            this.flag = flag;
        }
    }

    private static void reassure(Player player, ElysiumHazard hazard, Answer answer) {
        // The flag records which reason was shown, so a player who walks out of
        // a claim still wearing the wrong armour is told again — this time by
        // being hurt, which is the correct lesson and one they can now connect
        // to having left. The same goes for a meal running out.
        String key = "elysium.hazard.sealed." + hazard.getId().getPath() + answer.flag;
        if (player.getPersistentData().getBoolean(key)) {
            return;
        }
        player.getPersistentData().putBoolean(key, true);
        player.displayClientMessage(Component.translatable(answer.message,
                hazard.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * Name the room a player has just walked into, once.
     *
     * Keyed on which room was last named rather than on a timer, so walking
     * from a shelter into the bunker next door says so, and walking back says
     * so again. The key is cleared when they step outside, which is what makes
     * the boundary of a room something a player can find by walking it.
     */
    private static void announceRoom(Player player, com.elysium.lib.room.ElysiumRoom room) {
        // Not a translation key, and named so it cannot be mistaken for one —
        // the validator's "every literal that looks like a lang key has an
        // entry" check found the previous spelling, correctly.
        String key = "elysium_room_named";
        String was = player.getPersistentData().getString(key);
        String now = room == null ? "" : room.getId().toString();
        if (was.equals(now)) {
            return;
        }
        if (now.isEmpty()) {
            player.getPersistentData().remove(key);
            return;
        }
        player.getPersistentData().putString(key, now);
        player.displayClientMessage(Component.translatable("elysium.room.entered",
                room.getDisplayName().copy().withStyle(ChatFormatting.AQUA),
                room.getDescription()).withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * Clear the warning flags for hazards the player is no longer in.
     *
     * Runs on the same cadence as the damage. Without it the message is
     * once-per-life rather than once-per-exposure, and a player who walks into
     * a second radiation field a week later is told nothing.
     */
    @SubscribeEvent
    public static void clearWarnings(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide() || player.tickCount % INTERVAL != 10) {
            return;                     // offset from the damage pass
        }
        BlockPos pos = player.blockPosition();
        for (ElysiumHazard hazard : ElysiumHazard.REGISTRY.all()) {
            if (ElysiumHazardField.intensity(level, pos, hazard) <= 0.0F) {
                player.getPersistentData().remove(
                        "elysium.hazard.warned." + hazard.getId().getPath());
                player.getPersistentData().remove(
                        "elysium.hazard.sealed." + hazard.getId().getPath() + ".gear");
                player.getPersistentData().remove(
                        "elysium.hazard.sealed." + hazard.getId().getPath() + ".claim");
                player.getPersistentData().remove(
                        "elysium.hazard.sealed." + hazard.getId().getPath() + ".meal");
            }
        }
    }
}
