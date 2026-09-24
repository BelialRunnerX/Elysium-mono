package com.elysium.wilds.client;

import com.elysium.wilds.ElysiumWilds;
import com.elysium.wilds.registry.WildComponents;
import com.elysium.wilds.registry.WildItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The needle.
 *
 * <p>A compass that opens a list and then does nothing visible is a menu, not
 * a compass. The needle is the whole difference: once a place is found, the
 * item points at it, and a player walks rather than reads coordinates off a
 * chat line they have already scrolled past.
 */
@EventBusSubscriber(modid = ElysiumWilds.MODID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class WildsClient {

    private WildsClient() {
    }

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(WildItems.COMPASS.get(),
                ResourceLocation.fromNamespaceAndPath(ElysiumWilds.MODID, "angle"),
                (stack, level, holder, seed) -> {
                    if (holder == null) {
                        return 0.0F;
                    }
                    WildComponents.Bearing bearing =
                            stack.get(WildComponents.BEARING.get());
                    if (bearing == null || level == null
                            || !bearing.where().dimension().equals(level.dimension())) {
                        // Spinning, rather than pointing somewhere wrong. A
                        // needle that lies is worse than one that says nothing.
                        return wobble(holder, seed);
                    }
                    return (float) pointing(holder, bearing.where());
                }));
    }

    /** Where the needle should sit, as a fraction of a turn. */
    private static double pointing(Entity holder, GlobalPos there) {
        Vec3 here = holder.position();
        double wanted = Math.atan2(there.pos().getZ() + 0.5D - here.z,
                there.pos().getX() + 0.5D - here.x);
        double facing = Math.toRadians(holder.getVisualRotationYInDegrees());
        double turn = 0.5D - (facing / (Math.PI * 2.0D) - 0.25D + wanted / (Math.PI * 2.0D));
        return turn - Math.floor(turn);
    }

    private static float wobble(Entity holder, int seed) {
        double spin = (holder.tickCount + seed) * 0.017D;
        double turn = spin - Math.floor(spin);
        return (float) turn;
    }
}
