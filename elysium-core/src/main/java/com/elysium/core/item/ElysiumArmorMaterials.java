package com.elysium.core.item;

import com.elysium.core.Elysium;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/**
 * Armour materials for Elysium.
 *
 * In 1.21.1 {@code ArmorMaterial} is a registered record rather than an
 * interface you implement, so materials are registered like any other content
 * and referenced through a {@link Holder}. Durability is no longer part of the
 * material - it lives on the item properties (see
 * {@code Elysium.armourProperties}).
 *
 * Both materials are declared here on purpose: the class is touched explicitly
 * from the mod constructor, which guarantees this static initialiser runs
 * before the registry events fire.
 */
public final class ElysiumArmorMaterials {

    private ElysiumArmorMaterials() {
    }

    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(BuiltInRegistries.ARMOR_MATERIAL, Elysium.MODID);

    /** Multiplied by the per-slot base durability to get the final item durability. */
    public static final int DURABILITY_MULTIPLIER = 45;

    public static final int NEUTRONIUM_DURABILITY_MULTIPLIER = 55;

    /**
     * The core Elysium material: high protection, high enchantability, a little
     * knockback resistance.
     */
    public static final Holder<ArmorMaterial> ELYSIUM = ARMOR_MATERIALS.register("elysium",
            () -> new ArmorMaterial(
                    Map.of(
                            ArmorItem.Type.BOOTS, 5,
                            ArmorItem.Type.LEGGINGS, 8,
                            ArmorItem.Type.CHESTPLATE, 10,
                            ArmorItem.Type.HELMET, 5,
                            ArmorItem.Type.BODY, 8),
                    25,
                    SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.of(Elysium.NEUTRONIUM_INGOT.get()),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(Elysium.MODID, "elysium"))),
                    5.0F,
                    0.3F));

    /**
     * Neutronium: heavier and tougher than Elysium, but with no elemental
     * affinity of its own.
     */
    public static final Holder<ArmorMaterial> NEUTRONIUM = ARMOR_MATERIALS.register("neutronium",
            () -> new ArmorMaterial(
                    Map.of(
                            ArmorItem.Type.BOOTS, 6,
                            ArmorItem.Type.LEGGINGS, 9,
                            ArmorItem.Type.CHESTPLATE, 11,
                            ArmorItem.Type.HELMET, 6,
                            ArmorItem.Type.BODY, 9),
                    30,
                    SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.of(Elysium.NEUTRONIUM_INGOT.get()),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(Elysium.MODID, "neutronium"))),
                    6.0F,
                    0.4F));
}
