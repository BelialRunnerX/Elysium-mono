package com.elysium.core.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

/**
 * Neutronium armour material.
 *
 * The material itself is declared in {@link ElysiumArmorMaterials} so that a
 * single class initialiser covers every material - that class is touched
 * explicitly from the mod constructor, which is what guarantees the materials
 * are queued before the registry events fire. This class stays as the named
 * entry point for the neutronium set.
 */
public final class NeutroniumArmorMaterials {

    private NeutroniumArmorMaterials() {
    }

    public static final Holder<ArmorMaterial> NEUTRONIUM = ElysiumArmorMaterials.NEUTRONIUM;

    public static final int DURABILITY_MULTIPLIER = ElysiumArmorMaterials.NEUTRONIUM_DURABILITY_MULTIPLIER;
}
