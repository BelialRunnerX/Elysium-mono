package com.elysium.space.client.mob;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.entity.mob.RimeSpit;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** A rime spit flies as an arrow does, in its own shard texture. */
public class RimeSpitRenderer extends ArrowRenderer<RimeSpit> {

    static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, "textures/entity/rime_spit.png");

    public RimeSpitRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(RimeSpit entity) {
        return TEXTURE;
    }
}
