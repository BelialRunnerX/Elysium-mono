package com.elysium.npcs.entity;

import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The five figures of the court, and what each of them is for.
 *
 * <h2>Why an enum and not a registry</h2>
 *
 * Everything else in this project that could be extended by another mod is a
 * registry. This is not one, on purpose: these five are named characters with
 * faces, offices and a place in the fiction. "Register your own member of the
 * Imperial court" is not an extension point anybody wants — a pack that wants
 * more traders wants more traders, which is a different mod with its own
 * entity. Making this a registry would be machinery in service of a use nobody
 * has.
 *
 * <h2>What separates them</h2>
 *
 * Each has an office, and the office decides three things: which meter they
 * read, how far up it you must be before they will deal with you at all, and
 * what they pay in. The Empire's officers read Suspicion — they are interested
 * in people the Code has noticed — and the two who stand outside the chain of
 * command read Favor instead.
 *
 * <h2>The reward tier, and why it is not an item list</h2>
 *
 * None of them names an item. Each asks the library for a reward at a tier, and
 * whatever mod has registered rewards answers — so with elysium-core installed
 * the court pays in runes and reforge catalysts, and with it absent the court
 * pays in whatever else is on offer. An envoy that carried its own loot table
 * would be a fourth place to keep item ids in step with the mod that owns them.
 */
public enum EnvoyKind {

    /**
     * Elysomnion, on the throne. The only one who deals in the top tier, and
     * the only one who will not look at you until the Code has taken a serious
     * interest.
     */
    EMPEROR("emperor", Meter.SUSPICION, ElysiumStanding.BAND_HUNTED, 3,
            Regalia.CROWN, Regalia.CAPE, Regalia.PAULDRONS),

    /**
     * Sylphara Voss, Chief Imperial Architect. Deals in the making of things,
     * which is why what she gives is worth most to somebody who reforges.
     */
    ARCHITECT("architect", Meter.SUSPICION, 1, 2,
            Regalia.COLLAR, Regalia.CAPE),

    /**
     * A Sentinel of the stealth envoy class. Not a person so much as an
     * instrument, and the only one who will trade with somebody the Code has
     * not noticed at all — observation is its whole office.
     */
    SENTINEL("sentinel", Meter.SUSPICION, 0, 1,
            Regalia.VISOR, Regalia.PAULDRONS),

    /**
     * Lillith, Fleet Commander. Reads Favor rather than Suspicion: the fleet
     * recruits from people the Unsworn already trust.
     */
    COMMANDER("commander", Meter.FAVOR, 1, 2,
            Regalia.CAPE),

    /**
     * Aurelia, Queen, and before that a sentient star. Reads Favor, asks for
     * the most of it, and is the only one who does not carry a weapon.
     */
    QUEEN("queen", Meter.FAVOR, ElysiumStanding.BAND_HUNTED, 3,
            Regalia.CROWN, Regalia.CAPE, Regalia.HALO);

    /** Which standing meter an envoy reads before deciding to deal. */
    public enum Meter { FAVOR, SUSPICION }

    /**
     * The optional model parts.
     *
     * One model carries all of them and hides what a kind does not have, rather
     * than five models with four parts in common. Five models would be five
     * copies of a humanoid to keep in step, and the moment one of them gained
     * an arm and the others did not, the court would stop reading as one set.
     */
    public enum Regalia { CROWN, CAPE, PAULDRONS, COLLAR, VISOR, HALO }

    private final String id;
    private final Meter meter;
    private final int bandRequired;
    private final int rewardTier;
    private final java.util.Set<Regalia> regalia;

    EnvoyKind(String id, Meter meter, int bandRequired, int rewardTier, Regalia... regalia) {
        this.id = id;
        this.meter = meter;
        this.bandRequired = bandRequired;
        this.rewardTier = rewardTier;
        this.regalia = java.util.Set.of(regalia);
    }

    public String id() {
        return id;
    }

    public Meter meter() {
        return meter;
    }

    public int rewardTier() {
        return rewardTier;
    }

    public boolean wears(Regalia part) {
        return regalia.contains(part);
    }

    public ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath("elysiumnpcs",
                "textures/entity/envoy/" + id + ".png");
    }

    public Component displayName() {
        return Component.translatable("entity.elysiumnpcs.envoy." + id);
    }

    /** What they say when they will not deal with you. */
    public Component refusal() {
        return Component.translatable("elysiumnpcs.refusal." + id);
    }

    /**
     * Whether this player is far enough up the right meter.
     *
     * Read at the moment of the trade rather than at spawn, so a player who
     * climbs the meter while an envoy is standing there can then deal with
     * them. The alternative — deciding at spawn — produces an envoy who
     * refuses for a reason that is no longer true, which is indistinguishable
     * from a bug.
     */
    public boolean willDealWith(Player player) {
        int value = meter == Meter.FAVOR
                ? ElysiumStanding.getFavor(player)
                : ElysiumStanding.getSuspicion(player);
        return ElysiumStanding.bandOf(value) >= bandRequired;
    }

    public static EnvoyKind byId(String id) {
        for (EnvoyKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return SENTINEL;
    }
}
