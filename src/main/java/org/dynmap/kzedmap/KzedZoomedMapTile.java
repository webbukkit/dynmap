package org.dynmap.kzedmap;

import org.bukkit.World;
import org.dynmap.MapTile;

public class KzedZoomedMapTile extends MapTile {
    @Override
    public String getFilename() {
        return "z" + originalTile.renderer.getName() + "_" + getTileX() + "_" + getTileY() + ".png";
    }

    @Override
    public String getDayFilename() {
        return "z" + originalTile.renderer.getName() + "_day_" + getTileX() + "_" + getTileY() + ".png";
    }

    public KzedMapTile originalTile;

    public KzedZoomedMapTile(World world, KzedMap map, KzedMapTile original) {
        super(world, map);
        this.originalTile = original;
    }

    public int getTileX() {
        return ztilex(originalTile.px + KzedMap.tileWidth);
    }

    public int getTileY() {
        return ztiley(originalTile.py);
    }

    private static int ztilex(int x) {
        if (x < 0)
            return x + (x % (KzedMap.tileWidth * 2));
        else
            return x - (x % (KzedMap.tileWidth * 2));
    }

    /* zoomed-out tile Y for tile position y */
    private static int ztiley(int y) {
        if (y < 0)
            return y + (y % (KzedMap.tileHeight * 2));
        else
            return y - (y % (KzedMap.tileHeight * 2));
    }

    @Override
    public int hashCode() {
        return getFilename().hashCode() ^ getWorld().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof KzedZoomedMapTile) {
            return ((KzedZoomedMapTile) obj).originalTile.equals(originalTile);
        }
        return super.equals(obj);
    }
}
