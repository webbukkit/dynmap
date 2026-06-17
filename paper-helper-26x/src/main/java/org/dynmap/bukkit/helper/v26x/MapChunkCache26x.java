package org.dynmap.bukkit.helper.v26x;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.dynmap.DynmapChunk;
import org.dynmap.bukkit.helper.BukkitWorld;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericMapChunkCache;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public class MapChunkCache26x extends GenericMapChunkCache {
	private World w;

	public MapChunkCache26x(GenericChunkCache cc) {
		super(cc);
	}

	@Override
	protected Supplier<GenericChunk> getLoadedChunkAsync(DynmapChunk chunk) {
		CompletableFuture<Optional<SerializableChunkData>> chunkData = CompletableFuture.supplyAsync(() -> {
			CraftWorld cw = (CraftWorld) w;
			LevelChunk c = cw.getHandle().getChunkIfLoaded(chunk.x, chunk.z);
			if (c == null || !c.loaded) {
				return Optional.empty();
			}
			return Optional.of(SerializableChunkData.copyOf(cw.getHandle(), c));
		}, ((CraftServer) Bukkit.getServer()).getServer());
		return () -> {
			try {
				return chunkData.join().map(SerializableChunkData::write).map(NBT.NBTCompound::new).map(this::parseChunkFromNBT).orElse(null);
			} catch (CompletionException e) {
				return null;
			}
		};
	}

	@Override
	protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
		CraftWorld cw = (CraftWorld) w;
		if (!cw.isChunkLoaded(chunk.x, chunk.z)) return null;
		LevelChunk c = cw.getHandle().getChunkIfLoaded(chunk.x, chunk.z);
		if (c == null || !c.loaded) return null;
		SerializableChunkData chunkData = SerializableChunkData.copyOf(cw.getHandle(), c);
		CompoundTag nbt = chunkData.write();
		return nbt != null ? parseChunkFromNBT(new NBT.NBTCompound(nbt)) : null;
	}

	@Override
	protected Supplier<GenericChunk> loadChunkAsync(DynmapChunk chunk) {
		CraftWorld cw = (CraftWorld) w;
		CompletableFuture<Optional<CompoundTag>> genericChunk = cw.getHandle().getChunkSource().chunkMap.read(new ChunkPos(chunk.x, chunk.z));
		return () -> {
			try {
				return genericChunk.join().map(NBT.NBTCompound::new).map(this::parseChunkFromNBT).orElse(null);
			} catch (CompletionException e) {
				return null;
			}
		};
	}

	@Override
	protected GenericChunk loadChunk(DynmapChunk chunk) {
		CraftWorld cw = (CraftWorld) w;
		CompoundTag nbt = null;
		ChunkPos cc = new ChunkPos(chunk.x, chunk.z);
		GenericChunk gc = null;
		try {
			nbt = cw.getHandle()
					.getChunkSource()
					.chunkMap
					.read(cc)
					.join().get();
		} catch (CancellationException | CompletionException cx) {
		} catch (NoSuchElementException snex) {
		}
		if (nbt != null) {
			gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
		}
		return gc;
	}

	public void setChunks(BukkitWorld dw, List<DynmapChunk> chunks) {
		this.w = dw.getWorld();
		super.setChunks(dw, chunks);
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
		BiomeSpecialEffects fog = bm.<Biome>getBiomeObject().map(Biome::getSpecialEffects).orElse(null);
		if (fog == null) return colormap[bm.biomeLookup()];
		return fog.grassColorModifier().modifyColor((double) x, (double) z, fog.grassColorOverride().orElse(colormap[bm.biomeLookup()]));
	}
}
