package org.dynmap;

import java.lang.reflect.Method;
import java.util.LinkedList;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;

/**
 * Container for managing chunks, as well as abstracting the different methods we may
 * handle chunk data (existing chunk loading, versus upcoming chunk snapshots)
 * 
 */
public class MapChunkCache {
    private World w;
    private static Method getchunkdata = null;
    private static Method gethandle = null;
    private static boolean initialized = false;
    
    private int x_min, x_max, z_min, z_max;
    private int x_dim;
    
    private CraftChunkSnapshot[] snaparray; /* Index = (x-x_min) + ((z-z_min)*x_dim) */
    private LinkedList<DynmapChunk> loadedChunks = new LinkedList<DynmapChunk>();

    /**
     * Create chunk cache container
     * @param w - world
     * @param x_min - minimum chunk x coordinate
     * @param z_min - minimum chunk z coordinate
     * @param x_max - maximum chunk x coordinate
     * @param z_max - maximum chunk z coordinate
     */
    @SuppressWarnings({ "unchecked" })
    public MapChunkCache(World w, DynmapChunk[] chunks) {
        /* Compute range */
        if(chunks.length == 0) {
            this.x_min = 0;
            this.x_max = 0;
            this.z_min = 0;
            this.z_max = 0;
            x_dim = 1;            
        }
        else {
            x_min = x_max = chunks[0].x;
            z_min = z_max = chunks[0].z;
            for(int i = 1; i < chunks.length; i++) {
                if(chunks[i].x > x_max)
                    x_max = chunks[i].x;
                if(chunks[i].x < x_min)
                    x_min = chunks[i].x;
                if(chunks[i].z > z_max)
                    z_max = chunks[i].z;
                if(chunks[i].z < z_min)
                    z_min = chunks[i].z;
            }
            x_dim = x_max - x_min + 1;            
        }
        this.w = w;
    
        if(!initialized) {
            try {
                Class c = Class.forName("net.minecraft.server.Chunk");
                getchunkdata = c.getDeclaredMethod("a", new Class[] { byte[].class, int.class, 
                    int.class, int.class, int.class, int.class, int.class, int.class });
                c = Class.forName("org.bukkit.craftbukkit.CraftChunk");
                gethandle = c.getDeclaredMethod("getHandle", new Class[0]);
            } catch (ClassNotFoundException cnfx) {
            } catch (NoSuchMethodException nsmx) {
            }
            initialized = true;
            if(gethandle != null)
                Log.info("Chunk snapshot support enabled");
            else
                Log.info("Chunk snapshot support disabled");
        }
        if(gethandle != null) {  /* We can use caching */
            snaparray = new CraftChunkSnapshot[x_dim * (z_max-z_min+1)];
        }
        if(snaparray != null) {
            // Load the required chunks.
            for (DynmapChunk chunk : chunks) {
                boolean wasLoaded = w.isChunkLoaded(chunk.x, chunk.z);
                boolean didload = w.loadChunk(chunk.x, chunk.z, false);
                /* If it did load, make cache of it */
                if(didload) {
                    Chunk c = w.getChunkAt(chunk.x, chunk.z);
                    try {
                        Object cc = gethandle.invoke(c);
                        byte[] buf = new byte[32768 + 16384 + 16384 + 16384];   /* Get big enough buffer for whole chunk */
                        getchunkdata.invoke(cc, buf, 0, 0, 0, 16, 128, 16, 0);
                        snaparray[(chunk.x-x_min) + (chunk.z - z_min)*x_dim] = 
                            new CraftChunkSnapshot(chunk.x, chunk.z, buf); 
                    } catch (Exception x) {
                    }
                }
                if ((!wasLoaded) && didload) {
                    /* It looks like bukkit "leaks" entities - they don't get removed from the world-level table
                     * when chunks are unloaded but not saved - removing them seems to do the trick */
                    Chunk cc = w.getChunkAt(chunk.x, chunk.z);
                    if(cc != null) {
                        for(Entity e: cc.getEntities())
                            e.remove();
                    }
                    /* Since we only remember ones we loaded, and we're synchronous, no player has
                     * moved, so it must be safe (also prevent chunk leak, which appears to happen
                     * because isChunkInUse defined "in use" as being within 256 blocks of a player,
                     * while the actual in-use chunk area for a player where the chunks are managed
                     * by the MC base server is 21x21 (or about a 160 block radius) */
                    w.unloadChunk(chunk.x, chunk.z, false, false);
                }
            }
        }
        else {  /* Else, load and keep them loaded for now */
            // Load the required chunks.
            for (DynmapChunk chunk : chunks) {
                boolean wasLoaded = w.isChunkLoaded(chunk.x, chunk.z);
                boolean didload = w.loadChunk(chunk.x, chunk.z, false);
                if ((!wasLoaded) && didload)
                    loadedChunks.add(chunk);
            }
        }
    }
    /**
     * Unload chunks
     */
    public void unloadChunks() {
        if(snaparray != null) {
            for(int i = 0; i < snaparray.length; i++) {
                snaparray[i] = null;
            }
        }
        else {
            while (!loadedChunks.isEmpty()) {
                DynmapChunk c = loadedChunks.pollFirst();
                /* It looks like bukkit "leaks" entities - they don't get removed from the world-level table
                 * when chunks are unloaded but not saved - removing them seems to do the trick */
                Chunk cc = w.getChunkAt(c.x, c.z);
                if(cc != null) {
                    for(Entity e: cc.getEntities())
                        e.remove();
                }
                /* Since we only remember ones we loaded, and we're synchronous, no player has
                 * moved, so it must be safe (also prevent chunk leak, which appears to happen
                 * because isChunkInUse defined "in use" as being within 256 blocks of a player,
                 * while the actual in-use chunk area for a player where the chunks are managed
                 * by the MC base server is 21x21 (or about a 160 block radius) */
                w.unloadChunk(c.x, c.z, false, false);
            }
        }
    }
    /**
     * Get block ID at coordinates
     */
    public int getBlockTypeID(int x, int y, int z) {
        if(snaparray != null) {
            CraftChunkSnapshot ss = snaparray[((x>>4) - x_min) + ((z>>4) - z_min) * x_dim];
            if(ss == null)
                return 0;
            else
                return ss.getBlockTypeId(x & 0xF, y, z & 0xF);
        }
        else {
            return w.getBlockTypeIdAt(x, y, z);
        }
    }
    /**
     * Get block data at coordiates
     */
    public byte getBlockData(int x, int y, int z) {
        if(snaparray != null) {
            CraftChunkSnapshot ss = snaparray[((x>>4) - x_min) + ((z>>4) - z_min) * x_dim];
            if(ss == null)
                return 0;
            else
                return (byte)ss.getBlockData(x & 0xF, y, z & 0xF);
        }
        else {
            return w.getBlockAt(x, y, z).getData();
        }
    }
    /* Get highest block Y
     * 
     */
    public int getHighestBlockYAt(int x, int z) {
        if(snaparray != null) {
            CraftChunkSnapshot ss = snaparray[((x>>4) - x_min) + ((z>>4) - z_min) * x_dim];
            if(ss == null) {
                return 0;
            }
            else
                return ss.getHighestBlockYAt(x & 0xF, z & 0xF);
        }
        else {
            return w.getHighestBlockYAt(x, z);
        }
    }
}
