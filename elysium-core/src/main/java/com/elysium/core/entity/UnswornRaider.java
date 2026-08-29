package com.elysium.core.entity;

import com.elysium.core.Elysium;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An Unsworn Raider — one of the Empire's enemies, and therefore, if you are
 * carrying Imperial Favor, one of your targets.
 *
 * Built on {@link Husk} for the same reason the enforcer is built on
 * {@link Zombie}: it inherits a vanilla renderer that already draws worn
 * armour, so it is visually distinct from an enforcer without the mod shipping
 * a model. It also does not burn in daylight, which matters for something the
 * Empire dispatches you against at any hour.
 *
 * Every raider spawns carrying one of the five elemental weapons at random.
 * That is deliberate: it means the counter matrix comes up in ordinary fights
 * rather than only when a player deliberately tests it, and it makes the armour
 * set you chose matter against a mob you did not choose.
 */
public class UnswornRaider extends Husk {

    public UnswornRaider(EntityType<? extends Husk> type, Level level) {
        super(type, level);
    }

    /**
     * Lighter and faster than an enforcer. The Empire's soldiers are armoured;
     * its enemies are quick.
     */
    public static AttributeSupplier.Builder createRaiderAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 22.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    /**
     * @param band how much Favor the player has, 0-3 — higher bands draw out
     *             better-armed raiders, so the loop stays worth riding
     */
    public void equipForBand(int band, RandomSource random) {
        setPersistenceRequired();

        Item[] arms = {
                Elysium.VOIDCUT_BLADE.get(),
                Elysium.PLASMA_BRAND.get(),
                Elysium.NEURAL_LASH.get(),
                Elysium.RIFT_EDGE.get(),
                Elysium.KINETIC_MAUL.get(),
        };
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(arms[random.nextInt(arms.length)]));
        setDropChance(EquipmentSlot.MAINHAND, 0.05F);

        if (band >= 2) {
            setItemSlot(EquipmentSlot.HEAD, new ItemStack(Elysium.ELYSIUM_HELMET.get()));
            setDropChance(EquipmentSlot.HEAD, 0.03F);
        }
        if (band >= 3) {
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(Elysium.PLASMA_CHESTPLATE.get()));
            setDropChance(EquipmentSlot.CHEST, 0.03F);
        }
    }
}
