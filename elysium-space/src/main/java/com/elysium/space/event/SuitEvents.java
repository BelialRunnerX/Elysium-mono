package com.elysium.space.event;

import com.elysium.lib.hazard.ElysiumHazardField;
import com.elysium.space.ElysiumSpace;
import com.elysium.space.hazard.SpaceHazards;
import com.elysium.space.item.SpaceSuitItem;
import com.elysium.space.oxygen.OxygenField;
import com.elysium.space.world.SuitAir;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The suit's air, once a second (Stellaris's LivingEntityMixin oxygen check).
 *
 * <p>The library already treats a full suit as sealed against vacuum, so it
 * deals no vacuum damage to its wearer. This handler supplies what that seal
 * costs: it drains the chestplate while the wearer is out in vacuum, refills
 * it in a filled room, and when the tank is empty the wearer suffocates.
 */
@EventBusSubscriber(modid = ElysiumSpace.MODID)
public final class SuitEvents {

    private SuitEvents() {
    }

    public static final ResourceKey<DamageType> ASPHYXIATION = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, "asphyxiation"));

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % 20 != 0 || player.isCreative() || player.isSpectator()
                || !SpaceSuitItem.fullSuit(player)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        // Vacuum through the library, so a filled room reads as no vacuum here too.
        boolean inRoom = OxygenField.isOxygenated(player.level(), pos);
        boolean exposed = ElysiumHazardField.intensity(player.level(), pos, SpaceHazards.VACUUM) > 0.0F;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        int before = SpaceSuitItem.air(chest);
        int after = SuitAir.step(before, exposed, inRoom);
        if (after != before) {
            SpaceSuitItem.setAir(chest, after);
        }
        if (SuitAir.suffocating(after, exposed, inRoom)) {
            DamageSource source = new DamageSource(player.level().registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ASPHYXIATION));
            player.hurt(source, SuitAir.SUFFOCATION_DAMAGE);
        }
    }
}
