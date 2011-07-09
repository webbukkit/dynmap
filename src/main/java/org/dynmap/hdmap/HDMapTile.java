package org.dynmap.hdmap;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapWorld;
import org.dynmap.MapManager;

import java.util.List;
import org.dynmap.MapTile;
import org.dynmap.utils.MapChunkCache;

public class HDMapTile extends MapTile {
    public HDPerspective perspective;
    public int tx, ty;  /* Tile X and Tile Y are in tile coordinates (pixels/tile-size) */
    
    public HDMapTile(DynmapWorld world, HDPerspective perspective, int tx, int ty) {
        super(world);
        this.perspective = perspective;
        this.tx = tx;
        this.ty = ty;
    }

    @Override
    public String getFilename() {
        return getFilename("hdmap");
    }

    public String getFilename(String prefix) {
        return prefix + "/"  + (tx >> 5) + '_' + (ty >> 5) + '/' + tx + "_" + ty + ".png";
    }

    @Override
    public String getDayFilename() {
        return getDayFilename("hdmap");
    }

    public String getDayFilename(String prefix) {
        return prefix + "_day/"  + (tx >> 5) + '_' + (ty >> 5) + '/' + tx + "_" + ty + ".png";
    }
    
    @Override
    public int hashCode() {
        return perspective.getName().hashCode() ^ getWorld().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HDMapTile) {
            return equals((HDMapTile) obj);
        }
        return super.equals(obj);
    }

    public boolean equals(HDMapTile o) {
        return o.tx == tx && o.ty == ty && o.getWorld().equals(getWorld()) && (perspective.equals(o.perspective));
    }

    public String getKey() {
        return getWorld().getName() + "." + perspective.getName();
    }

    @Override
    public String toString() {
        return getWorld().getName() + ":" + getFilename();
    }
    
    @Override
    public boolean isBiomeDataNeeded() { return MapManager.mapman.hdmapman.isBiomeDataNeeded(this); }
    
    @Override
    public boolean isHightestBlockYDataNeeded() { return MapManager.mapman.hdmapman.isHightestBlockYDataNeeded(this); }
    
    @Override
    public boolean isRawBiomeDataNeeded() { return MapManager.mapman.hdmapman.isRawBiomeDataNeeded(this); }
    
    @Override
    public boolean isBlockTypeDataNeeded() { return MapManager.mapman.hdmapman.isBlockTypeDataNeeded(this); }
    
    public boolean render(MapChunkCache cache) {
        return perspective.render(cache, this);
    }
    
    public List<DynmapChunk> getRequiredChunks() {
        return perspective.getRequiredChunks(this);
    }
    
    public MapTile[] getAdjecentTiles() {
        return perspective.getAdjecentTiles(this);
    }
}
