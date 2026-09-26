package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.FaunaEntity;
import com.elysium.bestiary.rules.FaunaRules;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * One renderer, and the hitbox decides how big the animal looks.
 *
 * <p>Four shapes and twelve animals: the shape comes from the kind, the
 * texture from the id, and the size from the two numbers the game builds the
 * hitbox out of. Nothing here chooses a scale — see {@link BeastRenderer},
 * which makes the same argument about the things that hunt you.
 */
public class FaunaRenderer extends MobRenderer<FaunaEntity, FaunaModel> {

    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    /** How many pixels the game counts to a block. */
    public static final float PIXELS = 16.0F;

    public FaunaRenderer(EntityRendererProvider.Context context, String kind) {
        super(context, new FaunaModel(context.bakeLayer(FaunaModel.LAYERS.get(kind))),
                0.3F);
    }

    /** Squash the shape onto the hitbox, exactly as the bestiary does. */
    @Override
    protected void scale(FaunaEntity beast, PoseStack pose, float partialTick) {
        FaunaRules.Animal row = beast.animal();
        CreatureModel.Bounds shape = FaunaModel.bounds(row.kind());
        float across = row.width() * PIXELS / Math.max(shape.wide(), shape.deep());
        float up = row.height() * PIXELS / shape.tall();
        pose.scale(across, up, across);
        this.shadowRadius = row.width() * 0.6F;
    }

    @Override
    public ResourceLocation getTextureLocation(FaunaEntity beast) {
        String name = EntityType.getKey(beast.getType()).getPath();
        return SKINS.computeIfAbsent(name, key -> ResourceLocation.fromNamespaceAndPath(
                ElysiumBestiary.MODID, "textures/entity/" + key + ".png"));
    }
}
