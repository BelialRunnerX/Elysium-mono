package com.elysium.agronomy.item;

import com.elysium.agronomy.entity.BeeEntity;
import com.elysium.agronomy.event.Keeping;
import com.elysium.agronomy.registry.AgronomyComponents;
import com.elysium.agronomy.registry.BeeEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A jar: how a bee gets from the world into a hive.
 *
 * <p>Empty, used on one of our bees, it takes the bee out of the air and
 * holds its species in {@link AgronomyComponents#SPECIES}; the bees around
 * notice. Full, used on a hive, the hive takes the bee (see {@code
 * HiveBlock}); used on anything else, the bee is let go there. The jar is the
 * component item the hive accepts, which is the route {@code KeepingRules}
 * walks from a biome to every comb.
 */
public class BeeJarItem extends Item {

    public BeeJarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack jar, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!(target instanceof BeeEntity bee) || AgronomyComponents.speciesIn(jar) != null) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide) {
            jar.set(AgronomyComponents.SPECIES.get(), bee.species());
            player.setItemInHand(hand, jar);
            Keeping.robbed(player.level(), bee.blockPosition(), player);
            bee.discard();
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack jar = context.getItemInHand();
        String species = AgronomyComponents.speciesIn(jar);
        if (species == null) {
            return InteractionResult.PASS;
        }
        if (context.getLevel() instanceof ServerLevel server) {
            BlockPos at = context.getClickedPos().relative(context.getClickedFace());
            BeeEntity bee = BeeEntities.of(species).spawn(server, at, MobSpawnType.BUCKET);
            if (bee != null) {
                bee.setSpecies(species);
                jar.remove(AgronomyComponents.SPECIES.get());
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
