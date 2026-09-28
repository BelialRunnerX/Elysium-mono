package com.elysium.bestiary.client;

import com.elysium.bestiary.client.CreatureModel.Part;
import com.elysium.bestiary.client.shape.Shapes;
import com.elysium.bestiary.entity.BeastEntity;
import com.elysium.bestiary.rules.BeastRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;

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
 * <p>{@link #SHAPES} is the six-box table the beasts used to be. It stays here
 * because the kin, the court, the denizens, the wards and the myths still
 * borrow a beast's shape by name from {@link CreatureModel}'s table form,
 * until each of them has a plan of its own; nothing here draws it.
 */
public class BeastModel extends EntityModel<BeastEntity> {

    /** The six-box shapes, kept for the kinds that borrow them (see the class comment). */
    public static final Map<String, List<Part>> SHAPES = shapes();

    private static Map<String, List<Part>> shapes() {
        Map<String, List<Part>> made = new LinkedHashMap<>();
        made.put(BeastRules.PROWLER, List.of(
                new Part(HEAD, 0.0F, -22.0F, 0.0F, -5.0F, -10.0F, -8.0F, 10, 10, 10),
                new Part(BODY, 0.0F, -14.0F, 0.0F, -6.0F, -8.0F, -8.0F, 12, 12, 16),
                new Part(ARM_LEFT, -6.0F, -21.0F, 0.0F, -2.0F, -1.0F, -2.0F, 2, 14, 4),
                new Part(ARM_RIGHT, 6.0F, -21.0F, 0.0F, 0.0F, -1.0F, -2.0F, 2, 14, 4),
                new Part(LEG_LEFT, -3.0F, -10.0F, 0.0F, -2.0F, 0.0F, -2.0F, 4, 10, 4),
                new Part(LEG_RIGHT, 3.0F, -10.0F, 0.0F, -2.0F, 0.0F, -2.0F, 4, 10, 4)));
        made.put(BeastRules.BRUTE, List.of(
                new Part(HEAD, 0.0F, -26.0F, 0.0F, -5.0F, -6.0F, -7.0F, 10, 6, 10),
                new Part(BODY, 0.0F, -16.0F, 0.0F, -5.0F, -10.0F, -8.0F, 10, 14, 16),
                new Part(ARM_LEFT, -5.0F, -25.0F, 0.0F, -3.0F, -1.0F, -3.0F, 3, 17, 6),
                new Part(ARM_RIGHT, 5.0F, -25.0F, 0.0F, 0.0F, -1.0F, -3.0F, 3, 17, 6),
                new Part(LEG_LEFT, -3.0F, -12.0F, 0.0F, -3.0F, 0.0F, -3.0F, 6, 12, 6),
                new Part(LEG_RIGHT, 3.0F, -12.0F, 0.0F, -3.0F, 0.0F, -3.0F, 6, 12, 6)));
        made.put(BeastRules.WIGHT, List.of(
                new Part(HEAD, 0.0F, -26.0F, 0.0F, -4.0F, -6.0F, -5.0F, 8, 6, 10),
                new Part(BODY, 0.0F, -16.0F, 0.0F, -4.0F, -10.0F, -5.0F, 8, 14, 10),
                new Part(ARM_LEFT, -4.0F, -25.0F, 0.0F, -4.0F, -1.0F, -2.0F, 4, 18, 4),
                new Part(ARM_RIGHT, 4.0F, -25.0F, 0.0F, 0.0F, -1.0F, -2.0F, 4, 18, 4),
                new Part(LEG_LEFT, -2.0F, -12.0F, 0.0F, -2.0F, 0.0F, -2.0F, 4, 12, 4),
                new Part(LEG_RIGHT, 2.0F, -12.0F, 0.0F, -2.0F, 0.0F, -2.0F, 4, 12, 4)));
        return Map.copyOf(made);
    }

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
    public static CreatureModel.Bounds bounds(String family) {
        if (!Shapes.FAMILIES.contains(family)) {
            throw new IllegalArgumentException("nothing is shaped like a " + family);
        }
        float[] size = Shapes.bounds(family);
        return new CreatureModel.Bounds(size[0], size[1], size[2]);
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
