package com.elysium.armory.registry;

import com.elysium.armory.ElysiumArmory;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/**
 * One armour material for every piece of modular armour.
 *
 * <p>It grants nothing: the defence, toughness and knockback resistance a piece
 * has are computed from its parts and written onto the stack as attribute
 * modifiers, the same way a tool's damage is. What the material is for is the
 * two things vanilla will not take from a component — the equip sound, and the
 * worn texture. The layer is <b>dyeable</b>, so the armour a player is wearing
 * is tinted by the material it was made of, from the same colour the sprite
 * uses.
 */
public final class ArmoryArmorMaterial {

    private ArmoryArmorMaterial() {
    }

    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(BuiltInRegistries.ARMOR_MATERIAL, ElysiumArmory.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MODULAR =
            MATERIALS.register("modular", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.HELMET, 0, ArmorItem.Type.CHESTPLATE, 0,
                            ArmorItem.Type.LEGGINGS, 0, ArmorItem.Type.BOOTS, 0, ArmorItem.Type.BODY, 0),
                    0,
                    SoundEvents.ARMOR_EQUIP_IRON,
                    () -> Ingredient.EMPTY,
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(ElysiumArmory.MODID, "modular"), "", true)),
                    0.0F, 0.0F));

    public static Holder<ArmorMaterial> holder() {
        return MODULAR;
    }

    public static void register(IEventBus modBus) {
        MATERIALS.register(modBus);
    }
}
