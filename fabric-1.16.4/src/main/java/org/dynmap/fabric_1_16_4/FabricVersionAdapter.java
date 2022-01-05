package org.dynmap.fabric_1_16_4;

import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.dynmap.fabric_helper.FabricVersionInterface;

import java.io.IOException;

public class FabricVersionAdapter implements FabricVersionInterface {

    @Override
    public float[] World_getBrightnessTable(World world) {
        float brightnessTable[] = new float[16];
        for (int i=0; i<16; i++) {
            brightnessTable[i] = world.getDimension().method_28516(i);
        }
        return brightnessTable;
    }

    @Override
    public GenericNBTCompound ThreadedAnvilChunkStorage_getNbt(ThreadedAnvilChunkStorage tacs, ChunkPos chunkPos) throws IOException {
        return new NBT.NBTCompound(tacs.getNbt(chunkPos));
    }

}
