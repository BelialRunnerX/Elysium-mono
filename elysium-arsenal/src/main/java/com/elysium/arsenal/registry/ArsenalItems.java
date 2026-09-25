package com.elysium.arsenal.registry;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.item.BroadToolItem;
import com.elysium.arsenal.item.ScytheItem;
import com.elysium.arsenal.item.WeaponItem;
import com.elysium.arsenal.rules.ArsenalRules;
import com.elysium.arsenal.rules.RangedRules;
import com.elysium.arsenal.rules.UniqueRules;
import com.elysium.arsenal.rules.WeaponRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Twenty-four tools and thirty-six weapons, registered from the
 * catalogues rather than typed.
 *
 * <h2>Where the numbers come from</h2>
 *
 * The attack damage and the swing speed come from the vanilla tool each one
 * replaces, multiplied by the share in {@link ArsenalRules} — so a hammer is
 * exactly as strong as the pickaxe of its tier and exactly as much slower as
 * the rules say. Nothing here chooses a number; everything here reads one.
 */
public final class ArsenalItems {

    private ArsenalItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumArsenal.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElysiumArsenal.MODID);

    /**
     * The tier behind each of our tier names, in the rules' order: the game's
     * own for the game's six, and one built from Valoria's numbers for each
     * of the Cordon's, repaired with the tag its weapons are made of.
     *
     * <p>Public because the uniques are built on the same tiers and a second
     * copy of this map is a second copy that can drift.
     */
    public static final Map<String, Tier> TIERS = tiers();

    private static final Map<String, Tier> GAME = Map.of(
            "wooden", Tiers.WOOD, "stone", Tiers.STONE, "iron", Tiers.IRON,
            "golden", Tiers.GOLD, "diamond", Tiers.DIAMOND,
            "netherite", Tiers.NETHERITE);

    private static Map<String, Tier> tiers() {
        Map<String, Tier> made = new LinkedHashMap<>();
        for (WeaponRules.Rung rung : WeaponRules.RUNGS) {
            if (!rung.cordon()) {
                Tier game = GAME.get(rung.name());
                if (game == null) {
                    throw new IllegalStateException(rung.name() + " is one of the game's rungs "
                            + "and the game has no tier of that name");
                }
                made.put(rung.name(), game);
                continue;
            }
            TagKey<Item> repair = TagKey.create(Registries.ITEM, ResourceLocation.parse(rung.material()));
            made.put(rung.name(), new SimpleTier(incorrectFor(rung.level()), rung.uses(),
                    rung.speed(), rung.damage(), rung.enchant(), () -> Ingredient.of(repair)));
        }
        if (made.size() != WeaponRules.RUNGS.size()) {
            throw new IllegalStateException("the rules list " + WeaponRules.RUNGS.size()
                    + " rungs and " + made.size() + " tiers were built");
        }
        return Map.copyOf(made);
    }

    /** What a tool of that harvest level cannot mine, on the game's own scale. */
    private static TagKey<Block> incorrectFor(int level) {
        return switch (Math.min(4, Math.max(0, level))) {
            case 0, 1 -> BlockTags.INCORRECT_FOR_STONE_TOOL;
            case 2 -> BlockTags.INCORRECT_FOR_IRON_TOOL;
            case 3 -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
            default -> BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        };
    }

    /**
     * How hard a pickaxe of each tier hits, which is what a hammer copies.
     *
     * <p>Vanilla's own numbers, written out because they are not exposed: a
     * tool that invented its own damage would be a tool the rest of the game's
     * balance had never heard of.
     */
    private static final Map<String, Float> PICKAXE_DAMAGE = Map.of(
            "wooden", 1.0F, "stone", 1.0F, "iron", 1.0F,
            "golden", 1.0F, "diamond", 1.0F, "netherite", 1.0F);

    /** And how fast it swings. A hammer swings slower by the rules' share. */
    private static final float PICKAXE_SPEED = -2.8F;

    public static final Map<String, DeferredItem<Item>> ALL = register();

    /**
     * Thirty-six weapons, registered the same way and for the same reason.
     *
     * <p>Every number on every one of them is read out of
     * {@link WeaponRules}, because the property this module has to hold is
     * about all thirty-six together — that no two are the same weapon and none
     * is beaten on every axis by another — and a number typed into an item is
     * a number the arithmetic has never seen.
     */
    public static final Map<String, DeferredItem<Item>> WEAPONS = weapons();

    private static Map<String, DeferredItem<Item>> weapons() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (String tierName : WeaponRules.TIERS) {
            Tier tier = TIERS.get(tierName);
            if (tier == null) {
                throw new IllegalStateException(tierName + " has no vanilla tier "
                        + "behind it, so a weapon at it would have no durability");
            }
            for (WeaponRules.Weapon kind : WeaponRules.KINDS) {
                String id = tierName + "_" + kind.name();
                made.put(id, ITEMS.register(id, () -> new WeaponItem(tier, tierName,
                        kind, new Item.Properties())));
            }
        }
        if (made.size() != WeaponRules.catalogue().size()) {
            throw new IllegalStateException("the rules list "
                    + WeaponRules.catalogue().size() + " weapons and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }

    private static Item.Properties armed(Tier tier, float damage, float speed) {
        return new Item.Properties()
                .attributes(DiggerItem.createAttributes(tier, damage, speed));
    }

    /**
     * How slowly this shape swings, given how slowly a pickaxe does.
     *
     * <p>The game's speed attribute is "attacks a second minus four", so a
     * bigger negative is slower. Sharing it by the rules' fraction is a
     * division rather than a multiplication, which is the sort of thing that
     * is wrong by a reciprocal for a year.
     */
    private static float slower(float pickaxe, float share) {
        float attacks = 4.0F + pickaxe;
        return attacks * share - 4.0F;
    }

    private static Map<String, DeferredItem<Item>> register() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (String name : ArsenalRules.TIERS) {
            Tier tier = TIERS.get(name);
            float damage = PICKAXE_DAMAGE.getOrDefault(name, 1.0F);
            for (ArsenalRules.Broad broad : ArsenalRules.BROADS) {
                String id = name + "_" + broad.name();
                float speed = slower(PICKAXE_SPEED, broad.share());
                made.put(id, ITEMS.register(id, () -> switch (broad.name()) {
                    case "scythe" -> new ScytheItem(tier, armed(tier, damage, speed));
                    case "excavator" -> new BroadToolItem(tier, BlockTags.MINEABLE_WITH_SHOVEL,
                            armed(tier, damage, speed), broad.name(), broad.wide());
                    case "paxel" -> new BroadToolItem(tier, BlockTags.MINEABLE_WITH_PICKAXE,
                            armed(tier, damage, speed), broad.name(), broad.wide());
                    default -> new BroadToolItem(tier, BlockTags.MINEABLE_WITH_PICKAXE,
                            armed(tier, damage, speed), broad.name(), broad.wide());
                }));
            }
        }
        return Map.copyOf(made);
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "arsenal", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumarsenal"))
                    .icon(() -> new ItemStack(ALL.get("iron_hammer").get()))
                    .displayItems((parameters, output) -> {
                        for (String id : ArsenalRules.catalogue()) {
                            output.accept(ALL.get(id).get());
                        }
                        for (String id : WeaponRules.catalogue()) {
                            output.accept(WEAPONS.get(id).get());
                        }
                        // Walked rather than listed, here as everywhere. A tab
                        // that shows thirty-five of thirty-six bows is a bug
                        // nobody reports, because nobody knows the last one is
                        // missing.
                        for (String id : RangedRules.catalogue()) {
                            output.accept(RangedItems.BOWS.get(id).get());
                        }
                        for (RangedRules.Shot shot : RangedRules.SHOTS) {
                            output.accept(RangedItems.SHOTS.get(shot.name()).get());
                        }
                        for (String hammer : new java.util.TreeMap<>(
                                com.elysium.arsenal.rules.ForgeRules.HAMMERS)
                                .keySet()) {
                            output.accept(ForgeItems.HAMMERS.get(hammer).get());
                        }
                        output.accept(UniqueItems.SIGIL.get());
                        for (UniqueRules.Unique unique : UniqueRules.UNIQUES) {
                            output.accept(UniqueItems.ALL.get(unique.id()).get());
                        }
                        for (DeferredItem<Item> dug : DigItems.all().values()) {
                            output.accept(dug.get());
                        }
                    })
                    .build());

    /** Every id, in the order the catalogue lists them. */
    public static List<String> catalogue() {
        return ArsenalRules.catalogue();
    }
}
