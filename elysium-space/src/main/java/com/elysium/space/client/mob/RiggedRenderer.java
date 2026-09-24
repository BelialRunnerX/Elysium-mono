package com.elysium.space.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

import java.util.function.Function;

/** Renders a rigged mob with a texture chosen per entity (moonfolk wear their profession). */
public class RiggedRenderer<T extends Mob> extends MobRenderer<T, RiggedModel<T>> {

    private final Function<T, ResourceLocation> texture;

    public RiggedRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, Rig rig,
                          float shadow, Function<T, ResourceLocation> texture) {
        super(context, new RiggedModel<>(context.bakeLayer(layer), rig), shadow);
        this.texture = texture;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture.apply(entity);
    }

    @Override
    protected void scale(T entity, PoseStack pose, float partialTick) {
        if (entity.isBaby()) {
            pose.scale(0.5F, 0.5F, 0.5F);
        }
    }
}
