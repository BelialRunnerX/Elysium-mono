package com.elysium.core.compat;

import com.elysium.core.item.ElysiumMaterialGear;
import com.elysium.core.item.ElysiumTools;
import com.elysium.lib.item.ElysiumGear;
import com.elysium.lib.item.ElysiumGearData;
import com.elysium.lib.item.ElysiumGearMaterial;
import com.elysium.lib.item.ElysiumSocketable;
import com.elysium.lib.item.ElysiumSockets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.gear.material.MaterialInstance;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.item.CompoundPartItem;
import net.silentchaos512.gear.util.GearData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The same piece of gear, as an Elysium item or as a Silent Gear one.
 *
 * <h2>Why this direction is a swap and the Apotheosis one is not</h2>
 *
 * Apotheosis writes data onto whatever item it is given; Silent Gear gear
 * <em>is</em> a different item, built out of parts. So there is no way to hold
 * both identities on one stack, and the conversion has to hand back a different
 * thing. Which makes losslessness the whole problem: a player converting a
 * blade they spent a week on must get that blade back.
 *
 * <h2>How the round trip stays exact</h2>
 *
 * Three things travel:
 *
 * <ul>
 *   <li>the <b>material</b>, which both mods already agree on — Elysium ships
 *       Neutronium and Aetherium as Silent Gear materials under
 *       {@code data/elysium/silentgear_materials/}, so the two systems are
 *       naming the same substance rather than translating between two
 *       tables;</li>
 *   <li>the <b>Elysium gear data</b> — runes, reforge rolls, ascension — which
 *       is a data component and rides along on the Silent Gear item untouched,
 *       invisible to Silent Gear and waiting for the trip home;</li>
 *   <li>the <b>original item id</b>, because Elysium has shapes Silent Gear does
 *       not. See {@link ConvergenceComponents}.</li>
 * </ul>
 *
 * <h2>Why items are looked up by id rather than through Silent Gear's fields</h2>
 *
 * {@code BuiltInRegistries.ITEM.get(rl("silentgear", "helmet"))} rather than
 * their registry object class. The ids are the stable part of a mod — they are
 * what data packs, recipes and every other mod's compatibility is written
 * against — while the Java field that holds them is free to move between
 * versions. Looking up by id means a Silent Gear update that reorganises its
 * setup classes does not break this file, and a missing item produces a refused
 * conversion rather than a crash on class load.
 */
final class SilentGearBridge {

    private SilentGearBridge() {
    }

    private static final String SG = "silentgear";

    /**
     * Elysium armour slot to the Silent Gear item and the part that makes it.
     *
     * The part item id is not derivable from the gear item id — it is
     * {@code helmet_plates} but {@code legging_plates} and {@code boot_plates},
     * singular and plural in the same table — so it is written out rather than
     * built from a string.
     */
    private static final Map<ArmorItem.Type, String[]> ARMOUR =
            new EnumMap<>(Map.of(
                    ArmorItem.Type.HELMET, new String[] {"helmet", "helmet_plates"},
                    ArmorItem.Type.CHESTPLATE, new String[] {"chestplate", "chestplate_plates"},
                    ArmorItem.Type.LEGGINGS, new String[] {"leggings", "legging_plates"},
                    ArmorItem.Type.BOOTS, new String[] {"boots", "boot_plates"}));

    /**
     * Elysium tool shape to the Silent Gear item and its main part.
     *
     * Broadaxe has no Silent Gear counterpart and maps to an axe; Scythe maps to
     * a sickle. Those two are the reason {@link ConvergenceComponents#ORIGIN}
     * exists — without it a broadaxe would come home an axe.
     */
    private static final Map<ElysiumMaterialGear.Shape, String[]> TOOLS =
            new EnumMap<>(Map.of(
                    ElysiumMaterialGear.Shape.SWORD, new String[] {"sword", "sword_blade"},
                    ElysiumMaterialGear.Shape.PICKAXE, new String[] {"pickaxe", "pickaxe_head"},
                    ElysiumMaterialGear.Shape.AXE, new String[] {"axe", "axe_head"},
                    ElysiumMaterialGear.Shape.SHOVEL, new String[] {"shovel", "shovel_head"},
                    ElysiumMaterialGear.Shape.HOE, new String[] {"hoe", "hoe_head"},
                    ElysiumMaterialGear.Shape.HAMMER, new String[] {"hammer", "hammer_head"},
                    ElysiumMaterialGear.Shape.BROADAXE, new String[] {"axe", "axe_head"},
                    ElysiumMaterialGear.Shape.SCYTHE, new String[] {"sickle", "sickle_blade"},
                    ElysiumMaterialGear.Shape.SPEAR, new String[] {"spear", "spear_tip"}));

