package com.elysium.bestiary.client;

import com.elysium.bestiary.entity.ShadeEntity;
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
 * A standing shape with no legs, because it does not walk so much as arrive.
 *
 * <p>Its own model rather than one of {@link CreatureModel}'s four, which are
 * built for things with four limbs and a walk cycle. A shade has a torso, two
 * arms and a head, and the bottom of it trails off — the one thing that has to
 * read at a glance is that it is not a mob you can farm.
 */
public class ShadeModel extends EntityModel<ShadeEntity> {

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart trail;

    public ShadeModel(ModelPart root) {
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.armLeft = root.getChild("arm_left");
        this.armRight = root.getChild("arm_right");
        this.trail = root.getChild("trail");
    }

    /** The boxes, once. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8),
                PartPose.offset(0.0F, 4.0F, 0.0F));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4),
                PartPose.offset(0.0F, 4.0F, 0.0F));
        root.addOrReplaceChild("arm_left", CubeListBuilder.create()
                        .texOffs(40, 16).addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4),
                PartPose.offset(5.0F, 6.0F, 0.0F));
        root.addOrReplaceChild("arm_right", CubeListBuilder.create()
                        .texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4),
                PartPose.offset(-5.0F, 6.0F, 0.0F));
        // Where the legs would be, tapering: a shade is not standing on
        // anything, and a pair of legs would say it was.
        root.addOrReplaceChild("trail", CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-3.0F, 0.0F, -1.5F, 6, 10, 3),
                PartPose.offset(0.0F, 16.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(ShadeEntity shade, float limbSwing, float limbSwingAmount,
                          float age, float yaw, float pitch) {
        head.yRot = yaw * ((float) Math.PI / 180.0F);
        head.xRot = pitch * ((float) Math.PI / 180.0F);
        // It reaches when it has something to reach for, and hangs when it
        // does not. The arms are the only tell that it is hunting.
        boolean hunting = shade.getTarget() != null;
        float drift = (float) Math.sin(age * 0.08F) * 0.08F;
        armLeft.xRot = hunting ? -1.4F + drift : drift;
        armRight.xRot = hunting ? -1.4F - drift : -drift;
        body.y = 4.0F + (float) Math.sin(age * 0.06F) * 0.6F;
        head.y = body.y;
        trail.xRot = drift * 2.0F;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer into, int light,
                               int overlay, int colour) {
        head.render(pose, into, light, overlay, colour);
        body.render(pose, into, light, overlay, colour);
        armLeft.render(pose, into, light, overlay, colour);
        armRight.render(pose, into, light, overlay, colour);
        trail.render(pose, into, light, overlay, colour);
    }
}
