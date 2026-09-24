package com.elysium.wilds.client;

import com.elysium.lib.client.ElysiumChooser;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.wilds.network.WildPayloads;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Pick a place to look for.
 *
 * <p>The same chooser the rockets, the waygates and the waystones use, because
 * five ways of deciding where to go are five ways of doing one thing and
 * should not look like five mods.
 */
public class CompassScreen extends ElysiumChooser {

    private final List<String> places;

    public CompassScreen(List<String> places) {
        super(Component.translatable("elysiumwilds.compass.title"));
        this.places = List.copyOf(places);
    }

    @Override
    protected List<Choice> choices() {
        List<Choice> rows = new ArrayList<>(places.size());
        for (String place : places) {
            rows.add(new Choice(Component.literal(prettied(place)),
                    Component.literal(place), ElysiumPalette.DIMENSIONAL, true));
        }
        return rows;
    }

    /** "minecraft:dark_forest" reads as "Dark Forest", because a player is not a file. */
    private static String prettied(String place) {
        String tail = place.contains(":") ? place.substring(place.indexOf(':') + 1) : place;
        StringBuilder made = new StringBuilder(tail.length());
        boolean start = true;
        for (char letter : tail.toCharArray()) {
            if (letter == '_') {
                made.append(' ');
                start = true;
            } else if (start) {
                made.append(Character.toUpperCase(letter));
                start = false;
            } else {
                made.append(letter);
            }
        }
        return made.toString();
    }

    /**
     * Typing narrows the list, because this is the one screen in the pack
     * where it has to.
     *
     * <p>A modded world knows well over a hundred biomes. Seven rows a page is
     * twenty pages, and nobody pages through twenty pages — they close the
     * screen and look it up on a wiki instead, which is the mod having failed
     * at the one thing it is for.
     */
    @Override
    protected boolean filterable() {
        return true;
    }

    @Override
    protected Component status() {
        return Component.translatable("elysiumwilds.compass.count", places.size());
    }

    @Override
    protected Component nothing() {
        return Component.translatable("elysiumwilds.compass.nomatch");
    }

    @Override
    protected void chose(int index) {
        if (index < 0 || index >= places.size()) {
            return;
        }
        PacketDistributor.sendToServer(new WildPayloads.LookFor(places.get(index)));
        onClose();
    }
}
