package org.dynmap.hdmap;

import org.dynmap.DynmapWorld;
import java.io.File;
import org.dynmap.MapTile;

public class HDMapTile extends MapTile {
    public HDMap map;
    public int tx, ty;  /* Tile X and Tile Y are in tile coordinates (pixels/tile-size) */
    
    public HDMapTile(DynmapWorld world, HDMap map, int tx, int ty) {
        super(world, map);
        this.map = map;
        this.tx = tx;
        this.ty = ty;
    }

    @Override
    public String getFilename() {
        return getFilename("hdmap");
    }

    public String getFilename(String shader) {
        return shader + "/"  + (tx >> 5) + '_' + (ty >> 5) + '/' + tx + "_" + ty + ".png";
    }

    @Override
    public String getDayFilename() {
        return getDayFilename("hdmap");
    }

    public String getDayFilename(String shader) {
        return shader + "_day/"  + (tx >> 5) + '_' + (ty >> 5) + '/' + tx + "_" + ty + ".png";
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
        return o.tx == tx && o.ty == ty && o.getWorld().equals(getWorld());
    }

    public String getKey() {
        return getWorld().getName() + ".hdmap";
    }

    public String toString() {
        return getWorld().getName() + ":" + getFilename();
    }
}
