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

	private GenericChunk parseChunkFromNBT(NBTTagCompound nbt) {
		if ((nbt != null) && nbt.e("Level")) {
			nbt = nbt.p("Level");
		}
		if (nbt == null) return null;
		// Start generic chunk builder
		GenericChunk.Builder bld = new GenericChunk.Builder(dw.minY,  dw.worldheight);
		int cx = nbt.h("xPos");
		int cz = nbt.h("zPos");
		bld.coords(cx, cz);
        if (nbt.e("InhabitedTime")) {
        	bld.inhabitedTicks(nbt.i("InhabitedTime"));
        }
        // Check for 2D or old 3D biome data from chunk level: need these when we build old sections
        List<BiomeMap[]> old3d = null;	// By section, then YZX list
        BiomeMap[] old2d = null;
        if (nbt.e("Biomes")) {
            int[] bb = nbt.n("Biomes");
        	if (bb != null) {
        		// If v1.15+ format
        		if (bb.length > 256) {
        			old3d = new ArrayList<BiomeMap[]>();
        			// Get 4 x 4 x 4 list for each section
        			for (int sect = 0; sect < (bb.length / 64); sect++) {
        				BiomeMap smap[] = new BiomeMap[64];
        				for (int i = 0; i < 64; i++) {
        					smap[i] = BiomeMap.byBiomeID(bb[sect*64 + i]);
        				}
        				old3d.add(smap);
        			}
        	    }
        	    else { // Else, older chunks
        	    	old2d = new BiomeMap[256];
        	        for (int i = 0; i < bb.length; i++) {
    					old2d[i] = BiomeMap.byBiomeID(bb[i]);
        	        }
        	    }
            }
        }
        // Start section builder
        GenericChunkSection.Builder sbld = new GenericChunkSection.Builder();
        /* Get sections */
        NBTTagList sect = nbt.e("sections") ? nbt.c("sections", 10) : nbt.c("Sections", 10);
        for (int i = 0; i < sect.size(); i++) {
            NBTTagCompound sec = sect.a(i);
            int secnum = sec.h("Y");
            
            DynmapBlockState[] palette = null;
            // If we've got palette and block states list, process non-empty section
            if (sec.b("Palette", 9) && sec.b("BlockStates", 12)) {
            	NBTTagList plist = sec.c("Palette", 10);
            	long[] statelist = sec.o("BlockStates");
            	palette = new DynmapBlockState[plist.size()];
            	for (int pi = 0; pi < plist.size(); pi++) {
            		NBTTagCompound tc = plist.a(pi);
            		String pname = tc.l("Name");
                    if (tc.e("Properties")) {
                        StringBuilder statestr = new StringBuilder();
                        NBTTagCompound prop = tc.p("Properties");
                        for (String pid : prop.d()) {
                            if (statestr.length() > 0) statestr.append(',');
                            statestr.append(pid).append('=').append(prop.c(pid).e_());
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
            	int recsperblock = (4096 + statelist.length - 1) / statelist.length;
            	int bitsperblock = 64 / recsperblock;
            	DataBits db = null;
            	DataBitsPacked dbp = null;
            	try {
            		db = new SimpleBitStorage(bitsperblock, 4096, statelist);
            	} catch (Exception x) {	// Handle legacy encoded
	            	bitsperblock = (statelist.length * 64) / 4096;
            		dbp = new DataBitsPacked(bitsperblock, 4096, statelist);
            	}
                if (bitsperblock > 8) {	// Not palette
                    for (int j = 0; j < 4096; j++) {
                    	int v = (dbp != null) ? dbp.getAt(j) : db.a(j);
                    	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, DynmapBlockState.getStateByGlobalIndex(v));
                    }
                }
                else {
                    for (int j = 0; j < 4096; j++) {
                    	int v = (dbp != null) ? dbp.getAt(j) : db.a(j);
                        DynmapBlockState bs = (v < palette.length) ? palette[v] : DynmapBlockState.AIR;
                    	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, bs);
                    }
                }
			}

            else if (sec.e("block_states")) {	// 1.18
            	NBTTagCompound block_states = sec.p("block_states");
            	// If we've block state data, process non-empty section
            	if (block_states.b("data", 12)) {
            		long[] statelist = block_states.o("data");
            		NBTTagList plist = block_states.c("palette", 10);
            		palette = new DynmapBlockState[plist.size()];
            		for (int pi = 0; pi < plist.size(); pi++) {
            			NBTTagCompound tc = plist.a(pi);
            			String pname = tc.l("Name");
            			if (tc.e("Properties")) {
            				StringBuilder statestr = new StringBuilder();
            				NBTTagCompound prop = tc.p("Properties");
            				for (String pid : prop.d()) {
            					if (statestr.length() > 0) statestr.append(',');
            					statestr.append(pid).append('=').append(prop.c(pid).e_());
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
        			SimpleBitStorage db = null;
        			DataBitsPacked dbp = null;

        			int bitsperblock = (statelist.length * 64) / 4096;
        			int expectedStatelistLength = (4096 + (64 / bitsperblock) - 1) / (64 / bitsperblock);
        			if (statelist.length == expectedStatelistLength) {
        				db = new SimpleBitStorage(bitsperblock, 4096, statelist);
        			}
        			else {
		            	bitsperblock = (statelist.length * 64) / 4096;
	            		dbp = new DataBitsPacked(bitsperblock, 4096, statelist);
        			}
        			if (bitsperblock > 8) {    // Not palette
        				for (int j = 0; j < 4096; j++) {
        					int v = db != null ? db.a(j) : dbp.getAt(j);
                        	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, DynmapBlockState.getStateByGlobalIndex(v));
        				}
        			}
        			else {
        				for (int j = 0; j < 4096; j++) {
        					int v = db != null ? db.a(j) : dbp.getAt(j);
        					DynmapBlockState bs = (v < palette.length) ? palette[v] : DynmapBlockState.AIR;
                        	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, bs);
        				}
        			}
            	}
            }
            if (sec.e("BlockLight")) {
            	sbld.emittedLight(sec.m("BlockLight"));
            }
			if (sec.e("SkyLight")) {
				sbld.skyLight(sec.m("SkyLight"));
			}
			// If section biome palette
			if (sec.e("biomes")) {
                NBTTagCompound nbtbiomes = sec.p("biomes");
                long[] bdataPacked = nbtbiomes.o("data");
                NBTTagList bpalette = nbtbiomes.c("palette", 8);
                SimpleBitStorage bdata = null;
                if (bdataPacked.length > 0)
                    bdata = new SimpleBitStorage(bdataPacked.length, 64, bdataPacked);
                for (int j = 0; j < 64; j++) {
                    int b = bdata != null ? bdata.a(j) : 0;
                    String rl = bpalette.j(b);
                    BiomeMap bm = BiomeMap.byBiomeResourceLocation(rl);
                    sbld.xyzBiome(j & 0x3, (j & 0x30) >> 4, (j & 0xC) >> 2, bm);
                }
            }
			else {	// Else, apply legacy biomes
				if (old3d != null) {
					BiomeMap m[] = old3d.get((secnum > 0) ? ((secnum < old3d.size()) ? secnum : old3d.size()-1) : 0);
					if (m != null) {
		                for (int j = 0; j < 64; j++) {
		                    sbld.xyzBiome(j & 0x3, (j & 0x30) >> 4, (j & 0xC) >> 2, m[j]);
		                }
					}
				}
				else if (old2d != null) {
	                for (int j = 0; j < 256; j++) {
	                    sbld.xzBiome(j & 0xF, (j & 0xF0) >> 4, old2d[j]);
	                }					
				}
			}
			// Finish and add section
			bld.addSection(secnum, sbld.build());
			sbld.reset();
        }
		return bld.build();
	}
	
	// Load generic chunk from existing and already loaded chunk
	protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
        CraftWorld cw = (CraftWorld) w;
        NBTTagCompound nbt = null;
        if (cw.isChunkLoaded(chunk.x, chunk.z)) {
            Chunk c = cw.getHandle().getChunkIfLoaded(chunk.x,  chunk.z);
            if ((c != null) && c.o) {	// c.loaded
				nbt = ChunkRegionLoader.a(cw.getHandle(), c);
            }
        	if (nbt.e("Level")) {
        		nbt = nbt.p("Level");
        	}
            if (nbt != null) {
                String stat = nbt.l("Status");
				ChunkStatus cs = ChunkStatus.a(stat);
                if ((stat == null) || (!cs.b(ChunkStatus.l))) {	// ChunkStatus.LIGHT
                    nbt = null;
                }
            }
        }
    	return parseChunkFromNBT(nbt);
	}
	// Load generic chunk from unloaded chunk
	protected GenericChunk loadChunk(DynmapChunk chunk) {
        CraftWorld cw = (CraftWorld) w;
        NBTTagCompound nbt = null;
        ChunkCoordIntPair cc = new ChunkCoordIntPair(chunk.x, chunk.z);
        try {
            nbt = cw.getHandle().k().a.f(cc);	// playerChunkMap
        } catch (IOException iox) {
        }
        if (nbt != null) {
        	// See if we have Level - unwrap this if so
        	if (nbt.e("Level")) {
        		nbt = nbt.p("Level");
        	}
            if (nbt != null) {
            	String stat = nbt.l("Status");
            	if ((stat == null) || (stat.equals("full") == false)) {
                    nbt = null;
            	}
            }
        }
    	return parseChunkFromNBT(nbt);
	}

	public void setChunks(BukkitWorld dw, List<DynmapChunk> chunks) {
		this.w = dw.getWorld();
		super.setChunks(dw, chunks);
	}
	
    private NBTTagCompound fetchLoadedChunkNBT(World w, int x, int z) {
        CraftWorld cw = (CraftWorld) w;
        NBTTagCompound nbt = null;
        if (cw.isChunkLoaded(x, z)) {
            Chunk c = cw.getHandle().getChunkIfLoaded(x,  z);
            if ((c != null) && c.o) {	// c.loaded
				nbt = ChunkRegionLoader.a(cw.getHandle(), c);
            }
        }
        if (nbt != null) {
        	if (nbt.e("Level")) {
        		nbt = nbt.p("Level");
        	}
            if (nbt != null) {
                String stat = nbt.l("Status");
				ChunkStatus cs = ChunkStatus.a(stat);
                if ((stat == null) || (!cs.b(ChunkStatus.l))) {	// ChunkStatus.LIGHT
                    nbt = null;
                }
            }
        }
        return nbt;
    }    
}
