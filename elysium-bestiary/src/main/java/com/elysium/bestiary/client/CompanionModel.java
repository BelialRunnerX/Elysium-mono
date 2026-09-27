package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.CompanionEntity;
import com.elysium.bestiary.rules.FaunaRules;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * All eleven companions on the fauna's forager shape.
 *
 * <p>Its own class rather than reusing {@link FaunaModel} directly, because a
 * model is typed to the thing it draws and a companion is not an animal. The
 * <em>boxes</em> are the fauna's, which is the part worth sharing: a companion
 * is a small thing that follows you about, which is what a forager already is,
 * and eleven bespoke models would be eleven more places for a part to be named
 * wrong and a walk cycle to fall out of step.
 */
public class CompanionModel extends CreatureModel<CompanionEntity> {

    /** The one layer every companion is drawn on. */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ElysiumBestiary.MODID,
                    "companion"), "main");

    /** The boxes: the forager's, borrowed whole. */
    public static List<Part> shape() {
        return FaunaModel.SHAPES.get(FaunaRules.FORAGER);
    }

    /** Built once, from those. */
    public static LayerDefinition createLayer() {
        return CreatureModel.createLayer(shape());
    }

    public CompanionModel(ModelPart root) {
        super(root);
    }

    /**
     * What it does while it is standing there.
     *
     * <p>Nothing beyond the walk cycle the base already runs. A companion's
     * character is the one thing it does for you, which happens in the world
     * rather than in its pose.
     */
    @Override
    protected void idle(CompanionEntity companion, float age, float limbSwingAmount) {
    }
}
