package org.dynmap.forge_1_16_5;

import java.util.List;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeAmbience;
import net.minecraft.world.chunk.AbstractChunkProvider;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.storage.ChunkSerializer;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;

import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericMapChunkCache;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class ForgeMapChunkCache extends GenericMapChunkCache {
    private ServerWorld w;
    private ServerChunkProvider cps;
	/**
	 * Construct empty cache
	 */
	public ForgeMapChunkCache(GenericChunkCache cc) {
		super(cc);
	}

	// Load generic chunk from existing and already loaded chunk
	protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
		GenericChunk gc = null;
		IChunk ch = cps.getChunk(chunk.x, chunk.z, ChunkStatus.FULL, false);
		if (ch != null) {
			CompoundNBT nbt = ChunkSerializer.write(w, ch);
			if (nbt != null) {
				gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
			}
		}
		return gc;
	}
	// Load generic chunk from unloaded chunk
	protected GenericChunk loadChunk(DynmapChunk chunk) {
		GenericChunk gc = null;
		CompoundNBT nbt = readChunk(chunk.x, chunk.z);
		// If read was good
		if (nbt != null) {
			gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
		}
		return gc;
	}

	public void setChunks(ForgeWorld dw, List<DynmapChunk> chunks) {
		this.w = (ServerWorld) dw.getWorld();
		if (dw.isLoaded()) {
        	/* Check if world's provider is ServerChunkProvider */
        	AbstractChunkProvider cp = this.w.getChunkProvider();

        	if (cp instanceof ServerChunkProvider) {
                cps = (ServerChunkProvider)cp;
        	}
        	else {
        		Log.severe("Error: world " + dw.getName() + " has unsupported chunk provider");
        	}
		}
		super.setChunks(dw, chunks);
	}

	private CompoundNBT readChunk(int x, int z) {
		try {
			return cps.chunkManager.readChunk(new ChunkPos(x, z));
		} catch (Exception exc) {
			Log.severe(String.format("Error reading chunk: %s,%d,%d", dw.getName(), x, z), exc);
			return null;
		}
	}
	@Override
	public int getFoliageColor(BiomeMap bm, int[] colormap, int x, int z) {
		return bm.<Biome>getBiomeObject().map(Biome::getAmbience)
				.flatMap(BiomeAmbience::getFoliageColor)
				.orElse(colormap[bm.biomeLookup()]);
	}

	@Override
	public int getGrassColor(BiomeMap bm, int[] colormap, int x, int z) {
		BiomeAmbience effects = bm.<Biome>getBiomeObject().map(Biome::getAmbience).orElse(null);
		if (effects == null) return colormap[bm.biomeLookup()];
		BiomeAmbience.GrassColorModifier modifier = effects.getGrassColorModifier();
		if (modifier == BiomeAmbience.GrassColorModifier.DARK_FOREST) {
			return ((effects.getGrassColor().orElse(colormap[bm.biomeLookup()]) & 0xfefefe) + 0x28340a) >> 1;
		} else if (modifier == BiomeAmbience.GrassColorModifier.SWAMP) {
			double d0 = Biome.INFO_NOISE.noiseAt(x * 0.0225D, z * 0.0225D, false);
			return d0 < -0.1D ? 0x4c763c : 0x6a7039;
		} else {
			return effects.getGrassColor().orElse(colormap[bm.biomeLookup()]);
		}
	}
}
