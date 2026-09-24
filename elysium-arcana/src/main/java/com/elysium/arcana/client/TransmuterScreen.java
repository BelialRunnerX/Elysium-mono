package com.elysium.arcana.client;

import com.elysium.lib.client.ElysiumChooser;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.arcana.network.ArcanaPayloads;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything you know, with a price against each.
 *
 * <p>The same chooser as every other list in this pack. What differs is the
 * second line, which is the price and — when you cannot afford it — how short
 * you are, because "8,192, and you have 6,000" is a sentence a player can act
 * on and a greyed-out row is not.
 */
public class TransmuterScreen extends ElysiumChooser {

    private final ArcanaPayloads.OpenTransmutations known;

    public TransmuterScreen(ArcanaPayloads.OpenTransmutations known) {
        super(Component.translatable("elysiumarcana.transmuter.title"));
        this.known = known;
    }

    @Override
    protected List<Choice> choices() {
        List<Choice> rows = new ArrayList<>(known.keys().size());
        for (int at = 0; at < known.keys().size(); at++) {
            long price = known.prices().get(at);
            boolean affordable = known.motes() >= price;
            rows.add(new Choice(
                    Component.translatable(known.names().get(at)),
                    affordable
                            ? Component.translatable("elysiumarcana.row",
                                    String.valueOf(price))
                            : Component.translatable("elysiumarcana.row_short",
                                    String.valueOf(price),
                                    String.valueOf(price - known.motes())),
                    affordable ? ElysiumPalette.VOID : ElysiumPalette.TEXT_FAINT,
                    affordable));
        }
        return rows;
    }

    @Override
    protected Component status() {
        return Component.translatable("elysiumarcana.transmuter.motes",
                String.valueOf(known.motes()));
    }

    @Override
    protected Component nothing() {
        return Component.translatable("elysiumarcana.transmuter.alone");
    }

    @Override
    protected void chose(int index) {
        // A stack if you are holding shift, one otherwise. The same gesture
        // the rest of the game uses for "all of them", so nobody has to be
        // told about it.
        PacketDistributor.sendToServer(new ArcanaPayloads.ChooseTransmutation(
                known.keys().get(index), hasShiftDown() ? 64 : 1));
        onClose();
    }
}
