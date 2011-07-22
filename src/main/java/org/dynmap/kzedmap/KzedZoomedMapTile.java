package org.dynmap.kzedmap;

import java.util.List;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapWorld;
import org.dynmap.MapTile;
import org.dynmap.utils.MapChunkCache;

public class KzedZoomedMapTile extends MapTile {
    private String fname;
    private String fname_day;

    @Override
    public String getFilename() {
        if(fname == null) {
            if(world.bigworld)
                fname = "z" + originalTile.renderer.getPrefix() + "/" + (getTileX()>>12) + '_' + 
                    (getTileY() >> 12) + '/' + getTileX() + "_" + getTileY() + ".png";
            else
                fname = "z" + originalTile.renderer.getPrefix() + "_" + getTileX() + "_" + getTileY() + ".png";
        }
        return fname;
    }

    @Override
    public String getDayFilename() {
        if(fname_day == null) {
            if(world.bigworld)
                fname_day = "z" + originalTile.renderer.getPrefix() + "_day/" + (getTileX()>>12) + '_' + 
                    (getTileY() >> 12) + '/' + getTileX() + "_" + getTileY() + ".png";
            else
                fname_day = "z" + originalTile.renderer.getPrefix() + "_day_" + getTileX() + "_" + getTileY() + ".png";
        }
        return fname_day;
    }

    public KzedMapTile originalTile;

    public KzedZoomedMapTile(DynmapWorld world, KzedMapTile original) {
        super(world);
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
    

    public String getKey() {
        return getWorld().getName() + ".z" + originalTile.renderer.getPrefix();
    }

    @Override
    public boolean render(MapChunkCache cache) {
        return false;
    }

    @Override
    public List<DynmapChunk> getRequiredChunks() {
        return null;
    }

    @Override
    public MapTile[] getAdjecentTiles() {
        return null;
    }

    public boolean isBiomeDataNeeded() { return originalTile.isBiomeDataNeeded(); }
    public boolean isHightestBlockYDataNeeded() { return false; }
    public boolean isRawBiomeDataNeeded() { return originalTile.isRawBiomeDataNeeded(); }
    public boolean isBlockTypeDataNeeded() { return true; }
    
}
