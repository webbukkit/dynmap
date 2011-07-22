package org.dynmap.kzedmap;

import java.io.File;

import org.dynmap.DynmapWorld;
import org.dynmap.utils.MapChunkCache;

import org.json.simple.JSONObject;

public interface MapTileRenderer {
    String getPrefix();
    String getName();

    boolean render(MapChunkCache cache, KzedMapTile tile, File outputFile);

    void buildClientConfiguration(JSONObject worldObject, DynmapWorld w, KzedMap map);
    
    boolean isBiomeDataNeeded();
    boolean isRawBiomeDataNeeded();
    boolean isNightAndDayEnabled();
}
