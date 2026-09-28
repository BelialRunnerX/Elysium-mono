package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.BeastEntity;
import com.elysium.bestiary.rules.BeastRules;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * One renderer, and the hitbox decides how big the thing looks.
 *
 * <p>Three shapes and nine beasts: the shape comes from the family, the
 * texture from the id, and the <em>size</em> from {@link BeastRules.Beast#width()}
 * and {@link BeastRules.Beast#height()} — the same two numbers the game builds
 * the hitbox out of. Nothing here is allowed to choose a scale, because a
 * scale anybody chose is a scale that stops matching the moment somebody
 * edits a row.
 */
public class BeastRenderer extends MobRenderer<BeastEntity, BeastModel> {

    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    /** How many pixels the game counts to a block. */
    public static final float PIXELS = 16.0F;

    public BeastRenderer(EntityRendererProvider.Context context, String family) {
        super(context, new BeastModel(context.bakeLayer(BeastModel.LAYERS.get(family)), family),
                0.4F);
    }

    /**
     * Squash the shape onto the hitbox.
     *
     * <p>Height exactly, because a mob whose head is above its own hitbox is a
     * mob you cannot hit in the head. Width and depth by the same factor, off
     * whichever of the two the shape is wider in, so nothing ever pokes out
     * sideways — a beast narrower than its shape ends up with room to spare
     * rather than with an elbow through a wall.
     */
    @Override
    protected void scale(BeastEntity beast, PoseStack pose, float partialTick) {
        BeastRules.Beast row = beast.beast();
        CreatureModel.Bounds shape = BeastModel.bounds(row.family());
        float across = row.width() * PIXELS / Math.max(shape.wide(), shape.deep());
        float up = row.height() * PIXELS / shape.tall();
        pose.scale(across, up, across);
        this.shadowRadius = row.width() * 0.6F;
    }

    @Override
    public ResourceLocation getTextureLocation(BeastEntity beast) {
        String name = EntityType.getKey(beast.getType()).getPath();
        return SKINS.computeIfAbsent(name, key -> ResourceLocation.fromNamespaceAndPath(
                ElysiumBestiary.MODID, "textures/entity/" + key + ".png"));
    }
}
