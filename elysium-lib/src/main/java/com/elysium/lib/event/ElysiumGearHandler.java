package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.item.ElysiumGear;
import com.elysium.lib.item.ElysiumSocketable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Makes foreign gear look like what it now is.
 *
 * <h2>The tooltip</h2>
 *
 * {@link ElysiumGear} admits any weapon, tool or piece of armour into the gear
 * systems, so a vanilla iron sword is Kinetic gear that takes runes, reforges
 * and ascends. None of that is visible on it. Elysium's own items print their
 * element, tier, stats and sockets from {@code appendHoverText}, which is a
 * method on their classes — and a vanilla item has no such override and never
 * will.
 *
 * <p>That gap is worse than it sounds. A player can socket a Kineticsurge rune
 * into an iron sword, get the full aligned bonus, and have the item report
 * nothing about it: no element, no socket count, no stats. The feature would
 * exist and be undiscoverable, which for a system built on choosing matchups is
 * the same as not existing.
 *
 * <p>So the lines are added from an event instead, and only for gear that
 * cannot add them itself. An item implementing {@link ElysiumSocketable}
 * already printed them from its own tooltip, and printing them again here would
 * show every Elysium item's stats twice.
 *
 * <h2>The cache</h2>
 *
 * A material's ingredient is a tag, so what an item is made of is a datapack
 * question and the answer changes on reload. {@link ElysiumGear} caches
 * resolutions per item, which makes this the one event that has to clear it.
 * Tags reload on world load, on {@code /reload}, and on joining a server, so
 * this fires in every case where the answer could have moved.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumGearHandler {

    private ElysiumGearHandler() {
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        int forgotten = ElysiumGear.resolvedCount();
        ElysiumGear.invalidate();
        // The finer question caches per item too, and a tag reload can change
        // its answer for exactly the same reason.
        com.elysium.lib.item.ElysiumLootCategory.invalidate();
        if (forgotten > 0) {
            ElysiumLib.LOGGER.debug(
                    "tags reloaded: forgetting {} resolved gear item(s)", forgotten);
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        // Elysium's own items write these lines from their own appendHoverText.
        // This is only for the gear that cannot.
        if (ElysiumGear.isNative(stack)) {
            return;
        }
        ElysiumSocketable gear = ElysiumGear.of(stack);
        if (gear == null) {
            return;
        }
        gear.appendSocketTooltip(stack, event.getToolTip());
    }
}
