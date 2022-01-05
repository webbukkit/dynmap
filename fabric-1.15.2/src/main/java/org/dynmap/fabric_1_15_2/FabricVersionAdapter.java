package org.dynmap.fabric_1_15_2;

import net.minecraft.world.World;
import org.dynmap.fabric_helper.FabricVersionInterface;

public class FabricVersionAdapter implements FabricVersionInterface {

    @Override
    public float[] World_getBrightnessTable(World world) {
        float brightnessTable[] = new float[16];
        for (int i=0; i<16; i++) {
            brightnessTable[i] = world.getDimension().getBrightness(i);
        }
        return brightnessTable;
    }

}
