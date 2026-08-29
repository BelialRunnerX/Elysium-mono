package com.elysium.core.item;

import com.elysium.core.Elysium;
import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.element.ElysiumElements;
import com.elysium.lib.item.ElysiumGearMaterial;
import com.elysium.lib.item.ElysiumGearMaterial.ArmourProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tiers;

import java.util.ArrayList;
import java.util.List;

/**
 * Every material Elysium forges gear from.
 *
 * <h2>Three families</h2>
 *
 * <ol>
 *   <li><b>The Empire's own</b> — Voidglass, Aetherium, Neutronium. Each
 *       resonates with a psionic element, which is what makes rune alignment
 *       mean something: a rune socketed into gear of its own element bites
 *       harder than the same rune in gear that merely tolerates it.</li>
 *   <li><b>Vanilla</b> — copper, iron, gold, diamond, netherite. The ramp that
 *       was missing. Without these a player had no Elysium gear at all until
 *       they found Voidglass, which meant the stat system, the sockets and the
 *       level requirement did nothing for the whole early game.</li>
 *   <li><b>Modded</b> — the metals other mods commonly add. Registered
 *       unconditionally and gated at runtime on their ingredient tag; see
 *       {@link ElysiumGearMaterial} for why it has to work that way.</li>
 * </ol>
 *
 * <h2>How the vanilla materials were pitched</h2>
 *
 * Tier 0 for everything up to diamond, tier 1 for netherite, against the
 * Empire's 0/1/2. So vanilla gear participates fully — it sockets, reforges,
 * takes an element and shows a required level — without ever overtaking the
 * material you had to go and find. Diamond is a good hammer; it is not
 * Neutronium.
 *
 * The element assignments are not arbitrary. Copper conducts, so Plasma. Iron
 * is the blunt structural metal, so Kinetic. Gold is the conductor vanilla
 * already treats as magically receptive, so Neural. Diamond is a lattice that
 * bends light, so Dimensional. Netherite comes back from somewhere else
 * entirely, so Void. That covers all five, which is deliberate: it means a
 * player can align any rune they own long before they reach the Empire's
 * materials, and the elemental system is something they learn early rather than
 * meet at the end.
 */
public final class ElysiumMaterials {

