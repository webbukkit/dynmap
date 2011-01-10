package org.dynmap.kzedmap;

import org.dynmap.MapTile;

public interface MapTileRenderer {
	String getName();
	void render(KzedMapTile tile, String path);
}
