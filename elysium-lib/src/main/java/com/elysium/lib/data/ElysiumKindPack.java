package com.elysium.lib.data;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.item.ElysiumKindTags;
import com.elysium.lib.item.ElysiumLootCategory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A data pack the library writes itself: the vanilla kind tags for every
 * piece of gear the pack registers.
 *
 * <p>Built each time the datapacks are assembled, which is after every item
 * exists: the item registry is walked, every item in one of the pack's own
 * namespaces is asked what kind of gear it is ({@link ElysiumLootCategory},
 * the one resolver every other system asks), and {@link ElysiumKindTags} says
 * which of vanilla's lists that kind belongs on. The result is served as
 * {@code data/<namespace>/tags/item/<path>.json} files that merge into
 * vanilla's own, exactly as a file in a jar would, so nothing downstream —
 * enchantments, trims, the recipe browser, another mod's tag queries — knows
 * the files were never on a disk.
 *
 * <p>Why not files: the gear is registered by twenty modules from their own
 * rules tables — a material table, a tier table, an armoury's part list — and
 * a tag file per module that repeated those tables would be wrong the first
 * time one of them changed, silently, with the only symptom a basin that says
 * "Not on this". The pack cannot drift because it reads nothing but the
 * registry.
 *
 * <p>The pack is required and fixed, so it cannot be turned off in the
 * datapack screen; hidden, so that screen does not list it as something to
 * turn off.
 */
public final class ElysiumKindPack implements PackResources {

    /** The mod bus hands the finder in; the pack is made when the list is. */
    public static void addFinder(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(
                    new PackLocationInfo(ElysiumKindTags.PACK_ID, Component.literal("Elysium kind tags"),
                            PackSource.BUILT_IN, Optional.empty()),
                    new Pack.ResourcesSupplier() {
                        @Override
                        public PackResources openPrimary(PackLocationInfo info) {
                            return new ElysiumKindPack(info);
                        }

                        @Override
                        public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                            return new ElysiumKindPack(info);
                        }
                    },
                    PackType.SERVER_DATA,
                    new PackSelectionConfig(true, Pack.Position.TOP, true));
            if (pack != null) {
                consumer.accept(pack);
            }
        });
    }

    private final PackLocationInfo location;
    private final Map<ResourceLocation, byte[]> files;

    private ElysiumKindPack(PackLocationInfo location) {
        this.location = location;
        this.files = build();
    }

    /**
     * Every tag file, from the registry as it stands.
     *
     * <p>One file per tag, listing every item of the pack's that carries it;
     * sorted, so two reloads produce the same bytes and a diff of the pack
     * means something.
     */
    static Map<ResourceLocation, byte[]> build() {
        Map<String, Set<String>> members = new TreeMap<>();
        int tagged = 0;
        for (Map.Entry<ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            if (!id.getNamespace().startsWith(ElysiumKindTags.NAMESPACE_PREFIX)) {
                continue;
            }
            String kind = ElysiumLootCategory.of(entry.getValue());
            if (kind == null) {
                continue;
            }
            List<String> tags = ElysiumKindTags.tagsFor(kind);
            if (tags.isEmpty()) {
                continue;
            }
            tagged++;
            for (String tag : tags) {
                members.computeIfAbsent(tag, unused -> new LinkedHashSet<>()).add(id.toString());
            }
        }
        Map<ResourceLocation, byte[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> tag : members.entrySet()) {
            ResourceLocation tagId = ResourceLocation.parse(tag.getKey());
            JsonArray values = new JsonArray();
            for (String item : new TreeSet<>(tag.getValue())) {
                values.add(item);
            }
            JsonObject file = new JsonObject();
            file.addProperty("replace", false);
            file.add("values", values);
            out.put(ResourceLocation.fromNamespaceAndPath(tagId.getNamespace(),
                            ElysiumKindTags.TAG_FOLDER + "/" + tagId.getPath() + ".json"),
                    file.toString().getBytes(StandardCharsets.UTF_8));
        }
        ElysiumLib.LOGGER.info("Elysium kind tags: {} item(s) on {} tag(s)", tagged, out.size());
        return out;
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    @Nullable
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        byte[] bytes = type == PackType.SERVER_DATA ? files.get(id) : null;
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.SERVER_DATA) {
            return;
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<ResourceLocation, byte[]> file : files.entrySet()) {
            ResourceLocation id = file.getKey();
            if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) {
                byte[] bytes = file.getValue();
                output.accept(id, () -> new ByteArrayInputStream(bytes));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> namespaces = new LinkedHashSet<>();
        if (type == PackType.SERVER_DATA) {
            for (ResourceLocation id : files.keySet()) {
                namespaces.add(id.getNamespace());
            }
        }
        return namespaces;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        if (PackMetadataSection.TYPE.getMetadataSectionName().equals(serializer.getMetadataSectionName())) {
            return (T) new PackMetadataSection(Component.literal("The vanilla kind tags of Elysium's gear"),
                    SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
        }
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void close() {
    }
}
