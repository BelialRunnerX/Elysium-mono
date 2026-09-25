package com.elysium.arsenal.event;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.block.FossilBlock;
import com.elysium.arsenal.rules.FossilRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What standing near a fossil does.
 *
 * <h2>Why the player looks for the fossils and not the other way round</h2>
 *
 * Twenty-one fossil blocks each ticking and searching the room for players is
 * a server that slows down in whichever room somebody decorated, and the cost
 * grows with how much the player built rather than with how many players there
 * are. Looking outward from the player instead costs the same whatever is
 * built, and it is the player the effect is for.
 *
 * <h2>Why it is not every tick</h2>
 *
 * Because a cube eight blocks on a side is four and a half thousand blocks to
 * look at, and doing that twenty times a second for every player is the same
 * mistake in a different place. Once a second is faster than anybody can walk
 * out of a room.
 */
@EventBusSubscriber(modid = ElysiumArsenal.MODID)
public final class FossilEffects {

    private FossilEffects() {
    }

    /** How often a player looks around for fossils, in ticks. */
    public static final int EVERY = 20;

    /** How long an effect lasts once given, so it does not flicker. */
    public static final int LASTS = EVERY * 3;

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % EVERY != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            give(player, (ServerLevel) player.level());
        }
    }

    /**
     * What is near this player, and what that is worth.
     *
     * <p>The two halves are counted separately and the pair is worth more than
     * the two apart, which is the whole reason a fossil is two blocks.
     */
    public static Map<String, Float> around(ServerLevel level, BlockPos middle) {
        Map<String, Boolean> skulls = new LinkedHashMap<>();
        Map<String, Boolean> bodies = new LinkedHashMap<>();
        int reach = FossilRules.FURTHEST;
        for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-reach, -reach, -reach),
                middle.offset(reach, reach, reach))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof FossilBlock block)) {
                continue;
            }
            // Each fossil has its own reach, and a fossil further off than its
            // own reach does nothing -- the loop above uses the largest reach
            // any fossil has so that one pass finds them all.
            if (middle.distSqr(pos) > (double) block.fossil().reach()
                    * block.fossil().reach()) {
                continue;
            }
            String creature = block.fossil().creature();
            if (block.isSkull()) {
                skulls.put(creature, Boolean.TRUE);
            }
            if (block.isBody()) {
                bodies.put(creature, Boolean.TRUE);
            }
        }
        Map<String, Float> given = new LinkedHashMap<>();
        for (FossilRules.Fossil one : FossilRules.FOSSILS) {
            float worth = FossilRules.given(one,
                    skulls.containsKey(one.creature()),
                    bodies.containsKey(one.creature()));
            if (worth > 0.0F) {
                given.put(one.effect(), worth);
            }
        }
        return given;
    }

    private static void give(ServerPlayer player, ServerLevel level) {
        Map<String, Float> near = around(level, player.blockPosition());
        if (near.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Float> row : near.entrySet()) {
            switch (row.getKey()) {
                case "breathe_under_water" -> player.addEffect(new MobEffectInstance(
                        MobEffects.WATER_BREATHING, LASTS, 0, true, false));
                case "lighter_landings" -> player.addEffect(new MobEffectInstance(
                        MobEffects.JUMP, LASTS, 0, true, false));
                case "blasts_hurt_less" -> player.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_RESISTANCE, LASTS, 0, true, false));
                case "traders_restock" -> player.addEffect(new MobEffectInstance(
                        MobEffects.HERO_OF_THE_VILLAGE, LASTS, 0, true, false));
                case "wool_grows_back" -> player.addEffect(new MobEffectInstance(
                        MobEffects.SATURATION, LASTS, 0, true, false));
                case "creepers_keep_away", "skeletons_keep_away" ->
                        frighten(level, player, row.getKey(), row.getValue());
                default -> { }
            }
        }
    }

    /**
     * Push one sort of thing away from the player.
     *
     * <p>One sort, named, rather than "monsters": an ocelot fossil that
     * frightened everything would be a wolf fossil as well, and the two would
     * be one block under two names — which is the thing the roster refuses.
     */
    private static void frighten(ServerLevel level, ServerPlayer player,
                                 String effect, float strength) {
        int reach = FossilRules.FURTHEST;
        for (Mob mob : level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(reach))) {
            boolean matches = "creepers_keep_away".equals(effect)
                    ? mob instanceof Creeper
                    : mob instanceof Skeleton;
            if (!matches) {
                continue;
            }
            double dx = mob.getX() - player.getX();
            double dz = mob.getZ() - player.getZ();
            double away = Math.sqrt(dx * dx + dz * dz);
            if (away < 0.1D) {
                continue;
            }
            mob.setDeltaMovement(mob.getDeltaMovement()
                    .add(dx / away * 0.08D * strength, 0.0D,
                            dz / away * 0.08D * strength));
            mob.setTarget(null);
        }
    }

    /**
     * The one fossil effect that is not about being near, but about being hit.
     *
     * <p>Kept here with the others rather than beside the damage code, because
     * what a fossil does is a fact about the fossil and somebody retuning the
     * roster should find all seven in one place.
     */
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            return;
        }
        Float lighter = around((ServerLevel) player.level(), player.blockPosition())
                .get("lighter_landings");
        if (lighter == null) {
            return;
        }
        event.setAmount(event.getAmount() * Math.max(0.0F, 1.0F - lighter));
    }
}
