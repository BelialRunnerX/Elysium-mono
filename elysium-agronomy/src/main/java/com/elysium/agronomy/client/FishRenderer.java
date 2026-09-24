package com.elysium.agronomy.client;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.entity.FishEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * One renderer; the species picks the texture and the length picks the scale.
 *
 * <p>A hundred and five sizes and not one of them chosen here: the scale is
 * the fish's own length divided by the length of the model, so a five-metre
 * oarfish and a ten-centimetre goby come out of the same two boxes at the
 * sizes the rules already say they are.
 */
public class FishRenderer extends MobRenderer<FishEntity, FishModel> {

    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    /** How long the model is, in centimetres, before anything is scaled. */
    public static final float MODEL_CENTIMETRES = 60.0F;

    /** The smallest and largest a fish is allowed to be drawn. */
    public static final float LEAST_SCALE = 0.35F;
    public static final float MOST_SCALE = 3.0F;

    public FishRenderer(EntityRendererProvider.Context context) {
        super(context, new FishModel(context.bakeLayer(FishModel.LAYER)), 0.2F);
    }

    @Override
    protected void scale(FishEntity fish, PoseStack pose, float partial) {
        float wanted = fish.rules().length() / MODEL_CENTIMETRES;
        float scale = Math.max(LEAST_SCALE, Math.min(MOST_SCALE, wanted));
        pose.scale(scale, scale, scale);
    }

    @Override
    public ResourceLocation getTextureLocation(FishEntity fish) {
        return SKINS.computeIfAbsent(fish.species(),
                species -> ResourceLocation.fromNamespaceAndPath(
                        ElysiumAgronomy.MODID,
                        "textures/entity/fish/" + species + ".png"));
    }
}
