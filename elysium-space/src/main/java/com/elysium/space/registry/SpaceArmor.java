package com.elysium.space.registry;

import com.elysium.space.ElysiumSpace;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

/**
 * The space suit's material. Light armour (between leather and iron): the
 * suit is for breathing, not for fighting. Worn texture:
 * {@code textures/models/armor/space_suit_layer_1/2.png}.
 */
public final class SpaceArmor {

    private SpaceArmor() {
    }

    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, ElysiumSpace.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SPACE_SUIT = MATERIALS.register("space_suit",
            () -> {
                EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
                defense.put(ArmorItem.Type.HELMET, 2);
                defense.put(ArmorItem.Type.CHESTPLATE, 5);
                defense.put(ArmorItem.Type.LEGGINGS, 4);
                defense.put(ArmorItem.Type.BOOTS, 2);
                defense.put(ArmorItem.Type.BODY, 5);
                return new ArmorMaterial(defense, 12, SoundEvents.ARMOR_EQUIP_IRON,
                        () -> Ingredient.of(Items.IRON_INGOT),
                        List.of(new ArmorMaterial.Layer(
                                ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, "space_suit"))),
                        0.0F, 0.0F);
            });

    /** Iron's durability multiplier. */
    public static final int DURABILITY = 15;
}
