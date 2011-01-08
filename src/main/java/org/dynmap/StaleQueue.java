package org.dynmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Set;

public class StaleQueue {
	/* a list of MapTiles to be updated */
	private LinkedList<MapTile> staleTilesQueue;
	private Set<MapTile> staleTiles;

	/* this list stores the tile updates */
	public LinkedList<TileUpdate> tileUpdates = null;
	
	/* remember up to this old tile updates (ms) */
	private static final int maxTileAge = 60000;
	
	public StaleQueue() {
		staleTilesQueue = new LinkedList<MapTile>();
		staleTiles = new HashSet<MapTile>();
		tileUpdates = new LinkedList<TileUpdate>();
	}
	
	/* put a MapTile that needs to be regenerated on the list of stale tiles */
	public boolean pushStaleTile(MapTile m)
	{
		synchronized(MapManager.lock) {
			if(staleTiles.add(m)) {
				staleTilesQueue.addLast(m);
				return true;
			}
			return false;
		}
	}
	
	/* get next MapTile that needs to be regenerated, or null
	 * the mapTile is removed from the list of stale tiles! */
	public MapTile popStaleTile()
	{
		synchronized(MapManager.lock) {
			try {
				MapTile t = staleTilesQueue.removeFirst();
				if(!staleTiles.remove(t)) {
					// This should never happen.
				}
				return t;
			} catch(NoSuchElementException e) {
				return null;
			}
		}
	}
	
	public void freshenTile(MapTile t) {
		long now = System.currentTimeMillis();
		long deadline = now - maxTileAge;
		synchronized(MapManager.lock) {
			ListIterator<TileUpdate> it = tileUpdates.listIterator(0);
			while(it.hasNext()) {
				TileUpdate tu = it.next();
				if(tu.at < deadline || tu.tile == t)
					it.remove();
			}
			tileUpdates.addLast(new TileUpdate(now, t));
		}
	}
	
	private ArrayList<TileUpdate> tmpupdates = new ArrayList<TileUpdate>();
	public TileUpdate[] getTileUpdates(long cutoff) {
		long now = System.currentTimeMillis();
		long deadline = now - maxTileAge;
		TileUpdate[] updates;
		synchronized(MapManager.lock) {
			tmpupdates.clear();
			Iterator<TileUpdate> it = tileUpdates.descendingIterator();
			while(it.hasNext()) {
				TileUpdate tu = it.next();
				if(tu.at >= cutoff) { // Tile is new.
					tmpupdates.add(tu);
				} else if(tu.at < deadline) { // Tile is too old, removing this one (will eventually decrease).
					it.remove();
					break;
				} else { // Tile is old, but not old enough for removal.
					break;
				}
			}
			updates = new TileUpdate[tmpupdates.size()];
			tmpupdates.toArray(updates);
		}
		return updates;
	}

}
