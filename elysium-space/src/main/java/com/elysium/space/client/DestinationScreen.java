package com.elysium.space.client;

import com.elysium.lib.client.ElysiumChooser;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.space.network.SpacePayloads;
import com.elysium.space.world.SpaceRoute;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Where to go: one row per world, what the trip burns, and "Land here".
 *
 * <p>A world the tank cannot reach is still listed, greyed, with its price on
 * it. Hiding it would leave the player looking at a shorter list with no way
 * to find out what the missing worlds cost, which is the question they opened
 * the screen to answer.
 */
public class DestinationScreen extends ElysiumChooser {

    private final SpacePayloads.OpenDestinations offer;
    private final List<String> worlds;

    public DestinationScreen(SpacePayloads.OpenDestinations offer) {
        super(Component.translatable("elysiumspace.destinations.title"));
        this.offer = offer;
        this.worlds = List.copyOf(offer.options());
    }

    @Override
    protected List<Choice> choices() {
        List<Choice> rows = new ArrayList<>(worlds.size());
        for (String world : worlds) {
            int cost = fuelFor(world);
            rows.add(new Choice(Component.translatable(dimensionKey(world)),
                    Component.translatable("elysiumspace.destinations.burn", cost),
                    ElysiumPalette.PLASMA, cost >= 0 && cost <= offer.fuel()));
        }
        return rows;
    }

    private int fuelFor(String world) {
        return SpaceRoute.fuelFor(offer.from(), world,
                com.elysium.space.entity.RocketEntity.parseMotor(offer.motor()));
    }

    @Override
    protected Component status() {
        return Component.translatable("elysiumspace.destinations.fuel", offer.fuel(), offer.tank());
    }

    @Override
    protected Component nothing() {
        return Component.translatable("elysiumspace.destinations.nowhere");
    }

    @Override
    protected Component footer() {
        return Component.translatable("elysiumspace.destinations.land");
    }

    @Override
    protected void footerPressed() {
        choose("");
    }

    @Override
    protected void chose(int index) {
        choose(worlds.get(index));
    }

    private static String dimensionKey(String world) {
        return world.equals("overworld") ? "elysiumspace.destinations.overworld"
                : "dimension.elysiumspace." + world;
    }

    private void choose(String world) {
        PacketDistributor.sendToServer(new SpacePayloads.ChooseDestination(world));
        onClose();
    }
}
