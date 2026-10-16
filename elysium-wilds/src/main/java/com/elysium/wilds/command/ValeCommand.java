package com.elysium.wilds.command;

import com.elysium.wilds.ElysiumWilds;
import com.elysium.wilds.level.ValeLinks;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/**
 * {@code /elysium vale trips}: the world's record of its trips into the Vale,
 * read back in one line.
 *
 * <pre>Vale trips: first N, out to spawn M, last LINE</pre>
 *
 * N the first trips the world has had, M the ways out of the Vale that put the
 * player asking at the world's spawn for want of a way in on record, and LINE
 * the line the latest first trip logged, word for word, or {@code none} before
 * the first. All of it is kept in {@link ValeLinks}, the mod's own saved data.
 *
 * <h2>Why this exists</h2>
 *
 * The live Vale stages (tools/livetest/stages) read it, the footing stage
 * before the gate and after the crossing to hold a first-trip line from the
 * log against the trip the world counted, the vale stage before the gate and
 * at home to tell the way back from a way out to the spawn. A line in the log
 * is no proof by itself: a custom name holding line breaks writes a whole line
 * of its own into the client's log when its mob dies, and a log that rolled
 * over at midnight keeps the line it held in a file nobody reads. Until 16
 * October 2026 (round 5, OP1) the record was command storage, and the operator
 * who can name a mob so could set that to agree with {@code /data modify}.
 * Saved data has no command that writes it, and this one writes nothing.
 *
 * <p>The words are the stages' and are matched whole, as the first-trip line
 * is, so they are literal text rather than a translation. Operators only, as
 * {@code /data get} is: it names other players' rings.
 */
@EventBusSubscriber(modid = ElysiumWilds.MODID)
public final class ValeCommand {

    /** The one line it answers (see the class comment), never reworded without the stages. */
    private static final String RECORD = "Vale trips: first %d, out to spawn %d, last %s";

    private ValeCommand() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(root());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("elysium").then(Commands.literal("vale").then(Commands.literal("trips")
                .requires(source -> source.hasPermission(2))
                .executes(context -> trips(context.getSource()))));
    }

    private static int trips(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ValeLinks links = ValeLinks.get(source.getServer());
        String last = links.lastFirstTrip().isEmpty() ? "none" : links.lastFirstTrip();
        String said = String.format(Locale.ROOT, RECORD, links.firstTrips(), links.outToSpawnFor(player.getUUID()), last);
        source.sendSuccess(() -> Component.literal(said), false);
        return links.firstTrips();
    }
}
