package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.KinEntity;
import com.elysium.bestiary.registry.KinEntities;
import com.elysium.bestiary.rules.KindredRules;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * One renderer for all twenty-seven, and the hitbox decides how big each looks.
 *
 * <p>The shape comes from the kindred, the texture from the id, and the size
 * from the two numbers the game built the hitbox out of — the same arrangement
 * {@link BeastRenderer} and {@link MythRenderer} use, and for the same reason.
 * Nothing here chooses a scale, because a scale anybody chose is a scale that
 * stops matching the moment somebody edits a row.
 *
 * <p>The one thing this renderer does that the others do not is refuse to draw
 * something that is pretending not to be there. That belongs here rather than
 * in the trait, because whether a player can see a thing is a question about
 * the screen.
 */
public class KinRenderer extends MobRenderer<KinEntity, KinModel> {

    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    /** How many pixels the game counts to a block. */
    public static final float PIXELS = 16.0F;

    /** How much smaller a young one is than the row it belongs to. */
    public static final float YOUNG = 0.5F;

    public KinRenderer(EntityRendererProvider.Context context, String form) {
        super(context, new KinModel(context.bakeLayer(KinModel.LAYERS.get(form)), form),
                0.4F);
    }

    @Override
    protected void scale(KinEntity one, PoseStack pose, float partialTick) {
        KindredRules.Kin row = one.creature();
        Bounds shape = KinModel.bounds(KindredRules.shapeOf(row));
        float[] size = KinEntities.sizeOf(row);
        float small = one.young() ? YOUNG : 1.0F;
        float across = size[0] * PIXELS / Math.max(shape.wide(), shape.deep()) * small;
        float up = size[1] * PIXELS / shape.tall() * small;
        pose.scale(across, up, across);
        this.shadowRadius = size[0] * 0.6F * small;
    }

    /**
     * Whether it is on the screen at all.
     *
     * <p>A disguise that the renderer ignores is not a disguise, it is a note in
     * a table. The entity says whether it is pretending and this is the only
     * place that reads it.
     */
    @Override
    public void render(KinEntity one, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        if (one.disguised() || one.under()) {
            return;
        }
        super.render(one, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(KinEntity one) {
        String name = EntityType.getKey(one.getType()).getPath();
        return SKINS.computeIfAbsent(name, key -> ResourceLocation.fromNamespaceAndPath(
                ElysiumBestiary.MODID, "textures/entity/" + key + ".png"));
    }
}
