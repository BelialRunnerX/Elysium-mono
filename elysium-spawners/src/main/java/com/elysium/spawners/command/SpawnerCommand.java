package com.elysium.spawners.command;

import com.elysium.spawners.ElysiumSpawners;
import com.elysium.spawners.block.ElysiumSpawnerBlockEntity;
import com.elysium.spawners.data.SpawnerTuning;
import com.elysium.spawners.rules.SpawnerRules;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /spawner} — what the spawner you are looking at actually is.
 *
 * <h2>Why a command and not a screen</h2>
 *
 * Everything this would show is a consequence of the runes already in the
 * spawner, and a screen would be a page of read-only numbers with nothing to
 * press. What a player actually wants is the answer to "is this one worth
 * another rune", which is a question asked once in a while and answered in
 * five lines of chat.
 *
 * <h2>Why it needs no permission</h2>
 *
 * It reads. Everything it prints is derivable from what the player is already
 * looking at, so gating it behind operator would only stop the people it was
 * written for.
 */
@EventBusSubscriber(modid = ElysiumSpawners.MODID)
public final class SpawnerCommand {

    private SpawnerCommand() {
    }

    /** How far a player may be from the spawner they are asking about. */
    public static final double REACH = 8.0D;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("spawner").executes(context -> {
            Player player = context.getSource().getPlayerOrException();
            ElysiumSpawnerBlockEntity spawner = lookingAt(player);
            if (spawner == null) {
                context.getSource().sendFailure(
                        Component.translatable("elysiumspawners.command.none"));
                return 0;
            }
            print(context.getSource(), spawner.tuning());
            return 1;
        }));
    }

    /** The spawner in front of the player, or null. */
    private static ElysiumSpawnerBlockEntity lookingAt(Player player) {
        HitResult hit = player.pick(REACH, 0.0F, false);
        if (!(hit instanceof BlockHitResult block)) {
            return null;
        }
        BlockEntity entity = player.level().getBlockEntity(block.getBlockPos());
        return entity instanceof ElysiumSpawnerBlockEntity spawner ? spawner : null;
    }

    /**
     * The five axes with their numbers, then the behaviours that are on.
     *
     * Both the steps and the number they produce, because the step is what a
     * player spent and the number is what they got — and the whole question
     * this command answers is whether one more of the first is worth it.
     */
    private static void print(CommandSourceStack source, SpawnerTuning tuning) {
        source.sendSuccess(() -> Component.translatable("elysiumspawners.command.header")
                .withStyle(ChatFormatting.GOLD), false);
        line(source, "delay", tuning.delaySteps(),
                SpawnerRules.minDelay(tuning.delaySteps()) + "-"
                        + SpawnerRules.maxDelay(tuning.delaySteps()));
        line(source, "count", tuning.countSteps(),
                String.valueOf(SpawnerRules.spawnCount(tuning.countSteps())));
        line(source, "nearby", tuning.nearbySteps(),
                String.valueOf(SpawnerRules.maxNearby(tuning.nearbySteps())));
        line(source, "range", tuning.rangeSteps(),
                tuning.ignoresPlayers() ? String.valueOf(SpawnerRules.ALWAYS_AWAKE)
                        : String.valueOf(SpawnerRules.playerRange(tuning.rangeSteps())));
        line(source, "spread", tuning.spreadSteps(),
                String.valueOf(SpawnerRules.spawnRange(tuning.spreadSteps())));
        StringBuilder on = new StringBuilder();
        for (String flag : SpawnerTuning.FLAGS) {
            if (tuning.flag(flag)) {
                on.append(on.isEmpty() ? "" : ", ").append(flag);
            }
        }
        if (!on.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                            "elysiumspawners.command.behaviours", on.toString())
                    .withStyle(ChatFormatting.DARK_AQUA), false);
        }
        source.sendSuccess(() -> Component.translatable("elysiumspawners.command.spent",
                        tuning.spent()).withStyle(ChatFormatting.DARK_GRAY), false);
    }

    private static void line(CommandSourceStack source, String axis, int steps, String value) {
        source.sendSuccess(() -> Component.translatable(
                        "elysiumspawners.command.axis",
                        Component.translatable("elysiumspawners.axis." + axis),
                        value, steps, SpawnerRules.stepCost(steps))
                .withStyle(ChatFormatting.GRAY), false);
    }
}
