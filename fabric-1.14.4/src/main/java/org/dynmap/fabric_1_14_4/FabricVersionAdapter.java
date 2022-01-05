package org.dynmap.fabric_1_14_4;

import net.minecraft.world.World;
import org.dynmap.fabric_helper.FabricVersionInterface;

public class FabricVersionAdapter implements FabricVersionInterface {

    @Override
    public float[] World_getBrightnessTable(World world) {
        return world.getDimension().getLightLevelToBrightness();
    }

}
