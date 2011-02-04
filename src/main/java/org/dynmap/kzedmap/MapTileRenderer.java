package org.dynmap.kzedmap;


public interface MapTileRenderer {
	String getName();
	boolean render(KzedMapTile tile, String path);
}
