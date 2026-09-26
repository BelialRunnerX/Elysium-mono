package com.elysium.logistics.pack;

import com.elysium.logistics.ElysiumLogistics;
import com.elysium.logistics.rules.PackRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * What the parts of a worn pack do while it is worn.
 *
 * <p>Three hooks, one per moment a part can act: something picked up (a
 * gathering lens, and the seal behind it), and the wearer's own tick (a ration
 * tin and a restocker). Cooking, pressing and sieving act on the way in, in
 * {@link PackStore}, so they happen however a thing arrives.
 *
 * <p>A pack counts as worn anywhere in its wearer's inventory, as it does in
 * the jars this replaces: a bag in a chest does nothing, a bag in a pocket
 * works.
 */
@EventBusSubscriber(modid = ElysiumLogistics.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class PackEvents {

    private PackEvents() {
    }

    /** Every worn pack with a part of this kind fitted, as a store over the stack. */
    static List<PackStore> worn(Player player, String kind, ItemStack skip) {
        List<PackStore> found = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == skip || !(stack.getItem() instanceof PackItem)) {
                continue;
            }
            PackStore store = PackItem.storeOf(stack);
            if (PackRules.has(store.fitted(), kind)) {
                found.add(store);
            }
        }
        return found;
    }

    /**
     * A gathering lens: what is picked up goes into the bag before the pockets.
     *
     * <p>Shrinks the very stack the game is about to hand the inventory, so
     * whatever the bags did not take goes on to the inventory exactly as it
     * would have, in the same tick.
     */
    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        ItemEntity entity = event.getItemEntity();
        if (player.level().isClientSide || entity.hasPickUpDelay()
                || (entity.getTarget() != null && !entity.getTarget().equals(player.getUUID()))) {
            return;
        }
        ItemStack coming = entity.getItem();
        if (coming.isEmpty()) {
            return;
        }
        int before = coming.getCount();
        for (PackStore bag : worn(player, PackRules.GATHER, null)) {
            ItemStack left = bag.insert(coming, false, false, player.level());
            coming.setCount(left.getCount());
            if (coming.isEmpty()) {
                break;
            }
        }
        int taken = before - coming.getCount();
        if (taken > 0) {
            player.take(entity, taken);
            entity.setItem(coming);
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide || player.isSpectator()) {
            return;
        }
        if (player.tickCount % PackRules.FEED_EVERY == 0) {
            feed(player, level);
        }
        if (player.tickCount % PackRules.RESTOCK_EVERY == 0) {
            restock(player);
        }
    }

    /** A ration tin: the first food whose whole worth is wanted, eaten out of the bag. */
    private static void feed(Player player, Level level) {
        int missing = 20 - player.getFoodData().getFoodLevel();
        if (missing <= 0) {
            return;
        }
        for (PackStore bag : worn(player, PackRules.FEED, null)) {
            for (int slot = 0; slot < bag.items().size(); slot++) {
                ItemStack food = bag.items().get(slot);
                FoodProperties worth = food.get(DataComponents.FOOD);
                if (worth == null || !PackRules.worthEating(missing, worth.nutrition())) {
                    continue;
                }
                ItemStack one = bag.extract(slot, 1, false);
                ItemStack after = one.finishUsingItem(level, player);
                if (!after.isEmpty()) {
                    // A bowl, a bottle: back into the bag if it fits, else the pockets.
                    ItemStack left = bag.insert(after, false, true, level);
                    if (!left.isEmpty()) {
                        player.getInventory().placeItemBackInInventory(left);
                    }
                }
                return;
            }
        }
    }

    /** A restocker: the stack in hand topped back up from the bag. */
    private static void restock(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || held.getItem() instanceof PackItem) {
            return;
        }
        int wanted = PackRules.restockWanted(held.getCount(), held.getMaxStackSize());
        if (wanted <= 0) {
            return;
        }
        for (PackStore bag : worn(player, PackRules.RESTOCK, held)) {
            for (int slot = 0; slot < bag.items().size() && wanted > 0; slot++) {
                ItemStack here = bag.items().get(slot);
                if (here.isEmpty() || !ItemStack.isSameItemSameComponents(here, held)) {
                    continue;
                }
                ItemStack out = bag.extract(slot, wanted, false);
                held.grow(out.getCount());
                wanted -= out.getCount();
            }
            if (wanted <= 0) {
                return;
            }
        }
    }
}
