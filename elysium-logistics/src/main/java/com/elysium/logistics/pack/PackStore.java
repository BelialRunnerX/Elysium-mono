package com.elysium.logistics.pack;

import com.elysium.logistics.ElysiumLogistics;
import com.elysium.logistics.registry.LogisticComponents;
import com.elysium.logistics.rules.CompressRules;
import com.elysium.logistics.rules.PackRules;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What is inside one pack, and every way it may change.
 *
 * <h2>One class for a bag and a crate</h2>
 *
 * A carried pack keeps its contents on the item and a placed one keeps them
 * in its block entity, and that is the only difference. Both hand this class
 * a list and a way to write it back, and every change — a pile put in, a part
 * fitted, a part taken out, a stack cooked — goes through here, where it is
 * decided by {@link PackRules} and nowhere else. A bag and a crate that each
 * decided for themselves would be two capacity bugs.
 *
 * <h2>The list is the capacity</h2>
 *
 * The list is always exactly {@link PackRules#capacity} long. Fitting a pocket
 * lengthens it; taking one out shortens it, and only after the rules have
 * agreed that what is inside fits in what is left, and only after it has been
 * packed down into the slots that will still be there.
 */
public final class PackStore {

    private final PackRules.Body body;
    private final List<String> parts;
    private NonNullList<ItemStack> items;
    private final Runnable saved;

    public PackStore(PackRules.Body body, List<String> parts, NonNullList<ItemStack> items,
                     Runnable saved) {
        this.body = body;
        this.parts = new ArrayList<>(parts);
        this.saved = saved;
        int room = capacity();
        if (items.size() != room) {
            NonNullList<ItemStack> sized = NonNullList.withSize(room, ItemStack.EMPTY);
            int at = 0;
            for (ItemStack one : items) {
                if (!one.isEmpty() && at < room) {
                    sized.set(at++, one);
                }
            }
            items = sized;
        }
        this.items = items;
    }

    public PackRules.Body body() {
        return body;
    }

    /** The parts, as the rules know them. */
    public List<PackRules.Part> fitted() {
        return PackRules.partsNamed(parts);
    }

    /** The parts, by name, for writing back. */
    public List<String> partNames() {
        return List.copyOf(parts);
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    /** How much this pack holds with what is fitted to it: the sum, and only the sum. */
    public int capacity() {
        return PackRules.capacity(body, fitted());
    }

    /** How many slots have something in them. */
    public int occupied() {
        int used = 0;
        for (ItemStack one : items) {
            if (!one.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /** Whether some of this already sits in the pack. */
    public boolean holds(ItemStack stack) {
        for (ItemStack one : items) {
            if (!one.isEmpty() && ItemStack.isSameItemSameComponents(one, stack)) {
                return true;
            }
        }
        return false;
    }

    public void changed() {
        saved.run();
    }

    // ------------------------------------------------------------------
    // What may go in

    /**
     * Whether this may be put inside a pack at all.
     *
     * <p>No body of ours, carried or placed, and nothing carrying a pack's
     * contents — which is the same rule said twice, once by name for the
     * roster and once by what the stack actually is, so a pack renamed in an
     * anvil is still a pack.
     */
    public static boolean mayHold(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        boolean ours = ElysiumLogistics.MODID.equals(id.getNamespace());
        return !(ours && !PackRules.mayHold(id.getPath()))
                && !stack.has(LogisticComponents.CONTENTS.get());
    }

    /**
     * Put a pile in, and say what did not go.
     *
     * @param chosen whether a player put it in by hand. A sieve only sieves
     *               what nobody chose; everything else is ruled the same.
     */
    public ItemStack insert(ItemStack stack, boolean simulate, boolean chosen,
                            Level level) {
        if (stack.isEmpty() || !mayHold(stack)) {
            return stack;
        }
        List<PackRules.Part> fitted = fitted();
        boolean held = holds(stack);
        if (!chosen && !PackRules.admits(fitted, held)) {
            return stack;
        }
        ItemStack left = stack.copy();
        // More of what is there first, so a pack fills its piles before it
        // starts new ones.
        for (int slot = 0; slot < items.size() && !left.isEmpty(); slot++) {
            ItemStack here = items.get(slot);
            if (here.isEmpty() || !ItemStack.isSameItemSameComponents(here, left)) {
                continue;
            }
            int moved = Math.min(left.getCount(), here.getMaxStackSize() - here.getCount());
            if (moved <= 0) {
                continue;
            }
            if (!simulate) {
                here.grow(moved);
            }
            left.shrink(moved);
        }
        // Then new piles, each one a slot the rules have agreed there is room for.
        int stored = occupied();
        for (int slot = 0; slot < items.size() && !left.isEmpty(); slot++) {
            if (!items.get(slot).isEmpty()) {
                continue;
            }
            if (PackRules.accepted(body, fitted, stored, 1) != 1) {
                break;
            }
            int moved = Math.min(left.getCount(), left.getMaxStackSize());
            if (!simulate) {
                items.set(slot, left.copyWithCount(moved));
            }
            left.shrink(moved);
            stored++;
        }
        if (!left.isEmpty() && PackRules.voidsOverflow(fitted, held)) {
            // The one place a pack destroys anything: a seal fitted on purpose,
            // and only what the pack already holds and has no room for.
            left = ItemStack.EMPTY;
        }
        if (!simulate && left.getCount() != stack.getCount()) {
            settle(level);
            changed();
        }
        return left;
    }

    /** Take some out of one slot. */
    public ItemStack extract(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= items.size() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack here = items.get(slot);
        if (here.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(amount, here.getCount());
        ItemStack out = here.copyWithCount(taken);
        if (!simulate) {
            here.shrink(taken);
            if (here.isEmpty()) {
                items.set(slot, ItemStack.EMPTY);
            }
            changed();
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Fitting and unfitting

    /** Why a part was refused, or null if it went in. */
    public String fit(PackRules.Part part) {
        List<PackRules.Part> after = new ArrayList<>(fitted());
        after.add(part);
        if (!PackRules.fits(body, after)) {
            return fitted().contains(part) ? "elysiumlogistics.pack.twice"
                    : "elysiumlogistics.pack.no_room_for_parts";
        }
        parts.add(part.name());
        resize();
        changed();
        return null;
    }

    /**
     * Take the last part out, or null if the rules refuse.
     *
     * <p>Refused while what is inside would not fit in what is left, and on
     * the way out the contents are packed down into the slots that remain,
     * so the removal the harness proved safe is the removal that happens.
     */
    public PackRules.Part unfit() {
        List<PackRules.Part> fitted = fitted();
        if (fitted.isEmpty()) {
            return null;
        }
        PackRules.Part going = fitted.get(fitted.size() - 1);
        if (!PackRules.mayRemove(body, fitted, going, occupied())) {
            return null;
        }
        parts.remove(parts.size() - 1);
        resize();
        changed();
        return going;
    }

    /** Make the list exactly the capacity long, packing what is inside to the front. */
    private void resize() {
        int room = capacity();
        NonNullList<ItemStack> sized = NonNullList.withSize(room, ItemStack.EMPTY);
        int at = 0;
        for (ItemStack one : items) {
            if (one.isEmpty()) {
                continue;
            }
            if (at >= room) {
                throw new IllegalStateException("a pack was shrunk below what it holds; "
                        + "PackRules.mayRemove should have refused");
            }
            sized.set(at++, one);
        }
        items = sized;
    }

    // ------------------------------------------------------------------
    // What the parts do on the way in

    /**
     * Cook, then press, whatever is inside, if the parts say so.
     *
     * <p>Run after every change rather than on one pile, because a press
     * works on the pack's total of a thing: eight cobblestone in one slot and
     * one in another is nine.
     */
    public void settle(Level level) {
        if (level == null || level.isClientSide) {
            return;
        }
        List<PackRules.Part> fitted = fitted();
        if (PackRules.has(fitted, PackRules.COOK)) {
            cook(level);
        }
        if (PackRules.has(fitted, PackRules.PRESS)) {
            press(level);
        }
    }

    private void cook(Level level) {
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack raw = items.get(slot);
            if (raw.isEmpty()) {
                continue;
            }
            Optional<RecipeHolder<SmeltingRecipe>> recipe = level.getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(raw), level);
            if (recipe.isEmpty()) {
                continue;
            }
            ItemStack result = recipe.get().value().getResultItem(level.registryAccess());
            if (result.isEmpty()) {
                continue;
            }
            int made = PackRules.cooked(raw.getCount(), result.getCount(),
                    result.getMaxStackSize());
            if (made > 0) {
                items.set(slot, result.copyWithCount(made));
            }
        }
    }

    /** What each thing a press takes is pressed into, from the compression roster. */
    private static Map<Item, Item> pressings;

    private static Map<Item, Item> pressings() {
        if (pressings == null) {
            Map<Item, Item> made = new HashMap<>();
            for (String material : CompressRules.MATERIALS) {
                for (int tier = 1; tier <= CompressRules.TIERS; tier++) {
                    Item from = BuiltInRegistries.ITEM.get(
                            ResourceLocation.parse(CompressRules.madeOf(material, tier)));
                    Item into = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                            ElysiumLogistics.MODID, CompressRules.idFor(material, tier)));
                    made.put(from, into);
                }
            }
            pressings = made;
        }
        return pressings;
    }

    /**
     * Every pile of nine pressed into one of the tier above, until none is.
     *
     * <p>Tried on a copy and kept only if everything made found a place, so a
     * full pack is never pressed into losing anything.
     */
    private void press(Level level) {
        for (int round = 0; round <= CompressRules.TIERS; round++) {
            boolean any = false;
            for (Map.Entry<Item, Item> step : pressings().entrySet()) {
                int count = 0;
                for (ItemStack one : items) {
                    if (one.is(step.getKey()) && one.getComponentsPatch().isEmpty()) {
                        count += one.getCount();
                    }
                }
                int[] made = PackRules.pressed(count);
                if (made[0] <= 0) {
                    continue;
                }
                NonNullList<ItemStack> before = NonNullList.withSize(items.size(),
                        ItemStack.EMPTY);
                for (int at = 0; at < items.size(); at++) {
                    before.set(at, items.get(at).copy());
                }
                int taking = made[0] * CompressRules.PER_STEP;
                for (int at = 0; at < items.size() && taking > 0; at++) {
                    ItemStack one = items.get(at);
                    if (one.is(step.getKey()) && one.getComponentsPatch().isEmpty()) {
                        int off = Math.min(taking, one.getCount());
                        one.shrink(off);
                        taking -= off;
                        if (one.isEmpty()) {
                            items.set(at, ItemStack.EMPTY);
                        }
                    }
                }
                ItemStack left = new ItemStack(step.getValue(), made[0]);
                for (int at = 0; at < items.size() && !left.isEmpty(); at++) {
                    ItemStack here = items.get(at);
                    if (here.isEmpty()) {
                        items.set(at, left.split(left.getMaxStackSize()));
                    } else if (ItemStack.isSameItemSameComponents(here, left)) {
                        int moved = Math.min(left.getCount(),
                                here.getMaxStackSize() - here.getCount());
                        here.grow(moved);
                        left.shrink(moved);
                    }
                }
                if (!left.isEmpty()) {
                    items = before;
                    continue;
                }
                any = true;
            }
            if (!any) {
                return;
            }
        }
    }
}
