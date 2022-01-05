package org.dynmap.fabric_1_18;

import net.minecraft.server.world.ServerWorld;
import org.dynmap.DynmapLocation;
import org.dynmap.fabric_helper.FabricVersionInterface;

public final class FabricAdapter {
    public static FabricVersionInterface VERSION_SPECIFIC = new FabricVersionAdapter();

    public static DynmapLocation toDynmapLocation(DynmapPlugin plugin, ServerWorld world, double x, double y, double z) {
        return new DynmapLocation(plugin.getWorld(world).getName(), x, y, z);
    }

    private FabricAdapter() {
    }
}
