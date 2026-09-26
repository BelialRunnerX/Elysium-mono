package com.elysium.bestiary.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.Mob;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Six boxes on a sheet, and the arithmetic that keeps them honest.
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
 * <p>So the boxes are not typed into a mesh builder. They are a table, which
 * {@link #createLayer} builds and {@link #bounds} measures, and a renderer
 * scales whatever comes out of it onto the hitbox the entity actually has.
 * Anything added tomorrow with a size nobody thought about is still exactly as
 * big as it looks, because the size it looks is computed from the size it is.
 *
 * <h2>Why this class exists rather than a second copy of it</h2>
 *
 * Because there are two rosters now. Nine things that hunt you and twelve that
 * do not are twenty-one creatures over seven shapes, and the alternative was
 * a second file with {@code Animal} written where {@code Beast} is — which is
 * the same mistake as two copies of a hitbox, made one level up. What differs
 * between the two is a table of shapes and one flourish of movement, and those
 * are the only two things a subclass supplies.
 *
 * @param <T> whatever is being drawn
 */
public abstract class CreatureModel<T extends Mob> extends EntityModel<T> {

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

    /** How big a sheet one creature gets. */
    public static final int SHEET = 128;

    /**
     * Where each part's unwrapped box starts on the sheet.
     *
     * <p>Fixed corners rather than a packing, so the generator that draws the
     * sheets and the mesh that reads them cannot disagree. Each slot is bigger
     * than the biggest box any shape puts in it; the harness proves it.
     */
    public static final Map<String, int[]> SLOTS = Map.of(
            HEAD, new int[]{0, 0, 48, 24},
            BODY, new int[]{0, 32, 72, 32},
            ARM_LEFT, new int[]{0, 72, 24, 32},
            ARM_RIGHT, new int[]{32, 72, 24, 32},
            LEG_LEFT, new int[]{64, 72, 24, 32},
            LEG_RIGHT, new int[]{96, 72, 24, 32});

    /** How big one shape actually is, in pixels, measured rather than declared. */
    public record Bounds(float wide, float tall, float deep) {
    }

    /** Measure a shape. */
    public static Bounds bounds(List<Part> parts) {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("nothing is shaped like that");
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

    /** Build a layer out of a shape. */
    public static LayerDefinition createLayer(List<Part> parts) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition creature = root.addOrReplaceChild("beast",
                CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
        for (Part part : parts) {
            int[] slot = SLOTS.get(part.name());
            creature.addOrReplaceChild(part.name(), CubeListBuilder.create()
                            .texOffs(slot[0], slot[1])
                            .addBox(part.boxX(), part.boxY(), part.boxZ(),
                                    part.wide(), part.tall(), part.deep()),
                    PartPose.offset(part.pivotX(), part.pivotY(), part.pivotZ()));
        }
        return LayerDefinition.create(mesh, SHEET, SHEET);
    }

    private final ModelPart root;
    protected final Map<String, ModelPart> parts = new LinkedHashMap<>();

    protected CreatureModel(ModelPart root) {
        this.root = root;
        ModelPart creature = root.getChild("beast");
        for (String name : PARTS) {
            parts.put(name, creature.getChild(name));
        }
    }

    /**
     * The walk cycle, and nothing more clever than that.
     *
     * <p>What is on screen has to be readable at twenty blocks in the dark,
     * which rules out anything subtle by definition.
     */
    @Override
    public void setupAnim(T creature, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        ModelPart head = parts.get(HEAD);
        head.yRot = netHeadYaw * ((float) Math.PI / 180.0F);
        head.xRot = headPitch * ((float) Math.PI / 180.0F);

        float swing = phase(limbSwing);
        float amount = Math.min(limbSwingAmount, 1.0F);
        parts.get(LEG_LEFT).xRot = (float) Math.cos(swing) * 1.4F * amount;
        parts.get(LEG_RIGHT).xRot = (float) Math.cos(swing + Math.PI) * 1.4F * amount;
        parts.get(ARM_LEFT).xRot = (float) Math.cos(swing + Math.PI) * 1.0F * amount;
        parts.get(ARM_RIGHT).xRot = (float) Math.cos(swing) * 1.0F * amount;

        idle(creature, ageInTicks, amount);
    }

    /**
     * Whatever this roster does when it is not walking.
     *
     * <p>The one thing that tells two creatures of the same shape apart at a
     * distance, and the only part of a model a subclass gets to decide.
     */
    protected abstract void idle(T creature, float ageInTicks, float moving);

    /** Where in the walk cycle a limb is, at the game's own stride. */
    protected static float phase(float limbSwing) {
        return limbSwing * 0.6662F;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                               int overlay, int colour) {
        root.render(pose, buffer, light, overlay, colour);
    }
}
