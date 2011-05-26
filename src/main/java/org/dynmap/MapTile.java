package org.dynmap;

import org.bukkit.World;

public abstract class MapTile {
    private World world;
    private MapType map;

    public World getWorld() {
        return world;
    }

    public MapType getMap() {
        return map;
    }

    public abstract String getFilename();

    public MapTile(World world, MapType map) {
        this.world = world;
        this.map = map;
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
}
