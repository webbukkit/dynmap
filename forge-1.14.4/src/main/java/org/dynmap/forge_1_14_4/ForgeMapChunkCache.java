package org.dynmap.forge_1_14_4;

import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.ByteArrayNBT;
import net.minecraft.nbt.ByteNBT;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.DoubleNBT;
import net.minecraft.nbt.FloatNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.IntArrayNBT;
import net.minecraft.nbt.IntNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.LongNBT;
import net.minecraft.nbt.ShortNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.AbstractChunkProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.storage.ChunkSerializer;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
import org.dynmap.forge_1_14_4.SnapshotCache.SnapshotRec;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.utils.DynIntHashMap;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.VisibilityLimit;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class ForgeMapChunkCache extends MapChunkCache
{
    private static boolean init = false;
    private static Field updateEntityTick = null;
    /* ChunkManager fields */
    private static Field chunksToRemove = null; // Map

    /* ChunjManager Pending fields */
    private static Field chunkCoord = null;
    private static Field nbtTag = null;
    
    private World w;
    private DynmapWorld dw;
    private ServerChunkProvider cps;
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

    
    private static final BlockStep unstep[] = { BlockStep.X_MINUS, BlockStep.Y_MINUS, BlockStep.Z_MINUS,
            BlockStep.X_PLUS, BlockStep.Y_PLUS, BlockStep.Z_PLUS
                                              };

    private static BiomeMap[] biome_to_bmap;

    private static final int getIndexInChunk(int cx, int cy, int cz) {
        return (cy << 8) | (cz << 4) | cx;
    }

    /**
     * Iterator for traversing map chunk cache (base is for non-snapshot)
     */
    public class OurMapIterator implements MapIterator
    {
        private int x, y, z, chunkindex, bx, bz;
        private ChunkSnapshot snap;
        private BlockStep laststep;
        private DynmapBlockState blk;
        private final int worldheight;
        private final int x_base;
        private final int z_base;

        OurMapIterator(int x0, int y0, int z0)
        {
            x_base = x_min << 4;
            z_base = z_min << 4;

            if (biome)
            {
                biomePrep();
            }

            initialize(x0, y0, z0);
            worldheight = w.getHeight();
        }
        @Override
        public final void initialize(int x0, int y0, int z0)
        {
            this.x = x0;
            this.y = y0;
            this.z = z0;
            this.chunkindex = ((x >> 4) - x_min) + (((z >> 4) - z_min) * x_dim);
            this.bx = x & 0xF;
            this.bz = z & 0xF;

            if((chunkindex >= snapcnt) || (chunkindex < 0)) {
                snap = EMPTY;
            }
            else {
                snap = snaparray[chunkindex];
            }

            laststep = BlockStep.Y_MINUS;

            if ((y >= 0) && (y < worldheight))
            {
                blk = null;
            }
            else
            {
                blk = DynmapBlockState.AIR;
            }
        }
        @Override
        public int getBlockSkyLight()
        {
            try
            {
                return snap.getBlockSkyLight(bx, y, bz);
            }
            catch (ArrayIndexOutOfBoundsException aioobx)
            {
                return 15;
            }
        }
        @Override
        public final int getBlockEmittedLight()
        {
            try
            {
                return snap.getBlockEmittedLight(bx, y, bz);
            }
            catch (ArrayIndexOutOfBoundsException aioobx)
            {
                return 0;
            }
        }
        private void biomePrep()
        {
            if (sameneighborbiomecnt != null)
            {
                return;
            }

            int x_size = x_dim << 4;
            int z_size = (z_max - z_min + 1) << 4;
            sameneighborbiomecnt = new byte[x_size][];
            biomemap = new BiomeMap[x_size][];

            for (int i = 0; i < x_size; i++)
            {
                sameneighborbiomecnt[i] = new byte[z_size];
                biomemap[i] = new BiomeMap[z_size];
            }

            for (int i = 0; i < x_size; i++)
            {
                for (int j = 0; j < z_size; j++)
                {
                    if (j == 0)
                        initialize(i + x_base, 64, z_base);
                    else
                        stepPosition(BlockStep.Z_PLUS);

                    int bb = snap.getBiome(bx, bz);
                    BiomeMap bm = BiomeMap.byBiomeID(bb);

                    biomemap[i][j] = bm;
                    int cnt = 0;

                    if (i > 0)
                    {
                        if (bm == biomemap[i - 1][j])  /* Same as one to left */
                        {
                            cnt++;
                            sameneighborbiomecnt[i - 1][j]++;
                        }

                        if ((j > 0) && (bm == biomemap[i - 1][j - 1]))
                        {
                            cnt++;
                            sameneighborbiomecnt[i - 1][j - 1]++;
                        }

                        if ((j < (z_size - 1)) && (bm == biomemap[i - 1][j + 1]))
                        {
                            cnt++;
                            sameneighborbiomecnt[i - 1][j + 1]++;
                        }
                    }

                    if ((j > 0) && (biomemap[i][j] == biomemap[i][j - 1]))  /* Same as one to above */
                    {
                        cnt++;
                        sameneighborbiomecnt[i][j - 1]++;
                    }

                    sameneighborbiomecnt[i][j] = (byte)cnt;
                }
            }
        }
        @Override
        public final BiomeMap getBiome()
        {
            try
            {
                return biomemap[x - x_base][z - z_base];
            }
            catch (Exception ex)
            {
                return BiomeMap.NULL;
            }
        }
        @Override
        public final int getSmoothGrassColorMultiplier(int[] colormap)
        {
            int mult = 0xFFFFFF;

            try
            {
                int rx = x - x_base;
                int rz = z - z_base;
                BiomeMap bm = biomemap[rx][rz];

                if (sameneighborbiomecnt[rx][rz] >= (byte)8)    /* All neighbors same? */
                {
                    mult = bm.getModifiedGrassMultiplier(colormap[bm.biomeLookup()]);
                }
                else
                {
                    int raccum = 0;
                    int gaccum = 0;
                    int baccum = 0;

                    for (int xoff = -1; xoff < 2; xoff++)
                    {
                        for (int zoff = -1; zoff < 2; zoff++)
                        {
                            bm = biomemap[rx + xoff][rz + zoff];
                            int rmult = bm.getModifiedGrassMultiplier(colormap[bm.biomeLookup()]);
                            raccum += (rmult >> 16) & 0xFF;
                            gaccum += (rmult >> 8) & 0xFF;
                            baccum += rmult & 0xFF;
                        }
                    }

                    mult = ((raccum / 9) << 16) | ((gaccum / 9) << 8) | (baccum / 9);
                }
            }
            catch (Exception x)
            {
                mult = 0xFFFFFF;
            }

            return mult;
        }
        @Override
        public final int getSmoothFoliageColorMultiplier(int[] colormap)
        {
            int mult = 0xFFFFFF;

            try
            {
                int rx = x - x_base;
                int rz = z - z_base;
                BiomeMap bm = biomemap[rx][rz];

                if (sameneighborbiomecnt[rx][rz] >= (byte)8)    /* All neighbors same? */
                {
                    mult = bm.getModifiedFoliageMultiplier(colormap[bm.biomeLookup()]);
                }
                else
                {
                    int raccum = 0;
                    int gaccum = 0;
                    int baccum = 0;

                    for (int xoff = -1; xoff < 2; xoff++)
                    {
                        for (int zoff = -1; zoff < 2; zoff++)
                        {
                            bm = biomemap[rx + xoff][rz + zoff];
                            int rmult = bm.getModifiedFoliageMultiplier(colormap[bm.biomeLookup()]);
                            raccum += (rmult >> 16) & 0xFF;
                            gaccum += (rmult >> 8) & 0xFF;
                            baccum += rmult & 0xFF;
                        }
                    }

                    mult = ((raccum / 9) << 16) | ((gaccum / 9) << 8) | (baccum / 9);
                }
            }
            catch (Exception x)
            {
                mult = 0xFFFFFF;
            }

            return mult;
        }
        @Override
        public final int getSmoothColorMultiplier(int[] colormap, int[] swampmap)
        {
            int mult = 0xFFFFFF;

            try
            {
                int rx = x - x_base;
                int rz = z - z_base;
                BiomeMap bm = biomemap[rx][rz];

                if (sameneighborbiomecnt[rx][rz] >= (byte)8)    /* All neighbors same? */
                {
                    if (bm == BiomeMap.SWAMPLAND)
                    {
                        mult = swampmap[bm.biomeLookup()];
                    }
                    else
                    {
                        mult = colormap[bm.biomeLookup()];
                    }
                }
                else
                {
                    int raccum = 0;
                    int gaccum = 0;
                    int baccum = 0;

                    for (int xoff = -1; xoff < 2; xoff++)
                    {
                        for (int zoff = -1; zoff < 2; zoff++)
                        {
                            bm = biomemap[rx + xoff][rz + zoff];
                            int rmult;

                            if (bm == BiomeMap.SWAMPLAND)
                            {
                                rmult = swampmap[bm.biomeLookup()];
                            }
                            else
                            {
                                rmult = colormap[bm.biomeLookup()];
                            }

                            raccum += (rmult >> 16) & 0xFF;
                            gaccum += (rmult >> 8) & 0xFF;
                            baccum += rmult & 0xFF;
                        }
                    }

                    mult = ((raccum / 9) << 16) | ((gaccum / 9) << 8) | (baccum / 9);
                }
            }
            catch (Exception x)
            {
                mult = 0xFFFFFF;
            }

            return mult;
        }
        @Override
        public final int getSmoothWaterColorMultiplier()
        {
            try
            {
                int rx = x - x_base;
                int rz = z - z_base;
                BiomeMap bm = biomemap[rx][rz];

                if (sameneighborbiomecnt[rx][rz] >= (byte)8)    /* All neighbors same? */
                {
                    return bm.getWaterColorMult();
                }

                int raccum = 0;
                int gaccum = 0;
                int baccum = 0;

                for (int xoff = -1; xoff < 2; xoff++)
                {
                    for (int zoff = -1; zoff < 2; zoff++)
                    {
                        bm = biomemap[rx + xoff][rz + zoff];
                        int mult = bm.getWaterColorMult();
                        raccum += (mult >> 16) & 0xFF;
                        gaccum += (mult >> 8) & 0xFF;
                        baccum += mult & 0xFF;
                    }
                }

                return ((raccum / 9) << 16) | ((gaccum / 9) << 8) | (baccum / 9);
            }
            catch (Exception x)
            {
                return 0xFFFFFF;
            }
        }
        @Override
        public final int getSmoothWaterColorMultiplier(int[] colormap)
        {
            int mult = 0xFFFFFF;

            try
            {
                int rx = x - x_base;
                int rz = z - z_base;
                BiomeMap bm = biomemap[rx][rz];

                if (sameneighborbiomecnt[rx][rz] >= (byte)8)    /* All neighbors same? */
                {
                    mult = colormap[bm.biomeLookup()];
                }
                else
                {
                    int raccum = 0;
                    int gaccum = 0;
                    int baccum = 0;

                    for (int xoff = -1; xoff < 2; xoff++)
                    {
                        for (int zoff = -1; zoff < 2; zoff++)
                        {
                            bm = biomemap[rx + xoff][rz + zoff];
                            int rmult = colormap[bm.biomeLookup()];
                            raccum += (rmult >> 16) & 0xFF;
                            gaccum += (rmult >> 8) & 0xFF;
                            baccum += rmult & 0xFF;
                        }
                    }

                    mult = ((raccum / 9) << 16) | ((gaccum / 9) << 8) | (baccum / 9);
                }
            }
            catch (Exception x)
            {
                mult = 0xFFFFFF;
            }

            return mult;
        }
        /**
         * Step current position in given direction
         */
        @Override
        public final void stepPosition(BlockStep step)
        {
            blk = null;

            switch (step.ordinal())
            {
                case 0:
                    x++;
                    bx++;

                    if (bx == 16)   /* Next chunk? */
                    {
                        bx = 0;
                        chunkindex++;
                        if((chunkindex >= snapcnt) || (chunkindex < 0)) {
                            snap = EMPTY;
                        }
                        else {
                            snap = snaparray[chunkindex];
                        }
                    }

                    break;

                case 1:
                    y++;

                    if (y >= worldheight)
                    {
                        blk = DynmapBlockState.AIR;
                    }

                    break;

                case 2:
                    z++;
                    bz++;

                    if (bz == 16)   /* Next chunk? */
                    {
                        bz = 0;
                        chunkindex += x_dim;
                        if((chunkindex >= snapcnt) || (chunkindex < 0)) {
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

                    if (bx == -1)   /* Next chunk? */
                    {
                        bx = 15;
                        chunkindex--;
                        if((chunkindex >= snapcnt) || (chunkindex < 0)) {
                            snap = EMPTY;
                        }
                        else {
                            snap = snaparray[chunkindex];
                        }
                    }

                    break;

                case 4:
                    y--;

                    if (y < 0)
                    {
                        blk = DynmapBlockState.AIR;
                    }

                    break;

                case 5:
                    z--;
                    bz--;

                    if (bz == -1)   /* Next chunk? */
                    {
                        bz = 15;
                        chunkindex -= x_dim;
                        if((chunkindex >= snapcnt) || (chunkindex < 0)) {
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
        public BlockStep unstepPosition()
        {
            BlockStep ls = laststep;
            stepPosition(unstep[ls.ordinal()]);
            return ls;
        }
        /**
         * Unstep current position in oppisite director of given step
         */
        @Override
        public void unstepPosition(BlockStep s)
        {
            stepPosition(unstep[s.ordinal()]);
        }
        @Override
        public final void setY(int y)
        {
            if (y > this.y)
            {
                laststep = BlockStep.Y_PLUS;
            }
            else
            {
                laststep = BlockStep.Y_MINUS;
            }

            this.y = y;

            if ((y < 0) || (y >= worldheight))
            {
                blk = DynmapBlockState.AIR;
            }
            else
            {
                blk = null;
            }
        }
        @Override
        public final int getX()
        {
            return x;
        }
        @Override
        public final int getY()
        {
            return y;
        }
        @Override
        public final int getZ()
        {
            return z;
        }
        @Override
        public final DynmapBlockState getBlockTypeAt(BlockStep s)
        {
            if (s == BlockStep.Y_MINUS)
            {
                if (y > 0)
                {
                    return snap.getBlockType(bx, y - 1, bz);
                }
            }
            else if (s == BlockStep.Y_PLUS)
            {
                if (y < (worldheight - 1))
                {
                    return snap.getBlockType(bx, y + 1, bz);
                }
            }
            else
            {
                BlockStep ls = laststep;
                stepPosition(s);
                DynmapBlockState tid = snap.getBlockType(bx, y, bz);
                unstepPosition();
                laststep = ls;
                return tid;
            }

            return DynmapBlockState.AIR;
        }
        @Override
        public BlockStep getLastStep()
        {
            return laststep;
        }
        @Override
        public int getWorldHeight()
        {
            return worldheight;
        }
        @Override
        public long getBlockKey()
        {
            return (((chunkindex * worldheight) + y) << 8) | (bx << 4) | bz;
        }
        @Override
        public final boolean isEmptySection()
        {
            try
            {
                return !isSectionNotEmpty[chunkindex][y >> 4];
            }
            catch (Exception x)
            {
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
        public DynmapBlockState getBlockTypeAt(int xoff, int yoff, int zoff) {
            int xx = this.x + xoff;
            int yy = this.y + yoff;
            int zz = this.z + zoff;
            int idx = ((xx >> 4) - x_min) + (((zz >> 4) - z_min) * x_dim);
            try {
                return snaparray[idx].getBlockType(xx & 0xF, yy, zz & 0xF);
            } catch (Exception x) {
                return DynmapBlockState.AIR;
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
                return snap.getInhabitedTicks();
            } catch (Exception x) {
                return 0;
            }
        }
		@Override
		public DynmapBlockState getBlockType() {
			if (blk == null) {
				blk = snap.getBlockType(bx, y, bz);
            }
			return blk;
		}
    }

    private class OurEndMapIterator extends OurMapIterator
    {
        OurEndMapIterator(int x0, int y0, int z0)
        {
            super(x0, y0, z0);
        }
        @Override
        public final int getBlockSkyLight()
        {
            return 15;
        }
    }
    /**
     * Chunk cache for representing unloaded chunk (or air)
     */
    private static class EmptyChunk extends ChunkSnapshot
    {
        public EmptyChunk()
        {
            super(256, 0, 0, 0, 0);
        }
        /* Need these for interface, but not used */
        @Override
        public int getX()
        {
            return 0;
        }
        @Override
        public int getZ()
        {
            return 0;
        }
        @Override
        public final DynmapBlockState getBlockType(int x, int y, int z)
        {
            return DynmapBlockState.AIR;
        }
        @Override
        public final int getBlockSkyLight(int x, int y, int z)
        {
            return 15;
        }
        @Override
        public final int getBlockEmittedLight(int x, int y, int z)
        {
            return 0;
        }
        @Override
        public final int getHighestBlockYAt(int x, int z)
        {
            return 0;
        }
        @Override
        public int getBiome(int x, int z)
        {
            return -1;
        }
        @Override
        public boolean isSectionEmpty(int sy)
        {
            return true;
        }
    }

    /**
     * Chunk cache for representing generic stone chunk
     */
    private static class PlainChunk extends ChunkSnapshot
    {
        private DynmapBlockState fill;

        PlainChunk(String fill)
        {
            super(256, 0, 0, 0, 0);
            this.fill = DynmapBlockState.getBaseStateByName(fill);
        }
        /* Need these for interface, but not used */
        @Override
        public int getX()
        {
            return 0;
        }
        @Override
        public int getZ()
        {
            return 0;
        }
        @Override
        public int getBiome(int x, int z)
        {
            return -1;
        }
        @Override
        public final DynmapBlockState getBlockType(int x, int y, int z)
        {
            if (y < 64)
            {
                return fill;
            }

            return DynmapBlockState.AIR;
        }
        @Override
        public final int getBlockSkyLight(int x, int y, int z)
        {
            if (y < 64)
            {
                return 0;
            }

            return 15;
        }
        @Override
        public final int getBlockEmittedLight(int x, int y, int z)
        {
            return 0;
        }
        @Override
        public final int getHighestBlockYAt(int x, int z)
        {
            return 64;
        }
        @Override
        public boolean isSectionEmpty(int sy)
        {
            return (sy < 4);
        }
    }

    private static final EmptyChunk EMPTY = new EmptyChunk();
    private static final PlainChunk STONE = new PlainChunk(DynmapBlockState.STONE_BLOCK);
    private static final PlainChunk OCEAN = new PlainChunk(DynmapBlockState.WATER_BLOCK);


    public static void init() {
    	if (!init)
    	{
    		Field[] f = ServerChunkProvider.class.getDeclaredFields();
    		    		
    		f = ServerWorld.class.getDeclaredFields();
    		for(int i = 0; i < f.length; i++) {
    			if((updateEntityTick == null) && f[i].getType().isAssignableFrom(int.class)) {
    				updateEntityTick = f[i];
    				//Log.info("Found updateEntityTick - " + f[i].getName());
    				updateEntityTick.setAccessible(true);
    			}
    		}

    		f = ChunkManager.class.getDeclaredFields();
    		for(int i = 0; i < f.length; i++) {
    		    if((chunksToRemove == null) && (f[i].getType().equals(Map.class))) {
                    chunksToRemove = f[i];
                    //Log.info("Found chunksToRemove - " + f[i].getName());
    		        chunksToRemove.setAccessible(true);
    		    }
//    		    else if((pendingAnvilChunksCoordinates == null) && (f[i].getType().equals(it.unimi.dsi.fastutil.longs.LongSet.class))) {
//                    //Log.info("Found pendingAnvilChunksCoordinates - " + f[i].getName());
//    		        pendingAnvilChunksCoordinates = f[i];
//    		        pendingAnvilChunksCoordinates.setAccessible(true);
//    		    }
    		}
			if (updateEntityTick == null) {
				Log.severe("ERROR: cannot find updateEntityTick - dynmap cannot drive entity cleanup when no players are active");
			}

    		init = true;
    	}
    }

    /**
     * Construct empty cache
     */
    public ForgeMapChunkCache()
    {
    	init();
    }
    
    public void setChunks(ForgeWorld dw, List<DynmapChunk> chunks)
    {
        this.dw = dw;
        this.w = dw.getWorld();
        if(dw.isLoaded()) {
        	/* Check if world's provider is ServerChunkProvider */
        	AbstractChunkProvider cp = this.w.getChunkProvider();

        	if (cp instanceof ServerChunkProvider)
        	{
                cps = (ServerChunkProvider)cp;
        	}
        	else
        	{
        		Log.severe("Error: world " + dw.getName() + " has unsupported chunk provider");
        	}
        }
        else {
        	chunks = new ArrayList<DynmapChunk>();
        }
        nsect = dw.worldheight >> 4;
        this.chunks = chunks;

        /* Compute range */
        if (chunks.size() == 0)
        {
            this.x_min = 0;
            this.x_max = 0;
            this.z_min = 0;
            this.z_max = 0;
            x_dim = 1;
        }
        else
        {
            x_min = x_max = chunks.get(0).x;
            z_min = z_max = chunks.get(0).z;

            for (DynmapChunk c : chunks)
            {
                if (c.x > x_max)
                {
                    x_max = c.x;
                }

                if (c.x < x_min)
                {
                    x_min = c.x;
                }

                if (c.z > z_max)
                {
                    z_max = c.z;
                }

                if (c.z < z_min)
                {
                    z_min = c.z;
                }
            }

            x_dim = x_max - x_min + 1;
        }

        snapcnt = x_dim * (z_max-z_min+1);
        snaparray = new ChunkSnapshot[snapcnt];
        snaptile = new DynIntHashMap[snapcnt];
        isSectionNotEmpty = new boolean[snapcnt][];
        
    }

    private static boolean didError = false;
    
    public CompoundNBT readChunk(int x, int z) {
        try {
            ChunkManager acl = cps.chunkManager;

            ChunkPos coord = new ChunkPos(x, z);
            CompoundNBT rslt = acl.readChunk(coord);
            if(rslt != null) {
                rslt = rslt.getCompound("Level");
                // Don't load uncooked chunks
                String stat = rslt.getString("Status");
                ChunkStatus cs = ChunkStatus.byName(stat);
                if ((stat == null) || 
                    // Needs to be at least lighted
                    (!cs.isAtLeast(ChunkStatus.LIGHT))) {
                    rslt = null;
                }
            }
            //Log.info(String.format("loadChunk(%d,%d)=%s", x, z, (rslt != null) ? rslt.toString() : "null"));
            return rslt;
        } catch (Exception exc) {
            Log.severe(String.format("Error reading chunk: %s,%d,%d", dw.getName(), x, z), exc);
            return null;
        }
    }
    
    private Object getNBTValue(INBT v) {
        Object val = null;
        switch(v.getId()) {
            case 1: // Byte
                val = Byte.valueOf(((ByteNBT)v).getByte());
                break;
            case 2: // Short
                val = Short.valueOf(((ShortNBT)v).getShort());
                break;
            case 3: // Int
                val = Integer.valueOf(((IntNBT)v).getInt());
                break;
            case 4: // Long
                val = Long.valueOf(((LongNBT)v).getLong());
                break;
            case 5: // Float
                val = Float.valueOf(((FloatNBT)v).getFloat());
                break;
            case 6: // Double
                val = Double.valueOf(((DoubleNBT)v).getDouble());
                break;
            case 7: // Byte[]
                val = ((ByteArrayNBT)v).getByteArray();
                break;
            case 8: // String
                val = ((StringNBT)v).getString();
                break;
            case 9: // List
                ListNBT tl = (ListNBT) v;
                ArrayList<Object> vlist = new ArrayList<Object>();
                int type = tl.getTagType();
                for (int i = 0; i < tl.size(); i++) {
                    switch (type) {
                        case 5:
                            float fv = tl.getFloat(i);
                            vlist.add(fv);
                            break;
                        case 6:
                            double dv = tl.getDouble(i);
                            vlist.add(dv);
                            break;
                        case 8:
                            String sv = tl.getString(i);
                            vlist.add(sv);
                            break;
                        case 10:
                            CompoundNBT tc = tl.getCompound(i);
                            vlist.add(getNBTValue(tc));
                            break;
                        case 11:
                            int[] ia = tl.getIntArray(i);
                            vlist.add(ia);
                            break;
                    }
                }
                val = vlist;
                break;
            case 10: // Map
                CompoundNBT tc = (CompoundNBT) v;
                HashMap<String, Object> vmap = new HashMap<String, Object>();
                for (Object t : tc.keySet()) {
                    String st = (String) t;
                    INBT tg = tc.get(st);
                    vmap.put(st, getNBTValue(tg));
                }
                val = vmap;
                break;
            case 11: // Int[]
                val = ((IntArrayNBT)v).getIntArray();
                break;
        }
        return val;
    }
    
    private boolean isChunkVisible(DynmapChunk chunk) {
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
        return vis;
    }
    
    private boolean tryChunkCache(DynmapChunk chunk, boolean vis) {
        /* Check if cached chunk snapshot found */
        ChunkSnapshot ss = null;
        SnapshotRec ssr = DynmapPlugin.plugin.sscache.getSnapshot(dw.getName(), chunk.x, chunk.z, blockdata, biome, biomeraw, highesty); 
        if(ssr != null) {
            ss = ssr.ss;
            if (!vis)
            {
                if (hidestyle == HiddenChunkStyle.FILL_STONE_PLAIN)
                {
                    ss = STONE;
                }
                else if (hidestyle == HiddenChunkStyle.FILL_OCEAN)
                {
                    ss = OCEAN;
                }
                else
                {
                    ss = EMPTY;
                }
            }
            int idx = (chunk.x-x_min) + (chunk.z - z_min)*x_dim;
            snaparray[idx] = ss;
            snaptile[idx] = ssr.tileData;
        }
        return (ssr != null);
    }
    
    // Prep snapshot and add to cache
    private SnapshotRec prepChunkSnapshot(DynmapChunk chunk, CompoundNBT nbt) {
        ChunkSnapshot ss = new ChunkSnapshot(nbt, dw.worldheight);
        DynIntHashMap tileData = new DynIntHashMap();

        ListNBT tiles = nbt.getList("TileEntities", 10);
        if(tiles == null) tiles = new ListNBT();
        /* Get tile entity data */
        List<Object> vals = new ArrayList<Object>();
        for(int tid = 0; tid < tiles.size(); tid++) {
            CompoundNBT tc = tiles.getCompound(tid);
            int tx = tc.getInt("x");
            int ty = tc.getInt("y");
            int tz = tc.getInt("z");
            int cx = tx & 0xF;
            int cz = tz & 0xF;
            DynmapBlockState blk = ss.getBlockType(cx, ty, cz);
            String[] te_fields = HDBlockModels.getTileEntityFieldsNeeded(blk);
            if(te_fields != null) {
                vals.clear();
                for(String id: te_fields) {
                    INBT v = tc.get(id);  /* Get field */
                    if(v != null) {
                        Object val = getNBTValue(v);
                        if(val != null) {
                            vals.add(id);
                            vals.add(val);
                        }
                    }
                }
                if(vals.size() > 0) {
                    Object[] vlist = vals.toArray(new Object[vals.size()]);
                    tileData.put(getIndexInChunk(cx, ty, cz), vlist);
                }
            }
        }
        SnapshotRec ssr = new SnapshotRec();
        ssr.ss = ss;
        ssr.tileData = tileData;
        DynmapPlugin.plugin.sscache.putSnapshot(dw.getName(), chunk.x, chunk.z, ssr, blockdata, biome, biomeraw, highesty);
        
        return ssr;
    }
    
    /** 
     * Read NBT data from loaded chunks - needs to be called from server/world thread to be safe
     * @returns number loaded
     */
    public int getLoadedChunks() {
        int cnt = 0;
        if(!dw.isLoaded()) {
            isempty = true;
            unloadChunks();
            return 0;
        }
        ListIterator<DynmapChunk> iter = chunks.listIterator();
        while (iter.hasNext()) {
            long startTime = System.nanoTime();
            DynmapChunk chunk = iter.next();
            int chunkindex = (chunk.x-x_min) + (chunk.z - z_min)*x_dim;
            if (snaparray[chunkindex] != null) continue;    // Skip if already processed
            
            boolean vis = isChunkVisible(chunk);

            /* Check if cached chunk snapshot found */
            if (tryChunkCache(chunk, vis)) {
                endChunkLoad(startTime, ChunkStats.CACHED_SNAPSHOT_HIT);
                cnt++;
            }
            // If chunk is loaded and not being unloaded, we're grabbing its NBT data
            else if (cps.chunkExists(chunk.x, chunk.z)) {
                ChunkSnapshot ss;
                DynIntHashMap tileData;
                if (vis) {  // If visible 
                    CompoundNBT nbt = ChunkSerializer.write((ServerWorld)w, cps.getChunk(chunk.x, chunk.z, false));
                    if (nbt != null) nbt = nbt.getCompound("Level");
                    SnapshotRec ssr = prepChunkSnapshot(chunk, nbt);
                    ss = ssr.ss;
                    tileData = ssr.tileData;
                }
                else {
                    if (hidestyle == HiddenChunkStyle.FILL_STONE_PLAIN) {
                        ss = STONE;
                    }
                    else if (hidestyle == HiddenChunkStyle.FILL_OCEAN) {
                        ss = OCEAN;
                    }
                    else {
                        ss = EMPTY;
                    }
                    tileData = new DynIntHashMap();
                }
                snaparray[chunkindex] = ss;
                snaptile[chunkindex] = tileData;
                endChunkLoad(startTime, ChunkStats.LOADED_CHUNKS);
                cnt++;
            }
        }
        return cnt;
    }

    @Override
    public int loadChunks(int max_to_load)
    {
        return getLoadedChunks() + readChunks(max_to_load);
        
    }
    
    public int readChunks(int max_to_load)
    {
        if(!dw.isLoaded()) {
        	isempty = true;
        	unloadChunks();
        	return 0;
        }

        int cnt = 0;

        if (iterator == null)
        {
            iterator = chunks.listIterator();
        }

        DynmapCore.setIgnoreChunkLoads(true);

        // Load the required chunks.
        while ((cnt < max_to_load) && iterator.hasNext())
        {
            long startTime = System.nanoTime();

            DynmapChunk chunk = iterator.next();

            int chunkindex = (chunk.x-x_min) + (chunk.z - z_min)*x_dim;

            if (snaparray[chunkindex] != null) continue;    // Skip if already processed

            boolean vis = isChunkVisible(chunk);

            /* Check if cached chunk snapshot found */
            if (tryChunkCache(chunk, vis)) {
                endChunkLoad(startTime, ChunkStats.CACHED_SNAPSHOT_HIT);
            }
            else {
                CompoundNBT nbt = readChunk(chunk.x, chunk.z);
                // If read was good
                if (nbt != null) {
                    ChunkSnapshot ss;
                    DynIntHashMap tileData;
                    // If hidden
                    if (!vis) {
                        if (hidestyle == HiddenChunkStyle.FILL_STONE_PLAIN) {
                            ss = STONE;
                        }
                        else if (hidestyle == HiddenChunkStyle.FILL_OCEAN) {
                            ss = OCEAN;
                        }
                        else {
                            ss = EMPTY;
                        }
                        tileData = new DynIntHashMap();
                    }
                    else {
                        // Prep snapshot
                        SnapshotRec ssr = prepChunkSnapshot(chunk, nbt);
                        ss = ssr.ss;
                        tileData = ssr.tileData;
                    }
                    snaparray[chunkindex] = ss;
                    snaptile[chunkindex] = tileData;
                    endChunkLoad(startTime, ChunkStats.UNLOADED_CHUNKS);
                }
                else {
                    endChunkLoad(startTime, ChunkStats.UNGENERATED_CHUNKS);
                }
            }
            cnt++;
        }

        DynmapCore.setIgnoreChunkLoads(false);

        if (iterator.hasNext() == false)    /* If we're done */
        {
            isempty = true;

            /* Fill missing chunks with empty dummy chunk */
            for (int i = 0; i < snaparray.length; i++)
            {
                if (snaparray[i] == null)
                {
                    snaparray[i] = EMPTY;
                }
                else if (snaparray[i] != EMPTY)
                {
                    isempty = false;
                }
            }
        }
        return cnt;
    }
    /**
     * Test if done loading
     */
    public boolean isDoneLoading()
    {
    	if(!dw.isLoaded()) {
    		return true;
    	}
        if (iterator != null)
        {
            return !iterator.hasNext();
        }

        return false;
    }
    /**
     * Test if all empty blocks
     */
    public boolean isEmpty()
    {
        return isempty;
    }
    /**
     * Unload chunks
     */
    public void unloadChunks()
    {
        if (snaparray != null)
        {
            for (int i = 0; i < snaparray.length; i++)
            {
                snaparray[i] = null;
            }

            snaparray = null;
        }
    }
    private void initSectionData(int idx)
    {
        isSectionNotEmpty[idx] = new boolean[nsect + 1];

        if (snaparray[idx] != EMPTY)
        {
            for (int i = 0; i < nsect; i++)
            {
                if (snaparray[idx].isSectionEmpty(i) == false)
                {
                    isSectionNotEmpty[idx][i] = true;
                }
            }
        }
    }
    public boolean isEmptySection(int sx, int sy, int sz)
    {
        int idx = (sx - x_min) + (sz - z_min) * x_dim;

        if (isSectionNotEmpty[idx] == null)
        {
            initSectionData(idx);
        }

        return !isSectionNotEmpty[idx][sy];
    }

    /**
     * Get cache iterator
     */
    public MapIterator getIterator(int x, int y, int z)
    {
        if (dw.getEnvironment().equals("the_end"))
        {
            return new OurEndMapIterator(x, y, z);
        }

        return new OurMapIterator(x, y, z);
    }
    /**
     * Set hidden chunk style (default is FILL_AIR)
     */
    public void setHiddenFillStyle(HiddenChunkStyle style)
    {
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
    public boolean setChunkDataTypes(boolean blockdata, boolean biome, boolean highestblocky, boolean rawbiome)
    {
        this.biome = biome;
        this.biomeraw = rawbiome;
        this.highesty = highestblocky;
        this.blockdata = blockdata;
        return true;
    }
    @Override
    public DynmapWorld getWorld()
    {
        return dw;
    }

    static
    {
        Biome b[] = DynmapPlugin.getBiomeList();
        BiomeMap[] bm = BiomeMap.values();
        biome_to_bmap = new BiomeMap[256];

        for (int i = 0; i < biome_to_bmap.length; i++)
        {
            biome_to_bmap[i] = BiomeMap.NULL;
        }

        for (int i = 0; i < b.length; i++)
        {
        	if(b[i] == null) continue;
        	
            String bs = b[i].getTranslationKey();

            for (int j = 0; j < bm.length; j++)
            {
                if (bm[j].toString().equals(bs))
                {
                    biome_to_bmap[i] = bm[j];
                    break;
                }
            }
        }
    }
}
