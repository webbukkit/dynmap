package org.dynmap.fabric_1_18.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.Chunk;

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
        void onChunkGenerate(ServerWorld world, Chunk chunk);
    }
}