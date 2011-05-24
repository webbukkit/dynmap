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
    
    private ChunkSnapshot[] snaparray; /* Index = (x-x_min) + ((z-z_min)*x_dim) */
    private LinkedList<DynmapChunk> loadedChunks = new LinkedList<DynmapChunk>();

    /**
     * Iterator for traversing map chunk cache (base is for non-snapshot)
     */
    public class MapIterator {
        public int x, y, z;  
        MapIterator(int x0, int y0, int z0) {
            initialize(x0, y0, z0);
        }
        public void initialize(int x0, int y0, int z0) {
            this.x = x0;
            this.y = y0;
            this.z = z0;            
        }
        public int getBlockTypeID() {
            return w.getBlockTypeIdAt(x, y, z);
        }
        public int getBlockData() {
            return w.getBlockAt(x, y, z).getData();
        }
        public int getHighestBlockYAt() {
            return w.getHighestBlockYAt(x, z);
        }
        public int getBlockSkyLight() {
            return 15;
        }
        public int getBlockEmittedLight() {
            return 0;
        }
        public void incrementX() {
            x++;
        }
        public void decrementX() {
            x--;
        }
        public void incrementY() {
            y++;
        }
        public void decrementY() {
            y--;
        }
        public void incrementZ() {
            z++;
        }
        public void decrementZ() {
            z--;
        }
        public void setY(int y) {
            this.y = y;
        }
     }

    /**
     * Iterator for snapshot mode
     */
    public class SnapshotMapIterator extends MapIterator {
        private ChunkSnapshot snap;
        private int x4, z4;
    
        public SnapshotMapIterator(int x0, int y0, int z0) {
            super(x0, y0, z0);
        }
        public void initialize(int x0, int y0, int z0) {
            super.initialize(x0, y0, z0);
            try {
                snap = snaparray[((x>>4) - x_min) + ((z>>4) - z_min) * x_dim];
            } catch (ArrayIndexOutOfBoundsException aioobx) {
                snap = EMPTY;
            }
            x4 = x0 & 0xF;
            z4 = z0 & 0xF;
        }
        public int getBlockTypeID() {
            return snap.getBlockTypeId(x4, y, z4);
        }
        public int getBlockData() {
            return snap.getBlockData(x4, y, z4);
        }
        public int getHighestBlockYAt() {
            return snap.getHighestBlockYAt(x4, z4);
        }
        public int getBlockSkyLight() {
            return snap.getBlockSkyLight(x4, y, z4);
        }
        public int getBlockEmittedLight() {
            return snap.getBlockEmittedLight(x4, y, z4);
        }
        public void incrementX() {
            x++; x4 = x & 0xF;
            if(x4 == 0) {  /* Next chunk? */
                try {
                    snap = snaparray[((x>>4) - x_min) + ((z>>4) - z_min) * x_dim];
                } catch (ArrayIndexOutOfBoundsException aioobx) {
                    snap = EMPTY;
                }
            }
        }
        public void decrementX() {
            x--; x4 = x & 0xF;
            if(x4 == 15) {  /* Next chunk? */
                try {
                    snap = snaparray[((x>>4) - x_min) + ((z>>4) - z_min) * x_dim];
                } catch (ArrayIndexOutOfBoundsException aioobx) {
                    snap = EMPTY;
                }
            }
        }
        public void incrementY() {
            y++;
        }
        public void decrementY() {
            y--;
        }
        public void incrementZ() {
            z++; z4 = z & 0xF;
            if(z4 == 0) {  /* Next chunk? */
                try {
                    snap = snaparray[((x>>4) - x_min) + ((z>>4) - z_min) * x_dim];
                } catch (ArrayIndexOutOfBoundsException aioobx) {
                    snap = EMPTY;
                }
            }
        }
        public void decrementZ() {
            z--; z4 = z & 0xF;
            if(z4 == 15) {  /* Next chunk? */
                try {
                    snap = snaparray[((x>>4) - x_min) + ((z>>4) - z_min) * x_dim];
                } catch (ArrayIndexOutOfBoundsException aioobx) {
                    snap = EMPTY;
                }
            }
        }
    }
    /**
     * Chunk cache for representing unloaded chunk
     */
    private static class EmptyChunk implements ChunkSnapshot {
        public final int getBlockTypeId(int x, int y, int z) {
            return 0;
        }
        public final int getBlockData(int x, int y, int z) {
            return 0;
        }
        public final int getBlockSkyLight(int x, int y, int z) {
            return 15;
        }
        public final int getBlockEmittedLight(int x, int y, int z) {
            return 0;
        }
        public final int getHighestBlockYAt(int x, int z) {
            return 1;
        }
    }
    
    private static final EmptyChunk EMPTY = new EmptyChunk();
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
            snaparray = new ChunkSnapshot[x_dim * (z_max-z_min+1)];
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
                        CraftChunkSnapshot ss = new CraftChunkSnapshot(chunk.x, chunk.z, buf); 
                        snaparray[(chunk.x-x_min) + (chunk.z - z_min)*x_dim] = ss;
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
            for(int i = 0; i < snaparray.length; i++) {
                if(snaparray[i] == null)
                    snaparray[i] = EMPTY;
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
            ChunkSnapshot ss = snaparray[((x>>4) - x_min) + ((z>>4) - z_min) * x_dim];
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
            ChunkSnapshot ss = snaparray[((x>>4) - x_min) + ((z>>4) - z_min) * x_dim];
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
            ChunkSnapshot ss = snaparray[((x>>4) - x_min) + ((z>>4) - z_min) * x_dim];
            return ss.getHighestBlockYAt(x & 0xF, z & 0xF);
        }
        else {
            return w.getHighestBlockYAt(x, z);
        }
    }
    /* Get sky light level
     */
    public int getBlockSkyLight(int x, int y, int z) {
        if(snaparray != null) {
            ChunkSnapshot ss = snaparray[((x>>4) - x_min) + ((z>>4) - z_min) * x_dim];
            return ss.getBlockSkyLight(x & 0xF, y, z & 0xF);
        }
        else {
            return 15;
        }
    }
    /* Get emitted light level
     */
    public int getBlockEmittedLight(int x, int y, int z) {
        if(snaparray != null) {
            ChunkSnapshot ss = snaparray[((x>>4) - x_min) + ((z>>4) - z_min) * x_dim];
            return ss.getBlockEmittedLight(x & 0xF, y, z & 0xF);
        }
        else {
            return 0;
        }
    }
    /**
     * Get cache iterator
     */
    public MapIterator getIterator(int x, int y, int z) {
        if(snaparray != null)
            return new SnapshotMapIterator(x, y, z);
        else
            return new MapIterator(x, y, z);
    }
}
