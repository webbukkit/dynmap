package org.dynmap.render;

import org.dynmap.MapTile;

public class CombinedTileRenderer implements MapTileRenderer {
	private MapTileRenderer[] renderers;
	
	public CombinedTileRenderer(MapTileRenderer[] renderers) {
		this.renderers = renderers;
	}
	@Override
	public void render(MapTile tile) {
		for(MapTileRenderer renderer : renderers) {
			renderer.render(tile);
		}
	}

}
