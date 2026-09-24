package com.elysium.space.item;

import com.elysium.space.entity.RocketEntity;
import com.elysium.space.registry.SpaceBlocks;
import com.elysium.space.registry.SpaceEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Places a rocket on a Launch Pad, with whatever fuel it was picked up with. */
public class RocketItem extends Item {

    public RocketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pad = context.getClickedPos();
        if (!level.getBlockState(pad).is(SpaceBlocks.LAUNCH_PAD.get())) {
            if (context.getPlayer() != null && !level.isClientSide) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("elysiumspace.rocket.needs_pad"), true);
            }
            return InteractionResult.FAIL;
        }
        BlockPos above = pad.above();
        if (!level.getBlockState(above).isAir() || !level.getBlockState(above.above()).isAir()
                || !level.getBlockState(above.above(2)).isAir()) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            RocketEntity rocket = SpaceEntities.ROCKET.get().create(level);
            if (rocket == null) {
                return InteractionResult.FAIL;
            }
            rocket.moveTo(above.getX() + 0.5D, above.getY(), above.getZ() + 0.5D,
                    context.getRotation(), 0.0F);
            ItemStack stack = context.getItemInHand();
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data != null) {
                rocket.setTank(data.copyTag().getInt(RocketEntity.TANK_TAG));
                rocket.setMotor(RocketEntity.parseMotor(data.copyTag().getString(RocketEntity.MOTOR_TAG)));
                rocket.setFuel(data.copyTag().getInt(RocketEntity.FUEL_TAG));
            }
            level.addFreshEntity(rocket);
            stack.consume(1, context.getPlayer());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
