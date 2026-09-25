import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.commands.Commands;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import java.lang.reflect.Field;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Load our data the way the game loads it, without the game.
 *
 * <h2>Why</h2>
 *
 * Four data files shipped in one jar that were valid JSON, named files that
 * existed, and failed the moment a player pressed Create World — because that
 * is when {@link RegistryDataLoader} first runs our files through the real
 * codecs. Nothing we could check by reading would have found them, and finding
 * them meant asking Dylan to press a button and send back a log.
 *
 * <p>So: boot the game's registries outside the launcher, put stand-ins in for
 * the blocks and items our code would have registered, build a resource
 * manager over vanilla's data pack and ours, and call the same loader with the
 * same registry list. Every error it reports is an error the game would have
 * reported, named by file.
 *
 * <h2>What it cannot do</h2>
 *
 * It does not run our Java: that needs FML. So NeoForge's own registries —
 * biome modifiers among them — are not loaded here, and neither is anything
 * that only exists once our mod's constructor has run. The static checks cover
 * those. What this covers is every vanilla datapack registry, which is where
 * three of the four were.
 */
public final class LoadTest {

    private LoadTest() {
    }

    private static int failures;

    public static void main(String[] args) throws Exception {
        Path ids = Path.of(args[0]);
        // How many of our recipes the loader is expected to come back with.
        // A recipe manager drops what it cannot parse without failing, so
        // without a number to check against, every one of ours could vanish
        // and this harness would print a cheerful total made of vanilla's.
        int expected = Integer.parseInt(args[1]);
        List<Path> resources = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            resources.add(Path.of(args[i]));
        }

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        int stubbed = stub(ids);

        List<PackResources> packs = new ArrayList<>();
        packs.add(vanilla());
        // NeoForge ships a data pack of its own — the neoforge: tags among
        // other things — and the game always has it. Without it our files
        // that build on those tags look broken and are not.
        String modded = System.getProperty("elysium.moddedPack", "");
        for (String jar : modded.split(java.io.File.pathSeparator)) {
            if (jar.isBlank()) {
                continue;
            }
            packs.add(new TagsOnly(
                    new net.minecraft.server.packs.FilePackResources.FileResourcesSupplier(
                            Path.of(jar)).openPrimary(new PackLocationInfo(
                                    "modded/" + Path.of(jar).getFileName(), null,
                                    PackSource.BUILT_IN, Optional.empty()))));
        }
        int index = 0;
        for (Path resource : resources) {
            packs.add(new PathPackResources(
                    new PackLocationInfo("elysium/" + (index++), null, PackSource.BUILT_IN,
                            Optional.empty()),
                    resource));
        }

        try (MultiPackResourceManager manager =
                     new MultiPackResourceManager(PackType.SERVER_DATA, packs)) {
            LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();

            // 1. The worldgen registries: biomes, features, structures, and
            //    the dimensions built out of them.
            int entries = 0;
            try {
                RegistryAccess.Frozen loaded = RegistryDataLoader.load(manager,
                        layers.getAccessForLoading(RegistryLayer.WORLDGEN),
                        RegistryDataLoader.WORLDGEN_REGISTRIES);
                layers = layers.replaceFrom(RegistryLayer.WORLDGEN, loaded);
                for (RegistryDataLoader.RegistryData<?> data
                        : RegistryDataLoader.WORLDGEN_REGISTRIES) {
                    entries += loaded.registryOrThrow(cast(data.key())).size();
                }
            } catch (Throwable trouble) {
                failures++;
                System.out.println("WORLDGEN REGISTRIES FAILED TO LOAD");
                report(trouble, 0);
            }

            // 2. Loot: tables, predicates and item modifiers, which in this
            //    version are registries of their own and are where most of
            //    our data files actually live.
            int loot = 0;
            try {
                LayeredRegistryAccess<RegistryLayer> withLoot =
                        ReloadableServerRegistries.reload(layers, manager, Runnable::run).join();
                layers = withLoot;
                loot = withLoot.compositeAccess()
                        .registryOrThrow(net.minecraft.core.registries.Registries.LOOT_TABLE).size();
            } catch (Throwable trouble) {
                failures++;
                System.out.println("LOOT FAILED TO LOAD");
                report(trouble, 0);
            }

            // 3. Everything reloadable: recipes, advancements, tags. A tag
            //    naming something that does not exist fails here, which is
            //    the other half of what Create World does.
            int recipes = 0;
            int ours = 0;
            try {
                ReloadableServerResources server = ReloadableServerResources.loadResources(
                        manager, layers, FeatureFlags.REGISTRY.allFlags(),
                        Commands.CommandSelection.DEDICATED, 2, Runnable::run, Runnable::run)
                        .join();
                recipes = server.getRecipeManager().getRecipes().size();
                // Count ours separately: a recipe manager quietly drops what
                // it cannot parse, so a total that includes vanilla's eleven
                // hundred would hide every one of ours going missing.
                ours = (int) server.getRecipeManager().getRecipes().stream()
                        .filter(holder -> holder.id().getNamespace().startsWith("elysium"))
                        .count();
            } catch (Throwable trouble) {
                failures++;
                System.out.println("RECIPES AND TAGS FAILED TO LOAD");
                report(trouble, 0);
            }

            if (ours < expected) {
                failures++;
                System.out.println("RECIPES WENT MISSING: " + expected + " of ours were "
                        + "expected and " + ours + " came back. A recipe the loader "
                        + "cannot parse is dropped with a message and no failure, so "
                        + "look above for it.");
            }
            System.out.println("loaded " + entries + " worldgen entries, " + loot
                    + " loot tables and " + recipes + " recipes (" + ours
                    + " ours), with " + stubbed
                    + " stubbed ids");
        } catch (Throwable trouble) {
            failures++;
            System.out.println("THE HARNESS ITSELF FAILED");
            report(trouble, 0);
        }

