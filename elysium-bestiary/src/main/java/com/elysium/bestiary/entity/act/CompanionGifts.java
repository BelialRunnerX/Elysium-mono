package com.elysium.bestiary.entity.act;

import com.elysium.bestiary.entity.CompanionEntity;
import com.elysium.bestiary.rules.CompanionRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The one place a companion's one thing happens.
 *
 * <h2>One branch per gift, and no creature named</h2>
 *
 * The third file in this module to be built this way, and the argument has not
 * changed: a branch per <em>thing done</em> rather than per creature. Eleven
 * branches is a thing somebody can read in one sitting and a validator can
 * check the shape of; eleven classes is eleven places for the same bug, and a
 * twelfth creature is a twelfth file.
 *
 * <p>No companion is named anywhere below. If one ever is, the module has
 * started growing a branch per creature and the whole roster will follow it
 * there — which is why {@code validate.py} reads this file and fails if it
 * finds one.
 *
 * <h2>Why every gift is conditional</h2>
 *
 * Each of these reaches only as far as the row says and lasts only as long as
 * the row says. That is not politeness: a gift that reaches everywhere is a
 * permanent effect with a mob somewhere behind it, and a gift that does not
 * fade is a stat the player keeps — at which point they kill the creature for
 * it, which is the opposite of a companion.
 */
public final class CompanionGifts {

    private CompanionGifts() {
    }

    /** How long between one of these and the next, in ticks. */
    public static final int BETWEEN = 60;

    /** How long an effect one of these leaves lasts, in ticks. */
    public static final int LINGERS = 120;

    /** How dark a place has to be before it is worth pointing at. */
    public static final int DARK_ENOUGH = 7;

    /** How far one looks for somewhere dark, in blocks. */
    public static final int LOOKS = 5;

    /**
     * Do the one thing, for whoever is near enough.
     *
     * <p>Handed the row rather than reading it, so a control can hand one built
     * to be caught.
     */
    public static void give(CompanionEntity self, CompanionRules.Companion row) {
        Level level = self.level();
        List<Player> near = nearby(self, row.reach());
        switch (row.gift()) {
            case CompanionRules.SHOWS_DARK -> {
                // It shows where the dark is, which a torch does by being put
                // there. This only points; the player still has to go.
                if (level instanceof ServerLevel server) {
                    BlockPos dark = darkNearby(self);
                    if (dark != null) {
                        server.sendParticles(ParticleTypes.GLOW,
                                dark.getX() + 0.5D, dark.getY() + 1.0D,
                                dark.getZ() + 0.5D, 4, 0.2D, 0.2D, 0.2D, 0.0D);
                    }
                }
            }
            case CompanionRules.REACH -> lasting(near, MobEffects.MOVEMENT_SPEED, 0);
            case CompanionRules.BLOOMS -> {
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            self.getX(), self.getY() + 0.6D, self.getZ(),
                            3, 0.4D, 0.4D, 0.4D, 0.0D);
                }
            }
            case CompanionRules.DIGS -> lasting(near, MobEffects.DIG_SPEED, 0);
            case CompanionRules.HOLDS, CompanionRules.PRESSES,
                 CompanionRules.HIDES -> {
                // These three are done by being interacted with rather than on
                // a clock, and a branch that pretends otherwise would be a
                // branch nothing ever runs.
            }
            case CompanionRules.CHILLS -> {
                for (Monster other : monsters(self, row.reach())) {
                    other.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, LINGERS, 1));
                }
            }
            case CompanionRules.DOUBLES -> lasting(near, MobEffects.INVISIBILITY, 0);
            case CompanionRules.BITES -> {
                for (Monster other : monsters(self, row.reach())) {
                    if (other.getTarget() instanceof Player) {
                        self.setTarget(other);
                    }
                }
            }
            case CompanionRules.BURNS -> {
                for (Monster other : monsters(self, row.reach())) {
                    other.igniteForSeconds(2);
                }
            }
            default -> {
            }
        }
    }

    /** Everybody near enough to be given something. */
    private static List<Player> nearby(CompanionEntity self, double reach) {
        AABB around = new AABB(BlockPos.containing(self.position())).inflate(reach);
        return self.level().getEntitiesOfClass(Player.class, around,
                player -> !player.isSpectator());
    }

    /** And everything near enough to be discouraged. */
    private static List<Monster> monsters(CompanionEntity self, double reach) {
        AABB around = new AABB(BlockPos.containing(self.position())).inflate(reach);
        return self.level().getEntitiesOfClass(Monster.class, around,
                other -> other.isAlive());
    }

    /**
     * Give this to everybody near, for as long as the module allows.
     *
     * <p>One place, so that no branch above can quietly hand out something
     * permanent: the duration is not a parameter.
     */
    private static void lasting(List<? extends LivingEntity> who,
                                net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> what,
                                int strength) {
        for (LivingEntity one : who) {
            one.addEffect(new MobEffectInstance(what, LINGERS, strength, true, false));
        }
    }

    /** Somewhere dark within reach, or null. */
    private static BlockPos darkNearby(CompanionEntity self) {
        BlockPos here = BlockPos.containing(self.position());
        for (int x = -LOOKS; x <= LOOKS; x++) {
            for (int z = -LOOKS; z <= LOOKS; z++) {
                for (int y = -2; y <= 2; y++) {
                    BlockPos at = here.offset(x, y, z);
                    if (self.level().getMaxLocalRawBrightness(at) <= DARK_ENOUGH
                            && self.level().getBlockState(at.below()).isSolidRender(
                                    self.level(), at.below())
                            && self.level().getBlockState(at).isAir()) {
                        return at;
                    }
                }
            }
        }
        return null;
    }
}
