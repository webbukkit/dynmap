package org.dynmap;

import java.io.File;
import java.util.List;

import org.bukkit.Location;
import org.dynmap.utils.MapChunkCache;
import org.json.simple.JSONObject;

public abstract class MapType {
    public Event<MapTile> onTileInvalidated = new Event<MapTile>();

    public abstract MapTile[] getTiles(Location l);

    public abstract MapTile[] getAdjecentTiles(MapTile tile);

    public abstract List<DynmapChunk> getRequiredChunks(MapTile tile);

    public abstract boolean render(MapChunkCache cache, MapTile tile, File outputFile);
    
    public void buildClientConfiguration(JSONObject worldObject) {
    }
    
    public abstract String getName();
    
    public boolean isBiomeDataNeeded() { return false; }
    public boolean isHightestBlockYDataNeeded() { return false; }
    public boolean isRawBiomeDataNeeded() { return false; }
    public boolean isBlockTypeDataNeeded() { return true; }
 
    public abstract List<String> baseZoomFilePrefixes();
    public abstract int baseZoomFileStepSize();
    /* How many bits of coordinate are shifted off to make big world directory name */
    public abstract int getBigWorldShift();

    /**
     * Step sequence for creating zoomed file: first index is top-left, second top-right, third bottom-left, forth bottom-right
     * Values correspond to tile X,Y (0), X+step,Y (1), X,Y+step (2), X+step,Y+step (3) 
     */
    public abstract int[] zoomFileStepSequence();
}
