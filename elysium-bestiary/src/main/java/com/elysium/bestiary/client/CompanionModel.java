package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.client.shape.Shapes;
import com.elysium.bestiary.entity.CompanionEntity;
import com.elysium.bestiary.rules.FaunaRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * A companion: a forager's body plan, at a companion's size.
 *
 * <p>Every companion is drawn as the forager — small, low, close to the
 * ground — from {@code client/shape} (docs/POLISH.md P10), under its own
 * layer name; the renderer squashes it onto the companion's hitbox.
 */
public class CompanionModel extends EntityModel<CompanionEntity> {

    public static final String FORM = FaunaRules.FORAGER;

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ElysiumBestiary.MODID, "companion"), "main");

    public static LayerDefinition createLayer() {
        return Shapes.layer(FORM);
    }

    public static Bounds bounds() {
        float[] size = Shapes.bounds(FORM);
        return new Bounds(size[0], size[1], size[2]);
    }

    private final EntityModel<CompanionEntity> inner;

    public CompanionModel(ModelPart root) {
        this.inner = Shapes.model(FORM, root);
    }

    @Override
    public void setupAnim(CompanionEntity creature, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.inner.setupAnim(creature, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                               int overlay, int colour) {
        this.inner.renderToBuffer(pose, buffer, light, overlay, colour);
    }
}
