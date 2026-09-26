package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.FaunaEntity;
import com.elysium.bestiary.rules.FaunaRules;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Four shapes for twelve animals, on the bestiary's own boxes.
 *
 * <p>A grazer is a body on four legs with its head out in front of it; a
 * forager is the same thing low to the ground; a fowl stands up; a swimmer is
 * a long thing with fins where the legs would be. The six part names are the
 * bestiary's six, because {@link CreatureModel} walks them by name and a fifth
 * shape with a fifth vocabulary would be a second walk cycle to keep in step.
 */
public class FaunaModel extends CreatureModel<FaunaEntity> {

    /** The four shapes. */
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

    /** Measure one of the four. */
    public static Bounds bounds(String kind) {
        List<Part> parts = SHAPES.get(kind);
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("nothing is shaped like a " + kind);
        }
        return CreatureModel.bounds(parts);
    }

    /** One layer per kind. */
    public static final Map<String, ModelLayerLocation> LAYERS = layers();

    private static Map<String, ModelLayerLocation> layers() {
        Map<String, ModelLayerLocation> made = new LinkedHashMap<>();
        for (String kind : FaunaRules.KINDS) {
            made.put(kind, new ModelLayerLocation(
                    ResourceLocation.fromNamespaceAndPath(ElysiumBestiary.MODID, kind),
                    "main"));
        }
        return Map.copyOf(made);
    }

    public static LayerDefinition createLayer(String kind) {
        return CreatureModel.createLayer(SHAPES.get(kind));
    }

    public FaunaModel(ModelPart root) {
        super(root);
    }

    /**
     * What each kind does when it is standing still.
     *
     * <p>A grazer or a forager puts its head down to eat, a fowl bobs, and a
     * swimmer's tail never stops. Which is the difference between a pen that
     * looks alive and a pen full of statues, and — like the wight's drift —
     * the only thing that tells two animals of the same shape apart across a
     * field.
     */
    @Override
    protected void idle(FaunaEntity animal, float ageInTicks, float moving) {
        float still = 1.0F - moving;
        switch (animal.animal().kind()) {
            case FaunaRules.GRAZER, FaunaRules.FORAGER -> parts.get(HEAD).xRot +=
                    still * (0.5F + (float) Math.sin(ageInTicks * 0.05F) * 0.25F);
            case FaunaRules.FOWL -> {
                float bob = (float) Math.sin(ageInTicks * 0.18F) * 0.22F;
                parts.get(HEAD).xRot += still * bob;
                parts.get(ARM_LEFT).zRot = -0.15F + bob * 0.4F;
                parts.get(ARM_RIGHT).zRot = 0.15F - bob * 0.4F;
            }
            case FaunaRules.SWIMMER -> {
                float wag = (float) Math.sin(ageInTicks * 0.25F) * 0.45F;
                parts.get(LEG_LEFT).yRot = wag;
                parts.get(LEG_RIGHT).yRot = wag;
                parts.get(LEG_LEFT).xRot = 0.0F;
                parts.get(LEG_RIGHT).xRot = 0.0F;
                parts.get(ARM_LEFT).zRot = -0.3F - wag * 0.3F;
                parts.get(ARM_RIGHT).zRot = 0.3F + wag * 0.3F;
            }
            default -> {
            }
        }
    }
}
