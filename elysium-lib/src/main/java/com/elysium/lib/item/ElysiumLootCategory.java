package com.elysium.lib.item;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What kind of thing a stack is, in the vocabulary of {@link ElysiumGearTree}.
 *
 * <h2>The question this answers</h2>
 *
 * {@link ElysiumGear} answers "is this gear at all", which is the gate into
 * the mod's systems. That was enough while everything an item could receive
 * applied to all gear equally. It is not enough for affixes: a pool of
 * bowstring affixes on a helmet is nonsense, and "only on melee weapons" is a
 * sentence the data has to be able to write.
 *
 * <p>So this is the finer question, asked the same way: not "what class is
 * it", which only this mod's own items can answer, but a series of questions
 * any item can be asked.
 *
 * <ol>
 *   <li><b>It says so.</b> Anything implementing {@link ElysiumKinded} names
 *       its own kind. Modular gear has to — a sword and a pickaxe are the same
 *       class with different parts — and other mods may.</li>
 *   <li><b>Its class.</b> The vanilla item classes, which most of the game and
 *       most mods still descend from.</li>
 *   <li><b>What it does.</b> A worn piece that supplies armour is armour, by
 *       its slot; anything else carrying attack damage in the hand is a melee
 *       weapon whatever it descends from.</li>
 * </ol>
 *
 * Anything that answers none of those is not gear, and gets {@code null}: a
 * stick, a block, a potion.
 *
 * <h2>The cache</h2>
 *
 * The answer depends only on the item, never on the stack, and affix rolling
 * asks it per item per roll. It is cached per item and cleared with
 * {@link ElysiumGear}'s cache, since a tag reload can change an answer.
 */
public final class ElysiumLootCategory {

    private ElysiumLootCategory() {
    }

    private static final Map<Item, String> RESOLVED = new ConcurrentHashMap<>();

    /** The sentinel a cache stores for "not gear", since a map holds no nulls. */
    private static final String NONE = "";

    /**
     * The kind this stack is, or null for anything that is not gear.
     *
     * @return a kind name from {@link ElysiumGearTree}
     */
    @Nullable
    public static String of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String kind = RESOLVED.computeIfAbsent(stack.getItem(), ElysiumLootCategory::resolve);
        return kind.equals(NONE) ? null : kind;
    }

    /** Whether a stack answers to a selector — {@code "melee_weapon"}, {@code "not:armor"}, {@code "any"}. */
    public static boolean is(ItemStack stack, String selector) {
        String kind = of(stack);
        return kind != null && ElysiumGearTree.matches(kind, selector);
    }

    /** Whether a stack answers to at least one of a list of selectors. */
    public static boolean isAny(ItemStack stack, List<String> selectors) {
        String kind = of(stack);
        return kind != null && ElysiumGearTree.matchesAny(kind, selectors);
    }

    /** Forget every resolved item. Called when tags reload. */
    public static void invalidate() {
        RESOLVED.clear();
    }

    // ------------------------------------------------------------------

    private static String resolve(Item item) {
        if (item instanceof ElysiumKinded kinded) {
            String named = kinded.gearKind();
            if (ElysiumGearTree.exists(named)) {
                return named;
            }
        }
        String byClass = byClass(item);
        if (byClass != null) {
            return byClass;
        }
        String worn = byWear(item);
        if (worn != null) {
            return worn;
        }
        return hits(item) ? "melee_weapon" : NONE;
    }

    @Nullable
    private static String byClass(Item item) {
        if (item instanceof SwordItem) {
            return "sword";
        }
        if (item instanceof MaceItem) {
            return "mace";
        }
        if (item instanceof AxeItem) {
            return "axe";
        }
        if (item instanceof PickaxeItem) {
            return "pickaxe";
        }
        if (item instanceof ShovelItem) {
            return "shovel";
        }
        if (item instanceof HoeItem) {
            return "hoe";
        }
        if (item instanceof ShearsItem) {
            return "shears";
        }
        if (item instanceof TridentItem) {
            return "trident";
        }
        if (item instanceof BowItem) {
            return "bow";
        }
        if (item instanceof CrossbowItem) {
            return "crossbow";
        }
        if (item instanceof ShieldItem) {
            return "shield";
        }
        if (item instanceof FishingRodItem) {
            return "fishing_rod";
        }
        if (item instanceof ElytraItem) {
            return "elytra";
        }
        if (item instanceof ArmorItem armour) {
            return bySlot(armour.getEquipmentSlot());
        }
        return null;
    }

    /**
     * Worn and armouring, which is the pair of questions {@link ElysiumGear}
     * asks: vanilla counts a carved pumpkin as head-wearable, and a pumpkin is
     * not a helmet.
     */
    @Nullable
    private static String byWear(Item item) {
        if (!(item instanceof Equipable equipable)) {
            return null;
        }
        EquipmentSlot slot = equipable.getEquipmentSlot();
        if (slot == null || slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
            return null;
        }
        return armours(item, slot) ? bySlot(slot) : null;
    }

    @Nullable
    private static String bySlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> null;
        };
    }

    private static boolean armours(Item item, EquipmentSlot slot) {
        return modifier(item, Attributes.ARMOR, slot) > 0.0D
                || modifier(item, Attributes.ARMOR_TOUGHNESS, slot) > 0.0D;
    }

    private static boolean hits(Item item) {
        return item instanceof TieredItem
                || modifier(item, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND) > 0.0D;
    }

    private static double modifier(Item item, Holder<Attribute> attribute, EquipmentSlot slot) {
        ItemAttributeModifiers modifiers =
                item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                        ItemAttributeModifiers.EMPTY);
        double total = 0.0D;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().equals(attribute) && entry.slot().test(slot)) {
                total += entry.modifier().amount();
            }
        }
        return total;
    }
}
