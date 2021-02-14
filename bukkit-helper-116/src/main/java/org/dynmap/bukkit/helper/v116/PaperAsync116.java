package org.dynmap.bukkit.helper.v116;

import org.bukkit.Bukkit;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.libs.jline.internal.Log;
import org.bukkit.craftbukkit.v1_16_R1.CraftWorld;

import java.io.IOException;
import java.util.Arrays;

import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.bukkit.helper.AbstractMapChunkCache;
import org.dynmap.bukkit.helper.BukkitVersionHelper;
import org.dynmap.bukkit.helper.SnapshotCache;
import org.dynmap.bukkit.helper.SnapshotCache.SnapshotRec;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.DynIntHashMap;
import org.dynmap.utils.VisibilityLimit;

import net.minecraft.server.v1_16_R1.Chunk;
import net.minecraft.server.v1_16_R1.ChunkCoordIntPair;
import net.minecraft.server.v1_16_R1.ChunkRegionLoader;
import net.minecraft.server.v1_16_R1.ChunkStatus;
import net.minecraft.server.v1_16_R1.DataBits;
import net.minecraft.server.v1_16_R1.DataBitsPacked;
import net.minecraft.server.v1_16_R1.NBTTagCompound;
import net.minecraft.server.v1_16_R1.NBTTagList;


public class PaperAsync116 {
    private NBTTagCompound fetchLoadedChunkNBT(World w, int x, int z) {
        CraftWorld cw = (CraftWorld) w;
        NBTTagCompound nbt = null;
        if (cw.isChunkLoaded(x, z)) {
            Chunk c = cw.getHandle().getChunkAtAsync(x,  z);
            if ((c != null) && c.loaded) {
                nbt = ChunkRegionLoader.saveChunk(cw.getHandle(), c);
            }
        }
        if (nbt != null) {
            nbt = nbt.getCompound("Level");
            if (nbt != null) {
                String stat = nbt.getString("Status");
				ChunkStatus cs = ChunkStatus.a(stat);
                if ((stat == null) || (!cs.b(ChunkStatus.LIGHT))) {
                    nbt = null;
                }
            }
        }
        return nbt;
    }
    
    private NBTTagCompound loadChunkNBT(World w, int x, int z) {
        CraftWorld cw = (CraftWorld) w;
        NBTTagCompound nbt = null;
        ChunkCoordIntPair cc = new ChunkCoordIntPair(x, z);
        try {
            nbt = cw.getHandle().getChunkProvider().playerChunkMap.read(cc);
        } catch (IOException iox) {
        }
        if (nbt != null) {
            nbt = nbt.getCompound("Level");
            if (nbt != null) {
            	String stat = nbt.getString("Status");
            	if ((stat == null) || (stat.equals("full") == false)) {
                    nbt = null;
                    if ((stat == null) || stat.equals("") && DynmapCore.migrateChunks()) {
                        Chunk c = cw.getHandle().getChunkAtAsync(x, z);
                        if (c != null) {
                            nbt = fetchLoadedChunkNBT(w, x, z);
                            cw.getHandle().unloadChunk(c);
                        }
                    }
            	}
            }
        }
        return nbt;
    }   

}