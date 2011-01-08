package org.dynmap;

public abstract class MapTile {
	private Map map;
	public Map getMap() {
		return map;
	}
	
	public abstract String getName();
	
	public MapTile(Map map) {
		this.map = map;
	}
}
