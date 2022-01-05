package org.dynmap.fabric_1_16_4;

import net.minecraft.world.World;
import org.dynmap.fabric_helper.FabricVersionInterface;

public class FabricVersionAdapter implements FabricVersionInterface {

    @Override
    public float[] World_getBrightnessTable(World world) {
        float brightnessTable[] = new float[16];
        for (int i=0; i<16; i++) {
            brightnessTable[i] = world.getDimension().method_28516(i);
        }
        return brightnessTable;
    }

}
