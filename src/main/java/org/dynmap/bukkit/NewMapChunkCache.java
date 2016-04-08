package org.dynmap.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;
import org.bukkit.ChunkSnapshot;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.bukkit.SnapshotCache.SnapshotRec;
import org.dynmap.common.BiomeMap;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.utils.DynIntHashMap;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.VisibilityLimit;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class NewMapChunkCache extends MapChunkCache {
    private static boolean init = false;

    private World w;
    private DynmapWorld dw;
    private int nsect;
    private List<DynmapChunk> chunks;
    private ListIterator<DynmapChunk> iterator;
    private int x_min, x_max, z_min, z_max;
    private int x_dim;
    private boolean biome, biomeraw, highesty, blockdata;
    private HiddenChunkStyle hidestyle = HiddenChunkStyle.FILL_AIR;
    private List<VisibilityLimit> visible_limits = null;
    private List<VisibilityLimit> hidden_limits = null;
    private boolean isempty = true;
    private int snapcnt;
    private ChunkSnapshot[] snaparray; /* Index = (x-x_min) + ((z-z_min)*x_dim) */
    private DynIntHashMap[] snaptile;
    private byte[][] sameneighborbiomecnt;
    private BiomeMap[][] biomemap;
    private boolean[][] isSectionNotEmpty; /* Indexed by snapshot index, then by section index */
    private long[] inhabitedTicks;  /* Index = (x-x_min) + ((z-z_min)*x_dim) */
    private static final BiomeMap[] nullBiomeMap = { BiomeMap.NULL };
    
    private static BukkitVersionHelper helper = BukkitVersionHelper.getHelper();
    
    private static final BlockStep unstep[] = { BlockStep.X_MINUS, BlockStep.Y_MINUS, BlockStep.Z_MINUS,
        BlockStep.X_PLUS, BlockStep.Y_PLUS, BlockStep.Z_PLUS };

    private static BiomeMap[] biome_to_bmap;

    private static final int getIndexInChunk(int cx, int cy, int cz) {
        return (cy << 8) | (cz << 4) | cx;
    }

    /**
     * Iterator for traversing map chunk cache (base is for non-snapshot)
     */
    public class OurMapIterator implements MapIterator {
        @SuppressWarnings("unused")
        private int x, y, z, chunkindex, bx, bz, off;  
        private ChunkSnapshot snap;
        private BlockStep laststep;
        private int typeid = -1;
        private int blkdata = -1;
        private final int worldheight;
        private final int x_base;
        private final int z_base;
        
        OurMapIterator(int x0, int y0, int z0) {
            x_base = x_min << 4;
            z_base = z_min << 4;
            if(biome)
                biomePrep();
            initialize(x0, y0, z0);
            worldheight = w.getMaxHeight();
        }
        
        @Override
        public final void initialize(int x0, int y0, int z0) {
            this.x = x0;
            this.y = y0;
            this.z = z0;
            this.chunkindex = ((x >> 4) - x_min) + (((z >> 4) - z_min) * x_dim);
            this.bx = x & 0xF;
            this.bz = z & 0xF;
            this.off = bx + (bz << 4);
            if ((chunkindex >= snapcnt) || (chunkindex < 0)) {
                snap = EMPTY;
            }
            else {
                snap = snaparray[chunkindex];
            }
            laststep = BlockStep.Y_MINUS;
            if((y >= 0) && (y < worldheight))
                typeid = blkdata = -1;
            else
                typeid = blkdata = 0;
        }
        @Override
        public final int getBlockTypeID() {
            if(typeid < 0) {
                typeid = snap.getBlockTypeId(bx, y, bz);
            }
            return typeid;
        }
        @Override
        public final int getBlockData() {
            if(blkdata < 0) {
                blkdata = snap.getBlockData(bx, y, bz);
            }
            return blkdata;
        }
        @Override
        public int getBlockSkyLight() {
            try {
                return snap.getBlockSkyLight(bx, y, bz);
            } catch (ArrayIndexOutOfBoundsException aioobx) {
                return 15;
            }
        }
        @Override
        public final int getBlockEmittedLight() {
            try {
                return snap.getBlockEmittedLight(bx, y, bz);
            } catch (ArrayIndexOutOfBoundsException aioobx) {
                return 0;
            }
        }
        private void biomePrep() {
            if(sameneighborbiomecnt != null)
                return;
            int x_size = x_dim << 4;
            int z_size = (z_max - z_min + 1) << 4;
            sameneighborbiomecnt = new byte[x_size][];
            biomemap = new BiomeMap[x_size][];
            for(int i = 0; i < x_size; i++) {
                sameneighborbiomecnt[i] = new byte[z_size];
                biomemap[i] = new BiomeMap[z_size];
            }
            ChunkSnapshot last_css = null;
            Object[] biomebase = null;
            for(int i = 0; i < x_size; i++) {
                for(int j = 0; j < z_size; j++) {
                    BiomeMap bm;
                    if (j == 0) {
                        initialize(i + x_base, 64, z_base);
                    }
                    else {
                        stepPosition(BlockStep.Z_PLUS);
                    }
                    if (last_css != snap) {
                        if ((snap instanceof EmptyChunk) || (snap instanceof PlainChunk)) {
                            biomebase = nullBiomeMap;
                        }
                        else {
                            biomebase = helper.getBiomeBaseFromSnapshot(snap);
                        }
                        last_css = snap;
                    }
                    if (biomebase == nullBiomeMap) {
                        bm = BiomeMap.NULL;
                    }
                    else if(biomebase != null) {
                        bm = BiomeMap.byBiomeID(helper.getBiomeBaseID(biomebase[bz << 4 | bx]));
                    }
                    else {
                        Biome bb = snap.getBiome(bx, bz);
                        if(bb == null)
                            bm = BiomeMap.NULL;
                        else
                            bm = biome_to_bmap[bb.ordinal()];
                    }
                    biomemap[i][j] = bm;
                    int cnt = 0;
                    if(i > 0) {
                        if(bm == biomemap[i-1][j]) {   /* Same as one to left */
                            cnt++;
                            sameneighborbiomecnt[i-1][j]++;
                        }
                        if((j > 0) && (bm == biomemap[i-1][j-1])) {
                            cnt++;
                            sameneighborbiomecnt[i-1][j-1]++;
                        }
                        if((j < (z_size-1)) && (bm == biomemap[i-1][j+1])) {
                            cnt++;
                            sameneighborbiomecnt[i-1][j+1]++;
                        }
                    }
                    if((j > 0) && (biomemap[i][j] == biomemap[i][j-1])) {   /* Same as one to above */
                        cnt++;
                        sameneighborbiomecnt[i][j-1]++;
                    }
                    sameneighborbiomecnt[i][j] = (byte)cnt;
                }
            }
        }
        @Override
        public final BiomeMap getBiome() {
            try {
                return biomemap[x - x_base][z - z_base];
            } catch (Exception ex) {
                return BiomeMap.NULL;
            }
        }
        @Override
        public final int getSmoothGrassColorMultiplier(int[] colormap) {
            int mult = 0xFFFFFF;
            try {
                int rx = x - x_base;
                int rz = z - z_base;
                BiomeMap bm = biomemap[rx][rz];
                if(sameneighborbiomecnt[rx][rz] >= (byte)8) {   /* All neighbors same? */
                    mult = bm.getModifiedGrassMultiplier(colormap[bm.biomeLookup()]);
                }
                else {
                    int raccum = 0;
                    int gaccum = 0;
                    int baccum = 0;
                    for(int xoff = -1; xoff < 2; xoff++) {
                        for(int zoff = -1; zoff < 2; zoff++) {
                            bm = biomemap[rx+xoff][rz+zoff];
                            int rmult = bm.getModifiedGrassMultiplier(colormap[bm.biomeLookup()]);
                            raccum += (rmult >> 16) & 0xFF;
                            gaccum += (rmult >> 8) & 0xFF;
                            baccum += rmult & 0xFF;
                        }
                    }
                    mult = ((raccum / 9) << 16) | ((gaccum / 9) << 8) | (baccum / 9);
                }
            } catch (Exception x) {
                mult = 0xFFFFFF;
            }
            return mult;
        }
        @Override
        public final int getSmoothFoliageColorMultiplier(int[] colormap) {
            int mult = 0xFFFFFF;
            try {
                int rx = x - x_base;
                int rz = z - z_base;
                BiomeMap bm = biomemap[rx][rz];
                if(sameneighborbiomecnt[rx][rz] >= (byte)8) {   /* All neighbors same? */
                    mult = bm.getModifiedFoliageMultiplier(colormap[bm.biomeLookup()]);
                }
                else {
                    int raccum = 0;
                    int gaccum = 0;
                    int baccum = 0;
                    for(int xoff = -1; xoff < 2; xoff++) {
                        for(int zoff = -1; zoff < 2; zoff++) {
                            bm = biomemap[rx+xoff][rz+zoff];
                            int rmult = bm.getModifiedFoliageMultiplier(colormap[bm.biomeLookup()]);
                            raccum += (rmult >> 16) & 0xFF;
                            gaccum += (rmult >> 8) & 0xFF;
                            baccum += rmult & 0xFF;
                        }
                    }
                    mult = ((raccum / 9) << 16) | ((gaccum / 9) << 8) | (baccum / 9);
                }
            } catch (Exception x) {
                mult = 0xFFFFFF;
            }
            return mult;
        }
        @Override
        public final int getSmoothColorMultiplier(int[] colormap, int[] swampmap) {
            int mult = 0xFFFFFF;
            try {
                int rx = x - x_base;
                int rz = z - z_base;
                BiomeMap bm = biomemap[rx][rz];
                if(sameneighborbiomecnt[rx][rz] >= (byte)8) {   /* All neighbors same? */
                    if(bm == BiomeMap.SWAMPLAND) {
                        mult = swampmap[bm.biomeLookup()];
                    }
                    else {
                        mult = colormap[bm.biomeLookup()];
                    }
                }
                else {
                    int raccum = 0;
                    int gaccum = 0;
                    int baccum = 0;
                    for(int xoff = -1; xoff < 2; xoff++) {
                        for(int zoff = -1; zoff < 2; zoff++) {
                            bm = biomemap[rx+xoff][rz+zoff];
                            int rmult;
                            if(bm == BiomeMap.SWAMPLAND) {
                                rmult = swampmap[bm.biomeLookup()];
                            }
                            else {
                                rmult = colormap[bm.biomeLookup()];
                            }
                            raccum += (rmult >> 16) & 0xFF;
                            gaccum += (rmult >> 8) & 0xFF;
                            baccum += rmult & 0xFF;
                        }
                    }
                    mult = ((raccum / 9) << 16) | ((gaccum / 9) << 8) | (baccum / 9);
                }
            } catch (Exception x) {
                mult = 0xFFFFFF;
            }
            return mult;
        }
        @Override
        public final int getSmoothWaterColorMultiplier() {
            try {
                int rx = x - x_base;
                int rz = z - z_base;
                BiomeMap bm = biomemap[rx][rz];
                if(sameneighborbiomecnt[rx][rz] >= (byte)8) {   /* All neighbors same? */
                    return bm.getWaterColorMult();
                }
                int raccum = 0;
                int gaccum = 0;
                int baccum = 0;
                for(int xoff = -1; xoff < 2; xoff++) {
                    for(int zoff = -1; zoff < 2; zoff++) {
                        bm = biomemap[rx+xoff][rz+zoff];
                        int mult = bm.getWaterColorMult();
                        raccum += (mult >> 16) & 0xFF;
                        gaccum += (mult >> 8) & 0xFF;
                        baccum += mult & 0xFF;
                    }
                }
                return ((raccum / 9) << 16) | ((gaccum / 9) << 8) | (baccum / 9);
            } catch (Exception x) {
                return 0xFFFFFF;
            }
        }
        @Override
        public final int getSmoothWaterColorMultiplier(int[] colormap) {
            int mult = 0xFFFFFF;
            try {
                int rx = x - x_base;
                int rz = z - z_base;
                BiomeMap bm = biomemap[rx][rz];
                if(sameneighborbiomecnt[rx][rz] >= (byte)8) {   /* All neighbors same? */
                    mult = colormap[bm.biomeLookup()];
                }
                else {
                    int raccum = 0;
                    int gaccum = 0;
                    int baccum = 0;
                    for(int xoff = -1; xoff < 2; xoff++) {
                        for(int zoff = -1; zoff < 2; zoff++) {
                            bm = biomemap[rx+xoff][rz+zoff];
                            int rmult = colormap[bm.biomeLookup()];
                            raccum += (rmult >> 16) & 0xFF;
                            gaccum += (rmult >> 8) & 0xFF;
                            baccum += rmult & 0xFF;
                        }
                    }
                    mult = ((raccum / 9) << 16) | ((gaccum / 9) << 8) | (baccum / 9);
                }
            } catch (Exception x) {
                mult = 0xFFFFFF;
            }
            return mult;
        }
        /**
         * Step current position in given direction
         */
        @Override
        public final void stepPosition(BlockStep step) {
            typeid = -1;
            blkdata = -1;
            switch(step.ordinal()) {
                case 0:
                    x++;
                    bx++;
                    off++;
                    if(bx == 16) {  /* Next chunk? */
                        bx = 0;
                        off -= 16;
                        chunkindex++;
                        if ((chunkindex >= snapcnt) || (chunkindex < 0)) {
                            snap = EMPTY;
                        }
                        else {
                            snap = snaparray[chunkindex];
                        }
                    }
                    break;
                case 1:
                    y++;
                    if(y >= worldheight) {
                        typeid = blkdata = 0;
                    }
                    break;
                case 2:
                    z++;
                    bz++;
                    off+=16;
                    if(bz == 16) {  /* Next chunk? */
                        bz = 0;
                        off -= 256;
                        chunkindex += x_dim;
                        if ((chunkindex >= snapcnt) || (chunkindex < 0)) {
                            snap = EMPTY;
                        }
                        else {
                            snap = snaparray[chunkindex];
                        }
                    }
                    break;
                case 3:
                    x--;
                    bx--;
                    off--;
                    if(bx == -1) {  /* Next chunk? */
                        bx = 15;
                        off += 16;
                        chunkindex--;
                        if ((chunkindex >= snapcnt) || (chunkindex < 0)) {
                            snap = EMPTY;
                        }
                        else {
                            snap = snaparray[chunkindex];
                        }
                    }
                    break;
                case 4:
                    y--;
                    if(y < 0) {
                        typeid = blkdata = 0;
                    }
                    break;
                case 5:
                    z--;
                    bz--;
                    off-=16;
                    if(bz == -1) {  /* Next chunk? */
                        bz = 15;
                        off += 256;
                        chunkindex -= x_dim;
                        if ((chunkindex >= snapcnt) || (chunkindex < 0)) {
                            snap = EMPTY;
                        }
                        else {
                            snap = snaparray[chunkindex];
                        }
                    }
                    break;
            }
            laststep = step;
        }
        /**
         * Unstep current position to previous position
         */
        @Override
        public BlockStep unstepPosition() {
            BlockStep ls = laststep;
            stepPosition(unstep[ls.ordinal()]);
            return ls;
        }
        /**
         * Unstep current position in oppisite director of given step
         */
        @Override
        public void unstepPosition(BlockStep s) {
            stepPosition(unstep[s.ordinal()]);
        }
        @Override
        public final void setY(int y) {
            if(y > this.y)
                laststep = BlockStep.Y_PLUS;
            else
                laststep = BlockStep.Y_MINUS;
            this.y = y;
            if((y < 0) || (y >= worldheight)) {
                typeid = blkdata = 0;
            }
            else {
                typeid = blkdata = -1;
            }
        }
        @Override
        public final int getX() {
            return x;
        }
        @Override
        public final int getY() {
            return y;
        }
        @Override
        public final int getZ() {
            return z;
        }
        @Override
        public final int getBlockTypeIDAt(BlockStep s) {
            if(s == BlockStep.Y_MINUS) {
                if(y > 0)
                    return snap.getBlockTypeId(bx, y-1, bz);
            }
            else if(s == BlockStep.Y_PLUS) {
                if(y < (worldheight-1))
                    return snap.getBlockTypeId(bx, y+1, bz);
            }
            else {
                BlockStep ls = laststep;
                stepPosition(s);
                int tid = snap.getBlockTypeId(bx, y, bz);
                unstepPosition();
                laststep = ls;
                return tid;
            }
            return 0;
        }
        @Override
        public BlockStep getLastStep() {
            return laststep;
        }
        @Override
        public int getWorldHeight() {
            return worldheight;
        }
        @Override
        public long getBlockKey() {
            return (((chunkindex * worldheight) + y) << 8) | (bx << 4) | bz;
        }
        @Override
        public final boolean isEmptySection() {
            try {
                return !isSectionNotEmpty[chunkindex][y >> 4];
            } catch (Exception x) {
                initSectionData(chunkindex);
                return !isSectionNotEmpty[chunkindex][y >> 4];
            }
        }
        @Override
        public RenderPatchFactory getPatchFactory() {
            return HDBlockModels.getPatchDefinitionFactory();
        }
        @Override
        public Object getBlockTileEntityField(String fieldId) {
            try {
                int idx = getIndexInChunk(bx,y,bz);
                Object[] vals = (Object[])snaptile[chunkindex].get(idx);
                for (int i = 0; i < vals.length; i += 2) {
                    if (vals[i].equals(fieldId)) {
                        return vals[i+1];
                    }
                }
            } catch (Exception x) {
            }
            return null;
        }
        @Override
        public int getBlockTypeIDAt(int xoff, int yoff, int zoff) {
            int xx = this.x + xoff;
            int yy = this.y + yoff;
            int zz = this.z + zoff;
            int idx = ((xx >> 4) - x_min) + (((zz >> 4) - z_min) * x_dim);
            try {
                return snaparray[idx].getBlockTypeId(xx & 0xF, yy, zz & 0xF);
            } catch (Exception x) {
                return 0;
            }
        }
        @Override
        public int getBlockDataAt(int xoff, int yoff, int zoff) {
            int xx = this.x + xoff;
            int yy = this.y + yoff;
            int zz = this.z + zoff;
            int idx = ((xx >> 4) - x_min) + (((zz >> 4) - z_min) * x_dim);
            try {
                return snaparray[idx].getBlockData(xx & 0xF, yy, zz & 0xF);
            } catch (Exception x) {
                return 0;
            }
        }
        @Override
        public Object getBlockTileEntityFieldAt(String fieldId, int xoff,
                int yoff, int zoff) {
            return null;
        }
        @Override
        public long getInhabitedTicks() {
            try {
                return inhabitedTicks[chunkindex];
            } catch (Exception x) {
                return 0;
            }
        }
     }

    private class OurEndMapIterator extends OurMapIterator {

        OurEndMapIterator(int x0, int y0, int z0) {
            super(x0, y0, z0);
        }
        public final int getBlockSkyLight() {
            return 15;
        }
    }
    /**
     * Chunk cache for representing unloaded chunk (or air)
     */
    private static class EmptyChunk implements ChunkSnapshot {
        /* Need these for interface, but not used */
        public int getX() { return 0; }
        public int getZ() { return 0; }
        public String getWorldName() { return ""; }
        public long getCaptureFullTime() { return 0; }
        
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
            return 0;
        }
        public Biome getBiome(int x, int z) {
            return null;
        }
        public double getRawBiomeTemperature(int x, int z) {
            return 0.0;
        }
        public double getRawBiomeRainfall(int x, int z) {
            return 0.0;
        }
        public boolean isSectionEmpty(int sy) {
            return true;
        }
    }

    /**
     * Chunk cache for representing generic stone chunk
     */
    private static class PlainChunk implements ChunkSnapshot {
        private int fillid;
        PlainChunk(int fillid) { this.fillid = fillid; }
        /* Need these for interface, but not used */
        public int getX() { return 0; }
        public int getZ() { return 0; }
        public String getWorldName() { return ""; }
        public Biome getBiome(int x, int z) { return null; }
        public double getRawBiomeTemperature(int x, int z) { return 0.0; }
        public double getRawBiomeRainfall(int x, int z) { return 0.0; }
        public long getCaptureFullTime() { return 0; }
        
        public final int getBlockTypeId(int x, int y, int z) {
            if(y < 64) return fillid;
            return 0;
        }
        public final int getBlockData(int x, int y, int z) {
            return 0;
        }
        public final int getBlockSkyLight(int x, int y, int z) {
            if(y < 64)
                return 0;
            return 15;
        }
        public final int getBlockEmittedLight(int x, int y, int z) {
            return 0;
        }
        public final int getHighestBlockYAt(int x, int z) {
            return 64;
        }
        public boolean isSectionEmpty(int sy) {
            return (sy < 4);
        }
    }

    private static final EmptyChunk EMPTY = new EmptyChunk();
    private static final PlainChunk STONE = new PlainChunk(1);
    private static final PlainChunk OCEAN = new PlainChunk(9);

    /**
     * Construct empty cache
     */
    public NewMapChunkCache() {
        if(!init) {
            init = true;
        }
    }
    public void setChunks(BukkitWorld dw, List<DynmapChunk> chunks) {
        this.dw = dw;
        this.w = dw.getWorld();
        if(this.w == null) {
            this.chunks = new ArrayList<DynmapChunk>();
        }
        nsect = dw.worldheight >> 4;
        this.chunks = chunks;
        /* Compute range */
        if(chunks.size() == 0) {
            this.x_min = 0;
            this.x_max = 0;
            this.z_min = 0;
            this.z_max = 0;
            x_dim = 1;            
        }
        else {
            x_min = x_max = chunks.get(0).x;
            z_min = z_max = chunks.get(0).z;
            for(DynmapChunk c : chunks) {
                if(c.x > x_max)
                    x_max = c.x;
                if(c.x < x_min)
                    x_min = c.x;
                if(c.z > z_max)
                    z_max = c.z;
                if(c.z < z_min)
                    z_min = c.z;
            }
            x_dim = x_max - x_min + 1;            
        }
    
        snapcnt = x_dim * (z_max-z_min+1);
        snaparray = new ChunkSnapshot[snapcnt];
        inhabitedTicks = new long[snapcnt];
        snaptile = new DynIntHashMap[snapcnt];
        isSectionNotEmpty = new boolean[snapcnt][];
    }
    
    public int loadChunks(int max_to_load) {
        if(dw.isLoaded() == false)
            return 0;
        Object queue = helper.getUnloadQueue(w);
        
        int cnt = 0;
        if(iterator == null)
            iterator = chunks.listIterator();

        DynmapCore.setIgnoreChunkLoads(true);
        //boolean isnormral = w.getEnvironment() == Environment.NORMAL;
        // Load the required chunks.
        while((cnt < max_to_load) && iterator.hasNext()) {
            long startTime = System.nanoTime();
            DynmapChunk chunk = iterator.next();
            boolean vis = true;
            if(visible_limits != null) {
                vis = false;
                for(VisibilityLimit limit : visible_limits) {
                    if (limit.doIntersectChunk(chunk.x, chunk.z)) {
                        vis = true;
                        break;
                    }
                }
            }
            if(vis && (hidden_limits != null)) {
                for(VisibilityLimit limit : hidden_limits) {
                    if (limit.doIntersectChunk(chunk.x, chunk.z)) {
                        vis = false;
                        break;
                    }
                }
            }
            /* Check if cached chunk snapshot found */
            ChunkSnapshot ss = null;
            long inhabited_ticks = 0;
            DynIntHashMap tileData = null;
            SnapshotRec ssr = DynmapPlugin.plugin.sscache.getSnapshot(dw.getName(), chunk.x, chunk.z, blockdata, biome, biomeraw, highesty); 
            if(ssr != null) {
                ss = ssr.ss;
                inhabited_ticks = ssr.inhabitedTicks;
                if(!vis) {
                    if(hidestyle == HiddenChunkStyle.FILL_STONE_PLAIN)
                        ss = STONE;
                    else if(hidestyle == HiddenChunkStyle.FILL_OCEAN)
                        ss = OCEAN;
                    else
                        ss = EMPTY;
                }
                int idx = (chunk.x-x_min) + (chunk.z - z_min)*x_dim;
                snaparray[idx] = ss;
                snaptile[idx] = ssr.tileData;
                inhabitedTicks[idx] = inhabited_ticks;
                
                endChunkLoad(startTime, ChunkStats.CACHED_SNAPSHOT_HIT);
                continue;
            }
            boolean wasLoaded = w.isChunkLoaded(chunk.x, chunk.z);
            boolean didload = false;
            boolean isunloadpending = false;
            if (queue != null) {
                isunloadpending = helper.isInUnloadQueue(queue, chunk.x, chunk.z);
            }
            if (isunloadpending) {  /* Workaround: can't be pending if not loaded */
                wasLoaded = true;
            }
            try {
                didload = w.loadChunk(chunk.x, chunk.z, false);
            } catch (Throwable t) { /* Catch chunk error from Bukkit */
                Log.warning("Bukkit error loading chunk " + chunk.x + "," + chunk.z + " on " + w.getName());
                if(!wasLoaded) {    /* If wasn't loaded, we loaded it if it now is */
                    didload = w.isChunkLoaded(chunk.x, chunk.z);
                }
            }
            /* If it did load, make cache of it */
            if(didload) {
                tileData = new DynIntHashMap();

                Chunk c = w.getChunkAt(chunk.x, chunk.z);   /* Get the chunk */
                /* Get inhabited ticks count */
                inhabited_ticks = helper.getInhabitedTicks(c);
                if(!vis) {
                    if(hidestyle == HiddenChunkStyle.FILL_STONE_PLAIN)
                        ss = STONE;
                    else if(hidestyle == HiddenChunkStyle.FILL_OCEAN)
                        ss = OCEAN;
                    else
                        ss = EMPTY;
                }
                else {
                    if(blockdata || highesty) {
                        ss = c.getChunkSnapshot(highesty, biome, biomeraw);
                        /* Get tile entity data */
                        List<Object> vals = new ArrayList<Object>();
                        Map<?,?> tileents = helper.getTileEntitiesForChunk(c);
                        for(Object t : tileents.values()) {
                            int te_x = helper.getTileEntityX(t);
                            int te_y = helper.getTileEntityY(t);
                            int te_z = helper.getTileEntityZ(t);
                            int cx = te_x & 0xF;
                            int cz = te_z & 0xF;
                            int blkid = ss.getBlockTypeId(cx, te_y, cz);
                            int blkdat = ss.getBlockData(cx, te_y, cz);
                            String[] te_fields = HDBlockModels.getTileEntityFieldsNeeded(blkid,  blkdat);
                            if(te_fields != null) {
                                Object nbtcompound = helper.readTileEntityNBT(t);
                                
                                vals.clear();
                                for(String id: te_fields) {
                                    Object val = helper.getFieldValue(nbtcompound, id);
                                    if(val != null) {
                                        vals.add(id);
                                        vals.add(val);
                                    }
                                }
                                if(vals.size() > 0) {
                                    Object[] vlist = vals.toArray(new Object[vals.size()]);
                                    tileData.put(getIndexInChunk(cx,te_y,cz), vlist);
                                }
                            }
                        }
                    }
                    else
                        ss = w.getEmptyChunkSnapshot(chunk.x, chunk.z, biome, biomeraw);
                    if(ss != null) {
                        ssr = new SnapshotRec();
                        ssr.ss = ss;
                        ssr.inhabitedTicks = inhabited_ticks;
                        ssr.tileData = tileData;
                        DynmapPlugin.plugin.sscache.putSnapshot(dw.getName(), chunk.x, chunk.z, ssr, blockdata, biome, biomeraw, highesty);
                    }
                }
                int chunkIndex = (chunk.x-x_min) + (chunk.z - z_min)*x_dim;
                snaparray[chunkIndex] = ss;
                snaptile[chunkIndex] = tileData;
                inhabitedTicks[chunkIndex] = inhabited_ticks;
                
                /* If wasn't loaded before, we need to do unload */
                if (!wasLoaded) {
                    /* Since we only remember ones we loaded, and we're synchronous, no player has
                     * moved, so it must be safe (also prevent chunk leak, which appears to happen
                     * because isChunkInUse defined "in use" as being within 256 blocks of a player,
                     * while the actual in-use chunk area for a player where the chunks are managed
                     * by the MC base server is 21x21 (or about a 160 block radius).
                     * Also, if we did generate it, need to save it */
                    if (w.isChunkInUse(chunk.x, chunk.z) == false) {
                        if (helper.isUnloadChunkBroken()) {
                            // Give up on broken unloadChunk API - lets see if this works
                            w.unloadChunkRequest(chunk.x, chunk.z);
                        }
                        else {
                            helper.unloadChunkNoSave(w, c, chunk.x, chunk.z);
                        }
                    }
                    endChunkLoad(startTime, ChunkStats.UNLOADED_CHUNKS);
                }
                else if (isunloadpending) { /* Else, if loaded and unload is pending */
                    if (w.isChunkInUse(chunk.x, chunk.z) == false) {
                        w.unloadChunkRequest(chunk.x, chunk.z); /* Request new unload */
                    }
                    endChunkLoad(startTime, ChunkStats.LOADED_CHUNKS);
                }
                else {
                    endChunkLoad(startTime, ChunkStats.LOADED_CHUNKS);
                }
            }
            else {
                endChunkLoad(startTime, ChunkStats.UNGENERATED_CHUNKS);
            }
            cnt++;
        }
        DynmapCore.setIgnoreChunkLoads(false);

        if(iterator.hasNext() == false) {   /* If we're done */
            isempty = true;
            /* Fill missing chunks with empty dummy chunk */
            for(int i = 0; i < snaparray.length; i++) {
                if(snaparray[i] == null)
                    snaparray[i] = EMPTY;
                else if(snaparray[i] != EMPTY)
                    isempty = false;
            }
        }

        return cnt;
    }
    /**
     * Test if done loading
     */
    public boolean isDoneLoading() {
        if(dw.isLoaded() == false) {
            isempty = true;
            unloadChunks();
            return true;
        }
        if(iterator != null)
            return !iterator.hasNext();
        return false;
    }
    /**
     * Test if all empty blocks
     */
    public boolean isEmpty() {
        return isempty;
    }
    /**
     * Unload chunks
     */
    public void unloadChunks() {
        if(snaparray != null) {
            for(int i = 0; i < snaparray.length; i++) {
                snaparray[i] = null;
            }
            snaparray = null;
            inhabitedTicks = null;
        }
    }
    private void initSectionData(int idx) {
        isSectionNotEmpty[idx] = new boolean[nsect + 1];
        if(snaparray[idx] != EMPTY) {
            for(int i = 0; i < nsect; i++) {
                if(snaparray[idx].isSectionEmpty(i) == false) {
                    isSectionNotEmpty[idx][i] = true;
                }
            }
        }
    }
    public boolean isEmptySection(int sx, int sy, int sz) {
        int idx = (sx - x_min) + (sz - z_min) * x_dim;
        if(isSectionNotEmpty[idx] == null) {
            initSectionData(idx);
        }
        return !isSectionNotEmpty[idx][sy];
    }

    /**
     * Get cache iterator
     */
    public MapIterator getIterator(int x, int y, int z) {
        if(w.getEnvironment().toString().equals("THE_END"))
            return new OurEndMapIterator(x, y, z);
        return new OurMapIterator(x, y, z);
    }
    /**
     * Set hidden chunk style (default is FILL_AIR)
     */
    public void setHiddenFillStyle(HiddenChunkStyle style) {
        this.hidestyle = style;
    }
    /**
     * Add visible area limit - can be called more than once 
     * Needs to be set before chunks are loaded
     * Coordinates are block coordinates
     */
    public void setVisibleRange(VisibilityLimit lim) {
        if(visible_limits == null)
            visible_limits = new ArrayList<VisibilityLimit>();
        visible_limits.add(lim);
    }
    /**
     * Add hidden area limit - can be called more than once 
     * Needs to be set before chunks are loaded
     * Coordinates are block coordinates
     */
    public void setHiddenRange(VisibilityLimit lim) {
        if(hidden_limits == null)
            hidden_limits = new ArrayList<VisibilityLimit>();
        hidden_limits.add(lim);
    }
    @Override
    public boolean setChunkDataTypes(boolean blockdata, boolean biome, boolean highestblocky, boolean rawbiome) {
        this.biome = biome;
        this.biomeraw = rawbiome;
        this.highesty = highestblocky;
        this.blockdata = blockdata;
        return true;
    }
    @Override
    public DynmapWorld getWorld() {
        return dw;
    }
    
    static {
        Biome[] b = Biome.values();
        BiomeMap[] bm = BiomeMap.values();
        biome_to_bmap = new BiomeMap[1024];
        for(int i = 0; i < biome_to_bmap.length; i++) {
            biome_to_bmap[i] = BiomeMap.NULL;
        }
        for(int i = 0; i < b.length; i++) {
            String bs = b[i].toString();
            for(int j = 0; j < bm.length; j++) {
                if(bm[j].toString().equals(bs)) {
                    biome_to_bmap[b[i].ordinal()] = bm[j];
                    break;
                }
            }
        }
    }
}
