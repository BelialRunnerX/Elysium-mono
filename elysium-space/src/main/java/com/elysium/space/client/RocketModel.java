package com.elysium.space.client;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.entity.RocketEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * A placeholder tier-one rocket: hull, nose, tip, engine bell and four fins.
 * Our own shape rather than Stellaris's; the S8 reskin replaces it.
 */
public class RocketModel extends EntityModel<RocketEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, "rocket"), "main");

    private final ModelPart root;

    public RocketModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition rocket = root.addOrReplaceChild("rocket", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        rocket.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-6.0F, -40.0F, -6.0F, 12.0F, 36.0F, 12.0F), PartPose.ZERO);
        rocket.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(48, 0)
                .addBox(-4.0F, -50.0F, -4.0F, 8.0F, 10.0F, 8.0F), PartPose.ZERO);
        rocket.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(80, 0)
                .addBox(-2.0F, -54.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.ZERO);
        rocket.addOrReplaceChild("engine", CubeListBuilder.create().texOffs(48, 20)
                .addBox(-5.0F, -4.0F, -5.0F, 10.0F, 4.0F, 10.0F), PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            rocket.addOrReplaceChild("fin_" + i, CubeListBuilder.create().texOffs(88, 20)
                    .addBox(-0.5F, -16.0F, 6.0F, 1.0F, 14.0F, 6.0F),
                    PartPose.rotation(0.0F, (float) (i * Math.PI / 2.0D), 0.0F));
        }
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(RocketEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int colour) {
        root.render(pose, buffer, light, overlay, colour);
    }
}
