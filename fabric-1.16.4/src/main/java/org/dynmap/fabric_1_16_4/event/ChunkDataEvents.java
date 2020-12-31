package org.dynmap.fabric_1_16_4.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.Chunk;

public class ChunkDataEvents {
    private ChunkDataEvents() {
    }

    public static Event<Save> SAVE = EventFactory.createArrayBacked(Save.class,
            (listeners) -> (world, chunk) -> {
                for (Save callback : listeners) {
                    callback.onSave(world, chunk);
                }
            }
    );

    @FunctionalInterface
    public interface Save {
        void onSave(ServerWorld world, Chunk chunk);
    }
}
