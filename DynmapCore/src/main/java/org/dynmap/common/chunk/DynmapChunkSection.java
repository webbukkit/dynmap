package org.dynmap.common.chunk;

import java.util.Arrays;

import org.dynmap.common.BiomeMap;
import org.dynmap.renderer.DynmapBlockState;

// Generic section: represents 16 x 16 x 16 grid of blocks
public class DynmapChunkSection {
	public final BiomeAccess biomes;	// Access for biome data
	public final BlockStateAccess blocks;	// Access for block states
	public final LightingAccess sky;	// Access for sky light data
	public final LightingAccess emitted;	// Access for emitted light data
	public final boolean isEmpty;	// if true, section is all air with default sky and emitted light
	
	// Block state access interface
	public interface BlockStateAccess {
		public DynmapBlockState getBlock(int x, int y, int z);
	}
	private static class BlockStateAccess3D implements BlockStateAccess {
		private final DynmapBlockState blocks[];
		BlockStateAccess3D(DynmapBlockState[][][] bs) {	// (16 x 16 x 16) X, Y, Z
			blocks = new DynmapBlockState[4096];
			Arrays.fill(blocks, DynmapBlockState.AIR);	// Initialize to AIR
			for (int x = 0; (x < 16) && (x < bs.length); x++) {
				for (int y = 0; (y < 16) && (y < bs[x].length); y++) {
					for (int z = 0; (z < 16) && (z < bs[x][y].length); z++) {
						blocks[(256 * y) + (16 * z) + x] = bs[x][y][z];
					}
				}
			}
		}
		public final DynmapBlockState getBlock(int x, int y, int z) {
			return blocks[(256 * y) + (16 * z) + x];
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
	}
	// Biome access interface
	public interface BiomeAccess {
		public BiomeMap getBiome(int x, int y, int z);
	}
	// For classic 2D biome map
	private static class BiomeAccess2D implements BiomeAccess  {
		private final BiomeMap biomes[];	// (16 * Z) + X
		BiomeAccess2D(BiomeMap[][] b) {	// Grid is 16 x 16 (X, Z)
			biomes = new BiomeMap[256];
			Arrays.fill(biomes, BiomeMap.NULL);	// Initialize to null
			for (int x = 0; (x < 16) && (x < b.length); x++) {
				for (int z = 0; (z < 16) && (z < b[x].length); z++) {
					biomes[(z << 4) + x] = b[x][z];
				}
			}
		}
		public final BiomeMap getBiome(int x, int y, int z) {
			return biomes[((z & 0xF) << 4) + (x & 0xF)];
		}
	}
	// For 3D biome map
	private static class BiomeAccess3D implements BiomeAccess  {
		private final BiomeMap biomes[];	// (16 * (Y >> 2)) + (4 * (Z >> 2)) + (X >> 2)
		BiomeAccess3D(BiomeMap[][][] b) {	// Grid is 4 x 4 x 4 (X, Y, Z)
			biomes = new BiomeMap[64];
			Arrays.fill(biomes, BiomeMap.NULL);	// Initialize to null
			for (int x = 0; (x < 4) && (x < b.length); x++) {
				for (int y = 0; (y < 4) && (y < b[x].length); y++) {
					for (int z = 0; (z < 4) && (z < b[x][y].length); z++) {
						biomes[((y & 0xC) << 2) | (z & 0xC) | ((x & 0xC) >> 2)] = b[x][y][z];
					}
				}
			}
		}
		public final BiomeMap getBiome(int x, int y, int z) {
			return biomes[ ((y & 0xC) << 2) | (z & 0xC) | ((x & 0xC) >> 2) ];
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
	}
	// Lighting access interface
	public interface LightingAccess {
		public int getLight(int x, int y, int z);
	}
	private static class LightingAccess3D implements LightingAccess {
		private final long[] light;		// Nibble array (16 * y) * z (nibble at 4 << x)

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
			return (int)(light[(16 * (y & 0xF)) + (z & 0xF)] >> (4 * (x & 0xF)) & 0xFL);
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
	}	
	private DynmapChunkSection(BlockStateAccess blks, BiomeAccess bio, LightingAccess skyac, LightingAccess emitac, boolean empty) {
		blocks = blks;
		biomes = bio;
		sky = skyac;
		emitted = emitac;
		isEmpty = empty;
	}
	private static BiomeAccess defaultBiome = new BiomeAccessSingle(BiomeMap.NULL);
	private static BlockStateAccess defaultBlockState = new BlockStateAccessSingle(DynmapBlockState.AIR);
	private static LightingAccess defaultSky = new LightingAccessSingle(15);
	private static LightingAccess defaultEmit = new LightingAccessSingle(0);
	
	// Shared default empty section
	public static final DynmapChunkSection EMPTY = new DynmapChunkSection(defaultBlockState, defaultBiome, defaultSky, defaultEmit, true);
	
	// Factory for building section
	public static class Builder {
		private BiomeAccess ba;
		private BlockStateAccess bs;
		private LightingAccess sk;
		private LightingAccess em;
		private boolean empty;
		// Initialize builder with empty state
		public Builder() {
			reset();
		}
		// Reset builder to default state
		public void reset() {
			ba = defaultBiome;
			bs = defaultBlockState;
			sk = defaultSky;
			em = defaultEmit;			
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
			ba = new BiomeAccessSingle(bio);
			return this;
		}
		// Set bipme to 2D array of values (bio[x][z] = 16 x 16)
		public Builder xzBiome(BiomeMap bio[][]) {
			ba = new BiomeAccess2D(bio);
			return this;
		}
		// Set bipme to 3D array of values (bio[x][y][z] = 4 x 4 x 4)
		public Builder xyzBiome(BiomeMap bio[][][]) {
			ba = new BiomeAccess3D(bio);
			return this;
		}
		// Set block state to single value
		public Builder singleBlockState(DynmapBlockState block) {
			bs = new BlockStateAccessSingle(block);
			empty = block.isAir();
			return this;
		}
		// Set block state to 3D array (blocks[x][y][z])
		public Builder xyzBlockState(DynmapBlockState blocks[][][]) {
			bs = new BlockStateAccess3D(blocks);
			empty = false;
			return this;
		}
		// Build section based on current builder state
		public DynmapChunkSection build() {
			return new DynmapChunkSection(bs, ba, sk, em, empty); 
		}
	}
}
