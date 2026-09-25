package com.elysium.arsenal.forge;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.rules.ForgeRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Locale;

/**
 * Where a grade comes from, and what it does once it is there.
 *
 * <h2>The three moments</h2>
 *
 * A grade is put on a piece of gear when it is crafted and when it is first
 * picked up. Crafting alone would miss everything a player loots, traded or
 * was given, which in a pack this size is most of what they carry; picking up
 * alone would miss what they make and equip straight from the grid.
 *
 * <p>It is read back every time the game asks the item what it does to the
 * player's attributes, rather than being written into the item's own modifier
 * component. Writing it in would mean a grade retuned in an update leaves
 * every sword already in the world carrying the old numbers, with nothing to
 * say which is which.
 */
@EventBusSubscriber(modid = ElysiumArsenal.MODID)
public final class ForgeEvents {

    private ForgeEvents() {
    }

    /** The namespace a grade's modifier ids live in. */
    public static final String MODIFIER = "grade";

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        assignTo(event.getCrafting(), event.getEntity().getRandom().nextInt());
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        // Before the pickup rather than after: afterwards the stack has been
        // merged into one the player already had, and grading it then would
        // regrade the stack it merged with -- a free roll for anybody willing
        // to drop and collect the same sword.
        assignTo(event.getItemEntity().getItem(),
                event.getPlayer().getRandom().nextInt());
    }

    /**
     * Give a stack a grade, if it can have one and does not already.
     *
     * <p>The "does not already" is the whole of it. Without that guard a
     * player who drops a sword and picks it up again rolls it again, which is
     * a free hammer that costs no experience and wears nothing out — and the
     * arithmetic that makes reforging a decision would be describing a
     * mechanic the game does not have.
     */
    public static void assignTo(ItemStack stack, int roll) {
        if (!ForgeKinds.gradeable(stack)) {
            return;
        }
        if (ForgeComponents.gradeOn(stack) != null) {
            return;
        }
        String kind = ForgeKinds.of(stack);
        ForgeComponents.setGrade(stack, ForgeRules.roll(kind, roll).id());
    }

    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        String id = ForgeComponents.gradeOn(stack);
        if (id == null) {
            return;
        }
        ForgeRules.Grade grade = ForgeRules.grade(id);
        // A grade whose name no longer exists: a world saved under an older
        // version of the roster. Doing nothing is right -- the gear is simply
        // ungraded until it is reforged -- and doing anything else would mean
        // guessing which grade the player had.
        if (grade == null) {
            return;
        }
        for (ForgeRules.Change change : grade.changes()) {
            Holder<Attribute> attribute = attributeNamed(change.attribute());
            if (attribute == null) {
                continue;
            }
            event.addModifier(attribute,
                    new AttributeModifier(
                            ResourceLocation.fromNamespaceAndPath(ElysiumArsenal.MODID,
                                    MODIFIER + "/" + id + "/" + change.attribute()),
                            change.amount(),
                            change.share()
                                    ? AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                                    : AttributeModifier.Operation.ADD_VALUE),
                    ForgeKinds.where(stack));
        }
    }

    /**
     * The attribute of that name, or null.
     *
     * <p>Looked up in the game's own registry rather than switched over a list
     * here, so an attribute another mod adds works without this file knowing
     * about it — and so that a misspelling in the roster is a grade that does
     * nothing rather than a crash on the first sword a player picks up.
     */
    public static Holder<Attribute> attributeNamed(String name) {
        ResourceLocation id = name.indexOf(':') >= 0
                ? ResourceLocation.tryParse(name)
                : ResourceLocation.withDefaultNamespace(name);
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        String id = ForgeComponents.gradeOn(event.getItemStack());
        if (id == null) {
            return;
        }
        ForgeRules.Grade grade = ForgeRules.grade(id);
        if (grade == null) {
            return;
        }
        event.getToolTip().add(1, Component.translatable(
                        ElysiumArsenal.MODID + ".grade." + id)
                .withStyle(colourOf(grade)));
    }

    /**
     * What colour a grade reads in.
     *
     * <p>From what the grade is worth rather than from a colour written beside
     * it: a grade retuned from a loss into a win would otherwise keep saying
     * grey while making the gear better.
     */
    public static ChatFormatting colourOf(ForgeRules.Grade grade) {
        float worth = grade.worth();
        if (worth < 0.0F) {
            return ChatFormatting.RED;
        }
        if (worth == 0.0F) {
            return ChatFormatting.GRAY;
        }
        ForgeRules.Grade best = ForgeRules.bestOf(grade.kind());
        if (best != null && worth >= best.worth()) {
            return ChatFormatting.GOLD;
        }
        if (best != null && worth >= best.worth() * 0.6F) {
            return ChatFormatting.LIGHT_PURPLE;
        }
        return ChatFormatting.GREEN;
    }

    /** The translation key a grade's name lives under. */
    public static String keyFor(ForgeRules.Grade grade) {
        return ElysiumArsenal.MODID + ".grade."
                + grade.id().toLowerCase(Locale.ROOT);
    }
}
