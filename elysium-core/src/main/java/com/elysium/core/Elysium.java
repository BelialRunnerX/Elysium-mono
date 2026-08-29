package com.elysium.core;

import com.elysium.lib.affix.ElysiumAffixes;
import com.elysium.core.block.AetheriumOreBlock;
import com.elysium.core.block.AscensionForgeBlock;
import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.core.block.NeutroniumBlock;
import com.elysium.core.block.NeutroniumOreBlock;
import com.elysium.core.block.ReforgeTableBlock;
import com.elysium.core.block.ReforgeTableBlockEntity;
import com.elysium.core.block.RuneSocketTableBlock;
import com.elysium.core.block.VoidglassOreBlock;
import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.element.ElysiumElements;
import com.elysium.core.entity.ImperialEnforcer;
import com.elysium.core.entity.UnswornRaider;
import com.elysium.core.item.ElysiumArmorItem;
import com.elysium.core.item.ElysiumArmorMaterials;
import com.elysium.core.item.ElysiumCodexItem;
import com.elysium.core.item.ElysiumMaterialConfig;
import com.elysium.core.item.ElysiumMaterialGear;
import com.elysium.core.item.ElysiumTools;
import com.elysium.core.item.ElysiumMaterials;
import com.elysium.core.item.ElysiumRuneItem;
import com.elysium.core.item.ElysiumRunes;
import com.elysium.lib.item.ElysiumRune;
import com.elysium.lib.item.ElysiumComponents;
import com.elysium.lib.item.ElysiumRarities;
import com.elysium.core.item.ElysiumReforgeItem;
import com.elysium.core.item.ElysiumTools;
import com.elysium.core.item.ElysiumWeaponItem;
import com.elysium.core.item.NeutroniumArmorMaterials;
import com.elysium.core.menu.ReforgeTableMenu;
import com.elysium.core.silentgear.ElysiumSilentGear;
import com.elysium.lib.standing.ElysiumStanding;
import com.elysium.core.tooltip.ElysiumLegendaryTooltips;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Elysium - NeoForge 1.21.1 (neoforge 21.1.x)
 *
 * A modular gear progression mod: tiered elemental armour and weapons, a rune
 * socket system, reforging, and armour ascension.
 *
 * Content and systems follow the Sleeping Empire equipment archive - the five
 * elements answer one another in a counter matrix, gear tiers set how large
 * that advantage is, and reforging is a finite resource per piece.
 *
 * Everything is registered through {@link DeferredRegister}, which is the only
 * supported registration route on NeoForge. Integrations with Apotheosis,
 * Silent Gear and Legendary Tooltips are soft - the mod never references their
 * classes at compile time.
 */
@Mod(Elysium.MODID)
public class Elysium {

    public static final String MODID = "elysium";
    public static final Logger LOGGER = LogUtils.getLogger();

