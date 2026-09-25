package com.elysium.arsenal.registry;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.item.BowItem;
import com.elysium.arsenal.item.WeaponItem;
import com.elysium.arsenal.rules.RangedRules;
import com.elysium.arsenal.rules.UniqueRules;
import com.elysium.arsenal.rules.WeaponRules;
import com.elysium.lib.ability.ElysiumAbilityRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Twenty-four named weapons, each an ordinary one that also does something.
 *
 * <p>Nothing here invents a number. The weapon underneath is whatever
 * {@link WeaponRules} or {@link RangedRules} says a weapon of that shape and
 * tier is, and the something is an ability out of the library, priced there
 * and checked there. A unique that carried better numbers than its own shape
 * would make that shape not worth carrying, which is the thing this module
 * exists not to do — so it does not.
 */
public final class UniqueItems {

    private UniqueItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumArsenal.MODID);

    private static final Rarity[] COLOURS = {
            Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC};

    /**
     * The smithing template every unique is made with.
     *
     * <p>One template rather than twenty-four, because which unique comes out
     * is already decided by the weapon that goes in: every unique sits on a
     * shape-and-tier pair no other unique sits on, which
     * {@link UniqueRules#indistinguishable()} is what keeps true. A template
     * per unique would be twenty-four items whose only job is to repeat
     * something the base already says.
     */
    public static final DeferredItem<Item> SIGIL = ITEMS.register("legend_sigil",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final Map<String, DeferredItem<Item>> ALL = register();

    private static Map<String, DeferredItem<Item>> register() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (UniqueRules.Unique unique : UniqueRules.UNIQUES) {
            // Refused here as well as in the harness. A unique added by hand
            // later would never go through a harness at all.
            List<String> trouble =
                    ElysiumAbilityRules.cannotShareAnItem(unique.abilities());
            if (!trouble.isEmpty()) {
                throw new IllegalStateException(unique.id() + " carries abilities no "
                        + "item is allowed to carry: " + trouble);
            }
            Rarity colour = COLOURS[Math.max(0,
                    Math.min(COLOURS.length - 1, unique.rarity() - 1))];
            made.put(unique.id(), ITEMS.register(unique.id(),
                    () -> build(unique, colour)));
        }
        if (made.size() != UniqueRules.UNIQUES.size()) {
            throw new IllegalStateException("there are "
                    + UniqueRules.UNIQUES.size() + " uniques and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }

    /**
     * How many of the twenty-four are bows.
     *
     * <p>Counted off the roster rather than written down, because the client
     * checks that every bow in the module knows how to draw and a number
     * typed here would make that check agree with itself instead of with the
     * roster.
     */
    public static int rangedCount() {
        int bows = 0;
        for (UniqueRules.Unique unique : UniqueRules.UNIQUES) {
            if (unique.ranged()) {
                bows++;
            }
        }
        return bows;
    }

    private static Item build(UniqueRules.Unique unique, Rarity colour) {
        Item.Properties properties = new Item.Properties().rarity(colour)
                .stacksTo(1).fireResistant();
        if (unique.ranged()) {
            RangedRules.Bow shape = RangedRules.kind(unique.shape());
            if (shape == null) {
                throw new IllegalStateException(unique.id() + " is built on a "
                        + unique.shape() + ", which is not a bow");
            }
            Integer uses = RangedItems.USES.get(unique.tier());
            if (uses == null) {
                throw new IllegalStateException(unique.tier() + " is a tier with no "
                        + "durability, so " + unique.id() + " would be unbreakable");
            }
            return new UniqueBow(properties.durability(uses),
                    RangedRules.at(shape, unique.tier()), unique);
        }
        WeaponRules.Weapon shape = null;
        for (WeaponRules.Weapon weapon : WeaponRules.KINDS) {
            if (weapon.name().equals(unique.shape())) {
                shape = weapon;
                break;
            }
        }
        if (shape == null) {
            throw new IllegalStateException(unique.id() + " is built on a "
                    + unique.shape() + ", which is not a weapon");
        }
        Tier tier = ArsenalItems.TIERS.get(unique.tier());
        if (tier == null) {
            throw new IllegalStateException(unique.tier() + " has no vanilla tier "
                    + "behind it, so " + unique.id() + " would have no durability");
        }
        return new UniqueWeapon(tier, unique.tier(), shape, properties, unique);
    }

    /**
     * The words a unique says about itself.
     *
     * <p>Read off the ability roster rather than written into a translation
     * string, so retuning a cooldown changes what the tooltip says. A tooltip
     * that stops being true is worse than no tooltip.
     */
    static void describe(UniqueRules.Unique unique, List<Component> lines) {
        lines.add(Component.translatable("elysiumarsenal.unique.shape",
                        Component.translatable(
                                "elysiumarsenal.shape." + unique.shape()),
                        Component.translatable(
                                "elysiumarsenal.tier." + unique.tier()))
                .withStyle(ChatFormatting.GRAY));
        for (String name : unique.abilities()) {
            ElysiumAbilityRules.Ability ability = ElysiumAbilityRules.ability(name);
            if (ability == null) {
                continue;
            }
            lines.add(Component.translatable("elysiumarsenal.ability." + name)
                    .withStyle(ChatFormatting.AQUA));
            if (ElysiumAbilityRules.PASSIVE.equals(ability.kind())) {
                lines.add(Component.translatable("elysiumarsenal.unique.always")
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                lines.add(Component.translatable("elysiumarsenal.unique.every",
                                String.format("%.1f", ability.cooldown() / 20.0F))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    /** A named melee weapon. */
    public static class UniqueWeapon extends WeaponItem {

        private final UniqueRules.Unique unique;

        public UniqueWeapon(Tier tier, String tierName, WeaponRules.Weapon kind,
                            Properties properties, UniqueRules.Unique unique) {
            super(tier, tierName, kind, properties);
            this.unique = unique;
        }

        /** Which unique this is. */
        public UniqueRules.Unique unique() {
            return unique;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    List<Component> lines, TooltipFlag flag) {
            super.appendHoverText(stack, context, lines, flag);
            describe(unique, lines);
        }
    }

    /** A named bow. */
    public static class UniqueBow extends BowItem {

        private final UniqueRules.Unique unique;

        public UniqueBow(Properties properties, RangedRules.Bow bow,
                         UniqueRules.Unique unique) {
            super(properties, bow);
            this.unique = unique;
        }

        /** Which unique this is. */
        public UniqueRules.Unique unique() {
            return unique;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    List<Component> lines, TooltipFlag flag) {
            super.appendHoverText(stack, context, lines, flag);
            describe(unique, lines);
        }
    }
}
