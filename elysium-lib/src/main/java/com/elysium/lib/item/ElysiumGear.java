package com.elysium.lib.item;

import com.elysium.lib.element.ElysiumElement;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a stack is gear, and what Elysium makes of it.
 *
 * <h2>The question this replaces</h2>
 *
 * Everything in this engine used to ask {@code stack.getItem() instanceof
 * ElysiumSocketable}, in thirteen places. That is a test only classes compiled
 * against this library can pass, so it drew a hard line down the middle of the
 * game: an Elysium iron hammer took runes, granted stats, reforged and
 * ascended, and a <em>vanilla iron sword</em> — the same metal, made by the
 * same player, in the same world — was inert. Not weaker. Outside the systems
 * entirely, with no way in.
 *
 * That is the same mistake {@link ElysiumSocketable#isArmour()} was written to
 * fix one level down, where the engine asked {@code instanceof
 * ElysiumArmorItem} and no other mod's armour could ever answer yes. The fix
 * there was to ask the item a question instead of asking what class it is.
 * This is that fix applied to the outer gate.
 *
 * <h2>What counts as gear now</h2>
 *
 * Anything you could sensibly swing or wear:
 *
 * <ul>
 *   <li>anything that already implements {@link ElysiumSocketable} — returned
 *       unchanged, so every existing Elysium item behaves exactly as before;</li>
 *   <li>anything worn in a humanoid armour slot that also supplies armour —
 *       both halves, because vanilla counts a carved pumpkin as head-wearable
 *       and it is not armour;</li>
 *   <li>any {@link TieredItem} — swords, pickaxes, axes, shovels and hoes,
 *       vanilla or modded;</li>
 *   <li>anything else carrying an attack-damage modifier in the hand, which is
 *       what catches a trident, a mace, and the weapon types mods invent that
 *       descend from none of the above.</li>
 * </ul>
 *
 * A stick is not gear. A block is not gear. A potion is not gear.
 *
 * <h2>Where a foreign item's element and tier come from</h2>
 *
 * Not invented here — read out of {@link ElysiumGearMaterial}, which this mod
 * was already maintaining for its own gear and which already has an opinion
 * about the vanilla metals. Iron is Kinetic, gold is Neural, diamond is
 * Dimensional, netherite is Void, copper is Plasma, each with a tier. So a
 * vanilla iron sword is not merely admitted to the system as an inert lump: it
 * is <b>Kinetic gear at iron's tier</b>, it takes a Kineticsurge rune at the
 * full aligned bonus, and it loses to the elements iron loses to. The answer
 * was already written down; nothing was asking it.
 *
 * <p>Two ways of finding the material, tried in order:
 *
 * <ol>
 *   <li><b>What repairs it.</b> {@code Item#isValidRepairItem} is the game's
 *       own "is this thing made of that thing", it works for armour and tools
 *       alike, and mods implement it because vanilla anvils need it. Each
 *       registered material names its ingredient as a tag, so this is one
 *       question asked against real items.</li>
 *   <li><b>Its tool tier.</b> A fallback for a tool whose repair ingredient is
 *       untagged: {@link TieredItem#getTier()} against each material's
 *       registered {@code toolTier}.</li>
 * </ol>
 *
 * Anything that matches neither is still gear — it is simply inert gear at tier
 * zero, which is a real state the stat system already handles (see
 * {@code ElysiumGearStats#forElement}). Unknown is not excluded.
 *
 * <h2>Why there is no conversion recipe</h2>
 *
 * There was a version of this feature that added one: a workstation that turned
 * a vanilla diamond sword into an Elysium item. It is the worse answer twice
 * over. Elysium's shapes are hammer, broadaxe, scythe and spear, so there is
 * nothing for a sword or a pickaxe to convert *into* without changing what the
 * tool does in the player's hand. And a conversion step is a chore levied on
 * the player for a problem the mod created: every enchanted, named, hard-won
 * piece in their world would have to be fed through a machine to keep
 * mattering. Admitting the gear is strictly better than charging admission.
 *
 * <h2>The cache</h2>
 *
 * Resolution walks the material registry and touches tags, and the combat and
 * stat handlers ask this question several times per hit and once per tick per
 * worn piece. The answer depends only on the {@link Item}, never on the stack,
 * so it is cached per item and cleared whenever tags reload — which is the one
 * thing that can change the answer.
 */
public final class ElysiumGear {

    private ElysiumGear() {
    }

    /**
     * Resolved descriptions for items that do not implement the interface.
     *
     * Keyed on Item, because that is the whole of what the answer depends on —
     * element, tier and kind are properties of the item, while runes, reforges
     * and ascension live on the stack and are read from the stack every time.
     */
    private static final Map<Item, Optional<ElysiumSocketable>> RESOLVED =
            new ConcurrentHashMap<>();

    // ------------------------------------------------------------------
    // The question everything asks
    // ------------------------------------------------------------------

    /**
     * What Elysium makes of this stack, or null if it is not gear at all.
     *
     * @return the item itself when it implements {@link ElysiumSocketable}, a
     *         description derived from its material otherwise, and null for
     *         anything that is not a weapon, tool or piece of armour
     */
    @Nullable
    public static ElysiumSocketable of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof ElysiumSocketable gear) {
            return gear;
        }
        return RESOLVED.computeIfAbsent(item, ElysiumGear::describe).orElse(null);
    }

    /** Whether this stack participates in Elysium's gear systems at all. */
    public static boolean is(ItemStack stack) {
        return of(stack) != null;
    }

    /**
     * Whether the item is one of Elysium's own, rather than adopted.
     *
     * <b>Not</b> a gate, and not to be used as one — that is the whole point of
     * this class. It answers one narrower question: does this item already
     * speak for itself?
     *
     * The tooltip handler is the caller that needs it. Elysium's own items
     * print their element, tier, stats and sockets from {@code appendHoverText}
     * on their own classes; adopted gear cannot, so an event adds those lines
     * for it. Without a way to tell the two apart, every Elysium item would
     * print its stats twice.
     *
     * It exists as a named method because the raw test is the same syntax as
     * the gate this class replaced, and an {@code instanceof ElysiumSocketable}
     * sitting in an event handler is indistinguishable at a glance from the
     * mistake — to a reader and to the validator that now watches for it.
     */
    public static boolean isNative(ItemStack stack) {
        return stack.getItem() instanceof ElysiumSocketable;
    }

    /**
     * Gear that is worn rather than swung, or null.
     *
     * The armour-only call sites want both halves of this test and reading them
     * as one call keeps them from drifting apart.
     */
    @Nullable
    public static ElysiumSocketable armour(ItemStack stack) {
        ElysiumSocketable gear = of(stack);
        return gear != null && gear.isArmour() ? gear : null;
    }

    /**
     * Gear that is swung rather than worn, or null.
     *
     * A chestplate held in the hand is not a weapon, which is a case the combat
     * handler has to get right — you can hold anything.
     */
    @Nullable
    public static ElysiumSocketable weapon(ItemStack stack) {
        ElysiumSocketable gear = of(stack);
        return gear != null && !gear.isArmour() ? gear : null;
    }

    /**
     * Forget every resolved item.
     *
     * Called when tags reload. A material's ingredient is a tag, so a datapack
     * change really can change which material an item is made of, and a cache
     * that outlived a reload would keep answering with the old pack's opinion.
     */
    public static void invalidate() {
        RESOLVED.clear();
    }

    /** How many foreign items have been resolved so far. For the log line. */
    public static int resolvedCount() {
        return RESOLVED.size();
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    private static Optional<ElysiumSocketable> describe(Item item) {
        Kind kind = kindOf(item);
        if (kind == Kind.NONE) {
            return Optional.empty();
        }
        ElysiumGearMaterial material = materialOf(item);
        return Optional.of(new Foreign(
                material != null ? material.getElement() : ElysiumElement.NONE,
                material != null ? material.getTier() : 0,
                kind == Kind.ARMOUR));
    }

    /**
     * TRUE for armour, FALSE for a weapon or tool, null for anything that is
     * not gear.
     *
     * Three states in one return rather than two methods, because "is it gear"
     * and "is it worn" are answered by the same walk over the item and asking
     * twice invites them to disagree.
     */
    /** What Elysium makes of an item's shape. */
    enum Kind {
        /** Worn, and it protects you. */
        ARMOUR,
        /** Swung or wielded. */
        HELD,
        /** Not gear. */
        NONE
    }

    /**
     * The gear test, as pure logic over four facts about an item.
     *
     * Separated from the item so it can be executed outside the game — see
     * {@code tools/GearHarness.java}. What goes wrong here goes wrong quietly:
     * the rule admits something it should not and a player finds a socketable
     * creeper head, or it turns something away and a whole mod's armour is
     * silently outside the game's systems. Neither fails to compile and neither
     * appears in a log, and the table of cases is long enough that reading it
     * is not the same as checking it.
     *
     * @param humanoidArmourSlot   the item can be worn in a player armour slot
     * @param suppliesArmour       wearing it adds to the armour attribute
     * @param tiered               it is a {@link TieredItem}: a vanilla-shaped
     *                             sword, pickaxe, axe, shovel or hoe
     * @param suppliesAttackDamage holding it adds to attack damage
     */
    static Kind classify(boolean humanoidArmourSlot, boolean suppliesArmour,
                         boolean tiered, boolean suppliesAttackDamage) {
        // Both halves. Vanilla's own "can this be worn on a head" says yes to a
        // carved pumpkin and to every mob head, because those do go in the head
        // slot — so the wearable test alone made a creeper head into socketable
        // Elysium armour with a stat grant attached. Asking whether the piece
        // supplies armour is the question that was meant, it is one every mod's
        // armour answers because the game reads it to compute defence, and it
        // correctly turns away an elytra, which is worn on the chest and
        // protects nothing.
        if (humanoidArmourSlot && suppliesArmour) {
            return Kind.ARMOUR;
        }
        // Armour tested first, so a piece that somehow does both is worn rather
        // than swung — that is the slot it spends its life in.
        if (tiered || suppliesAttackDamage) {
            return Kind.HELD;
        }
        return Kind.NONE;
    }

    private static Kind kindOf(Item item) {
        ItemStack stack = item.getDefaultInstance();
        ItemAttributeModifiers modifiers = stack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        // ANIMAL_ARMOR is excluded here rather than in classify(): horse barding
        // is not something a player wears, and admitting it would put a socketed
        // chestplate's worth of Fortitude on a horse.
        Equipable equipable = Equipable.get(stack);
        EquipmentSlot slot = equipable != null ? equipable.getEquipmentSlot() : null;
        boolean humanoidArmourSlot =
                slot != null && slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;

        return classify(
                humanoidArmourSlot,
                humanoidArmourSlot && grants(modifiers, Attributes.ARMOR, slot),
                item instanceof TieredItem,
                grants(modifiers, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND));
    }

    /** Whether the item adds anything to one attribute in one slot. */
    private static boolean grants(ItemAttributeModifiers modifiers,
                                  Holder<Attribute> attribute, EquipmentSlot slot) {
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().is(attribute.getKey())
                    && entry.slot().test(slot)
                    && entry.modifier().amount() > 0.0D) {
                return true;
            }
        }
        return false;
    }

    /**
     * What this item is made of, or null if nothing registered claims it.
     *
     * Deterministic despite walking a registry: materials are checked in
     * registration order and the first match wins, so two materials sharing an
     * ingredient resolve the same way on every machine and every launch rather
     * than by hash order.
     */
    @Nullable
    private static ElysiumGearMaterial materialOf(Item item) {
        ItemStack stack = item.getDefaultInstance();

        for (ElysiumGearMaterial material : ElysiumGearMaterial.REGISTRY.all()) {
            if (repairs(material, item, stack)) {
                return material;
            }
        }
        if (item instanceof TieredItem tiered) {
            for (ElysiumGearMaterial material : ElysiumGearMaterial.REGISTRY.all()) {
                if (material.getToolTier() == tiered.getTier()) {
                    return material;
                }
            }
        }
        return null;
    }

    private static boolean repairs(ElysiumGearMaterial material, Item item, ItemStack stack) {
        return BuiltInRegistries.ITEM.getTag(material.getIngredientTag())
                .map(entries -> {
                    for (var holder : entries) {
                        if (item.isValidRepairItem(stack, new ItemStack(holder.value()))) {
                            return true;
                        }
                    }
                    return false;
                })
                .orElse(false);
    }

    // ------------------------------------------------------------------

    /**
     * An item that is gear without knowing it.
     *
     * Deliberately the plain defaults for everything {@link ElysiumSocketable}
     * lets an item override. A foreign weapon gets a damage multiplier of 1.0
     * rather than a guess derived from its attack damage: the multiplier is
     * Elysium's statement about how well a <em>shape</em> carries Strength — a
     * hammer 1.6, a sword 1.0 — and inventing one for somebody else's item
     * would be this mod quietly rebalancing another mod's weapons.
     */
    private record Foreign(ElysiumElement element, int tier, boolean worn)
            implements ElysiumSocketable {

        @Override
        public ElysiumElement getElement() {
            return element;
        }

        @Override
        public int getElysiumTier() {
            return tier;
        }

        @Override
        public boolean isArmour() {
            return worn;
        }
    }
}
