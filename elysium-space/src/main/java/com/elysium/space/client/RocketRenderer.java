package com.elysium.space.client;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.entity.RocketEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class RocketRenderer extends EntityRenderer<RocketEntity> {

    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, "textures/entity/rocket.png");

    private final RocketModel model;

    public RocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new RocketModel(context.bakeLayer(RocketModel.LAYER));
        this.shadowRadius = 0.6F;
    }

    @Override
    public void render(RocketEntity rocket, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);
        model.renderToBuffer(pose, buffers.getBuffer(model.renderType(TEXTURE)), light,
                OverlayTexture.NO_OVERLAY, -1);
        pose.popPose();
        super.render(rocket, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(RocketEntity rocket) {
        return TEXTURE;
    }
}
