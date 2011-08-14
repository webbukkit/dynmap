package org.dynmap;

import java.io.File;
import java.util.List;

import org.bukkit.World;
import org.dynmap.kzedmap.MapTileRenderer;
import org.dynmap.utils.MapChunkCache;

public abstract class MapTile {
    protected DynmapWorld world;

    public abstract boolean render(MapChunkCache cache, String mapname);
    public abstract List<DynmapChunk> getRequiredChunks();
    public abstract MapTile[] getAdjecentTiles();

    public World getWorld() {
        return world.world;
    }

    public DynmapWorld getDynmapWorld() {
        return world;
    }

    public abstract String getFilename();

    public abstract String getDayFilename();

    public MapTile(DynmapWorld world) {
        this.world = world;
    }

    @Override
    public int hashCode() {
        return getFilename().hashCode() ^ getWorld().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MapTile) {
            MapTile t = (MapTile)obj;
            return getFilename().equals(t.getFilename()) && getWorld().equals(t.getWorld());
        }
        return super.equals(obj);
    }
    
    public abstract String getKey();
    
    public abstract boolean isBiomeDataNeeded();
    public abstract boolean isHightestBlockYDataNeeded();
    public abstract boolean isRawBiomeDataNeeded();
    public abstract boolean isBlockTypeDataNeeded();

    public abstract int tileOrdinalX();
    public abstract int tileOrdinalY();
    
}