        if (failures > 0) {
            System.exit(1);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ResourceKey<? extends Registry<T>> cast(ResourceKey<?> key) {
        return (ResourceKey<? extends Registry<T>>) key;
    }

    /**
     * Print a failure and everything under it.
     *
     * The loader wraps one exception per broken file inside one outer error,
     * so the outer message says nothing useful and the causes are the report.
     */
    private static void report(Throwable trouble, int depth) {
        String pad = "  ".repeat(depth + 1);
        System.out.println(pad + trouble.getClass().getSimpleName() + ": " + trouble.getMessage());
        for (Throwable suppressed : trouble.getSuppressed()) {
            report(suppressed, depth + 1);
        }
        if (trouble.getCause() != null && trouble.getCause() != trouble) {
            report(trouble.getCause(), depth + 1);
        }
    }

    // ------------------------------------------------------------------
    // Stand-ins
    // ------------------------------------------------------------------

    private static int stub(Path ids) throws Exception {
        known = knownProperties();
        int count = 0;
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        MappedRegistry<Item> items = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        MappedRegistry<EntityType<?>> entities =
                (MappedRegistry<EntityType<?>>) BuiltInRegistries.ENTITY_TYPE;
        MappedRegistry<MobEffect> effects =
                (MappedRegistry<MobEffect>) BuiltInRegistries.MOB_EFFECT;
        MappedRegistry<net.minecraft.world.level.levelgen.structure.StructureType<?>>
                structures = (MappedRegistry<net.minecraft.world.level.levelgen
                        .structure.StructureType<?>>) BuiltInRegistries.STRUCTURE_TYPE;
        structures.unfreeze();
        MappedRegistry<PlacementModifierType<?>> placements =
                (MappedRegistry<PlacementModifierType<?>>) BuiltInRegistries.PLACEMENT_MODIFIER_TYPE;
        MappedRegistry<net.minecraft.core.component.DataComponentType<?>> components =
                (MappedRegistry<net.minecraft.core.component.DataComponentType<?>>)
                        BuiltInRegistries.DATA_COMPONENT_TYPE;
        components.unfreeze();
        placements.unfreeze();
        blocks.unfreeze();
        items.unfreeze();
        entities.unfreeze();
        effects.unfreeze();

        for (String line : Files.readAllLines(ids)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t");
            ResourceLocation id = ResourceLocation.parse(parts[1]);
            switch (parts[0]) {
                case "block" -> {
                    if (!blocks.containsKey(id)) {
                        Registry.register(blocks, id,
                                stubBlock(parts.length > 2 ? parts[2] : ""));
                        count++;
                    }
                }
                case "item" -> {
                    if (!items.containsKey(id)) {
                        Registry.register(items, id, new Item(new Item.Properties()));
                        count++;
                    }
                }
                case "entity_type" -> {
                    if (!entities.containsKey(id)) {
                        Registry.register(entities, id, EntityType.Builder
                                .<Entity>of((type, level) -> null, MobCategory.MISC)
                                .build(id.toString()));
                        count++;
                    }
                }
                case "placement_modifier_type" -> {
                    if (!BuiltInRegistries.PLACEMENT_MODIFIER_TYPE.containsKey(id)) {
                        Registry.register(placements, id, passThrough());
                        count++;
                    }
                }
                case "data_component_type" -> {
                    if (!BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(id)) {
                        Registry.register(components, id,
                                net.minecraft.core.component.DataComponentType.builder()
                                        .persistent(com.mojang.serialization.Codec.unit(
                                                net.minecraft.util.Unit.INSTANCE))
                                        .build());
                        count++;
                    }
                }
                case "structure_type" -> {
                    if (!BuiltInRegistries.STRUCTURE_TYPE.containsKey(id)) {
                        Registry.register(structures, id,
                                StubStructure.stubType());
                        count++;
                    }
                }
                case "mob_effect" -> {
                    if (!effects.containsKey(id)) {
                        Registry.register(effects, id,
                                new MobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF) {
                                });
                        count++;
                    }
                }
                default -> {
                }
            }
        }
        structures.freeze();
        components.freeze();
        placements.freeze();
        blocks.freeze();
        items.freeze();
        entities.freeze();
        effects.freeze();
        return count;
    }

