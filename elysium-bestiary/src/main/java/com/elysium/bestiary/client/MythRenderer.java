package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.MythEntity;
import com.elysium.bestiary.registry.MythEntities;
import com.elysium.bestiary.rules.MythRules;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * One renderer for all eight, and the hitbox decides how big each looks.
 *
 * <p>The shape comes from the row's form, the texture from the id, and the
 * size from the two numbers the game built the hitbox out of — which is the
 * same arrangement {@link BeastRenderer} uses and for the same reason. Nothing
 * here chooses a scale, because a scale anybody chose is a scale that stops
 * matching the moment somebody edits a row.
 */
public class MythRenderer extends MobRenderer<MythEntity, MythModel> {

    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    /** How many pixels the game counts to a block. */
    public static final float PIXELS = 16.0F;

    public MythRenderer(EntityRendererProvider.Context context, String form) {
        super(context, new MythModel(context.bakeLayer(MythModel.LAYERS.get(form))),
                0.4F);
    }

    @Override
    protected void scale(MythEntity myth, PoseStack pose, float partialTick) {
        MythRules.Myth row = myth.myth();
        CreatureModel.Bounds shape = CreatureModel.bounds(MythModel.shape(row.form()));
        float[] size = MythEntities.sizeOf(row);
        float across = size[0] * PIXELS / Math.max(shape.wide(), shape.deep());
        float up = size[1] * PIXELS / shape.tall();
        pose.scale(across, up, across);
        this.shadowRadius = size[0] * 0.6F;
    }

    @Override
    public ResourceLocation getTextureLocation(MythEntity myth) {
        String name = EntityType.getKey(myth.getType()).getPath();
        return SKINS.computeIfAbsent(name, key -> ResourceLocation.fromNamespaceAndPath(
                ElysiumBestiary.MODID, "textures/entity/" + key + ".png"));
    }
}
