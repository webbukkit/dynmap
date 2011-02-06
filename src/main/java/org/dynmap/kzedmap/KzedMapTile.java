package org.dynmap.kzedmap;

import java.util.logging.Logger;

import org.dynmap.MapTile;

public class KzedMapTile extends MapTile {
    protected static final Logger log = Logger.getLogger("Minecraft");

    public KzedMap map;

    public MapTileRenderer renderer;

    /* projection position */
    public int px, py;

    /* minecraft space origin */
    public int mx, my, mz;

    /* create new MapTile */
    public KzedMapTile(KzedMap map, MapTileRenderer renderer, int px, int py) {
        super(map);
        this.map = map;
        this.renderer = renderer;
        this.px = px;
        this.py = py;

        mx = KzedMap.anchorx + px / 2 + py / 2;
        my = KzedMap.anchory;
        mz = KzedMap.anchorz + px / 2 - py / 2;
    }

    @Override
    public String getName() {
        return renderer.getName() + "_" + px + "_" + py;
    }

    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof KzedMapTile) {
            return equals((KzedMapTile) obj);
        }
        return super.equals(obj);
    }

    public boolean equals(KzedMapTile o) {
        return o.getName().equals(getName());
    }

    /* return a simple string representation... */
    public String toString() {
        return getName();
    }
}
