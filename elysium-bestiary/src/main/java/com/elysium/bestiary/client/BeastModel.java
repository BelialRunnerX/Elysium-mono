package com.elysium.bestiary.client;

import com.elysium.bestiary.client.shape.Shapes;
import com.elysium.bestiary.entity.BeastEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;

import java.util.Map;

/**
 * A beast, drawn as its family's body plan.
 *
 * <p>Nine beasts over three shapes, each shape a body plan from
 * {@code art/bodyplans.py} generated into {@code client/shape} — a hundred-plus
 * boxes in a part tree, with its sheet from the same rows and its bounds
 * measured through the hierarchy (docs/POLISH.md P10). This class is the seam:
 * the renderer asks it for a layer, a model and a size by family, and it asks
 * {@link Shapes}. The renderer still squashes whatever comes out onto the
 * row's hitbox, so a plan drawn at any size stands exactly as tall as the thing
 * a player swings at.
 *
 * <p>The six-box table the beasts used to be is gone: every kind in this module
 * wears a plan, so nothing borrows a table any more (28 Sep).
 */
public class BeastModel extends EntityModel<BeastEntity> {

    /** One layer per family, as {@link Shapes} names them. */
    public static final Map<String, ModelLayerLocation> LAYERS = Shapes.LAYERS;

    /** The mesh for one family: its shape's. */
    public static LayerDefinition createLayer(String family) {
        return Shapes.layer(family);
    }

    /**
     * How big a family's shape is, measured through its part tree at bind
     * pose, in the same record the other kinds report theirs in.
     */
    public static Bounds bounds(String family) {
        if (!Shapes.FAMILIES.contains(family)) {
            throw new IllegalArgumentException("nothing is shaped like a " + family);
        }
        float[] size = Shapes.bounds(family);
        return new Bounds(size[0], size[1], size[2]);
    }

    private final EntityModel<BeastEntity> inner;

    public BeastModel(ModelPart root, String family) {
        this.inner = Shapes.model(family, root);
    }

    @Override
    public void setupAnim(BeastEntity creature, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.inner.setupAnim(creature, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                               int overlay, int colour) {
        this.inner.renderToBuffer(pose, buffer, light, overlay, colour);
    }
}
