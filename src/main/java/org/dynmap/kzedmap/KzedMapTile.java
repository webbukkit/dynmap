package org.dynmap.kzedmap;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapWorld;
import org.dynmap.MapManager;

import java.io.File;
import java.util.List;

import org.dynmap.MapTile;
import org.dynmap.utils.MapChunkCache;

public class KzedMapTile extends MapTile {
    public KzedMap map;
    public MapTileRenderer renderer;
    public int px, py;
    private String fname;
    private String fname_day;

    // Hack.
    public File file = null;

    public KzedMapTile(DynmapWorld world, KzedMap map, MapTileRenderer renderer, int px, int py) {
        super(world);
        this.map = map;
        this.renderer = renderer;
        this.px = px;
        this.py = py;
    }

    @Override
    public String getFilename() {
        if(fname == null) {
            if(map.isBigWorldMap(world))        
                fname = renderer.getPrefix() + "/"  + (px >> 12) + '_' + (py >> 12) + '/' + px + "_" + py + ".png";
            else
                fname = renderer.getPrefix() + "_" + px + "_" + py + ".png";            
        }
        return fname;
    }

    @Override
    public String getDayFilename() {
        if(fname_day == null) {
            if(map.isBigWorldMap(world))        
                fname_day = renderer.getPrefix() + "_day/"  + (px >> 12) + '_' + (py >> 12) + '/' + px + "_" + py + ".png";
            else
                fname_day = renderer.getPrefix() + "_day_" + px + "_" + py + ".png";            
        }
        return fname_day;
    }

    @Override
    public int hashCode() {
        return getFilename().hashCode() ^ getWorld().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof KzedMapTile) {
            return equals((KzedMapTile) obj);
        }
        return super.equals(obj);
    }

    public boolean equals(KzedMapTile o) {
        return o.px == px && o.py == py && o.getWorld().equals(getWorld());
    }

    public String getKey() {
        return getWorld().getName() + "." + renderer.getPrefix();
    }

    public String toString() {
        return getWorld().getName() + ":" + getFilename();
    }
    
    public boolean render(MapChunkCache cache) {
        return map.render(cache, this, MapManager.mapman.getTileFile(this));
    }
    
    public List<DynmapChunk> getRequiredChunks() {
        return map.getRequiredChunks(this);
    }
    
    public MapTile[] getAdjecentTiles() {
        return map.getAdjecentTiles(this);
    }
    
    public boolean isBiomeDataNeeded() { return map.isBiomeDataNeeded(); }
    public boolean isHightestBlockYDataNeeded() { return false; }
    public boolean isRawBiomeDataNeeded() { return map.isRawBiomeDataNeeded(); }
    public boolean isBlockTypeDataNeeded() { return true; }

}
