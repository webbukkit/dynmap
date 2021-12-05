package org.dynmap.forge_1_18;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericChunkSection;
import org.dynmap.common.chunk.GenericMapChunkCache;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.utils.DynIntHashMap;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.DataBitsPacked;
import org.dynmap.utils.VisibilityLimit;

import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since
 * rendering is off server thread
 */
public class ForgeMapChunkCache extends GenericMapChunkCache {
	private ServerLevel w;
	private ServerChunkCache cps;
	/**
	 * Construct empty cache
	 */
	public ForgeMapChunkCache(GenericChunkCache cc) {
		super(cc);
		init();
	}

	private GenericChunk parseChunkFromNBT(CompoundTag nbt) {
		if ((nbt != null) && nbt.contains("Level")) {
			nbt = nbt.getCompound("Level");
		}
		if (nbt == null) return null;
		// Start generic chunk builder
		GenericChunk.Builder bld = new GenericChunk.Builder(dw.minY,  dw.worldheight);
		bld.coords(nbt.getInt("xPos"), nbt.getInt("zPos"));
        if (nbt.contains("InhabitedTime")) {
        	bld.inhabitedTicks(nbt.getLong("InhabitedTime"));
        }
        // Check for 2D or old 3D biome data from chunk level: need these when we build old sections
        List<BiomeMap[]> old3d = null;	// By section, then YZX list
        BiomeMap[] old2d = null;
        if (nbt.contains("Biomes")) {
            int[] bb = nbt.getIntArray("Biomes");
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
        ListTag sect = nbt.contains("sections") ? nbt.getList("sections", 10) : nbt.getList("Sections", 10);
        for (int i = 0; i < sect.size(); i++) {
            CompoundTag sec = sect.getCompound(i);
            int secnum = sec.getByte("Y");
            
            DynmapBlockState[] palette = null;
            // If we've got palette and block states list, process non-empty section
            if (sec.contains("Palette", 9) && sec.contains("BlockStates", 12)) {
                ListTag plist = sec.getList("Palette", 10);
                long[] statelist = sec.getLongArray("BlockStates");
                palette = new DynmapBlockState[plist.size()];
                for (int pi = 0; pi < plist.size(); pi++) {
                    CompoundTag tc = plist.getCompound(pi);
                    String pname = tc.getString("Name");
                    if (tc.contains("Properties")) {
                        StringBuilder statestr = new StringBuilder();
                        CompoundTag prop = tc.getCompound("Properties");
                        for (String pid : prop.getAllKeys()) {
                            if (statestr.length() > 0) statestr.append(',');
                            statestr.append(pid).append('=').append(prop.get(pid).getAsString());
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
            	BitStorage db = null;
            	DataBitsPacked dbp = null;
            	try {
            		db = new SimpleBitStorage(bitsperblock, 4096, statelist);
            	} catch (Exception x) {	// Handle legacy encoded
	            	bitsperblock = (statelist.length * 64) / 4096;
            		dbp = new DataBitsPacked(bitsperblock, 4096, statelist);
            	}
                if (bitsperblock > 8) {	// Not palette
                    for (int j = 0; j < 4096; j++) {
                    	int v = (dbp != null) ? dbp.getAt(j) : db.get(j);
                    	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, DynmapBlockState.getStateByGlobalIndex(v));
                    }
                }
                else {
                    for (int j = 0; j < 4096; j++) {
                    	int v = (dbp != null) ? dbp.getAt(j) : db.get(j);
                        DynmapBlockState bs = (v < palette.length) ? palette[v] : DynmapBlockState.AIR;
                    	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, bs);
                    }
                }
            }
            else if (sec.contains("block_states")) {	// 1.18
            	CompoundTag block_states = sec.getCompound("block_states");
            	// If we've block state data, process non-empty section
            	if (block_states.contains("data", 12)) {
            		long[] statelist = block_states.getLongArray("data");
            		ListTag plist = block_states.getList("palette", 10);
            		palette = new DynmapBlockState[plist.size()];
            		for (int pi = 0; pi < plist.size(); pi++) {
            			CompoundTag tc = plist.getCompound(pi);
            			String pname = tc.getString("Name");
            			if (tc.contains("Properties")) {
            				StringBuilder statestr = new StringBuilder();
            				CompoundTag prop = tc.getCompound("Properties");
            				for (String pid : prop.getAllKeys()) {
            					if (statestr.length() > 0) statestr.append(',');
            					statestr.append(pid).append('=').append(prop.get(pid).getAsString());
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
        					int v = db != null ? db.get(j) : dbp.getAt(j);
                        	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, DynmapBlockState.getStateByGlobalIndex(v));
        				}
        			}
        			else {
        				for (int j = 0; j < 4096; j++) {
        					int v = db != null ? db.get(j) : dbp.getAt(j);
        					DynmapBlockState bs = (v < palette.length) ? palette[v] : DynmapBlockState.AIR;
                        	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, bs);
        				}
        			}
            	}
            }
            if (sec.contains("BlockLight")) {
            	sbld.emittedLight(sec.getByteArray("BlockLight"));
            }
            if (sec.contains("SkyLight")) {
            	sbld.skyLight(sec.getByteArray("SkyLight"));
            }
			// If section biome palette
			if (sec.contains("biomes")) {
                CompoundTag nbtbiomes = sec.getCompound("biomes");
                long[] bdataPacked = nbtbiomes.getLongArray("data");
                ListTag bpalette = nbtbiomes.getList("palette", 8);
                SimpleBitStorage bdata = null;
                if (bdataPacked.length > 0)
                    bdata = new SimpleBitStorage(bdataPacked.length, 64, bdataPacked);
                for (int j = 0; j < 64; j++) {
                    int b = bdata != null ? bdata.get(j) : 0;
                    sbld.xyzBiome(j & 0x3, (j & 0x30) >> 4, (j & 0xC) >> 2, BiomeMap.byBiomeResourceLocation(bpalette.getString(b)));
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
		GenericChunk gc = null;
		ChunkAccess ch = cps.getChunk(chunk.x, chunk.z, ChunkStatus.FULL, false);
		if (ch != null) {
			CompoundTag nbt = ChunkSerializer.write(w, ch);
			if ((nbt != null) && nbt.contains("Level")) {
				nbt = nbt.getCompound("Level");
			}
			if (nbt != null) {
				gc = parseChunkFromNBT(nbt);
			}
		}
		return gc;
	}
	// Load generic chunk from unloaded chunk
	protected GenericChunk loadChunk(DynmapChunk chunk) {
		GenericChunk gc = null;
		CompoundTag nbt = readChunk(chunk.x, chunk.z);
		// If read was good
		if (nbt != null) {
			if ((nbt != null) && nbt.contains("Level")) {
				nbt = nbt.getCompound("Level");
			}
			if (nbt != null) {
				gc = parseChunkFromNBT(nbt);
			}
		}
		return gc;
	}

	public void setChunks(ForgeWorld dw, List<DynmapChunk> chunks) {
		this.w = dw.getWorld();
		if (dw.isLoaded()) {
			/* Check if world's provider is ServerChunkProvider */
			cps = this.w.getChunkSource();
		}
		super.setChunks(dw, chunks);
	}

	private CompoundTag readChunk(int x, int z) {
		try {
			CompoundTag rslt = cps.chunkMap.readChunk(new ChunkPos(x, z));
			if (rslt != null) {
				if (rslt.contains("Level")) {
					rslt = rslt.getCompound("Level");
				}
				// Don't load uncooked chunks
				String stat = rslt.getString("Status");
				ChunkStatus cs = ChunkStatus.byName(stat);
				if ((stat == null) ||
				// Needs to be at least lighted
						(!cs.isOrAfter(ChunkStatus.LIGHT))) {
					rslt = null;
				}
			}
			// Log.info(String.format("loadChunk(%d,%d)=%s", x, z, (rslt != null) ?
			// rslt.toString() : "null"));
			return rslt;
		} catch (Exception exc) {
			Log.severe(String.format("Error reading chunk: %s,%d,%d", dw.getName(), x, z), exc);
			return null;
		}
	}
}
