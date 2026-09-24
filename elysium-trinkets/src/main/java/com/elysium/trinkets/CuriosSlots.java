package com.elysium.trinkets;

import com.elysium.trinkets.item.ElysiumTrinketItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The entire dependency on Curios, in one class.
 *
 * <h2>What crosses this line</h2>
 *
 * One question: <em>what is this player wearing?</em> The library asks it
 * through {@code ElysiumTrinkets.Provider} and knows nothing else about
 * accessories — not the slot names, not the inventory, not that Curios exists.
 * Everything a trinket does is an {@code ElysiumPassive}, so Curios answers
 * where a thing is and never what it does.
 *
 * The practical consequence is the one the library's javadoc promised: moving
 * to another accessory API is this file and no trinket.
 *
 * <h2>Why there is a cache, and why it is a tick and not an event</h2>
 *
 * {@code ElysiumCharacter.passives} runs on every hit taken, every hit dealt
 * and every server tick. Walking a Curios inventory inside all three would put
 * an inventory scan several times into a single swing, and the library states
 * caching as a requirement rather than enforcing it because it cannot see the
 * events that would let it do the caching itself.
 *
 * The obvious cache is keyed on Curios' own equip and unequip events. This one
 * is keyed on the player's tick count instead, which is worth explaining
 * because it looks lazier and is deliberately not:
 *
 * <ul>
 *   <li>It is <b>correct by construction</b>. An event-driven cache is stale
 *       exactly when an event is missed, and a missed unequip means a removed
 *       trinket keeps working — a bug that survives until someone notices a
 *       number is wrong. This one cannot be more than a tick behind anything,
 *       whatever happens.</li>
 *   <li>It costs <b>one scan per player per tick at most</b>, which is the same
 *       order as the event-driven version and vastly better than the uncached
 *       one. The expensive case was never "once a tick", it was "five times a
 *       swing".</li>
 *   <li>It depends on <b>no Curios event class</b>, which keeps the surface
 *       this mod has to be right about down to three symbols.</li>
 * </ul>
 *
 * Entries are keyed by UUID and dropped when a player logs out, so the map does
 * not grow without bound on a long-running server.
 */
public final class CuriosSlots {

    private CuriosSlots() {
    }

    private record Snapshot(int tick, List<com.elysium.lib.trinket.ElysiumTrinkets.Equipped> worn) {
    }

    private static final Map<UUID, Snapshot> CACHE = new ConcurrentHashMap<>();

    /**
     * Everything worn in a charm slot that is a potion charm.
     *
     * Uncached, unlike {@link #install}'s answer: this is asked once a second
     * by one handler rather than several times a swing by the damage path, and
     * a second cache keyed on the same tick would cost more to keep honest
     * than the scan it saves.
     */
    public static List<ItemStack> wornCharms(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(inventory -> inventory.findCurios(
                        stack -> stack.getItem()
                                instanceof com.elysium.trinkets.item.PotionCharmItem))
                .orElse(List.of())
                .stream()
                .map(SlotResult::stack)
                .toList();
    }

    /**
     * Tells Curios the potion charm is wearable.
     *
     * Run at common setup rather than from the mod constructor, because it
     * needs the item to exist — and {@code DeferredHolder.get()} in a
     * constructor is what killed two earlier launches of this project.
     *
     * <p>The charm itself implements nothing: this is the whole of what makes
     * it a curio, which keeps this module's knowledge of Curios in one file.
     */
    public static void registerCharm() {
        CuriosApi.registerCurio(ElysiumTrinkets.POTION_CHARM.get(),
                new top.theillusivec4.curios.api.type.capability.ICurioItem() {
                });
    }

    /**
     * Tells Curios about everything any Elysium module declared wearable.
     *
     * The armoury builds modular rings, bracelets and necklaces and is not
     * allowed to know Curios exists; this file is the only one that does. The
     * library holds the list in the middle, and this reads it — so a module
     * that adds a new kind of jewellery tomorrow becomes wearable without a
     * line changing here.
     *
     * <p>What a worn piece grants comes off the stack's own attribute
     * modifiers, the ones written with {@code EquipmentSlotGroup.ANY}. That is
     * the group vanilla means by "wherever this is", and reading it means the
     * bridge never has to know what a ring is made of or what its material
     * decided it should be worth.
     *
     * <p>Run at common setup rather than from the mod constructor, for the
     * same reason {@link #registerCharm} is: the items have to exist.
     */
    public static void registerWearables() {
        for (com.elysium.lib.trinket.ElysiumWearables.Wearable wearable
                : com.elysium.lib.trinket.ElysiumWearables.all()) {
            CuriosApi.registerCurio(wearable.item(),
                    new top.theillusivec4.curios.api.type.capability.ICurioItem() {
                        @Override
                        public com.google.common.collect.Multimap<
                                net.minecraft.core.Holder<
                                        net.minecraft.world.entity.ai.attributes.Attribute>,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier>
                                getAttributeModifiers(
                                        top.theillusivec4.curios.api.SlotContext context,
                                        net.minecraft.resources.ResourceLocation id,
                                        ItemStack stack) {
                            return granted(stack);
                        }
                    });
        }
    }

