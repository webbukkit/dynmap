package org.dynmap.common.chunk;

// Generic block location iterator - represents 3D position, but includes fast precomputed chunk and section offsets
public class GenericChunkPos {
	public int x, y, z;	// 3D world position
	public int cx, cz;	// 2D chunk position (x / 16, z / 16)
	public int cy;		// Vertical section index (Y / 16)
	public int sx, sy, sz;	// 3D section position (x % 16, y % 16, z % 16)
	public int soffset;	// Section offset (256 * sy) + (16 * sz) + sx
	public int sdiv4offset;	// Subsection offset (16 * (sy / 4)) + (4 * (sz / 4)) + (sx / 4) (3D biomes)
	
	public GenericChunkPos(int x, int y, int z) {
		setPos(x, y, z);
	}
	// Replace X value
	public final void setX(int x) {
		this.x = x;
		this.cx = x >> 4;
		this.sx = x & 0xF;		
		this.soffset = (sy << 8) | (sz << 4) | sx;
		this.sdiv4offset = ((sy & 0xC) << 4) | (sz & 0xC) | ((sx & 0xC) >> 2); 
	}
	// Replace Y value
	public final void setY(int y) {
		this.y = y;
		this.cy = y >> 4;
		this.sy = y & 0xF;		
		this.soffset = (sy << 8) | (sz << 4) | sx;
		this.sdiv4offset = ((sy & 0xC) << 4) | (sz & 0xC) | ((sx & 0xC) >> 2); 
	}
	// Replace Z value
	public final void setZ(int z) {
		this.z = z;
		this.cz = z >> 4;
		this.sz = z & 0xF;		
		this.soffset = (sy << 8) | (sz << 4) | sx;
		this.sdiv4offset = ((sy & 0xC) << 4) | (sz & 0xC) | ((sx & 0xC) >> 2); 
	}
	// Replace X, Y, and Z values
	public final void setPos(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.cx = x >> 4;
		this.cy = y >> 4;
		this.cz = z >> 4;
		this.sx = x & 0xF;		
		this.sy = y & 0xF;		
		this.sz = z & 0xF;		
		this.soffset = (sy << 8) | (sz << 4) | sx;
		this.sdiv4offset = ((sy & 0xC) << 4) | (sz & 0xC) | ((sx & 0xC) >> 2); 
	}
}
