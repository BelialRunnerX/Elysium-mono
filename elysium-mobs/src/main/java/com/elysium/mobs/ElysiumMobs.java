package com.elysium.mobs;

import com.elysium.lib.entity.ElysiumBestiary;
import com.elysium.lib.entity.ElysiumFaction;
import com.elysium.lib.entity.ElysiumScaling;
import com.elysium.lib.standing.ElysiumDispatch;
import com.elysium.mobs.boss.ElysiumBoss;
import com.elysium.mobs.boss.ElysiumBosses;
import com.elysium.mobs.entity.ElysiumFamilies;
import com.elysium.mobs.entity.ElysiumMob;
import com.elysium.mobs.variant.ElysiumVariants;
import com.elysium.mobs.variant.MobVariant;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Elysium Mobs — six families, thirty sub-variants, two bosses.
 *
 * <h2>What this mod is</h2>
 *
 * Opposition that means something at every level. Three Unsworn families and
 * three Imperial, each in five sub-variants that differ in how they fight
 * rather than in how big their numbers are, plus a boss for each side. Every
 * one of them scales on spawn to the players nearby and to how far up the Favor
 * and Suspicion meters those players are.
 *
 * <h2>What it knows about other mods: nothing</h2>
 *
 * It registers into the library's bestiary and faction rules and stops there.
 * It never mentions Elysium Dungeons, and Elysium Dungeons never mentions it —
 * the dungeon asks the bestiary for a creature and gets one if any mod has
 * offered any. Install both and dungeons fill with these; install either alone
 * and both still work.
 *
 * <h2>Eight entity types for thirty creatures</h2>
 *
 * The sub-variant is synced entity data rather than a separate registration.
 * See {@code MobVariant} for the reasoning; the short version is that thirty
 * registered types would be thirty models and thirty renderers for creatures
 * that differ in a texture and one ability.
 */
@Mod(ElysiumMobs.MODID)
public class ElysiumMobs {

