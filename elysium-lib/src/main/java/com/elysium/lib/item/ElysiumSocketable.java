package com.elysium.lib.item;

import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.stats.ElysiumGearStats;
import com.elysium.lib.stats.ElysiumStat;
import com.elysium.lib.stats.ElysiumStatBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;
import java.util.Locale;

/**
 * Anything that can take runes: armour, weapons and tools alike.
 *
 * Socketing used to be armour-only, which made rune alignment a half-idea —
 * a Voidglass hammer with a Void affinity had nothing to align *with*. One
 * interface over all three kinds means a rune is a rune wherever it goes, and
 * the only thing that changes is whether it matches.
 */
public interface ElysiumSocketable {

    /** The element this piece resonates with, or {@code NONE} for inert gear. */
    ElysiumElement getElement();

    /**
     * The Elysium tier this item was registered at, ignoring ascension.
     *
     * <b>Not</b> {@code getTier()}. Every weapon and tool in this mod descends
     * from {@code TieredItem}, which already has a {@code getTier()} returning
     * a vanilla {@code Tier} — an interface method of the same name returning
     * an int is an incompatible override, and the compiler rejects the class
     * outright. The two concepts are genuinely different: a vanilla Tier is a
     * mining level, an Elysium tier is a progression rank that ascension can
     * push past Sovereign. They need different names.
     */
    int getElysiumTier();

    /**
     * How much of the wielder's Strength this item turns into damage.
     *
     * The stat system's rule is {@code base damage x item multiplier}: the
     * character supplies the base, the item decides how much of it lands. A
     * chestplate multiplies nothing, which is why the default is 1.0 and only
     * things you swing override it.
     */
    default float getDamageMultiplier() {
        return 1.0F;
    }

    /**
     * True for something worn rather than swung.
     *
     * The engine needs to tell the two apart in three places — the counter
     * matrix reads worn affinity, the stat grant gives armour extra Fortitude,
     * and a held chestplate is not a weapon. It used to ask
     * {@code instanceof ElysiumArmorItem}, which only the mod defining that
     * class could ever satisfy.
     */
    default boolean isArmour() {
        return false;
    }

    /** The tier this particular stack is at, including ascension. */
    default int getEffectiveTier(ItemStack stack) {
        int ascended = ElysiumSockets.gearData(stack).ascendedTier();
        return ascended >= 0 ? ascended : getElysiumTier();
    }

    /**
     * Whether this stack can be ascended another tier.
     *
     * Always, by default, and there is no ceiling: past Sovereign a piece
     * becomes Ascendant 1, 2, 3 and onward. The cost is the limit — each step
     * needs a second piece at the same tier, so the price doubles every time.
     *
     * Lived on {@code ElysiumArmorItem} until weapons and tools became
     * ascendable too, which meant the answer to "can this be ascended" was only
     * askable of the one kind of gear that had it.
     */
    default boolean canAscend(ItemStack stack) {
        return true;
    }

    default int getNextTier(ItemStack stack) {
        return getEffectiveTier(stack) + 1;
    }

    default List<ElysiumRune> getSocketedRunes(ItemStack stack) {
        return ElysiumSockets.socketedRunes(stack);
    }

    default int getMaxRuneSlots(ItemStack stack) {
        return ElysiumSockets.maxSlots(getEffectiveTier(stack));
    }

    default boolean socketRune(ItemStack stack, ElysiumRune rune) {
        return ElysiumSockets.socket(stack, getEffectiveTier(stack), rune);
    }

    default ItemAttributeModifiers applyRunes(ItemStack stack,
                                              ItemAttributeModifiers modifiers,
                                              EquipmentSlotGroup group) {
        return ElysiumSockets.applyRunes(stack, getElement(), modifiers, group);
    }

    // ------------------------------------------------------------------
    // Ascension
    // ------------------------------------------------------------------
    //
    // Ascension used to raise a piece's *rank* and nothing a player could feel
    // on the piece itself: an ascended chestplate had the armour of the day it
    // was forged, and an ascended blade hit for what it always hit for. The
    // growth was entirely in the character-stat system next door, which is a
    // real effect and reads like nothing at all when the number on the tooltip
    // has not moved.
    //
    // The three methods below are what an item declares about itself, and the
    // one below them is what turns that into modifiers. An item that swings
    // overrides the damage one; an item that is worn overrides the armour ones;
    // a trinket that does neither overrides none and still ascends, because its
    // behaviour reads the tier directly.

    /**
     * This item's own attack damage before ascension, or 0 for something that
     * is not swung.
     *
     * Declared rather than read back off the attribute component because the
     * component is built from it — a weapon knows its own damage at
     * construction, and reading it back out means parsing a list to recover a
     * number the constructor was handed.
     */
    default float getBaseAttackDamage() {
        return 0.0F;
    }

    /** This item's own armour points before ascension; 0 if it is not worn. */
    default float getBaseArmour() {
        return 0.0F;
    }

    /** This item's own armour toughness before ascension. */
    default float getBaseToughness() {
        return 0.0F;
    }

