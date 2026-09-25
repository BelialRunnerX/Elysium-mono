package com.elysium.lib.ability.act;

import com.elysium.lib.ability.ElysiumAbilityRules;
import com.elysium.lib.ability.ElysiumAbilityShapes;

import net.minecraft.world.entity.EntityType;

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
 */
public final class ElysiumAbilityEntities {

    private ElysiumAbilityEntities() {
    }

    private static Supplier<EntityType<?>> bolt;
    private static Supplier<EntityType<?>> summon;

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
        return java.util.List.copyOf(bad);
    }
}
