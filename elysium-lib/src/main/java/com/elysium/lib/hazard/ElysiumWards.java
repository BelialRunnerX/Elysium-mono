package com.elysium.lib.hazard;

import com.elysium.lib.ElysiumLib;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A ward: the thing a meal leaves behind, and the third way to be immune.
 *
 * <h2>Why food is a counter at all</h2>
 *
 * §10.2 asks every hazard to have a free answer and a built one, and §15.5 asks
 * food to carry buffs that answer hazards. Those are the same sentence read
 * twice. The built answers are armour of the right element and a filed claim —
 * both of them projects. The free answer has to be something a player who has
 * neither can do on the afternoon they first walk into a burning valley, and
 * cooking is the one production chain every Minecraft player already has
 * running before they have anything else.
 *
 * <h2>Why a ward is immunity, and not a share</h2>
 *
 * It shipped as a share — a tenth, one source in {@link ElysiumResistance}'s
 * product, worth about eleven per cent more time in a hazard. The project owner
 * decided otherwise: <b>a ration grants immunity to environmental damage</b>.
 * That is the third named state, beside the complete matching set and the
 * filed claim, and it is recorded in the catalogue with the other two.
 *
 * What keeps it from being the cheapest route to the expensive answer is the
 * clock. A set is worn until it breaks and a claim stands until it is struck;
 * a ward lasts {@link #DURATION} and then it is gone, and a player living in a
 * hazard on rations is a player eating on a schedule they will notice. The
 * armour set is what you wear to go in; the claim is what you build to live
 * there; the meal is what you eat to get across.
 *
 * <h2>Why a second helping does nothing but reset the clock</h2>
 *
 * A ward is a state — warded or not — and {@link #isWarded} reads it as one.
 * There is no amplifier to scale and no share to stack, so the only thing a
 * better ration could ever buy is duration, which is the axis where more food
 * honestly means more.
 *
 * <h2>Why the effects are here and not in a content mod</h2>
 *
 * For the same reason the hazards are. The damage tick has to be able to ask
 * "is this player warded against Thermal" without knowing which mod cooked the
 * meal, and a content mod that invented its own thermal ward would be
 * answering a different question. Anything may grant one of these — a food
 * item, a potion, a machine, a villager — and the hazard layer will read it.
 *
 * An add-on that registers a sixth hazard registers its own effect and calls
 * {@link #bind}; nothing about the five below is special except that they are
 * the Empire's.
 */
public final class ElysiumWards {

    private ElysiumWards() {
    }

    /**
     * How long a ward lasts, in ticks.
     *
     * Three minutes. Long enough to cross a hazard and get back, short enough
     * that living in one means eating on a schedule you will notice — which is
     * the difference between food being a counter and food being a toggle.
     */
    public static final int DURATION = 20 * 180;

    /** Hazard to the effect that wards it. Insertion-ordered for legible reports. */
    private static final Map<ElysiumHazard, Holder<MobEffect>> BOUND =
            new LinkedHashMap<>();

    /**
     * Declare that this effect wards this hazard.
     *
     * Idempotent for the same pair and last-writer-wins otherwise, so a pack
     * that wants the Empire's thermal ward to be granted by its own effect can
     * say so without this class knowing the pack exists.
     */
    public static void bind(ElysiumHazard hazard, Holder<MobEffect> effect) {
        if (hazard == null || effect == null) {
            return;
        }
        BOUND.put(hazard, effect);
    }

    /** The effect that wards this hazard, or null if nothing does. */
    public static Holder<MobEffect> wardFor(ElysiumHazard hazard) {
        return hazard == null ? null : BOUND.get(hazard);
    }

    /** How many hazards currently have a ward. Reported at startup. */
    public static int bound() {
        return BOUND.size();
    }

    /**
     * The rule, with no entity around it.
     *
     * <p>A ward is a state. Present means immune, absent means nothing, and the
     * amplifier — the one thing a potion-style effect carries that could have
     * made this a meter — is deliberately not looked at. Separated from
     * {@link #isWarded} so the harness can say so without a LivingEntity.
     *
     * @param present   whether the entity has the hazard's ward at all
     * @param amplifier the level of it, and deliberately unused
     */
    public static boolean wardedBy(boolean present, int amplifier) {
        return present;
    }

    /** Whether this entity's last meal is holding this hazard off entirely. */
    public static boolean isWarded(LivingEntity entity, ElysiumHazard hazard) {
        Holder<MobEffect> ward = wardFor(hazard);
        if (entity == null || ward == null) {
            return false;
        }
        var active = entity.getEffect(ward);
        return wardedBy(active != null, active == null ? 0 : active.getAmplifier());
    }

    // ------------------------------------------------------------------
    // The Empire's five, and the registry they live in
    // ------------------------------------------------------------------

    /**
     * The registrations, held one class down.
     *
     * Not decoration. Touching {@code BuiltInRegistries.MOB_EFFECT} builds
     * Minecraft's registries, which a harness running outside a game cannot do
     * — and the rules above are exactly what a harness needs to execute. A
     * nested class has its own initialiser, so {@link ElysiumWards#wardedBy} can
     * be called without any of this loading.
     */
    public static final class Effects {

        private Effects() {
        }

        public static final DeferredRegister<MobEffect> EFFECTS =
                DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, ElysiumLib.MODID);

        public static final DeferredHolder<MobEffect, MobEffect> THERMAL =
                ward("thermal_ward", ElysiumHazards.THERMAL);

        public static final DeferredHolder<MobEffect, MobEffect> CRYOGENIC =
                ward("cryogenic_ward", ElysiumHazards.CRYOGENIC);

        public static final DeferredHolder<MobEffect, MobEffect> CORROSIVE =
                ward("corrosive_ward", ElysiumHazards.CORROSIVE);

        public static final DeferredHolder<MobEffect, MobEffect> RADIOLOGICAL =
                ward("radiological_ward", ElysiumHazards.RADIOLOGICAL);

        public static final DeferredHolder<MobEffect, MobEffect> PRESSURE =
                ward("pressure_ward", ElysiumHazards.PRESSURE);

        /**
         * One ward, registered and bound in the same breath.
         *
         * Bound here rather than in a separate pass so that the two cannot
         * drift: an effect registered and never bound is a status icon that
         * does nothing, and there would be nothing in the game to tell a player
         * that is what they had eaten.
         */
        private static DeferredHolder<MobEffect, MobEffect> ward(String name,
                                                                 ElysiumHazard hazard) {
            DeferredHolder<MobEffect, MobEffect> holder = EFFECTS.register(name,
                    () -> new WardEffect(hazard));
            bind(hazard, holder);
            return holder;
        }

        /** Call from the library's constructor. */
        public static void register(IEventBus modEventBus) {
            EFFECTS.register(modEventBus);
        }
    }

    /**
     * The effect itself, which does nothing on its own.
     *
     * Deliberately. A ward has no attribute modifier and no tick — its entire
     * function is to be visible to {@link ElysiumProtection}, which reads it
     * once a second in the one place hazard damage is computed. Giving it an
     * attribute as well would mean the protection had two implementations that
     * had to agree.
     *
     * It is BENEFICIAL so that milk, a cleric's cure and every "remove debuffs"
     * mechanic in the ecosystem leave it alone, and it takes the element's own
     * colour so the icon in the corner of the inventory says which hazard it
     * answers without being read.
     */
    private static final class WardEffect extends MobEffect {

        private WardEffect(ElysiumHazard hazard) {
            super(MobEffectCategory.BENEFICIAL, colourOf(hazard));
        }

        private static int colourOf(ElysiumHazard hazard) {
            Integer colour = hazard.getElement().getColour().getColor();
            return colour == null ? 0xFFFFFF : colour;
        }
    }

    /** The id an Elysium ward is registered under, for anything that needs one. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, path);
    }
}
