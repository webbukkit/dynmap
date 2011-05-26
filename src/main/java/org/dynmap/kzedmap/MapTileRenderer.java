package org.dynmap.kzedmap;

import java.io.File;
import org.dynmap.MapChunkCache;

import org.json.simple.JSONObject;

public interface MapTileRenderer {
    String getName();

    boolean render(MapChunkCache cache, KzedMapTile tile, File outputFile);

    void buildClientConfiguration(JSONObject worldObject);
}
