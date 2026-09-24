package com.elysium.agronomy.client;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.entity.FishEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * One shape for every fish; the texture and the scale carry the species.
 *
 * <p>The scale comes from the length in the rules, which is the same number
 * the hitbox is built from, so a fish that looks like a metre of fish is a
 * metre of fish. Nothing here chooses a size.
 */
public class FishModel extends EntityModel<FishEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ElysiumAgronomy.MODID, "fish"), "main");

    private final ModelPart body;
    private final ModelPart tail;

    public FishModel(ModelPart root) {
        this.body = root.getChild("body");
        this.tail = root.getChild("tail");
    }

    /**
     * The shape, laid out to fit a 32 by 32 texture.
     *
     * <p>The body is eight by four by three, so twenty-two across and seven
     * down at (0, 0); the tail is four by four by one, so ten across and five
     * down at (0, 8). The generator draws into exactly those rectangles.
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -2.0F, -1.5F, 8.0F, 4.0F, 3.0F,
                                CubeDeformation.NONE),
                PartPose.offset(0.0F, 22.0F, 0.0F));
        root.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(0, 8)
                        .addBox(0.0F, -2.0F, -0.5F, 4.0F, 4.0F, 1.0F,
                                CubeDeformation.NONE),
                PartPose.offset(4.0F, 22.0F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(FishEntity fish, float limbSwing, float limbSwingAmount,
                          float age, float headYaw, float headPitch) {
        float sweep = fish.isInWaterOrBubble() ? 1.0F : 1.8F;
        this.tail.yRot = -sweep * 0.45F * (float) Math.cos(age * 0.6F);
        this.body.yRot = -sweep * 0.08F * (float) Math.cos(age * 0.6F);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                               int overlay, int colour) {
        this.body.render(pose, buffer, light, overlay, colour);
        this.tail.render(pose, buffer, light, overlay, colour);
    }
}
