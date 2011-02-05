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
	
	public abstract MapTile[] getTiles(Location l);
	public abstract MapTile[] getAdjecentTiles(MapTile tile);
	public abstract DynmapChunk[] getRequiredChunks(MapTile tile);
	public abstract boolean render(MapTile tile);
	public abstract boolean isRendered(MapTile tile);
}
