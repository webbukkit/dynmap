package org.dynmap.common.chunk;

import java.util.Arrays;

import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.common.BiomeMap;

// Generic chunk representation
public class GenericChunk {
	public final int cx, cz;	// Chunk coord (world coord / 16)
	public final GenericChunkSection[] sections;
	public final int cy_min;	// CY value of first section in sections list (index = (Y >> 4) - cy_min
	public final long inhabitedTicks;
	public final int dataVersion;	// Version of chunk data loaded
	public final String chunkStatus;	// Chunk status of loaded chunk
	
	private GenericChunk(int cx, int cz, int cy_min, GenericChunkSection[] sections, long inhabTicks, int dataversion, String chunkstatus) {
		this.cx = cx;
		this.cz = cz;
		this.inhabitedTicks = inhabTicks;
		this.dataVersion = dataversion;
		this.chunkStatus = chunkstatus;
		this.sections = new GenericChunkSection[sections.length + 2];	// Add one empty at top and bottom
		this.cy_min = cy_min - 1;	// Include empty at bottom
		Arrays.fill(this.sections, GenericChunkSection.EMPTY);	// Fill all spots with empty, including pad on bottom/top
		for (int off = 0; off < sections.length; off++) {
			if (sections[off] != null) {	// If defined, set the section
				this.sections[off+1] = sections[off];
			}
		}
	}
	// Get section for given block Y coord
	public final GenericChunkSection getSection(int y) {
		try {
			return this.sections[(y >> 4) - cy_min];
		} catch (IndexOutOfBoundsException ioobx) {	// Builder and padding should be avoiding this, but be safe
			return GenericChunkSection.EMPTY;
		}
	}
	
    public final DynmapBlockState getBlockType(int x, int y, int z) {    	
    	return getSection(y).blocks.getBlock(x, y, z);
    }
    public final DynmapBlockState getBlockType(GenericChunkPos pos) {    	
    	return getSection(pos.y).blocks.getBlock(pos);
    }
    public final int getBlockSkyLight(int x, int y, int z) {
    	return getSection(y).sky.getLight(x, y, z);
    }
    public final int getBlockSkyLight(GenericChunkPos pos) {
    	return getSection(pos.y).sky.getLight(pos);
    }
    public final int getBlockEmittedLight(int x, int y, int z) {
    	return getSection(y).emitted.getLight(x, y, z);
    }
    public final int getBlockEmittedLight(GenericChunkPos pos) {
    	return getSection(pos.y).emitted.getLight(pos);
    }
    public final BiomeMap getBiome(int x, int y, int z) {
    	return getSection(y).biomes.getBiome(x, y, z);
    }
    public final BiomeMap getBiome(GenericChunkPos pos) {
    	return getSection(pos.y).biomes.getBiome(pos);
    }
    public final boolean isSectionEmpty(int cy) {
    	return getSection(cy << 4).isEmpty;
    }
    public final long getInhabitedTicks() {
        return inhabitedTicks;
    }
	public String toString() {
		return String.format("chunk(%d,%d:%s,off=%d", cx, cz, Arrays.deepToString((sections)), cy_min);
	}

    // Generic empty (coordinates are wrong, but safe otherwise
    public static final GenericChunk EMPTY = new GenericChunk(0, 0, -4, new GenericChunkSection[24], 0, 0, null);
    
    // Builder for fabricating finalized chunk
    public static class Builder {
    	int x;
    	int z;
    	int y_min;
    	GenericChunkSection[] sections;
    	long inhabTicks;
    	String chunkstatus;
    	int dataversion;
    	
