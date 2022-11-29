package org.dynmap.fabric_1_16_4;

import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeEffects;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.ChunkStatus;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericMapChunkCache;
import org.dynmap.fabric_1_16_4.mixin.BiomeEffectsAccessor;

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

    private CompoundTag readChunk(int x, int z) {
        try {
            ThreadedAnvilChunkStorage acl = cps.threadedAnvilChunkStorage;

            ChunkPos coord = new ChunkPos(x, z);
            return acl.getNbt(coord);
        } catch (Exception exc) {
            Log.severe(String.format("Error reading chunk: %s,%d,%d", dw.getName(), x, z), exc);
            return null;
        }
    }

	// Load generic chunk from existing and already loaded chunk
	protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
		GenericChunk gc = null;
        if (cps.isChunkLoaded(chunk.x, chunk.z)) {
            CompoundTag nbt = null;
            try {
                nbt = ChunkSerializer.serialize((ServerWorld) w, cps.getWorldChunk(chunk.x, chunk.z, false));
            } catch (NullPointerException e) {
                // TODO: find out why this is happening and why it only seems to happen since 1.16.2
                Log.severe("ChunkSerializer.serialize threw a NullPointerException", e);
            }
			if (nbt != null) {
				gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
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
			gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
		}
		return gc;
	}
    @Override
    public int getFoliageColor(BiomeMap bm, int[] colormap, int x, int z) {
        return bm.<Biome>getBiomeObject()
                .map(Biome::getEffects)
                .map(BiomeEffectsAccessor.class::cast)
                .flatMap(BiomeEffectsAccessor::getFoliageColor)
                .orElse(colormap[bm.biomeLookup()]);
    }

    @Override
    public int getGrassColor(BiomeMap bm, int[] colormap, int x, int z) {
        BiomeEffectsAccessor effects = (BiomeEffectsAccessor) bm.<Biome>getBiomeObject().map(Biome::getEffects).orElse(null);
        if (effects == null) return colormap[bm.biomeLookup()];
        int grassMult = effects.getGrassColor().orElse(colormap[bm.biomeLookup()]);
        BiomeEffects.GrassColorModifier modifier = effects.getGrassColorModifier();
        if (modifier == BiomeEffects.GrassColorModifier.DARK_FOREST) {
            return ((grassMult & 0xfefefe) + 0x28340a) >> 1;
        } else if (modifier == BiomeEffects.GrassColorModifier.SWAMP) {
            double var5 = Biome.FOLIAGE_NOISE.sample(x * 0.0225, z * 0.0225, false);
            return var5 < -0.1 ? 0x4c763c : 0x6a7039;
        } else {
            return grassMult;
        }
    }
}

