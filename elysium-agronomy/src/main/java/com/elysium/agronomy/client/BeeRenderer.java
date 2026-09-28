package com.elysium.agronomy.client;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.entity.BeeEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * One renderer; the species picks the texture.
 *
 * <p>The path is computed from the bee's own species rather than looked up in
 * a table, so a bee added to the rules renders the moment its texture exists,
 * and a bee whose texture is missing shows the game's own missing-texture
 * rather than crashing.
 */
public class BeeRenderer extends MobRenderer<BeeEntity, BeeModel<BeeEntity>> {

    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    /** The one layer every species bakes; the model itself is generated and keeps no constants but its size. */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ElysiumAgronomy.MODID, "bee"), "main");

    public BeeRenderer(EntityRendererProvider.Context context) {
        super(context, new BeeModel<>(context.bakeLayer(LAYER)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(BeeEntity bee) {
        return SKINS.computeIfAbsent(bee.species(),
                species -> ResourceLocation.fromNamespaceAndPath(
                        ElysiumAgronomy.MODID, "textures/entity/bee/" + species + ".png"));
    }
}
