package com.elysium.space.client.mob;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PiglinRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * Cinderlings and their brutes: vanilla's piglin renderer and model (Stellaris's
 * PygroModel is the piglin's animation over its own head), on our geometry,
 * with a piglin's armour layers.
 */
public class CinderlingRenderer extends PiglinRenderer {

    private final ResourceLocation texture;

    public CinderlingRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, ResourceLocation texture) {
        super(context, layer, ModelLayers.PIGLIN_INNER_ARMOR, ModelLayers.PIGLIN_OUTER_ARMOR, false);
        this.texture = texture;
    }

    @Override
    public ResourceLocation getTextureLocation(Mob entity) {
        return texture;
    }
}
