package org.dynmap.forge_26_1;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericMapChunkCache;
import org.dynmap.renderer.DynmapBlockState;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since
 * rendering is off server thread
 */
public class ForgeMapChunkCache extends GenericMapChunkCache {
	private ServerLevel w;
	private ServerChunkCache cps;
	/**
	 * Construct empty cache
	 */
	public ForgeMapChunkCache(GenericChunkCache cc) {
		super(cc);
	}

	// Load generic chunk from existing and already loaded chunk
	protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
		GenericChunk gc = null;
		ChunkAccess ch = cps.getChunk(chunk.x, chunk.z, ChunkStatus.FULL, false);
		if (ch != null) {
            SerializableChunkData sc = SerializableChunkData.copyOf(w, cps.getChunk(chunk.x, chunk.z, false));
            CompoundTag nbt = sc.write();
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

	public void setChunks(ForgeWorld dw, List<DynmapChunk> chunks) {
		this.w = dw.getWorld();
		if (dw.isLoaded()) {
			/* Check if world's provider is ServerChunkProvider */
			cps = this.w.getChunkSource();
		}
		super.setChunks(dw, chunks);
	}

	private CompoundTag readChunk(int x, int z) {
		try {
			CompoundTag rslt = cps.chunkMap.read(new ChunkPos(x, z)).join().get();
			if (rslt != null) {
				CompoundTag lev = rslt;
				if (lev.contains("Level")) {
					lev = lev.getCompoundOrEmpty("Level");
				}
				// Don't load uncooked chunks
				String stat = lev.getStringOr("Status", null);
				ChunkStatus cs = ChunkStatus.byName(stat);
				if ((stat == null) ||
				// Needs to be at least lighted
						(!cs.isOrAfter(ChunkStatus.LIGHT))) {
					rslt = null;
				}
			}
			// Log.info(String.format("loadChunk(%d,%d)=%s", x, z, (rslt != null) ?
			// rslt.toString() : "null"));
			return rslt;
		} catch (NoSuchElementException nsex) {
			return null;
		} catch (Exception exc) {
			Log.severe(String.format("Error reading chunk: %s,%d,%d", dw.getName(), x, z), exc);
			return null;
		}
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
		return bm.<Biome>getBiomeObject().map(Biome::getSpecialEffects).
				flatMap(effects -> effects.foliageColorOverride())
				.orElse(colormap[bm.biomeLookup()]);
	}

	@Override
	public int getGrassColor(BiomeMap bm, int[] colormap, int x, int z) {
		BiomeSpecialEffects effects = bm.<Biome>getBiomeObject().map(Biome::getSpecialEffects).orElse(null);
		if (effects == null) return colormap[bm.biomeLookup()];
		return effects.grassColorModifier().modifyColor(x, z, effects.grassColorOverride().orElse(colormap[bm.biomeLookup()]));
	}
}