    public static final String MODID = "elysiummobs";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, MODID);

    private static final List<DeferredHolder<Item, Item>> TAB_ORDER = new ArrayList<>();

    // ==================================================================
    // The six families
    // ==================================================================

    public static final DeferredHolder<EntityType<?>, EntityType<ElysiumFamilies.Scavenger>>
            SCAVENGER = family("scavenger", ElysiumFamilies.Scavenger::new, 0.6F, 1.5F);

    public static final DeferredHolder<EntityType<?>, EntityType<ElysiumFamilies.Reaver>>
            REAVER = family("reaver", ElysiumFamilies.Reaver::new, 0.9F, 2.3F);

    public static final DeferredHolder<EntityType<?>, EntityType<ElysiumFamilies.Whisper>>
            WHISPER = family("whisper", ElysiumFamilies.Whisper::new, 0.6F, 1.9F);

    public static final DeferredHolder<EntityType<?>, EntityType<ElysiumFamilies.Drone>>
            DRONE = family("drone", ElysiumFamilies.Drone::new, 0.8F, 1.2F);

    public static final DeferredHolder<EntityType<?>, EntityType<ElysiumFamilies.Lictor>>
            LICTOR = family("lictor", ElysiumFamilies.Lictor::new, 0.9F, 2.4F);

    public static final DeferredHolder<EntityType<?>, EntityType<ElysiumFamilies.Adept>>
            ADEPT = family("adept", ElysiumFamilies.Adept::new, 0.7F, 2.0F);

    // ==================================================================
    // The two bosses
    // ==================================================================

    public static final DeferredHolder<EntityType<?>, EntityType<ElysiumBosses.Choir>>
            CHOIR = family("choir", ElysiumBosses.Choir::new, 1.4F, 2.8F);

    public static final DeferredHolder<EntityType<?>, EntityType<ElysiumBosses.Praetor>>
            PRAETOR = family("praetor", ElysiumBosses.Praetor::new, 1.2F, 3.0F);

    // ==================================================================
    // Spawn eggs
    // ==================================================================
    //
    // One per family rather than per variant. An egg spawns a random
    // sub-variant, which is the same thing a dungeon or a dispatch does - and
    // thirty eggs for creatures that differ in a texture would be thirty items
    // nobody can tell apart in a creative tab.

    public static final DeferredHolder<Item, Item> SCAVENGER_EGG =
            egg("scavenger", SCAVENGER, 0x4a4038, 0x8fa86e);
    public static final DeferredHolder<Item, Item> REAVER_EGG =
            egg("reaver", REAVER, 0x2e2a26, 0xa8562e);
    public static final DeferredHolder<Item, Item> WHISPER_EGG =
            egg("whisper", WHISPER, 0x1e2028, 0x6e8fa8);
    public static final DeferredHolder<Item, Item> DRONE_EGG =
            egg("drone", DRONE, 0x2a3040, 0xd4a017);
    public static final DeferredHolder<Item, Item> LICTOR_EGG =
            egg("lictor", LICTOR, 0x181430, 0xa86ef0);
    public static final DeferredHolder<Item, Item> ADEPT_EGG =
            egg("adept", ADEPT, 0x241d38, 0x33d296);
    public static final DeferredHolder<Item, Item> CHOIR_EGG =
            egg("choir", CHOIR, 0x3a1414, 0xd44a2e);
    public static final DeferredHolder<Item, Item> PRAETOR_EGG =
            egg("praetor", PRAETOR, 0x2a2410, 0xe6c34d);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_TABS.register("elysiummobs", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiummobs"))
                    .icon(() -> new ItemStack(PRAETOR_EGG.get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredHolder<Item, Item> entry : TAB_ORDER) {
                            output.accept(entry.get());
                        }
                    })
                    .build());

    // ==================================================================

    public ElysiumMobs(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        modEventBus.addListener(ElysiumMobEvents::onAttributes);
        modEventBus.addListener(ElysiumMobEvents::onSpawnPlacements);

        // Touching the class runs the thirty registrations. Without it the
        // variants exist in the source and not in the game, and every creature
        // spawns unvariant - which is legal, silent, and wrong.
        ElysiumVariants.bootstrap();

        registerFactions();
        registerBestiary();
        registerDispatchers();

        LOGGER.info("Elysium Mobs ready: {} families, {} sub-variants, {} bestiary entries",
                ElysiumFamilies.ALL.length, MobVariant.REGISTRY.size(), ElysiumBestiary.size());
    }

    // ------------------------------------------------------------------
    // Telling the library what these are
    // ------------------------------------------------------------------

    /**
     * Which side each family is on.
     *
     * One rule for all eight types, asked of the mob itself rather than listed
     * here — so a family added later is classified correctly without this
     * method changing, and there is exactly one place a family's faction is
     * written down.
     */
    private static void registerFactions() {
        ElysiumFaction.addRule(entity ->
                entity instanceof ElysiumMob mob ? mob.getFaction() : null);

        // Every one of these is a named combatant: they are placed
        // deliberately, so they always pay standing and loot rather than
        // rolling for it like an incidental zombie.
        ElysiumFaction.addNamedCombatantRule(entity -> entity instanceof ElysiumMob);
    }

    /**
     * Offering the creatures to anything that wants one.
     *
     * This is the entire integration surface with Elysium Dungeons. Dungeons
     * asks the bestiary; the bestiary answers with these; neither mod imports
     * the other.
     */
    private static void registerBestiary() {
        bestiary("scavenger", ElysiumFaction.UNSWORN, ElysiumBestiary.Role.GRUNT, 4, SCAVENGER);
        bestiary("reaver", ElysiumFaction.UNSWORN, ElysiumBestiary.Role.ELITE, 2, REAVER);
        bestiary("whisper", ElysiumFaction.UNSWORN, ElysiumBestiary.Role.GRUNT, 3, WHISPER);
        bestiary("drone", ElysiumFaction.EMPIRE, ElysiumBestiary.Role.GRUNT, 4, DRONE);
        bestiary("lictor", ElysiumFaction.EMPIRE, ElysiumBestiary.Role.ELITE, 2, LICTOR);
        bestiary("adept", ElysiumFaction.EMPIRE, ElysiumBestiary.Role.GRUNT, 2, ADEPT);

        bossEntry("choir", ElysiumFaction.UNSWORN, CHOIR);
        bossEntry("praetor", ElysiumFaction.EMPIRE, PRAETOR);
    }

    private static void bestiary(String path, ElysiumFaction faction,
                                 ElysiumBestiary.Role role, int weight,
                                 DeferredHolder<EntityType<?>, ? extends EntityType<? extends ElysiumMob>> type) {
        ElysiumBestiary.register(ResourceLocation.fromNamespaceAndPath(MODID, path),
                new ElysiumBestiary.Entry(faction, role, weight, (level, where, mobLevel) -> {
                    ElysiumMob mob = type.get().create(level);
                    if (mob == null) {
                        return null;
                    }
                    mob.initialise(mobLevel, level.getRandom());
                    return mob;
                }));
    }

    private static void bossEntry(String path, ElysiumFaction faction,
                                  DeferredHolder<EntityType<?>, ? extends EntityType<? extends ElysiumBoss>> type) {
        ElysiumBestiary.register(ResourceLocation.fromNamespaceAndPath(MODID, path + "_boss"),
                new ElysiumBestiary.Entry(faction, ElysiumBestiary.Role.BOSS, 1,
                        (level, where, mobLevel) -> {
                            ElysiumBoss boss = type.get().create(level);
                            if (boss == null) {
                                return null;
                            }
                            boss.initialiseBoss(mobLevel, level.getRandom());
                            return boss;
                        }));
    }

    /**
     * Standing sends these after you.
     *
     * Registered as dispatchers as well as bestiary entries, because the two
     * answer different questions: the bestiary is "give me a creature", and a
     * dispatcher is "the meter is high enough that one should arrive". Sharing
     * the factory between them keeps a dispatched Lictor and a dungeon Lictor
     * the same creature.
     */
    private static void registerDispatchers() {
        dispatcher(ElysiumFaction.EMPIRE, LICTOR);
        dispatcher(ElysiumFaction.UNSWORN, REAVER);
    }

    private static void dispatcher(ElysiumFaction faction,
                                   DeferredHolder<EntityType<?>, ? extends EntityType<? extends ElysiumMob>> type) {
        ElysiumDispatch.register(new ElysiumDispatch.Dispatcher() {
            @Override
            public ElysiumFaction faction() {
                return faction;
            }

            @Override
            public Class<? extends Mob> type() {
                return ElysiumMob.class;
            }

            @Override
            public Mob create(ServerLevel level, int band) {
                return type.get().create(level);
            }

            @Override
            public void afterPlaced(Mob mob, ServerLevel level,
                                    net.minecraft.world.entity.player.Player player,
                                    BlockPos pos, int band) {
                // Scaled after placement, because the level depends on who is
                // nearby and the mob is not anywhere until it has been placed.
                if (mob instanceof ElysiumMob elysium) {
                    elysium.initialise(
                            ElysiumScaling.levelFor(level, pos, faction), level.getRandom());
                }
            }
        });
    }

    // ------------------------------------------------------------------

    private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> family(
            String name, EntityType.EntityFactory<T> factory, float width, float height) {
        return ENTITIES.register(name, () -> EntityType.Builder.of(factory, MobCategory.MONSTER)
                .sized(width, height)
                .clientTrackingRange(10)
                .build(name));
    }

    private static DeferredHolder<Item, Item> egg(String name,
                                                  DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> type,
                                                  int background, int highlight) {
        DeferredHolder<Item, Item> holder = ITEMS.register(name + "_spawn_egg",
                () -> new DeferredSpawnEggItem(type, background, highlight, new Item.Properties()));
        TAB_ORDER.add(holder);
        return holder;
    }

    /** A source for anything that needs one outside an entity. */
    public static RandomSource random(ServerLevel level) {
        return level.getRandom();
    }
}
