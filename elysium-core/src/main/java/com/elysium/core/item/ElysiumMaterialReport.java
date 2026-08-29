package com.elysium.core.item;

import com.elysium.core.Elysium;
import com.elysium.lib.item.ElysiumGearMaterial;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What Elysium found, and what it missed.
 *
 * <h2>The problem this solves</h2>
 *
 * The shipped material table cannot be complete — there is no list of every
 * metal every mod will ever add. So some pack will always have an ore Elysium
 * has no gear for, and the failure mode without this class is <em>silence</em>:
 * the player sees Elysium hammers for iron and steel, none for whatever the
 * pack's signature metal is, and has no way to tell whether that was a
 * deliberate balance decision or an oversight.
 *
 * So on every reload this scans {@code c:ingots/*} for metals nothing here
 * covers and says so, once, in the log. {@code /elysium materials} prints the
 * same thing on demand, along with which of the shipped materials actually
 * resolved.
 *
 * <h2>Why a report rather than generating the gear</h2>
 *
 * Because gear cannot be generated here. Tags load long after item
 * registration, so by the time this can see that a metal exists it is far too
 * late to give it a hammer. Adding the name to the config and restarting is the
 * action, and this is what tells you to take it — the config comment points
 * back here for the spelling.
 */
@EventBusSubscriber(modid = Elysium.MODID)
public final class ElysiumMaterialReport {

    private ElysiumMaterialReport() {
    }

    private static final String INGOT_PREFIX = "ingots/";

    /**
     * Metals in {@code c:ingots/*} that no Elysium material covers.
     *
     * Recomputed on each reload rather than cached across them, because a
     * datapack can change what a tag contains without the game restarting.
     */
    private static List<String> uncovered = List.of();

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        uncovered = findUncovered();
        if (uncovered.isEmpty()) {
            return;
        }
        Elysium.LOGGER.info("Elysium has no gear for {} ingot type(s) present in this pack: {}",
                uncovered.size(), String.join(", ", uncovered));
        Elysium.LOGGER.info("Add them under [materials] extra_materials in elysium-common.toml "
                + "and restart to give them gear. Format: name, name:tier or name:tier:element.");
    }

    /**
     * Every {@code c:ingots/<name>} with entries that no material claims.
     *
     * Two things are deliberately not treated as uncovered. A tag with nothing
     * in it is not a metal anyone has — several mods declare tags they do not
     * fill. And a name Elysium already registered is covered whether or not the
     * ingredient turned up, because the gear exists either way; that case is
     * reported separately by {@link #missing()} as a material with no supplier.
     */
    public static List<String> findUncovered() {
        Set<String> covered = new LinkedHashSet<>();
        for (ElysiumGearMaterial material : ElysiumGearMaterial.all()) {
            ResourceLocation tag = material.getIngredientTagId();
            if (tag.getNamespace().equals("c") && tag.getPath().startsWith(INGOT_PREFIX)) {
                covered.add(tag.getPath().substring(INGOT_PREFIX.length()));
            }
        }

        List<String> found = new ArrayList<>();
        for (TagKey<Item> tag : BuiltInRegistries.ITEM.getTagNames().toList()) {
            ResourceLocation id = tag.location();
            if (!id.getNamespace().equals("c") || !id.getPath().startsWith(INGOT_PREFIX)) {
                continue;
            }
            String name = id.getPath().substring(INGOT_PREFIX.length());
            if (covered.contains(name)) {
                continue;
            }
            boolean populated = BuiltInRegistries.ITEM.getTag(tag)
                    .map(entries -> entries.size() > 0)
                    .orElse(false);
            if (populated) {
                found.add(name);
            }
        }
        found.sort(String::compareTo);
        return List.copyOf(found);
    }

    /** The last computed list, for the command. */
    public static List<String> uncovered() {
        return uncovered;
    }

    /** Registered materials whose ingredient nothing in this pack supplies. */
    public static List<String> missing() {
        List<String> found = new ArrayList<>();
        for (ElysiumGearMaterial material : ElysiumGearMaterial.all()) {
            if (!material.isAvailable()) {
                found.add(material.getPath());
            }
        }
        return found;
    }

    /** Registered materials something in this pack can actually supply. */
    public static List<String> present() {
        List<String> found = new ArrayList<>();
        for (ElysiumGearMaterial material : ElysiumGearMaterial.all()) {
            if (material.isAvailable()) {
                found.add(material.getPath());
            }
        }
        return found;
    }
}
