package com.elysium.colonies.colony;

import com.elysium.colonies.ElysiumColonies;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.blocks.AbstractColonyBlock;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.registry.IBuildingRegistry;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.Map;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.api.util.CreativeBuildingStructureHandler;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.mojang.authlib.GameProfile;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.DynamicHappinessSupplier;
import com.minecolonies.api.entity.citizen.happiness.HappinessRegistry;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * The only file in this module that knows MineColonies exists.
 *
 * <h2>Why it is only one file</h2>
 *
 * Because an add-on is a bet on somebody else's API, and the way to lose that
 * bet cheaply is to make it in one place. MineColonies is a fast-moving mod;
 * when a method here changes name, exactly one file fails to compile and
 * everything else carries on speaking about {@link Settlement}s,
 * {@link Person}s and {@link Structure}s. The validator enforces the rule by
 * counting the files that name the host.
 *
 * <h2>Why the records are flat</h2>
 *
 * A wrapper around an {@code IColony} leaks the type it wraps the moment
 * anybody wants a third thing out of it. So everything that crosses this
 * boundary is a record of plain values, and the one place that needs the live
 * object back — ordering an upgrade — takes a callback rather than handing the
 * object out.
 */
public final class Colonies {

    private Colonies() {
    }

    // ------------------------------------------------------------------
    // What crosses the boundary
    // ------------------------------------------------------------------

    /**
     * A settlement, as the rest of this module needs to talk about one.
     *
     * @param id        the colony's own number, stable for its life
     * @param name      what it is called
     * @param citizens  how many people live there
     * @param owner     the player it belongs to, or null for an abandoned one
     * @param dimension the world it stands in
     * @param centre    its town hall
     * @param day       how many days old it is, by the colony's own count
     * @param happiness the colony's overall happiness, as MineColonies reads it
     */
    public record Settlement(int id, String name, int citizens, @Nullable UUID owner,
                             ResourceLocation dimension, BlockPos centre, int day,
                             double happiness) {
    }

    /**
     * One inhabitant.
     *
     * @param id        their number within the colony
     * @param name      what they are called
     * @param job       the id of their work, or "" for the unemployed
     * @param housed    whether they have somewhere to live
     * @param employed  whether they have somewhere to work
     * @param idle      whether they are at work and have nothing to do
     * @param saturation how well fed, on MineColonies' scale
     * @param health    their health as a share of their maximum, or 1 when the
     *                  person is not currently loaded as an entity
     * @param at        where they are, or the colony centre when not loaded
     * @param actions   how many pieces of work their job has finished
     */
    public record Person(int id, String name, String job, boolean housed,
                         boolean employed, boolean idle, double saturation,
                         float health, BlockPos at, int actions) {
    }

    /**
     * One building.
     *
     * @param at       where it stands
     * @param kind     the id of what it is
     * @param level    how far it has been built
     * @param maximum  how far it could be
     * @param building whether a work order for it is already outstanding
     */
    public record Structure(BlockPos at, String kind, int level, int maximum,
                            boolean building) {
    }

    // ------------------------------------------------------------------
    // Finding a settlement
    // ------------------------------------------------------------------

    /** The settlement this player owns in this world, or null for none. */
    @Nullable
    public static Settlement ownedBy(Player player) {
        Level level = player.level();
        if (level == null || level.isClientSide) {
            // Colonies are server-side data. Asking on the client returns a
            // view object with different numbers on it, which is exactly the
            // kind of quiet disagreement this module must not introduce.
            return null;
        }
        return read(IColonyManager.getInstance().getIColonyByOwner(level, player));
    }

    /**
     * The settlement nearest to something, or null if there is none nearby.
     *
     * Used for raiders, which is the case that needs it: a raider is put into
     * the world and told which colony it belongs to <em>afterwards</em>, so at
     * the moment it appears its own colony is still null and the only honest
     * answer is the one under its feet.
     */
    @Nullable
    public static Settlement around(Entity entity) {
        Level level = entity.level();
        if (level == null || level.isClientSide) {
            return null;
        }
        if (entity instanceof AbstractEntityMinecoloniesRaider raider) {
            IColony told = raider.getColony();
            if (told != null) {
                return read(told);
            }
        }
        return read(IColonyManager.getInstance()
                .getClosestIColony(level, entity.blockPosition()));
    }

