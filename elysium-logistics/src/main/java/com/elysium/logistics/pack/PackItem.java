package com.elysium.logistics.pack;

import com.elysium.logistics.ElysiumLogistics;
import com.elysium.logistics.registry.LogisticComponents;
import com.elysium.logistics.rules.PackRules;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A bag: a body from {@link PackRules#BODIES} worn rather than placed.
 *
 * <h2>Three gestures</h2>
 *
 * <ul>
 *   <li>Use it to open it, in the game's own chest screen. A bag bigger than
 *       one screen opens on its next page each time, and the title says which.
 *   <li>Use it crouching with a part in the other hand to fit the part. The
 *       rules refuse a part fitted twice and a part more than the bag takes.
 *   <li>Use it crouching with the other hand empty to take the last part back
 *       out into it — refused while what is inside would not fit without it.
 * </ul>
 */
public class PackItem extends Item {

    private final PackRules.Body body;

    public PackItem(Properties properties, PackRules.Body body) {
        super(properties);
        this.body = body;
    }

    public PackRules.Body body() {
        return body;
    }

    /** What is inside this bag, written back to it on every change. */
    public static PackStore storeOf(ItemStack stack) {
        PackRules.Body body = stack.getItem() instanceof PackItem pack ? pack.body()
                : PackRules.BODIES.get(0);
        List<String> parts = stack.getOrDefault(LogisticComponents.FITTED.get(), List.of());
        ItemContainerContents contents = stack.getOrDefault(LogisticComponents.CONTENTS.get(),
                ItemContainerContents.EMPTY);
        NonNullList<ItemStack> items = NonNullList.withSize(
                PackRules.capacity(body, PackRules.partsNamed(parts)), ItemStack.EMPTY);
        if (contents.getSlots() <= items.size()) {
            contents.copyInto(items);
        } else {
            NonNullList<ItemStack> all = NonNullList.withSize(contents.getSlots(),
                    ItemStack.EMPTY);
            contents.copyInto(all);
            items = all;
        }
        PackStore[] made = new PackStore[1];
        made[0] = new PackStore(body, parts, items, () -> {
            stack.set(LogisticComponents.CONTENTS.get(),
                    ItemContainerContents.fromItems(made[0].items()));
            stack.set(LogisticComponents.FITTED.get(), made[0].partNames());
        });
        return made[0];
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack bag = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(bag);
        }
        PackStore store = storeOf(bag);
        if (player.isShiftKeyDown()) {
            InteractionHand other = hand == InteractionHand.MAIN_HAND
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            player.displayClientMessage(refit(store, player, other), true);
            return InteractionResultHolder.consume(bag);
        }
        int page = Math.max(0, bag.getOrDefault(LogisticComponents.PAGE.get(), 0));
        int room = store.capacity();
        if (page >= PackRules.pages(room)) {
            page = 0;
        }
        bag.set(LogisticComponents.PAGE.get(), PackRules.nextPage(room, page));
        // The slot the bag is in, so neither a click nor a number key nor the
        // swap-hands key can move it out from under its own screen.
        int locked = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected
                : Inventory.SLOT_OFFHAND;
        int shown = page;
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, who) -> new PackMenu(id, inventory, store, shown, level,
                        () -> who.getItemInHand(hand) == bag && !bag.isEmpty(), locked),
                titleOf(bag, room, page)));
        return InteractionResultHolder.consume(bag);
    }

    /** The name, and the page when there is more than one. */
    static Component titleOf(ItemStack stack, int room, int page) {
        int pages = PackRules.pages(room);
        return pages <= 1 ? stack.getHoverName()
                : Component.translatable("elysiumlogistics.pack.page", stack.getHoverName(),
                        page + 1, pages);
    }

    /** A part in the other hand goes in; an empty other hand takes the last one out. */
    static Component refit(PackStore store, Player player, InteractionHand other) {
        ItemStack offered = player.getItemInHand(other);
        if (offered.isEmpty()) {
            PackRules.Part out = store.unfit();
            if (out == null) {
                return store.fitted().isEmpty()
                        ? Component.translatable("elysiumlogistics.pack.nothing_fitted")
                        : Component.translatable("elysiumlogistics.pack.too_full",
                                store.occupied());
            }
            ItemStack part = new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath(ElysiumLogistics.MODID, out.name())));
            player.setItemInHand(other, part);
            return Component.translatable("elysiumlogistics.pack.unfitted",
                    part.getHoverName(), store.capacity());
        }
        PackRules.Part part = partOf(offered);
        if (part == null) {
            return Component.translatable("elysiumlogistics.pack.not_a_part",
                    offered.getHoverName());
        }
        String refused = store.fit(part);
        if (refused != null) {
            return Component.translatable(refused, offered.getHoverName());
        }
        Component name = offered.getHoverName();
        offered.shrink(1);
        return Component.translatable("elysiumlogistics.pack.fitted", name, store.capacity());
    }

    /** The part this stack is, or null. */
    static PackRules.Part partOf(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ElysiumLogistics.MODID.equals(id.getNamespace()) ? PackRules.part(id.getPath())
                : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        PackStore store = storeOf(stack);
        tooltip.add(Component.translatable("elysiumlogistics.pack.holding",
                store.occupied(), store.capacity()));
        for (String part : store.partNames()) {
            tooltip.add(Component.translatable("item." + ElysiumLogistics.MODID + "." + part));
        }
    }
}
