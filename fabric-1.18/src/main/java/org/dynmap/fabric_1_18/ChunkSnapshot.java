package org.dynmap.fabric_1_18;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.collection.PackedIntegerArray;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.WordPackedArray;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
import org.dynmap.renderer.DynmapBlockState;
import net.fabricmc.fabric.api.util.NbtType;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * Represents a static, thread-safe snapshot of chunk of blocks
 * Purpose is to allow clean, efficient copy of a chunk data to be made, and then handed off for processing in another thread (e.g. map rendering)
 */
public class ChunkSnapshot {
    private static interface Section {
        public DynmapBlockState getBlockType(int x, int y, int z);

        public int getBlockSkyLight(int x, int y, int z);

        public int getBlockEmittedLight(int x, int y, int z);

        public boolean isEmpty();

        public int getBiome(int x, int y, int z);
    }

    private final int x, z;
    private final Section[] section;
    private final int sectionOffset;
    private final int[] hmap; // Height map
    private final long captureFulltime;
    private final int sectionCnt;
    private final long inhabitedTicks;

    private static final int BLOCKS_PER_SECTION = 16 * 16 * 16;
    private static final int BIOMES_PER_SECTION = 4 * 4 * 4;
    private static final int COLUMNS_PER_CHUNK = 16 * 16;
    private static final byte[] emptyData = new byte[BLOCKS_PER_SECTION / 2];
    private static final byte[] fullData = new byte[BLOCKS_PER_SECTION / 2];

    static {
        Arrays.fill(fullData, (byte) 0xFF);
    }

    private static class EmptySection implements Section {
        @Override
        public DynmapBlockState getBlockType(int x, int y, int z) {
            return DynmapBlockState.AIR;
        }

        @Override
        public int getBlockSkyLight(int x, int y, int z) {
            return 15;
        }

        @Override
        public int getBlockEmittedLight(int x, int y, int z) {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public int getBiome(int x, int y, int z) {
            return BiomeMap.PLAINS.getBiomeID();
        }
    }

    private static final EmptySection empty_section = new EmptySection();

    private static class StdSection implements Section {
        DynmapBlockState[] states;
        int[] biomes;
        byte[] skylight;
        byte[] emitlight;

        public StdSection() {
            states = new DynmapBlockState[BLOCKS_PER_SECTION];
            biomes = new int[BIOMES_PER_SECTION];
            Arrays.fill(biomes, BiomeMap.PLAINS.getBiomeID());
            Arrays.fill(states, DynmapBlockState.AIR);
            skylight = emptyData;
            emitlight = emptyData;
        }

        @Override
        public DynmapBlockState getBlockType(int x, int y, int z) {
            return states[((y & 0xF) << 8) | (z << 4) | x];
        }

        @Override
        public int getBlockSkyLight(int x, int y, int z) {
            int off = ((y & 0xF) << 7) | (z << 3) | (x >> 1);
            return (skylight[off] >> (4 * (x & 1))) & 0xF;
        }

        @Override
        public int getBlockEmittedLight(int x, int y, int z) {
            int off = ((y & 0xF) << 7) | (z << 3) | (x >> 1);
            return (emitlight[off] >> (4 * (x & 1))) & 0xF;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public int getBiome(int x, int y, int z) {
            int off = (((y & 0xF) >> 2) << 4) | ((z >> 2) << 2) | (x >> 2);
            return biomes[off];
        }
    }

    /**
     * Construct empty chunk snapshot
     *
     * @param x
     * @param z
     */
    public ChunkSnapshot(int worldheight, int x, int z, long captime, long inhabitedTime) {
        this.x = x;
        this.z = z;
        this.captureFulltime = captime;
        this.sectionCnt = worldheight / 16;
        /* Allocate arrays indexed by section */
        this.section = new Section[this.sectionCnt + 1];
        this.sectionOffset = 0;
        /* Fill with empty data */
        for (int i = 0; i <= this.sectionCnt; i++) {
            this.section[i] = empty_section;
        }

        /* Create empty height map */
        this.hmap = new int[COLUMNS_PER_CHUNK];

        this.inhabitedTicks = inhabitedTime;
    }

