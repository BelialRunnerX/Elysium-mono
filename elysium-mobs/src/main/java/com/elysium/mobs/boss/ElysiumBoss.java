package com.elysium.mobs.boss;

import com.elysium.lib.entity.ElysiumFaction;
import com.elysium.lib.entity.ElysiumScaling;
import com.elysium.mobs.entity.ElysiumMob;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * What the two bosses have in common.
 *
 * <h2>Phases, and why they are health thresholds</h2>
 *
 * A boss with one behaviour is a big mob. What makes a boss fight a fight is
 * that it changes while you are having it, and the player has to notice and
 * respond.
 *
 * The trigger is <b>remaining health</b> rather than elapsed time, because time
 * rewards nothing: a player who fights badly reaches phase two at the same
 * moment as one who fights well. Health means the phase change is something the
 * player caused, which is what makes it read as progress rather than as a
 * timer going off.
 *
 * <h2>The boss bar</h2>
 *
 * Added and removed as players come and go rather than set once, because in a
 * dungeon a second player can walk in halfway through — and a boss bar that
 * only the first person can see makes the fight look like it is not happening
 * to everyone.
 */
public abstract class ElysiumBoss extends ElysiumMob {

    private final ServerBossEvent bossBar;

    /** Which phase is running: 0 at full health, rising as it falls. */
    private int phase;

    protected ElysiumBoss(EntityType<? extends Monster> type, Level level,
                          BossEvent.BossBarColor colour) {
        super(type, level);
        this.bossBar = new ServerBossEvent(getDisplayName(), colour,
                BossEvent.BossBarOverlay.PROGRESS);
        this.bossBar.setDarkenScreen(true);
    }

    /**
     * How many phases this boss has, including the first.
     *
     * Two means one change; three means two. More than three is a fight that
     * outlasts the player's interest in it.
     */
    protected abstract int phaseCount();

    /** Called once when a phase begins, including phase 0 at spawn. */
    protected abstract void onPhaseStart(int phase);

    /** The boss's own behaviour, on the ability cadence. */
    protected abstract void onBossTick(int phase);

    // ------------------------------------------------------------------

    /**
     * A boss scales like everything else, then is multiplied.
     *
     * Deliberately the same {@link ElysiumScaling} curve rather than its own:
     * a boss that scaled differently would be trivial at one level and
     * impossible at another, and there would be no single number a player could
     * use to judge whether they were ready.
     */
    public void initialiseBoss(int mobLevel, net.minecraft.util.RandomSource random) {
        initialise(mobLevel, random);

        net.minecraft.world.entity.ai.attributes.AttributeInstance health =
                getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * bossHealthMultiplier(mobLevel));
        }
        setHealth(getMaxHealth());

        // A boss keeps its own name, not its variant's - the variant still
        // decides its stats and ability, but "Feral" above a boss's head reads
        // as a mistake.
        setCustomName(getBossName());
        setCustomNameVisible(false);   // the bar is the name
        bossBar.setName(getBossName());

        phase = 0;
        onPhaseStart(0);
    }

    /** What a boss is worth at level 1, as a multiple of its family's health. */
    protected static final float BOSS_HEALTH_BASE = 2.0F;

    /** How fast that multiple climbs, per square root of level. */
    protected static final float BOSS_HEALTH_GROWTH = 0.6F;

    /**
     * How much more health a boss has than the same creature would.
     *
     * <h3>Why this is not a constant any more</h3>
     *
     * It was a flat 6x, and that made the first boss a player ever met the
     * hardest one relative to what they could do about it. A Choir came out at
     * 120 x 6 = 720 health — 360 hearts, against a character with a starting
     * weapon and no ascended gear — and then grew only as fast as
     * {@code ElysiumScaling}'s health curve, which is a square root. A wall at
     * the bottom and a formality by the time the player had the damage to
     * answer it: the wrong shape at both ends.
     *
     * The multiplier now climbs itself. A boss starts at
     * {@value #BOSS_HEALTH_BASE}x its family's own health, putting a level-1
     * Choir at 240 and a Praetor at 300 — the range vanilla puts the Ender
     * Dragon and the Wither in, and a fight a starting character can finish. It
     * passes the old 6x at about level 45, by which point the player has
     * ascended gear and the stats to go with it, and keeps climbing after.
     *
     * Square root rather than linear, for the same reason the health curve it
     * multiplies is: a boss with proportionally more health at every level is
     * not a harder fight, only a longer one.
     */
    protected float bossHealthMultiplier(int mobLevel) {
        return BOSS_HEALTH_BASE
                + BOSS_HEALTH_GROWTH * (float) Math.sqrt(Math.max(0, mobLevel - 1));
    }

    protected abstract Component getBossName();

    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }

        bossBar.setProgress(getHealth() / Math.max(1.0F, getMaxHealth()));

        int shouldBe = phaseFor(getHealth() / Math.max(1.0F, getMaxHealth()));
        if (shouldBe > phase) {
            phase = shouldBe;
            onPhaseStart(phase);
        }

        if (tickCount % 10 == 0) {
            onBossTick(phase);
        }
    }

    /**
     * Which phase a fraction of health corresponds to.
     *
     * Phases are evenly spaced across the bar. Uneven spacing — a long first
     * phase and a short desperate last one — reads better in a scripted fight
     * and worse in a procedurally placed one, where the player has no idea how
     * long the fight is supposed to be.
     */
    private int phaseFor(float healthFraction) {
        int phases = Math.max(1, phaseCount());
        int index = (int) ((1.0F - healthFraction) * phases);
        return Math.min(phases - 1, Math.max(0, index));
    }

    public int getPhase() {
        return phase;
    }

    // ------------------------------------------------------------------
    // The bar follows the players, not the other way round
    // ------------------------------------------------------------------

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        bossBar.removeAllPlayers();
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        // Without this a boss removed by anything other than dying - a chunk
        // unloading, a command, the dungeon being abandoned - leaves its bar on
        // every screen that could see it, permanently.
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    // ------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ElysiumPhase", phase);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        phase = tag.getInt("ElysiumPhase");
        bossBar.setName(getBossName());
    }

    /** A boss is never pushed around and never burns. */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }
}
