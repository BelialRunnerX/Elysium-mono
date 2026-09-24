package com.elysium.lib.item;

import com.elysium.lib.affix.ElysiumAffix;
import com.elysium.lib.element.ElysiumElement;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;

/**
 * The rune socket system, shared by armour, weapons and tools.
 *
 * The rule that matters: <b>a rune aligned to the item's element hits
 * harder.</b> A Voidward rune in Voidglass gear is resonating with the metal;
 * the same rune in Neutronium still works, it is just fighting the material.
 * Nothing is ever locked out — mismatched runes give exactly what they always
 * gave, and aligned ones give {@link #ALIGNED_MULTIPLIER} times that. Alignment
 * is a reward, never a gate.
 *
 * Utility runes have no element, so they are never aligned and never penalised.
 * They are the flat option you take when you cannot get a match.
 */
public final class ElysiumSockets {

    private ElysiumSockets() {
    }

    /** What an aligned rune is worth, relative to the same rune mismatched. */
    public static final float ALIGNED_MULTIPLIER = 1.75F;

    // ------------------------------------------------------------------
    // Stack data
    // ------------------------------------------------------------------

    public static ElysiumGearData gearData(ItemStack stack) {
        return stack.getOrDefault(ElysiumComponents.GEAR_DATA.get(), ElysiumGearData.EMPTY);
    }

    public static void setGearData(ItemStack stack, ElysiumGearData data) {
        stack.set(ElysiumComponents.GEAR_DATA.get(), data);
    }

    /** A rune in a socket, and the tier it was socketed at. */
    public record Socketed(ElysiumRune rune, int tier) {

        /** N, for a tier-N rune. See {@link ElysiumRuneTiers}. */
        public float scale() {
            return ElysiumRuneTiers.multiplier(tier);
        }
    }

    /**
     * Every socketed rune with its tier, in socket order.
     *
     * The tier is read by socket index, which is why a rune whose id no longer
     * resolves is skipped only after its index is counted — skipping it first
     * would shift every later rune onto its neighbour's tier.
     */
    public static List<Socketed> socketed(ItemStack stack) {
        ElysiumGearData data = gearData(stack);
        List<Socketed> out = new ArrayList<>();
        List<String> names = data.runes();
        for (int i = 0; i < names.size(); i++) {
            ElysiumRune parsed = ElysiumRune.REGISTRY.get(names.get(i));
            if (parsed != null) {
                out.add(new Socketed(parsed, data.tierAt(i)));
            }
        }
        return out;
    }

    public static List<ElysiumRune> socketedRunes(ItemStack stack) {
        List<ElysiumRune> runes = new ArrayList<>();
        for (String name : gearData(stack).runes()) {
            ElysiumRune parsed = ElysiumRune.REGISTRY.get(name);
            if (parsed != null) {
                runes.add(parsed);
            }
        }
        return runes;
    }

    /**
     * Socket capacity by tier, the same curve for every kind of gear.
     *
     * One slot per two tiers, with no ceiling — the old table stopped at three
     * slots, which quietly capped the whole rune system at the moment
     * ascension stopped being capped.
     */
    public static int maxSlots(int tier) {
        return 1 + Math.max(0, tier) / 2;
    }

    /**
     * How many sockets this particular piece has: what its tier allows, plus
     * any a Sigil of Socketing added.
     *
     * The piece rather than the tier, because a sigil is a thing that happened
     * to one item and not a rule about every item of that tier.
     */
    public static int maxSlots(ItemStack stack, int tier) {
        return maxSlots(tier) + Math.max(0, gearData(stack).extraSockets());
    }

    public static boolean socket(ItemStack stack, int tier, ElysiumRune rune) {
        return socket(stack, tier, rune, ElysiumRuneTiers.BASE);
    }

    /**
     * Socket a rune at a rune tier. {@code gearTier} is the piece's tier, which
     * decides how many sockets it has; {@code runeTier} is the rune's, which
     * decides how much it does.
     */
    public static boolean socket(ItemStack stack, int gearTier, ElysiumRune rune,
                                 int runeTier) {
        List<ElysiumRune> current = socketedRunes(stack);
        if (current.size() >= maxSlots(stack, gearTier) || current.contains(rune)) {
            return false;
        }
        setGearData(stack, gearData(stack).withRuneAdded(
                rune.getSerialisedName(), runeTier));
        return true;
    }

    // ------------------------------------------------------------------
    // Alignment
    // ------------------------------------------------------------------

    public static boolean isAligned(ElysiumRune rune, ElysiumElement element) {
        ElysiumElement runeElement = rune.getElement();
        return runeElement.isElemental() && runeElement == element;
    }

    public static float alignmentScale(ElysiumRune rune, ElysiumElement element) {
        return isAligned(rune, element) ? ALIGNED_MULTIPLIER : 1.0F;
    }

    // ------------------------------------------------------------------
    // Attribute application
    // ------------------------------------------------------------------

    /**
     * Adds one modifier per socketed rune, scaled by whether it aligns.
     *
     * The slot group is folded into each modifier's id. Without that, a
     * Voidward rune in a helmet and another in a chestplate would produce two
     * modifiers with identical ids on the same attribute, and only one of them
     * would survive.
     */
    public static ItemAttributeModifiers applyRunes(ItemStack stack,
                                                    ElysiumElement element,
                                                    ItemAttributeModifiers modifiers,
                                                    EquipmentSlotGroup group) {
        for (Socketed socketed : socketed(stack)) {
            ElysiumRune rune = socketed.rune();
            ElysiumAffix affix = rune.getAffix();
            if (affix == null) {
                // A rune with no affix is behaviour rather than an attribute —
                // a dodge chance or a recharging shield. The tick handler and
                // the damage pipeline pick those up.
                continue;
            }
            // Tier N does N times the base. Alignment and tier multiply, so an
            // aligned tier-3 rune is 1.75 x 3 its base.
            float scale = alignmentScale(rune, element) * socketed.scale();
            // A percentage affix scales its stat, not its share: see isShare.
            modifiers = modifiers.withModifierAdded(affix.getAttribute(),
                    affix.isShare()
                            ? affix.createShareModifier(1.0F, scale, group)
                            : affix.createModifier(1.0F, scale, group), group);
        }
        return modifiers;
    }
}
