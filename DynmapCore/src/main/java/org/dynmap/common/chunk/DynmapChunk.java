package org.dynmap.common.chunk;

import java.util.Arrays;

import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.common.BiomeMap;

// Generic chunk representation
public class DynmapChunk {
	public final int cx, cz;	// Chunk coord (world coord / 16)
	public final DynmapChunkSection[] sections;
	public final int cy_min;	// CY value of first section in sections list (index = (Y >> 4) - cy_min
	public final long inhabitedTicks;
	
	private DynmapChunk(int cx, int cz, int cy_min, DynmapChunkSection[] sections, long inhabTicks) {
		this.cx = cx;
		this.cz = cz;
		this.inhabitedTicks = inhabTicks;
		this.sections = new DynmapChunkSection[sections.length + 2];	// Add one empty at top and bottom
		this.cy_min = cy_min - 1;	// Include empty at bottom
		Arrays.fill(this.sections, DynmapChunkSection.EMPTY);	// Fill all spots with empty, including pad on bottom/top
		for (int off = 0; off < sections.length; off++) {
			if (sections[off] != null) {	// If defined, set the section
				this.sections[off+1] = sections[off];
			}
		}
	}
	// Get section for given block Y coord
	public final DynmapChunkSection getSection(int y) {
		try {
			return this.sections[(y >> 4) - cy_min];
		} catch (IndexOutOfBoundsException ioobx) {	// Builder and padding should be avoiding this, but be safe
			return DynmapChunkSection.EMPTY;
		}
	}
	
    public final DynmapBlockState getBlockType(int x, int y, int z) {    	
    	return getSection(y).blocks.getBlock(x, y, z);
    }
    public final DynmapBlockState getBlockType(DynmapChunkPos pos) {    	
    	return getSection(pos.y).blocks.getBlock(pos);
    }
    public final int getBlockSkyLight(int x, int y, int z) {
    	return getSection(y).sky.getLight(x, y, z);
    }
    public final int getBlockSkyLight(DynmapChunkPos pos) {
    	return getSection(pos.y).sky.getLight(pos);
    }
    public final int getBlockEmittedLight(int x, int y, int z) {
    	return getSection(y).emitted.getLight(x, y, z);
    }
    public final int getBlockEmittedLight(DynmapChunkPos pos) {
    	return getSection(pos.y).emitted.getLight(pos);
    }
    public final BiomeMap getBiome(int x, int y, int z) {
    	return getSection(y).biomes.getBiome(x, y, z);
    }
    public final BiomeMap getBiome(DynmapChunkPos pos) {
    	return getSection(pos.y).biomes.getBiome(pos);
    }
    public final boolean isSectionEmpty(int cy) {
    	return getSection(cy << 4).isEmpty;
    }
    public final long getInhabitedTicks() {
        return inhabitedTicks;
    }	
    // Builder for fabricating finalized chunk
    public static class Builder {
    	int x;
    	int z;
    	int y_min;
    	DynmapChunkSection[] sections;
    	long inhabTicks;
    	
    	public Builder(int world_ymin, int world_ymax) {
    		reset(world_ymin, world_ymax);
    	}
    	public void reset(int world_ymin, int world_ymax) {
    		x = 0; z = 0; 
    		y_min = world_ymin >> 4;
    		int y_max = (world_ymax + 15) >> 4;	// Round up
    		sections = new DynmapChunkSection[y_max - y_min];	// Range for all potential sections
    	}
    	// Set inhabited ticks
    	public Builder inhabitedTicks(long inh) {
    		this.inhabTicks = inh;
    		return this;
    	}
    	// Set section
    	public Builder addSection(int sy, DynmapChunkSection sect) {
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
    	// Build chunk
    	public DynmapChunk build() {
    		return new DynmapChunk(x, z, y_min, sections, inhabTicks);
    	}
    }
}
