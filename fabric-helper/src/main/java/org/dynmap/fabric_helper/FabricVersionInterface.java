package org.dynmap.fabric_helper;

import net.minecraft.world.World;

/**
 * Abstraction interface for version-specific Minecraft logic.
 */
public interface FabricVersionInterface {

    float[] World_getBrightnessTable(World world);

}