    /**
     * The attribute modifiers a worn stack grants, read off the stack itself.
     *
     * Only the ones written for {@code ANY}: a modular ring's are, and a
     * sword's attack damage is written for the main hand and must not follow
     * it into a necklace slot.
     */
    private static com.google.common.collect.Multimap<
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>,
            net.minecraft.world.entity.ai.attributes.AttributeModifier> granted(ItemStack stack) {
        com.google.common.collect.ImmutableMultimap.Builder<
                net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>,
                net.minecraft.world.entity.ai.attributes.AttributeModifier> out =
                com.google.common.collect.ImmutableMultimap.builder();
        net.minecraft.world.item.component.ItemAttributeModifiers modifiers =
                stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                        net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        for (net.minecraft.world.item.component.ItemAttributeModifiers.Entry entry
                : modifiers.modifiers()) {
            if (entry.slot() == net.minecraft.world.entity.EquipmentSlotGroup.ANY) {
                out.put(entry.attribute(), entry.modifier());
            }
        }
        return out.build();
    }

    /**
     * Everything this player has in an accessory slot, whatever it is.
     *
     * The second seam: the first hands the engine this mod's own trinkets, and
     * this hands it the stacks, so that anything worn — a modular ring, an
     * affixed amulet, another mod's charm — is asked what effects it carries
     * exactly as a helmet is. Cached on the same tick key and for the same
     * reason.
     */
    private static final Map<UUID, StackSnapshot> STACKS = new ConcurrentHashMap<>();

    private record StackSnapshot(int tick, List<ItemStack> worn) {
    }

    private static List<ItemStack> wornStacks(Player player) {
        if (player == null) {
            return List.of();
        }
        StackSnapshot cached = STACKS.get(player.getUUID());
        if (cached != null && cached.tick() == player.tickCount) {
            return cached.worn();
        }
        List<ItemStack> worn = CuriosApi.getCuriosInventory(player)
                .map(inventory -> inventory.findCurios(stack -> !stack.isEmpty()))
                .orElse(List.of())
                .stream()
                .map(SlotResult::stack)
                .toList();
        STACKS.put(player.getUUID(), new StackSnapshot(player.tickCount, worn));
        return worn;
    }

    /** Installs the adapter. Called once, from the mod constructor. */
    public static void install() {
        // Fully qualified: the library's ElysiumTrinkets and this mod's own
        // main class share a simple name. Importing both is not possible and
        // renaming either would be worse - the library class is the extension
        // point add-ons read about, and the mod class has to match its mod id.
        com.elysium.lib.trinket.ElysiumTrinkets.setProvider(CuriosSlots::equipped);
        com.elysium.lib.trinket.ElysiumTrinkets.setStackProvider(CuriosSlots::wornStacks);
    }

    /** Forgets a player. Called on logout; purely to bound the map. */
    public static void forget(UUID player) {
        CACHE.remove(player);
        STACKS.remove(player);
    }

    private static List<com.elysium.lib.trinket.ElysiumTrinkets.Equipped> equipped(Player player) {
        if (player == null) {
            return List.of();
        }
        Snapshot cached = CACHE.get(player.getUUID());
        if (cached != null && cached.tick() == player.tickCount) {
            return cached.worn();
        }
        List<com.elysium.lib.trinket.ElysiumTrinkets.Equipped> worn = scan(player);
        CACHE.put(player.getUUID(), new Snapshot(player.tickCount, worn));
        return worn;
    }

    /**
     * The one call into Curios.
     *
     * The ascension tier comes off the stack's own gear data — the same
     * component armour and weapons use — rather than a trinket-specific one, so
     * a trinket ascended at the reforge table reports the tier that table wrote
     * without anything here knowing how it got there.
     */
    private static List<com.elysium.lib.trinket.ElysiumTrinkets.Equipped> scan(Player player) {
        List<com.elysium.lib.trinket.ElysiumTrinkets.Equipped> worn = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            for (SlotResult result : inventory.findCurios(CuriosSlots::isTrinket)) {
                ItemStack stack = result.stack();
                if (!(stack.getItem() instanceof ElysiumTrinketItem item)) {
                    continue;
                }
                worn.add(new com.elysium.lib.trinket.ElysiumTrinkets.Equipped(
                        item.getTrinket(), item.getEffectiveTier(stack)));
            }
        });
        return List.copyOf(worn);
    }

    private static boolean isTrinket(ItemStack stack) {
        return stack.getItem() instanceof ElysiumTrinketItem;
    }
}
