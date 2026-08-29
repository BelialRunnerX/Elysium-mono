package com.elysium.lib.network;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.character.ElysiumClass;
import com.elysium.lib.character.ElysiumRace;
import com.elysium.lib.stats.ElysiumStat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Packet registration, and the server side of every handler.
 *
 * <h2>Why the client handler is a lambda</h2>
 *
 * Registration runs on both the client and a dedicated server, so a direct
 * method reference to a client-only class would be resolved during
 * registration and crash a server on startup. A lambda body is not resolved
 * until it runs, and a client-bound packet only ever runs on a client, so
 * {@code ElysiumClientHooks} is never loaded server-side.
 *
 * <h2>Why nothing a client sends is trusted</h2>
 *
 * Both inbound packets carry strings a modified client could put anything in.
 * Each is looked up against the enum and dropped if it does not resolve, and
 * point spending is clamped against the balance the server holds — never
 * against a number the client supplied.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ElysiumNetwork {

    private ElysiumNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                ElysiumPayloads.OpenCharacter.TYPE,
                ElysiumPayloads.OpenCharacter.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ElysiumClientHooks.openCharacterScreen(payload)));

        registrar.playToClient(
                ElysiumPayloads.SyncCharacter.TYPE,
                ElysiumPayloads.SyncCharacter.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ElysiumClientHooks.syncCharacter(payload)));

        registrar.playToServer(
                ElysiumPayloads.ChooseCharacter.TYPE,
                ElysiumPayloads.ChooseCharacter.STREAM_CODEC,
                ElysiumNetwork::onChoose);

        registrar.playToServer(
                ElysiumPayloads.SpendPoints.TYPE,
                ElysiumPayloads.SpendPoints.STREAM_CODEC,
                ElysiumNetwork::onSpend);
    }

    // ------------------------------------------------------------------
    // Server side
    // ------------------------------------------------------------------

    /**
     * Applies a character choice, once.
     *
     * <b>Both halves are permanent.</b> The packet is a plain play-phase
     * message with no cost, no cooldown and no proximity check, so honouring a
     * repeat class choice meant a modified client could swap to Factor for the
     * extra-drop chance, Warden for doubled reflection and Marksman for the
     * bigger critical between one swing and the next. Anything already set is
     * kept and the packet is ignored.
     *
     * Changing class later is a thing the design wants — at an Ascension Forge,
     * for a price — but that has to be a workstation interaction with a cost
     * attached, not a free packet. Until it exists, a class is for life.
     */
    private static void onChoose(ElysiumPayloads.ChooseCharacter payload, IPayloadContext context) {
        Player player = context.player();
        if (player == null) {
            return;
        }

        ElysiumRace race = ElysiumRace.REGISTRY.get(payload.race());
        ElysiumClass job = ElysiumClass.REGISTRY.get(payload.job());
        if (race == null || job == null) {
            return;
        }

        if (ElysiumCharacter.hasChosen(player)) {
            return;
        }
        if (ElysiumCharacter.getRace(player) == null) {
            ElysiumCharacter.setRace(player, race);
        }
        if (ElysiumCharacter.getElysiumClass(player) == null) {
            ElysiumCharacter.setElysiumClass(player, job);
        }

        ElysiumRace settled = ElysiumCharacter.getRace(player);
        ElysiumClass settledJob = ElysiumCharacter.getElysiumClass(player);
        if (settled == null || settledJob == null) {
            return;
        }
        player.displayClientMessage(Component.translatable("elysium.character.chosen",
                        settled.getDisplayName(), settledJob.getDisplayName())
                .withStyle(ChatFormatting.GOLD), false);
    }

    /**
     * Spends free points, one at a time, up to whatever the player actually
     * has. The amount in the packet is a request, not an authority.
     */
    private static void onSpend(ElysiumPayloads.SpendPoints payload, IPayloadContext context) {
        Player player = context.player();
        if (player == null) {
            return;
        }

        ElysiumStat stat = ElysiumStat.REGISTRY.get(payload.stat());
        if (stat == null) {
            return;
        }

        int requested = Math.max(0, Math.min(payload.amount(), ElysiumCharacter.getUnspentPoints(player)));
        for (int i = 0; i < requested; i++) {
            if (!ElysiumCharacter.spendPoint(player, stat)) {
                break;
            }
        }
        sendSheet(player);
    }

    // ------------------------------------------------------------------

    /**
     * Pushes the current sheet to a player, opening their screen.
     *
     * The one place the server initiates: first join, the Codex, the command,
     * and every point spent all funnel through here so the screen can never
     * show a figure the server does not hold.
     */
    public static void sendSheet(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        ElysiumPacketSender.toPlayer(player, new ElysiumPayloads.OpenCharacter(
                CharacterSheet.pack(player), ElysiumCharacter.getUnspentPoints(player)));
    }

    /**
     * Pushes the current sheet without opening anything, for the HUD.
     *
     * Separate from {@link #sendSheet} because that one takes over the screen,
     * and this one is sent on a timer — the two must never be the same call, or
     * a player mid-fight has their character sheet thrown open at them because
     * a mob they killed moved their Suspicion by one point.
     */
    public static void syncSheet(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        ElysiumPacketSender.toPlayer(player, new ElysiumPayloads.SyncCharacter(
                CharacterSheet.pack(player), ElysiumCharacter.getUnspentPoints(player)));
    }
}
