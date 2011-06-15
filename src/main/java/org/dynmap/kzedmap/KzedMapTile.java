package org.dynmap.kzedmap;

import org.dynmap.DynmapWorld;
import java.io.File;
import org.dynmap.MapTile;

public class KzedMapTile extends MapTile {
    public KzedMap map;
    public MapTileRenderer renderer;
    public int px, py;
    private String fname;
    private String fname_day;

    // Hack.
    public File file = null;

    public KzedMapTile(DynmapWorld world, KzedMap map, MapTileRenderer renderer, int px, int py) {
        super(world, map);
        this.map = map;
        this.renderer = renderer;
        this.px = px;
        this.py = py;
    }

    @Override
    public String getFilename() {
        if(fname == null) {
            if(world.bigworld)        
                fname = renderer.getName() + "/"  + (px >> 12) + '_' + (py >> 12) + '/' + px + "_" + py + ".png";
            else
                fname = renderer.getName() + "_" + px + "_" + py + ".png";            
        }
        return fname;
    }

    @Override
    public String getDayFilename() {
        if(fname_day == null) {
            if(world.bigworld)        
                fname_day = renderer.getName() + "_day/"  + (px >> 12) + '_' + (py >> 12) + '/' + px + "_" + py + ".png";
            else
                fname_day = renderer.getName() + "_day_" + px + "_" + py + ".png";            
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
        return getWorld().getName() + "." + renderer.getName();
    }

    public String toString() {
        return getWorld().getName() + ":" + getFilename();
    }
}
