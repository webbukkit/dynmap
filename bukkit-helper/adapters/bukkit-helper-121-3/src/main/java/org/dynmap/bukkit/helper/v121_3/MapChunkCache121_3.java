package org.dynmap.bukkit.helper.v121_3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.dynmap.DynmapChunk;
import org.dynmap.bukkit.helper.BukkitWorld;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericMapChunkCache;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class MapChunkCache121_3 extends GenericMapChunkCache {
    private World w;

    /**
     * Construct empty cache
     */
    public MapChunkCache121_3(GenericChunkCache cc) {
        super(cc);
    }

    // Load generic chunk from existing and already loaded chunk
    protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
        CraftWorld cw = (CraftWorld) w;
        if (!cw.isChunkLoaded(chunk.x, chunk.z)) return null;
        LevelChunk c = cw.getHandle().getChunkIfLoaded(chunk.x, chunk.z);
        if (c == null || !c.loaded) return null;    // c.loaded

        SerializableChunkData chunkData = SerializableChunkData.copyOf(cw.getHandle(), c);
        CompoundTag nbt = chunkData.write();
        return nbt != null ? parseChunkFromNBT(new NBT.NBTCompound(nbt)) : null;
    }

    // Load generic chunk from unloaded chunk
    @Override
    protected Supplier<GenericChunk> loadChunkAsync(DynmapChunk chunk) {
        CraftWorld cw = (CraftWorld) w;
        CompletableFuture<Optional<CompoundTag>> future = cw.getHandle().getChunkSource().chunkMap.read(new ChunkPos(chunk.x, chunk.z));
        return () -> {
            try {
                Optional<CompoundTag> nbt = future.get();
                return nbt.map(n -> parseChunkFromNBT(new NBT.NBTCompound(n))).orElse(null);
            } catch (InterruptedException e) {
                return null;
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        };
    }

    protected GenericChunk loadChunk(DynmapChunk chunk) {
        return loadChunkAsync(chunk).get();
    }

    public void setChunks(BukkitWorld dw, List<DynmapChunk> chunks) {
        this.w = dw.getWorld();
        super.setChunks(dw, chunks);
    }

    @Override
    public int getFoliageColor(BiomeMap bm, int[] colormap, int x, int z) {
        return bm.<Biome>getBiomeObject().map(Biome::getSpecialEffects).flatMap(BiomeSpecialEffects::getFoliageColorOverride).orElse(colormap[bm.biomeLookup()]);
    }

    @Override
    public int getGrassColor(BiomeMap bm, int[] colormap, int x, int z) {
        Optional<BiomeSpecialEffects> effects = bm.<Biome>getBiomeObject().map(Biome::getSpecialEffects);
        return effects.map(biomeSpecialEffects -> biomeSpecialEffects.getGrassColorModifier()
                .modifyColor(x, z, biomeSpecialEffects.getGrassColorOverride().orElse(colormap[bm.biomeLookup()]))).orElse(colormap[bm.biomeLookup()]);
    }
}
