package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.client.shape.Shapes;
import com.elysium.bestiary.entity.MythEntity;
import com.elysium.bestiary.rules.MythRules;
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
 * A myth: the form its row names.
 *
 * <p>Drawn as the body plan of its form — a beast family or an animal kind,
 * which the rules name per row — from {@code client/shape} (docs/POLISH.md
 * P10). One layer per form under this kind's own name, so a kind and a beast
 * never register the same location twice; the mesh and the bounds are the
 * form's, through {@link Shapes}, and the renderer squashes the result onto
 * the row's hitbox as it always did.
 */
public class MythModel extends EntityModel<MythEntity> {

    /** One layer per form, named for this kind. */
    public static final Map<String, ModelLayerLocation> LAYERS = layers();

    private static Map<String, ModelLayerLocation> layers() {
        Map<String, ModelLayerLocation> made = new LinkedHashMap<>();
        for (String form : MythRules.forms()) {
            made.put(form, new ModelLayerLocation(
                    ResourceLocation.fromNamespaceAndPath(ElysiumBestiary.MODID, "myth_" + form), "main"));
        }
        return Map.copyOf(made);
    }

    /** The form's mesh: a beast family's or an animal kind's, or the prowler's for a name nothing has. */
    public static LayerDefinition createLayer(String form) {
        return Shapes.layer(known(form));
    }

    /** How big the form is, measured through its part tree at bind pose. */
    public static Bounds bounds(String form) {
        float[] size = Shapes.bounds(known(form));
        return new Bounds(size[0], size[1], size[2]);
    }

    private static String known(String form) {
        return Shapes.FAMILIES.contains(form) ? form : "prowler";
    }

    private final EntityModel<MythEntity> inner;

    public MythModel(ModelPart root, String form) {
        this.inner = Shapes.model(known(form), root);
    }

    @Override
    public void setupAnim(MythEntity creature, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.inner.setupAnim(creature, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                               int overlay, int colour) {
        this.inner.renderToBuffer(pose, buffer, light, overlay, colour);
    }
}
