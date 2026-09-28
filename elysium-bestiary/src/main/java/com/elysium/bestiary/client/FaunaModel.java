package com.elysium.bestiary.client;

import com.elysium.bestiary.client.shape.Shapes;
import com.elysium.bestiary.entity.FaunaEntity;
import com.elysium.bestiary.rules.FaunaRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An animal, drawn as its kind's body plan.
 *
 * <p>Twelve animals over four kinds, each kind a body plan from
 * {@code art/bodyplans.py} generated into {@code client/shape}: the grazers
 * and foragers are quadrupeds of different builds, the fowl a winged thing,
 * the swimmers fish (docs/POLISH.md P10). Like {@link BeastModel}, this is
 * the seam the renderer talks to; {@link Shapes} holds the plans, and the
 * renderer squashes whatever comes out onto the row's hitbox.
 *
 * <p>The six-box table the animals used to be is gone: every kind in this
 * module wears a plan, so nothing borrows a table any more (28 Sep).
 */
public class FaunaModel extends EntityModel<FaunaEntity> {

    /** One layer per kind, as {@link Shapes} names them. */
    public static final Map<String, ModelLayerLocation> LAYERS = layers();

    private static Map<String, ModelLayerLocation> layers() {
        Map<String, ModelLayerLocation> made = new LinkedHashMap<>();
        for (String kind : FaunaRules.KINDS) {
            made.put(kind, Shapes.LAYERS.containsKey(kind) ? Shapes.LAYERS.get(kind)
                    : new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(
                            com.elysium.bestiary.ElysiumBestiary.MODID, kind), "main"));
        }
        return Map.copyOf(made);
    }

    /** The mesh for one kind: its shape's. */
    public static LayerDefinition createLayer(String kind) {
        return Shapes.layer(kind);
    }

    /** How big a kind's shape is, measured through its part tree at bind pose. */
    public static Bounds bounds(String kind) {
        if (!Shapes.FAMILIES.contains(kind)) {
            throw new IllegalArgumentException("nothing is shaped like a " + kind);
        }
        float[] size = Shapes.bounds(kind);
        return new Bounds(size[0], size[1], size[2]);
    }

    private final EntityModel<FaunaEntity> inner;

    public FaunaModel(ModelPart root, String kind) {
        this.inner = Shapes.model(kind, root);
    }

    @Override
    public void setupAnim(FaunaEntity creature, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.inner.setupAnim(creature, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                               int overlay, int colour) {
        this.inner.renderToBuffer(pose, buffer, light, overlay, colour);
    }
}
