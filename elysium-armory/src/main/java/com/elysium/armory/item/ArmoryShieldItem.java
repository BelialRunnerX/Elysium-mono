package com.elysium.armory.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;

/** A modular shield: raised like any other, but as tough as what it was made of. */
public class ArmoryShieldItem extends ShieldItem implements ArmoryGear {

    public ArmoryShieldItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public String gearType() {
        return "shield";
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        ArmoryGearLogic.tick(this, stack, level);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return false;                   // repaired with its own materials, at the bench
    }

    @Override
    public <T extends net.minecraft.world.entity.LivingEntity> int damageItem(ItemStack stack, int amount,
                                                                              T entity,
                                                                              java.util.function.Consumer<net.minecraft.world.item.Item> onBroken) {
        return ArmoryGearLogic.damage(stack, amount, entity);
    }
}
