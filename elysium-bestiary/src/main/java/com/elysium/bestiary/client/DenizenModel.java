package com.elysium.bestiary.client;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.DenizenEntity;
import com.elysium.bestiary.rules.BeastRules;
import com.elysium.bestiary.rules.DenizenRules;
import com.elysium.bestiary.rules.MythRules;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Twenty-eight creatures on shapes this module already has.
 *
 * <p>No new boxes. A ring is drawn with one of the beasts' three families or
 * one of the fauna's four kinds — whichever it reads as — and every member of
 * it wears that same shape with its own texture and the size its hitbox was
 * built from. That is not a saving, it is the claim: things the same distance out look alike, and a
 * frost scorpion drawn as a bird is not a version of a scorpion, it is a second
 * creature wearing a scorpion's name.
 *
 * <p>{@link DenizenRules#faultsInShapes} is what stops a kindred being given a
 * shape that does not exist. This class trusts that and does not check again:
 * two places deciding the same thing is how they come to disagree.
 */
public class DenizenModel extends CreatureModel<DenizenEntity> {

    /** One layer per shape a kindred can wear. */
    public static final Map<String, ModelLayerLocation> LAYERS = layers();

    private static Map<String, ModelLayerLocation> layers() {
        Map<String, ModelLayerLocation> made = new LinkedHashMap<>();
        for (String form : MythRules.forms()) {
            made.put(form, new ModelLayerLocation(
                    ResourceLocation.fromNamespaceAndPath(ElysiumBestiary.MODID,
                            "vale_" + form), "main"));
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

    public DenizenModel(ModelPart root) {
        super(root);
    }

    /**
     * What it does while it is standing there.
     *
     * <p>Nothing beyond the walk cycle the base already runs. A creature's
     * character here is its trait, which happens in the world rather than in
     * its pose, and an idle animation per creature would be thirteen more
     * things to keep in step with thirteen rows.
     */
    @Override
    protected void idle(DenizenEntity one, float age, float limbSwingAmount) {
    }
}
