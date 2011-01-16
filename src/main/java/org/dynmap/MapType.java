package org.dynmap;

import org.bukkit.Location;
import org.bukkit.World;
import org.dynmap.debug.Debugger;

public abstract class MapType {
	private MapManager manager;
	public MapManager getMapManager() {
		return manager;
	}
	
	private World world;
	public World getWorld() {
		return world;
	}
	
	private Debugger debugger;
	public Debugger getDebugger() {
		return debugger;
	}
	
	public MapType(MapManager manager, World world, Debugger debugger) {
		this.manager = manager;
		this.world = world;
		this.debugger = debugger;
	}
	
	public void invalidateTile(MapTile tile) {
		manager.invalidateTile(tile);
	}
	
	public abstract void touch(Location l);
	public abstract void render(MapTile tile);
}