    /**
     * Every settlement in this world this player belongs to.
     *
     * <h3>Why a list</h3>
     *
     * Because MineColonies never said it had to be one. Membership lives as a
     * map inside each colony with no uniqueness check anywhere, so a player has
     * always been able to belong to several — nobody had asked the question
     * from the outside. A frontier where a person can hold a share in three
     * settlements is a better frontier than one where joining a friend's town
     * means giving up your own.
     *
     * <p>Owned or merely joined: both count. What this module does with a
     * settlement — lift a world tier, keep a file open on its owner — is about
     * having a stake in a place, and a stake is a stake.
     */
    public static List<Settlement> memberOf(Player player) {
        List<Settlement> out = new ArrayList<>();
        Level level = player.level();
        if (level == null || level.isClientSide) {
            return out;
        }
        for (IColony colony : IColonyManager.getInstance().getColonies(level)) {
            if (colony.getPermissions().isColonyMember(player)) {
                Settlement read = read(colony);
                if (read != null) {
                    out.add(read);
                }
            }
        }
        return out;
    }

    /**
     * Adds somebody to a settlement without disturbing who owns it.
     *
     * Deliberately not the host's own "abandon" path, which is what a player
     * reaching for this usually finds first: that one renames the owner to
     * {@code [abandoned]} and demotes them, which is a thing to do to your own
     * colony once, not a way to let a friend in. This is the plain membership
     * call, and it touches nothing else.
     *
     * @param rank one of {@link #RANK_FRIEND} or {@link #RANK_OFFICER}
     */
    public static boolean admit(Level level, int colonyId, Player joining, int rank) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return false;
        }
        IPermissions permissions = colony.getPermissions();
        Rank at = rank == RANK_OFFICER ? permissions.getRankOfficer()
                : permissions.getRankFriend();
        return permissions.addPlayer(joining.getGameProfile(), at);
    }

    /**
     * Takes somebody out of a settlement.
     *
     * Never the owner. The host treats an owner leaving as abandonment — it
     * renames them, opens the colony's permissions up and can mark it fully
     * abandoned once the officers are gone — and that is a decision with
     * consequences a "leave" command should not make on somebody's behalf.
     *
     * @return false when the player was the owner, or was not a member
     */
    public static boolean release(Level level, int colonyId, java.util.UUID leaving) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null || leaving == null) {
            return false;
        }
        IPermissions permissions = colony.getPermissions();
        if (leaving.equals(permissions.getOwner())) {
            return false;
        }
        if (!permissions.getPlayers().containsKey(leaving)) {
            return false;
        }
        return permissions.removePlayer(leaving);
    }

    /** Whether this player is the one whose name is on the settlement. */
    public static boolean owns(Level level, int colonyId, java.util.UUID who) {
        IColony colony = colonyIn(level, colonyId);
        return colony != null && who != null
                && who.equals(colony.getPermissions().getOwner());
    }

    /** How many people hold a stake in a settlement. */
    public static int membersOf(Level level, int colonyId) {
        IColony colony = colonyIn(level, colonyId);
        return colony == null ? 0 : colony.getPermissions().getPlayers().size();
    }

    /** A rank that may come and go and use the place. */
    public static final int RANK_FRIEND = 0;

    /** A rank that may also run it. */
    public static final int RANK_OFFICER = 1;

    /** Every settlement in this world. */
    public static List<Settlement> in(Level level) {
        List<Settlement> out = new ArrayList<>();
        if (level == null || level.isClientSide) {
            return out;
        }
        for (IColony colony : IColonyManager.getInstance().getColonies(level)) {
            Settlement read = read(colony);
            if (read != null) {
                out.add(read);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Looking inside one
    // ------------------------------------------------------------------

    /** Everybody who lives in a settlement. */
    public static List<Person> peopleOf(Level level, int colonyId) {
        List<Person> out = new ArrayList<>();
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return out;
        }
        for (ICitizenData data : colony.getCitizenManager().getCitizens()) {
            out.add(person(data, colony.getCenter()));
        }
        return out;
    }

    /** Everything a settlement has built. */
    public static List<Structure> structuresOf(Level level, int colonyId) {
        List<Structure> out = new ArrayList<>();
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return out;
        }
        for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
            out.add(new Structure(building.getPosition(),
                    String.valueOf(building.getBuildingDisplayName()),
                    building.getBuildingLevel(),
                    building.getMaxBuildingLevel(),
                    building.isPendingConstruction()));
        }
        return out;
    }

    /** How much of a thing a settlement has done in its life. */
    public static int statistic(Level level, int colonyId, String name) {
        IColony colony = colonyIn(level, colonyId);
        return colony == null ? 0 : colony.getStatisticsManager().getStatTotal(name);
    }

    /** How many pieces of research a settlement has finished. */
    public static int researchDone(Level level, int colonyId) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return 0;
        }
        return colony.getResearchManager().getResearchTree().getCompletedList().size();
    }

    // ------------------------------------------------------------------
    // Asking one to do something
    // ------------------------------------------------------------------

    /**
     * Asks a settlement to raise one of its own buildings.
     *
     * <h3>Why not the host's own {@code requestUpgrade}</h3>
     *
     * Because that method takes a Player, and on three of its branches it
     * hands that Player straight to a message sender with no null check — so
     * calling it with nobody there works right up until the upgrade is refused
     * and then throws. A town that runs itself cannot be one tap away from a
     * crash whenever the thing it asked for is not allowed.
     *
     * <p>So the work order is filed directly, which is what that method does
     * on its happy path anyway. The order is the same order; what is dropped
     * is the part that wanted somebody to apologise to.
     *
     * @return whether an order was filed
     */
    public static boolean raise(Level level, int colonyId, BlockPos where) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return false;
        }
        IBuilding building = colony.getServerBuildingManager().getBuilding(where);
        if (building == null || building.isPendingConstruction()) {
            return false;
        }
        if (building.getBuildingLevel() >= building.getMaxBuildingLevel()) {
            return false;
        }
        return order(colony, building, WorkOrderType.UPGRADE);
    }

    /**
     * Asks a settlement to go over one of its buildings again.
     *
     * The host's repair order is already player-free — it walks the blueprint
     * and replaces whatever no longer matches. Nothing in MineColonies decides
     * that a building <em>needs</em> repairing; it is an instruction, not a
     * diagnosis. So a town that runs itself simply re-issues it now and then,
     * which is what a caretaker does.
     */
    public static boolean repair(Level level, int colonyId, BlockPos where) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return false;
        }
        IBuilding building = colony.getServerBuildingManager().getBuilding(where);
        if (building == null || building.isPendingConstruction()
                || building.getBuildingLevel() <= 0) {
            return false;
        }
        building.requestRepair(where);
        return true;
    }

    /**
     * Puts a new building into the world and asks for it to be built.
     *
     * <h3>What a player normally does</h3>
     *
     * Opens a build tool, picks a blueprint, points at the ground, places the
     * hut block, and the colony picks it up. Every step of that is available
     * without them: the hut block's own placement hook takes the placer as an
     * argument and never reads it, the building manager's registration takes
     * no player at all, and the blueprint can be found by name in the pack the
     * colony is already building from.
     *
     * @param kind  the building's registry id, {@code "minecolonies:builder"}
     * @param where the anchor to put it on
     * @return whether a building was placed and ordered
     */
    public static boolean found(ServerLevel level, int colonyId, String kind,
                                BlockPos where) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return false;
        }
        // The host's registry has no default and throws on a miss rather than
        // answering null, so a plan naming a building that does not exist
        // would crash the tick that founds it. Ask first.
        ResourceLocation kindId = ResourceLocation.parse(kind);
        if (!IBuildingRegistry.getInstance().containsKey(kindId)) {
            ElysiumColonies.LOGGER.warn("no such building as {}; the plan names it", kind);
            return false;
        }
        BuildingEntry entry = IBuildingRegistry.getInstance().get(kindId);
        if (entry == null) {
            return false;
        }
        AbstractColonyBlock<?> hut = entry.getBuildingBlock();
        if (hut == null) {
            return false;
        }
        String pack = colony.getStructurePack();
        String blueprint = blueprintFor(pack, entry);
        if (blueprint == null) {
            // No blueprint of that name in this colony's pack. Better to build
            // nothing than to place a hut the builder will never be able to
            // raise, which is a block a player then has to find and break.
            return false;
        }

        net.minecraft.world.level.block.state.BlockState state =
                hut.defaultBlockState().setValue(AbstractColonyBlock.FACING,
                        net.minecraft.core.Direction.NORTH);
        if (!level.setBlock(where, state, 3)) {
            return false;
        }
        hut.setPlacedBy(level, where, state, null, ItemStack.EMPTY);

        IBuilding placed = colony.getServerBuildingManager().getBuilding(where);
        if (placed == null) {
            level.removeBlock(where, false);
            return false;
        }
        placed.setStructurePack(pack);
        placed.setBlueprintPath(blueprint);
        return order(colony, placed, WorkOrderType.BUILD);
    }

    /**
     * Finds the level-one blueprint for a building in a pack.
     *
     * MineColonies' packs name their files after the building and its level —
     * {@code builder1}, {@code house1} — so the search is by name rather than
     * by walking categories, which differ between packs. The path handed back
     * is relative to the pack root, which is the form a building stores.
     */
    @Nullable
    private static String blueprintFor(String pack, BuildingEntry entry) {
        try {
            StructurePackMeta meta = StructurePacks.getStructurePack(pack);
            if (meta == null) {
                return null;
            }
            java.nio.file.Path found = StructurePacks.findBlueprint(pack,
                    entry.getRegistryName().getPath() + "1");
            if (found == null) {
                return null;
            }
            return meta.getPath().relativize(found).toString().replace('\\', '/');
        } catch (Throwable unavailable) {
            // Structurize not answering, or a pack that has gone away under
            // us. Not building is always a safe answer.
            return null;
        }
    }

    private static boolean order(IColony colony, IBuilding building, WorkOrderType type) {
        try {
            colony.getWorkManager().addWorkOrder(
                    WorkOrderBuilding.create(type, building), false);
            return true;
        } catch (Throwable refused) {
            ElysiumColonies.LOGGER.warn("a work order was refused: {}",
                    refused.toString());
            return false;
        }
    }

    /**
     * Gives a settlement the style of the world it stands on.
     *
     * <h3>Once, and then it is the player's</h3>
     *
     * Set when this module first meets a settlement and never again. A style
     * is a visible, opinionated thing — it decides what every building in the
     * town looks like and what it costs — and a player who has deliberately
     * chosen a different one should not find it changed back every morning.
     * The charter's answer is a default, not a rule.
     *
     * @return whether the style was changed, and whether it could be
     */
    public static boolean style(Level level, int colonyId, String pack) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null || pack == null || pack.isEmpty()) {
            return false;
        }
        try {
            if (!StructurePacks.hasPack(pack)) {
                // The style is not installed. That is the normal state for a
                // world whose mod is absent, and building in whatever the
                // colony already had is the right answer rather than an error.
                return false;
            }
        } catch (Throwable unavailable) {
            return false;
        }
        if (pack.equals(colony.getStructurePack())) {
            return false;
        }
        colony.setStructurePack(pack);
        return true;
    }

    /** Whether a settlement already has a building of this kind. */
    public static boolean has(Level level, int colonyId, String kind) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return false;
        }
        ResourceLocation wanted = ResourceLocation.parse(kind);
        for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
            if (wanted.equals(building.getBuildingType().getRegistryName())) {
                return true;
            }
        }
        return false;
    }

    /** Whether anything at all stands within this many blocks of a spot. */
    public static boolean clearOf(Level level, int colonyId, BlockPos where, int within) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return false;
        }
        for (BlockPos taken : colony.getServerBuildingManager().getBuildings().keySet()) {
            if (taken.distSqr(where) < (double) within * within) {
                return false;
            }
        }
        return true;
    }

    /** Whether a spot is inside the settlement's own ground. */
    public static boolean inside(Level level, int colonyId, BlockPos where) {
        IColony colony = colonyIn(level, colonyId);
        return colony != null && colony.isCoordInColony(level, where);
    }

    // ------------------------------------------------------------------
    // What a settlement is waiting for, and what it has spare
    // ------------------------------------------------------------------

    /**
     * One thing a town is waiting for somebody to bring it.
     *
     * @param token  the request's own handle, opaque to everything but this file
     * @param wanted the item, one of it
     * @param count  how many
     */
    public record Owed(Object token, ItemStack wanted, int count) {
    }

    /**
     * Everything sitting on the town's "a player will have to bring this" list.
     *
     * This is the deadlock the exchange exists to break. When every resolver a
     * colony has has failed, the request lands on the player resolver and
     * waits — forever, if nobody is coming. Reading that list is reading
     * exactly the set of things stopping the town.
     */
    public static List<Owed> owed(Level level, int colonyId) {
        List<Owed> out = new ArrayList<>();
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return out;
        }
        IRequestManager requests = colony.getRequestManager();
        for (IToken<?> token : requests.getPlayerResolver().getAllAssignedRequests()) {
            IRequest<?> request;
            try {
                request = requests.getRequestForToken(token);
            } catch (Throwable gone) {
                continue;
            }
            if (request == null) {
                continue;
            }
            IDeliverable deliverable =
                    request.getRequestOfType(IDeliverable.class).orElse(null);
            if (deliverable == null) {
                continue;
            }
            List<ItemStack> shown = request.getDisplayStacks();
            if (shown == null || shown.isEmpty()) {
                continue;
            }
            out.add(new Owed(token, shown.get(0).copy(),
                    Math.max(1, deliverable.getCount())));
        }
        return out;
    }

    /**
     * Hands a town the thing it was waiting for.
     *
     * The same call the "deliver this" button in the town hall makes, so the
     * request closes the way the host expects rather than being cancelled and
     * re-made.
     */
    public static boolean deliver(Level level, int colonyId, Owed owed, int count) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null || !(owed.token() instanceof IToken<?> token)) {
            return false;
        }
        ItemStack giving = owed.wanted().copy();
        giving.setCount(Math.max(1, count));
        try {
            colony.getRequestManager().overruleRequest(token, giving);
            return true;
        } catch (Throwable refused) {
            return false;
        }
    }

    /** How many of an item a town has in its warehouses. */
    public static int stockOf(Level level, int colonyId, ItemStack like) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return 0;
        }
        int held = 0;
        for (IBuilding store : colony.getServerBuildingManager().getBuildings().values()) {
            for (IItemHandler handler : store.getHandlers()) {
                held += InventoryUtils.getItemCountInItemHandler(handler,
                        stack -> ItemStack.isSameItem(stack, like));
            }
        }
        return held;
    }

    /**
     * Everything a town has more than a stack of, as item and count.
     *
     * Read across every building rather than only the warehouses, because a
     * town without a warehouse is exactly the sort of town that gets stuck,
     * and its surplus is sitting in huts.
     */
    public static Map<ItemStack, Integer> surplusOf(Level level, int colonyId,
                                                    int keep) {
        Map<String, Integer> counted = new java.util.LinkedHashMap<>();
        Map<String, ItemStack> examples = new java.util.LinkedHashMap<>();
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return Map.of();
        }
        for (IBuilding store : colony.getServerBuildingManager().getBuildings().values()) {
            for (IItemHandler handler : store.getHandlers()) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack held = handler.getStackInSlot(slot);
                    if (held.isEmpty()) {
                        continue;
                    }
                    String id = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
                    counted.merge(id, held.getCount(), Integer::sum);
                    examples.putIfAbsent(id, held.copy());
                }
            }
        }
        Map<ItemStack, Integer> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : counted.entrySet()) {
            int spare = entry.getValue() - Math.max(0, keep);
            if (spare > 0) {
                out.put(examples.get(entry.getKey()), spare);
            }
        }
        return out;
    }

    /**
     * Takes items out of a town's own stores.
     *
     * @return how many were actually taken, which may be fewer than asked
     */
    public static int take(Level level, int colonyId, ItemStack like, int count) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null || count <= 0) {
            return 0;
        }
        int left = count;
        for (IBuilding store : colony.getServerBuildingManager().getBuildings().values()) {
            for (IItemHandler handler : store.getHandlers()) {
                for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
                    ItemStack held = handler.getStackInSlot(slot);
                    if (held.isEmpty() || !ItemStack.isSameItem(held, like)) {
                        continue;
                    }
                    ItemStack taken = handler.extractItem(slot, left, false);
                    left -= taken.getCount();
                }
            }
        }
        return count - left;
    }

    /**
     * The one thing this module writes into its host.
     *
     * <h3>Why happiness and not a bar of our own</h3>
     *
     * A colony already has a happiness screen and a player already reads it. A
     * second, parallel, Elysium-flavoured misery meter would be two numbers
     * that disagree about the same person. So what this module has worked out
     * about a citizen's life is handed to MineColonies as one more reason,
     * beside its own housing and food and security, in the place a player is
     * already looking.
     *
     * <h3>How it is installed</h3>
     *
     * MineColonies keeps two registries: one of modifier <em>types</em> and
     * one of the functions a modifier may consult. An add-on registers a
     * function once, at load, and then hands each colony a modifier that names
     * it. That indirection is what lets the modifier be written to disk and
     * read back — the saved form is the function's id, not a lambda.
     */
    public static final ResourceLocation HAPPINESS_FUNCTION =
            ResourceLocation.fromNamespaceAndPath("elysiumcolonies", "frontier");

    /**
     * The registry MineColonies keeps its happiness functions in.
     *
     * Built from its name rather than read off the field that holds it,
     * because that field lives in the host's implementation package rather
     * than its api, and reaching into an implementation is how an add-on
     * breaks on a version bump it had no business caring about.
     */
    private static final net.minecraft.resources.ResourceKey<
            net.minecraft.core.Registry<HappinessRegistry.HappinessFunctionEntry>>
            HAPPINESS_FUNCTIONS = net.minecraft.resources.ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath("minecolonies",
                            "happinessfunction"));

    /**
     * What this module is asked, per citizen, when the colony wants a number.
     *
     * Takes the world and the colony as well as the person, because what it
     * knows about them is level data keyed by both — a Person alone is a name
     * and a job, and the thing worth asking about is the life behind it.
     */
    @FunctionalInterface
    public interface Reason {
        double of(net.minecraft.server.level.ServerLevel level, int colonyId,
                  Person person);
    }

    /** Where the answer comes from. Set once, by {@link #registerHappiness}. */
    @Nullable
    private static Reason reason;

    /**
     * Registers this module's one happiness function.
     *
     * Called from the mod constructor with the mod event bus. The function is
     * asked per citizen and answers with a multiplier on the neutral one.
     */
    public static void registerHappiness(net.neoforged.bus.api.IEventBus bus,
                                         Reason answer) {
        reason = answer;
        net.neoforged.neoforge.registries.DeferredRegister<
                HappinessRegistry.HappinessFunctionEntry> functions =
                net.neoforged.neoforge.registries.DeferredRegister.create(
                        HAPPINESS_FUNCTIONS, HAPPINESS_FUNCTION.getNamespace());
        functions.register(HAPPINESS_FUNCTION.getPath(),
                () -> new HappinessRegistry.HappinessFunctionEntry(data -> {
                    Reason asked = reason;
                    if (asked == null || data == null) {
                        return 1.0D;
                    }
                    IColony colony = data.getColony();
                    if (colony == null
                            || !(colony.getWorld()
                                 instanceof net.minecraft.server.level.ServerLevel level)) {
                        return 1.0D;
                    }
                    return asked.of(level, colony.getID(),
                            person(data, colony.getCenter()));
                }));
        functions.register(bus);
    }

    /**
     * Makes sure a settlement's people carry this module's reason.
     *
     * Asked on the daily tick rather than installed on a colony-created event,
     * so a world that had colonies in it before this module was added picks
     * them up too — and so that load order between two mods cannot leave a
     * colony without one.
     *
     * @return true when it had to be installed, so a caller can say so
     */
    public static boolean ensureHappiness(Level level, int colonyId, double weight) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return false;
        }
        String id = HAPPINESS_FUNCTION.toString();
        for (ICitizenData data : colony.getCitizenManager().getCitizens()) {
            if (data.getCitizenHappinessHandler().getModifier(id) != null) {
                return false;              // already carried; nothing to do
            }
            break;                         // one is enough to tell
        }
        colony.getCitizenManager().injectModifier(new StaticHappinessModifier(
                id, weight, new DynamicHappinessSupplier(HAPPINESS_FUNCTION)));
        return true;
    }

    // ------------------------------------------------------------------
    // Raiders
    // ------------------------------------------------------------------

    /** Whether this creature is something a colony raid sent. */
    public static boolean isRaider(Entity entity) {
        return entity instanceof AbstractEntityMinecoloniesRaider;
    }

    /** The same, narrowed to a Mob, which is what promotion needs. */
    @Nullable
    public static Mob raiderOf(Entity entity) {
        return entity instanceof AbstractEntityMinecoloniesRaider raider ? raider : null;
    }

    /** Whether this creature is one of a colony's own people. */
    public static boolean isCitizen(Entity entity) {
        return entity instanceof AbstractEntityCitizen;
    }

    /** Which settlement and person a citizen entity is, or null. */
    @Nullable
    public static Person personOf(Entity entity) {
        if (!(entity instanceof AbstractEntityCitizen citizen)) {
            return null;
        }
        ICitizenData data = citizen.getCitizenData();
        if (data == null) {
            return null;
        }
        return person(data, entity.blockPosition());
    }

    /** The colony a citizen entity belongs to, or zero for none. */
    public static int colonyOf(Entity entity) {
        if (!(entity instanceof AbstractEntityCitizen citizen)) {
            return 0;
        }
        ICitizenData data = citizen.getCitizenData();
        IColony colony = data == null ? null : data.getColony();
        return colony == null ? 0 : colony.getID();
    }

    /** Whether MineColonies is actually loaded and answering. */
    public static boolean present() {
        try {
            return IColonyManager.getInstance() != null;
        } catch (Throwable missing) {
            // A hard dependency is declared, so this should never happen; it is
            // here so that a pack which removed the host at the wrong moment
            // fails quietly rather than throwing on every tick.
            return false;
        }
    }

    // ------------------------------------------------------------------

    @Nullable
    private static IColony colonyIn(Level level, int colonyId) {
        if (level == null || level.isClientSide) {
            return null;
        }
        return IColonyManager.getInstance().getColonyByWorld(colonyId, level);
    }

    @Nullable
    private static Settlement read(@Nullable IColony colony) {
        if (colony == null) {
            return null;
        }
        return new Settlement(
                colony.getID(),
                String.valueOf(colony.getName()),
                colony.getCitizenManager().getCurrentCitizenCount(),
                colony.getPermissions().getOwner(),
                colony.getDimension().location(),
                colony.getCenter(),
                colony.getDay(),
                colony.getOverallHappiness());
    }

    private static Person person(ICitizenData data, BlockPos fallback) {
        AbstractEntityCitizen body = data.getEntity().orElse(null);
        float health = 1.0F;
        BlockPos at = fallback;
        if (body != null) {
            LivingEntity living = body;
            float most = Math.max(1.0F, living.getMaxHealth());
            health = Math.max(0.0F, Math.min(1.0F, living.getHealth() / most));
            at = body.blockPosition();
        }
        String job = data.getJob() == null ? ""
                : String.valueOf(data.getJob().getJobRegistryEntry().getKey());
        int actions = data.getJob() == null ? 0 : data.getJob().getActionsDone();
        return new Person(data.getId(), String.valueOf(data.getName()), job,
                data.getHomeBuilding() != null, data.getWorkBuilding() != null,
                data.isIdleAtJob(), data.getSaturation(), health, at, actions);
    }

    // --- towns that found themselves -------------------------------------
    //
    // Four more writes into the host, each the instruction a player gives:
    // creating a colony (the town hall's build tool), completing a building
    // whole (the build tool in creative), and handing a colony to a new owner
    // (the town hall's own permissions screen). They exist for a settlement
    // no player made, whose steward is a fake player with a name, and they
    // are all here, in the seam, where the other writes are.

    /** The id of MineColonies' town hall, as its building registry knows it. */
    private static final String TOWN_HALL = "minecolonies:townhall";

    /**
     * One thing a town is waiting to have built.
     *
     * @param id          the work order's number
     * @param blueprint   the blueprint it builds, relative to the pack
     * @param where       the hut it belongs to
     * @param targetLevel the level it builds to
     * @param kind        build, upgrade, repair or remove
     * @param claimed     whether a builder has taken it
     */
    public record Order(int id, String blueprint, BlockPos where, int targetLevel,
                        String kind, boolean claimed) {
    }

    /** Whether the host would allow a colony here at all. */
    public static boolean mayFoundAt(ServerLevel level, BlockPos where) {
        try {
            IColonyManager manager = IColonyManager.getInstance();
            return manager.getColonyByPosFromWorld(level, where) == null
                    && manager.isFarEnoughFromColonies(level, where);
        } catch (Throwable unavailable) {
            return false;
        }
    }

    /**
     * Founds a colony with nobody at the keyboard.
     *
     * The owner is a fake player with the steward's name: MineColonies wants
     * an owner, its permissions are keyed by one, and a real player's would
     * make the town theirs. The town hall goes down the way {@link #found}
     * puts down any hut, which is what registers the building.
     *
     * @return the new colony's id, or -1 when the host refused
     */
    public static int settle(ServerLevel level, BlockPos where, String name, String pack,
                             UUID stewardId, String stewardName) {
        IColonyManager manager = IColonyManager.getInstance();
        if (manager.getColonyByPosFromWorld(level, where) != null) {
            return -1;
        }
        IColony colony;
        try {
            Player steward = FakePlayerFactory.get(level, new GameProfile(stewardId, stewardName));
            colony = manager.createColony(level, where, steward, name, pack);
        } catch (Throwable refused) {
            ElysiumColonies.LOGGER.warn("the host refused to found {} at {}: {}", name, where,
                    refused.toString());
            return -1;
        }
        if (colony == null) {
            return -1;
        }
        BuildingEntry entry = IBuildingRegistry.getInstance().containsKey(ResourceLocation.parse(TOWN_HALL))
                ? IBuildingRegistry.getInstance().get(ResourceLocation.parse(TOWN_HALL)) : null;
        AbstractColonyBlock<?> hut = entry == null ? null : entry.getBuildingBlock();
        if (hut != null) {
            net.minecraft.world.level.block.state.BlockState state =
                    hut.defaultBlockState().setValue(AbstractColonyBlock.FACING,
                            net.minecraft.core.Direction.NORTH);
            if (level.setBlock(where, state, 3)) {
                hut.setPlacedBy(level, where, state, null, ItemStack.EMPTY);
                IBuilding hall = colony.getServerBuildingManager().getBuilding(where);
                if (hall != null) {
                    hall.setStructurePack(pack);
                    String blueprint = blueprintFor(pack, entry);
                    if (blueprint != null) {
                        hall.setBlueprintPath(blueprint);
                    }
                    order(colony, hall, WorkOrderType.BUILD);
                }
            }
        }
        return colony.getID();
    }

    /** Everything a settlement is waiting to have built, oldest first. */
    public static List<Order> orders(Level level, int colonyId) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return List.of();
        }
        List<Order> out = new ArrayList<>();
        for (IServerWorkOrder order : colony.getWorkManager().getWorkOrders().values()) {
            out.add(new Order(order.getID(), order.getStructurePath(), order.getLocation(),
                    order.getTargetLevel(),
                    order.getWorkOrderType().name().toLowerCase(java.util.Locale.ROOT),
                    order.isClaimed()));
        }
        out.sort(java.util.Comparator.comparingInt(Order::id));
        return out;
    }

    /**
     * Completes a work order whole, as the build tool does in creative.
     *
     * For a town that founded itself: nobody restocks its warehouse, so a
     * builder waiting for planks would wait forever. The blueprint is placed
     * in one go, the building takes its level from what now stands, and the
     * order is struck. Taking a building down is not done: a town may build
     * its way up by itself and may never demolish by itself.
     */
    public static boolean finish(ServerLevel level, int colonyId, int orderId) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return false;
        }
        IServerWorkOrder order = colony.getWorkManager().getWorkOrders().get(orderId);
        if (order == null || order.getWorkOrderType() == WorkOrderType.REMOVE) {
            return false;
        }
        try {
            UUID owner = colony.getPermissions().getOwner();
            String ownerName = colony.getPermissions().getOwnerName();
            net.minecraft.server.level.ServerPlayer hands = FakePlayerFactory.get(level,
                    new GameProfile(owner, ownerName == null || ownerName.isEmpty()
                            ? "steward" : ownerName));
            java.util.concurrent.Future<Blueprint> future = StructurePacks.getBlueprintFuture(
                    order.getStructurePack(), order.getStructurePath(), level.registryAccess());
            Blueprint placed = CreativeBuildingStructureHandler.loadAndPlaceStructureWithRotation(
                    level, future, order.getLocation(), order.getRotationMirror(), false, hands);
            if (placed == null) {
                return false;
            }
        } catch (Throwable refused) {
            ElysiumColonies.LOGGER.warn("order {} of colony {} could not be completed: {}",
                    orderId, colonyId, refused.toString());
            return false;
        }
        IBuilding building = colony.getServerBuildingManager().getBuilding(order.getLocation());
        if (building != null) {
            building.upgradeBuildingLevelToSchematicData();
        }
        colony.getWorkManager().removeWorkOrder(orderId);
        return true;
    }

    /**
     * Hands a settlement to a player: the steward steps aside.
     *
     * The one place ownership changes hands, and only ever from a steward to
     * a person. A player's town is never taken from them by this module.
     */
    public static boolean handOver(ServerLevel level, int colonyId, Player player) {
        IColony colony = colonyIn(level, colonyId);
        if (colony == null) {
            return false;
        }
        UUID former = colony.getPermissions().getOwner();
        if (!colony.getPermissions().setOwner(player)) {
            return false;
        }
        if (former != null && !former.equals(player.getUUID())) {
            colony.getPermissions().removePlayer(former);
        }
        return true;
    }

    /** The settlement with this id in this world, or null. */
    public static Settlement byId(Level level, int colonyId) {
        return read(colonyIn(level, colonyId));
    }
}
