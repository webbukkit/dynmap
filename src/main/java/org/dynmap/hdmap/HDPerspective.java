package org.dynmap.hdmap;

import java.util.List;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapLocation;
import org.dynmap.MapTile;
import org.dynmap.utils.MapChunkCache;
import org.json.simple.JSONObject;

public interface HDPerspective {
    /* Get name of perspective */
    String getName();
    /* Get tiles invalidated by change at given location */
    MapTile[] getTiles(DynmapLocation loc);
    /* Get tiles invalidated by change at given volume, defined by 2 opposite corner locations */
    MapTile[] getTiles(DynmapLocation loc0, int sx, int sy, int sz);
    /* Get tiles adjacent to given tile */
    MapTile[] getAdjecentTiles(MapTile tile);
    /* Get chunks needed for given tile */
    List<DynmapChunk> getRequiredChunks(MapTile tile);
    /* Render given tile */
    boolean render(MapChunkCache cache, HDMapTile tile, String mapname);
    
    public boolean isBiomeDataNeeded();
    public boolean isHightestBlockYDataNeeded();
    public boolean isRawBiomeDataNeeded();
    public boolean isBlockTypeDataNeeded();
    
    double getScale();
    int getModelScale();

    public void addClientConfiguration(JSONObject mapObject);
}
