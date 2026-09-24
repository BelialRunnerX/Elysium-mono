package com.elysium.space.event;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.oxygen.OxygenField;
import com.elysium.space.registry.SpaceBlocks;
import com.elysium.space.world.SpaceWorld;
import com.elysium.space.world.SpaceWorlds;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Game-side rules that are not hazards: gravity, the paradise world's
 * spawning, stripping emgrove logs, and forgetting the air when the server
 * stops.
 */
@EventBusSubscriber(modid = ElysiumSpace.MODID)
public final class SpaceEvents {

    private SpaceEvents() {
    }

    static final ResourceLocation GRAVITY_ID =
            ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, "world_gravity");

    /**
     * Gravity (Stellaris's gravity mixins): a world's gravity is a transient
     * modifier on the gravity and fall-damage attributes, added when a living
     * thing enters the world and removed when it enters any other. Players
     * changing dimension join the new level, so this one event covers travel.
     * Attributes, not a mixin, so anything else that scales gravity still
     * stacks with it.
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living) || event.getLevel().isClientSide()) {
            return;
        }
        SpaceWorld world = SpaceWorlds.of(event.getLevel());
        double modifier = world == null ? 0.0D : world.gravityModifier();
        apply(living, Attributes.GRAVITY, modifier);
        apply(living, Attributes.FALL_DAMAGE_MULTIPLIER, modifier);
    }

    private static void apply(LivingEntity living, Holder<Attribute> attribute, double amount) {
        AttributeInstance instance = living.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(GRAVITY_ID);
        if (amount != 0.0D) {
            instance.addTransientModifier(new AttributeModifier(GRAVITY_ID, amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /**
     * A world with hostile spawns off (the Emerald Bastion): no monster spawns
     * naturally, whatever the light. Its biomes list no monsters already; this
     * also stops what spawns through other routes (patrols, chunk generation).
     * Spawners, eggs and summons still work.
     */
    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent event) {
        SpaceWorld world = SpaceWorlds.of(event.getLevel().getLevel());
        if (world == null || world.hostileSpawns()) {
            return;
        }
        MobSpawnType type = event.getSpawnType();
        boolean natural = type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION
                || type == MobSpawnType.PATROL || type == MobSpawnType.EVENT;
        if (natural && event.getEntity().getType().getCategory() == MobCategory.MONSTER) {
            event.setSpawnCancelled(true);
            event.setCanceled(true);
        }
    }

    /** Axe on an emgrove log or wood strips it, keeping its axis. */
    @SubscribeEvent
    public static void onStrip(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != ItemAbilities.AXE_STRIP) {
            return;
        }
        BlockState state = event.getState();
        Block stripped = SpaceBlocks.strippable().get(state.getBlock());
        if (stripped != null) {
            event.setFinalState(stripped.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS)));
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        OxygenField.reset();
    }
}
