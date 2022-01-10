package org.dynmap.fabric_common.access;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.world.ChunkHolder;

public interface ThreadedAnvilChunkStorageAccessor {
    Long2ObjectLinkedOpenHashMap<ChunkHolder> getChunkHolders();
}
