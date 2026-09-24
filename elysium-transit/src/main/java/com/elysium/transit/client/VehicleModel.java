package com.elysium.transit.client;

import com.elysium.transit.ElysiumTransit;
import com.elysium.transit.entity.VehicleEntity;
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
 * A flatbed on four wheels, and the wheels turn.
 *
 * <p>One model for all three rungs: they differ by scale and texture, which is
 * what makes a fourth rung four numbers and a sprite rather than another file.
 * The wheels rolling is not decoration — it is the only thing on screen that
 * tells a player the difference between a vehicle that is out of fuel and one
 * that is stuck against a kerb.
 */
public class VehicleModel extends EntityModel<VehicleEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ElysiumTransit.MODID, "vehicle"), "main");

    private final ModelPart root;
    private final ModelPart[] wheels = new ModelPart[4];

    public VehicleModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("vehicle");
        for (int index = 0; index < wheels.length; index++) {
            wheels[index] = body.getChild("wheel_" + index);
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition vehicle = root.addOrReplaceChild("vehicle", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        vehicle.addOrReplaceChild("bed", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-8.0F, -9.0F, -12.0F, 16.0F, 3.0F, 24.0F), PartPose.ZERO);
        vehicle.addOrReplaceChild("cab", CubeListBuilder.create().texOffs(0, 28)
                .addBox(-7.0F, -16.0F, 2.0F, 14.0F, 7.0F, 9.0F), PartPose.ZERO);
        vehicle.addOrReplaceChild("sides", CubeListBuilder.create().texOffs(0, 45)
                .addBox(-8.0F, -12.0F, -12.0F, 16.0F, 3.0F, 1.0F), PartPose.ZERO);
        int index = 0;
        for (float x : new float[]{-7.5F, 7.5F}) {
            for (float z : new float[]{-7.0F, 7.0F}) {
                vehicle.addOrReplaceChild("wheel_" + index, CubeListBuilder.create()
                                .texOffs(48, 28)
                                .addBox(-1.0F, -3.0F, -3.0F, 2.0F, 6.0F, 6.0F),
                        PartPose.offset(x, -3.0F, z));
                index++;
            }
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(VehicleEntity vehicle, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Rolled from the speed the server actually gave it, not from how far
        // it looks like it has come: a wheel that spins while the vehicle is
        // held against a wall would say it was moving when it was not.
        float roll = ageInTicks * vehicle.speed() * 0.12F;
        for (ModelPart wheel : wheels) {
            wheel.xRot = roll;
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                               int overlay, int colour) {
        root.render(pose, buffer, light, overlay, colour);
    }
}
