package com.elysium.agronomy.client;

import com.elysium.agronomy.entity.BeeEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import com.elysium.agronomy.ElysiumAgronomy;

/**
 * One shape for every bee.
 *
 * <p>Seventy-five species and one model: they differ in colour and in what
 * they make, not in how many wings they have. The texture carries the species,
 * which is the cheapest thing in the game to vary and the only thing here that
 * needs to.
 */
public class BeeModel extends EntityModel<BeeEntity> {

    /** Where the game keeps the baked shape. */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ElysiumAgronomy.MODID, "bee"), "main");

    private final ModelPart body;
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public BeeModel(ModelPart root) {
        this.body = root.getChild("body");
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
    }

    /**
     * The shape, laid out to fit a 32 by 32 texture.
     *
     * <p>A box needs {@code 2*(width+depth)} pixels across and
     * {@code height+depth} down. The body is six by five by six, so twenty-four
     * by eleven; the wings are five by one by four, so eighteen by five. Both
     * fit, and the generator draws into exactly those rectangles — which is
     * why the numbers are written here rather than guessed at twice.
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3.0F, -2.5F, -3.0F, 6.0F, 5.0F, 6.0F,
                                CubeDeformation.NONE),
                PartPose.offset(0.0F, 19.0F, 0.0F));
        root.addOrReplaceChild("left_wing",
                CubeListBuilder.create().texOffs(0, 12)
                        .addBox(0.0F, 0.0F, -2.0F, 5.0F, 1.0F, 4.0F,
                                CubeDeformation.NONE),
                PartPose.offset(1.5F, 16.5F, 0.0F));
        root.addOrReplaceChild("right_wing",
                CubeListBuilder.create().texOffs(0, 18)
                        .addBox(-5.0F, 0.0F, -2.0F, 5.0F, 1.0F, 4.0F,
                                CubeDeformation.NONE),
                PartPose.offset(-1.5F, 16.5F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(BeeEntity bee, float limbSwing, float limbSwingAmount,
                          float age, float headYaw, float headPitch) {
        // Wings beat off the entity's own age, so two bees side by side are
        // not in lockstep, and they beat whether or not the bee is moving —
        // which is what a hovering insect does.
        float beat = age * 2.1F + (bee.getId() % 7);
        float lift = (float) Math.sin(beat) * 0.6F;
        this.leftWing.zRot = lift;
        this.rightWing.zRot = -lift;
        this.body.xRot = headPitch * 0.008F;
        this.body.yRot = headYaw * 0.008F;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                               int overlay, int colour) {
        this.body.render(pose, buffer, light, overlay, colour);
        this.leftWing.render(pose, buffer, light, overlay, colour);
        this.rightWing.render(pose, buffer, light, overlay, colour);
    }
}
