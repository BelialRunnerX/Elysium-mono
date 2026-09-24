package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.practice.ElysiumPractice;
import com.elysium.lib.practice.ElysiumPracticeLog;
import com.elysium.lib.social.ElysiumMemories;
import com.elysium.lib.stress.ElysiumStress;
import com.elysium.lib.stress.ElysiumStressRecord;
import com.elysium.lib.stress.ElysiumStressor;
import com.elysium.lib.social.ElysiumMemoryKind;
import com.elysium.lib.social.ElysiumMemoryKinds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

import java.util.List;

/**
 * Turns what happens near a villager into something they remember.
 *
 * <h2>The witness radius</h2>
 *
 * {@link #EARSHOT} is the one number here that decides how the system feels.
 * Too small and killing somebody in a doorway is unwitnessed, which reads as
 * the game not noticing. Too large and a fight two streets away sours a whole
 * town, which reads as the game being unfair. Sixteen blocks is a little under
 * a villager's own {@code follow_range} and about the distance across a small
 * village square — near enough that a player can see who is watching.
 *
 * <h2>Line of sight is not checked, and that is a choice</h2>
 *
 * A wall between you and the witness does not save you. Checking it would be
 * more realistic and worse: it would make whether a villager saw you depend on
 * geometry a player cannot evaluate while fighting, and it would reward killing
 * people indoors. "They were close enough to know" is a rule a player can hold
 * in their head.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumSocialHandler {

    private ElysiumSocialHandler() {
    }

    /** How far a thing has to happen before nobody notices. */
    public static final double EARSHOT = 16.0D;

    // ------------------------------------------------------------------
    // Recording
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        if (event.getSource().getEntity() instanceof Player player) {
            ElysiumMemories.remember(villager, ElysiumMemoryKinds.HURT_BY, player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()
                || !(event.getSource().getEntity() instanceof Player player)) {
            return;
        }

        // Killing one of theirs is remembered by everyone close enough. Killing
        // something that was hunting them is remembered the same way — the
        // difference is only which kind is recorded, which is why this is one
        // sweep and not two.
        ElysiumMemoryKind kind;
        if (dead instanceof Villager) {
            kind = ElysiumMemoryKinds.SAW_KILLING;
        } else if (dead instanceof Enemy) {
            kind = ElysiumMemoryKinds.DEFENDED;
        } else {
            return;
        }

        AABB earshot = dead.getBoundingBox().inflate(EARSHOT);
        for (Villager witness : dead.level().getEntitiesOfClass(
                Villager.class, earshot, villager -> villager != dead)) {
            ElysiumMemories.remember(witness, kind, player, dead.blockPosition());
        }
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (event.getAbstractVillager() instanceof Villager villager) {
            ElysiumMemories.remember(villager, ElysiumMemoryKinds.TRADED,
                    event.getEntity());
            // And the villager gets a little better at that one trade. Keyed
            // by what was sold rather than by the villager's profession — see
            // ElysiumPractice for why that distinction is the whole feature.
            ElysiumPracticeLog.record(villager, event.getMerchantOffer().getResult());
        }
    }

    // ------------------------------------------------------------------
    // Consequences
    // ------------------------------------------------------------------

    /**
     * Refusing to deal, and being asked what you remember.
     *
     * Both live on the same event because both are "a player right-clicked a
     * villager" and the order between them matters: a villager who will not
     * trade with you still has to be able to tell you why, or the refusal is
     * indistinguishable from a bug.
     */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()
                || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getTarget() instanceof Villager villager)) {
            return;
        }
        Player player = event.getEntity();

        // Empty-handed and crouching: ask, do not trade. Chosen because it is
        // the one gesture that cannot be confused with wanting to buy
        // something — crouching alone already suppresses the trade screen for
        // players carrying a block they meant to place.
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            recount(villager, player);
            return;
        }

        // Price their offers before vanilla opens the screen. This is the one
        // moment the offers exist and the price is still changeable: doing it
        // on the trade itself would be after the player has paid.
        ElysiumPracticeLog.price(villager, villager.getOffers());

        if (ElysiumMemories.refuses(villager, player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            player.displayClientMessage(
                    Component.translatable("elysium.memory.refuses",
                                    villager.getDisplayName())
                            .withStyle(ChatFormatting.RED), true);
            return;
        }

        // Somebody in crisis is not refusing *you*. Said differently for that
        // reason, and said with the reason attached: a villager who will not
        // trade and cannot say why is a bug as far as a player is concerned,
        // and they will reload the world looking for one.
        ElysiumStress.Reading stress = ElysiumStressRecord.readingFor(villager);
        if (stress.inCrisis()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            ElysiumStressor worst = stress.worst();
            player.displayClientMessage(Component.translatable(
                            "elysium.stress.too_upset", villager.getDisplayName(),
                            worst == null
                                    ? Component.translatable("elysium.stress.reason.unknown")
                                    : Component.translatable(worst.translationKey()))
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }

    /**
     * What this villager remembers about you, worst first.
     *
     * Worst first rather than newest first, because the question a player is
     * actually asking is "why won't you deal with me" and the answer is at the
     * top of that ordering. The raw opinion is printed alongside, so the list
     * and the behaviour can be checked against each other.
     */
    private static void recount(Villager villager, Player player) {
        long now = villager.level().getGameTime();
        List<ElysiumMemories.Episode> episodes =
                ElysiumMemories.about(villager, player.getUUID());
        int opinion = ElysiumMemories.opinionOf(villager, player);

        player.displayClientMessage(Component.translatable(
                "elysium.memory.header", villager.getDisplayName(),
                Component.literal(Integer.toString(opinion)).withStyle(
                        opinion <= ElysiumMemories.REFUSAL ? ChatFormatting.RED
                                : opinion >= ElysiumMemories.WARMTH ? ChatFormatting.GREEN
                                : ChatFormatting.YELLOW)), false);

        if (episodes.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("elysium.memory.stranger")
                            .withStyle(ChatFormatting.DARK_GRAY), false);
            sayHowTheyAre(villager, player);
            return;
        }

        sayHowTheyAre(villager, player);

        episodes.sort((first, second) ->
                Integer.compare(first.weightAt(now), second.weightAt(now)));
        for (ElysiumMemories.Episode episode : episodes) {
            ElysiumMemoryKind kind = episode.resolve();
            if (kind == null) {
                continue;
            }
            int weight = episode.weightAt(now);
            player.displayClientMessage(Component.literal(" ")
                    .append(kind.describe(episode.name()))
                    .append(Component.literal(" (" + (weight > 0 ? "+" : "")
                                    + weight + ")")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .withStyle(kind.isGood() ? ChatFormatting.GREEN
                            : ChatFormatting.RED), false);
        }
    }

    /**
     * What this villager has spent their life doing, and what it is worth.
     *
     * Part of the same answer as the memory and the stress: a player asking
     * about somebody gets everything the game knows about them in one place.
     * Naming the discount rather than only the band, because a band with no
     * number attached is a compliment rather than a fact, and this one is the
     * reason their bread is cheap.
     */
    private static void sayWhatTheyAreGoodAt(Villager villager, Player player) {
        java.util.List<java.util.Map.Entry<String, Integer>> best =
                ElysiumPracticeLog.best(villager, 3);
        if (best.isEmpty()) {
            return;
        }
        for (java.util.Map.Entry<String, Integer> entry : best) {
            ElysiumPractice band = ElysiumPractice.forTrades(entry.getValue());
            if (band == ElysiumPractice.UNFAMILIAR) {
                continue;
            }
            player.displayClientMessage(Component.literal(" ")
                    .append(Component.translatable(
                            "elysium.practice.line",
                            Component.translatable(
                                    net.minecraft.core.registries.BuiltInRegistries.ITEM
                                            .get(net.minecraft.resources.ResourceLocation
                                                    .parse(entry.getKey()))
                                            .getDescriptionId()),
                            Component.translatable(band.translationKey()),
                            entry.getValue(), band.percent()))
                    .withStyle(ChatFormatting.AQUA), false);
        }
    }

    /**
     * How this villager is doing, and every reason for it.
     *
     * Printed as the arithmetic rather than as a description: each reason with
     * its own weight, and the total they add to. §8.3 asks that a player be
     * able to ask why, and the only answer that can never be wrong is the one
     * the number was made of — so this prints the sum, not a summary of it.
     *
     * Relief is shown alongside the burdens, in the same list. A villager who
     * is calm <em>because</em> they are home is the more interesting half of
     * the answer, and a readout that showed only what was wrong would make
     * every improvement invisible.
     */
    private static void sayHowTheyAre(Villager villager, Player player) {
        ElysiumStress.Reading reading = ElysiumStressRecord.readingFor(villager);
        ChatFormatting colour = switch (reading.band()) {
            case CRISIS -> ChatFormatting.RED;
            case DISTRESSED -> ChatFormatting.GOLD;
            case STRAINED -> ChatFormatting.YELLOW;
            case RECOVERY -> ChatFormatting.AQUA;
            default -> ChatFormatting.GREEN;
        };
        player.displayClientMessage(Component.translatable(
                        "elysium.stress.header",
                        Component.translatable(reading.band().translationKey()),
                        reading.total())
                .withStyle(colour), false);

        if (reading.reasons().isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("elysium.stress.nothing")
                            .withStyle(ChatFormatting.DARK_GRAY), false);
            return;
        }
        sayWhatTheyAreGoodAt(villager, player);
        for (ElysiumStressor reason : reading.ordered()) {
            player.displayClientMessage(Component.literal(" ")
                    .append(Component.translatable(reason.translationKey()))
                    .append(Component.literal(" (" + (reason.weight() > 0 ? "+" : "")
                                    + reason.weight() + ")")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .withStyle(reason.relieves() ? ChatFormatting.GREEN
                            : ChatFormatting.RED), false);
        }
    }
}
