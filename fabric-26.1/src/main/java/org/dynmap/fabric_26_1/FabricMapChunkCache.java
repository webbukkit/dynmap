package org.dynmap.fabric_26_1;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.PackedBitStorage;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
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
import java.util.concurrent.ConcurrentHashMap;

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
            /* Check if world's provider is ServerChunkCache */
            ChunkSource cp = this.w.getChunkSource();

            if (cp instanceof ServerChunkCache) {
                cps = (ServerChunkCache) cp;
            } else {
                Log.severe("Error: world " + dw.getName() + " has unsupported chunk provider");
            }
        } 
        super.setChunks(dw, chunks);
    }

	// Load generic chunk from existing and already loaded chunk
	protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
		GenericChunk gc = null;
        if (cps.hasChunk(chunk.x, chunk.z)) {
            CompoundTag nbt = null;
            try {
                ChunkAccess ca = cps.getChunkNow(chunk.x, chunk.z);
                if (ca == null) return null;
                SerializableChunkData sc = SerializableChunkData.copyOf((ServerLevel) w, ca);
                nbt = sc.write();
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

    private CompoundTag readChunk(int x, int z) {
        try {
            ChunkMap acl = cps.chunkMap;

            ChunkPos coord = new ChunkPos(x, z);
            // Async chunk reading is synchronized here. Perhaps we can do async and improve performance?
            return acl.read(coord).join().orElse(null);
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

    // 26.3+ chunk palettes omit the properties of a block in its default state and otherwise list all of them.
    // Resolve the entry through the game registry (default state + explicit properties) and map the exact
    // BlockState to its Dynmap state, instead of guessing from the Dynmap name table.
    private static final ConcurrentHashMap<String, DynmapBlockState> paletteStateCache = new ConcurrentHashMap<>();

    @Override
    protected DynmapBlockState lookupBlockState(String name, String statestr) {
        String key = ((statestr == null) || statestr.isEmpty()) ? name : name + "[" + statestr + "]";
        DynmapBlockState dbs = paletteStateCache.get(key);
        if (dbs != null) return dbs;
        BlockState bs = resolveGameBlockState(name, statestr);
        if (bs != null) {
            int idx = Block.BLOCK_STATE_REGISTRY.getId(bs);
            if ((idx >= 0) && (idx < DynmapPlugin.stateByID.length)) {
                dbs = DynmapPlugin.stateByID[idx];
            }
        }
        if (dbs == null) {
            dbs = super.lookupBlockState(name, statestr);
        }
        if (dbs != null) {
            paletteStateCache.put(key, dbs);
        }
        return dbs;
    }

    private static BlockState resolveGameBlockState(String name, String statestr) {
        Identifier id = Identifier.tryParse(name);
        if (id == null) return null;
        Optional<Block> blk = BuiltInRegistries.BLOCK.getOptional(id);
        if (!blk.isPresent()) return null;
        BlockState bs = blk.get().defaultBlockState();
        if ((statestr != null) && (!statestr.isEmpty())) {
            StateDefinition<Block, BlockState> def = blk.get().getStateDefinition();
            for (String kv : statestr.split(",")) {
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                Property<?> p = def.getProperty(kv.substring(0, eq).trim());
                if (p != null) {
                    bs = withValue(bs, p, kv.substring(eq + 1).trim());
                }
            }
        }
        return bs;
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState bs, Property<T> p, String value) {
        Optional<T> v = p.getValue(value);
        return v.isPresent() ? bs.setValue(p, v.get()) : bs;
    }

    @Override
    public int getFoliageColor(BiomeMap bm, int[] colormap, int x, int z) {
        return bm.<Biome>getBiomeObject()
                .map(Biome::getSpecialEffects)
                .map(effects -> effects.foliageColorOverride()
                        .orElse(colormap[bm.biomeLookup()]))
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
