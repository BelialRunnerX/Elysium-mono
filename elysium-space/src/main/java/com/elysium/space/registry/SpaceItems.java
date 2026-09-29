package com.elysium.space.registry;

import com.elysium.space.ElysiumSpace;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/** A block item for every block except the vine body, and the creative tab. */
public final class SpaceItems {

    private SpaceItems() {
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ElysiumSpace.MODID);

    public static final List<DeferredItem<?>> TAB_ORDER = new ArrayList<>();

    static {
        SpaceBlocks.BLOCKS.getEntries().forEach(block -> {
            if (block.getId().getPath().equals("pale_vines_plant")) {
                return;                    // vines are placed as the head, as vanilla does
            }
            TAB_ORDER.add(ITEMS.registerSimpleBlockItem(block.getId().getPath(), block::get,
                    new Item.Properties()));
        });
    }

    /** Tier-one rocket; S6 adds the part-built tiers and upgrades. */
    public static final DeferredItem<Item> ROCKET = register(ITEMS.registerItem("rocket",
            com.elysium.space.item.RocketItem::new, new Item.Properties().stacksTo(1)));

    /** 250 mB of rocket fuel, until the fuel refinery and fluids arrive (S6). */
    public static final DeferredItem<Item> FUEL_CANISTER = register(ITEMS.registerItem("fuel_canister",
            com.elysium.space.item.FuelCanisterItem::new, new Item.Properties().stacksTo(16)));

    public static final DeferredItem<Item> SPACE_SUIT_HELMET = register(ITEMS.registerItem("space_suit_helmet",
            p -> new com.elysium.space.item.SpaceSuitItem(net.minecraft.world.item.ArmorItem.Type.HELMET, p)));
    public static final DeferredItem<Item> SPACE_SUIT_CHESTPLATE = register(ITEMS.registerItem("space_suit_chestplate",
            p -> new com.elysium.space.item.SpaceSuitItem(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, p)));
    public static final DeferredItem<Item> SPACE_SUIT_LEGGINGS = register(ITEMS.registerItem("space_suit_leggings",
            p -> new com.elysium.space.item.SpaceSuitItem(net.minecraft.world.item.ArmorItem.Type.LEGGINGS, p)));
    public static final DeferredItem<Item> SPACE_SUIT_BOOTS = register(ITEMS.registerItem("space_suit_boots",
            p -> new com.elysium.space.item.SpaceSuitItem(net.minecraft.world.item.ArmorItem.Type.BOOTS, p)));

    /** The Jet Suit: a space suit chestplate with thrusters and its own fuel. */
    public static final DeferredItem<Item> JET_SUIT = register(ITEMS.registerItem("jet_suit",
            com.elysium.space.item.JetSuitItem::new));

    /** 1000 mB of air for the suit. Placeholder recipe until oxygen machines (S6). */
    public static final DeferredItem<Item> OXYGEN_CANISTER = register(ITEMS.registerItem("oxygen_canister",
            com.elysium.space.item.OxygenCanisterItem::new, new Item.Properties().stacksTo(16)));

    /** Rocket parts (Stellaris's): a rocket is crafted from these. */
    public static final DeferredItem<Item> NOSE_CONE = register(ITEMS.registerSimpleItem("rocket_nose_cone",
            new Item.Properties()));
    public static final DeferredItem<Item> ROCKET_FIN = register(ITEMS.registerSimpleItem("rocket_fin",
            new Item.Properties()));
    public static final DeferredItem<Item> ROCKET_ENGINE = register(ITEMS.registerSimpleItem("rocket_engine",
            new Item.Properties()));
    /** Fitted to an empty rocket, it burns hydrogen: 21.36 Mm/mB against fuel's 19.22. */
    public static final DeferredItem<Item> HYDROGEN_MOTOR = register(ITEMS.registerSimpleItem("hydrogen_motor",
            new Item.Properties().stacksTo(1)));
    /** 250 mB of hydrogen, bottled at a Water Separator (sneak while using a glass bottle). */
    public static final DeferredItem<Item> HYDROGEN_CANISTER = register(ITEMS.registerSimpleItem("hydrogen_canister",
            new Item.Properties().stacksTo(16)));

