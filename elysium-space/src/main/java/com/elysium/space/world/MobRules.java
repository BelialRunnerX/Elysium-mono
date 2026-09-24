package com.elysium.space.world;

import java.util.List;

/**
 * The space mobs' numbers: Stellaris 1.4.25's, from each mob's
 * setCustomAttributes() and EntityRegistry sizes, under our names. Pure Java, so
 * the harness checks the table without a game; SpaceMobs reads it for the
 * attributes and entity sizes, so the two can never disagree.
 *
 * <p>Credit: Stellaris, (c) Exodus Studio, CC BY-NC-SA 4.0. See CREDITS.md.
 */
public final class MobRules {

    private MobRules() {
    }

    /** Where a mob belongs: the Stellaris world it came from, under our name. */
    public record Mob(String id, String portedFrom, String home, boolean hostile,
                      double health, double speed, double damage,
                      double knockbackResistance, double attackKnockback, double followRange,
                      float width, float height) {
    }

    public static final Mob MOONFOLK = new Mob("moonfolk", "alien", "pale_moon", false,
            20.0D, 0.5D, 0.0D, 0.0D, 0.0D, 48.0D, 0.75F, 2.5F);
    public static final Mob HOLLOW_MOONFOLK = new Mob("hollow_moonfolk", "alien_zombie", "pale_moon", true,
            20.0D, 0.3D, 3.0D, 0.0D, 0.0D, 16.0D, 0.6F, 2.4F);
    public static final Mob VOID_CRAWLER = new Mob("void_crawler", "star_crawler", "pale_moon", true,
            40.0D, 0.4D, 9.0D, 0.0D, 0.0D, 16.0D, 1.3F, 1.0F);
    public static final Mob FROST_RAPTOR = new Mob("frost_raptor", "martian_raptor", "frostfall", true,
            20.0D, 0.3D, 3.0D, 0.0D, 0.0D, 16.0D, 0.75F, 2.0F);
    public static final Mob CINDER_BOAR = new Mob("cinder_boar", "mogler", "cinderveil", true,
            40.0D, 0.3D, 6.0D, 0.6D, 0.6D, 16.0D, 1.4F, 1.4F);
    public static final Mob CINDERLING = new Mob("cinderling", "pygro", "cinderveil", true,
            16.0D, 0.35D, 5.0D, 0.0D, 0.0D, 16.0D, 0.6F, 1.8F);
    public static final Mob CINDERLING_BRUTE = new Mob("cinderling_brute", "pygro_brute", "cinderveil", true,
            16.0D, 0.35D, 5.0D, 0.0D, 0.0D, 16.0D, 0.6F, 1.8F);

    public static final List<Mob> ALL = List.of(MOONFOLK, HOLLOW_MOONFOLK, VOID_CRAWLER, FROST_RAPTOR,
            CINDER_BOAR, CINDERLING, CINDERLING_BRUTE);

    /** Hollow moonfolk's rime spit (Stellaris's IceSpit): an arrow-like shard. */
    public static final double SPIT_DAMAGE = 2.0D;
    public static final float SPIT_SPEED = 1.4F;
    public static final float SPIT_SPREAD = 12.0F;
    /** Added to the aim per block of distance, so the shard arcs onto the target. */
    public static final double SPIT_ARC = 0.2D;
    /** Ticks a shard flies before it melts; water melts it at once. */
    public static final int SPIT_LIFETIME = 60;
    /** Ticks of slowness a hit leaves (Elysium's: Stellaris's shard only did damage). */
    public static final int SPIT_CHILL_TICKS = 40;
    /** Ticks between spits, and the range the spitter keeps. */
    public static final int SPIT_INTERVAL = 40;
    public static final float SPIT_RANGE = 15.0F;

    /** Ticks the attack animation runs after a hit (frost raptor, void crawler). */
    public static final int ATTACK_ANIMATION_TICKS = 10;

    public static Mob named(String id) {
        for (Mob mob : ALL) {
            if (mob.id().equals(id)) {
                return mob;
            }
        }
        return null;
    }

    /** What a void crawler's plating turns: arrows. Named by class so the rule needs no game. */
    public static boolean crawlerDeflects(String projectileClassSimpleName) {
        return "Arrow".equals(projectileClassSimpleName) || "SpectralArrow".equals(projectileClassSimpleName);
    }
}
