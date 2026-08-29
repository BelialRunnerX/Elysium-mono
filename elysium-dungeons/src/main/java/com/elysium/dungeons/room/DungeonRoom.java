package com.elysium.dungeons.room;

import com.elysium.lib.registry.ElysiumRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * One kind of room, and how to build it.
 *
 * <h2>What a room is responsible for</h2>
 *
 * Its <b>contents</b>, and nothing else. The shell — floor, walls, ceiling and
 * the doorways to its neighbours — is built by {@link DungeonBuilder} before
 * any room is asked to decorate, for two reasons:
 *
 * <ul>
 *   <li>Every room in an enclosed dungeon has the same shell, so writing it
 *       once means a new room cannot get it subtly wrong and leave a hole into
 *       the void.</li>
 *   <li>Doorways depend on which neighbours exist, which is the layout's
 *       business and not the room's. A room that punched its own doors would
 *       need to know the layout, and then every room would need updating when
 *       the layout changed.</li>
 * </ul>
 *
 * So a room is handed a hollow box with the doors already in it, and fills it.
 *
 * <h2>Registering one</h2>
 *
 * <pre>{@code
 * public static final DungeonRoom OSSUARY = DungeonRoom.builder(id("ossuary"))
 *         .kind(DungeonLayout.Kind.FILLER)
 *         .weight(3)
 *         .build((ctx, random) -> {
 *             for (int i = 0; i < 20; i++) {
 *                 ctx.set(ctx.randomFloorPos(random), Blocks.BONE_BLOCK.defaultBlockState());
 *             }
 *         });
 * }</pre>
 *
 * Weight is relative within a kind. A filler at weight 3 turns up three times
 * as often as one at weight 1; there is no scale and no total to keep to.
 *
 * <h2>Why this is a registry rather than an enum</h2>
 *
 * The same reason everything else in Elysium is. An enum of rooms is a fixed
 * list only this mod can extend, and the point of building on the library is
 * that another mod can add a room without touching this one.
 */
public final class DungeonRoom {

    /** Every room in the game. Add-ons register here during construction. */
    public static final ElysiumRegistry<DungeonRoom> REGISTRY = new ElysiumRegistry<>("dungeon room");

    /** What fills a room once its shell is standing. */
    @FunctionalInterface
    public interface Decorator {
        /**
         * @param context the room being built: its bounds, its doors, and the
         *                helpers for placing blocks inside it
         * @param random  seeded from the instance seed and this room's grid
         *                position, so a room is the same every time the same
         *                dungeon is generated and different from its neighbours
         */
        void decorate(RoomContext context, RandomSource random);
    }

    private final DungeonLayout.Kind kind;
    private final int weight;
    private final Decorator decorator;

    private DungeonRoom(DungeonLayout.Kind kind, int weight, Decorator decorator) {
        this.kind = kind;
        this.weight = weight;
        this.decorator = decorator;
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private DungeonLayout.Kind kind = DungeonLayout.Kind.FILLER;
        private int weight = 1;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        /** Which slot in the layout this room can fill. */
        public Builder kind(DungeonLayout.Kind kind) {
            this.kind = kind;
            return this;
        }

        /** How often this turns up relative to others of the same kind. */
        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        public DungeonRoom build(Decorator decorator) {
            if (weight <= 0) {
                throw new IllegalArgumentException(
                        "Room '" + id + "' has weight " + weight + ". A room at zero weight can "
                        + "never be chosen, which is a room that does not exist - delete it or "
                        + "give it a weight.");
            }
            return REGISTRY.register(id, new DungeonRoom(kind, weight, decorator));
        }
    }

    // ------------------------------------------------------------------

    public DungeonLayout.Kind getKind() {
        return kind;
    }

    public int getWeight() {
        return weight;
    }

    public void decorate(RoomContext context, RandomSource random) {
        decorator.decorate(context, random);
    }

    public ResourceLocation getId() {
        return REGISTRY.idOf(this);
    }

    /**
     * Picks a room of a kind, by weight.
     *
     * @return null when nothing is registered for that kind, which the builder
     *         treats as "leave the room empty" rather than as an error — an
     *         empty room is a worse dungeon, a crash is a worse game
     */
    public static DungeonRoom pick(DungeonLayout.Kind kind, RandomSource random) {
        int total = 0;
        for (DungeonRoom room : REGISTRY.all()) {
            if (room.kind == kind) {
                total += room.weight;
            }
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (DungeonRoom room : REGISTRY.all()) {
            if (room.kind != kind) {
                continue;
            }
            roll -= room.weight;
            if (roll < 0) {
                return room;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        ResourceLocation id = getId();
        return id == null ? "unregistered room" : id.toString();
    }
}
