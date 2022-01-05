package org.dynmap.fabric_1_14_4;

import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.dynmap.fabric_helper.FabricVersionInterface;

import java.io.IOException;

public class FabricVersionAdapter implements FabricVersionInterface {

    @Override
    public float[] World_getBrightnessTable(World world) {
        return world.getDimension().getLightLevelToBrightness();
    }

    @Override
    public GenericNBTCompound ThreadedAnvilChunkStorage_getNbt(ThreadedAnvilChunkStorage tacs, ChunkPos chunkPos) throws IOException {
        return new NBT.NBTCompound(tacs.getTagAt(chunkPos));
    }

}
