package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.RelicBoltEntity;
import com.elysium.bestiary.rules.SummonRules;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * What a thrown ability throws, drawn.
 *
 * <p>On the game's own arrow renderer, because a bolt is an arrow in every way
 * that matters to a renderer — it points where it is going and it is one
 * texture. What it is not is an arrow you can pick up, and that is decided in
 * {@link RelicBoltEntity} rather than here.
 */
public class RelicBoltRenderer extends ArrowRenderer<RelicBoltEntity> {

    private static final ResourceLocation SKIN =
            ResourceLocation.fromNamespaceAndPath(ElysiumBestiary.MODID,
                    "textures/entity/" + SummonRules.BOLT + ".png");

    public RelicBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(RelicBoltEntity bolt) {
        return SKIN;
    }
}
