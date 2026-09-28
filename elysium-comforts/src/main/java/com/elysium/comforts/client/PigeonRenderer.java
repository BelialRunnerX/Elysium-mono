package com.elysium.comforts.client;

import com.elysium.comforts.ElysiumComforts;
import com.elysium.comforts.entity.PigeonEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * One renderer for every bird, with the colouring coming out of the entity.
 *
 * <p>The texture is looked up by the name the bird was registered under and the
 * colouring it rolled when it spawned. A missing texture is a bug; a missing
 * texture that only one player in four ever sees is a bug nobody can reproduce,
 * which is why the validator counts the skins on disk against the number of
 * colourings the rules say a bird can roll.
 */
public class PigeonRenderer extends MobRenderer<PigeonEntity, PigeonModel<PigeonEntity>> {

    /** The one layer every bird in this module is drawn on. */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ElysiumComforts.MODID, "pigeon"),
            "main");

    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    public PigeonRenderer(EntityRendererProvider.Context context) {
        super(context, new PigeonModel<>(context.bakeLayer(LAYER)), 0.25F);
    }

    @Override
    public ResourceLocation getTextureLocation(PigeonEntity bird) {
        String name = EntityType.getKey(bird.getType()).getPath();
        String key = name + "_" + bird.variant();
        return SKINS.computeIfAbsent(key, made ->
                ResourceLocation.fromNamespaceAndPath(ElysiumComforts.MODID,
                        "textures/entity/" + made + ".png"));
    }
}
