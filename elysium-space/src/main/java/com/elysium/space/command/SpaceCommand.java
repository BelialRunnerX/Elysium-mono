package com.elysium.space.command;

import com.elysium.lib.hazard.ElysiumHazard;
import com.elysium.lib.hazard.ElysiumHazardField;
import com.elysium.space.ElysiumSpace;
import com.elysium.space.oxygen.OxygenField;
import com.elysium.space.world.SpaceWorld;
import com.elysium.space.world.SpaceWorlds;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /elysiumspace travel <world>} and {@code /elysiumspace air}, for
 * operators. A stand-in for rockets until S4, so every world can be tested
 * now. {@code overworld} is a destination too, for getting home.
 */
@EventBusSubscriber(modid = ElysiumSpace.MODID)
public final class SpaceCommand {

    private SpaceCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("elysiumspace")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("travel")
                        .then(Commands.argument("world", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        java.util.stream.Stream.concat(SpaceWorld.ALL.stream().map(SpaceWorld::path),
                                                java.util.stream.Stream.of("overworld")), b))
                                .executes(ctx -> travel(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "world")))))
                .then(Commands.literal("air").executes(ctx -> air(ctx.getSource()))));
    }

    private static int travel(CommandSourceStack src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel target;
        if (name.equals("overworld")) {
            target = src.getServer().getLevel(Level.OVERWORLD);
        } else {
            SpaceWorld world = SpaceWorld.named(name);
            target = world == null ? null : src.getServer().getLevel(SpaceWorlds.key(world));
        }
        if (target == null) {
            src.sendFailure(Component.translatable("commands.elysiumspace.travel.unknown", name));
            return 0;
        }
        BlockPos top = com.elysium.space.world.SpaceArrival.groundAt(target, 0, 0);
        player.teleportTo(target, top.getX() + 0.5D, top.getY(), top.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        src.sendSuccess(() -> Component.translatable("commands.elysiumspace.travel.done", name), true);
        return 1;
    }

    private static int air(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        StringBuilder hazards = new StringBuilder();
        for (ElysiumHazard hazard : ElysiumHazard.REGISTRY.all()) {
            float f = ElysiumHazardField.intensity(level, pos, hazard);
            if (f > 0.0F) {
                hazards.append(' ').append(hazard.getId().getPath())
                        .append('=').append(Math.round(f * 100.0F)).append('%');
            }
        }
        boolean filled = OxygenField.isOxygenated(level, pos);
        String line = (filled ? "oxygenated" : "not oxygenated") + ";"
                + (hazards.length() == 0 ? " no hazards" : hazards.toString());
        src.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }
}
