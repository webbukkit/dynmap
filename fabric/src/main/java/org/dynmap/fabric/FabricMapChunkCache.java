package org.dynmap.fabric;

import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkManager;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericMapChunkCache;

import java.util.List;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class FabricMapChunkCache extends GenericMapChunkCache {
    private World world;
    private ServerChunkManager serverChunkManager;

    /**
     * Construct empty cache
     */
    public FabricMapChunkCache(DynmapPlugin plugin) {
        super(plugin.sscache);
    }

    public void setChunks(FabricWorld dw, List<DynmapChunk> chunks) {
        this.world = dw.getWorld();
        if (dw.isLoaded()) {
            /* Check if world's provider is ServerChunkManager */
            ChunkManager cp = this.world.getChunkManager();

            if (cp instanceof ServerChunkManager) {
                serverChunkManager = (ServerChunkManager) cp;
            } else {
                Log.severe(String.format("Error: world %s has unsupported chunk provider", dw.getName()));
            }
        }
        super.setChunks(dw, chunks);
    }

    protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
        if (!serverChunkManager.isChunkLoaded(chunk.x, chunk.z))
            return null;

        try {
            return parseChunkFromNBT(FabricAdapter.VERSION_SPECIFIC.NbtCompound_getGenericNbt(ChunkSerializer.serialize((ServerWorld) world, serverChunkManager.getWorldChunk(chunk.x, chunk.z, false))));
        } catch (NullPointerException e) {
            // TODO: find out why this is happening and why it only seems to happen since 1.16.2
            Log.severe("ChunkSerializer.serialize threw a NullPointerException", e);
            return null;
        }
    }

    protected GenericChunk loadChunk(DynmapChunk chunk) {
        try {
            ThreadedAnvilChunkStorage tacs = serverChunkManager.threadedAnvilChunkStorage;
            ChunkPos chunkPos = new ChunkPos(chunk.x, chunk.z);
            return parseChunkFromNBT(FabricAdapter.VERSION_SPECIFIC.ThreadedAnvilChunkStorage_getGenericNbt(tacs, chunkPos));
        } catch (Exception exc) {
            Log.severe(String.format("Error reading chunk: %s,%d,%d", dw.getName(), chunk.x, chunk.z), exc);
            return null;
        }
    }
}
