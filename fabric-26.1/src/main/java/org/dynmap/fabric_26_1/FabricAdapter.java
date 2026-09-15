package org.dynmap.fabric_26_1;

import net.minecraft.server.level.ServerLevel;
import org.dynmap.DynmapLocation;

public final class FabricAdapter {
    public static DynmapLocation toDynmapLocation(DynmapPlugin plugin, ServerLevel world, double x, double y, double z) {
        return new DynmapLocation(plugin.getWorld(world).getName(), x, y, z);
    }

    private FabricAdapter() {
    }
}