    private static ResourceLocation sg(String path) {
        return ResourceLocation.fromNamespaceAndPath(SG, path);
    }

    private static Item item(String path) {
        Item found = BuiltInRegistries.ITEM.get(sg(path));
        return found == Items.AIR ? null : found;
    }

    static boolean isSilentGear(ItemStack stack) {
        return stack.getItem() instanceof GearItem;
    }

    /**
     * The Silent Gear material that means the same substance as this one.
     *
     * Elysium's own namespace first, because that is where Elysium's materials
     * are defined; then Silent Gear's, which covers the vanilla metals both
     * mods know. A material with no counterpart returns null and the conversion
     * is refused — inventing one would produce gear whose stats came from
     * nowhere.
     */
    private static DataResource<net.silentchaos512.gear.api.material.Material>
            materialFor(ElysiumGearMaterial material) {
        for (String namespace : new String[] {"elysium", SG}) {
            DataResource<net.silentchaos512.gear.api.material.Material> candidate =
                    DataResource.material(ResourceLocation.fromNamespaceAndPath(
                            namespace, material.getPath()));
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return null;
    }

    /** The reverse: a Silent Gear material id back to an Elysium material. */
    private static ElysiumGearMaterial elysiumMaterialFor(ResourceLocation id) {
        for (ElysiumGearMaterial material : ElysiumGearMaterial.REGISTRY.all()) {
            if (material.getPath().equals(id.getPath())) {
                return material;
            }
        }
        return null;
    }

    /**
     * Which Elysium material and shape an item is, by asking the registry.
     *
     * A reverse lookup over every registered material rather than a cast: an
     * Elysium armour item does not carry its material as a field this package
     * can reach, and comparing identities against what
     * {@link ElysiumMaterialGear} registered is exact where a guess from the
     * registry name would not be.
     */
    private static Object[] identify(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ElysiumTools.MaterialTool tool) {
            for (Map.Entry<ElysiumMaterialGear.Shape, String[]> entry : TOOLS.entrySet()) {
                var holder = ElysiumMaterialGear.tool(tool.material(), entry.getKey());
                if (holder != null && holder.get() == item) {
                    return new Object[] {tool.material(), entry.getKey(), null};
                }
            }
            return null;
        }
        if (item instanceof ArmorItem armour) {
            for (ElysiumGearMaterial material : ElysiumGearMaterial.REGISTRY.all()) {
                var holder = ElysiumMaterialGear.armour(material, armour.getType());
                if (holder != null && holder.get() == item) {
                    return new Object[] {material, null, armour.getType()};
                }
            }
        }
        return null;
    }

