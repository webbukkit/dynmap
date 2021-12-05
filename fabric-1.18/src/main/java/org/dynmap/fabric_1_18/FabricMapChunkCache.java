package org.dynmap.fabric_1_18;

import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.collection.PackedIntegerArray;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.WordPackedArray;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.ChunkStatus;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkSection;
import org.dynmap.common.chunk.GenericMapChunkCache;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.utils.*;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class FabricMapChunkCache extends GenericMapChunkCache {
    private World w;
    private ServerChunkManager cps;

    /**
     * Construct empty cache
     */
    public FabricMapChunkCache(DynmapPlugin plugin) {
    	super(plugin.sscache);
    }

    public void setChunks(FabricWorld dw, List<DynmapChunk> chunks) {
        this.w = dw.getWorld();
        if (dw.isLoaded()) {
            /* Check if world's provider is ServerChunkManager */
            ChunkManager cp = this.w.getChunkManager();

            if (cp instanceof ServerChunkManager) {
                cps = (ServerChunkManager) cp;
            } else {
                Log.severe("Error: world " + dw.getName() + " has unsupported chunk provider");
            }
        } 
        super.setChunks(dw, chunks);
    }

    private NbtCompound readChunk(int x, int z) {
        try {
            ThreadedAnvilChunkStorage acl = cps.threadedAnvilChunkStorage;

            ChunkPos coord = new ChunkPos(x, z);
            NbtCompound rslt = acl.getNbt(coord);
            if (rslt != null) {
                // Don't load uncooked chunks
                String stat = rslt.getString("Status");
                ChunkStatus cs = ChunkStatus.byId(stat);
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

	private GenericChunk parseChunkFromNBT(NbtCompound nbt) {
		if ((nbt != null) && nbt.contains("Level")) {
			nbt = nbt.getCompound("Level");
		}
		if (nbt == null) return null;
		// Start generic chunk builder
		GenericChunk.Builder bld = new GenericChunk.Builder(dw.minY,  dw.worldheight);
		int x = nbt.getInt("xPos");
		int z = nbt.getInt("zPos");
		bld.coords(x, z);
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
        NbtList sect = nbt.contains("sections") ? nbt.getList("sections", 10) : nbt.getList("Sections", 10);
        for (int i = 0; i < sect.size(); i++) {
            NbtCompound sec = sect.getCompound(i);
            int secnum = sec.getByte("Y");
            
            DynmapBlockState[] palette = null;
            // If we've got palette and block states list, process non-empty section
            if (sec.contains("Palette", 9) && sec.contains("BlockStates", 12)) {
                NbtList plist = sec.getList("Palette", 10);
                long[] statelist = sec.getLongArray("BlockStates");
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
		            	bitsperblock = (statelist.length * 64) / 4096;
	            		dbp = new WordPackedArray(bitsperblock, 4096, statelist);
                    }
                }

                if (bitsperblock > 8) { // Not palette
                    for (int j = 0; j < 4096; j++) {
                        int v = db != null ? db.get(j) : dbp.get(j);
                    	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, DynmapBlockState.getStateByGlobalIndex(v));
                    }
                } else {
                    for (int j = 0; j < 4096; j++) {
                        int v = db != null ? db.get(j) : dbp.get(j);
                        DynmapBlockState bs = (v < palette.length) ? palette[v] : DynmapBlockState.AIR;
                    	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, bs);
                    }
                }
            }
            else if (sec.contains("block_states")) {	// 1.18
            	NbtCompound block_states = sec.getCompound("block_states");
            	// If we've block state data, process non-empty section
            	if (block_states.contains("data", 12)) {
            		long[] statelist = block_states.getLongArray("data");
            		NbtList plist = block_states.getList("palette", 10);
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
    		            	bitsperblock = (statelist.length * 64) / 4096;
    	            		dbp = new WordPackedArray(bitsperblock, 4096, statelist);
                        }
                    }

                    if (bitsperblock > 8) { // Not palette
                        for (int j = 0; j < 4096; j++) {
                            int v = db != null ? db.get(j) : dbp.get(j);
                        	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, DynmapBlockState.getStateByGlobalIndex(v));
                        }
                    } else {
                        for (int j = 0; j < 4096; j++) {
                            int v = db != null ? db.get(j) : dbp.get(j);
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
                NbtCompound nbtbiomes = sec.getCompound("biomes");
                long[] bdataPacked = nbtbiomes.getLongArray("data");
                NbtList bpalette = nbtbiomes.getList("palette", 8);
                PackedIntegerArray bdata = null;
                if (bdataPacked.length > 0)
                    bdata = new PackedIntegerArray(bdataPacked.length, 64, bdataPacked);
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
        if (cps.isChunkLoaded(chunk.x, chunk.z)) {
            DynIntHashMap tileData;
            NbtCompound nbt = null;
            try {
                nbt = ChunkSerializer.serialize((ServerWorld) w, cps.getWorldChunk(chunk.x, chunk.z, false));
            } catch (NullPointerException e) {
                // TODO: find out why this is happening and why it only seems to happen since 1.16.2
                Log.severe("ChunkSerializer.serialize threw a NullPointerException", e);
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
        NbtCompound nbt = readChunk(chunk.x, chunk.z);
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
}
