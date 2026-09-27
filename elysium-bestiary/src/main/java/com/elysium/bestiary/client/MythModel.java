package com.elysium.bestiary.client;

import com.elysium.bestiary.entity.MythEntity;
import com.elysium.bestiary.rules.BeastRules;
import com.elysium.bestiary.rules.MythRules;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eight myths on shapes this module already has.
 *
 * <p>No new boxes. A myth is given one of the beasts' three families or one of
 * the fauna's four kinds — whichever it reads as — and is drawn with that
 * shape's parts, its own texture, and the size its hitbox was built from. The
 * alternative was eight bespoke models, which is eight more places for a part
 * to be named wrong and a walk cycle to fall out of step.
 *
 * <p>{@link MythRules#faultsInForms} is what stops a myth being given a shape
 * that does not exist. This class trusts that and does not check again: two
 * places deciding the same thing is how they come to disagree.
 */
public class MythModel extends CreatureModel<MythEntity> {

    /** One layer per shape a myth can wear. */
    public static final Map<String, ModelLayerLocation> LAYERS = layers();

    private static Map<String, ModelLayerLocation> layers() {
        Map<String, ModelLayerLocation> made = new LinkedHashMap<>();
        for (String form : MythRules.forms()) {
            made.put(form, new ModelLayerLocation(
                    ResourceLocation.fromNamespaceAndPath(
                            com.elysium.bestiary.ElysiumBestiary.MODID,
                            "myth_" + form), "main"));
        }
        return Map.copyOf(made);
    }

    /** The parts a shape is built from, wherever this module already keeps them. */
    public static List<Part> shape(String form) {
        List<Part> beastly = BeastModel.SHAPES.get(form);
        if (beastly != null) {
            return beastly;
        }
        List<Part> animal = FaunaModel.SHAPES.get(form);
        return animal == null ? BeastModel.SHAPES.get(BeastRules.PROWLER) : animal;
    }

    /** The boxes for one shape. */
    public static LayerDefinition createLayer(String form) {
        return CreatureModel.createLayer(shape(form));
    }

    public MythModel(ModelPart root) {
        super(root);
    }

    /**
     * What it does while it is standing there.
     *
     * <p>Nothing beyond the walk cycle the base already runs. A myth's
     * character is its bane, which happens in the world rather than in its
     * pose, and an idle animation per creature would be eight more things to
     * keep in step with eight rows.
     */
    @Override
    protected void idle(MythEntity myth, float age, float limbSwingAmount) {
    }
}
