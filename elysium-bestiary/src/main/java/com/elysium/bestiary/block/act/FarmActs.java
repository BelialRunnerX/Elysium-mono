package com.elysium.bestiary.block.act;

import com.elysium.bestiary.block.FarmBlockEntity;
import com.elysium.bestiary.rules.FarmRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The one place a machine's one job happens.
 *
 * <h2>One branch per kind, and no machine named</h2>
 *
 * The fifth file in this module built that way. Five branches for nine
 * machines, because two fans and two killers are the same job done in
 * different places, and a branch per <em>machine</em> would be nine places for
 * one bug and a tenth machine would be a tenth file.
 *
 * <p>No machine is named anywhere below, and {@code validate.py} reads this
 * file and fails if it finds one.
 *
 * <h2>Why the killer asks the row for its multipliers</h2>
 *
 * Because that is the entire proof. {@link FarmRules} shows that a killer
 * leaving no experience pushes the bestiary's reward spread from 1.148 to
 * 1.618, past the band the whole thing is balanced on. If this file typed its
 * own share of the orbs, the proof would still pass and the game would still
 * do the other thing — which is the failure that is invisible from inside,
 * because the farm works and the items arrive.
 */
public final class FarmActs {

    private FarmActs() {
    }

    /** How far a machine reaches for what it is working on, in blocks. */
    public static final double REACHES = 2.0D;

    /** How hard a fan pushes. */
    public static final double PUSH = 0.16D;

    /** What a killer takes off a mob each time it acts. */
    public static final float BITE = 4.0F;

    /** How much experience one solid lump of it is worth. */
    public static final int LUMP = 20;

    /** How far a gatherer reaches, which is further, because that is its job. */
    public static final double GATHERS_FROM = 4.0D;

    /**
     * The one job, for whatever is in reach.
     *
     * <p>Handed the row rather than reading it, so a control can hand one built
     * to be caught.
     */
    public static void happen(FarmBlockEntity self, FarmRules.Machine row) {
        Level level = self.getLevel();
        if (level == null) {
            return;
        }
        switch (row.kind()) {
            case FarmRules.MOVES -> {
                Direction way = facing(self);
                for (Mob mob : mobs(self, REACHES)) {
                    mob.setDeltaMovement(mob.getDeltaMovement().add(
                            way.getStepX() * PUSH, 0.0D, way.getStepZ() * PUSH));
                    mob.hurtMarked = true;
                }
            }
            case FarmRules.KILLS -> {
                // The row's two multipliers, asked for rather than typed. This
                // is the line the whole file is about.
                for (Mob mob : mobs(self, REACHES)) {
                    strike(self, mob, row.orbs(), row.drops());
                }
            }
            case FarmRules.GATHERS -> {
                for (ItemEntity dropped : level.getEntitiesOfClass(ItemEntity.class,
                        around(self, GATHERS_FROM))) {
                    dropped.setPos(self.getBlockPos().getX() + 0.5D,
                            self.getBlockPos().getY() + 0.5D,
                            self.getBlockPos().getZ() + 0.5D);
                }
                for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class,
                        around(self, GATHERS_FROM))) {
                    self.hold(orb.getValue());
                    orb.discard();
                }
            }
            case FarmRules.KEEPS -> {
                // What it has gathered, turned into something that can be
                // carried or handed back. It never creates any: a keeper that
                // made experience would be a machine outside the arithmetic.
                int taken = self.takeOrbs(LUMP);
                if (taken > 0) {
                    ExperienceOrb.award((net.minecraft.server.level.ServerLevel) level,
                            Vec3.atCenterOf(self.getBlockPos().above()), taken);
                }
            }
            case FarmRules.SETTLES -> {
                // Nothing on a clock. What these two do is be present, which
                // the block's own properties and the spawn rules answer, and a
                // branch that pretended otherwise would be a branch that cost
                // a tick to do nothing.
            }
            default -> {
            }
        }
    }

    /**
     * Take a bite out of something, leaving the row's share of what it is worth.
     *
     * <p>Split out so that there is exactly one place the multipliers are
     * applied, and so a control can break that place on its own.
     */
    private static void strike(FarmBlockEntity self, Mob mob, double orbs, double drops) {
        Level level = self.getLevel();
        if (level == null) {
            return;
        }
        boolean dying = mob.getHealth() <= BITE;
        mob.hurt(mob.damageSources().generic(), BITE);
        if (!dying) {
            return;
        }
        // Whatever it would have dropped, multiplied, and whatever experience
        // a player kill would have given, kept at the row's share.
        int extra = (int) Math.round(drops) - 1;
        for (int again = 0; again < Math.max(0, extra); again++) {
            for (ItemEntity dropped : level.getEntitiesOfClass(ItemEntity.class,
                    around(self, REACHES))) {
                ItemStack copy = dropped.getItem().copy();
                level.addFreshEntity(new ItemEntity(level, dropped.getX(),
                        dropped.getY(), dropped.getZ(), copy));
            }
        }
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        // The game's own number for what this mob is worth, asked for with the
        // machine standing in for the player -- which is the whole of "leaving
        // the player's credit", and the reason the row's share is applied to
        // this rather than to something we worked out ourselves.
        int worth = (int) Math.round(mob.getExperienceReward(server, mob) * orbs);
        if (worth > 0) {
            ExperienceOrb.award(server, Vec3.atCenterOf(self.getBlockPos().above()),
                    worth);
        }
    }

    /** Which way this machine is pointed. */
    private static Direction facing(FarmBlockEntity self) {
        return self.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? self.getBlockState().getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;
    }

    /** The box a machine works in. */
    private static AABB around(FarmBlockEntity self, double reach) {
        return new AABB(self.getBlockPos()).inflate(reach);
    }

    /** Everything alive in reach that is not a player. */
    private static List<Mob> mobs(FarmBlockEntity self, double reach) {
        Level level = self.getLevel();
        if (level == null) {
            return List.of();
        }
        return level.getEntitiesOfClass(Mob.class, around(self, reach),
                Entity::isAlive);
    }
}
