package com.elysium.comforts.client;

import com.elysium.comforts.entity.PigeonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A bird: a body, a head, two wings, two legs and a tail.
 *
 * <h2>Why the wings are driven by whether it is off the ground</h2>
 *
 * Because a pigeon that flaps while standing is the single thing that makes a
 * mob read as broken, and it is not caught by anything except looking at it.
 * The walk cycle moves the legs and the flap moves the wings, and which one
 * runs is decided by whether the bird is on the ground rather than by a flag
 * somebody has to remember to set.
 */
public class PigeonModel extends EntityModel<PigeonEntity> {

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart wingLeft;
    private final ModelPart wingRight;
    private final ModelPart legLeft;
    private final ModelPart legRight;
    private final ModelPart tail;

    public PigeonModel(ModelPart root) {
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.wingLeft = root.getChild("wing_left");
        this.wingRight = root.getChild("wing_right");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
        this.tail = root.getChild("tail");
    }

    /** The boxes, once, for every bird this module has. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, -3.0F, -3.0F, 4, 5, 6),
                PartPose.offset(0.0F, 20.0F, 0.0F));
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(20, 0).addBox(-1.5F, -3.0F, -3.0F, 3, 3, 3),
                PartPose.offset(0.0F, 17.5F, -2.5F));
        root.addOrReplaceChild("wing_left", CubeListBuilder.create()
                        .texOffs(0, 12).addBox(0.0F, 0.0F, -3.0F, 1, 4, 6),
                PartPose.offset(2.0F, 17.5F, 0.0F));
        root.addOrReplaceChild("wing_right", CubeListBuilder.create()
                        .texOffs(0, 12).addBox(-1.0F, 0.0F, -3.0F, 1, 4, 6),
                PartPose.offset(-2.0F, 17.5F, 0.0F));
        root.addOrReplaceChild("leg_left", CubeListBuilder.create()
                        .texOffs(16, 12).addBox(-0.5F, 0.0F, -0.5F, 1, 2, 1),
                PartPose.offset(1.0F, 22.0F, 0.5F));
        root.addOrReplaceChild("leg_right", CubeListBuilder.create()
                        .texOffs(16, 12).addBox(-0.5F, 0.0F, -0.5F, 1, 2, 1),
                PartPose.offset(-1.0F, 22.0F, 0.5F));
        root.addOrReplaceChild("tail", CubeListBuilder.create()
                        .texOffs(20, 6).addBox(-2.0F, -0.5F, 0.0F, 4, 1, 4),
                PartPose.offset(0.0F, 19.0F, 3.0F));
        return LayerDefinition.create(mesh, 32, 16);
    }

    @Override
    public void setupAnim(PigeonEntity bird, float limbSwing, float limbSwingAmount,
                          float age, float yaw, float pitch) {
        head.yRot = yaw * ((float) Math.PI / 180.0F);
        head.xRot = pitch * ((float) Math.PI / 180.0F);

        boolean flying = !bird.onGround();
        if (flying) {
            float beat = age * 0.9F;
            wingLeft.zRot = -((float) Math.cos(beat) * 0.9F) - 0.4F;
            wingRight.zRot = ((float) Math.cos(beat) * 0.9F) + 0.4F;
            legLeft.xRot = 0.8F;
            legRight.xRot = 0.8F;
        }
        if (!flying) {
            wingLeft.zRot = -0.1F;
            wingRight.zRot = 0.1F;
            legLeft.xRot = (float) Math.cos(limbSwing * 0.6662F) * 1.2F * limbSwingAmount;
            legRight.xRot = (float) Math.cos(limbSwing * 0.6662F + (float) Math.PI)
                    * 1.2F * limbSwingAmount;
        }
        tail.xRot = flying ? -0.3F : 0.1F;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer into, int light,
                               int overlay, int colour) {
        body.render(pose, into, light, overlay, colour);
        head.render(pose, into, light, overlay, colour);
        wingLeft.render(pose, into, light, overlay, colour);
        wingRight.render(pose, into, light, overlay, colour);
        legLeft.render(pose, into, light, overlay, colour);
        legRight.render(pose, into, light, overlay, colour);
        tail.render(pose, into, light, overlay, colour);
    }
}
