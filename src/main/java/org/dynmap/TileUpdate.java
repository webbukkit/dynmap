package org.dynmap;

/* this class stores a tile update */

public class TileUpdate {
	public long at;
	public MapTile tile;

	public TileUpdate(long at, MapTile tile)
	{
		this.at = at;
		this.tile = tile;
	}
}
