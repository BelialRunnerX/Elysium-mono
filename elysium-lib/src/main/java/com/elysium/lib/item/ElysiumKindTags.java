package com.elysium.lib.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The vanilla item tags a kind of gear belongs to.
 *
 * <p>Vanilla's enchantments do not ask what class an item is; they ask what
 * tags it carries. Sharpness goes on {@code #minecraft:enchantable/sharp_weapon},
 * which is {@code #minecraft:swords} and {@code #minecraft:axes}, and those two
 * are lists of six items each. A sword that is not on the list is a sword the
 * infusion basin answers "Not on this" to, the arcane table offers nothing
 * for, and no enchanting mod recognises (found by hand at the basin,
 * 28 Sep 2026: every Elysium sword). The tags are datapack files, the gear is
 * registered by twenty modules from their own rules tables, and a file per
 * module that repeated those tables would drift from them the first time one
 * changed.
 *
 * <p>So the tags come from the same place every other question about gear is
 * answered: {@link ElysiumLootCategory} says what kind of thing an item is, in
 * the vocabulary of {@link ElysiumGearTree}, and this table says which tags a
 * kind carries. {@code com.elysium.lib.data.ElysiumKindPack} walks the item
 * registry at datapack load, asks both, and serves the result as a data pack.
 * Nothing is listed by name anywhere.
 *
 * <p>A kind without a row inherits its nearest ancestor's: a hammer is a
 * pickaxe, so a hammer carries {@code #minecraft:pickaxes}. A row lists the
 * tags in full — the vanilla tags nest, so {@code #minecraft:swords} is enough
 * for a sword to be sharp, on fire and looting, but an elytra is named in
 * three of vanilla's lists and needs all three.
 *
 * <p>The common tags ({@code c:}) are NeoForge's, for the other mods that read
 * them; they are the ones its jar ships for 1.21.1, not the older per-piece
 * armour tags.
 */
public final class ElysiumKindTags {

    private ElysiumKindTags() {
    }

    /** Only items this pack registers are tagged; other mods' gear is theirs to tag. */
    public static final String NAMESPACE_PREFIX = "elysium";

    /** The one pack id, and the folder the tags are read from. */
    public static final String PACK_ID = "elysiumlib:kind_tags";
    public static final String TAG_FOLDER = "tags/item";

    public static final Map<String, List<String>> TAGS;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        // Blades. Everything vanilla hangs off #minecraft:swords: sharp_weapon,
        // sword, weapon, fire_aspect, durability, vanishing.
        for (String blade : new String[] {"sword", "katana", "machete", "dagger", "knife", "spear"}) {
            m.put(blade, List.of("minecraft:swords", "c:tools/melee_weapon"));
        }
        m.put("mace", List.of("minecraft:enchantable/mace", "minecraft:enchantable/weapon",
                "minecraft:enchantable/durability", "c:tools/mace", "c:tools/melee_weapon"));
        // A weapon that is none of the above still takes Smite and Bane and wears out.
        m.put("melee_weapon", List.of("minecraft:enchantable/weapon", "minecraft:enchantable/durability",
                "c:tools/melee_weapon"));
        // Harvest tools; hammer, prospector_hammer, saw and excavator inherit.
        m.put("axe", List.of("minecraft:axes", "c:tools/mining_tool", "c:tools/melee_weapon"));
        m.put("pickaxe", List.of("minecraft:pickaxes", "c:tools/mining_tool"));
        m.put("shovel", List.of("minecraft:shovels", "c:tools/mining_tool"));
        m.put("hoe", List.of("minecraft:hoes", "c:tools/mining_tool"));
        m.put("sickle", List.of("minecraft:hoes", "c:tools/mining_tool"));
        m.put("mattock", List.of("minecraft:hoes", "minecraft:shovels", "c:tools/mining_tool"));
        m.put("paxel", List.of("minecraft:pickaxes", "minecraft:axes", "minecraft:shovels", "c:tools/mining_tool"));
        m.put("shears", List.of("minecraft:enchantable/mining", "minecraft:enchantable/durability", "c:tools/shear"));
        // Ranged and the rest of the hand.
        m.put("bow", List.of("minecraft:enchantable/bow", "minecraft:enchantable/durability",
                "c:tools/bow", "c:tools/ranged_weapon"));
        m.put("slingshot", List.of("minecraft:enchantable/bow", "minecraft:enchantable/durability",
                "c:tools/ranged_weapon"));
        m.put("crossbow", List.of("minecraft:enchantable/crossbow", "minecraft:enchantable/durability",
                "c:tools/crossbow", "c:tools/ranged_weapon"));
        m.put("trident", List.of("minecraft:enchantable/trident", "minecraft:enchantable/durability",
                "c:tools/spear", "c:tools/melee_weapon", "c:tools/ranged_weapon"));
        m.put("fishing_rod", List.of("minecraft:enchantable/fishing", "minecraft:enchantable/durability",
                "c:tools/fishing_rod"));
        m.put("shield", List.of("minecraft:enchantable/durability", "c:tools/shield"));
        // Armour. #minecraft:head_armor and its three siblings are what
        // enchantable/armor, trimmable_armor, durability and equippable read.
        m.put("helmet", List.of("minecraft:head_armor", "c:armors"));
        m.put("chestplate", List.of("minecraft:chest_armor", "c:armors"));
        m.put("leggings", List.of("minecraft:leg_armor", "c:armors"));
        m.put("boots", List.of("minecraft:foot_armor", "c:armors"));
        m.put("elytra", List.of("minecraft:enchantable/durability", "minecraft:enchantable/equippable",
                "minecraft:enchantable/vanishing"));
        m.put("arrow", List.of("minecraft:arrows"));
        TAGS = Collections.unmodifiableMap(m);
    }

    /**
     * The tags a kind carries: its own row, or the nearest ancestor's.
     *
     * @return empty for a kind with no row anywhere up its lineage (a curio,
     *     which Curios tags in its own vocabulary), and for a name the tree
     *     does not know
     */
    public static List<String> tagsFor(String kind) {
        for (String step : ElysiumGearTree.lineage(kind)) {
            List<String> tags = TAGS.get(step);
            if (tags != null) {
                return tags;
            }
        }
        return List.of();
    }

    /** The tag ids this table can ever produce, each once, in table order. */
    public static List<String> allTags() {
        List<String> out = new ArrayList<>();
        for (List<String> tags : TAGS.values()) {
            for (String tag : tags) {
                if (!out.contains(tag)) {
                    out.add(tag);
                }
            }
        }
        return out;
    }

    /**
     * What is wrong with the table, in words; empty when nothing is.
     *
     * <p>Every row names a kind the tree has; every tag is a namespaced id;
     * every leaf of the tree that is not a curio resolves to at least one tag,
     * so no piece of gear the tree can name is left out of vanilla's lists.
     */
    public static List<String> faults() {
        List<String> faults = new ArrayList<>();
        for (Map.Entry<String, List<String>> row : TAGS.entrySet()) {
            if (!ElysiumGearTree.exists(row.getKey())) {
                faults.add("the row \"" + row.getKey() + "\" names a kind the gear tree does not have");
            }
            if (row.getValue().isEmpty()) {
                faults.add("the row \"" + row.getKey() + "\" carries no tags, which is a row that says nothing");
            }
            for (String tag : row.getValue()) {
                if (!tag.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                    faults.add("\"" + tag + "\" on \"" + row.getKey() + "\" is not a namespaced tag id");
                }
            }
        }
        for (String leaf : ElysiumGearTree.leaves()) {
            if (ElysiumGearTree.is(leaf, "curio")) {
                continue;
            }
            if (tagsFor(leaf).isEmpty()) {
                faults.add("the kind \"" + leaf + "\" carries no vanilla tag and inherits none, so a "
                        + leaf + " cannot be enchanted");
            }
        }
        return faults;
    }
}
