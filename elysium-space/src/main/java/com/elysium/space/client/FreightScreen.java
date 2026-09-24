package com.elysium.space.client;

import com.elysium.lib.client.ElysiumChooser;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.space.network.SpacePayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a crate goes: one row per terminal, with what the run costs.
 *
 * <p>The waygate's chooser with a price on it. A gate is instant and free and
 * a freight run is neither, so the row carries both numbers — how much fuel
 * the run burns and how long it is in the air.
 *
 * <p>The prices arrive as words rather than as numbers, worked out on the
 * server, because what a route costs is a fact about the world and a client
 * that computed it would be a client that could be wrong about it. That is
 * also why no row here is greyed: affordability is the server's judgement and
 * this screen is not entitled to make it.
 */
public class FreightScreen extends ElysiumChooser {

    private final SpacePayloads.OpenFreight offer;
    private final BlockPos from;

    public FreightScreen(SpacePayloads.OpenFreight offer, BlockPos from) {
        super(Component.translatable("elysiumspace.freight.title"));
        this.offer = offer;
        this.from = from;
    }

    @Override
    protected List<Choice> choices() {
        List<Choice> rows = new ArrayList<>(offer.keys().size());
        for (int at = 0; at < offer.keys().size(); at++) {
            rows.add(new Choice(Component.literal(offer.names().get(at)),
                    Component.translatable("elysiumspace.freight.run",
                            offer.costs().get(at)),
                    ElysiumPalette.KINETIC, true));
        }
        return rows;
    }

    @Override
    protected Component status() {
        return Component.translatable("elysiumspace.freight.aboard", offer.stacks(), offer.fuel());
    }

    @Override
    protected Component nothing() {
        return Component.translatable("elysiumspace.freight.alone");
    }

    @Override
    protected int panelWidth() {
        return 280;
    }

    @Override
    protected void chose(int index) {
        PacketDistributor.sendToServer(
                new SpacePayloads.ChooseFreight(from, offer.keys().get(index)));
        onClose();
    }
}
