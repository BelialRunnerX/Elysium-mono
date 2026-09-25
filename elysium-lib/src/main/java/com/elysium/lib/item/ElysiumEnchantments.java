package com.elysium.lib.item;

import com.elysium.lib.ElysiumLib;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * The enchantments the library ships as data, and the one question a content
 * module asks about them.
 *
 * <p>Valoria's three. {@code bleeding} is complete in data: its post-attack
 * effect applies the library's own Bleeding. {@code accuracy} and
 * {@code explosive_charge} are read by the items that carry the abilities they
 * modify — the overcharged bow's spread and the charged pick's blast — and
 * until those items exist they are enchantments that can be found and applied
 * and change nothing, which is what they were on a sword in Valoria too. The
 * tags they apply to, {@code elysiumlib:enchantable/accuracy} and
 * {@code elysiumlib:enchantable/charge}, are the library's, so a module adds
 * its item to a tag rather than shipping its own copy of the enchantment.
 */
public final class ElysiumEnchantments {

    private ElysiumEnchantments() {
    }

    public static final ResourceKey<Enchantment> BLEEDING = key("bleeding");
    public static final ResourceKey<Enchantment> ACCURACY = key("accuracy");
    public static final ResourceKey<Enchantment> EXPLOSIVE_CHARGE = key("explosive_charge");

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, name));
    }

    /** The level of one of these on a stack, or zero. Needs a level because enchantments are data. */
    public static int level(Level level, ItemStack stack, ResourceKey<Enchantment> enchantment) {
        if (level == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        Optional<Holder.Reference<Enchantment>> holder = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).getHolder(enchantment);
        return holder.map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
    }
}
