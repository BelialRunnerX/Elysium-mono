package com.elysium.bestiary.client;

import com.elysium.bestiary.client.CreatureModel.Part;
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

import static com.elysium.bestiary.client.CreatureModel.ARM_LEFT;
import static com.elysium.bestiary.client.CreatureModel.ARM_RIGHT;
import static com.elysium.bestiary.client.CreatureModel.BODY;
import static com.elysium.bestiary.client.CreatureModel.HEAD;
import static com.elysium.bestiary.client.CreatureModel.LEG_LEFT;
import static com.elysium.bestiary.client.CreatureModel.LEG_RIGHT;

import java.util.LinkedHashMap;
import java.util.List;
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
 * <p>{@link #SHAPES} is the six-box table the animals used to be, kept for the
 * kinds that still borrow an animal's shape by name until they have plans of
 * their own; nothing here draws it.
 */
public class FaunaModel extends EntityModel<FaunaEntity> {

    /** The six-box shapes, kept for the kinds that borrow them (see the class comment). */
    public static final Map<String, List<Part>> SHAPES = shapes();

    private static Map<String, List<Part>> shapes() {
        Map<String, List<Part>> made = new LinkedHashMap<>();
        made.put(FaunaRules.GRAZER, List.of(
                new Part(HEAD, 0.0F, -21.0F, -10.0F, -3.0F, -3.0F, -8.0F, 6, 6, 8),
                new Part(BODY, 0.0F, -18.0F, 0.0F, -5.0F, -5.0F, -9.0F, 10, 9, 18),
                new Part(ARM_LEFT, -3.5F, -14.0F, -6.0F, -1.5F, 0.0F, -1.5F, 3, 13, 3),
                new Part(ARM_RIGHT, 3.5F, -14.0F, -6.0F, -1.5F, 0.0F, -1.5F, 3, 13, 3),
                new Part(LEG_LEFT, -3.5F, -14.0F, 6.0F, -1.5F, 0.0F, -1.5F, 3, 13, 3),
                new Part(LEG_RIGHT, 3.5F, -14.0F, 6.0F, -1.5F, 0.0F, -1.5F, 3, 13, 3)));
        made.put(FaunaRules.FORAGER, List.of(
                new Part(HEAD, 0.0F, -10.0F, -7.0F, -2.5F, -2.5F, -6.0F, 5, 5, 6),
                new Part(BODY, 0.0F, -9.0F, 0.0F, -4.0F, -4.0F, -6.0F, 8, 7, 12),
                new Part(ARM_LEFT, -3.0F, -5.0F, -4.0F, -1.5F, 0.0F, -1.5F, 3, 5, 3),
                new Part(ARM_RIGHT, 3.0F, -5.0F, -4.0F, -1.5F, 0.0F, -1.5F, 3, 5, 3),
                new Part(LEG_LEFT, -3.0F, -5.0F, 4.0F, -1.5F, 0.0F, -1.5F, 3, 5, 3),
                new Part(LEG_RIGHT, 3.0F, -5.0F, 4.0F, -1.5F, 0.0F, -1.5F, 3, 5, 3)));
        made.put(FaunaRules.FOWL, List.of(
                new Part(HEAD, 0.0F, -16.0F, -1.0F, -2.0F, -4.0F, -4.0F, 4, 5, 5),
                new Part(BODY, 0.0F, -11.0F, 0.0F, -3.0F, -4.0F, -4.0F, 6, 8, 8),
                new Part(ARM_LEFT, -3.0F, -14.0F, 0.0F, -1.0F, 0.0F, -3.0F, 1, 7, 6),
                new Part(ARM_RIGHT, 3.0F, -14.0F, 0.0F, 0.0F, 0.0F, -3.0F, 1, 7, 6),
                new Part(LEG_LEFT, -1.5F, -6.0F, 0.0F, -1.0F, 0.0F, -1.0F, 2, 6, 2),
                new Part(LEG_RIGHT, 1.5F, -6.0F, 0.0F, -1.0F, 0.0F, -1.0F, 2, 6, 2)));
        made.put(FaunaRules.SWIMMER, List.of(
                new Part(HEAD, 0.0F, -6.0F, -9.0F, -2.5F, -2.5F, -6.0F, 5, 5, 6),
                new Part(BODY, 0.0F, -6.0F, 0.0F, -3.0F, -3.0F, -8.0F, 6, 6, 16),
                new Part(ARM_LEFT, -3.0F, -6.0F, -2.0F, -1.0F, -2.0F, -2.5F, 1, 4, 5),
                new Part(ARM_RIGHT, 3.0F, -6.0F, -2.0F, 0.0F, -2.0F, -2.5F, 1, 4, 5),
                new Part(LEG_LEFT, -0.5F, -6.0F, 8.0F, -0.5F, -3.0F, 0.0F, 1, 6, 4),
                new Part(LEG_RIGHT, 0.5F, -6.0F, 8.0F, -0.5F, -3.0F, 0.0F, 1, 6, 4)));
        return Map.copyOf(made);
    }

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
    public static CreatureModel.Bounds bounds(String kind) {
        if (!Shapes.FAMILIES.contains(kind)) {
            throw new IllegalArgumentException("nothing is shaped like a " + kind);
        }
        float[] size = Shapes.bounds(kind);
        return new CreatureModel.Bounds(size[0], size[1], size[2]);
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
