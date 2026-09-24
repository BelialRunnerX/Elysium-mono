package com.elysium.space.hazard;

import com.elysium.lib.hazard.ElysiumHazard;
import com.elysium.lib.hazard.ElysiumHazardField;
import com.elysium.space.oxygen.OxygenField;
import com.elysium.space.world.SpaceWorld;
import com.elysium.space.world.SpaceWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Each world's hazards, handed to the library's hazard pipeline as one
 * {@link ElysiumHazardField.Source}. From there on a planet is no different
 * from a desert or a mineshaft: the same wards, armour sets, claims, rooms and
 * resistance answer it.
 *
 * <p>The one space rule: <b>vacuum is zero wherever an Oxygen Distributor has
 * filled the room</b>, and <b>on a toxic world</b> the same room also clears
 * corrosion, because what a distributor pushes out is the bad air. Heat, cold,
 * radiation and pressure come through walls, so a distributor does not
 * answer them. A room still gives its usual share against them.
 */
public final class SpaceAtmosphere implements ElysiumHazardField.Source {

    @Override
    public float intensity(Level level, BlockPos pos, ElysiumHazard hazard) {
        SpaceWorld world = SpaceWorlds.of(level);
        if (world == null || hazard == null || hazard.getId() == null) {
            return 0.0F;
        }
        String id = hazard.getId().toString();
        float raw = world.intensity(id);
        if (raw <= 0.0F) {
            return 0.0F;
        }
        if (world.clearedByAir(id) && OxygenField.isOxygenated(level, pos)) {
            return 0.0F;
        }
        return raw;
    }
}
