package com.elysium.dungeons.room;

import com.elysium.dungeons.ElysiumDungeons;
import com.elysium.lib.entity.ElysiumBestiary;
import com.elysium.lib.standing.ElysiumRewards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The rooms this mod ships.
 *
 * <h2>What makes a filler room worth building</h2>
 *
 * Not detail — a player walks through most of these in four seconds. What
 * matters is that a room is <b>recognisable at a glance</b>, so a dungeon reads
 * as a sequence of places rather than a corridor of identical boxes. Each
 * filler below changes one thing you can see from the doorway: the floor, the
 * ceiling height, the light, or what is standing in the middle.
 *
 * <h2>The one rule every room follows</h2>
 *
 * Never seal a doorway. Every free-standing placement goes through
 * {@link RoomContext#setClear} or checks {@link RoomContext#blocksDoorway},
 * because in an enclosed dungeon a pillar in front of the only exit is a run
 * that cannot be finished. The context enforces the outer walls; the doorways
 * are the part a room can still get wrong, so they are checked at every
 * placement rather than trusted.
 */
public final class DungeonRooms {

    private DungeonRooms() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumDungeons.MODID, path);
    }

    // ==================================================================
    // Entrance
    // ==================================================================

    /**
     * Where you arrive, and the way back.
     *
     * The return rift is built into the far wall rather than under the arrival
     * point, so a player does not spawn standing inside it — which, with the
     * travel cooldown, would mean walking out of the dungeon the instant they
     * arrived.
     */
    public static final DungeonRoom ENTRANCE = DungeonRoom.builder(id("entrance"))
            .kind(DungeonLayout.Kind.ENTRANCE)
            .build((context, random) -> {
                BlockState frame = ElysiumDungeons.RIFT_FRAME.get().defaultBlockState();
                BlockState portal = ElysiumDungeons.RIFT_PORTAL.get().defaultBlockState();

                int midX = context.width() / 2;
                // Against the north wall, which is the one wall a doorway
                // cannot also be in the middle of - a door sits at midX, and
                // this frame is built around midX, so it is placed on a wall
                // with no door where possible.
                int z = pickBlankWallZ(context);

                for (int x = midX - 2; x <= midX + 2; x++) {
                    context.set(x, 0, z, frame);
                    context.set(x, 4, z, frame);
                }
                for (int y = 1; y <= 3; y++) {
                    context.set(midX - 2, y, z, frame);
                    context.set(midX + 2, y, z, frame);
                    for (int x = midX - 1; x <= midX + 1; x++) {
                        context.set(x, y, z, portal);
                    }
                }

                // A little light so the way out is visible from the doorway.
                context.setClear(midX - 3, 1, z, Blocks.SEA_LANTERN.defaultBlockState());
                context.setClear(midX + 3, 1, z, Blocks.SEA_LANTERN.defaultBlockState());
            });

    /**
     * A wall with no doorway in it, for the return rift.
     *
     * Falls back to the north wall when every wall has a door — a five-room
     * junction is possible and rare, and a rift overlapping a doorway is ugly
     * rather than broken, because the door was carved before the room was
     * decorated and the frame simply covers part of it.
     */
    private static int pickBlankWallZ(RoomContext context) {
        if (!context.doors().contains(Direction.NORTH)) {
            return 1;
        }
        if (!context.doors().contains(Direction.SOUTH)) {
            return context.depth() - 2;
        }
        return 1;
    }

    // ==================================================================
    // Fillers
    // ==================================================================

    /** A hall of pillars. The plainest room, and the most common. */
    public static final DungeonRoom PILLARS = DungeonRoom.builder(id("pillar_hall"))
            .weight(3)
            .build((context, random) -> {
                BlockState pillar = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                for (int x = 3; x < context.width() - 2; x += 4) {
                    for (int z = 3; z < context.depth() - 2; z += 4) {
                        if (context.blocksDoorway(x, z)) {
                            continue;
                        }
                        context.column(x, z, 0, context.height() - 1, pillar);
                    }
                }
                scatterLight(context, random, 2);
                flavour(context, random);
                populate(context, random);
            });

    /**
     * A drained sump: a sunken basin, a broken floor, and no water in it.
     *
     * This room used to be a foot of standing water, and the water is gone on
     * purpose. In a sealed dungeon a fluid is the one block that does not stay
     * where it is put — it flows through a doorway the moment anything opens
     * one, it pushes items and mobs around, it drowns a loot drop into a
     * corner, and none of that is visible from the code that placed it. A
     * basin that is simply <em>dry</em> reads as the same ruin and cannot leak.
     *
     * Whether it is dry is not left to this method to remember: RoomContext
     * refuses to place a fluid at all.
     */
    public static final DungeonRoom CISTERN = DungeonRoom.builder(id("cistern"))
            .weight(2)
            .build((context, random) -> {
                // The basin floor, one step down, inset from the walls so the
                // doorways stay level.
                BlockState silt = Blocks.MUD.defaultBlockState();
                for (int x = 3; x < context.width() - 3; x++) {
                    for (int z = 3; z < context.depth() - 3; z++) {
                        context.set(x, 0, z, silt);
                    }
                }
                for (int i = 0; i < 6; i++) {
                    BlockPos at = context.randomFloorPos(random);
                    context.setClear(at.getX(), 0, at.getZ(),
                            Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                }
                scatterLight(context, random, 2);
                flavour(context, random);
                populate(context, random);
            });

    /** Rubble and a collapsed ceiling. */
    public static final DungeonRoom RUBBLE = DungeonRoom.builder(id("collapse"))
            .weight(2)
            .build((context, random) -> {
                BlockState[] debris = {
                        Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
                        Blocks.DEEPSLATE_TILES.defaultBlockState(),
                        Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
                };
                int piles = 8 + random.nextInt(8);
                for (int i = 0; i < piles; i++) {
                    BlockPos at = context.randomFloorPos(random);
                    int height = random.nextInt(3);
                    for (int y = 0; y <= height; y++) {
                        context.setClear(at.getX(), y, at.getZ(),
                                debris[random.nextInt(debris.length)]);
                    }
                }
                scatterLight(context, random, 1);
                flavour(context, random);
                populate(context, random);
            });

    /** A crypt: alcoves cut into two walls. */
    public static final DungeonRoom CRYPT = DungeonRoom.builder(id("crypt"))
            .weight(2)
            .build((context, random) -> {
                BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
                for (int z = 2; z < context.depth() - 2; z += 3) {
                    if (!context.blocksDoorway(1, z)) {
                        context.set(1, 1, z, bone);
                    }
                    if (!context.blocksDoorway(context.width() - 2, z)) {
                        context.set(context.width() - 2, 1, z, bone);
                    }
                }
                if (random.nextBoolean()) {
                    BlockPos centre = context.centre();
                    context.setClear(centre.getX(), 0, centre.getZ(),
                            Blocks.CHISELED_DEEPSLATE.defaultBlockState());
                }
                scatterLight(context, random, 2);
                flavour(context, random);
                populate(context, random);
            });

    /** A furnace hall: warm, lit from the floor. */
    public static final DungeonRoom FORGE = DungeonRoom.builder(id("cold_forge"))
            .weight(1)
            .build((context, random) -> {
                BlockPos centre = context.centre();
                // A square of magma with a rim, so the light source is obvious
                // and the damage is avoidable.
                for (int x = centre.getX() - 1; x <= centre.getX() + 1; x++) {
                    for (int z = centre.getZ() - 1; z <= centre.getZ() + 1; z++) {
                        context.setClear(x, 0, z, Blocks.MAGMA_BLOCK.defaultBlockState());
                    }
                }
                for (int i = 0; i < 4; i++) {
                    BlockPos at = context.randomFloorPos(random);
                    context.setClear(at.getX(), 0, at.getZ(),
                            Blocks.BLACKSTONE.defaultBlockState());
                }
                flavour(context, random);
                populate(context, random);
            });

    /** An empty room. Deliberately. */
    public static final DungeonRoom BARE = DungeonRoom.builder(id("bare"))
            .weight(1)
            .build((context, random) -> {
                // Nothing but light. A dungeon where every room has something
                // in it has no rhythm; the plain rooms are what make the others
                // read as rooms with something in them.
                scatterLight(context, random, 2);
                flavour(context, random);
                populate(context, random);
            });

    // ==================================================================
    // Loot
    // ==================================================================

    /**
     * Chests, filled from the library's reward providers.
     *
     * The contents are not this mod's business. {@link ElysiumRewards} is asked
     * for a tier and answers with whatever any installed mod has registered —
     * so a pack with elysium-core finds Elysium gear here, and a pack without
     * it finds whatever else is offered. If nothing at all is registered the
     * chest is empty, which is a poorer dungeon and not a broken one.
     */
    public static final DungeonRoom VAULT = DungeonRoom.builder(id("vault"))
            .kind(DungeonLayout.Kind.LOOT)
            .build((context, random) -> {
                BlockState brick = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                BlockPos centre = context.centre();

                // A plinth, so the chest is the thing you look at.
                for (int x = centre.getX() - 2; x <= centre.getX() + 2; x++) {
                    for (int z = centre.getZ() - 2; z <= centre.getZ() + 2; z++) {
                        context.setClear(x, 0, z, brick);
                    }
                }

                int chests = 1 + random.nextInt(2);
                for (int i = 0; i < chests; i++) {
                    int x = centre.getX() + (i == 0 ? 0 : (random.nextBoolean() ? 2 : -2));
                    int z = centre.getZ() + (i == 0 ? 0 : (random.nextBoolean() ? 2 : -2));
                    if (context.blocksDoorway(x, z)) {
                        continue;
                    }
                    context.set(x, 1, z, Blocks.CHEST.defaultBlockState());
                    fillChest(context, context.world(x, 1, z), random);
                }

                context.setClear(centre.getX() - 3, 1, centre.getZ(),
                        Blocks.SEA_LANTERN.defaultBlockState());
                context.setClear(centre.getX() + 3, 1, centre.getZ(),
                        Blocks.SEA_LANTERN.defaultBlockState());
            });

    /**
     * Rolls a chest's contents.
     *
     * The tier climbs with how far into the dungeon the room is, so the loot
     * room past the boss's door pays better than the one off the entrance —
     * which is the whole reason the layout puts loot rooms at dead ends and
     * sorts them by depth.
     */
    private static void fillChest(RoomContext context, BlockPos worldPos, RandomSource random) {
        if (!(context.level().getBlockEntity(worldPos) instanceof Container container)) {
            // The chest did not become a block entity. Nothing to fill, and
            // nothing to crash over - an empty chest is survivable.
            return;
        }
        int tier = Math.min(ElysiumRewards.MAX_TIER, 1 + context.cell().depth / 3);
        int rolls = 3 + random.nextInt(4);
        for (int i = 0; i < rolls; i++) {
            ItemStack stack = ElysiumRewards.roll(tier, random);
            if (stack.isEmpty()) {
                continue;
            }
            container.setItem(random.nextInt(container.getContainerSize()), stack);
        }
    }

    // ==================================================================
    // Boss
    // ==================================================================

    /**
     * The far end of the dungeon.
     *
     * The boss is a vanilla mob rather than one of this mod's own, on purpose:
     * a custom entity is a model, a texture, an AI goal set and a spawn egg
     * before it is a fight, and none of that is what this mod is about. What
     * makes it a boss is the arena and the name — and a room registered by
     * another mod can replace this one entirely without touching this file.
     */
    public static final DungeonRoom THRONE = DungeonRoom.builder(id("throne"))
            .kind(DungeonLayout.Kind.BOSS)
            .build((context, random) -> {
                BlockState dark = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                BlockPos centre = context.centre();

                // A raised dais, ringed with light. Nothing free-standing, so
                // there is nowhere to hide and nothing to seal a doorway.
                for (int x = centre.getX() - 3; x <= centre.getX() + 3; x++) {
                    for (int z = centre.getZ() - 3; z <= centre.getZ() + 3; z++) {
                        context.setClear(x, 0, z, dark);
                    }
                }
                for (int x = centre.getX() - 4; x <= centre.getX() + 4; x += 8) {
                    for (int z = centre.getZ() - 4; z <= centre.getZ() + 4; z += 8) {
                        context.setClear(x, 0, z, Blocks.SEA_LANTERN.defaultBlockState());
                    }
                }

                spawnBoss(context, centre);
            });

    /**
     * Asks the library's bestiary for a boss, and falls back if nobody offers.
     *
     * This is the whole of the integration with Elysium Mobs, and it names it
     * nowhere. Any mod that registers a BOSS entry fills this room; with none
     * installed the fallback below keeps the room from being empty, because a
     * dungeon whose boss room contains nothing is a dungeon with no ending.
     */
    private static void spawnBoss(RoomContext context, BlockPos centre) {
        BlockPos where = context.world(centre.getX(), 1, centre.getZ());
        // The dungeon's own level, not the proximity rule: nobody is standing
        // in this room when it is built, and asking made every boss a level-1
        // boss for the life of the save.
        Mob boss = ElysiumBestiary.spawn(context.level(), where, null,
                ElysiumBestiary.Role.BOSS, context.level().getRandom(),
                context.mobLevel());
        if (boss == null) {
            boss = fallbackBoss(context);
        }
        if (boss == null) {
            return;
        }
        boss.moveTo(where.getX() + 0.5D, where.getY(), where.getZ() + 0.5D, 0.0F, 0.0F);
        boss.setPersistenceRequired();
        context.level().addFreshEntity(boss);
    }

    /**
     * The boss when no mob mod is installed.
     *
     * A vanilla mob with a name, which is not much of a boss — and deliberately
     * so. It exists to keep the dungeon finishable on its own rather than to
     * compete with a real one; a pack that wants a fight installs a mod that
     * registers one.
     */
    private static Mob fallbackBoss(RoomContext context) {
        Entity entity = EntityType.WITHER_SKELETON.create(context.level());
        if (!(entity instanceof Mob boss)) {
            return null;
        }
        boss.setCustomName(net.minecraft.network.chat.Component.translatable(
                "elysiumdungeons.entity.rift_warden"));
        boss.setCustomNameVisible(true);
        return boss;
    }

    // ==================================================================

    /**
     * Puts a few creatures in a room, if anything has offered any.
     *
     * Called from every filler room, so a dungeon is populated rather than
     * decorated. The count rises with depth: rooms near the entrance hold one
     * or two, rooms near the boss hold a handful, which is the difficulty curve
     * expressed as arithmetic rather than as a table.
     *
     * Grunts, not elites, for most of it — an elite is worth meeting because it
     * is rare, and a room of them is just a harder room.
     */
    private static void populate(RoomContext context, RandomSource random) {
        if (ElysiumBestiary.isEmpty()) {
            return;
        }
        int depth = context.cell().depth;
        int base = 1 + depth / 2 + (random.nextBoolean() ? 1 : 0);
        int count = Math.min(9, Math.max(1, Math.round(base * areaScale(context))));
        for (int i = 0; i < count; i++) {
            BlockPos spot = context.randomFloorPos(random);
            BlockPos where = context.world(spot.getX(), 1, spot.getZ());

            // One in four is an elite, and only past the first couple of rooms.
            ElysiumBestiary.Role role = depth >= 2 && random.nextFloat() < 0.25F
                    ? ElysiumBestiary.Role.ELITE
                    : ElysiumBestiary.Role.GRUNT;

            Mob mob = ElysiumBestiary.spawn(context.level(), where, null, role, random,
                    context.mobLevel());
            if (mob == null) {
                continue;
            }
            mob.moveTo(where.getX() + 0.5D, where.getY(), where.getZ() + 0.5D,
                    random.nextFloat() * 360.0F, 0.0F);
            mob.setPersistenceRequired();
            context.level().addFreshEntity(mob);
        }
    }

    /**
     * Ceiling lights, never in a doorway.
     *
     * The count argument is a <em>density</em>, not a number of lamps: it is
     * how many the room would get at the old thirteen-by-thirteen size, scaled
     * to whatever size the room actually is. Rooms went from 169 floor tiles to
     * 441 and every literal count in this file would have lit two and a half
     * times less of it — a bigger room that is simply darker, which reads as a
     * bug rather than as atmosphere.
     */
    private static void scatterLight(RoomContext context, RandomSource random, int density) {
        int count = Math.max(1, Math.round(density * areaScale(context)));
        for (int i = 0; i < count; i++) {
            BlockPos at = context.randomFloorPos(random);
            context.setClear(at.getX(), context.height() - 1, at.getZ(),
                    Blocks.SEA_LANTERN.defaultBlockState());
        }
    }

    /**
     * This room's floor area, relative to the size the numbers here were tuned
     * at.
     *
     * One place, so a future size change moves every density at once instead of
     * moving some of them and leaving the rest to be found by a player walking
     * through a dark, empty hall.
     */
    private static final int TUNED_AT = 13;

    private static float areaScale(RoomContext context) {
        return (context.width() * context.depth()) / (float) (TUNED_AT * TUNED_AT);
    }

    // ==================================================================
    // Flavour
    // ==================================================================

    /**
     * The detail every room gets, whatever else is in it.
     *
     * <h3>Why this is shared rather than per-room</h3>
     *
     * A room's decorator says what the room <em>is</em> — a cistern, a crypt, a
     * collapse. This says what the dungeon is, and the dungeon is one place: the
     * same masonry, the same wear, the same builders. Putting it in each
     * decorator would mean seven copies drifting apart until the crypt and the
     * forge looked like they came from different mods.
     *
     * <h3>Why it is cheap</h3>
     *
     * Everything here is a wall or ceiling course - a fixed fraction of the
     * perimeter, not of the volume - so a room three times the floor area costs
     * about 1.7 times this rather than three times. The expensive part of a
     * dungeon is the shell, and this deliberately does not add to it.
     */
    private static void flavour(RoomContext context, RandomSource random) {
        BlockState trim = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        BlockState chiselled = Blocks.CHISELED_DEEPSLATE.defaultBlockState();

        int w = context.width();
        int d = context.depth();
        int h = context.height();

        // A course of tile at head height, all the way round. One line, and it
        // is most of what stops a wall reading as an untextured plane.
        for (int x = 0; x < w; x++) {
            context.set(x, 2, 0, trim);
            context.set(x, 2, d - 1, trim);
        }
        for (int z = 0; z < d; z++) {
            context.set(0, 2, z, trim);
            context.set(w - 1, 2, z, trim);
        }

        // Wear: a scatter of cracked brick on the walls, proportional to the
        // wall area rather than the floor, because that is what it is on.
        int weathered = Math.round(6 * (w + d) / 26.0F) + random.nextInt(4);
        for (int i = 0; i < weathered; i++) {
            int y = 1 + random.nextInt(Math.max(1, h - 2));
            if (random.nextBoolean()) {
                int x = random.nextInt(w);
                context.set(x, y, random.nextBoolean() ? 0 : d - 1, cracked);
            } else {
                int z = random.nextInt(d);
                context.set(random.nextBoolean() ? 0 : w - 1, y, z, cracked);
            }
        }

        // Pilasters: a chiselled stripe up the wall every six blocks, skipping
        // the middle of each wall so a doorway is never framed shut.
        for (int x = 4; x < w - 3; x += 6) {
            if (!context.blocksDoorway(x, 0)) {
                context.fill(x, 1, 0, x, h - 2, 0, chiselled);
            }
            if (!context.blocksDoorway(x, d - 1)) {
                context.fill(x, 1, d - 1, x, h - 2, d - 1, chiselled);
            }
        }
        for (int z = 4; z < d - 3; z += 6) {
            if (!context.blocksDoorway(0, z)) {
                context.fill(0, 1, z, 0, h - 2, z, chiselled);
            }
            if (!context.blocksDoorway(w - 1, z)) {
                context.fill(w - 1, 1, z, w - 1, h - 2, z, chiselled);
            }
        }

        // Chains hanging from the ceiling, in the open middle of the room where
        // the extra three blocks of height are actually visible.
        int chains = 2 + random.nextInt(3);
        for (int i = 0; i < chains; i++) {
            BlockPos at = context.randomFloorPos(random);
            if (context.blocksDoorway(at.getX(), at.getZ())) {
                continue;
            }
            int length = 2 + random.nextInt(Math.max(1, h - 4));
            for (int y = 0; y < length; y++) {
                context.set(at.getX(), h - 1 - y, at.getZ(),
                        Blocks.CHAIN.defaultBlockState());
            }
        }

        fittings(context, random);
    }

    /**
     * The second pass: what the room is made of, rather than what shape it is.
     *
     * Everything above is masonry — trim, wear, pilasters, chains. It reads as
     * a ruin and it reads as the <em>same</em> ruin in every room, because the
     * only thing that varies is where the cracks landed.
     *
     * These are fittings: things somebody installed and then left. Each one is
     * rolled independently and most rooms get two or three, so two rooms are
     * rarely furnished alike even when they are the same shape and the same
     * kind. None of it is placed where it could seal a doorway, and none of it
     * is placed on a tile a shape has already filled in — {@code setClear}
     * handles the first, and {@link RoomContext#set} lands harmlessly on solid
     * rock for the second.
     */
    private static void fittings(RoomContext context, RandomSource random) {
        int w = context.width();
        int d = context.depth();
        int h = context.height();

        // --- Cable runs -------------------------------------------------
        //
        // Chains along the wall at head height, in broken lengths. The single
        // cheapest thing that makes a corridor read as built rather than
        // excavated: a straight line at a constant height is unmistakably
        // somebody's work.
        if (random.nextInt(3) > 0) {
            int y = h - 3;
            for (int x = 1; x < w - 1; x++) {
                if (random.nextInt(9) != 0 && !context.blocksDoorway(x, 1)) {
                    context.set(x, y, 1, Blocks.CHAIN.defaultBlockState());
                }
                if (random.nextInt(9) != 0 && !context.blocksDoorway(x, d - 2)) {
                    context.set(x, y, d - 2, Blocks.CHAIN.defaultBlockState());
                }
            }
        }

        // --- Vents ------------------------------------------------------
        //
        // Iron bars set into the wall at floor level, with a dark recess
        // behind. Reads as ductwork and costs two blocks.
        int vents = random.nextInt(4);
        for (int i = 0; i < vents; i++) {
            boolean onX = random.nextBoolean();
            int a = 2 + random.nextInt(Math.max(1, (onX ? w : d) - 4));
            int wall = random.nextBoolean() ? 0 : (onX ? d : w) - 1;
            int vx = onX ? a : wall;
            int vz = onX ? wall : a;
            if (context.blocksDoorway(vx, vz)) {
                continue;
            }
            context.set(vx, 1, vz, Blocks.IRON_BARS.defaultBlockState());
            context.set(vx, 2, vz, Blocks.IRON_BARS.defaultBlockState());
        }

        // --- Consoles ---------------------------------------------------
        //
        // A slab on a block, against a wall, with a lit face. Furniture the
        // player reads as a workstation somebody abandoned.
        int consoles = random.nextInt(3);
        for (int i = 0; i < consoles; i++) {
            BlockPos at = context.randomFloorPos(random);
            int cx = at.getX();
            int cz = at.getZ();
            context.setClear(cx, 1, cz, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            context.setClear(cx, 2, cz,
                    Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState());
            if (random.nextBoolean()) {
                context.setClear(cx, 3, cz, Blocks.REDSTONE_LAMP.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
            }
        }

        // --- Amethyst growth --------------------------------------------
        //
        // The one thing in the palette that is not grey, and deliberately rare.
        // A room with a seam of crystal in the corner is the room a player
        // describes afterwards; a dungeon where every room has one is a dungeon
        // made of crystal.
        if (random.nextInt(5) == 0) {
            BlockPos at = context.randomFloorPos(random);
            int budded = 1 + random.nextInt(4);
            for (int i = 0; i < budded; i++) {
                int bx = at.getX() + random.nextInt(3) - 1;
                int bz = at.getZ() + random.nextInt(3) - 1;
                context.setClear(bx, 1, bz, Blocks.AMETHYST_BLOCK.defaultBlockState());
                if (random.nextBoolean()) {
                    context.setClear(bx, 2, bz, Blocks.AMETHYST_CLUSTER.defaultBlockState());
                }
            }
        }

        // --- Floor inlay ------------------------------------------------
        //
        // A band of tile across the middle of the floor, one axis only. Catches
        // the eye from a doorway and gives the room a direction.
        if (random.nextInt(3) == 0) {
            BlockState inlay = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            if (random.nextBoolean()) {
                for (int x = 1; x < w - 1; x++) {
                    context.set(x, 0, d / 2, inlay);
                    context.set(x, 0, d / 2 - 1, inlay);
                }
            } else {
                for (int z = 1; z < d - 1; z++) {
                    context.set(w / 2, 0, z, inlay);
                    context.set(w / 2 - 1, 0, z, inlay);
                }
            }
        }

        // --- Rubble piles -----------------------------------------------
        //
        // Half-height, so they read as debris rather than as furniture, and
        // they do not block a sightline.
        int piles = random.nextInt(5);
        for (int i = 0; i < piles; i++) {
            BlockPos at = context.randomFloorPos(random);
            context.setClear(at.getX(), 1, at.getZ(),
                    random.nextBoolean()
                            ? Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState()
                            : Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
        }
    }

    /** Touching this class registers every room. */
    public static void bootstrap() {
    }
}
