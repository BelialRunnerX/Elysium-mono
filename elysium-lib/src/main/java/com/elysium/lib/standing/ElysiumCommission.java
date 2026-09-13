package com.elysium.lib.standing;

import com.elysium.lib.ElysiumLib;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * A standing contract from the Court.
 *
 * <h2>What the specification asks for</h2>
 *
 * Fourth Edition §14.5, under expanded Imperial infrastructure:
 *
 * <blockquote>Court construction commissions: high-Favor contracts that ask the
 * player to build/restore specific infrastructure for unusual rewards.</blockquote>
 *
 * and §19.2's contract table, which lists a Court commission as
 * "supply/construct an unusual item" and names what it is for in three words:
 * <b>high-standing endgame sink</b>.
 *
 * The production reference's own brief for the system asks for "prestige,
 * secrecy, deadlines, faction sponsors, rewards, and failure consequences".
 * Three of those are here — a deadline, a reward, and a consequence for
 * failing. Prestige, secrecy and sponsors are not, because they need a faction
 * layer this mod does not have, and inventing a shallow version of each would
 * be five half-systems instead of one whole one.
 *
 * <h2>A sink, not a quest log</h2>
 *
 * <b>One commission at a time.</b> A list of outstanding contracts is a quest
 * log, and a quest log turns a decision into a checklist — you take all of them
 * because there is no cost to holding one. One at a time means accepting is a
 * choice about what you are going to spend the next stretch of play doing,
 * which is what "endgame sink" has to mean if it means anything.
 *
 * <h2>Why the library can hold this at all</h2>
 *
 * The library ships no items, and a commission names one. It gets away with it
 * the same way the save format does: by storing the item's <em>registry id</em>
 * as text rather than a reference to something it knows about. Whoever issues a
 * commission chooses the item; this only records the choice, and resolves it
 * back through the registry when somebody asks. An id that no longer resolves —
 * a mod removed since the contract was taken — reports as unresolvable rather
 * than throwing, so an uninstall costs a player a contract rather than a world.
 */
public record ElysiumCommission(String itemId, int required, int tier,
                                long issuedTick, long deadlineTick,
                                String office) {

    /** The empty contract: what a player who has taken none is holding. */
    public static final ElysiumCommission NONE =
            new ElysiumCommission("", 0, 0, 0L, 0L, "");

    public static final Codec<ElysiumCommission> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("item").forGetter(ElysiumCommission::itemId),
                    Codec.INT.fieldOf("required").forGetter(ElysiumCommission::required),
                    Codec.INT.fieldOf("tier").forGetter(ElysiumCommission::tier),
                    Codec.LONG.fieldOf("issued").forGetter(ElysiumCommission::issuedTick),
                    Codec.LONG.fieldOf("deadline").forGetter(ElysiumCommission::deadlineTick),
                    Codec.STRING.fieldOf("office").forGetter(ElysiumCommission::office)
            ).apply(instance, ElysiumCommission::new));

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    /**
     * The player's outstanding contract.
     *
     * <b>Not {@code copyOnDeath}</b>, unlike Favor and Suspicion.
     *
     * That is a deliberate asymmetry and it is the one rule here worth arguing
     * about. Standing survives death because dying to the enforcers the Empire
     * sent would be a strange way to clear your record — the Empire's opinion
     * of you is not carried in your pockets. A contract is different: it has a
     * deadline running against it, and a death that costs you the walk back to
     * your body has already cost you the time. Carrying it through death as
     * well would mean the only real failure state is one the player cannot see
     * coming.
     *
     * So dying releases you from the contract. It is not a punishment and not a
     * reward; it is the Court losing interest in somebody who stopped
     * reporting.
     */
    public static final Supplier<AttachmentType<ElysiumCommission>> ACTIVE =
            ATTACHMENTS.register("commission",
                    () -> AttachmentType.<ElysiumCommission>builder(() -> NONE)
                            .serialize(CODEC)
                            .build());

    public boolean isNone() {
        return itemId.isEmpty() || required <= 0;
    }

    /** The item this asks for, or empty if nothing installed answers to that id. */
    public Optional<Item> resolve() {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return Optional.empty();
        }
        return Optional.of(BuiltInRegistries.ITEM.get(id));
    }

    public boolean isExpired(long now) {
        return !isNone() && now >= deadlineTick;
    }

    /** Ticks left, floored at zero. */
    public long remaining(long now) {
        return Math.max(0L, deadlineTick - now);
    }

    // ------------------------------------------------------------------
    // The player's side
    // ------------------------------------------------------------------

    public static ElysiumCommission get(Player player) {
        return player.getData(ACTIVE.get());
    }

    public static void set(Player player, ElysiumCommission commission) {
        player.setData(ACTIVE.get(), commission);
    }

    public static void clear(Player player) {
        player.setData(ACTIVE.get(), NONE);
    }

    /**
     * How many of the required item the player is carrying.
     *
     * Counted rather than consumed, so a caller can report progress without
     * taking anything — the difference between "you need twelve more" and a
     * contract that silently eats what you walked in with.
     */
    public int carried(Player player) {
        Optional<Item> want = resolve();
        if (want.isEmpty()) {
            return 0;
        }
        Item item = want.get();
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /**
     * Take the goods, all or nothing.
     *
     * Returns false and takes nothing when the player is short. A partial take
     * would be the worst possible reading of a contract: the player loses the
     * materials and still owes the contract.
     */
    public boolean collect(Player player) {
        if (isNone() || carried(player) < required) {
            return false;
        }
        Optional<Item> want = resolve();
        if (want.isEmpty()) {
            return false;
        }
        Item item = want.get();
        int owed = required;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && owed > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int take = Math.min(owed, stack.getCount());
            stack.shrink(take);
            owed -= take;
        }
        return owed == 0;
    }
}
