package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.CourtEntity;
import com.elysium.bestiary.registry.CourtEntities;
import com.elysium.bestiary.rules.CourtRules;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * One renderer for all fifty-eight, and the hitbox decides how big each looks.
 *
 * <p>The shape comes from the kindred, the texture from the id, and the size
 * from the two numbers the game built the hitbox out of — the same arrangement
 * {@link BeastRenderer} and {@link MythRenderer} use, and for the same reason.
 * Nothing here chooses a scale, because a scale anybody chose is a scale that
 * stops matching the moment somebody edits a row.
 *
 * <p>The one thing this renderer does that the others do not is draw a head that has
 * turned slightly larger than one that has not. That belongs here rather than
 * in the role, because whether a fight has changed is something the player
 * finds out by looking.
 */
public class CourtRenderer extends MobRenderer<CourtEntity, CourtModel> {

    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    /** How many pixels the game counts to a block. */
    public static final float PIXELS = 16.0F;

    /** How much bigger a head looks once it has turned. */
    public static final float TURNED = 1.15F;

    public CourtRenderer(EntityRendererProvider.Context context, String form) {
        super(context, new CourtModel(context.bakeLayer(CourtModel.LAYERS.get(form)), form),
                0.4F);
    }

    @Override
    protected void scale(CourtEntity one, PoseStack pose, float partialTick) {
        CourtRules.Member row = one.member();
        Bounds shape = CourtModel.bounds(CourtRules.shapeOf(row));
        float[] size = CourtEntities.sizeOf(row);
        float small = one.turned() ? TURNED : 1.0F;
        float across = size[0] * PIXELS / Math.max(shape.wide(), shape.deep()) * small;
        float up = size[1] * PIXELS / shape.tall() * small;
        pose.scale(across, up, across);
        this.shadowRadius = size[0] * 0.6F * small;
    }

    @Override
    public ResourceLocation getTextureLocation(CourtEntity one) {
        String name = EntityType.getKey(one.getType()).getPath();
        return SKINS.computeIfAbsent(name, key -> ResourceLocation.fromNamespaceAndPath(
                ElysiumBestiary.MODID, "textures/entity/" + key + ".png"));
    }
}