    /**
     * Adds the tier's share of armour, toughness and attack damage.
     *
     * <h2>Why the modifier id carries the slot</h2>
     *
     * An entity's attribute modifiers are keyed by {@link ResourceLocation}. A
     * helmet and a chestplate that both added {@code elysiumlib:ascension/armor}
     * would be one key, and only one of the two would apply — the other would
     * silently do nothing, which is precisely the bug vanilla avoids by naming
     * its own armour modifiers per slot. So the group is part of the id.
     *
     * <h2>Why armour is not capped here</h2>
     *
     * Vanilla's damage formula already has diminishing returns on armour, so
     * a geometric armour curve flattens on its own in the only place that
     * matters. Toughness and attack damage do not flatten, and are deliberately
     * left to grow — that is the point of ascending, and the price doubles
     * every tier to pay for it.
     */
    default ItemAttributeModifiers applyAscension(ItemStack stack,
                                                  ItemAttributeModifiers modifiers,
                                                  EquipmentSlotGroup group) {
        int tier = getEffectiveTier(stack);
        if (tier <= 0) {
            return modifiers;
        }

        modifiers = addAscension(modifiers, group, Attributes.ARMOR,
                "armor", getBaseArmour(), tier);
        modifiers = addAscension(modifiers, group, Attributes.ARMOR_TOUGHNESS,
                "toughness", getBaseToughness(), tier);
        modifiers = addAscension(modifiers, group, Attributes.ATTACK_DAMAGE,
                "attack_damage", getBaseAttackDamage(), tier);
        return modifiers;
    }

    private static ItemAttributeModifiers addAscension(ItemAttributeModifiers modifiers,
                                                       EquipmentSlotGroup group,
                                                       Holder<Attribute> attribute,
                                                       String what,
                                                       float base,
                                                       int tier) {
        double amount = ElysiumAscension.added(base, tier);
        if (amount <= 0.0D) {
            // Nothing to add. Skipped rather than added as a zero, so an
            // un-ascended piece's tooltip is byte-for-byte what it always was.
            return modifiers;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("elysiumlib",
                "ascension/" + what + "/" + group.name().toLowerCase(Locale.ROOT));
        return modifiers.withModifierAdded(attribute,
                new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE),
                group);
    }

    /**
     * Every per-stack modifier Elysium adds, in one call.
     *
     * <h2>Why this exists rather than two calls at each site</h2>
     *
     * There are nine {@code getDefaultAttributeModifiers} overrides across the
     * gear in this project and every one of them used to call
     * {@link #applyRunes} directly. Adding ascension as a second call at each
     * site means a tenth item — the next weapon, the next tool, a trinket —
     * silently gets runes and no ascension, and nothing anywhere would say so.
     * One call that means "the Elysium treatment" is a thing a new item can
     * fail to make loudly, rather than a thing it can half-make quietly; the
     * lifecycle checker enforces that nothing calls {@code applyRunes} on its
     * own any more.
     */
    default ItemAttributeModifiers elysiumModifiers(ItemStack stack,
                                                    ItemAttributeModifiers modifiers,
                                                    EquipmentSlotGroup group) {
        return applyAscension(stack, applyRunes(stack, modifiers, group), group);
    }

    /**
     * The shared block of tooltip lines: element and tier, Imperial clearance,
     * the stats the piece grants and the level it asks for, then each socketed
     * rune with its alignment called out.
     */
    default void appendSocketTooltip(ItemStack stack, List<Component> tooltip) {
        appendIdentityTooltip(stack, tooltip);
        appendStatTooltip(stack, tooltip);
        appendRuneTooltip(stack, tooltip);
    }

    /**
     * What the piece gives, and what it costs to use.
     *
     * The level requirement is printed whether or not the reader meets it. A
     * tooltip cannot see the player holding it without reaching into
     * client-only code, and guessing wrong about who is looking is worse than
     * simply stating the requirement — a player who reads "Requires level 25"
     * and finds nothing happening has been told exactly why.
     */
    default void appendStatTooltip(ItemStack stack, List<Component> tooltip) {
        int required = ElysiumGearStats.requiredLevel(stack);
        if (required > 0) {
            tooltip.add(Component.translatable("elysium.tooltip.requires_level", required)
                    .withStyle(ChatFormatting.YELLOW));
        }

        ElysiumStatBlock granted = ElysiumGearStats.of(stack);
        for (ElysiumStat stat : ElysiumStat.REGISTRY.all()) {
            int amount = granted.get(stat);
            if (amount == 0) {
                continue;
            }
            tooltip.add(Component.literal(" +" + amount + " ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(stat.getDisplayName()));
        }
    }

    /** Element, tier and Imperial clearance. */
    default void appendIdentityTooltip(ItemStack stack, List<Component> tooltip) {
        int tier = getEffectiveTier(stack);
        tooltip.add(getElement().getDisplayName()
                .copy()
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(ElysiumRarities.getTierComponent(tier)));
        tooltip.add(ElysiumRarities.getClearance(tier));
    }

    /**
     * Socket count and one line per rune, aligned runes flagged in gold. Split
     * from the identity block so armour can slot its counter-matrix line in
     * between without duplicating any of this.
     */
    default void appendRuneTooltip(ItemStack stack, List<Component> tooltip) {
        List<ElysiumRune> runes = getSocketedRunes(stack);
        tooltip.add(Component.translatable("elysium.tooltip.runes",
                runes.size(), getMaxRuneSlots(stack)).withStyle(ChatFormatting.DARK_AQUA));

        for (ElysiumRune rune : runes) {
            boolean aligned = ElysiumSockets.isAligned(rune, getElement());
            Component line = Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable("elysium.rune." + rune.getSerialisedName() + ".effect")
                            .withStyle(aligned ? ChatFormatting.AQUA : ChatFormatting.GRAY));
            if (aligned) {
                line = line.copy().append(Component.literal(" ")
                        .append(Component.translatable("elysium.tooltip.aligned")
                                .withStyle(ChatFormatting.GOLD)));
            }
            tooltip.add(line);
        }
    }
}
