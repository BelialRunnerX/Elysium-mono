package com.elysium.mobs.client;

import com.elysium.mobs.ElysiumMobs;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

/**
 * The layer each family's baked model is filed under.
 *
 * One constant per model, referenced by both the renderer that bakes it and the
 * registration that supplies it. Named constants rather than building the
 * location at each site, because the two have to match exactly and a typo in
 * one of them produces "missing model layer" at runtime rather than at compile
 * time — the only class of bug in this file, and this removes it.
 */
public final class ElysiumMobLayers {

    private ElysiumMobLayers() {
    }

    public static final ModelLayerLocation SCAVENGER = layer("scavenger");
    public static final ModelLayerLocation REAVER = layer("reaver");
    public static final ModelLayerLocation WHISPER = layer("whisper");
    public static final ModelLayerLocation DRONE = layer("drone");
    public static final ModelLayerLocation LICTOR = layer("lictor");
    public static final ModelLayerLocation ADEPT = layer("adept");
    public static final ModelLayerLocation CHOIR = layer("choir");
    public static final ModelLayerLocation PRAETOR = layer("praetor");

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(
                ResourceLocation.fromNamespaceAndPath(ElysiumMobs.MODID, name), "main");
    }
}
