package org.dynmap.fabric_1_16_4;

import net.minecraft.server.world.ServerWorld;
import org.dynmap.DynmapLocation;

public final class FabricAdapter {
    public static DynmapLocation toDynmapLocation(DynmapPlugin plugin, ServerWorld world, double x, double y, double z) {
        return new DynmapLocation(plugin.getWorld(world).getName(), x, y, z);
    }

    private FabricAdapter() {
    }
}
