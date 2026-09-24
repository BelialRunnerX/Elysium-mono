package com.elysium.armory.registry;

import com.elysium.armory.ElysiumArmory;
import com.elysium.armory.item.ArmoryGearItem;
import com.elysium.armory.item.ArmoryPartItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The modular gear and the parts it is built from.
 *
 * <p>Items are registered at startup and the parts that describe them are
 * datapack data, so the two lists here have to agree with the files — the
 * validator compares them, rather than the game failing at the first craft.
 */
public final class ArmoryItems {

    private ArmoryItems() {
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ElysiumArmory.MODID);

    /** Every gear type that has an item: one per main part in the data. */
    public static final List<String> GEAR = List.of(
            "sword", "katana", "machete", "spear", "mace", "dagger", "knife",
            "pickaxe", "shovel", "axe", "hoe", "shears", "sickle", "mattock", "paxel",
            "hammer", "excavator", "saw", "prospector_hammer",
            "bow", "crossbow", "slingshot", "trident", "fishing_rod", "shield",
            "helmet", "chestplate", "leggings", "boots", "elytra",
            "arrow", "ring", "bracelet", "necklace");

    /** Every part the data defines an item for: the 48 part definitions. */
    public static final List<String> PARTS = List.of(
            "sword_blade", "katana_blade", "machete_blade", "spear_tip", "mace_core", "dagger_blade",
            "knife_blade", "pickaxe_head", "shovel_head", "axe_head", "hoe_head", "shear_blades",
            "sickle_blade", "mattock_head", "paxel_head", "hammer_head", "excavator_head", "saw_blade",
            "prospector_hammer_head", "bow_limbs", "crossbow_limbs", "slingshot_limbs", "trident_prongs",
            "fishing_reel_and_hook", "shield_plate", "helmet_plates", "chestplate_plates", "legging_plates",
            "boot_plates", "elytra_wings", "arrow_heads", "ring_shank", "bracelet_band", "necklace_chain",
            "rod", "tip", "binding", "grip", "lining", "coating", "setting", "cord", "fletching",
            "magnetic_upgrade", "red_card_upgrade", "road_maker_upgrade", "spoon_upgrade", "wide_plate_upgrade");

    /** The plans: a reusable blueprint, a single-use template, and the paper both are made of. */
    public static final DeferredItem<Item> BLUEPRINT_PAPER =
            ITEMS.registerSimpleItem("blueprint_paper", new Item.Properties());
    public static final DeferredItem<Item> BLUEPRINT =
            ITEMS.registerItem("blueprint", p -> new com.elysium.armory.item.ArmoryBlueprintItem(true, p));
    /** One book that remembers every plan it has been shown. */
    public static final DeferredItem<Item> PLAN_BOOK =
            ITEMS.registerItem("plan_book",
                    p -> new com.elysium.armory.item.ArmoryPlanBookItem(p));
    public static final DeferredItem<Item> MOD_KIT =
            ITEMS.registerItem("mod_kit", p -> new com.elysium.armory.item.ArmoryModKitItem(p));

    /**
     * What an affix comes back as, and what a reroll is paid in.
     *
     * One item rather than one per rarity: the count is the rarity, so a
     * player reads the price off the stack rather than off a table.
     */
    public static final DeferredItem<Item> AFFIX_ESSENCE =
            ITEMS.registerItem("affix_essence", p -> new Item(p));

    /** A pressed plate: the processed material the sheet parts are made of. */
    public static final DeferredItem<Item> SHEET_METAL =
            ITEMS.registerItem("sheet_metal", p -> new Item(p));
    public static final DeferredItem<Item> TEMPLATE =
            ITEMS.registerItem("template", p -> new com.elysium.armory.item.ArmoryBlueprintItem(false, p));

    private static final Map<String, DeferredItem<Item>> GEAR_ITEMS = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<Item>> PART_ITEMS = new LinkedHashMap<>();

