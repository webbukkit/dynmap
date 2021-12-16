package org.dynmap.bukkit.helper.v118;

import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.bukkit.helper.BukkitWorld;
import org.dynmap.bukkit.helper.AbstractMapChunkCache;
import org.dynmap.bukkit.helper.BukkitVersionHelper;
import org.dynmap.bukkit.helper.SnapshotCache;
import org.dynmap.bukkit.helper.SnapshotCache.SnapshotRec;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericChunkSection;
import org.dynmap.common.chunk.GenericMapChunkCache;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.DataBitsPacked;
import org.dynmap.utils.DynIntHashMap;
import org.dynmap.utils.VisibilityLimit;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DataBits;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkRegionLoader;
import net.minecraft.world.level.chunk.Chunk;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import org.dynmap.Log;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class MapChunkCache118 extends GenericMapChunkCache {
	private World w;
	/**
	 * Construct empty cache
	 */
	public MapChunkCache118(GenericChunkCache cc) {
		super(cc);
		init();
	}

	// Load generic chunk from existing and already loaded chunk
	protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
        CraftWorld cw = (CraftWorld) w;
        NBTTagCompound nbt = null;
        GenericChunk gc = null;
        if (cw.isChunkLoaded(chunk.x, chunk.z)) {
            Chunk c = cw.getHandle().getChunkIfLoaded(chunk.x,  chunk.z);
            if ((c != null) && c.o) {	// c.loaded
				nbt = ChunkRegionLoader.a(cw.getHandle(), c);
            }
            if (nbt != null) {
            	gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
            }
        }
    	return gc;
	}
	// Load generic chunk from unloaded chunk
	protected GenericChunk loadChunk(DynmapChunk chunk) {
        CraftWorld cw = (CraftWorld) w;
        NBTTagCompound nbt = null;
        ChunkCoordIntPair cc = new ChunkCoordIntPair(chunk.x, chunk.z);
        GenericChunk gc = null;
        try {
            nbt = cw.getHandle().k().a.f(cc);	// playerChunkMap
        } catch (IOException iox) {
        }
        if (nbt != null) {
        	gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
        }
    	return gc;
	}

	public void setChunks(BukkitWorld dw, List<DynmapChunk> chunks) {
		this.w = dw.getWorld();
		super.setChunks(dw, chunks);
	}
}
