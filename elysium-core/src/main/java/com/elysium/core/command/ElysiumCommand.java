package com.elysium.core.command;

import com.elysium.core.Elysium;
import com.elysium.core.item.ElysiumMaterialReport;
import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.character.ElysiumClass;
import com.elysium.lib.character.ElysiumRace;
import com.elysium.lib.network.ElysiumNetwork;
import com.elysium.lib.standing.ElysiumStanding;
import com.elysium.lib.stats.ElysiumStat;
import com.elysium.lib.stats.ElysiumStats;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /elysium} — the parts of the character system that want words rather
 * than a screen.
 *
 * <ul>
 *   <li>{@code sheet} — reopen the character screen.</li>
 *   <li>{@code stats} — print the totals into chat, which is the version you
 *       can screenshot, paste, or read on a server with the GUI scaled to
 *       something unfortunate.</li>
 *   <li>{@code standing} — Favor and Suspicion, unchanged.</li>
 *   <li>{@code respec} — hand every spent point back.</li>
 *   <li>{@code materials} — which gear materials this pack actually supplies,
 *       which are registered with nothing to make them from, and which of the
 *       pack's own metals Elysium has no gear for. The last of those is the
 *       one worth running: the shipped table cannot cover every mod, and
 *       without this a missing metal is indistinguishable from a deliberate
 *       omission.</li>
 * </ul>
 *
 * Deliberately no {@code race} or {@code class} subcommand. Race is chosen
 * once and class is changed at an Ascension Forge; a command that reassigned
 * either would make the screen decorative and the forge pointless.
 */
@EventBusSubscriber(modid = Elysium.MODID)
public final class ElysiumCommand {

