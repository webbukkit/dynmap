package org.dynmap.fabric_1_15_2;

import org.dynmap.DynmapLocation;

import net.minecraft.server.world.ServerWorld;

public final class FabricAdapter {
    public static DynmapLocation toDynmapLocation(DynmapPlugin plugin, ServerWorld world, double x, double y, double z) {
        return new DynmapLocation(plugin.getWorld(world).getName(), x, y, z);
    }

    private FabricAdapter() {
    }
}
