package org.dynmap;

public abstract class MapTile {
	private MapType map;
	public MapType getMap() {
		return map;
	}
	
	public abstract String getName();
	
	public MapTile(MapType map) {
		this.map = map;
	}
}
