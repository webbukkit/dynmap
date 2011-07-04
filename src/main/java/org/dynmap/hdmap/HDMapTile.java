package org.dynmap.hdmap;

import org.dynmap.DynmapWorld;
import java.io.File;
import org.dynmap.MapTile;

public class HDMapTile extends MapTile {
    public HDMap map;
    public HDMapTileRenderer renderer;
    public int tx, ty;  /* Tile X and Tile Y are in tile coordinates (pixels/tile-size) */
    private String fname;
    
    public HDMapTile(DynmapWorld world, HDMap map, HDMapTileRenderer renderer, int tx, int ty) {
        super(world, map);
        this.map = map;
        this.renderer = renderer;
        this.tx = tx;
        this.ty = ty;
    }

    @Override
    public String getFilename() {
        if(fname == null) {
            fname = renderer.getName() + "/"  + (tx >> 5) + '_' + (ty >> 5) + '/' + tx + "_" + ty + ".png";
        }
        return fname;
    }

    @Override
    public String getDayFilename() {
        return getFilename();
    }

    @Override
    public int hashCode() {
        return getFilename().hashCode() ^ getWorld().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HDMapTile) {
            return equals((HDMapTile) obj);
        }
        return super.equals(obj);
    }

    public boolean equals(HDMapTile o) {
        return o.tx == tx && o.ty == ty && o.renderer == o.renderer && o.getWorld().equals(getWorld());
    }

    public String getKey() {
        return getWorld().getName() + "." + renderer.getName();
    }

    public String toString() {
        return getWorld().getName() + ":" + getFilename();
    }
}
