package com.elysium.agronomy.event;

import com.elysium.agronomy.registry.AgronomyComponents;
import com.elysium.agronomy.registry.FishItems;
import com.elysium.agronomy.rules.FishRules;
import com.elysium.agronomy.rules.TackleRules;
import com.elysium.agronomy.rules.WaterRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The seam between a real pond and the rules' idea of one.
 *
 * <p>Everything the game knows about where a hook is &mdash; the fluid, the
 * biome, how deep the column under it goes, the hour &mdash; is turned into a
 * {@link FishRules.Water} here and handed to {@link TackleRules#table}, and
 * what comes back is what comes up. Nothing in this file decides which fish;
 * it decides which <em>water</em>, by the numbers in {@link WaterRules}, and
 * the table the harness proved does the rest.
 */
public final class Casts {

    private Casts() {
    }

    /** Which fluid a family lives in. */
    public static TagKey<Fluid> fluidOf(FishRules.Family family) {
        return FishRules.LAVA.equals(family.water()) ? FluidTags.LAVA : FluidTags.WATER;
    }

    /** Whether this biome's water is the sea, by the tags the rules list. */
    public static boolean salt(Level level, BlockPos at) {
        for (String tag : WaterRules.SALT_BIOMES) {
            TagKey<Biome> key = TagKey.create(Registries.BIOME,
                    ResourceLocation.parse(tag.substring(1)));
            if (level.getBiome(at).is(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The block a float is judged from: where it is if that is liquid, and
     * the block under it if the float is riding the top of the surface.
     */
    public static BlockPos liquidUnder(Level level, BlockPos at) {
        if (level.getFluidState(at).isEmpty() && !level.getFluidState(at.below()).isEmpty()) {
            return at.below();
        }
        return at;
    }

    /** How many blocks of this fluid there are from here down, this one included. */
    public static int depthOf(Level level, BlockPos at, TagKey<Fluid> fluid) {
        int depth = 0;
        BlockPos.MutableBlockPos below = at.mutable();
        while (depth <= WaterRules.deepestFloor()
                && level.getFluidState(below).is(fluid)) {
            depth++;
            below.move(Direction.DOWN);
        }
        return depth;
    }

    /**
     * The water at a position, as the rules spell it, or null if there is no
     * water there at all.
     *
     * <p>Lava is lava. Water is the sea if the biome says so and fresh
     * otherwise. Anywhere in the End with no liquid is the void, which is
     * bottomless and so in the deepest band there is.
     */
    @Nullable
    public static FishRules.Water whereIs(Level level, BlockPos at) {
        FluidState fluid = level.getFluidState(at);
        String kind;
        int depth;
        if (fluid.is(FluidTags.LAVA)) {
            kind = FishRules.LAVA;
            depth = depthOf(level, at, FluidTags.LAVA);
        } else if (fluid.is(FluidTags.WATER)) {
            kind = salt(level, at) ? FishRules.SALT : FishRules.FRESH;
            depth = depthOf(level, at, FluidTags.WATER);
        } else if (level.dimension() == Level.END) {
            kind = FishRules.VOID;
            depth = Integer.MAX_VALUE;
        } else {
            return null;
        }
        String when = level.isDay() ? FishRules.DAY : FishRules.NIGHT;
        return new FishRules.Water(kind, WaterRules.bandOf(depth), when);
    }

    /**
     * Whether a fish may appear here on its own.
     *
     * <p>The same {@link FishRules#lives} a cast is judged by, so a fish is
     * found swimming in exactly the water it comes up from. The void is
     * never a place to spawn: nothing swims in it, and {@link
     * WaterRules#spawnsWild} is where that is written.
     */
    public static boolean suits(FishRules.Fish fish, ServerLevelAccessor level, BlockPos pos) {
        if (!WaterRules.spawnsWild(fish.family())) {
            return false;
        }
        if (!level.getFluidState(pos).is(fluidOf(fish.family()))) {
            return false;
        }
        FishRules.Water where = whereIs(level.getLevel(), pos);
        return where != null && !FishRules.VOID.equals(where.kind())
                && FishRules.lives(fish, where);
    }

    /**
     * What a cast here brings up: as many fish as the hook catches, each
     * drawn from the table for this water, this rod, this bait and this
     * tackle. Empty if there is nothing in the water.
     */
    public static List<ItemStack> pick(Level level, BlockPos at, int rod,
                                       TackleRules.Tackle tackle, String bait,
                                       RandomSource random) {
        FishRules.Water where = whereIs(level, at);
        if (where == null) {
            return List.of();
        }
        List<FishRules.Chance> table = TackleRules.table(where, rod, bait, tackle);
        if (table.isEmpty()) {
            return List.of();
        }
        List<ItemStack> drops = new ArrayList<>();
        for (int caught = 0; caught < tackle.hook().catches(); caught++) {
            String fish = draw(table, random);
            if (fish != null && FishItems.RAW.containsKey(fish)) {
                drops.add(new ItemStack(FishItems.RAW.get(fish).get()));
            }
        }
        return drops;
    }

    /** One fish out of a normalised table. */
    @Nullable
    public static String draw(List<FishRules.Chance> table, RandomSource random) {
        double roll = random.nextDouble();
        double seen = 0.0D;
        String last = null;
        for (FishRules.Chance chance : table) {
            seen += chance.share();
            last = chance.fish();
            if (roll < seen) {
                return chance.fish();
            }
        }
        return last;
    }

    /** What the game does with a catch: throws it from the hook to the player. */
    public static void deliver(ServerLevel level, Entity hook, Player player,
                               List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            ItemEntity item = new ItemEntity(level, hook.getX(), hook.getY(), hook.getZ(),
                    drop);
            double dx = player.getX() - hook.getX();
            double dy = player.getY() - hook.getY();
            double dz = player.getZ() - hook.getZ();
            double away = Math.sqrt(dx * dx + dy * dy + dz * dz);
            item.setDeltaMovement(dx * 0.1D, dy * 0.1D + Math.sqrt(away) * 0.08D, dz * 0.1D);
            level.addFreshEntity(item);
        }
        if (!drops.isEmpty()) {
            level.addFreshEntity(new ExperienceOrb(level, player.getX(), player.getY() + 0.5D,
                    player.getZ() + 0.5D, level.getRandom().nextInt(6) + 1));
        }
    }

    /** One catch's worth of bait off a rod, if there was any on it. */
    public static void spendBait(ItemStack rod) {
        int had = AgronomyComponents.baitCount(rod);
        if (had <= 0) {
            return;
        }
        int left = had - WaterRules.BAIT_PER_CATCH;
        if (left <= 0) {
            rod.remove(AgronomyComponents.BAIT.get());
            rod.remove(AgronomyComponents.BAIT_COUNT.get());
        } else {
            rod.set(AgronomyComponents.BAIT_COUNT.get(), left);
        }
    }
}
