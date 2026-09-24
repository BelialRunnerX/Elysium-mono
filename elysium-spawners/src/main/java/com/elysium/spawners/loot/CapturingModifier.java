package com.elysium.spawners.loot;

import com.elysium.spawners.rules.SpawnerRules;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Capturing: the enchantment that turns a kill into a spawn egg.
 *
 * <h2>Why a loot modifier and not an effect component</h2>
 *
 * Everything else this repository's enchantments do is said in the game's own
 * enchantment vocabulary, which needs no Java at all. This cannot be: there is
 * no effect component for "add an item to what this creature drops", because
 * an enchantment's effects act on entities and values rather than on loot. So
 * the enchantment itself is still a data file — it exists, it is offered, it
 * can be tuned — and this is the one piece of Java that reads its level.
 *
 * <h2>Never certain</h2>
 *
 * Through {@code x/(x+b)} like every percentile thing in this mod, and with a
 * ceiling under one besides. A guaranteed spawn egg would make the first
 * Capturing sword the end of the mob-farm question forever.
 */
public class CapturingModifier extends LootModifier {

    public static final MapCodec<CapturingModifier> CODEC =
            RecordCodecBuilder.mapCodec(instance ->
                    codecStart(instance).apply(instance, CapturingModifier::new));

    /** The enchantment this reads. A data file, like every other one here. */
    public static final ResourceKey<Enchantment> CAPTURING = ResourceKey.create(
            Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath("elysiumspawners", "capturing"));

    public CapturingModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot,
                                                 LootContext context) {
        Entity killed = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        Entity killer = context.getParamOrNull(LootContextParams.ATTACKING_ENTITY);
        if (!(killed instanceof LivingEntity victim) || !(killer instanceof LivingEntity hunter)) {
            return loot;
        }
        int level = levelOn(hunter, context);
        if (level <= 0) {
            return loot;
        }
        Item egg = net.minecraft.world.item.SpawnEggItem.byId(victim.getType());
        if (egg == null) {
            // Plenty of creatures have no egg — the Wither, the dragon, a mod's
            // boss. Not an error: they are simply not capturable.
            return loot;
        }
        if (context.getRandom().nextFloat() >= SpawnerRules.captureChance(level)) {
            return loot;
        }
        loot.add(new ItemStack(egg));
        return loot;
    }

    private static int levelOn(LivingEntity hunter, LootContext context) {
        Optional<Holder.Reference<Enchantment>> enchantment =
                context.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .get(CAPTURING);
        if (enchantment.isEmpty()) {
            return 0;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment.get(),
                hunter.getMainHandItem());
    }

    @Override
    public MapCodec<? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier> codec() {
        return CODEC;
    }
}
