package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.DenizenEntity;
import com.elysium.bestiary.registry.DenizenEntities;
import com.elysium.bestiary.rules.DenizenRules;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * One renderer for all twenty-eight, and the hitbox decides how big each looks.
 *
 * <p>The shape comes from the ring, the texture from the id, and the size
 * from the two numbers the game built the hitbox out of — the same arrangement
 * {@link BeastRenderer} and {@link MythRenderer} use, and for the same reason.
 * Nothing here chooses a scale, because a scale anybody chose is a scale that
 * stops matching the moment somebody edits a row.
 *
 * <p>What this one does that the others do not is make how far out a player
 * has walked readable without their having to find out by fighting: a thing
 * from the outer ring is drawn larger than one from the middle, on the same
 * shape. That is the whole claim of this roster, shown rather than stated.
 */
public class DenizenRenderer extends MobRenderer<DenizenEntity, DenizenModel> {

    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    /** How many pixels the game counts to a block. */
    public static final float PIXELS = 16.0F;

    /** How much larger each ring out from the middle is drawn. */
    public static final float FURTHER = 0.08F;

    public DenizenRenderer(EntityRendererProvider.Context context, String form) {
        super(context, new DenizenModel(context.bakeLayer(DenizenModel.LAYERS.get(form)), form),
                0.4F);
    }

    @Override
    protected void scale(DenizenEntity one, PoseStack pose, float partialTick) {
        DenizenRules.Denizen row = one.denizen();
        Bounds shape = DenizenModel.bounds(DenizenRules.shapeOf(row));
        float[] size = DenizenEntities.sizeOf(row);
        float across = size[0] * PIXELS / Math.max(shape.wide(), shape.deep());
        float up = size[1] * PIXELS / shape.tall();
        float further = 1.0F + FURTHER * one.ringsOut();
        pose.scale(across * further, up * further, across * further);
        this.shadowRadius = size[0] * 0.6F * further;
    }

    @Override
    public ResourceLocation getTextureLocation(DenizenEntity one) {
        String name = EntityType.getKey(one.getType()).getPath();
        return SKINS.computeIfAbsent(name, key -> ResourceLocation.fromNamespaceAndPath(
                ElysiumBestiary.MODID, "textures/entity/" + key + ".png"));
    }
}