    /**
     * A pack with its loot tables hidden and nothing else.
     *
     * NeoForge's data pack is wanted because our files build on its tags and
     * its damage types, and without them they look broken. But it also
     * replaces vanilla's leaf loot tables with ones using a loot condition
     * NeoForge registers in Java, which this harness does not run — so every
     * leaf in the game fails to load and drowns the real report.
     *
     * <p>One exclusion, then, rather than an allow-list: everything that is
     * pure data comes through, and the one part that needs code does not.
     */
    private static final class TagsOnly implements PackResources {

        private final PackResources inner;

        private TagsOnly(PackResources inner) {
            this.inner = inner;
        }

        private static boolean wanted(ResourceLocation id) {
            return !id.getPath().startsWith("loot_table/");
        }

        @Override
        public net.minecraft.server.packs.resources.IoSupplier<java.io.InputStream>
                getRootResource(String... path) {
            return inner.getRootResource(path);
        }

        @Override
        public net.minecraft.server.packs.resources.IoSupplier<java.io.InputStream>
                getResource(PackType type, ResourceLocation id) {
            return wanted(id) ? inner.getResource(type, id) : null;
        }

        @Override
        public void listResources(PackType type, String namespace, String path,
                                  PackResources.ResourceOutput output) {
            if (path.startsWith("loot_table")) {
                return;
            }
            inner.listResources(type, namespace, path, output);
        }

        @Override
        public java.util.Set<String> getNamespaces(PackType type) {
            return inner.getNamespaces(type);
        }

        @Override
        public <T> T getMetadataSection(
                net.minecraft.server.packs.metadata.MetadataSectionSerializer<T> serializer)
                throws java.io.IOException {
            return inner.getMetadataSection(serializer);
        }

        @Override
        public PackLocationInfo location() {
            return inner.location();
        }

        @Override
        public void close() {
            inner.close();
        }
    }

    // ------------------------------------------------------------------
    // Blocks with the properties the real ones have
    // ------------------------------------------------------------------
    //
    // A plain Block has no properties, so a loot table asking whether a slab
    // is a double slab fails against a stand-in — which looks exactly like a
    // broken loot table and is not one. The blockstate file already says what
    // the properties are; all that is missing is a property object with the
    // right name and values, and the game has one of almost every shape in
    // BlockStateProperties.

    /**
     * Which properties the game already has, by the name they use in data.
     *
     * Filled after bootstrap, not at class load: touching BlockStateProperties
     * before Bootstrap.bootStrap() throws "Not bootstrapped", and a static
     * initializer runs before main gets a chance to boot anything.
     */
    private static Map<String, List<Property<?>>> known = Map.of();

    /** The properties of the block currently being constructed, since the
     *  state definition is built inside Block's own constructor. */
    private static final ThreadLocal<List<Property<?>>> BUILDING =
            ThreadLocal.withInitial(List::of);

    private static Map<String, List<Property<?>>> knownProperties() {
        Map<String, List<Property<?>>> known = new HashMap<>();
        for (Field field : BlockStateProperties.class.getFields()) {
            if (!Property.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                Property<?> property = (Property<?>) field.get(null);
                known.computeIfAbsent(property.getName(), key -> new ArrayList<>())
                        .add(property);
            } catch (IllegalAccessException ignored) {
                // A field we cannot read is a property we cannot offer.
            }
        }
        for (List<Property<?>> candidates : known.values()) {
            candidates.sort(Comparator.comparingInt(one -> one.getPossibleValues().size()));
        }
        return known;
    }

    /**
     * The smallest property called {@code name} that can take all these values.
     *
     * Smallest because "age" is a dozen different properties in vanilla and
     * the narrowest one that fits is the one the real block almost certainly
     * uses; and because if none fits, saying so is better than picking one
     * that silently cannot represent what the data asks for.
     */
    private static Property<?> propertyFor(String name, List<String> values) {
        for (Property<?> candidate : known.getOrDefault(name, List.of())) {
            if (values.stream().allMatch(value -> nameOf(candidate, value) != null)) {
                return candidate;
            }
        }
        return null;
    }

