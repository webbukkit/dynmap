package org.dynmap.common.chunk;

import java.util.Arrays;

import org.dynmap.common.BiomeMap;
import org.dynmap.renderer.DynmapBlockState;

// Generic section: represents 16 x 16 x 16 grid of blocks
public class GenericChunkSection {
	public final BiomeAccess biomes;	// Access for biome data
	public final BlockStateAccess blocks;	// Access for block states
	public final LightingAccess sky;	// Access for sky light data
	public final LightingAccess emitted;	// Access for emitted light data
	public final boolean isEmpty;	// if true, section is all air with default sky and emitted light
	
	// Block state access interface
	public interface BlockStateAccess {
		public DynmapBlockState getBlock(int x, int y, int z);
		public DynmapBlockState getBlock(GenericChunkPos pos);
	}
	private static class BlockStateAccess3D implements BlockStateAccess {
		private final DynmapBlockState blocks[];	// YZX order
		// Array given to us by builder
		BlockStateAccess3D(DynmapBlockState bs[]) {
			blocks = bs;
		}
		public final DynmapBlockState getBlock(int x, int y, int z) {
			return blocks[(256 * (y & 0xF)) + (16 * (z & 0xF)) + (x & 0xF)];
		}
		public final DynmapBlockState getBlock(GenericChunkPos pos) {
			return blocks[pos.soffset];
		}
	}
	private static class BlockStateAccessSingle implements BlockStateAccess {
		private final DynmapBlockState block;
		BlockStateAccessSingle(DynmapBlockState bs) {
			block = bs;
		}
		public final DynmapBlockState getBlock(int x, int y, int z) {
			return block;
		}
		public final DynmapBlockState getBlock(GenericChunkPos pos) {
			return block;
		}
	}
	// Biome access interface
	public interface BiomeAccess {
		public BiomeMap getBiome(int x, int y, int z);
		public BiomeMap getBiome(GenericChunkPos pos);
	}
	// For classic 2D biome map
	private static class BiomeAccess2D implements BiomeAccess  {
		private final BiomeMap biomes[];	// (16 * Z) + X
		// Array given to us by builder in right format
		BiomeAccess2D(BiomeMap b[]) {
			biomes = b;
		}
		public final BiomeMap getBiome(int x, int y, int z) {
			return biomes[((z & 0xF) << 4) + (x & 0xF)];
		}
		public final BiomeMap getBiome(GenericChunkPos pos) {
			return biomes[pos.soffset & 0xFF];	// Just ZX portion
		}
		public String toString() {
			return String.format("Biome2D(%s)", Arrays.deepToString(biomes));
		}
	}
	// For 3D biome map
	private static class BiomeAccess3D implements BiomeAccess  {
		private final BiomeMap biomes[];	// (16 * (Y >> 2)) + (4 * (Z >> 2)) + (X >> 2)
		// Array given to us by builder in right format (64 - YZX divided by 4)
		BiomeAccess3D(BiomeMap[] b) {
			biomes = b;
		}
		public final BiomeMap getBiome(int x, int y, int z) {
			return biomes[ ((y & 0xC) << 2) | (z & 0xC) | ((x & 0xC) >> 2) ];
		}
		public final BiomeMap getBiome(GenericChunkPos pos) {
			return biomes[pos.sdiv4offset];
		}
		public String toString() {
			return String.format("Biome3D(%s)", Arrays.deepToString(biomes));
		}
	}
	// For single biome map
	private static class BiomeAccessSingle implements BiomeAccess {
		private final BiomeMap biome;
		BiomeAccessSingle(BiomeMap b) {
			biome = b;
		}
		public final BiomeMap getBiome(int x, int y, int z) {
			return biome;
		}
		public final BiomeMap getBiome(GenericChunkPos pos) {
			return biome;
		}
		public String toString() {
			return String.format("Biome1(%s)", biome);
		}
	}
	// Lighting access interface
	public interface LightingAccess {
		public int getLight(int x, int y, int z);
		public int getLight(GenericChunkPos pos);
	}
	private static class LightingAccess3D implements LightingAccess {
		private final long[] light;		// Nibble array (16 * y) * z (nibble at << (4*x))

		// Construct using nibble array (same as lighting format in NBT fields) (128*Y + 8*Z + X/2) (oddX high, evenX low)
		LightingAccess3D(byte[] lig) {
			light = new long[256];
			if (lig != null) {
				for (int off = 0; (off < lig.length) && (off < 2048); off++) {
					light[off >> 3] |= (0xFFL & (long)lig[off]) << (8 * (off & 0x7));
				}
			}
		}
		public final int getLight(int x, int y, int z) {
			return 0xF & (int)(light[(16 * (y & 0xF)) + (z & 0xF)] >> (4 * (x & 0xF)));
		}
		public final int getLight(GenericChunkPos pos) {
			return 0xF & (int)(light[pos.soffset >> 4] >> (4 * pos.sx));			
		}
	}
	private static class LightingAccessSingle implements LightingAccess {
		private final int light;
		LightingAccessSingle(int lig) {
			light = lig & 0xF;
		}
		public final int getLight(int x, int y, int z) {
			return light;
		}
		public final int getLight(GenericChunkPos pos) {
			return light;
		}
	}	
	private GenericChunkSection(BlockStateAccess blks, BiomeAccess bio, LightingAccess skyac, LightingAccess emitac, boolean empty) {
		blocks = blks;
		biomes = bio;
		sky = skyac;
		emitted = emitac;
		isEmpty = empty;
	}
	public String toString() {
		return String.format("sect(bip:%s)", biomes);
	}
	private static BiomeAccess defaultBiome = new BiomeAccessSingle(BiomeMap.NULL);
	private static BlockStateAccess defaultBlockState = new BlockStateAccessSingle(DynmapBlockState.AIR);
	private static LightingAccess defaultLight = new LightingAccessSingle(0);
	
