package org.dynmap.kzedmap;

import java.io.File;

import org.bukkit.World;
import org.dynmap.MapTile;

public class KzedMapTile extends MapTile {
    public KzedMap map;
    public MapTileRenderer renderer;
    public int px, py;

    // Hack.
    public File file = null;

    public KzedMapTile(World world, KzedMap map, MapTileRenderer renderer, int px, int py) {
        super(world, map);
        this.map = map;
        this.renderer = renderer;
        this.px = px;
        this.py = py;
    }

    @Override
    public String getFilename() {
        return renderer.getName() + "_" + px + "_" + py + ".png";
    }

    @Override
    public String getDayFilename() {
        return renderer.getName() + "_day_" + px + "_" + py + ".png";
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
