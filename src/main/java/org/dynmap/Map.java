package org.dynmap;

import org.bukkit.Block;
import org.bukkit.Location;
import org.bukkit.World;

public abstract class Map {
	private World world;
	public World getWorld() {
		return world;
	}
	
	private StaleQueue staleQueue;
	
	public Map(World world, StaleQueue queue) {
		this.world = world;
		this.staleQueue = queue;
	}
	
	public void invalidateTile(MapTile tile) {
		staleQueue.pushStaleTile(tile);
	}
	
	public abstract void touch(Location l);
	public abstract void render(MapTile tile);
}
