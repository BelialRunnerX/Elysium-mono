package com.elysium.space.item;

import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.element.ElysiumElements;
import com.elysium.lib.item.ElysiumSocketable;
import com.elysium.space.registry.SpaceArmor;
import com.elysium.space.world.SuitAir;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;

/**
 * A space suit piece (Stellaris's SpaceSuit). A Void-element Elysium gear
 * piece, so the full set seals vacuum and radiation through the library's
 * matching-set rule and takes runes, sockets and ascension like any Elysium
 * armour. The chestplate carries the air ({@link SuitAir}).
 */
public class SpaceSuitItem extends ArmorItem implements ElysiumSocketable {

    public static final String AIR_TAG = "elysiumspace_air";

    public SpaceSuitItem(ArmorItem.Type type, Properties properties) {
        super(SpaceArmor.SPACE_SUIT, type, properties.durability(type.getDurability(SpaceArmor.DURABILITY)));
    }

    @Override
    public ElysiumElement getElement() {
        return ElysiumElements.VOID;
    }

    @Override
    public int getElysiumTier() {
        return 1;
    }

    @Override
    public boolean isArmour() {
        return true;
    }

    @Override
    public float getBaseArmour() {
        return getDefense();
    }

    @Override
    public float getBaseToughness() {
        return getToughness();
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return elysiumModifiers(stack, super.getDefaultAttributeModifiers(stack),
                EquipmentSlotGroup.bySlot(getEquipmentSlot()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        if (getType() == Type.CHESTPLATE) {
            lines.add(Component.translatable("elysiumspace.suit.air", air(stack), SuitAir.CAPACITY));
        }
        describe(stack, lines);
    }

    // ------------------------------------------------------------------
    // Air, kept on the chestplate
    // ------------------------------------------------------------------

    public static int air(ItemStack chest) {
        CustomData data = chest.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : SuitAir.clamp(data.copyTag().getInt(AIR_TAG));
    }

    public static void setAir(ItemStack chest, int air) {
        CustomData.update(DataComponents.CUSTOM_DATA, chest,
                tag -> tag.putInt(AIR_TAG, SuitAir.clamp(air)));
    }

    /** All four pieces worn. */
    public static boolean fullSuit(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!(entity.getItemBySlot(slot).getItem() instanceof SpaceSuitItem)) {
                return false;
            }
        }
        return true;
    }
}
