package org.dynmap.fabric_26_2;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
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
    private Level w;
    private ServerChunkCache cps;

    /**
     * Construct empty cache
     */
    public FabricMapChunkCache(DynmapPlugin plugin) {
    	super(plugin.sscache);
    }

    public void setChunks(FabricWorld dw, List<DynmapChunk> chunks) {
        this.w = dw.getWorld();
        if (dw.isLoaded()) {
            /* World is always a ServerLevel for a server-side mod */
            cps = ((ServerLevel) this.w).getChunkSource();
        }
        super.setChunks(dw, chunks);
    }

	// Load generic chunk from existing and already loaded chunk
	protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
		GenericChunk gc = null;
        if (cps.hasChunk(chunk.x, chunk.z)) {
            CompoundTag nbt = null;
            try {
                ChunkAccess loadedChunk = cps.getChunk(chunk.x, chunk.z, ChunkStatus.FULL, false);
                SerializableChunkData sc = SerializableChunkData.copyOf((ServerLevel) w, loadedChunk);
                nbt = sc.write();
            } catch (NullPointerException e) {
                // TODO: find out why this is happening and why it only seems to happen since 1.16.2
                Log.severe("SerializableChunkData.write threw a NullPointerException", e);
            }
            if (nbt != null) {
            	gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
            }
		}
		return gc;
	}

    private CompoundTag readChunk(int x, int z) {
        try {
            // Async chunk reading is synchronized here. Perhaps we can do async and improve performance?
            return cps.chunkMap.read(new net.minecraft.world.level.ChunkPos(x, z)).join().orElse(null);
        } catch (Exception exc) {
            Log.severe(String.format("Error reading chunk: %s,%d,%d", dw.getName(), x, z), exc);
            return null;
        }
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
                .map(Biome::getSpecialEffects)
                .flatMap(BiomeSpecialEffects::foliageColorOverride)
                .orElse(colormap[bm.biomeLookup()]);
    }

    @Override
    public int getGrassColor(BiomeMap bm, int[] colormap, int x, int z) {
        BiomeSpecialEffects effects = bm.<Biome>getBiomeObject()
                                .map(Biome::getSpecialEffects)
                                .orElse(null);

        if (effects == null) return colormap[bm.biomeLookup()];

        int baseColor = effects.grassColorOverride()
                        .orElse(colormap[bm.biomeLookup()]);

        BiomeSpecialEffects.GrassColorModifier modifier = effects.grassColorModifier();
        if (modifier != null)  return modifier.modifyColor((double)x, (double)z, baseColor);

        return baseColor;
    }
}
