package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.BeastEntity;
import com.elysium.bestiary.rules.BeastRules;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Three shapes for nine beasts, and every one of them exactly the size of the
 * thing you are swinging at.
 *
 * <h2>The bug this is built to make impossible</h2>
 *
 * A modded mob whose model is not the size of its hitbox is the single most
 * common visual bug in a big pack, and it is never filed as one. It shows up
 * as "I keep missing it", or as an arrow that stops in mid-air a foot from
 * something, or as a mob that wades through a wall. It happens because the
 * hitbox is a pair of numbers in one file and the model is a pile of boxes in
 * another, and nothing compares them.
 *
 * <p>So the boxes are not typed into a mesh builder here. They are a table —
 * {@link #SHAPES} — which {@link #createLayer} builds and {@link #bounds}
 * measures, and the renderer scales whatever comes out of it onto the hitbox
 * the entity actually has. A beast added tomorrow with a size nobody thought
 * about is still exactly as big as it looks, because the size it looks is
 * computed from the size it is.
 *
 * <h2>And why the texture offsets are constants rather than packed</h2>
 *
 * Every family uses the same six parts, so every part gets a fixed corner of
 * the sheet big enough for the largest box any family puts there. A packer
 * would be an algorithm living in both this file and the generator that draws
 * the sheets, and two copies of an algorithm is the same bug as two copies of
 * a hitbox.
 */
public class BeastModel extends EntityModel<BeastEntity> {

    /**
     * One box, hung off one pivot.
     *
     * @param name    which of the six it is
     * @param pivotX  where it turns, across
     * @param pivotY  where it turns, up — negative is up, as the game counts
     * @param pivotZ  where it turns, along
     * @param boxX    the near corner of the box, from the pivot
     * @param boxY    and down
     * @param boxZ    and along
     * @param wide    how big the box is, in pixels
     * @param tall    ...
     * @param deep    ...
     */
    public record Part(String name, float pivotX, float pivotY, float pivotZ,
                       float boxX, float boxY, float boxZ, int wide, int tall, int deep) {

        public float lowX() {
            return pivotX + boxX;
        }

        public float highX() {
            return pivotX + boxX + wide;
        }

        public float lowY() {
            return pivotY + boxY;
        }

        public float highY() {
            return pivotY + boxY + tall;
        }

        public float lowZ() {
            return pivotZ + boxZ;
        }

        public float highZ() {
            return pivotZ + boxZ + deep;
        }

        /** How much of a texture sheet this box needs, unwrapped. */
        public int sheetWide() {
            return 2 * deep + 2 * wide;
        }

        /** And how far down. */
        public int sheetTall() {
            return deep + tall;
        }
    }

    public static final String HEAD = "head";
    public static final String BODY = "body";
    public static final String ARM_LEFT = "arm_left";
    public static final String ARM_RIGHT = "arm_right";
    public static final String LEG_LEFT = "leg_left";
    public static final String LEG_RIGHT = "leg_right";

    /** The six, in the order everything walks them. */
    public static final List<String> PARTS =
            List.of(HEAD, BODY, ARM_LEFT, ARM_RIGHT, LEG_LEFT, LEG_RIGHT);

    /** How big a sheet one beast gets. */
    public static final int SHEET = 128;

    /**
     * Where each part's unwrapped box starts on the sheet.
     *
     * <p>Fixed corners rather than a packing, so the generator that draws the
     * sheets and the mesh that reads them cannot disagree. Each slot is bigger
     * than the biggest box any family puts in it; the harness proves it.
     */
    public static final Map<String, int[]> SLOTS = Map.of(
            HEAD, new int[]{0, 0, 48, 24},
            BODY, new int[]{0, 32, 72, 32},
            ARM_LEFT, new int[]{0, 72, 24, 32},
            ARM_RIGHT, new int[]{32, 72, 24, 32},
            LEG_LEFT, new int[]{64, 72, 24, 32},
            LEG_RIGHT, new int[]{96, 72, 24, 32});

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

    /** How big one shape actually is, in pixels, measured rather than declared. */
    public record Bounds(float wide, float tall, float deep) {
    }

    /** Measure a shape. */
    public static Bounds bounds(String family) {
        List<Part> parts = SHAPES.get(family);
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("nothing is shaped like a " + family);
        }
        float lowX = Float.MAX_VALUE;
        float highX = -Float.MAX_VALUE;
        float lowY = Float.MAX_VALUE;
        float highY = -Float.MAX_VALUE;
        float lowZ = Float.MAX_VALUE;
        float highZ = -Float.MAX_VALUE;
        for (Part part : parts) {
            lowX = Math.min(lowX, part.lowX());
            highX = Math.max(highX, part.highX());
            lowY = Math.min(lowY, part.lowY());
            highY = Math.max(highY, part.highY());
            lowZ = Math.min(lowZ, part.lowZ());
            highZ = Math.max(highZ, part.highZ());
        }
        return new Bounds(highX - lowX, highY - lowY, highZ - lowZ);
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
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition beast = root.addOrReplaceChild("beast", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        for (Part part : SHAPES.get(family)) {
            int[] slot = SLOTS.get(part.name());
            beast.addOrReplaceChild(part.name(), CubeListBuilder.create()
                            .texOffs(slot[0], slot[1])
                            .addBox(part.boxX(), part.boxY(), part.boxZ(),
                                    part.wide(), part.tall(), part.deep()),
                    PartPose.offset(part.pivotX(), part.pivotY(), part.pivotZ()));
        }
        return LayerDefinition.create(mesh, SHEET, SHEET);
    }

    private final ModelPart root;
    private final Map<String, ModelPart> parts = new LinkedHashMap<>();

    public BeastModel(ModelPart root) {
        this.root = root;
        ModelPart beast = root.getChild("beast");
        for (String name : PARTS) {
            parts.put(name, beast.getChild(name));
        }
    }

    @Override
    public void setupAnim(BeastEntity beast, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        ModelPart head = parts.get(HEAD);
        head.yRot = netHeadYaw * ((float) Math.PI / 180.0F);
        head.xRot = headPitch * ((float) Math.PI / 180.0F);

        // A walk cycle, and nothing more clever than that. What is on screen
        // has to be readable at twenty blocks in the dark, which rules out
        // anything subtle by definition.
        float swing = phase(limbSwing);
        float amount = Math.min(limbSwingAmount, 1.0F);
        parts.get(LEG_LEFT).xRot = (float) Math.cos(swing) * 1.4F * amount;
        parts.get(LEG_RIGHT).xRot = (float) Math.cos(swing + Math.PI) * 1.4F * amount;
        parts.get(ARM_LEFT).xRot = (float) Math.cos(swing + Math.PI) * 1.0F * amount;
        parts.get(ARM_RIGHT).xRot = (float) Math.cos(swing) * 1.0F * amount;

        // A wight's arms drift whether it is moving or not, which is the only
        // thing that tells one apart from a prowler at a distance in the dark.
        if (BeastRules.WIGHT.equals(beast.beast().family())) {
            float drift = (float) Math.sin(ageInTicks * 0.06F) * 0.12F;
            parts.get(ARM_LEFT).xRot += drift - 0.35F;
            parts.get(ARM_RIGHT).xRot -= drift + 0.35F;
        }
    }

    /** Where in the walk cycle a limb is, at the game's own stride. */
    private static float phase(float limbSwing) {
        return limbSwing * 0.6662F;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                               int overlay, int colour) {
        root.render(pose, buffer, light, overlay, colour);
    }
}
