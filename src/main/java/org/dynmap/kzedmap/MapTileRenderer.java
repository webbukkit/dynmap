package org.dynmap.kzedmap;

import java.io.File;
import org.dynmap.MapChunkCache;

public interface MapTileRenderer {
    String getName();

    boolean render(MapChunkCache cache, KzedMapTile tile, File outputFile);
}