    static ItemStack toSilentGear(ItemStack stack) {
        Object[] identity = identify(stack);
        if (identity == null) {
            return ItemStack.EMPTY;
        }
        ElysiumGearMaterial material = (ElysiumGearMaterial) identity[0];
        ElysiumMaterialGear.Shape shape = (ElysiumMaterialGear.Shape) identity[1];
        ArmorItem.Type slot = (ArmorItem.Type) identity[2];

        String[] names = shape != null ? TOOLS.get(shape) : ARMOUR.get(slot);
        if (names == null) {
            return ItemStack.EMPTY;
        }
        DataResource<net.silentchaos512.gear.api.material.Material> silentMaterial =
                materialFor(material);
        if (silentMaterial == null) {
            return ItemStack.EMPTY;
        }
        if (!(item(names[0]) instanceof GearItem gear)
                || !(item(names[1]) instanceof CompoundPartItem partItem)) {
            return ItemStack.EMPTY;
        }

        List<PartInstance> parts = new ArrayList<>();
        PartInstance main = PartInstance.from(
                partItem.create(MaterialInstance.of(silentMaterial)));
        if (main == null || !main.isValid()) {
            return ItemStack.EMPTY;
        }
        parts.add(main);

        // Tools need something to hold. Armour does not, and adding a rod to a
        // helmet is how you get a piece Silent Gear will not render.
        if (shape != null && item("rod") instanceof CompoundPartItem rodItem) {
            PartInstance rod = PartInstance.from(
                    rodItem.create(MaterialInstance.of(silentMaterial)));
            if (rod != null && rod.isValid()) {
                parts.add(rod);
            }
        }

        ItemStack built = gear.construct(parts);
        if (built.isEmpty()) {
            return ItemStack.EMPTY;
        }
        carryAcross(stack, built);
        built.set(ConvergenceComponents.ORIGIN.get(),
                BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return built;
    }

    static ItemStack toElysium(ItemStack stack) {
        ItemStack rebuilt = fromOrigin(stack);
        if (rebuilt.isEmpty()) {
            rebuilt = fromMaterial(stack);
        }
        if (rebuilt.isEmpty()) {
            return ItemStack.EMPTY;
        }
        carryAcross(stack, rebuilt);
        rebuilt.remove(ConvergenceComponents.ORIGIN.get());
        return rebuilt;
    }

    /** The exact item this piece was, if it remembers and still exists. */
    private static ItemStack fromOrigin(ItemStack stack) {
        ResourceLocation origin = stack.get(ConvergenceComponents.ORIGIN.get());
        if (origin == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(origin);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;      // removed by a pack since conversion
        }
        ItemStack candidate = new ItemStack(item);
        return ElysiumGear.isNative(candidate) ? candidate : ItemStack.EMPTY;
    }

    /**
     * A Silent Gear item that was never an Elysium one — looted, or built at
     * their own bench. Its main material decides what it becomes.
     */
    private static ItemStack fromMaterial(ItemStack stack) {
        PartInstance main = GearData.getCoatingOrMainPart(stack);
        if (main == null || !main.isValid()) {
            return ItemStack.EMPTY;
        }
        MaterialInstance primary = main.getPrimaryMaterial();
        if (primary == null) {
            return ItemStack.EMPTY;
        }
        ElysiumGearMaterial material = elysiumMaterialFor(primary.getId());
        if (material == null) {
            return ItemStack.EMPTY;
        }
        ResourceLocation gearId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (Map.Entry<ArmorItem.Type, String[]> entry : ARMOUR.entrySet()) {
            if (entry.getValue()[0].equals(gearId.getPath())) {
                var holder = ElysiumMaterialGear.armour(material, entry.getKey());
                return holder == null ? ItemStack.EMPTY : new ItemStack(holder.get());
            }
        }
        for (Map.Entry<ElysiumMaterialGear.Shape, String[]> entry : TOOLS.entrySet()) {
            // Skip the shapes that share a Silent Gear item: an axe coming the
            // other way is an Axe, never a Broadaxe, unless ORIGIN said so.
            if (entry.getValue()[0].equals(gearId.getPath())
                    && entry.getKey() != ElysiumMaterialGear.Shape.BROADAXE) {
                var holder = ElysiumMaterialGear.tool(material, entry.getKey());
                return holder == null ? ItemStack.EMPTY : new ItemStack(holder.get());
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Everything that belongs to the player rather than to either mod.
     *
     * The Elysium gear data rides across in both directions, which is what makes
     * the round trip lossless — Silent Gear never looks at that component, so a
     * piece can be a Silent Gear sword for a week and come back with its runes.
     * Damage travels as a fraction because the two items rarely have the same
     * maximum, and a blade converted at half health should come back at half
     * health rather than at half of somebody else's number.
     */
    private static void carryAcross(ItemStack from, ItemStack to) {
        ElysiumGearData data = ElysiumSockets.gearData(from);
        if (!data.equals(ElysiumGearData.EMPTY)) {
            ElysiumSockets.setGearData(to, data);
        }
        if (from.isDamageableItem() && to.isDamageableItem()
                && from.getMaxDamage() > 0 && to.getMaxDamage() > 0) {
            double worn = (double) from.getDamageValue() / from.getMaxDamage();
            to.setDamageValue((int) Math.round(worn * to.getMaxDamage()));
        }
        if (from.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            to.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    from.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        }
        if (from.has(net.minecraft.core.component.DataComponents.ENCHANTMENTS)) {
            to.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                    from.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS));
        }
    }
}
