package com.example.tide;

import com.elysium.lib.ElysiumHooks;
import com.elysium.lib.affix.ElysiumAffix;
import com.elysium.lib.character.ElysiumClass;
import com.elysium.lib.character.ElysiumPassive;
import com.elysium.lib.character.ElysiumRace;
import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.element.ElysiumElements;
import com.elysium.lib.entity.ElysiumFaction;
import com.elysium.lib.item.ElysiumRune;
import com.elysium.lib.standing.ElysiumDispatch;
import com.elysium.lib.standing.ElysiumRewards;
import com.elysium.lib.stats.ElysiumStat;
import com.elysium.lib.stats.ElysiumStatBlock;
import com.elysium.lib.stats.ElysiumStats;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Set;

/**
 * The worked example from EXTENDING.md, compiled.
 *
 * <h2>Why this exists</h2>
 *
 * A guide that has never been run is a guess. This is a third Elysium mod — not
 * the library, not elysium-core — that uses every extension point the library
 * offers, and it is built by the same harness that builds the other two. If an
 * extension point stops being usable from outside, this stops compiling, which
 * is the only way to find out before an add-on author does.
 *
 * It deliberately does not depend on elysium-core. That is the property under
 * test: a mod can be built on the library alone.
 */
public final class TideMod {

    public static final String MODID = "tidemod";

    private TideMod() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    // ------------------------------------------------------------------
    // A stat
    // ------------------------------------------------------------------

    /** Half its ceiling at 60 points, approaching 40% and never arriving. */
    public static final ElysiumStat TENACITY = ElysiumStat.curve(
            id("tenacity"), ChatFormatting.DARK_GREEN, 60.0F, 0.40F);

    // ------------------------------------------------------------------
    // An element, standing outside the Empire's ring
    // ------------------------------------------------------------------

    public static final ResourceLocation TIDE_ID = id("tide");

    public static final ElysiumElement TIDE = ElysiumElement.register(
            TIDE_ID, ChatFormatting.BLUE,
            Set.of(ElysiumElements.PLASMA_ID, ElysiumElements.KINETIC_ID),
            List.of(ElysiumStats.AGILITY, ElysiumStats.WILLPOWER));

    // ------------------------------------------------------------------
    // A race and a class
    // ------------------------------------------------------------------

    private static final class Undertow implements ElysiumPassive {
        @Override
        public Component getDisplayName() {
            return Component.translatable("elysium.passive.tidemod.undertow");
        }

        @Override
        public Component getDescription() {
            return Component.translatable("elysium.passive.tidemod.undertow.desc");
        }

        @Override
        public float defenceScale(Player defender, DamageSource source) {
            return defender.isInWater() ? 0.7F : 1.0F;
        }
    }

    public static final ElysiumRace TIDEBORN = ElysiumRace.register(
            id("tideborn"), ChatFormatting.BLUE,
            ElysiumStatBlock.of(
                    ElysiumStats.VITALITY, 4, ElysiumStats.FORTITUDE, 3,
                    ElysiumStats.RESILIENCE, 4, ElysiumStats.STRENGTH, 3,
                    ElysiumStats.AGILITY, 8, ElysiumStats.ACCURACY, 3,
                    ElysiumStats.REFLEXES, 5, ElysiumStats.RETRIBUTION, 2,
                    ElysiumStats.INTELLECT, 4, ElysiumStats.WILLPOWER, 4,
                    ElysiumStats.LUCK, 2, ElysiumStats.PRESENCE, 2),
            ElysiumStatBlock.of(ElysiumStats.AGILITY, 2, ElysiumStats.REFLEXES, 1),
            new Undertow());

    private static final class DeepDiver implements ElysiumPassive {
        @Override
        public Component getDisplayName() {
            return Component.translatable("elysium.class.tidemod.diver");
        }

        @Override
        public Component getDescription() {
            return Component.translatable("elysium.class.tidemod.diver.desc");
        }

        @Override
        public float regenScale(Player player) {
            return player.isInWater() ? 2.0F : 1.0F;
        }
    }

    public static final ElysiumClass DIVER = ElysiumClass.register(
            id("diver"), ChatFormatting.AQUA,
            ElysiumStatBlock.of(ElysiumStats.AGILITY, 1, ElysiumStats.VITALITY, 1),
            new DeepDiver());

    // ------------------------------------------------------------------
    // A rune
    // ------------------------------------------------------------------

    public static final ElysiumRune TIDECALL = ElysiumRune.builder(id("tidecall"))
            .element(TIDE)
            .affix(new ElysiumAffix("tidecall", Attributes.MOVEMENT_SPEED,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.05F, 0.05F))
            .effect((player, gear, aligned) -> player.heal(aligned ? 1.0F : 0.5F))
            .register();

    // ------------------------------------------------------------------
    // Everything told to the library from the mod constructor
    // ------------------------------------------------------------------

    public static void register() {
        // Touching the class runs the static registrations above.
        ElysiumStats.bootstrap();

        ElysiumFaction.addRule(entity ->
                entity instanceof Drowned ? ElysiumFaction.UNSWORN : null);
        ElysiumFaction.addNamedCombatantRule(entity -> entity instanceof Drowned);

        ElysiumDispatch.register(new ElysiumDispatch.Dispatcher() {
            @Override
            public ElysiumFaction faction() {
                return ElysiumFaction.UNSWORN;
            }

            @Override
            public Class<? extends Mob> type() {
                return Drowned.class;
            }

            @Override
            public Mob create(ServerLevel level, int band) {
                return net.minecraft.world.entity.EntityType.DROWNED.create(level);
            }

            @Override
            public void afterPlaced(Mob mob, ServerLevel level, Player player,
                                    BlockPos pos, int band) {
                mob.setPersistenceRequired();
            }
        });

        ElysiumRewards.register((tier, random) -> switch (tier) {
            case 3 -> new ItemStack(Items.HEART_OF_THE_SEA);
            case 2 -> new ItemStack(Items.NAUTILUS_SHELL);
            default -> ItemStack.EMPTY;   // decline; let another provider answer
        });

        // A supplier, not a block. Your own ore will be a DeferredHolder, which
        // has no value yet during construction — passing holder.get() here
        // throws "Trying to access unbound value" and kills your mod on launch.
        // The holder IS a Supplier, so pass it directly; the library resolves
        // it the first time a block is broken.
        ElysiumHooks.registerOre(() -> net.minecraft.world.level.block.Blocks.PRISMARINE, false);
        ElysiumHooks.setCodex(() -> new ItemStack(Items.BOOK));
    }

    /**
     * Reads a stat the library has never heard of, which is the half the
     * library does not do for you.
     */
    public static float tenacityShare(Player player) {
        return TENACITY.proportionOf(ElysiumStats.get(player, TENACITY));
    }
}
