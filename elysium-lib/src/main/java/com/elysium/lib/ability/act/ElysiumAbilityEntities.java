package com.elysium.lib.ability.act;

import com.elysium.lib.ability.ElysiumAbilityRules;
import com.elysium.lib.ability.ElysiumAbilityShapes;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The two entity types the ability system needs, filled in from outside.
 *
 * <h2>Why a holder rather than a reference</h2>
 *
 * Three of the thirty abilities cannot happen without something existing in
 * the world: two throw a bolt and one calls up a shade. Those are mobs, and
 * mobs belong in {@code elysium-bestiary} — which is built <em>after</em> this
 * library and after the module whose relics carry the abilities, because the
 * dependencies in this project run one way only.
 *
 * <p>So the library says what it needs and the module that has it fills this
 * in when it registers. An ability whose entity nobody filled in does not
 * silently do nothing: {@link #missing()} names it, and the validator refuses
 * a build where anything is missing.
 *
 * <h2>And why there is a second thing to fill in</h2>
 *
 * One bolt serves every ability that throws, and what differs between a
 * hunter's mark and a stormcall is how hard the thing hits — which this library
 * has already worked out from what each was priced at. Left to itself the bolt
 * would carry one number for both, and the stormcall's price would buy nothing.
 * So the module that owns the entity also supplies {@link #dress}, which is
 * handed each freshly made thing and the ability that made it. It counts as
 * missing when it is absent, for the same reason the type does: an ability that
 * fires and does the wrong amount is harder to notice than one that does
 * nothing.
 */
public final class ElysiumAbilityEntities {

    private ElysiumAbilityEntities() {
    }

    private static Supplier<EntityType<?>> bolt;
    private static Supplier<EntityType<?>> summon;
    private static BiConsumer<Entity, ElysiumAbilityRules.Ability> dress;

    /** What a thrown ability throws. Set by whichever module has the entity. */
    public static void bolt(Supplier<EntityType<?>> type) {
        bolt = type;
    }

    /** What a summoning ability calls up. */
    public static void summon(Supplier<EntityType<?>> type) {
        summon = type;
    }

    /** The bolt, or null when nothing has filled it in. */
    public static EntityType<?> bolt() {
        return bolt == null ? null : bolt.get();
    }

    /** The summon, or null when nothing has filled it in. */
    public static EntityType<?> summon() {
        return summon == null ? null : summon.get();
    }

    /**
     * How a freshly made thing is told which ability made it.
     *
     * <p>Set by whichever module owns the entities, at the same moment as the
     * types. One call rather than two so that a module cannot supply an entity
     * and forget the numbers that go on it.
     */
    public static void dress(BiConsumer<Entity, ElysiumAbilityRules.Ability> how) {
        dress = how;
    }

    /**
     * Tell this thing what made it.
     *
     * <p>Quietly does nothing when nobody supplied a way, because by then
     * {@link #missing()} has already said so and throwing here would turn a
     * misconfigured install into a crash in the middle of somebody's fight.
     */
    public static void dressed(Entity made, ElysiumAbilityRules.Ability ability) {
        if (dress != null && made != null && ability != null) {
            dress.accept(made, ability);
        }
    }

    /**
     * The shapes that still have nothing behind them.
     *
     * <p>Empty on a whole install. On a partial one it names exactly which of
     * the six ways an ability can happen would quietly do nothing, which is
     * the state this whole system was in before anybody noticed.
     */
    public static java.util.List<String> missing() {
        java.util.List<String> bad = new java.util.ArrayList<>();
        if (bolt == null) {
            bad.add(ElysiumAbilityShapes.BOLT);
        }
        if (summon == null) {
            bad.add(ElysiumAbilityShapes.SUMMON);
        }
        if (dress == null) {
            bad.add("what tells a thrown thing which ability threw it");
        }
        return java.util.List.copyOf(bad);
    }
}
