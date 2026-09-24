package com.elysium.npcs.village;

import com.elysium.npcs.ElysiumNpcs;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Five trades a village did not have, and the benches they are done at.
 *
 * <h2>What a profession actually needs</h2>
 *
 * Three registrations that have to agree, in three different registries:
 *
 * <ol>
 *   <li>a <b>block</b> — the thing in the world;</li>
 *   <li>a <b>point of interest</b> naming that block's states, which is how a
 *       villager's brain finds it and claims it;</li>
 *   <li>a <b>profession</b> naming that point of interest, which is what the
 *       villager becomes when it does.</li>
 * </ol>
 *
 * Get the second wrong and the block is furniture: villagers walk past it
 * forever and nothing says why. Get the third wrong and the profession exists
 * with no way into it. Neither fails to load, so both are checked in
 * {@code validate.py} rather than trusted.
 *
 * <h2>Why the POI takes every state</h2>
 *
 * {@code PoiType} matches on {@code BlockState}, not on {@code Block}. These
 * benches have no properties today, so their state set is one entry — but the
 * day one of them grows a {@code facing}, a POI registered against the default
 * state alone would stop matching three rotations out of four, and the symptom
 * would be villagers who take the job only if the bench happens to point north.
 * Taking {@code getStateDefinition().getPossibleStates()} is correct now and
 * stays correct then.
 *
 * <h2>Sci-fi, and still a village</h2>
 *
 * Each of the five answers a job a village already has — signals for a
 * cartographer's information, splicing for a cleric's potions, salvage for a
 * toolsmith's metal — so they slot into an existing economy rather than
 * inventing a parallel one. The flavour is Elysium's; the role is a role a
 * player already understands.
 */
public final class ElysiumStations {

    private ElysiumStations() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, ElysiumNpcs.MODID);
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, ElysiumNpcs.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, ElysiumNpcs.MODID);

    /** One profession, and everything that has to exist for it to work. */
    public record Station(String role, String id,
                          DeferredHolder<Block, Block> block,
                          DeferredHolder<Item, Item> item,
                          DeferredHolder<PoiType, PoiType> poi,
                          DeferredHolder<VillagerProfession, VillagerProfession> profession) {
    }

    private static final List<Station> ALL = new ArrayList<>();

    public static List<Station> all() {
        return List.copyOf(ALL);
    }

    /**
     * Declare a profession and the bench it is claimed at.
     *
     * The three registrations are made together, in one call, because they are
     * one fact. Registering them in three separate lists is how a bench ends up
     * with no profession attached to it — and that is not a hypothetical, it is
     * what the POI registry is shaped to let you do.
     */
    private static Station station(String role, String id, MapColor colour) {
        DeferredHolder<Block, Block> block = BLOCKS.register(id,
                () -> new Block(BlockBehaviour.Properties.of()
                        .mapColor(colour)
                        .strength(2.5F, 6.0F)
                        .sound(SoundType.COPPER)));

        DeferredHolder<Item, Item> item = ElysiumNpcs.ITEMS.register(id,
                () -> new BlockItem(block.get(), new Item.Properties()));

        DeferredHolder<PoiType, PoiType> poi = POI_TYPES.register(id,
                () -> new PoiType(
                        // Every state, not just the default. See the class note.
                        new HashSet<>(block.get().getStateDefinition().getPossibleStates()),
                        1, 1));

        // The profession is named for the job and the block for the bench, and
        // the two are deliberately different words. The profession's registry
        // path is doing three jobs at once: it is the id, it is the villager's
        // clothing texture (textures/entity/villager/profession/<path>.png), and
        // vanilla builds the display name from it as
        // "entity.minecraft.villager.<path>" — dropping the namespace, which is
        // why that key appears in this mod's language file under minecraft's
        // prefix. Calling the profession "signal_mast" would put the name of a
        // block on a person.
        DeferredHolder<VillagerProfession, VillagerProfession> profession =
                PROFESSIONS.register(role,
                        () -> new VillagerProfession(
                                role,
                                held -> held.value() == poi.get(),
                                held -> held.value() == poi.get(),
                                ImmutableSet.of(),
                                ImmutableSet.of(),
                                SoundEvents.VILLAGER_WORK_TOOLSMITH));

        Station declared = new Station(role, id, block, item, poi, profession);
        ALL.add(declared);
        return declared;
    }

    // ------------------------------------------------------------------
    // The five
    // ------------------------------------------------------------------

    /** Reads the sky and sells what it hears. A cartographer's job, upward. */
    public static final Station SIGNALLER = station("signaller", "signal_mast", MapColor.COLOR_LIGHT_BLUE);

    /** Grows things that did not used to grow. Potions, by another route. */
    public static final Station SPLICER = station("splicer", "splicing_vat", MapColor.COLOR_GREEN);

    /** Melts what you bring and gives it back better. */
    public static final Station VOIDWRIGHT = station("voidwright", "void_crucible", MapColor.COLOR_PURPLE);

    /** Food without soil, which is what a station eats. */
    public static final Station HYDROPONICIST = station("hydroponicist", "hydroponics_rack", MapColor.COLOR_LIGHT_GREEN);

    /** Deals in parts, and in the plans for them. */
    public static final Station GEAR_SMITH = station("gear_smith", "gear_bench", MapColor.METAL);

    /** Takes the broken things nobody else wants. */
    public static final Station SALVAGER = station("salvager", "salvage_rig", MapColor.COLOR_ORANGE);

    // ------------------------------------------------------------------

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        POI_TYPES.register(modEventBus);
        PROFESSIONS.register(modEventBus);
    }

    /** The block ids, for the generator and the validator to agree on. */
    public static Set<String> ids() {
        Set<String> found = new HashSet<>();
        for (Station one : ALL) {
            found.add(one.id());
        }
        return found;
    }

    /** The profession ids. */
    public static Set<String> roles() {
        Set<String> found = new HashSet<>();
        for (Station one : ALL) {
            found.add(one.role());
        }
        return found;
    }
}
