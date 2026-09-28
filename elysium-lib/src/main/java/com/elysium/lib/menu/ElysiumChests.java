package com.elysium.lib.menu;

import com.elysium.lib.ElysiumLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * The game's chest menu under the kit's own screen.
 *
 * <p>A cabinet, a tackle box, a freight terminal, a pack and the per-player
 * loot chests all opened the game's generic chest menu types, which is the
 * game's grey screen: five boxes outside the theme that {@code check_ui.py}
 * could not see, because the rule there is about screens we write and these
 * had none (the client stage, 27 September 2026). The menu logic is still the
 * game's — its slots are exactly what a box wants — but under a type of ours
 * for each row count, so the kit's {@code ElysiumChestScreen} opens for it.
 * Every box in the pack goes through here, and the check refuses the game's.
 */
public final class ElysiumChests {
    private ElysiumChests() {
    }

    /** The row counts a box may have, as the game's own generic menus have them. */
    public static final int MOST_ROWS = 6;

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ElysiumLib.MODID);

    /** One type per row count, {@code TYPES.get(rows - 1)}. */
    public static final List<DeferredHolder<MenuType<?>, MenuType<ChestMenu>>> TYPES = register();

    private static List<DeferredHolder<MenuType<?>, MenuType<ChestMenu>>> register() {
        List<DeferredHolder<MenuType<?>, MenuType<ChestMenu>>> made = new ArrayList<>();
        for (int rows = 1; rows <= MOST_ROWS; rows++) {
            final int count = rows;
            made.add(MENUS.register("chest_" + rows + "_rows", () -> new MenuType<>(
                    (id, inventory) -> new ChestMenu(type(count), id, inventory,
                            new SimpleContainer(count * 9), count),
                    FeatureFlags.VANILLA_SET)));
        }
        return List.copyOf(made);
    }

    /** The library's chest menu type for a box of this many rows. */
    public static MenuType<ChestMenu> type(int rows) {
        if (rows < 1 || rows > MOST_ROWS) {
            throw new IllegalArgumentException("a box has between 1 and " + MOST_ROWS + " rows, not " + rows);
        }
        return TYPES.get(rows - 1).get();
    }

    /** The game's chest menu for this container, under the kit's screen, sized to it. */
    public static ChestMenu rowsFor(int id, Inventory inventory, Container container) {
        int rows = Math.max(1, Math.min(MOST_ROWS, container.getContainerSize() / 9));
        return new ChestMenu(type(rows), id, inventory, container, rows);
    }

    /** The game's three-row chest menu, under the kit's screen. */
    public static ChestMenu threeRows(int id, Inventory inventory, Container container) {
        return new ChestMenu(type(3), id, inventory, container, 3);
    }

    /** The game's six-row chest menu, under the kit's screen. */
    public static ChestMenu sixRows(int id, Inventory inventory, Container container) {
        return new ChestMenu(type(6), id, inventory, container, 6);
    }
}
