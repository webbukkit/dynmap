package org.dynmap.hdmap;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapWorld;
import org.dynmap.MapManager;
import org.dynmap.MapType;

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

    public HDMapTile(DynmapWorld world, String parm) throws Exception {
        super(world);
        
        String[] parms = parm.split(",");
        if(parms.length < 3) throw new Exception("wrong parameter count");
        this.tx = Integer.parseInt(parms[0]);
        this.ty = Integer.parseInt(parms[1]);
        this.perspective = MapManager.mapman.hdmapman.perspectives.get(parms[2]);
        if(this.perspective == null) throw new Exception("invalid perspective");
    }
    
    @Override
    protected String saveTileData() {
        return String.format("%d,%d,%s", tx, ty, perspective.getName());
    }

    @Override
    public String getFilename() {
        return getFilename("hdmap", MapType.ImageFormat.FORMAT_PNG);
    }

    public String getFilename(String prefix, MapType.ImageFormat format) {
        return prefix + "/"  + (tx >> 5) + '_' + (ty >> 5) + '/' + tx + "_" + ty + "." + format.getFileExt();
    }

    @Override
    public String getDayFilename() {
        return getDayFilename("hdmap", MapType.ImageFormat.FORMAT_PNG);
    }

    public String getDayFilename(String prefix, MapType.ImageFormat format) {
        return prefix + "_day/"  + (tx >> 5) + '_' + (ty >> 5) + '/' + tx + "_" + ty + "." + format.getFileExt();
    }
    
    @Override
    public int hashCode() {
        return tx ^ ty ^ perspective.getName().hashCode() ^ world.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HDMapTile) {
            return equals((HDMapTile) obj);
        }
        return false;
    }

    public boolean equals(HDMapTile o) {
        return o.tx == tx && o.ty == ty && (perspective == o.perspective) && (o.world == world);
    }

    public String getKey(String prefix) {
        return world.getName() + "." + prefix;
    }

    @Override
    public String toString() {
        return world.getName() + ":" + getFilename();
    }
    
    @Override
    public boolean isBiomeDataNeeded() { return MapManager.mapman.hdmapman.isBiomeDataNeeded(this); }
    
    @Override
    public boolean isHightestBlockYDataNeeded() { return MapManager.mapman.hdmapman.isHightestBlockYDataNeeded(this); }
    
    @Override
    public boolean isRawBiomeDataNeeded() { return MapManager.mapman.hdmapman.isRawBiomeDataNeeded(this); }
    
    @Override
    public boolean isBlockTypeDataNeeded() { return MapManager.mapman.hdmapman.isBlockTypeDataNeeded(this); }
    
    public boolean render(MapChunkCache cache, String mapname) {
        return perspective.render(cache, this, mapname);
    }
    
    public List<DynmapChunk> getRequiredChunks() {
        return perspective.getRequiredChunks(this);
    }
    
    public MapTile[] getAdjecentTiles() {
        return perspective.getAdjecentTiles(this);
    }
    
    public int tileOrdinalX() { return tx; }
    public int tileOrdinalY() { return ty; }

}