    /** Which vanilla class each gear type has to be, for the game to treat it as what it is. */
    private static Item make(String type, Item.Properties p) {
        return switch (type) {
            case "helmet" -> new com.elysium.armory.item.ArmoryArmorItem(type,
                    net.minecraft.world.item.ArmorItem.Type.HELMET, p);
            case "chestplate" -> new com.elysium.armory.item.ArmoryArmorItem(type,
                    net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, p);
            case "leggings" -> new com.elysium.armory.item.ArmoryArmorItem(type,
                    net.minecraft.world.item.ArmorItem.Type.LEGGINGS, p);
            case "boots" -> new com.elysium.armory.item.ArmoryArmorItem(type,
                    net.minecraft.world.item.ArmorItem.Type.BOOTS, p);
            case "elytra" -> new com.elysium.armory.item.ArmoryElytraItem(p);
            case "shield" -> new com.elysium.armory.item.ArmoryShieldItem(p);
            case "bow", "slingshot" -> new com.elysium.armory.item.ArmoryRangedItems.Bow(type, p);
            case "crossbow" -> new com.elysium.armory.item.ArmoryRangedItems.Crossbow(p);
            case "trident" -> new com.elysium.armory.item.ArmoryRangedItems.Trident(p);
            case "fishing_rod" -> new com.elysium.armory.item.ArmoryRangedItems.Rod(p);
            case "arrow" -> new com.elysium.armory.item.ArmoryRangedItems.Arrow(p);
            // The tools with a habit of their own; the rest are plain gear.
            case "hammer", "excavator", "saw", "sickle", "machete", "prospector_hammer", "dagger",
                 "mattock", "paxel", "hoe" -> new com.elysium.armory.item.ArmoryToolItem(type, p);
            default -> new ArmoryGearItem(type, p);
        };
    }

    static {
        for (String type : GEAR) {
            GEAR_ITEMS.put(type, ITEMS.registerItem(type, p -> make(type, p)));
        }
        for (String part : PARTS) {
            PART_ITEMS.put(part, ITEMS.registerItem(part, p -> new ArmoryPartItem(part, p)));
        }
    }

    /**
     * The gear types worn in an accessory slot, and which slot each wants.
     *
     * The names are the accessory mod's vocabulary rather than this module's,
     * because a slot is that mod's idea and inventing our own word for it
     * would mean translating in two places.
     */
    public static final Map<String, String> WEARABLE = Map.of(
            "ring", "ring", "bracelet", "bracelet", "necklace", "necklace");

    /**
     * Tells the library which of our items want wearing.
     *
     * Called at common setup. Nothing here imports an accessory mod: the
     * library holds the list, whichever mod owns the slots reads it, and a
     * pack with no accessory mod simply has a list nobody reads — the rings
     * are then craftable, tradeable and inert, which is the honest behaviour
     * rather than a crash.
     */
    public static void declareWearables() {
        for (Map.Entry<String, String> entry : WEARABLE.entrySet()) {
            DeferredItem<Item> item = GEAR_ITEMS.get(entry.getKey());
            if (item != null) {
                com.elysium.lib.trinket.ElysiumWearables.register(item.get(), entry.getValue());
            }
        }
    }

    public static Map<String, DeferredItem<Item>> gear() {
        return java.util.Collections.unmodifiableMap(GEAR_ITEMS);
    }

    public static Map<String, DeferredItem<Item>> parts() {
        return java.util.Collections.unmodifiableMap(PART_ITEMS);
    }

    /** The gear item for a type, whatever vanilla class it had to be. */
    public static com.elysium.armory.item.ArmoryGear gearItem(String type) {
        DeferredItem<Item> item = GEAR_ITEMS.get(type);
        return item == null ? null : (com.elysium.armory.item.ArmoryGear) item.get();
    }

    public static ArmoryPartItem partItem(String part) {
        DeferredItem<Item> item = PART_ITEMS.get(part);
        return item == null ? null : (ArmoryPartItem) item.get();
    }

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumArmory.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("armory",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumarmory"))
                    .icon(() -> new ItemStack(GEAR_ITEMS.get("sword").get()))
                    .displayItems((params, out) -> {
                        List<DeferredItem<Item>> all = new ArrayList<>(GEAR_ITEMS.values());
                        all.addAll(PART_ITEMS.values());
                        all.add(BLUEPRINT_PAPER);
                        all.add(BLUEPRINT);
                        all.add(TEMPLATE);
                        all.add(MOD_KIT);
                        all.add(PLAN_BOOK);
                        all.forEach(item -> out.accept(item.get()));
                        // And one of each, built. An unbuilt modular sword has
                        // no damage, no durability and no name worth reading,
                        // and a creative player taking one out of the tab
                        // reasonably concludes the mod is broken.
                        com.elysium.armory.item.ArmorySamples.all().forEach(out::accept);
                    })
                    .build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