    	public Builder(int world_ymin, int world_ymax) {
    		reset(world_ymin, world_ymax);
    	}
    	public void reset(int world_ymin, int world_ymax) {
    		x = 0; z = 0; 
    		y_min = world_ymin >> 4;
    		dataversion = 0;
    		chunkstatus = null;
    		int y_max = (world_ymax + 15) >> 4;	// Round up
    		sections = new GenericChunkSection[y_max - y_min];	// Range for all potential sections
    	}
    	// Set inhabited ticks
    	public Builder inhabitedTicks(long inh) {
    		this.inhabTicks = inh;
    		return this;
    	}
    	// Set section
    	public Builder addSection(int sy, GenericChunkSection sect) {
    		if ((sy >= y_min) && ((sy - y_min) < sections.length)) {
    			this.sections[sy - y_min] = sect;
    		}
    		return this;
    	}
    	// Set coordinates
    	public Builder coords(int sx, int sz) {
    		this.x = sx;
    		this.z = sz;
    		return this;
    	}
    	// Generate simple sky lighting (must be after all sections have been added)
    	public Builder generateSky() {
    		int sky[] = new int[256]; // ZX array
    		boolean nonOpaque[] = new boolean[256]; // ZX array of non opaque blocks (atten < 15)
    		Arrays.fill(sky, 15);	// Start fully lit at top
    		GenericChunkSection.Builder bld = new GenericChunkSection.Builder();
    		boolean allzero = false;
    		// Make light array for each section, start from top
    		for (int i = (sections.length - 1); i >= 0; i--) {
    			GenericChunkSection sect = sections[i];
    			if (sect == null) continue;
    			if (allzero) {	// Start section with all zero already, just zero it and move on
    				// Replace section with new all zero light
    				sections[i] = bld.buildFrom(sect, 0);
    				continue;
    			}
				byte[] ssky = new byte[2048];
				int allfullcnt = 0;
				// Top to bottom 
				for (int y = 15; (y >= 0) && (!allzero); y--) {
					int totalval = 0;	// Use for allzero or allfull
					int yidx = y << 7;
					// Light next layer down
					for (int x = 0; x < 16; x++) {
						for (int z = 0; z < 16; z++) {
							int idx = (z << 4) + x;
							int val = sky[idx];
							DynmapBlockState bs = sect.blocks.getBlock(x, y, z);	// Get block
							int atten = bs.getLightAttenuation();
							if ((atten > 0) && (val > 0)) {
								val = (val >= atten) ? (val - atten) : 0;
								sky[idx] = val;
							}
							nonOpaque[idx] = atten < 15;
							totalval += val;
						}
					}
					allzero = (totalval == 0);
					boolean allfull = (totalval == (15 * 256));
					if (allfull) allfullcnt++;
					// If not all fully lit nor all zero, handle horizontal spread
					if (! (allfull || allzero)) {
						// Now do horizontal spread
						boolean changed;
						do {
	    					changed = false;
	        				for (int x = 0; x < 16; x++) {
	        					for (int z = 0; z < 16; z++) {
	        						int idx = (z << 4) + x;
	        						int cur = sky[idx];
	        						boolean curnonopaq = nonOpaque[idx];
	        						if (x < 15) {	// If not right edge, check X spread
	        							int right = sky[idx+1];
	            						boolean rightnonopaq = nonOpaque[idx+1];
	            						// If spread right
	            						if (rightnonopaq && ((cur - 1) > right)) {
	            							sky[idx+1] = cur - 1; changed = true;
            							}
	            						// If spread left
	            						else if (curnonopaq && (cur < (right - 1))) {
	            							sky[idx] = cur = right - 1; changed = true;
            							}
	        						}
	        						if (z < 15) {	// If not bottom edge, check Z spread
	        							int down = sky[idx+16];
	            						boolean downnonopaq = nonOpaque[idx+16];
	            						// If spread down
	            						if (downnonopaq && ((cur - 1) > down)) {
	            							sky[idx+16] = cur - 1; changed = true;
            							}
	            						// If spread up
	            						else if (curnonopaq && (cur < (down - 1))) {
	            							sky[idx] = down - 1; changed = true;
            							}
	        						}
	        					}
	        				}
	    				} while (changed);    				
						// Save values
						for (int v = 0; v < 128; v++) {
							ssky[yidx | v] = (byte)(sky[v << 1] | (sky[(v << 1) + 1] << 4));
						}
					}
					else if (allfull) {	// All light, just fill it
						for (int v = 0; v < 128; v++) {
							ssky[yidx | v] = (byte) 0xFF;
						}
					}
    			}
				// Replace section with new one with new lighting
				if (allfullcnt == 16) {	// Just full?
					sections[i] = bld.buildFrom(sect, 15);					
				}
				else {
					sections[i] = bld.buildFrom(sect, ssky);
				}
    		}
    		return this;    		
    	}
    	// Set chunk status
    	public Builder chunkStatus(String chunkstat) {
    		this.chunkstatus = chunkstat;
    		return this;
    	}
    	// Set data version
    	public Builder dataVersion(int dataver) {
    		this.dataversion = dataver;
    		return this;
    	}
    	// Build chunk
    	public GenericChunk build() {
    		return new GenericChunk(x, z, y_min, sections, inhabTicks, dataversion, chunkstatus);
    	}
    }
}