    private ElysiumCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("elysium")
                .then(Commands.literal("sheet").executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    ElysiumNetwork.sendSheet(player);
                    return 1;
                }))
                .then(Commands.literal("materials").executes(context -> {
                    printMaterials(context.getSource());
                    return 1;
                }))
                .then(Commands.literal("stats").executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    printStats(context.getSource(), player);
                    return 1;
                }))
                .then(Commands.literal("standing").executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    context.getSource().sendSuccess(() -> ElysiumStanding.report(player), false);
                    return 1;
                }))
                .then(Commands.literal("tier").executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    printTier(context.getSource(), player);
                    return 1;
                }))
                // Every block that needs a tool has one tagged to mine it, and
                // none a player can place is unbreakable. Operators only.
                .then(Commands.literal("audit")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("harvest")
                                .executes(context -> HarvestAudit.run(context.getSource()))))
                // Levels for a test: experience granted through the same path a
                // kill grants it, until the level asked for. Operators only.
                .then(Commands.literal("level")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 400))
                                .executes(context -> {
                                    Player player = context.getSource().getPlayerOrException();
                                    int wanted = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level");
                                    int guard = 0;
                                    while (com.elysium.lib.character.ElysiumCharacter.getLevel(player) < wanted && guard++ < 10000) {
                                        com.elysium.lib.character.ElysiumCharacter.addXp(player,
                                                com.elysium.lib.character.ElysiumCharacter.xpToNext(
                                                        com.elysium.lib.character.ElysiumCharacter.getLevel(player)));
                                    }
                                    com.elysium.lib.network.ElysiumNetwork.syncSheet(player);
                                    context.getSource().sendSuccess(() -> Component.literal("Level "
                                            + com.elysium.lib.character.ElysiumCharacter.getLevel(player)), false);
                                    return 1;
                                })))
                .then(Commands.literal("tree")
                        .then(Commands.literal("points").executes(context -> {
                            Player player = context.getSource().getPlayerOrException();
                            context.getSource().sendSuccess(() -> Component.translatable("elysium.tree.points",
                                    com.elysium.lib.tree.ElysiumTreeState.free(player),
                                    com.elysium.lib.tree.ElysiumTreeState.spent(player),
                                    com.elysium.lib.tree.ElysiumTreeState.erasures(player)), false);
                            return 1;
                        }))
                        .then(Commands.literal("held").executes(context -> {
                            Player player = context.getSource().getPlayerOrException();
                            context.getSource().sendSuccess(() -> Component.literal(
                                    String.join(" ", com.elysium.lib.tree.ElysiumTreeState.held(player))), false);
                            return 1;
                        }))
                        .then(Commands.literal("allocate")
                                .then(Commands.argument("node", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(context -> {
                                            Player player = context.getSource().getPlayerOrException();
                                            String node = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "node");
                                            String why = com.elysium.lib.tree.ElysiumTreeState.allocate(player, node);
                                            if (why != null) {
                                                context.getSource().sendFailure(Component.translatable(why));
                                                return 0;
                                            }
                                            com.elysium.lib.network.ElysiumNetwork.syncSheet(player);
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "elysium.tree.allocated", node), false);
                                            return 1;
                                        })))
                        .then(Commands.literal("refund")
                                .then(Commands.argument("node", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(context -> {
                                            Player player = context.getSource().getPlayerOrException();
                                            String node = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "node");
                                            String why = com.elysium.lib.tree.ElysiumTreeState.refund(player, node, true);
                                            if (why != null) {
                                                context.getSource().sendFailure(Component.translatable(why));
                                                return 0;
                                            }
                                            com.elysium.lib.network.ElysiumNetwork.syncSheet(player);
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "elysium.tree.refunded", node), false);
                                            return 1;
                                        })))
                        .then(Commands.literal("reset").executes(context -> {
                            Player player = context.getSource().getPlayerOrException();
                            int given = com.elysium.lib.tree.ElysiumTreeState.reset(player);
                            com.elysium.lib.network.ElysiumNetwork.syncSheet(player);
                            context.getSource().sendSuccess(() -> Component.translatable("elysium.tree.reset", given), false);
                            return 1;
                        })))
                .then(Commands.literal("respec").executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    ElysiumCharacter.respec(player);
                    ElysiumNetwork.sendSheet(player);
                    context.getSource().sendSuccess(
                            () -> Component.translatable("elysium.command.respec",
                                            ElysiumCharacter.getUnspentPoints(player))
                                    .withStyle(ChatFormatting.GOLD), false);
                    return 1;
                })));
    }

    /**
     * The material report.
     *
     * Deliberately prints all three lists even when two are empty, because
     * "nothing is missing" is information and an empty response is not.
     */
    private static void printMaterials(CommandSourceStack source) {
        java.util.List<String> present = ElysiumMaterialReport.present();
        java.util.List<String> missing = ElysiumMaterialReport.missing();
        java.util.List<String> uncovered = ElysiumMaterialReport.uncovered();

        source.sendSuccess(() -> Component.translatable("elysium.command.materials.header",
                present.size(), present.size() + missing.size())
                .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.literal(
                        present.isEmpty() ? "-" : String.join(", ", present))
                .withStyle(ChatFormatting.GREEN), false);

        if (!missing.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                            "elysium.command.materials.missing", missing.size())
                    .withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal(String.join(", ", missing))
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }

        if (uncovered.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                            "elysium.command.materials.complete")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return;
        }
        source.sendSuccess(() -> Component.translatable(
                        "elysium.command.materials.uncovered", uncovered.size())
                .withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal(String.join(", ", uncovered))
                .withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.translatable("elysium.command.materials.hint")
                .withStyle(ChatFormatting.DARK_GRAY), false);
    }

    /**
     * Where this player stands, and what it is worth.
     *
     * World tier is the one number every other system in this mod scales
     * against — mobs, loot, enchanting ceilings — and until now it was the one
     * number a player could not see. It is not on the character sheet because
     * it is not a character stat: it is a property of how far they have come
     * and where they are standing, and both halves are printed here.
     */
    private static void printTier(CommandSourceStack source, Player player) {
        int tier = com.elysium.lib.tier.ElysiumTiers.of(player);
        int floor = com.elysium.lib.tier.ElysiumTiers.floorOf(player);
        source.sendSuccess(() -> Component.translatable("elysium.command.tier.header",
                        Component.translatable(
                                com.elysium.lib.tier.ElysiumWorldTier.nameKey(tier)),
                        tier)
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("elysium.command.tier.from",
                        ElysiumCharacter.getLevel(player), floor)
                .withStyle(ChatFormatting.GRAY), false);
        // The share is the thing worth seeing: it is what every percentile
        // effect in the mod is multiplied by, and it never reaches one.
        source.sendSuccess(() -> Component.translatable("elysium.command.tier.share",
                        String.format("%.1f",
                                com.elysium.lib.tier.ElysiumWorldTier.share(tier) * 100.0D))
                .withStyle(ChatFormatting.DARK_GRAY), false);
    }

    private static void printStats(CommandSourceStack source, Player player) {
        ElysiumRace race = ElysiumCharacter.getRace(player);
        ElysiumClass job = ElysiumCharacter.getElysiumClass(player);

        source.sendSuccess(() -> Component.translatable("elysium.command.header",
                        race == null ? Component.translatable("elysium.character.unchosen")
                                : race.getDisplayName(),
                        job == null ? Component.translatable("elysium.character.unchosen")
                                : job.getDisplayName(),
                        ElysiumCharacter.getLevel(player))
                .withStyle(ChatFormatting.GOLD), false);

        for (ElysiumStat stat : ElysiumStat.REGISTRY.all()) {
            int value = ElysiumStats.get(player, stat);
            source.sendSuccess(() -> Component.empty()
                    .append(stat.getDisplayName())
                    .append(Component.literal("  " + value).withStyle(ChatFormatting.WHITE)), false);
        }
    }
}
