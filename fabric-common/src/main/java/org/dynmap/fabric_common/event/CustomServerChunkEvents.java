package org.dynmap.fabric_common.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;

public class CustomServerChunkEvents {
    public static Event<ChunkGenerate> CHUNK_GENERATE = EventFactory.createArrayBacked(ChunkGenerate.class,
            (listeners) -> (world, chunk) -> {
                for (ChunkGenerate callback : listeners) {
                    callback.onChunkGenerate(world, chunk);
                }
            }
    );

    @FunctionalInterface
    public interface ChunkGenerate {
        void onChunkGenerate(ServerWorld world, WorldChunk chunk);
    }
}