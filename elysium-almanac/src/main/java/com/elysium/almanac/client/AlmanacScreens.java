package com.elysium.almanac.client;

import net.minecraft.client.Minecraft;

/**
 * The one door between the item and the screens.
 *
 * <p>A class of its own so that {@code AlmanacItem} — which exists on a
 * dedicated server too — names exactly one client class, and that class names
 * everything else. Anything that draws is behind this door.
 */
public final class AlmanacScreens {

    private AlmanacScreens() {
    }

    /** Open the book at its contents page. */
    public static void open() {
        Minecraft.getInstance().setScreen(new ChapterScreen());
    }
}