    private static <T extends Comparable<T>> Object nameOf(Property<T> property, String value) {
        return property.getValue(value).orElse(null);
    }

    private static Block stubBlock(String described) {
        List<Property<?>> properties = new ArrayList<>();
        for (String piece : described.split("\\|")) {
            if (piece.isBlank() || !piece.contains("=")) {
                continue;
            }
            String[] halves = piece.split("=", 2);
            List<String> values = List.of(halves[1].split(","));
            Property<?> property = propertyFor(halves[0], values);
            if (property != null) {
                properties.add(property);
            }
        }
        BUILDING.set(properties);
        try {
            return new Stub(BlockBehaviour.Properties.of());
        } finally {
            BUILDING.set(List.of());
        }
    }

    /** A block that has whatever properties it was told to have. */
    private static final class Stub extends Block {

        private final List<Property<?>> mine = BUILDING.get();

        private Stub(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            // Read from the thread local rather than from the field: this runs
            // inside Block's constructor, before any field of ours is assigned.
            for (Property<?> property : BUILDING.get()) {
                builder.add(property);
            }
        }
    }

    /**
     * A placement modifier that places exactly where it was told to.
     *
     * Our own placement modifiers are registered by code this harness never
     * runs, so their files would fail to parse and everything referring to
     * them would come back unbound — which reads exactly like a real bug. A
     * stand-in that accepts any config and changes nothing lets the rest of
     * the file be checked, which is the part we are here for. What the
     * modifier actually does is the harness in its own module's business.
     */
    private static PlacementModifierType<?> passThrough() {
        class Anywhere extends PlacementModifier {
            private PlacementModifierType<?> kind;

            @Override
            public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random,
                                                 BlockPos pos) {
                return Stream.of(pos);
            }

            @Override
            public PlacementModifierType<?> type() {
                return kind;
            }
        }
        Anywhere anywhere = new Anywhere();
        PlacementModifierType<?> kind = () -> (MapCodec) MapCodec.unit(anywhere);
        anywhere.kind = kind;
        return kind;
    }

    /** The game's own data pack, which lives inside the jar we compile against. */
    private static PackResources vanilla() {
        return ServerPacksSource.createVanillaPackSource();
    }

    /**
     * A structure that parses and generates nothing.
     *
     * <p>Our structures are registered in Java, and Java is the one thing
     * this harness cannot run. Without a stand-in, every structure file of
     * ours fails with "unknown registry key" and every structure set naming
     * one comes back unbound — a wall of errors that looks exactly like a
     * real bug in files that are perfectly good.
     *
     * <p>It reads the settings every structure has and ignores everything
     * else in the file, which is the point: what is being checked here is
     * that the placement, the biome tag and the spawn overrides are all
     * things the game will accept, not what the structure does with them.
     */
    private static final class StubStructure
            extends net.minecraft.world.level.levelgen.structure.Structure {

        static final com.mojang.serialization.MapCodec<StubStructure> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(
                        instance -> instance.group(settingsCodec(instance))
                                .apply(instance, StubStructure::new));

        /**
         * A fresh type for every stubbed id.
         *
         * <p>Fresh, and an anonymous class rather than a lambda, for one
         * reason each. A registry refuses the same value twice, so all the
         * stubbed types cannot be one object; and a lambda that captures
         * nothing is built once and handed back every time it is asked for,
         * which is the same object wearing a different name.
         *
         * <p>The cast is because {@code StructureType} is not covariant in
         * its codec: a {@code MapCodec<StubStructure>} is not a {@code
         * MapCodec<Structure>}, and nothing here ever encodes one.
         */
        @SuppressWarnings("unchecked")
        static net.minecraft.world.level.levelgen.structure.StructureType<?>
                stubType() {
            return new net.minecraft.world.level.levelgen.structure
                    .StructureType<>() {
                @Override
                public com.mojang.serialization.MapCodec<
                        net.minecraft.world.level.levelgen.structure.Structure>
                        codec() {
                    return (com.mojang.serialization.MapCodec<
                            net.minecraft.world.level.levelgen.structure.Structure>)
                            (com.mojang.serialization.MapCodec<?>) CODEC;
                }
            };
        }

        StubStructure(StructureSettings settings) {
            super(settings);
        }

        @Override
        public java.util.Optional<GenerationStub> findGenerationPoint(
                GenerationContext context) {
            return java.util.Optional.empty();
        }

        @Override
        public net.minecraft.world.level.levelgen.structure.StructureType<?> type() {
            return stubType();
        }
    }
}
