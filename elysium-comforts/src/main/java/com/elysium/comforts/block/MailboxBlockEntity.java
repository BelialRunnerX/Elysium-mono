package com.elysium.comforts.block;

import com.elysium.comforts.registry.ComfortBlockEntities;
import com.elysium.comforts.rules.PostRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An address, and whatever has turned up at it.
 *
 * <h2>Nine slots, and why the address is not one of them</h2>
 *
 * A mailbox is two things at once: somewhere mail lands, and the name that
 * makes landing there possible. The first is a container and behaves like every
 * other container in this module — what is in it comes out when it is broken,
 * and {@link #drops()} is the one place that says so. The second is a string
 * and belongs nowhere near the item list, because an address stored in a slot
 * is an address a hopper can steal.
 *
 * <h2>The waiting parcel</h2>
 *
 * A parcel posted here is not here yet. It is written down with the tick it
 * will arrive on — {@link #ARRIVES} — and nothing hands it over before then.
 * That is the whole of what makes this a post office rather than a chest, so it
 * is stored rather than computed: a journey recomputed on load is a journey
 * that restarts every time the chunk does, and a parcel that restarts its
 * journey on every load is a parcel that never comes.
 */
public class MailboxBlockEntity extends BlockEntity {

    /** How many pieces of mail it will hold. */
    public static final int SLOTS = 9;

    /** Where the address lives in this block's own data. */
    public static final String ADDRESS = "ElysiumAddress";

    /** And the tick the parcel in flight lands on. */
    public static final String ARRIVES = "ElysiumArrives";

    /** And what is in flight. */
    public static final String FLYING = "ElysiumFlying";

    private final NonNullList<ItemStack> held =
            NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private ItemStack flying = ItemStack.EMPTY;
    private long arrives;
    private String address = "";

    public MailboxBlockEntity(BlockPos pos, BlockState state) {
        super(ComfortBlockEntities.MAILBOX.get(), pos, state);
    }

    /** What it is called, or the empty string. */
    public String address() {
        return address;
    }

    /**
     * Claim this address.
     *
     * @return whether the name was one the post will carry
     */
    public boolean nameIt(String written) {
        if (!PostRules.addressable(written)) {
            return false;
        }
        address = written;
        setChanged();
        return true;
    }

    /** Whether something is on its way here. */
    public boolean expecting() {
        return !flying.isEmpty();
    }

    /** The tick the thing on its way lands on. */
    public long arrives() {
        return arrives;
    }

    /**
     * Put a parcel in flight towards this box.
     *
     * <p>Refused while something else is already on its way, because a box that
     * accepts a second parcel has to decide what to do with the first, and
     * every answer to that is a way to lose one.
     *
     * @return whether the post took it
     */
    public boolean send(ItemStack parcel, long now, int blocks) {
        if (parcel.isEmpty() || expecting()) {
            return false;
        }
        flying = parcel.copy();
        arrives = now + PostRules.travelTicks(blocks);
        setChanged();
        return true;
    }

    /**
     * Land whatever is due.
     *
     * <p>Into the box if there is room, and back into flight if there is not —
     * never onto the floor and never nowhere. A full mailbox is a reason for a
     * parcel to wait, not a reason for it to stop existing.
     *
     * @return whether anything landed
     */
    public boolean landIfDue(long now) {
        if (!expecting() || now < arrives) {
            return false;
        }
        for (int slot = 0; slot < held.size(); slot++) {
            if (held.get(slot).isEmpty()) {
                // The loss the rules price, rolled once, where the parcel lands.
                ItemStack landing = flying;
                if (level != null && PostRules.lostOn(level.random.nextInt(1000))) {
                    landing = lostFrom(flying, level.registryAccess());
                }
                held.set(slot, landing);
                flying = ItemStack.EMPTY;
                arrives = 0L;
                setChanged();
                return true;
            }
        }
        arrives = now + PostRules.SOONEST;
        setChanged();
        return false;
    }

    /**
     * What arrives instead of a lost parcel: lost mail holding what the rules
     * say survives of each stack inside, as many stacks as lost mail holds.
     * A letter, with nothing inside, arrives as empty lost mail.
     */
    private static ItemStack lostFrom(ItemStack parcel, HolderLookup.Provider lookup) {
        ItemStack lost = new ItemStack(
                com.elysium.comforts.registry.ComfortItems.MAIL.get(PostRules.LOST_MAIL).get());
        int room = PostRules.mail(PostRules.LOST_MAIL).slots();
        java.util.List<ItemStack> kept = new java.util.ArrayList<>();
        for (ItemStack one : com.elysium.comforts.item.MailItem.inside(parcel, lookup)) {
            int left = PostRules.salvaged(one.getCount());
            if (left > 0 && kept.size() < room) {
                kept.add(one.copyWithCount(left));
            }
        }
        com.elysium.comforts.item.MailItem.fill(lost, kept, lookup);
        return lost;
    }

    /**
     * Everything this box would drop if it were broken now.
     *
     * <p>Including whatever is in flight towards it, which is the whole point
     * of this method existing: a parcel in the air is a parcel somebody has
     * already paid for, and a box broken while one is coming must not be a way
     * to make it vanish.
     */
    public NonNullList<ItemStack> drops() {
        NonNullList<ItemStack> all = NonNullList.create();
        for (ItemStack one : held) {
            if (!one.isEmpty()) {
                all.add(one.copy());
            }
        }
        if (!flying.isEmpty()) {
            all.add(flying.copy());
        }
        return all;
    }

    /** What is in it, for the block to hand out. */
    public NonNullList<ItemStack> contents() {
        return held;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        held.clear();
        ContainerHelper.loadAllItems(tag, held, lookup);
        address = tag.getString(ADDRESS);
        arrives = tag.getLong(ARRIVES);
        flying = tag.contains(FLYING)
                ? ItemStack.parseOptional(lookup, tag.getCompound(FLYING))
                : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        ContainerHelper.saveAllItems(tag, held, lookup);
        tag.putString(ADDRESS, address);
        tag.putLong(ARRIVES, arrives);
        if (!flying.isEmpty()) {
            tag.put(FLYING, flying.save(lookup, new CompoundTag()));
        }
    }
}
