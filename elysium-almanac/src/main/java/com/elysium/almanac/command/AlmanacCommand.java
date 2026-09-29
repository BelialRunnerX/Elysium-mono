package com.elysium.almanac.command;

import com.elysium.almanac.ElysiumAlmanac;
import com.elysium.almanac.event.AlmanacProgress;
import com.elysium.almanac.event.AlmanacRewards;
import com.elysium.almanac.registry.AlmanacAttachments;
import com.elysium.almanac.rules.AlmanacRules;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Set;

/**
 * {@code /almanac}: the book from the console.
 *
 * <p>For the tests and for an operator, not for play: {@code status} says how
 * far a player is, {@code claim} takes a reward the same way the book's button
 * does, and the two that change what a player has done — {@code finish} and
 * {@code reset} — want an operator. The rest of the module never calls this;
 * the book itself has no commands in it.
 */
@EventBusSubscriber(modid = ElysiumAlmanac.MODID)
public final class AlmanacCommand {

    private AlmanacCommand() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(root());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("almanac")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("claim")
                        .then(Commands.argument("quest", StringArgumentType.word())
                                .executes(context -> claim(context.getSource(),
                                        StringArgumentType.getString(context, "quest")))))
                .then(Commands.literal("finish").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("quest", StringArgumentType.word())
                                .executes(context -> finish(context.getSource(),
                                        StringArgumentType.getString(context, "quest")))))
                .then(Commands.literal("reset").requires(source -> source.hasPermission(2))
                        .executes(context -> reset(context.getSource())));
    }

    private static int status(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("elysiumalmanac.command.nobody"));
            return 0;
        }
        Set<String> done = AlmanacAttachments.done(player);
        done.remove(AlmanacProgress.GIVEN);
        int claimed = 0;
        for (String quest : done) {
            if (AlmanacAttachments.claimed(player, quest)) {
                claimed++;
            }
        }
        int open = AlmanacRules.openTo(AlmanacAttachments.done(player)).size();
        int finished = done.size();
        int paid = claimed;
        source.sendSuccess(() -> Component.translatable("elysiumalmanac.command.status",
                String.valueOf(finished), String.valueOf(AlmanacRules.QUESTS.size()),
                String.valueOf(paid), String.valueOf(open)), false);
        return finished;
    }

    private static int claim(CommandSourceStack source, String quest) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("elysiumalmanac.command.nobody"));
            return 0;
        }
        String why = AlmanacRewards.claim(player, quest);
        if (why != null) {
            source.sendFailure(Component.translatable(why));
            return 0;
        }
        return 1;
    }

    /** Mark a quest done as if the player had done it: the tests' way past a step. */
    private static int finish(CommandSourceStack source, String quest) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("elysiumalmanac.command.nobody"));
            return 0;
        }
        if (AlmanacRules.quest(quest) == null) {
            source.sendFailure(Component.translatable(AlmanacRewards.WHY_UNKNOWN));
            return 0;
        }
        if (!AlmanacAttachments.finish(player, quest)) {
            source.sendFailure(Component.translatable("elysiumalmanac.command.done_already"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("elysiumalmanac.finished",
                Component.translatable("elysiumalmanac.quest." + quest))
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("elysiumalmanac.command.nobody"));
            return 0;
        }
        AlmanacAttachments.reset(player);
        // The book was handed out on a first join that has already happened.
        AlmanacAttachments.finish(player, AlmanacProgress.GIVEN);
        source.sendSuccess(() -> Component.translatable("elysiumalmanac.command.reset"), false);
        return 1;
    }
}
