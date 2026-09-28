package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.CompanionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * One renderer for all eleven, on the fauna's forager shape.
 *
 * <p>A companion is a small thing that follows you about, which is what a
 * forager already is, and eleven bespoke models would be eleven more places for
 * a part to be named wrong. What differs between them is the texture and what
 * they do — neither of which is a model.
 */
public class CompanionRenderer extends MobRenderer<CompanionEntity, CompanionModel> {

    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    /** How many pixels the game counts to a block. */
    public static final float PIXELS = 16.0F;

    /** How wide one is drawn, matching the hitbox it is registered at. */
    public static final float WIDE = 0.7F;

    /** And how tall. */
    public static final float TALL = 0.9F;

    public CompanionRenderer(EntityRendererProvider.Context context) {
        super(context, new CompanionModel(context.bakeLayer(CompanionModel.LAYER)),
                0.3F);
    }

    @Override
    protected void scale(CompanionEntity companion, PoseStack pose, float partialTick) {
        Bounds shape = CompanionModel.bounds();
        float across = WIDE * PIXELS / Math.max(shape.wide(), shape.deep());
        float up = TALL * PIXELS / shape.tall();
        pose.scale(across, up, across);
        this.shadowRadius = WIDE * 0.6F;
    }

    @Override
    public ResourceLocation getTextureLocation(CompanionEntity companion) {
        String name = EntityType.getKey(companion.getType()).getPath();
        return SKINS.computeIfAbsent(name, key -> ResourceLocation.fromNamespaceAndPath(
                ElysiumBestiary.MODID, "textures/entity/" + key + ".png"));
    }
}
