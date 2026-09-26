package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.WardEntity;
import com.elysium.bestiary.registry.WardEntities;
import com.elysium.bestiary.rules.WardRules;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * One renderer for all thirteen, and the hitbox decides how big each looks.
 *
 * <p>The shape comes from the ward, the texture from the id, and the size
 * from the two numbers the game built the hitbox out of — the same arrangement
 * {@link BeastRenderer} and {@link MythRenderer} use, and for the same reason.
 * Nothing here chooses a scale, because a scale anybody chose is a scale that
 * stops matching the moment somebody edits a row.
 *
 * <p>Nothing here reads what the player is carrying either. Whether a creature
 * is hitting for its warded number or its unwarded one is a fact about the
 * fight rather than about the screen, and a renderer that drew it would be a
 * second place deciding the same thing.
 */
public class WardRenderer extends MobRenderer<WardEntity, WardModel> {

    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    /** How many pixels the game counts to a block. */
    public static final float PIXELS = 16.0F;

    public WardRenderer(EntityRendererProvider.Context context, String form) {
        super(context, new WardModel(context.bakeLayer(WardModel.LAYERS.get(form))),
                0.4F);
    }

    @Override
    protected void scale(WardEntity one, PoseStack pose, float partialTick) {
        WardRules.Warded row = one.warded();
        CreatureModel.Bounds shape = CreatureModel.bounds(
                WardModel.shape(WardRules.shapeOf(row)));
        float[] size = WardEntities.sizeOf(row);
        float across = size[0] * PIXELS / Math.max(shape.wide(), shape.deep());
        float up = size[1] * PIXELS / shape.tall();
        pose.scale(across, up, across);
        this.shadowRadius = size[0] * 0.6F;
    }

    @Override
    public ResourceLocation getTextureLocation(WardEntity one) {
        String name = EntityType.getKey(one.getType()).getPath();
        return SKINS.computeIfAbsent(name, key -> ResourceLocation.fromNamespaceAndPath(
                ElysiumBestiary.MODID, "textures/entity/" + key + ".png"));
    }
}
