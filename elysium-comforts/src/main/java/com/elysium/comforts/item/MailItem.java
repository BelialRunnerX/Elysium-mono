package com.elysium.comforts.item;

import com.elysium.comforts.registry.ComfortComponents;
import com.elysium.comforts.rules.PostRules;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * One class for every kind of mail, driven by its row.
 *
 * <h2>Why one class</h2>
 *
 * Eight kinds of mail differ in four numbers and three flags, and eight classes
 * would be eight places to forget that a sealed parcel must not open in the
 * wrong hands. Which kind a stack is comes from the name it was registered
 * under, looked up in {@link PostRules#MAIL}, so there is nothing stored on the
 * item that could disagree with the rules.
 *
 * <h2>Opening something with things in it</h2>
 *
 * {@link #unpack} is the only path that takes contents out, and it empties the
 * component before it hands a single one of them over. Every duplication bug
 * ever shipped wrote the copy before deleting the original, so this does it the
 * other way round.
 */
public class MailItem extends Item {

    private final String kind;

    public MailItem(Properties properties, String kind) {
        super(properties.stacksTo(kindOf(kind).slots() > 0 ? 1 : 16));
        this.kind = kind;
    }

    private static PostRules.Mail kindOf(String kind) {
        PostRules.Mail found = PostRules.mail(kind);
        if (found == null) {
            throw new IllegalStateException(kind + " is not a kind of mail the "
                    + "rules know, so it is an item with no post behind it");
        }
        return found;
    }

    /** The row this item is. */
    public PostRules.Mail mail() {
        return kindOf(kind);
    }

    /** Whether only the addressee may open it. */
    public boolean sealed() {
        return mail().sealed();
    }

    /** What is inside one, or an empty list. */
    public static NonNullList<ItemStack> inside(ItemStack stack,
                                                HolderLookup.Provider lookup) {
        NonNullList<ItemStack> held = NonNullList.create();
        CompoundTag written = stack.get(ComfortComponents.CONTENTS.get());
        if (written == null || written.isEmpty()) {
            return held;
        }
        ListTag list = written.getList("Items", Tag.TAG_COMPOUND);
        for (int at = 0; at < list.size(); at++) {
            ItemStack one = ItemStack.parseOptional(lookup, list.getCompound(at));
            if (!one.isEmpty()) {
                held.add(one);
            }
        }
        return held;
    }

    /** Write these into one. */
    public static void fill(ItemStack stack, List<ItemStack> with,
                            HolderLookup.Provider lookup) {
        CompoundTag written = new CompoundTag();
        ListTag list = new ListTag();
        for (ItemStack one : with) {
            if (!one.isEmpty()) {
                list.add(one.save(lookup, new CompoundTag()));
            }
        }
        written.put("Items", list);
        stack.set(ComfortComponents.CONTENTS.get(), written);
    }

    /**
     * Open it.
     *
     * <p>Refused on a sealed parcel unless the player's name is the one on it,
     * which is the whole service a seal buys. What comes out goes into the
     * player's inventory or onto the floor beside them — never nowhere.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        if (mail().slots() <= 0) {
            return InteractionResultHolder.pass(stack);
        }
        if (sealed() && !addressedTo(stack, player)) {
            player.displayClientMessage(Component.translatable(
                    "item.elysiumcomforts.sealed"), true);
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.consume(unpack(stack, player, level));
    }

    /** Whether this parcel has this player's name on it. */
    public static boolean addressedTo(ItemStack stack, Player player) {
        String written = stack.get(ComfortComponents.ADDRESS.get());
        if (written == null || written.isEmpty()) {
            return true;
        }
        return written.equalsIgnoreCase(player.getGameProfile().getName());
    }

    /**
     * Take everything out of it.
     *
     * <p>The contents are cleared from the stack before a single one of them is
     * handed over, so that a crash in the middle leaves a player with an empty
     * parcel rather than with two copies of what was in it.
     */
    public ItemStack unpack(ItemStack stack, Player player, Level level) {
        NonNullList<ItemStack> held = inside(stack, player.registryAccess());
        stack.remove(ComfortComponents.CONTENTS.get());
        for (ItemStack one : held) {
            if (!player.getInventory().add(one)) {
                player.drop(one, false);
            }
        }
        stack.shrink(1);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        String written = stack.get(ComfortComponents.ADDRESS.get());
        if (written != null && !written.isEmpty()) {
            lines.add(Component.translatable("item.elysiumcomforts.to", written));
        }
        if (mail().slots() > 0 && sealed()) {
            lines.add(Component.translatable("item.elysiumcomforts.unknown"));
        }
    }
}
