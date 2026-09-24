package com.elysium.colonies.port;

import com.elysium.colonies.ElysiumColonies;
import com.elysium.colonies.citizen.ColonyMemory;
import com.elysium.colonies.colony.Colonies;
import com.elysium.colonies.rules.PortRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The block at the bottom of a space elevator.
 *
 * <h2>What talking to it does</h2>
 *
 * Reads the column above it, works out whose colony it is standing in, writes
 * that down, and says what it found. All four in one action, deliberately: a
 * player who has just laid two hundred blocks wants to know whether it worked,
 * and a system that required them to also find a menu, press a button and
 * confirm would be asking them to do administration to be told a fact.
 *
 * <h2>And when it is wrong</h2>
 *
 * It says so, in the terms the player can act on — the column stops at this
 * height, or it is eighty blocks short, or something is standing over the top
 * of it. Nothing about a spaceport is hidden: there is no efficiency rating,
 * no fuel gauge and no hidden condition. A port either stands or it does not,
 * and if it does not, the block tells you where to look.
 */
public class PortAnchorBlock extends Block {

    public PortAnchorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.PASS;
        }
        report(server, pos, player);
        return InteractionResult.CONSUME;
    }

    /**
     * Reads the port, records it, and tells somebody what it found.
     *
     * The recording is the point rather than a side effect: nothing else in
     * this module goes looking for spaceports, because a daily sweep of every
     * loaded chunk for a block that might be an anchor is a cost paid by every
     * server whether or not anybody has built one. A port enters the registry
     * when it is built and when it is inspected, and leaves it when its anchor
     * is broken.
     */
    public static void report(ServerLevel level, BlockPos pos, Player player) {
        Colonies.Settlement town = townAt(level, pos);
        PortRegistry ports = PortRegistry.of(level.getServer());
        if (town == null) {
            // Not in anybody's colony. The elevator is a very tall sculpture,
            // which is allowed, and it ships nothing.
            ports.forgetAt(level.dimension().location(), pos);
            say(player, Component.translatable("elysium.colony.port.nowhere")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        SpaceElevator.Reading reading = SpaceElevator.read(level, pos);
        ports.record(new PortRegistry.Berth(level.dimension().location(), town.id(),
                pos.immutable(), reading.tether(), reading.working(), town.day(),
                reading.legs()));
        if (!reading.working()) {
            say(player, fault(reading).withStyle(ChatFormatting.RED));
            return;
        }
        long treasury = ColonyMemory.of(level).ledgerOf(town.id()).treasury();
        say(player, Component.translatable("elysium.colony.port.ready", town.name(),
                        reading.tether(), reading.throughput(), treasury)
                .withStyle(ChatFormatting.AQUA));
        // Said separately rather than folded into the line above, because a
        // port with no legs is a perfectly good port and the message about it
        // should read as an offer rather than as a fault.
        say(player, Component.translatable("elysium.colony.port.legs",
                        reading.legs(), PortRules.LEGS, PortRules.PER_LEG)
                .withStyle(reading.legs() >= PortRules.LEGS
                        ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        ElysiumColonies.LOGGER.info("{} has a spaceport {} blocks tall at {}",
                town.name(), reading.tether(), pos);
    }

    /**
     * What is wrong with it, in a sentence a player can act on.
     *
     * One message per complaint rather than one message with the complaint
     * pasted into it, so each says the thing that is actually useful: where
     * the column stops, how far short it is, or that the sky is blocked.
     */
    private static MutableComponent fault(SpaceElevator.Reading reading) {
        String complaint = SpaceElevator.complaint(reading);
        return switch (complaint) {
            case "roofed" -> Component.translatable("elysium.colony.port.roofed");
            case "short" -> Component.translatable("elysium.colony.port.short",
                    reading.tether(), PortRules.MINIMUM_TETHER);
            default -> Component.translatable("elysium.colony.port.broken",
                    reading.fault() == null ? 0 : reading.fault().getY());
        };
    }

    /** Whose colony this block is standing in, or null. */
    public static Colonies.Settlement townAt(ServerLevel level, BlockPos pos) {
        for (Colonies.Settlement town : Colonies.in(level)) {
            if (Colonies.inside(level, town.id(), pos)) {
                return town;
            }
        }
        return null;
    }

    private static void say(Player player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, false);
        }
    }

    /**
     * Placing the anchor is the same question as asking it.
     *
     * A player who lays the last block of a spaceport should not have to then
     * right-click it to find out whether it counts. If it does not, they hear
     * why while the column is still in front of them.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) {
            report(server, pos, placer instanceof Player player ? player : null);
        }
    }

    /**
     * Breaking the anchor closes the port.
     *
     * Guarded on the block actually changing to something else, because a
     * block state update — the tether above it being replaced, a neighbour
     * changing — passes through here too, and a port that deregistered itself
     * every time somebody walked past would be a port nobody could keep.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server) {
            if (PortRegistry.of(server.getServer())
                    .forgetAt(server.dimension().location(), pos)) {
                ElysiumColonies.LOGGER.info("the spaceport at {} was dismantled", pos);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
