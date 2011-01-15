package org.dynmap.kzedmap;


public interface MapTileRenderer {
	String getName();
	void render(KzedMapTile tile, String path);
}
