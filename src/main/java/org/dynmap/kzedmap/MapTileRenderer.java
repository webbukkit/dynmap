package org.dynmap.kzedmap;

import java.io.File;

public interface MapTileRenderer {
    String getName();

    boolean render(KzedMapTile tile, File outputFile);
}
