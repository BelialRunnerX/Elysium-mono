package com.elysium.arcana.transmute;

import com.elysium.arcana.block.MoteTable;
import com.elysium.arcana.network.ArcanaPayloads;
import com.elysium.arcana.registry.ArcanaAttachments;
import com.elysium.arcana.rules.ArcanaRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dissolving and condensing, and the decisions behind both.
 *
 * <h2>Everything is decided here</h2>
 *
 * The client is sent a catalogue with prices so it can draw a list. What comes
 * back is which row and how many, and every single thing that made that row
 * legal is worked out again on this side: that the player knows the item, what
 * it is worth by this server's own table, and whether the balance covers it.
 * A menu is a convenience; it is never an authority.
 */
public final class Transmuting {

    private Transmuting() {
    }

    /** The most a player may condense in one go. A stack, like anything else. */
    public static final int AT_MOST = 64;

    /**
     * Dissolve up to this many out of a stack.
     *
     * @return what to tell the player, always
     */
    public static Component dissolve(Player player, ItemStack held, int count) {
        long worth = MoteTable.worthOf(player.level(), held);
        if (worth <= 0L) {
            return Component.translatable("elysiumarcana.dissolve.worthless",
                    held.getHoverName());
        }
        if (held.isDamaged() || !held.getComponentsPatch().isEmpty()) {
            // A worn pick and a fresh one are not the same object, and neither
            // is an enchanted one. Pricing them the same is how a transmuter
            // becomes a repair bench and an enchantment launderer.
            return Component.translatable("elysiumarcana.dissolve.altered",
                    held.getHoverName());
        }
        int taken = Math.max(1, Math.min(count, held.getCount()));
        long gained = ArcanaRules.dissolve(worth, taken);
        if (gained <= 0L) {
            return Component.translatable("elysiumarcana.dissolve.worthless",
                    held.getHoverName());
        }
        String id = MoteTable.idOf(held);
        held.shrink(taken);
        ArcanaAttachments.gain(player, gained);
        boolean fresh = ArcanaAttachments.learn(player, id);
        return Component.translatable(fresh ? "elysiumarcana.dissolve.learned"
                        : "elysiumarcana.dissolve.done",
                String.valueOf(taken), Component.translatable(itemKey(id)),
                String.valueOf(gained), String.valueOf(ArcanaAttachments.motes(player)));
    }

    /** Build the catalogue and send it. */
    public static void open(ServerPlayer player) {
        Map<String, Long> table = MoteTable.table(player.level());
        List<String> keys = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Integer> prices = new ArrayList<>();
        for (String id : ArcanaAttachments.learned(player)) {
            Long worth = table.get(id);
            if (worth == null || worth <= 0L) {
                // Known, and no longer priceable — a recipe changed under it.
                // Left out rather than shown at nothing.
                continue;
            }
            keys.add(id);
            names.add(itemKey(id));
            prices.add((int) Math.min(Integer.MAX_VALUE, worth));
        }
        PacketDistributor.sendToPlayer(player, new ArcanaPayloads.OpenTransmutations(
                keys, names, prices, ArcanaAttachments.motes(player)));
    }

    /**
     * Condense, or say why not.
     *
     * @return what to tell the player, always
     */
    public static Component condense(ServerPlayer player, String id, int count) {
        if (!ArcanaAttachments.knows(player, id)) {
            // The catalogue could not have offered it, so something else sent
            // this.
            return Component.translatable("elysiumarcana.condense.unknown");
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return Component.translatable("elysiumarcana.condense.gone");
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        long worth = MoteTable.table(player.level()).getOrDefault(id, 0L);
        if (worth <= 0L) {
            return Component.translatable("elysiumarcana.condense.gone");
        }
        int wanted = Math.max(1, Math.min(Math.min(count, AT_MOST),
                item.getDefaultMaxStackSize()));
        int affordable = Math.min(wanted, ArcanaRules.affords(
                ArcanaAttachments.motes(player), worth));
        if (affordable <= 0) {
            return Component.translatable("elysiumarcana.condense.poor",
                    String.valueOf(worth),
                    String.valueOf(ArcanaAttachments.motes(player)));
        }
        long price = ArcanaRules.condensing(worth, affordable);
        if (!ArcanaAttachments.spend(player, price)) {
            return Component.translatable("elysiumarcana.condense.poor",
                    String.valueOf(price),
                    String.valueOf(ArcanaAttachments.motes(player)));
        }
        ItemStack made = new ItemStack(item, affordable);
        if (!player.getInventory().add(made)) {
            player.drop(made, false);
        }
        return Component.translatable("elysiumarcana.condense.done",
                String.valueOf(affordable), Component.translatable(itemKey(id)),
                String.valueOf(price),
                String.valueOf(ArcanaAttachments.motes(player)));
    }

    /** An item's own translation key, so a modded item reads right. */
    public static String itemKey(String id) {
        String[] parts = id.split(":", 2);
        return parts.length == 2 ? "item." + parts[0] + "." + parts[1] : id;
    }
}
