package com.elysium.space.item;

import com.elysium.space.world.JetRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * The Jet Suit (Stellaris's JetSuit): a space suit chestplate with thrusters.
 * It counts as the suit's chestplate (air, the Void seal), and carries its
 * own fuel for flight. Modes cycle with the Jet Mode key.
 */
public class JetSuitItem extends SpaceSuitItem {

    public static final String FUEL_TAG = "elysiumspace_jet_fuel";
    public static final String MODE_TAG = "elysiumspace_jet_mode";

    public JetSuitItem(Properties properties) {
        super(ArmorItem.Type.CHESTPLATE, properties);
    }

    @Override
    public int getElysiumTier() {
        return 2;
    }

    public static int fuel(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : Math.max(0, Math.min(JetRules.TANK, data.copyTag().getInt(FUEL_TAG)));
    }

    public static void setFuel(ItemStack stack, int fuel) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putInt(FUEL_TAG, Math.max(0, Math.min(JetRules.TANK, fuel))));
    }

    public static JetRules.Mode mode(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        int i = data == null ? 0 : data.copyTag().getInt(MODE_TAG);
        return i >= 0 && i < JetRules.Mode.values().length ? JetRules.Mode.values()[i] : JetRules.Mode.OFF;
    }

    public static void setMode(ItemStack stack, JetRules.Mode mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(MODE_TAG, mode.ordinal()));
    }

    /** The jet suit being worn, or empty. */
    public static ItemStack worn(LivingEntity entity) {
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        return chest.getItem() instanceof JetSuitItem ? chest : ItemStack.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("elysiumspace.jet.fuel", fuel(stack), JetRules.TANK));
        lines.add(Component.translatable("elysiumspace.jet.mode",
                Component.translatable("elysiumspace.jet.mode." + mode(stack).name().toLowerCase(java.util.Locale.ROOT))));
        super.appendHoverText(stack, context, lines, flag);
    }
}