	// Shared default empty section
	public static final GenericChunkSection EMPTY = new GenericChunkSection(defaultBlockState, defaultBiome, new LightingAccessSingle(15), defaultLight, true);
	
	// Factory for building section
	public static class Builder {
		private LightingAccess sk;
		private LightingAccess em;
		private DynmapBlockState bsaccumsing;	// Used for single
		private DynmapBlockState bsaccum[];		// Use for incremental setting of 3D - YZX order
		private BiomeMap baaccumsingle;			// Use for single
		private BiomeMap baaccum[];				// Use for incremental setting of 3D biome - YZX order or 2D biome (ZX order) length used to control which
		private boolean empty;
		// Initialize builder with empty state
		public Builder() {
			reset();
		}
		// Reset builder to default state
		public void reset() {
			bsaccumsing = DynmapBlockState.AIR;
			bsaccum = null;
			baaccumsingle = BiomeMap.NULL;
			baaccum = null;
			sk = defaultLight;
			em = defaultLight;			
			empty = true;
		}
		// Set sky lighting to single value
		public Builder singleSkyLight(int val) {
			sk = new LightingAccessSingle(val);
			return this;
		}
		// Set sky lighting to given nibble array (YZX order)
		public Builder skyLight(byte[] data) {
			sk = new LightingAccess3D(data);
			return this;
		}
		// Set emitted lighting to single value
		public Builder singleEmittedLight(int val) {
			em = new LightingAccessSingle(val);
			return this;
		}
		// Set emitted lighting to given nibble array (YZX order)
		public Builder emittedLight(byte[] data) {
			em = new LightingAccess3D(data);
			return this;
		}
		// Set bipme to single value
		public Builder singleBiome(BiomeMap bio) {
			baaccumsingle = bio;
			baaccum = null;
			return this;
		}
		// Set bipme for 2D style
		public Builder xzBiome(int x, int z, BiomeMap bio) {
			if ((baaccum == null) || (baaccum.length != 256)) {
				baaccum = new BiomeMap[256];
				Arrays.fill(baaccum, BiomeMap.NULL);
				baaccumsingle = BiomeMap.NULL;
			}
			baaccum[((z & 0xF) << 4) + (x & 0xF)] = bio;
			return this;
		}
		// Set bipme to 3D style
		public Builder xyzBiome(int xdiv4, int ydiv4, int zdiv4, BiomeMap bio) {
			if ((baaccum == null) || (baaccum.length != 64)) {
				baaccum = new BiomeMap[64];
				Arrays.fill(baaccum, BiomeMap.NULL);
				baaccumsingle = BiomeMap.NULL;
			}
			baaccum[((ydiv4 & 0x3) << 4) + ((zdiv4 & 0x3) << 2) + (xdiv4 & 0x3)] = bio;
			return this;
		}
		// Set block state to single value
		public Builder singleBlockState(DynmapBlockState block) {
			bsaccumsing = block;
			bsaccum = null;
			empty = block.isAir();
			return this;
		}
		// Set block state 
		public Builder xyzBlockState(int x, int y, int z, DynmapBlockState block) {
			if (bsaccum == null) {
				bsaccum = new DynmapBlockState[4096];
				Arrays.fill(bsaccum, DynmapBlockState.AIR);
				bsaccumsing = DynmapBlockState.AIR;
			}
			bsaccum[((y & 0xF) << 8) + ((z & 0xF) << 4) + (x & 0xF)] = block;
			empty = false;
			return this;
		}
		// Build section based on current builder state
		public GenericChunkSection build() {
			// Process state access - see if we can reduce
			if (bsaccum != null) {
				DynmapBlockState v = bsaccum[0];	// Get first
				boolean mismatch = false;
				for (int i = 0; i < bsaccum.length; i++) {
					if (bsaccum[i] != v) {
						mismatch = true;
						break;
					}
				}
				if (!mismatch) {	// All the same?
					bsaccumsing = v;
					bsaccum = null;
				}
			}
			BlockStateAccess bs;
			if (bsaccum != null) {
				bs = new BlockStateAccess3D(bsaccum);
				bsaccum = null;
				empty = false;
			}
			else if (bsaccumsing == DynmapBlockState.AIR) {	// Just air?
				bs = defaultBlockState;
				empty = true;
			}
			else {
				bs = new BlockStateAccessSingle(bsaccumsing);
				bsaccumsing = DynmapBlockState.AIR;
			}
			// See if biome access can be reduced to single
			if (baaccum != null) {
				BiomeMap v = baaccum[0];	// Get first
				boolean mismatch = false;
				for (int i = 0; i < baaccum.length; i++) {
					if (baaccum[i] != v) {
						mismatch = true;
						break;
					}
				}
				if (!mismatch) {	// All the same?
					baaccumsingle = v;
					baaccum = null;
				}
			}
			BiomeAccess ba;
			if (baaccum != null) {
				if (baaccum.length == 64) {	// 3D?
					ba = new BiomeAccess3D(baaccum);
				}
				else {
					ba = new BiomeAccess2D(baaccum);
				}
				baaccum = null;
			}
			else if (baaccumsingle == BiomeMap.NULL) {	// Just null?
				ba = defaultBiome;
			}
			else {
				ba = new BiomeAccessSingle(baaccumsingle);
				baaccumsingle = BiomeMap.NULL;
			}
			return new GenericChunkSection(bs, ba, sk, em, empty); 
		}
	}
}
