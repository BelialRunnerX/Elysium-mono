package com.elysium.transit.client;

import com.elysium.transit.ElysiumTransit;
import com.elysium.transit.entity.VehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/** One model, three textures, three sizes. */
public class VehicleRenderer extends EntityRenderer<VehicleEntity> {

    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    private final VehicleModel model;

    public VehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new VehicleModel(context.bakeLayer(VehicleModel.LAYER));
        this.shadowRadius = 0.7F;
    }

    @Override
    public void render(VehicleEntity vehicle, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        // The rungs differ in size as well as in numbers, so a hauler reads as
        // a hauler across a field without anybody having to prod it.
        float scale = 0.8F + 0.15F * vehicle.rung().tier();
        pose.scale(-scale, -scale, scale);
        pose.translate(0.0F, -1.501F, 0.0F);
        model.setupAnim(vehicle, 0.0F, 0.0F, vehicle.tickCount + partialTick, 0.0F, 0.0F);
        model.renderToBuffer(pose, buffers.getBuffer(
                        model.renderType(getTextureLocation(vehicle))), light,
                OverlayTexture.NO_OVERLAY, -1);
        pose.popPose();
        super.render(vehicle, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(VehicleEntity vehicle) {
        String name = EntityType.getKey(vehicle.getType()).getPath();
        return SKINS.computeIfAbsent(name, key -> ResourceLocation.fromNamespaceAndPath(
                ElysiumTransit.MODID, "textures/entity/" + key + ".png"));
    }
}
