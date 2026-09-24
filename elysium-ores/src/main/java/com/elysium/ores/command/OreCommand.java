package com.elysium.ores.command;

import com.elysium.ores.ElysiumOres;
import com.elysium.ores.data.OreCatalogue;
import com.elysium.ores.ore.OreDrops;
import com.elysium.ores.unify.OreUnification;
import com.elysium.ores.unify.UnifyRecipes;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;

/**
 * {@code /elysium ores} — what this pack decided, in one screen.
 *
 * <h2>Why this exists</h2>
 *
 * A unifier that works is invisible, and an invisible system is one nobody can
 * debug. When a player asks "why did my steel turn into that steel", the
 * answer has to be available in the game rather than in a log file: which
 * families were unified, what each one settled on, what was left alone by the
 * guard, and how many ores were filled in where.
 *
 * <p>Available to anybody rather than to operators. Nothing it prints can be
 * used to change anything, and the player asking the question is usually not
 * the one who owns the server.
 */
@EventBusSubscriber(modid = ElysiumOres.MODID)
public final class OreCommand {

    private OreCommand() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(root());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("elysium").then(Commands.literal("ores")
                .executes(context -> report(context.getSource())));
    }

    private static int report(CommandSourceStack source) {
        Map<String, Map<String, net.minecraft.world.item.Item>> canonicals =
                OreUnification.canonicals();
        if (canonicals.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.elysiumores.none")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        int filled = 0;
        for (OreCatalogue.Entry entry : OreCatalogue.ENTRIES) {
            if (OreDrops.present(entry.family(), entry.vanilla())) {
                filled++;
            }
        }
        final int placed = filled;
        source.sendSuccess(() -> Component.translatable("commands.elysiumores.header",
                        OreUnification.families(), placed)
                .withStyle(ChatFormatting.GOLD), false);

        for (Map.Entry<String, Map<String, net.minecraft.world.item.Item>> kind
                : canonicals.entrySet()) {
            for (Map.Entry<String, net.minecraft.world.item.Item> family
                    : kind.getValue().entrySet()) {
                String name = kind.getKey() + "/" + family.getKey();
                String won = String.valueOf(BuiltInRegistries.ITEM.getKey(family.getValue()));
                source.sendSuccess(() -> Component.translatable(
                                "commands.elysiumores.unified", name, won)
                        .withStyle(ChatFormatting.AQUA), false);
            }
        }
        if (OreUnification.protectedCount() > 0) {
            source.sendSuccess(() -> Component.translatable(
                            "commands.elysiumores.ambiguous",
                            OreUnification.protectedCount())
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return UnifyRecipes.injected();
    }
}
