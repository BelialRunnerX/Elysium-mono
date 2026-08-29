package com.elysium.core.item;

import com.elysium.core.Elysium;
import com.elysium.lib.item.ElysiumGearMaterial;
import com.elysium.lib.item.ElysiumGearMaterial.ArmourProfile;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The gear every material gets: four tool shapes and four armour pieces.
 *
 * <h2>Why this is a loop and the old gear is not</h2>
 *
 * The hand-written items in {@link Elysium} are each one of a kind — the
 * Emperor's Crown has a drop rate, the elemental blades have their own
 * identities. This is the opposite: eight items that differ only in a material,
 * repeated across two dozen materials. Written out by hand that is two hundred
 * near-identical declarations, and the first typo in the two hundred is a
 * copper hammer with iron's element that nobody notices for a year.
 *
 * So the shapes are declared once and the materials iterate.
 *
 * <h2>Every material is registered, installed or not</h2>
 *
 * Including tin when no mod adds tin. See {@link ElysiumGearMaterial} for the
 * full reasoning; the short version is that the item registry must be the same
 * shape in every world, or removing one mod orphans another mod's saved stacks.
 * What varies at runtime is only whether the creative tab shows the item and
 * whether its recipe can resolve.
 */
public final class ElysiumMaterialGear {

    private ElysiumMaterialGear() {
    }

    /** The four tool shapes, and the suffix each one's registry name takes. */
    public enum Shape {
        HAMMER("hammer"),
        BROADAXE("broadaxe"),
        SCYTHE("scythe"),
        SPEAR("spear");

        private final String suffix;

        Shape(String suffix) {
            this.suffix = suffix;
        }

        public String getSuffix() {
            return suffix;
        }
    }

    /** The armour slot each piece fills, and the suffix its registry name takes. */
    private static final Map<ArmorItem.Type, String> ARMOUR_SUFFIX = new EnumMap<>(Map.of(
            ArmorItem.Type.HELMET, "helmet",
            ArmorItem.Type.CHESTPLATE, "chestplate",
            ArmorItem.Type.LEGGINGS, "leggings",
            ArmorItem.Type.BOOTS, "boots"));

    private static final Map<ElysiumGearMaterial, Map<Shape, DeferredHolder<Item, Item>>> TOOLS =
            new LinkedHashMap<>();
    private static final Map<ElysiumGearMaterial, Map<ArmorItem.Type, DeferredHolder<Item, Item>>>
            ARMOUR = new LinkedHashMap<>();

    /** Every item this class registered, in registration order. */
    private static final List<DeferredHolder<Item, Item>> ALL = new ArrayList<>();

    /** The materials that got gear here, in the order they were given it. */
    private static final List<ElysiumGearMaterial> COVERED = new ArrayList<>();

    /**
     * Registers gear for a list of materials.
     *
     * Called from the mod constructor, once for the vanilla five and once for
     * the modded table, so the creative tab groups them the way a player would
     * expect rather than interleaving them.
     */
    public static void registerAll(List<ElysiumGearMaterial> materials) {
        for (ElysiumGearMaterial material : materials) {
            register(material);
        }
    }