    // Metals from the space ores, and the rime shard (Stellaris's ice shard).
    public static final DeferredItem<Item> RAW_LUNITE = register(ITEMS.registerSimpleItem("raw_lunite", new Item.Properties()));
    public static final DeferredItem<Item> LUNITE_INGOT = register(ITEMS.registerSimpleItem("lunite_ingot", new Item.Properties()));
    public static final DeferredItem<Item> LUNITE_NUGGET = register(ITEMS.registerSimpleItem("lunite_nugget", new Item.Properties()));
    public static final DeferredItem<Item> RAW_FROSTITE = register(ITEMS.registerSimpleItem("raw_frostite", new Item.Properties()));
    public static final DeferredItem<Item> FROSTITE_INGOT = register(ITEMS.registerSimpleItem("frostite_ingot", new Item.Properties()));
    public static final DeferredItem<Item> FROSTITE_NUGGET = register(ITEMS.registerSimpleItem("frostite_nugget", new Item.Properties()));
    public static final DeferredItem<Item> RAW_CRYONITE = register(ITEMS.registerSimpleItem("raw_cryonite", new Item.Properties()));
    public static final DeferredItem<Item> CRYONITE_INGOT = register(ITEMS.registerSimpleItem("cryonite_ingot", new Item.Properties()));
    public static final DeferredItem<Item> CRYONITE_NUGGET = register(ITEMS.registerSimpleItem("cryonite_nugget", new Item.Properties()));
    public static final DeferredItem<Item> RAW_CINDRIUM = register(ITEMS.registerSimpleItem("raw_cindrium", new Item.Properties()));
    public static final DeferredItem<Item> CINDRIUM_INGOT = register(ITEMS.registerSimpleItem("cindrium_ingot", new Item.Properties()));
    public static final DeferredItem<Item> CINDRIUM_NUGGET = register(ITEMS.registerSimpleItem("cindrium_nugget", new Item.Properties()));
    public static final DeferredItem<Item> RAW_STEEL = register(ITEMS.registerSimpleItem("raw_steel", new Item.Properties()));
    public static final DeferredItem<Item> STEEL_INGOT = register(ITEMS.registerSimpleItem("steel_ingot", new Item.Properties()));
    public static final DeferredItem<Item> STEEL_NUGGET = register(ITEMS.registerSimpleItem("steel_nugget", new Item.Properties()));
    public static final DeferredItem<Item> RIME_SHARD = register(ITEMS.registerSimpleItem("rime_shard", new Item.Properties()));
    // The radioactive metals (28 Sep 2026): neptunium on Cinderveil, plutonium on Frostfall, and
    // heavy metal alloyed from those two and the ore unification's uranium.
    public static final DeferredItem<Item> RAW_NEPTUNIUM = register(ITEMS.registerSimpleItem("raw_neptunium", new Item.Properties()));
    public static final DeferredItem<Item> NEPTUNIUM_INGOT = register(ITEMS.registerSimpleItem("neptunium_ingot", new Item.Properties()));
    public static final DeferredItem<Item> NEPTUNIUM_NUGGET = register(ITEMS.registerSimpleItem("neptunium_nugget", new Item.Properties()));
    public static final DeferredItem<Item> RAW_PLUTONIUM = register(ITEMS.registerSimpleItem("raw_plutonium", new Item.Properties()));
    public static final DeferredItem<Item> PLUTONIUM_INGOT = register(ITEMS.registerSimpleItem("plutonium_ingot", new Item.Properties()));
    public static final DeferredItem<Item> PLUTONIUM_NUGGET = register(ITEMS.registerSimpleItem("plutonium_nugget", new Item.Properties()));
    public static final DeferredItem<Item> HEAVY_METAL_INGOT = register(ITEMS.registerSimpleItem("heavy_metal_ingot", new Item.Properties()));
    public static final DeferredItem<Item> HEAVY_METAL_NUGGET = register(ITEMS.registerSimpleItem("heavy_metal_nugget", new Item.Properties()));

