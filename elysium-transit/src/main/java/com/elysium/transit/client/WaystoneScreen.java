package com.elysium.transit.client;

import com.elysium.lib.client.ElysiumChooser;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.transit.network.TransitPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the stone sends you: one row per place you have bound.
 *
 * <p>The same chooser the rocket, the freight terminal and the waygates use,
 * because four ways of crossing a distance are four ways of doing one thing
 * and should not look like four mods. What differs is the second line: a
 * waygate's row costs nothing and says which world it is on, and a waystone's
 * says the world <em>and</em> the price — or, when the stone cannot pay yet,
 * how long until it can, which is the one number a player actually wants.
 */
public class WaystoneScreen extends ElysiumChooser {

    private final TransitPayloads.OpenWaystones places;
    private final BlockPos from;

    public WaystoneScreen(TransitPayloads.OpenWaystones places, BlockPos from) {
        super(Component.translatable("elysiumtransit.waystone.title"));
        this.places = places;
        this.from = from;
    }

    @Override
    protected List<Choice> choices() {
        List<Choice> rows = new ArrayList<>(places.keys().size());
        for (int at = 0; at < places.keys().size(); at++) {
            int wait = places.waits().get(at);
            boolean ready = wait == 0;
            rows.add(new Choice(
                    Component.literal(places.names().get(at)),
                    ready
                            ? Component.translatable("elysiumtransit.waystone.row",
                                    worldKey(places.worlds().get(at)),
                                    String.valueOf(places.prices().get(at)))
                            : Component.translatable("elysiumtransit.waystone.row_wait",
                                    worldKey(places.worlds().get(at)),
                                    String.valueOf(places.prices().get(at)),
                                    String.valueOf(Math.max(1, wait / 20))),
                    ready ? ElysiumPalette.DIMENSIONAL : ElysiumPalette.TEXT_FAINT,
                    ready));
        }
        return rows;
    }

    @Override
    protected Component status() {
        return Component.translatable("elysiumtransit.waystone.count",
                places.keys().size());
    }

    @Override
    protected Component nothing() {
        return Component.translatable("elysiumtransit.waystone.alone");
    }

    @Override
    protected void chose(int index) {
        PacketDistributor.sendToServer(
                new TransitPayloads.ChooseWaystone(from, places.keys().get(index)));
        onClose();
    }

    /** A dimension's own name, so a world added by another mod reads right. */
    private static Component worldKey(String world) {
        String[] parts = world.split(":", 2);
        return parts.length == 2
                ? Component.translatable("dimension." + parts[0] + "." + parts[1])
                : Component.literal(world);
    }
}
