package com.elysium.arcana.registry;

import com.elysium.arcana.ElysiumArcana;
import com.elysium.arcana.rules.RelicRules;
import com.elysium.lib.ability.ElysiumAbilityRules;
import com.elysium.lib.ability.act.ElysiumAbilityUse;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thirty relics, each one a slot and a short list of abilities.
 *
 * <p>The abilities are the library's, and every list here has already been
 * through {@link ElysiumAbilityRules#cannotShareAnItem} in the harness. The
 * item class does one thing the rules cannot: it says, in the tooltip, what
 * the relic actually does — read out of the ability roster rather than
 * written into a translation string, so a retuned cooldown changes what the
 * tooltip says.
 */
public final class RelicItems {

    private RelicItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumArcana.MODID);

    /** The game's own rarity colours, one per band. */
    private static final Rarity[] COLOURS = {
            Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC};

    public static final Map<String, DeferredItem<Item>> ALL = register();

    private static Map<String, DeferredItem<Item>> register() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (RelicRules.Relic relic : RelicRules.RELICS) {
            // Refused here as well as in the harness, because a relic built by
            // hand later would not go through the harness at all.
            List<String> trouble =
                    ElysiumAbilityRules.cannotShareAnItem(relic.abilities());
            if (!trouble.isEmpty()) {
                throw new IllegalStateException(relic.id() + " carries abilities no "
                        + "item is allowed to carry: " + trouble);
            }
            made.put(relic.id(), ITEMS.register(relic.id(), () -> new RelicItem(
                    new Item.Properties()
                            .stacksTo(1)
                            .rarity(COLOURS[Math.max(0,
                                    Math.min(COLOURS.length - 1,
                                            relic.rarity() - 1))]),
                    relic)));
        }
        if (made.size() != RelicRules.RELICS.size()) {
            throw new IllegalStateException("there are " + RelicRules.RELICS.size()
                    + " relics and " + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    /** A relic, which knows which one it is. */
    public static class RelicItem extends Item {

        private final RelicRules.Relic relic;

        public RelicItem(Properties properties, RelicRules.Relic relic) {
            super(properties);
            this.relic = relic;
        }

        /** Which relic this is. */
        public RelicRules.Relic relic() {
            return relic;
        }

        /**
         * Press the button and the thing happens.
         *
         * <p>For a while this class had no {@code use} at all: thirty relics,
         * every ability priced, the harness passing, and nothing bound to the
         * right mouse button. The library now says what each ability does and
         * where; all this has to know is which of a relic's abilities is the
         * one you press, and that a relic with none is a relic you wear.
         */
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                      InteractionHand hand) {
            ItemStack held = player.getItemInHand(hand);
            ElysiumAbilityRules.Ability active = activeOf(player.isShiftKeyDown());
            if (active == null) {
                return InteractionResultHolder.pass(held);
            }
            if (player.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.fail(held);
            }
            if (!ElysiumAbilityUse.perform(active, player)) {
                return InteractionResultHolder.fail(held);
            }
            // The cooldown is the ability's own, off the roster. A number
            // typed here would be a second cooldown for a player to discover
            // disagrees with the tooltip.
            player.getCooldowns().addCooldown(this, active.cooldown());
            return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
        }

        /**
         * The ability this relic fires on this press, or null.
         *
         * <p>A relic carries a short list and at most one of them is an
         * active on each of the two presses, which is what
         * {@code cannotShareAnItem} has always guaranteed and is the reason
         * this can be a lookup rather than a menu.
         */
        public ElysiumAbilityRules.Ability activeOf(boolean sneaking) {
            String wanted = sneaking ? "on_sneak_use" : "on_use";
            for (String name : relic.abilities()) {
                ElysiumAbilityRules.Ability ability =
                        ElysiumAbilityRules.ability(name);
                if (ability != null
                        && ElysiumAbilityRules.ACTIVE.equals(ability.kind())
                        && wanted.equals(ability.trigger())) {
                    return ability;
                }
            }
            return null;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable("elysiumarcana.relic.slot",
                            Component.translatable(
                                    "elysiumarcana.slot." + relic.slot()))
                    .withStyle(ChatFormatting.GRAY));
            for (String name : relic.abilities()) {
                ElysiumAbilityRules.Ability ability =
                        ElysiumAbilityRules.ability(name);
                if (ability == null) {
                    continue;
                }
                // Read off the roster, not written into a string. A retuned
                // cooldown changes what the tooltip says, which is the only
                // way a tooltip stays true.
                lines.add(Component.translatable("elysiumarcana.ability." + name)
                        .withStyle(ChatFormatting.AQUA));
                if (ElysiumAbilityRules.PASSIVE.equals(ability.kind())) {
                    lines.add(Component.translatable("elysiumarcana.relic.always")
                            .withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    lines.add(Component.translatable("elysiumarcana.relic.every",
                                    String.format("%.1f", ability.cooldown() / 20.0F))
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
    }
}