    /** The heavier motors: each burns its metal's nuggets and ingots, at SpaceRoute's rate for that fuel. */
    public static final DeferredItem<Item> URANIUM_MOTOR = register(ITEMS.registerSimpleItem("uranium_motor",
            new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> PLUTONIUM_MOTOR = register(ITEMS.registerSimpleItem("plutonium_motor",
            new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> RADIOACTIVE_MOTOR = register(ITEMS.registerSimpleItem("radioactive_motor",
            new Item.Properties().stacksTo(1)));

    /** Used on a block, it says what the chunk's oil deposit holds (Stellaris's Oil Finder). */
    public static final DeferredItem<Item> OIL_FINDER = register(ITEMS.registerItem("oil_finder",
            com.elysium.space.item.OilFinderItem::new, new Item.Properties().stacksTo(1)));

    /** Doubles a rocket's tank to 6000 mB, which is what reaches the Emerald Bastion. */
    public static final DeferredItem<Item> LARGE_FUEL_TANK = register(ITEMS.registerSimpleItem("large_fuel_tank",
            new Item.Properties().stacksTo(1)));

    // Station blueprints (Stellaris's small, medium and big stations).
    public static final DeferredItem<Item> SMALL_STATION_BLUEPRINT = blueprint(com.elysium.space.world.StationRules.Size.SMALL);
    public static final DeferredItem<Item> MEDIUM_STATION_BLUEPRINT = blueprint(com.elysium.space.world.StationRules.Size.MEDIUM);
    public static final DeferredItem<Item> BIG_STATION_BLUEPRINT = blueprint(com.elysium.space.world.StationRules.Size.BIG);

    private static DeferredItem<Item> blueprint(com.elysium.space.world.StationRules.Size size) {
        return register(ITEMS.registerItem(size.id + "_station_blueprint",
                p -> new com.elysium.space.item.StationBlueprintItem(size, p.stacksTo(1))));
    }

    // Spawn eggs, one per mob: base colour, then spots.
    public static final DeferredItem<Item> MOONFOLK_SPAWN_EGG = egg("moonfolk", SpaceEntities.MOONFOLK, 0xC9C3B0, 0x6FA7C9);
    public static final DeferredItem<Item> HOLLOW_MOONFOLK_SPAWN_EGG = egg("hollow_moonfolk", SpaceEntities.HOLLOW_MOONFOLK, 0x6B7A6F, 0xB9E4F2);
    public static final DeferredItem<Item> VOID_CRAWLER_SPAWN_EGG = egg("void_crawler", SpaceEntities.VOID_CRAWLER, 0x2A2238, 0x8D5BD6);
    public static final DeferredItem<Item> FROST_RAPTOR_SPAWN_EGG = egg("frost_raptor", SpaceEntities.FROST_RAPTOR, 0xB8D8E8, 0x3F6E8C);
    public static final DeferredItem<Item> CINDER_BOAR_SPAWN_EGG = egg("cinder_boar", SpaceEntities.CINDER_BOAR, 0x5A2E1E, 0xE0762C);
    public static final DeferredItem<Item> CINDERLING_SPAWN_EGG = egg("cinderling", SpaceEntities.CINDERLING, 0x9E5B3A, 0xF2C14E);
    public static final DeferredItem<Item> CINDERLING_BRUTE_SPAWN_EGG = egg("cinderling_brute", SpaceEntities.CINDERLING_BRUTE, 0x4A2A1C, 0xF2C14E);

    private static DeferredItem<Item> egg(String mob,
            java.util.function.Supplier<? extends net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>> type,
            int base, int spots) {
        return register(ITEMS.registerItem(mob + "_spawn_egg",
                p -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(type, base, spots, p)));
    }

    static {
        for (com.elysium.space.registry.SpaceFluids.Kind kind : com.elysium.space.registry.SpaceFluids.ALL) {
            if (kind.bucket != null) {
                TAB_ORDER.add(kind.bucket);
            }
        }
    }

    private static DeferredItem<Item> register(DeferredItem<Item> item) {
        TAB_ORDER.add(item);
        return item;
    }

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumSpace.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("space",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumspace"))
                    .icon(() -> new ItemStack(SpaceBlocks.OXYGEN_DISTRIBUTOR.get()))
                    .displayItems((params, out) -> TAB_ORDER.forEach(i -> out.accept(i.get())))
                    .build());
}
