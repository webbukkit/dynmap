package org.dynmap;

import java.io.File;

import org.bukkit.Location;

public abstract class MapType {
    public Event<MapTile> onTileInvalidated = new Event<MapTile>();
    
    public abstract MapTile[] getTiles(Location l);

    public abstract MapTile[] getAdjecentTiles(MapTile tile);

    public abstract DynmapChunk[] getRequiredChunks(MapTile tile);

    public abstract boolean render(MapTile tile, File outputFile);
}
