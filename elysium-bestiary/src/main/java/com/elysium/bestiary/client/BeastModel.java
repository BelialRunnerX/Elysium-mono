package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.BeastEntity;
import com.elysium.bestiary.rules.BeastRules;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Three shapes for nine beasts, and every one of them exactly the size of the
 * thing you are swinging at.
 *
 * <p>The boxes, the sheet slots and the measuring are all in
 * {@link CreatureModel}, which the fauna uses too. What is here is the table
 * of three shapes and the one thing a wight does that nothing else does.
 */
public class BeastModel extends CreatureModel<BeastEntity> {

    /**
     * The three shapes.
     *
     * <p>A prowler is low and long and leads with its head; a brute is a door
     * with arms; a wight is a tall thin thing with arms too long for it. Every
     * one of them is thirty-two pixels tall and sixteen across, because the
     * renderer squashes the whole shape onto whatever hitbox the beast has and
     * a shape that started a different size would land a different size.
     */
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

    /** Measure one of the three. */
    public static Bounds bounds(String family) {
        List<Part> parts = SHAPES.get(family);
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("nothing is shaped like a " + family);
        }
        return CreatureModel.bounds(parts);
    }

    /** One layer per family. */
    public static final Map<String, ModelLayerLocation> LAYERS = layers();

    private static Map<String, ModelLayerLocation> layers() {
        Map<String, ModelLayerLocation> made = new LinkedHashMap<>();
        for (String family : BeastRules.FAMILIES) {
            made.put(family, new ModelLayerLocation(
                    ResourceLocation.fromNamespaceAndPath(ElysiumBestiary.MODID, family),
                    "main"));
        }
        return Map.copyOf(made);
    }

    public static LayerDefinition createLayer(String family) {
        return CreatureModel.createLayer(SHAPES.get(family));
    }

    public BeastModel(ModelPart root) {
        super(root);
    }

    /**
     * A wight's arms drift whether it is moving or not.
     *
     * <p>Which is the only thing that tells one apart from a prowler at a
     * distance in the dark.
     */
    @Override
    protected void idle(BeastEntity beast, float ageInTicks, float moving) {
        if (BeastRules.WIGHT.equals(beast.beast().family())) {
            float drift = (float) Math.sin(ageInTicks * 0.06F) * 0.12F;
            parts.get(ARM_LEFT).xRot += drift - 0.35F;
            parts.get(ARM_RIGHT).xRot -= drift + 0.35F;
        }
    }
}
