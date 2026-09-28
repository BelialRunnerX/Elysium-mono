package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.ShadeEntity;
import com.elysium.bestiary.rules.SummonRules;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * What a summoning ability calls up, drawn.
 *
 * <p>The reason this class exists at all is that it did not, for a while: the
 * shade and the bolt were registered, wired into the library and spawned by
 * three abilities, and nothing drew either of them. An entity type with no
 * renderer is not a quiet failure — the first one a player summons takes the
 * client down with it.
 */
public class ShadeRenderer extends MobRenderer<ShadeEntity, ShadeModel<ShadeEntity>> {

    /** The layer the shade is drawn on. */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ElysiumBestiary.MODID,
                    SummonRules.SHADE), "main");

    private static final ResourceLocation SKIN =
            ResourceLocation.fromNamespaceAndPath(ElysiumBestiary.MODID,
                    "textures/entity/" + SummonRules.SHADE + ".png");

    public ShadeRenderer(EntityRendererProvider.Context context) {
        super(context, new ShadeModel<>(context.bakeLayer(LAYER)), 0.4F);
    }

    /** Drawn as something you can see through, because it is. */
    @Override
    protected RenderType getRenderType(ShadeEntity shade, boolean visible,
                                       boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(shade));
    }

    @Override
    public ResourceLocation getTextureLocation(ShadeEntity shade) {
        return SKIN;
    }
}
