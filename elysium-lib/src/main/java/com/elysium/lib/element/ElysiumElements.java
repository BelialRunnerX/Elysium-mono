package com.elysium.lib.element;

import com.elysium.lib.ElysiumLib;
import net.minecraft.ChatFormatting;
import com.elysium.lib.affix.ElysiumAffixes;
import com.elysium.lib.affix.ElysiumPsionicAffix;
import com.elysium.lib.stats.ElysiumStats;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/**
 * The five psionic elements of the Sleeping Empire, and the ring they form.
 *
 * <pre>
 *   Void → Plasma → Neural → Dimensional → Kinetic → Void
 * </pre>
 *
 * Each element beats <b>the two that precede it</b> in that order. Void beats
 * Kinetic and Dimensional; Plasma beats Void and Kinetic; and so on round. Every
 * element is strong against exactly two and weak to exactly two, which is what
 * makes the matrix a real decision rather than a ladder.
 *
 * These five live in the library rather than in a content mod because the
 * combat engine here resolves every matchup, and because a content mod that
 * invented its own Void would be a different game. Add-ons are free to register
 * a sixth element and declare whatever relationships they want — including
 * beating one of these — but the ring itself is the Empire's, and it stays put.
 */
public final class ElysiumElements {

    private ElysiumElements() {
    }

    /** The namespace the canonical five are registered under. */
    public static final String NAMESPACE = ElysiumLib.MODID;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, path);
    }

    public static final ResourceLocation VOID_ID = id("void");
    public static final ResourceLocation PLASMA_ID = id("plasma");
    public static final ResourceLocation NEURAL_ID = id("neural");
    public static final ResourceLocation DIMENSIONAL_ID = id("dimensional");
    public static final ResourceLocation KINETIC_ID = id("kinetic");

    public static final ElysiumElement VOID = ElysiumElement.register(
            VOID_ID, ChatFormatting.LIGHT_PURPLE, Set.of(KINETIC_ID, DIMENSIONAL_ID),
            List.of(ElysiumStats.RESILIENCE, ElysiumStats.WILLPOWER));

    public static final ElysiumElement PLASMA = ElysiumElement.register(
            PLASMA_ID, ChatFormatting.GOLD, Set.of(VOID_ID, KINETIC_ID),
            List.of(ElysiumStats.STRENGTH, ElysiumStats.ACCURACY));

    public static final ElysiumElement NEURAL = ElysiumElement.register(
            NEURAL_ID, ChatFormatting.GREEN, Set.of(PLASMA_ID, VOID_ID),
            List.of(ElysiumStats.INTELLECT, ElysiumStats.AGILITY));

    public static final ElysiumElement DIMENSIONAL = ElysiumElement.register(
            DIMENSIONAL_ID, ChatFormatting.AQUA, Set.of(NEURAL_ID, PLASMA_ID),
            List.of(ElysiumStats.AGILITY, ElysiumStats.REFLEXES));

    public static final ElysiumElement KINETIC = ElysiumElement.register(
            KINETIC_ID, ChatFormatting.YELLOW, Set.of(DIMENSIONAL_ID, NEURAL_ID),
            List.of(ElysiumStats.STRENGTH, ElysiumStats.RETRIBUTION));

    /**
     * The psionic affixes the five confer on gear.
     *
     * Kept separate from registration because an affix names an attribute, and
     * attributes are a vanilla registry that is not ready when this class is
     * first touched. Called from the library's constructor, by which time it
     * is.
     */
    public static void registerPsionicAffixes() {
        ElysiumAffixes.register(new ElysiumPsionicAffix(VOID,
                Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE, 0.10F, 0.25F));
        ElysiumAffixes.register(new ElysiumPsionicAffix(PLASMA,
                Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.08F, 0.20F));
        ElysiumAffixes.register(new ElysiumPsionicAffix(NEURAL,
                Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.05F, 0.15F));
        ElysiumAffixes.register(new ElysiumPsionicAffix(DIMENSIONAL,
                Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.06F, 0.18F));
        ElysiumAffixes.register(new ElysiumPsionicAffix(KINETIC,
                Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.07F, 0.22F));
    }

    /** Touching this class registers all five. Called once from the library. */
    public static void bootstrap() {
        // The static initialisers above have already run by the time anything
        // reaches this line. The method exists so the caller's intent is
        // legible, rather than relying on a bare class reference that a future
        // tidy-up would delete as unused.
    }
}
