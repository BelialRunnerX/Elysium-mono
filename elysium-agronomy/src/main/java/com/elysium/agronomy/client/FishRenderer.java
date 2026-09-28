package com.elysium.agronomy.client;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.entity.FishEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
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
public class FishRenderer extends MobRenderer<FishEntity, FishModel<FishEntity>> {

    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    /** How long the model is, in centimetres, before anything is scaled. */
    // The plan is about 24 pixels long: a 180 cm fish at scale one.
    public static final float MODEL_CENTIMETRES = 180.0F;

    /** The smallest and largest a fish is allowed to be drawn. */
    public static final float LEAST_SCALE = 0.35F;
    public static final float MOST_SCALE = 3.0F;

    /** The one layer every species bakes; the model itself is generated and keeps no constants but its size. */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ElysiumAgronomy.MODID, "fish"), "main");

    public FishRenderer(EntityRendererProvider.Context context) {
        super(context, new FishModel<>(context.bakeLayer(LAYER)), 0.2F);
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
