package com.elysium.space.client;

import com.elysium.lib.client.ElysiumChooser;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.space.network.SpacePayloads;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the gate goes: one row per gate anybody has finished.
 *
 * <p>The same chooser as the rocket's and the freight terminal's, because the
 * three ways of crossing the solar system are three ways of doing one thing
 * and should not look like three mods. What differs is the second line: a
 * rocket's row costs fuel, a freight run costs fuel and time, and a gate's row
 * costs nothing and says which world it is on instead. A gate having no price
 * is the point of a gate.
 */
public class WaygateScreen extends ElysiumChooser {

    private final SpacePayloads.OpenWaygates gates;

    public WaygateScreen(SpacePayloads.OpenWaygates gates) {
        super(Component.translatable("elysiumspace.waygate.title"));
        this.gates = gates;
    }

    @Override
    protected List<Choice> choices() {
        List<Choice> rows = new ArrayList<>(gates.keys().size());
        for (int at = 0; at < gates.keys().size(); at++) {
            rows.add(new Choice(Component.literal(gates.names().get(at)),
                    Component.translatable(worldKey(gates.worlds().get(at))),
                    ElysiumPalette.DIMENSIONAL, true));
        }
        return rows;
    }

    @Override
    protected Component status() {
        return Component.translatable("elysiumspace.waygate.count", gates.keys().size());
    }

    @Override
    protected Component nothing() {
        return Component.translatable("elysiumspace.waygate.alone");
    }

    @Override
    protected void chose(int index) {
        PacketDistributor.sendToServer(
                new SpacePayloads.ChooseWaygate(gates.keys().get(index)));
        onClose();
    }

    /**
     * The translation key for a dimension.
     *
     * Vanilla's worlds and this mod's are named in different files, so the
     * namespace decides which — and a world from a third mod falls back to its
     * own id, which is at least true.
     */
    private static String worldKey(String world) {
        int colon = world.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : world.substring(0, colon);
        String path = colon < 0 ? world : world.substring(colon + 1);
        if (namespace.equals("elysiumspace")) {
            return "dimension.elysiumspace." + path;
        }
        if (namespace.equals("minecraft")) {
            return "elysiumspace.destinations." + path;
        }
        return world;
    }
}
