package com.elysium.armory.item;

import com.elysium.armory.registry.ArmoryArmorMaterial;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Modular wings: an armour piece worn on the chest that a player can glide
 * with, as long as it is not broken.
 */
public class ArmoryElytraItem extends ArmorItem implements ArmoryGear {

    public ArmoryElytraItem(Properties properties) {
        super(ArmoryArmorMaterial.holder(), Type.CHESTPLATE, properties.stacksTo(1));
    }

    @Override
    public String gearType() {
        return "elytra";
    }

    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return stack.getDamageValue() < stack.getMaxDamage() - 1;
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        if (!entity.level().isClientSide() && (flightTicks + 1) % 20 == 0) {
            stack.hurtAndBreak(1, entity, net.minecraft.world.entity.EquipmentSlot.CHEST);
        }
        return true;
    }

    @Override
    public int getDefense() {
        return 0;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        ArmoryGearLogic.tick(this, stack, level);
    }

    @Override
    public <T extends net.minecraft.world.entity.LivingEntity> int damageItem(ItemStack stack, int amount,
                                                                              T entity,
                                                                              java.util.function.Consumer<net.minecraft.world.item.Item> onBroken) {
        return ArmoryGearLogic.damage(stack, amount, entity);
    }
}
