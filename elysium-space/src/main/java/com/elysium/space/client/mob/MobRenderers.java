package com.elysium.space.client.mob;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.entity.mob.Moonfolk;
import com.elysium.space.registry.SpaceEntities;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** The mobs' model layers and renderers, called from SpaceClient's registration events. */
public final class MobRenderers {

    private MobRenderers() {
    }

    /** Mobs drawn by RiggedModel; cinderlings use vanilla's piglin model on their own geometry. */
    static final String[] RIGGED = {"moonfolk", "hollow_moonfolk", "void_crawler", "frost_raptor", "cinder_boar"};

    static ModelLayerLocation layer(String mob) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, mob), "main");
    }

    static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, "textures/entity/" + path + ".png");
    }

    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (String mob : RIGGED) {
            event.registerLayerDefinition(layer(mob), () -> Geometry.of(mob).layer);
        }
        event.registerLayerDefinition(layer("cinderling"), () -> Geometry.of("cinderling").layer);
    }

    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SpaceEntities.MOONFOLK.get(), c -> new RiggedRenderer<>(c, layer("moonfolk"),
                Geometry.of("moonfolk").rig, 0.5F, MobRenderers::moonfolkTexture));
        event.registerEntityRenderer(SpaceEntities.HOLLOW_MOONFOLK.get(), c -> new RiggedRenderer<>(c,
                layer("hollow_moonfolk"), Geometry.of("hollow_moonfolk").rig, 0.5F, e -> texture("hollow_moonfolk")));
        event.registerEntityRenderer(SpaceEntities.VOID_CRAWLER.get(), c -> new RiggedRenderer<>(c,
                layer("void_crawler"), Geometry.of("void_crawler").rig, 0.9F, e -> texture("void_crawler")));
        event.registerEntityRenderer(SpaceEntities.FROST_RAPTOR.get(), c -> new RiggedRenderer<>(c,
                layer("frost_raptor"), Geometry.of("frost_raptor").rig, 0.6F, e -> texture("frost_raptor")));
        event.registerEntityRenderer(SpaceEntities.CINDER_BOAR.get(), c -> new RiggedRenderer<>(c,
                layer("cinder_boar"), Geometry.of("cinder_boar").rig, 0.9F, e -> texture("cinder_boar")));
        event.registerEntityRenderer(SpaceEntities.CINDERLING.get(),
                c -> new CinderlingRenderer(c, layer("cinderling"), texture("cinderling")));
        event.registerEntityRenderer(SpaceEntities.CINDERLING_BRUTE.get(),
                c -> new CinderlingRenderer(c, layer("cinderling"), texture("cinderling_brute")));
        event.registerEntityRenderer(SpaceEntities.RIME_SPIT.get(), RimeSpitRenderer::new);
    }

    /** The professions Stellaris painted a moonfolk for; any other wears the plain texture. */
    static final Set<String> PAINTED = Set.of("armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman",
            "fletcher", "leatherworker", "librarian", "mason", "shepherd", "toolsmith", "weaponsmith");
    private static final Map<VillagerProfession, ResourceLocation> BY_PROFESSION = new HashMap<>();

    static ResourceLocation moonfolkTexture(Moonfolk entity) {
        return BY_PROFESSION.computeIfAbsent(entity.getVillagerData().getProfession(), profession -> {
            ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            return id != null && "minecraft".equals(id.getNamespace()) && PAINTED.contains(id.getPath())
                    ? texture("moonfolk/" + id.getPath()) : texture("moonfolk");
        });
    }
}
