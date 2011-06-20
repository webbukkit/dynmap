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
    
}
