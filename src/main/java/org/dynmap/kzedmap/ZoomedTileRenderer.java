package org.dynmap.kzedmap;

import java.io.File;
import java.util.Map;
import org.dynmap.MapChunkCache;
public class ZoomedTileRenderer {
    public ZoomedTileRenderer(Map<String, Object> configuration) {
    }

    public void render(MapChunkCache cache, final KzedZoomedMapTile zt, final File outputPath) {
        return;    /* Doing this in Default render, since image already loaded */
    }
}