    private static void register(ElysiumGearMaterial material) {
        String path = material.getPath();
        COVERED.add(material);

        Map<Shape, DeferredHolder<Item, Item>> tools = new EnumMap<>(Shape.class);
        for (Shape shape : Shape.values()) {
            String name = path + "_" + shape.getSuffix();
            DeferredHolder<Item, Item> holder = Elysium.ITEMS.register(name,
                    () -> switch (shape) {
                        case HAMMER -> new ElysiumTools.Hammer(material);
                        case BROADAXE -> new ElysiumTools.Broadaxe(material);
                        case SCYTHE -> new ElysiumTools.Scythe(material);
                        case SPEAR -> new ElysiumTools.Spear(material);
                    });
            tools.put(shape, holder);
            ALL.add(holder);
        }
        TOOLS.put(material, tools);

        ArmourProfile profile = material.getArmour();
        if (profile == null) {
            return;
        }

        // One vanilla ArmorMaterial per Elysium material. Registered here
        // rather than in ElysiumArmorMaterials because that class is a list of
        // hand-written constants and this is generated — but into the same
        // DeferredRegister, so they all arrive on the same event.
        Holder<ArmorMaterial> armorMaterial =
                ElysiumArmorMaterials.ARMOR_MATERIALS.register(path,
                        () -> new ArmorMaterial(
                                Map.of(
                                        ArmorItem.Type.BOOTS, profile.boots(),
                                        ArmorItem.Type.LEGGINGS, profile.leggings(),
                                        ArmorItem.Type.CHESTPLATE, profile.chestplate(),
                                        ArmorItem.Type.HELMET, profile.helmet(),
                                        ArmorItem.Type.BODY, profile.leggings()),
                                profile.enchantmentValue(),
                                SoundEvents.ARMOR_EQUIP_NETHERITE,
                                // The ingredient is the material's own tag, so
                                // repairing works for a modded metal exactly
                                // when crafting it does.
                                () -> Ingredient.of(material.getIngredientTag()),
                                List.of(new ArmorMaterial.Layer(
                                        ResourceLocation.fromNamespaceAndPath(
                                                Elysium.MODID, path))),
                                profile.toughness(),
                                profile.knockbackResistance()));

        Map<ArmorItem.Type, DeferredHolder<Item, Item>> pieces =
                new EnumMap<>(ArmorItem.Type.class);
        for (Map.Entry<ArmorItem.Type, String> entry : ARMOUR_SUFFIX.entrySet()) {
            ArmorItem.Type type = entry.getKey();
            String name = path + "_" + entry.getValue();
            DeferredHolder<Item, Item> holder = Elysium.ITEMS.register(name,
                    () -> new ElysiumArmorItem(
                            armorMaterial, type,
                            Elysium.materialArmourProperties(type, profile),
                            material.getElement(), material.getTier()));
            pieces.put(type, holder);
            ALL.add(holder);
        }
        ARMOUR.put(material, pieces);
    }

    // ------------------------------------------------------------------

    /** @return the tool, or null when this material has no gear registered */
    public static DeferredHolder<Item, Item> tool(ElysiumGearMaterial material, Shape shape) {
        Map<Shape, DeferredHolder<Item, Item>> tools = TOOLS.get(material);
        return tools == null ? null : tools.get(shape);
    }

    public static DeferredHolder<Item, Item> armour(ElysiumGearMaterial material,
                                                    ArmorItem.Type type) {
        Map<ArmorItem.Type, DeferredHolder<Item, Item>> pieces = ARMOUR.get(material);
        return pieces == null ? null : pieces.get(type);
    }

    /** Every item registered here, whether or not its material is available. */
    public static List<DeferredHolder<Item, Item>> all() {
        return ALL;
    }

    /** The materials this class gave gear to. */
    public static List<ElysiumGearMaterial> covered() {
        return COVERED;
    }

    /**
     * The subset a player can actually make.
     *
     * The creative tab is built from this rather than from {@link #all()}, so a
     * pack without a tin mod does not show a Tin Hammer nobody can craft. The
     * item still exists — it is simply not offered.
     */
    public static List<DeferredHolder<Item, Item>> available() {
        List<DeferredHolder<Item, Item>> found = new ArrayList<>();
        for (ElysiumGearMaterial material : COVERED) {
            if (!material.isAvailable()) {
                continue;
            }
            Map<Shape, DeferredHolder<Item, Item>> tools = TOOLS.get(material);
            if (tools != null) {
                found.addAll(tools.values());
            }
            Map<ArmorItem.Type, DeferredHolder<Item, Item>> pieces = ARMOUR.get(material);
            if (pieces != null) {
                found.addAll(pieces.values());
            }
        }
        return found;
    }
}