    public static class StateListException extends Exception {
        private static final long serialVersionUID = 1L;
        private static boolean loggedOnce = false;

        public StateListException(int x, int z, int actualLength, int expectedLength, int expectedLegacyLength) {
            if (Log.verbose || !loggedOnce) {
                loggedOnce = true;
                Log.info("Skipping chunk at x=" + x + ",z=" + z + ". Expected statelist of length " + expectedLength + " or " + expectedLegacyLength + " but got " + actualLength + ". This can happen if the chunk was not yet converted to the 1.16 format which can be fixed by visiting the chunk.");
                if (!Log.verbose) {
                    Log.info("You will only see this message once. Turn on verbose logging in the configuration to see all messages.");
                }
            }
        }
    }

    public ChunkSnapshot(NbtCompound nbt, int worldheight) throws StateListException {
        this.x = nbt.getInt("xPos");
        this.z = nbt.getInt("zPos");
        this.captureFulltime = 0;
        this.sectionCnt = worldheight / 16;
        if (nbt.contains("InhabitedTime")) {
            this.inhabitedTicks = nbt.getLong("InhabitedTime");
        } else {
            this.inhabitedTicks = 0;
        }
        this.hmap = new int[COLUMNS_PER_CHUNK];
        if (nbt.contains("Heightmaps")) {
            NbtCompound hmaps = nbt.getCompound("Heightmaps");
            long[] phmap = hmaps.getLongArray("WORLD_SURFACE");
            PackedIntegerArray hmap = new PackedIntegerArray((phmap.length * 64) / COLUMNS_PER_CHUNK, COLUMNS_PER_CHUNK,
                    phmap);
            for (int i = 0; i < this.hmap.length; i++) {
                this.hmap[i] = hmap.get(i);
            }
        }
        /* Allocate arrays indexed by section */
        LinkedList<Section> sections = new LinkedList<Section>();
        int sectoff = 0; // Default to zero
        int sectcnt = 0;
        /* Fill with empty data */
        for (int i = 0; i <= this.sectionCnt; i++) {
            sections.add(empty_section);
            sectcnt++;
        }
        /* Get sections */
        NbtList sect = nbt.getList("sections", 10);
        for (int i = 0; i < sect.size(); i++) {
            NbtCompound sec = sect.getCompound(i);
            int secnum = sec.getByte("Y");
            // Beyond end - extend up
            while (secnum >= (sectcnt - sectoff)) {
        		sections.addLast(empty_section);	// Pad with empty
        		sectcnt++;
            }
            // Negative - see if we need to extend sectionOffset
        	while ((secnum + sectoff) < 0) {
        		sections.addFirst(empty_section);	// Pad with empty
        		sectoff++;
        		sectcnt++;
        	}
            //System.out.println("section(" + secnum + ")=" + sec.asString());
            // Create normal section to initialize
            StdSection cursect = new StdSection();
            sections.set(secnum + sectoff, cursect);
            DynmapBlockState[] states = cursect.states;
            DynmapBlockState[] palette = null;
            int[] biomes = cursect.biomes;
            // If we've got palette and block states list, process non-empty section
            if (sec.contains("block_states", NbtType.COMPOUND)) {
                NbtCompound bstat = sec.getCompound("block_states");
                NbtList plist = bstat.getList("palette", 10);
                long[] statelist = bstat.getLongArray("data");
                palette = new DynmapBlockState[plist.size()];
                for (int pi = 0; pi < plist.size(); pi++) {
                    NbtCompound tc = plist.getCompound(pi);
                    String pname = tc.getString("Name");
                    if (tc.contains("Properties")) {
                        StringBuilder statestr = new StringBuilder();
                        NbtCompound prop = tc.getCompound("Properties");
                        for (String pid : prop.getKeys()) {
                            if (statestr.length() > 0) statestr.append(',');
                            statestr.append(pid).append('=').append(prop.get(pid).asString());
                        }
                        palette[pi] = DynmapBlockState.getStateByNameAndState(pname, statestr.toString());
                    }
                    if (palette[pi] == null) {
                        palette[pi] = DynmapBlockState.getBaseStateByName(pname);
                    }
                    if (palette[pi] == null) {
                        palette[pi] = DynmapBlockState.AIR;
                    }
                }

                if (statelist.length > 0) {
                    PackedIntegerArray db = null;
                    WordPackedArray dbp = null;

                    int bitsperblock = (statelist.length * 64) / 4096;
                    int expectedStatelistLength = (4096 + (64 / bitsperblock) - 1) / (64 / bitsperblock);
                    if (statelist.length == expectedStatelistLength) {
                        db = new PackedIntegerArray(bitsperblock, 4096, statelist);
                    } else {
                        int expectedLegacyStatelistLength = MathHelper.roundUpToMultiple(bitsperblock * 4096, 64) / 64;
                        if (statelist.length == expectedLegacyStatelistLength) {
                            dbp = new WordPackedArray(bitsperblock, 4096, statelist);
                        } else {
                            throw new StateListException(x, z, statelist.length, expectedStatelistLength,
                                    expectedLegacyStatelistLength);
                        }
                    }

                    if (bitsperblock > 8) { // Not palette
                        for (int j = 0; j < 4096; j++) {
                            int v = db != null ? db.get(j) : dbp.get(j);
                            states[j] = DynmapBlockState.getStateByGlobalIndex(v);
                        }
                    } else {
                        for (int j = 0; j < 4096; j++) {
                            int v = db != null ? db.get(j) : dbp.get(j);
                            states[j] = (v < palette.length) ? palette[v] : DynmapBlockState.AIR;
                        }
                    }
                }
            }
            if (sec.contains("BlockLight")) {
                cursect.emitlight = sec.getByteArray("BlockLight");
            }
            if (sec.contains("SkyLight")) {
                cursect.skylight = sec.getByteArray("SkyLight");
            }
            if (sec.contains("biomes")) {
                NbtCompound nbtbiomes = sec.getCompound("biomes");
                long[] bdataPacked = nbtbiomes.getLongArray("data");
                NbtList bpalette = nbtbiomes.getList("palette", NbtType.STRING);
                PackedIntegerArray bdata = null;
                if (bdataPacked.length > 0)
                    bdata = new PackedIntegerArray(bdataPacked.length, 64, bdataPacked);
                for (int j = 0; j < 64; j++) {
                    int b = bdata != null ? bdata.get(j) : 0;
                    biomes[j] = b < bpalette.size() ? BiomeMap.byBiomeName(bpalette.getString(b)).getBiomeID() : -1;
                }
            }
        }
        // Finalize sections array
        this.section = sections.toArray(new Section[sections.size()]);
        this.sectionOffset = sectoff;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public DynmapBlockState getBlockType(int x, int y, int z)
    {    	
    	int idx = (y >> 4) + sectionOffset;
    	if ((idx < 0) || (idx >= section.length)) return DynmapBlockState.AIR;
        return section[idx].getBlockType(x, y, z);
    }

    public int getBlockSkyLight(int x, int y, int z)
    {
    	int idx = (y >> 4) + sectionOffset;
    	if ((idx < 0) || (idx >= section.length)) return 15;
        return section[idx].getBlockSkyLight(x, y, z);
    }

    public int getBlockEmittedLight(int x, int y, int z)
    {
    	int idx = (y >> 4) + sectionOffset;
    	if ((idx < 0) || (idx >= section.length)) return 0;
        return section[idx].getBlockEmittedLight(x, y, z);
    }

    public int getHighestBlockYAt(int x, int z) {
        return hmap[z << 4 | x];
    }

    public int getBiome(int x, int z) {
        int y = getHighestBlockYAt(x, z);
        final int idx = (y >> 4) + sectionOffset;
        if ((idx < 0) || (idx >= section.length))
            return 0;
        return section[idx].getBiome(x % 16, y % 16, z % 16);
    }

    public final long getCaptureFullTime() {
        return captureFulltime;
    }

    public boolean isSectionEmpty(int sy)
    {
    	int idx = sy + sectionOffset;
    	if ((idx < 0) || (idx >= section.length)) return true;
        return section[idx].isEmpty();
    }

    public long getInhabitedTicks() {
        return inhabitedTicks;
    }
}