    private ElysiumMaterials() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Elysium.MODID, path);
    }

    private static ResourceLocation common(String path) {
        return ResourceLocation.fromNamespaceAndPath("c", path);
    }

    // ==================================================================
    // The Empire's own
    // ==================================================================

    /** Cut, not cast. Resonates with Void. */
    public static final ElysiumGearMaterial VOIDGLASS = ElysiumGearMaterial.builder(id("voidglass"))
            .ingredient(common("ingots/voidglass"))
            .element(ElysiumElements.VOID)
            .tier(0)
            .toolTier(Tiers.DIAMOND)
            .damageBonus(0.0F)
            .vanilla()   // Elysium ships the ingot itself, so it is always there.
            .register();

    /** Light and planar. Resonates with Dimensional. */
    public static final ElysiumGearMaterial AETHERIUM = ElysiumGearMaterial.builder(id("aetherium"))
            .ingredient(common("ingots/aetherium"))
            .element(ElysiumElements.DIMENSIONAL)
            .tier(1)
            .toolTier(Tiers.DIAMOND)
            .damageBonus(1.0F)
            .vanilla()
            .register();

    /** Absurdly dense. Resonates with Kinetic. */
    public static final ElysiumGearMaterial NEUTRONIUM =
            ElysiumGearMaterial.builder(id("neutronium"))
                    .ingredient(common("ingots/neutronium"))
                    .element(ElysiumElements.KINETIC)
                    .tier(2)
                    .toolTier(Tiers.NETHERITE)
                    .damageBonus(2.0F)
                    // Deliberately no ArmourProfile. Neutronium armour is
                    // hand-written in Elysium.java against its own material and
                    // its own textures; giving it one here would register a
                    // second neutronium_helmet under the same name.
                    .vanilla()
                    .register();

    // ==================================================================
    // Vanilla
    // ==================================================================

    /** Copper conducts. The first Elysium gear most players will hold. */
    public static final ElysiumGearMaterial COPPER = vanillaMaterial(
            "copper", ElysiumElements.PLASMA, Tiers.STONE, 0.0F,
            new ArmourProfile(1, 4, 5, 2, 10, 0.0F, 0.0F, 14));

    /** Iron: blunt, structural, Kinetic. */
    public static final ElysiumGearMaterial IRON = vanillaMaterial(
            "iron", ElysiumElements.KINETIC, Tiers.IRON, 0.5F,
            new ArmourProfile(2, 5, 6, 2, 9, 0.0F, 0.0F, 15));

    /** Gold: soft, fast, and receptive in a way vanilla already agrees with. */
    public static final ElysiumGearMaterial GOLD = vanillaMaterial(
            "gold", ElysiumElements.NEURAL, Tiers.GOLD, 0.0F,
            new ArmourProfile(1, 3, 5, 2, 25, 0.0F, 0.0F, 7));

    /**
     * Diamond: a lattice that bends light.
     *
     * The one vanilla material whose ingredient is not an ingot — the common
     * tag is {@code c:gems/diamond}, and a recipe written against
     * {@code c:ingots/diamond} would silently never resolve.
     */
    public static final ElysiumGearMaterial DIAMOND = vanillaMaterial(
            "diamond", common("gems/diamond"), ElysiumElements.DIMENSIONAL, Tiers.DIAMOND, 1.0F,
            new ArmourProfile(3, 6, 8, 3, 10, 2.0F, 0.0F, 33));

    /** Netherite: it comes back from somewhere else. */
    public static final ElysiumGearMaterial NETHERITE = vanillaMaterial(
            "netherite", ElysiumElements.VOID, Tiers.NETHERITE, 1.5F,
            new ArmourProfile(3, 6, 8, 3, 15, 3.0F, 0.1F, 37));

    // ==================================================================
    // Modded
    // ==================================================================

    /**
     * The metals other mods commonly add, and the element each answers to.
     *
     * Chosen by what actually turns up in modpacks rather than by an attempt at
     * completeness, which is unachievable — {@code ElysiumMaterialConfig} is
     * the answer for anything missing here.
     *
     * Every one of these is registered whether or not the mod supplying it is
     * installed. That is not waste; it is what keeps the item registry the same
     * shape in every world, so removing a mod never orphans a saved stack that
     * belonged to a different one.
     */
    private static final Object[][] MODDED = {
            // name          element                       tool tier      dmg   armour tier
            {"tin",          ElysiumElements.NEURAL,       Tiers.IRON,    0.0F, 0},
            {"zinc",         ElysiumElements.PLASMA,       Tiers.IRON,    0.0F, 0},
            {"lead",         ElysiumElements.KINETIC,      Tiers.IRON,    0.5F, 0},
            {"silver",       ElysiumElements.NEURAL,       Tiers.IRON,    0.5F, 0},
            {"nickel",       ElysiumElements.KINETIC,      Tiers.IRON,    0.5F, 0},
            {"aluminum",     ElysiumElements.DIMENSIONAL,  Tiers.IRON,    0.0F, 0},
            {"platinum",     ElysiumElements.NEURAL,       Tiers.DIAMOND, 1.0F, 1},
            {"bronze",       ElysiumElements.PLASMA,       Tiers.IRON,    0.5F, 0},
            {"brass",        ElysiumElements.PLASMA,       Tiers.IRON,    0.5F, 0},
            {"steel",        ElysiumElements.KINETIC,      Tiers.DIAMOND, 1.0F, 1},
            {"invar",        ElysiumElements.KINETIC,      Tiers.DIAMOND, 1.0F, 1},
            {"constantan",   ElysiumElements.NEURAL,       Tiers.DIAMOND, 1.0F, 1},
            {"electrum",     ElysiumElements.NEURAL,       Tiers.DIAMOND, 1.0F, 1},
            {"osmium",       ElysiumElements.VOID,         Tiers.DIAMOND, 1.0F, 1},
            {"uranium",      ElysiumElements.VOID,         Tiers.DIAMOND, 1.5F, 1},
            {"titanium",     ElysiumElements.DIMENSIONAL,  Tiers.DIAMOND, 1.5F, 1},
            {"tungsten",     ElysiumElements.KINETIC,      Tiers.DIAMOND, 1.5F, 1},
            {"cobalt",       ElysiumElements.DIMENSIONAL,  Tiers.DIAMOND, 1.0F, 1},
    };

    /** Every modded material, in table order. Populated by {@link #bootstrap()}. */
    public static final List<ElysiumGearMaterial> MODDED_MATERIALS = new ArrayList<>();

    // ==================================================================

    private static ElysiumGearMaterial vanillaMaterial(String name, ElysiumElement element,
                                                       net.minecraft.world.item.Tier toolTier,
                                                       float damage, ArmourProfile armour) {
        return vanillaMaterial(name, common("ingots/" + name), element, toolTier, damage, armour);
    }

    private static ElysiumGearMaterial vanillaMaterial(String name, ResourceLocation ingredient,
                                                       ElysiumElement element,
                                                       net.minecraft.world.item.Tier toolTier,
                                                       float damage, ArmourProfile armour) {
        return ElysiumGearMaterial.builder(id(name))
                .ingredient(ingredient)
                .element(element)
                .tier(toolTier == Tiers.NETHERITE ? 1 : 0)
                .toolTier(toolTier)
                .damageBonus(damage)
                .armour(armour)
                .vanilla()
                .register();
    }

    /**
     * Registers the modded table, and anything the config adds on top of it.
     *
     * Called from the mod constructor. The config is read before this, which is
     * the whole reason a config entry needs a restart: a material that appears
     * later than registration cannot have items.
     */
    public static void bootstrap() {
        for (Object[] row : MODDED) {
            String name = (String) row[0];
            int tier = (Integer) row[4];
            MODDED_MATERIALS.add(ElysiumGearMaterial.builder(id(name))
                    .ingredient(common("ingots/" + name))
                    .element((ElysiumElement) row[1])
                    .tier(tier)
                    .toolTier((net.minecraft.world.item.Tier) row[2])
                    .damageBonus((Float) row[3])
                    .armour(tier == 0
                            ? new ArmourProfile(2, 5, 6, 2, 10, 0.5F, 0.0F, 16)
                            : new ArmourProfile(3, 6, 8, 3, 12, 2.0F, 0.0F, 28))
                    .register());
        }

        for (ElysiumMaterialConfig.Extra extra : ElysiumMaterialConfig.extras()) {
            MODDED_MATERIALS.add(ElysiumGearMaterial.builder(id(extra.name()))
                    .ingredient(common("ingots/" + extra.name()))
                    .element(extra.element())
                    .tier(extra.tier())
                    .toolTier(extra.tier() == 0 ? Tiers.IRON : Tiers.DIAMOND)
                    .damageBonus(extra.tier() == 0 ? 0.5F : 1.0F)
                    .armour(extra.tier() == 0
                            ? new ArmourProfile(2, 5, 6, 2, 10, 0.5F, 0.0F, 16)
                            : new ArmourProfile(3, 6, 8, 3, 12, 2.0F, 0.0F, 28))
                    .register());
        }
    }

    /** The five vanilla materials, for the creative tab and the recipe generator. */
    public static List<ElysiumGearMaterial> vanillaMaterials() {
        return List.of(COPPER, IRON, GOLD, DIAMOND, NETHERITE);
    }

    /** The Empire's three. */
    public static List<ElysiumGearMaterial> elysiumMaterials() {
        return List.of(VOIDGLASS, AETHERIUM, NEUTRONIUM);
    }
}
