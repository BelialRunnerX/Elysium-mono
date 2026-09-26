package com.elysium.agronomy.entity;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.event.Casts;
import com.elysium.agronomy.rules.FishRules;
import com.elysium.agronomy.rules.TackleRules;
import com.elysium.agronomy.rules.WaterRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Our end of the line: the game's float, carrying the rod it came off.
 *
 * <h2>In water</h2>
 *
 * The game's own float does the fishing &mdash; the wait, the ripple, the
 * bite &mdash; and this only bends two of its numbers the way the tackle
 * says: a bobber takes {@link TackleRules.Bobber#sooner} off the wait
 * ({@link WaterRules#waitFor}), and a hook that holds keeps a bite on the
 * line until the player reels in ({@link WaterRules#nibbleFor}). The catch
 * itself is drawn by {@link com.elysium.agronomy.event.FishingEvents}, which
 * reads the rod rung, the fitted tackle and the bait off this float.
 *
 * <h2>In lava, and in the void</h2>
 *
 * The game's float sinks in lava and falls out of the End. Two of the
 * roster's families live there, so here the float does its own fishing: it
 * floats on the lava's surface, or {@link WaterRules#VOID_DEPTH} blocks under
 * the End's floor, waits and bites by the same two rules, and draws its catch
 * from the same table every other cast does.
 */
public class ElysiumHook extends net.minecraft.world.entity.projectile.FishingHook {

    private final int rung;
    private final TackleRules.Tackle tackle;
    private final String bait;

    /** The rod being reeled in, while it is; empty otherwise. */
    private ItemStack reeling = ItemStack.EMPTY;

    /** Ticks until something comes near the float in lava or the void; -1 for not yet rolled. */
    private int waiting = -1;

    /** Ticks a bite in lava or the void has left. */
    private int biting;

    public ElysiumHook(Player player, Level level, int lure, int rung,
                       TackleRules.Tackle tackle, String bait) {
        super(player, level, 0, lure);
        this.rung = rung;
        this.tackle = tackle;
        this.bait = bait;
    }

    /** The rung of rod this was cast from. */
    public int rung() {
        return rung;
    }

    /** What was fitted to the rod when it was cast. */
    public TackleRules.Tackle tackle() {
        return tackle;
    }

    /** The family of bait on the rod when it was cast, or {@link FishRules#NOTHING}. */
    public String bait() {
        return bait;
    }

    /** The rod being reeled in, so a catch can use its bait up. */
    public ItemStack reeling() {
        return reeling;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    // ------------------------------------------------------------------
    // Where the float is
    // ------------------------------------------------------------------

    /** The surface of the End's void. */
    private double voidSurface() {
        return level().getMinBuildHeight() - WaterRules.VOID_DEPTH;
    }

    /** Whether this float is sitting in the End's void. */
    private boolean inVoid() {
        return level().dimension() == Level.END && getY() <= voidSurface() + 1.0D;
    }

    /** The block of lava the float is on, or null. */
    private BlockPos lavaAt() {
        BlockPos at = blockPosition();
        if (level().getFluidState(at).is(FluidTags.LAVA)) {
            return at;
        }
        if (level().getFluidState(at.below()).is(FluidTags.LAVA)) {
            return at.below();
        }
        return null;
    }

    /** Whether this float is fishing on something the game's float cannot. */
    private boolean elsewhere() {
        return lavaAt() != null || inVoid();
    }

    /** The block a catch is judged from: the liquid under the float. */
    public BlockPos judgedAt() {
        BlockPos lava = lavaAt();
        if (lava != null) {
            return lava;
        }
        return Casts.liquidUnder(level(), blockPosition());
    }

    // ------------------------------------------------------------------
    // A tick
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        if (level().isClientSide || !elsewhere()) {
            int luredBefore = read(LURED);
            int nibbleBefore = read(NIBBLE);
            super.tick();
            if (!level().isClientSide && !isRemoved()) {
                bend(luredBefore, nibbleBefore);
            }
            return;
        }
        Player player = getPlayerOwner();
        if (player == null || !stillFishing(player)) {
            discard();
            return;
        }
        baseTick();
        floatOn();
        fish();
    }

    /** The two numbers the tackle changes on the game's own float. */
    private void bend(int luredBefore, int nibbleBefore) {
        int lured = read(LURED);
        if (lured > luredBefore && lured > 0) {
            write(LURED, WaterRules.waitFor(lured, tackle.bobber()));
        }
        int nibble = read(NIBBLE);
        if (nibble > 0 && nibbleBefore <= 0) {
            write(NIBBLE, WaterRules.nibbleFor(nibble, tackle.hook()));
        }
    }

    private boolean stillFishing(Player player) {
        boolean holding = player.getMainHandItem().canPerformAction(
                ItemAbilities.FISHING_ROD_CAST)
                || player.getOffhandItem().canPerformAction(ItemAbilities.FISHING_ROD_CAST);
        return !player.isRemoved() && player.isAlive() && holding
                && distanceToSqr(player) <= 1024.0D;
    }

    /** Bob on the surface, the way the game's float bobs on water. */
    private void floatOn() {
        Vec3 moving = getDeltaMovement();
        double surface;
        BlockPos lava = lavaAt();
        if (lava != null) {
            FluidState fluid = level().getFluidState(lava);
            surface = lava.getY() + fluid.getHeight(level(), lava);
        } else {
            surface = voidSurface();
            if (getY() < surface - 2.0D) {
                setPos(getX(), surface, getZ());
                moving = new Vec3(moving.x, 0.0D, moving.z);
            }
        }
        double off = getY() + moving.y - surface;
        if (Math.abs(off) < 0.01D) {
            off += Math.signum(off) * 0.1D;
        }
        setDeltaMovement(moving.x * 0.9D, moving.y - off * random.nextFloat() * 0.2D,
                moving.z * 0.9D);
        move(MoverType.SELF, getDeltaMovement());
    }

    /** Wait, then bite, by the rules' numbers. */
    private void fish() {
        if (biting > 0) {
            biting--;
            if (biting == 0) {
                waiting = -1;
            }
            return;
        }
        if (waiting < 0) {
            waiting = WaterRules.waitFor(Mth.nextInt(random, WaterRules.WAIT_LEAST_TICKS,
                    WaterRules.WAIT_MOST_TICKS), tackle.bobber());
        }
        if (--waiting > 0) {
            return;
        }
        biting = WaterRules.nibbleFor(Mth.nextInt(random, WaterRules.NIBBLE_LEAST_TICKS,
                WaterRules.NIBBLE_MOST_TICKS), tackle.hook());
        Vec3 moving = getDeltaMovement();
        setDeltaMovement(moving.x, -0.4F * Mth.nextFloat(random, 0.6F, 1.0F), moving.z);
        level().playSound(null, getX(), getY(), getZ(), lavaAt() != null
                        ? SoundEvents.LAVA_POP : SoundEvents.FISHING_BOBBER_SPLASH,
                SoundSource.NEUTRAL, 0.25F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(lavaAt() != null ? ParticleTypes.LAVA : ParticleTypes.PORTAL,
                    getX(), getY() + 0.5D, getZ(), 6, 0.2D, 0.1D, 0.2D, 0.0D);
        }
    }

    // ------------------------------------------------------------------
    // Reeling in
    // ------------------------------------------------------------------

    @Override
    public int retrieve(ItemStack rod) {
        this.reeling = rod;
        try {
            if (level().isClientSide || !elsewhere()) {
                return super.retrieve(rod);
            }
            Player player = getPlayerOwner();
            int damage = 0;
            if (player != null && biting > 0 && level() instanceof ServerLevel server) {
                List<ItemStack> drops = Casts.pick(server, judgedAt(), rung, tackle, bait, random);
                Casts.deliver(server, this, player, drops);
                if (!drops.isEmpty()) {
                    Casts.spendBait(rod);
                    damage = 1;
                }
            }
            discard();
            return damage;
        } finally {
            this.reeling = ItemStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------
    // The game's float's own two numbers
    // ------------------------------------------------------------------

    private static final Field LURED = field("timeUntilLured");
    private static final Field NIBBLE = field("nibble");

    private static Field field(String name) {
        try {
            Field found = net.minecraft.world.entity.projectile.FishingHook.class
                    .getDeclaredField(name);
            found.setAccessible(true);
            return found;
        } catch (ReflectiveOperationException | RuntimeException missing) {
            ElysiumAgronomy.LOGGER.warn("the game's fishing hook has no field {}; "
                    + "tackle will not change the wait or the bite in water", name);
            return null;
        }
    }

    private int read(Field field) {
        if (field == null) {
            return 0;
        }
        try {
            return field.getInt(this);
        } catch (IllegalAccessException denied) {
            return 0;
        }
    }

    private void write(Field field, int value) {
        if (field == null) {
            return;
        }
        try {
            field.setInt(this, value);
        } catch (IllegalAccessException denied) {
            // Left as the game rolled it.
        }
    }
}