    // ------------------------------------------------------------------
    // Registries
    // ------------------------------------------------------------------

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MODID);

    /** Everything registered here, in the order it should appear in the tab. */
    private static final List<DeferredHolder<Item, Item>> TAB_ORDER = new ArrayList<>();

    // ------------------------------------------------------------------
    // Blocks
    // ------------------------------------------------------------------

    public static final DeferredHolder<Block, Block> NEUTRONIUM_ORE =
            BLOCKS.register("neutronium_ore", NeutroniumOreBlock::new);

    public static final DeferredHolder<Block, Block> NEUTRONIUM_BLOCK =
            BLOCKS.register("neutronium_block", NeutroniumBlock::new);

    public static final DeferredHolder<Block, Block> AETHERIUM_ORE =
            BLOCKS.register("aetherium_ore", AetheriumOreBlock::new);

    public static final DeferredHolder<Block, Block> VOIDGLASS_ORE =
            BLOCKS.register("voidglass_ore", VoidglassOreBlock::new);

    public static final DeferredHolder<Block, Block> REFORGE_TABLE =
            BLOCKS.register("reforge_table", ReforgeTableBlock::new);

    public static final DeferredHolder<Block, Block> RUNE_SOCKET_TABLE =
            BLOCKS.register("rune_socket_table", RuneSocketTableBlock::new);

    public static final DeferredHolder<Block, Block> ASCENSION_FORGE =
            BLOCKS.register("ascension_forge", AscensionForgeBlock::new);

    // ------------------------------------------------------------------
    // Block items
    // ------------------------------------------------------------------

    public static final DeferredHolder<Item, Item> NEUTRONIUM_ORE_ITEM = blockItem(NEUTRONIUM_ORE);
    public static final DeferredHolder<Item, Item> AETHERIUM_ORE_ITEM = blockItem(AETHERIUM_ORE);
    public static final DeferredHolder<Item, Item> VOIDGLASS_ORE_ITEM = blockItem(VOIDGLASS_ORE);
    public static final DeferredHolder<Item, Item> NEUTRONIUM_BLOCK_ITEM = blockItem(NEUTRONIUM_BLOCK);
    public static final DeferredHolder<Item, Item> REFORGE_TABLE_ITEM = blockItem(REFORGE_TABLE);
    public static final DeferredHolder<Item, Item> RUNE_SOCKET_TABLE_ITEM = blockItem(RUNE_SOCKET_TABLE);
    public static final DeferredHolder<Item, Item> ASCENSION_FORGE_ITEM = blockItem(ASCENSION_FORGE);

    // ------------------------------------------------------------------
    // Materials
    // ------------------------------------------------------------------

    public static final DeferredHolder<Item, Item> NEUTRONIUM_INGOT =
            simpleItem("neutronium_ingot");

    public static final DeferredHolder<Item, Item> AETHERIUM_INGOT =
            simpleItem("aetherium_ingot");

    public static final DeferredHolder<Item, Item> VOIDGLASS_INGOT =
            simpleItem("voidglass_ingot");

    public static final DeferredHolder<Item, Item> ELYSIUM_REFORGE =
            item("elysium_reforge", ElysiumReforgeItem::new);

    /**
     * The character sheet in the hand. Issued once on first join and never
     * craftable — losing it costs nothing, because /elysium sheet does the
     * same thing.
     */
    public static final DeferredHolder<Item, Item> IMPERIAL_CODEX =
            item("imperial_codex", ElysiumCodexItem::new);

    // ------------------------------------------------------------------
    // Elysium armour
    // ------------------------------------------------------------------

    public static final DeferredHolder<Item, Item> ELYSIUM_HELMET =
            armour("elysium_helmet", ArmorItem.Type.HELMET, ElysiumElements.VOID, ElysiumRarities.RARE);

    /** The archive's "Plasma Carapace", Epic, Plasma affinity. */
    public static final DeferredHolder<Item, Item> PLASMA_CHESTPLATE =
            armour("plasma_chestplate", ArmorItem.Type.CHESTPLATE, ElysiumElements.PLASMA, ElysiumRarities.EPIC);

    /** The archive's flagship: "Voidweave Aegis", Legendary, Void affinity. */
    public static final DeferredHolder<Item, Item> VOIDWEAVE_AEGIS =
            armour("voidweave_aegis", ArmorItem.Type.CHESTPLATE, ElysiumElements.VOID, ElysiumRarities.LEGENDARY);

    public static final DeferredHolder<Item, Item> NEURAL_LEGGINGS =
            armour("neural_leggings", ArmorItem.Type.LEGGINGS, ElysiumElements.NEURAL, ElysiumRarities.RARE);

    public static final DeferredHolder<Item, Item> DIMENSIONAL_BOOTS =
            armour("dimensional_boots", ArmorItem.Type.BOOTS, ElysiumElements.DIMENSIONAL, ElysiumRarities.EPIC);

    /** Elysomnion's own. Unique tier, fire resistant. */
    public static final DeferredHolder<Item, Item> EMPEROR_CROWN =
            register("emperor_crown", () -> new ElysiumArmorItem(
                    ElysiumArmorMaterials.ELYSIUM, ArmorItem.Type.HELMET,
                    armourProperties(ArmorItem.Type.HELMET, ElysiumArmorMaterials.DURABILITY_MULTIPLIER)
                            .fireResistant(),
                    ElysiumElements.VOID, ElysiumRarities.UNIQUE));

    // ------------------------------------------------------------------
    // Neutronium armour - inert, no elemental affinity either way
    // ------------------------------------------------------------------

    public static final DeferredHolder<Item, Item> NEUTRONIUM_HELMET =
            neutroniumArmour("neutronium_helmet", ArmorItem.Type.HELMET);
    public static final DeferredHolder<Item, Item> NEUTRONIUM_CHESTPLATE =
            neutroniumArmour("neutronium_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredHolder<Item, Item> NEUTRONIUM_LEGGINGS =
            neutroniumArmour("neutronium_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredHolder<Item, Item> NEUTRONIUM_BOOTS =
            neutroniumArmour("neutronium_boots", ArmorItem.Type.BOOTS);

    // ------------------------------------------------------------------
    // Weapons
    //
    // One per element so every matchup in the counter matrix is reachable,
    // plus the two the archive names outright.
    // ------------------------------------------------------------------

    public static final DeferredHolder<Item, Item> VOIDCUT_BLADE =
            weapon("voidcut_blade", ElysiumElements.VOID, ElysiumRarities.RARE, Tiers.DIAMOND, 3.0F, -2.4F);

    public static final DeferredHolder<Item, Item> PLASMA_BRAND =
            weapon("plasma_brand", ElysiumElements.PLASMA, ElysiumRarities.RARE, Tiers.DIAMOND, 3.0F, -2.4F);

    public static final DeferredHolder<Item, Item> NEURAL_LASH =
            weapon("neural_lash", ElysiumElements.NEURAL, ElysiumRarities.RARE, Tiers.DIAMOND, 2.0F, -2.0F);

    public static final DeferredHolder<Item, Item> RIFT_EDGE =
            weapon("rift_edge", ElysiumElements.DIMENSIONAL, ElysiumRarities.RARE, Tiers.DIAMOND, 3.0F, -2.4F);

    public static final DeferredHolder<Item, Item> KINETIC_MAUL =
            weapon("kinetic_maul", ElysiumElements.KINETIC, ElysiumRarities.RARE, Tiers.DIAMOND, 5.0F, -3.0F);

    /**
     * "Singularity Lance - Legendary, Dimensional. Damage 72, Fire Rate 1
     * attack/turn." One heavy, slow blow.
     */
    public static final DeferredHolder<Item, Item> SINGULARITY_LANCE =
            weapon("singularity_lance", ElysiumElements.DIMENSIONAL, ElysiumRarities.LEGENDARY,
                    Tiers.NETHERITE, 5.0F, -3.2F);

    /**
     * "Neural Cascade Rifle - Epic, Neural. Damage 38, Fire Rate 2
     * attacks/turn." Half the weight, twice the rate.
     */
    public static final DeferredHolder<Item, Item> NEURAL_CASCADE_RIFLE =
            weapon("neural_cascade_rifle", ElysiumElements.NEURAL, ElysiumRarities.EPIC,
                    Tiers.NETHERITE, 1.0F, -1.6F);

    // ------------------------------------------------------------------
    // Area tools
    //
    // Four shapes in three materials. Each is a real weapon as well as a tool,
    // and each resonates with its material's element — a Voidward rune in a
    // Voidglass hammer is aligned, the same rune in a Neutronium one is not.
    // ------------------------------------------------------------------





    // ------------------------------------------------------------------
    // Runes
    // ------------------------------------------------------------------

    public static final DeferredHolder<Item, Item> VOIDWARD_RUNE =
            rune("voidward_rune", ElysiumRunes.VOIDWARD);
    public static final DeferredHolder<Item, Item> PLASMAFORGE_RUNE =
            rune("plasmaforge_rune", ElysiumRunes.PLASMAFORGE);
    public static final DeferredHolder<Item, Item> NEURALSPIKE_RUNE =
            rune("neuralspike_rune", ElysiumRunes.NEURALSPIKE);
    public static final DeferredHolder<Item, Item> DIMENSIONALSHIFT_RUNE =
            rune("dimensionalshift_rune", ElysiumRunes.DIMENSIONALSHIFT);
    public static final DeferredHolder<Item, Item> KINETICSURGE_RUNE =
            rune("kineticsurge_rune", ElysiumRunes.KINETICSURGE);

    public static final DeferredHolder<Item, Item> STABILIZER_RUNE =
            rune("stabilizer_rune", ElysiumRunes.STABILIZER);
    public static final DeferredHolder<Item, Item> REFLEX_RUNE =
            rune("reflex_rune", ElysiumRunes.REFLEX);
    public static final DeferredHolder<Item, Item> BARRIER_RUNE =
            rune("barrier_rune", ElysiumRunes.BARRIER);
    public static final DeferredHolder<Item, Item> PLASMA_CORE_RUNE =
            rune("plasma_core_rune", ElysiumRunes.PLASMA_CORE);

    // ------------------------------------------------------------------
    // Entities
    // ------------------------------------------------------------------

    /**
     * The Empire's answer to a high Suspicion score. Subclasses Zombie so it
     * can reuse vanilla's renderer, which already draws worn armour.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ImperialEnforcer>> IMPERIAL_ENFORCER =
            ENTITIES.register("imperial_enforcer", () -> EntityType.Builder
                    .<ImperialEnforcer>of(ImperialEnforcer::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("imperial_enforcer"));

    public static final DeferredHolder<Item, Item> IMPERIAL_ENFORCER_SPAWN_EGG =
            register("imperial_enforcer_spawn_egg", () -> new DeferredSpawnEggItem(
                    IMPERIAL_ENFORCER, 0x181430, 0xa86ef0, new Item.Properties()));

    /**
     * The other side. Built on Husk so it inherits a vanilla renderer that is
     * visually distinct from the enforcer's and does not burn in daylight.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<UnswornRaider>> UNSWORN_RAIDER =
            ENTITIES.register("unsworn_raider", () -> EntityType.Builder
                    .<UnswornRaider>of(UnswornRaider::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("unsworn_raider"));

    public static final DeferredHolder<Item, Item> UNSWORN_RAIDER_SPAWN_EGG =
            register("unsworn_raider_spawn_egg", () -> new DeferredSpawnEggItem(
                    UNSWORN_RAIDER, 0x3a4250, 0x33d296, new Item.Properties()));

    // ------------------------------------------------------------------
    // Block entities and menus
    // ------------------------------------------------------------------

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReforgeTableBlockEntity>> REFORGE_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("reforge_table", () -> BlockEntityType.Builder
                    .of(ReforgeTableBlockEntity::new,
                            REFORGE_TABLE.get(), RUNE_SOCKET_TABLE.get(), ASCENSION_FORGE.get())
                    .build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<ReforgeTableMenu>> REFORGE_TABLE_MENU =
            MENUS.register("reforge_table", () -> new MenuType<>(ReforgeTableMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // ------------------------------------------------------------------
    // Creative tab
    // ------------------------------------------------------------------

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ELYSIUM_TAB =
            CREATIVE_TABS.register("elysium", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysium"))
                    .icon(() -> new ItemStack(EMPEROR_CROWN.get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredHolder<Item, Item> entry : TAB_ORDER) {
                            output.accept(entry.get());
                        }
                        // Generated material gear, filtered to materials
                        // something installed can actually supply. The rest is
                        // registered and simply not offered — see
                        // ElysiumGearMaterial for why it exists at all.
                        for (DeferredHolder<Item, Item> entry
                                : ElysiumMaterialGear.available()) {
                            output.accept(entry.get());
                        }
                    })
                    .build());

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    public Elysium(IEventBus modEventBus, ModContainer container) {
        // Order matters, and all of it has to happen before the registry
        // events fire — which is why it is in the constructor rather than in a
        // setup listener.
        //
        // 1. The config, because the material table reads it.
        // 2. The materials, because the gear iterates over them.
        // 3. The gear, which adds several hundred entries to ITEMS.
        //
        // A material added after this point has nowhere to put its hammer,
        // which is the whole reason a config change needs a restart.
        //
        // STARTUP, not COMMON. A COMMON config is not loaded until just before
        // FMLCommonSetupEvent, which is after every constructor has run — so
        // reading it here threw "Cannot get config value before config is
        // loaded" and took the whole mod down on the first launch. STARTUP is
        // the only type read immediately on registration, and therefore the
        // only one whose values can decide what gets registered.
        //
        // The cost is that STARTUP is not synced: server and client must have
        // matching files or the client is kicked for a registry mismatch. That
        // is inherent to a config that changes which items exist, it is called
        // out in capitals in the config's own comment, and there is no third
        // option — no point in the lifecycle is both after config load and
        // before registration.
        container.registerConfig(ModConfig.Type.STARTUP, ElysiumMaterialConfig.SPEC);
        ElysiumMaterials.bootstrap();
        ElysiumMaterialGear.registerAll(ElysiumMaterials.elysiumMaterials());
        ElysiumMaterialGear.registerAll(ElysiumMaterials.vanillaMaterials());
        ElysiumMaterialGear.registerAll(ElysiumMaterials.MODDED_MATERIALS);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        ENTITIES.register(modEventBus);
        ElysiumArmorMaterials.ARMOR_MATERIALS.register(modEventBus);

        modEventBus.addListener(this::addCreative);

        // Everything this mod tells the library about itself: its factions, the
        // mobs standing can send, what a reward is, which blocks are ore, and
        // the codex. See ElysiumContent — it is also the worked example a
        // second Elysium mod copies.
        ElysiumContent.register();

        // Soft integrations. Each one checks ModList itself and no-ops when the
        // target mod is absent, so a missing mod can never break class loading.
        if (ModList.get().isLoaded("apotheosis")) {
            ElysiumAffixes.onApotheosisPresent();
        }
        ElysiumSilentGear.register();
        ElysiumLegendaryTooltips.register();

        LOGGER.info("Elysium initialised: {} named items + {} material items across {} materials, {} psionic affixes, {}",
                TAB_ORDER.size(), ElysiumMaterialGear.all().size(),
                ElysiumMaterialGear.covered().size(),
                ElysiumAffixes.all().size(), ElysiumContent.summary());
    }

    /**
     * Mirrors the mod's content into the relevant vanilla tabs as well, so the
     * items also show up where players expect to find them.
     */
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(NEUTRONIUM_BLOCK_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(NEUTRONIUM_ORE_ITEM.get());
            event.accept(AETHERIUM_ORE_ITEM.get());
            event.accept(VOIDGLASS_ORE_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(REFORGE_TABLE_ITEM.get());
            event.accept(RUNE_SOCKET_TABLE_ITEM.get());
            event.accept(ASCENSION_FORGE_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(NEUTRONIUM_INGOT.get());
            event.accept(AETHERIUM_INGOT.get());
            event.accept(VOIDGLASS_INGOT.get());
            event.accept(ELYSIUM_REFORGE.get());
            event.accept(IMPERIAL_CODEX.get());
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            for (DeferredHolder<Item, Item> entry : ElysiumMaterialGear.available()) {
                if (entry.get() instanceof ElysiumArmorItem) {
                    event.accept(entry.get());
                }
            }
            event.accept(ELYSIUM_HELMET.get());
            event.accept(PLASMA_CHESTPLATE.get());
            event.accept(VOIDWEAVE_AEGIS.get());
            event.accept(NEURAL_LEGGINGS.get());
            event.accept(DIMENSIONAL_BOOTS.get());
            event.accept(EMPEROR_CROWN.get());
            event.accept(NEUTRONIUM_HELMET.get());
            event.accept(NEUTRONIUM_CHESTPLATE.get());
            event.accept(NEUTRONIUM_LEGGINGS.get());
            event.accept(NEUTRONIUM_BOOTS.get());
            event.accept(VOIDCUT_BLADE.get());
            event.accept(PLASMA_BRAND.get());
            event.accept(NEURAL_LASH.get());
            event.accept(RIFT_EDGE.get());
            event.accept(KINETIC_MAUL.get());
            event.accept(SINGULARITY_LANCE.get());
            event.accept(NEURAL_CASCADE_RIFLE.get());
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(IMPERIAL_ENFORCER_SPAWN_EGG.get());
            event.accept(UNSWORN_RAIDER_SPAWN_EGG.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            // Every material's tools, filtered to what something installed can
            // actually supply. Mirrored into the vanilla tab rather than listed
            // by name, because the list is now generated and naming it here
            // would go stale the moment a material is added.
            for (DeferredHolder<Item, Item> entry : ElysiumMaterialGear.available()) {
                if (entry.get() instanceof ElysiumTools.Hammer
                        || entry.get() instanceof ElysiumTools.Broadaxe
                        || entry.get() instanceof ElysiumTools.Scythe
                        || entry.get() instanceof ElysiumTools.Spear) {
                    event.accept(entry.get());
                }
            }
            event.accept(VOIDWARD_RUNE.get());
            event.accept(PLASMAFORGE_RUNE.get());
            event.accept(NEURALSPIKE_RUNE.get());
            event.accept(DIMENSIONALSHIFT_RUNE.get());
            event.accept(KINETICSURGE_RUNE.get());
            event.accept(STABILIZER_RUNE.get());
            event.accept(REFLEX_RUNE.get());
            event.accept(BARRIER_RUNE.get());
            event.accept(PLASMA_CORE_RUNE.get());
        }
    }

    // ------------------------------------------------------------------
    // Registration helpers
    //
    // Each one records the item in TAB_ORDER as a side effect, so the creative
    // tab can never drift out of sync with what is actually registered.
    // ------------------------------------------------------------------

    private static DeferredHolder<Item, Item> register(String name,
                                                       java.util.function.Supplier<? extends Item> supplier) {
        DeferredHolder<Item, Item> holder = ITEMS.register(name, supplier);
        TAB_ORDER.add(holder);
        return holder;
    }

    private static DeferredHolder<Item, Item> item(String name,
                                                   java.util.function.Supplier<? extends Item> supplier) {
        return register(name, supplier);
    }

    private static DeferredHolder<Item, Item> simpleItem(String name) {
        return register(name, () -> new Item(new Item.Properties()));
    }

    private static DeferredHolder<Item, Item> blockItem(DeferredHolder<Block, Block> block) {
        return register(block.getId().getPath(),
                () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static DeferredHolder<Item, Item> armour(String name, ArmorItem.Type type,
                                                     ElysiumElement element, int tier) {
        return register(name, () -> new ElysiumArmorItem(
                ElysiumArmorMaterials.ELYSIUM, type,
                armourProperties(type, ElysiumArmorMaterials.DURABILITY_MULTIPLIER),
                element, tier));
    }

    private static DeferredHolder<Item, Item> neutroniumArmour(String name, ArmorItem.Type type) {
        return register(name, () -> new ElysiumArmorItem(
                NeutroniumArmorMaterials.NEUTRONIUM, type,
                armourProperties(type, NeutroniumArmorMaterials.DURABILITY_MULTIPLIER),
                ElysiumElement.NONE, ElysiumRarities.LEGENDARY));
    }

    private static DeferredHolder<Item, Item> weapon(String name, ElysiumElement element, int tier,
                                                     net.minecraft.world.item.Tier material,
                                                     float damage, float speed) {
        return register(name, () -> new ElysiumWeaponItem(material, element, tier, damage, speed));
    }

    /**
     * One tool shape in one material. The factory keeps the four shapes from
     * needing four near-identical helpers.
     */
    private static DeferredHolder<Item, Item> rune(String name, ElysiumRune rune) {
        return register(name, () -> new ElysiumRuneItem(rune));
    }

    /**
     * Armour durability is no longer carried by the material in 1.21.1 - it is
     * set on the item properties instead.
     */
    /**
     * Armour properties for a generated material piece.
     *
     * Durability is not part of {@code ArmorMaterial} in 1.21.1 — it lives on
     * the item — so the material's own multiplier is carried through the
     * ArmourProfile and applied here.
     */
    public static Item.Properties materialArmourProperties(
            ArmorItem.Type type, com.elysium.lib.item.ElysiumGearMaterial.ArmourProfile profile) {
        return armourProperties(type, profile.durabilityMultiplier());
    }

    private static Item.Properties armourProperties(ArmorItem.Type type, int durabilityMultiplier) {
        return new Item.Properties().durability(type.getDurability(durabilityMultiplier));
    }
}
