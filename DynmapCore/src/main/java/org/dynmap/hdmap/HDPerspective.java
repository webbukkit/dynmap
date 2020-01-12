package org.dynmap.hdmap;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapWorld;
import org.dynmap.MapTile;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.TileFlags;
import org.dynmap.utils.Vector3D;
import org.json.simple.JSONObject;

import java.util.List;

public interface HDPerspective {
    /* Get name of perspective */
    String getName();

    /* Get tiles invalidated by change at given location */
    List<TileFlags.TileCoord> getTileCoords(DynmapWorld w, int x, int y, int z);

    /* Get tiles invalidated by change at given volume, defined by 2 opposite corner locations */
    List<TileFlags.TileCoord> getTileCoords(DynmapWorld w, int minx, int miny, int minz, int maxx, int maxy, int maxz);

    /* Get tiles adjacent to given tile */
    MapTile[] getAdjecentTiles(MapTile tile);

    /* Get chunks needed for given tile */
    List<DynmapChunk> getRequiredChunks(MapTile tile);

    /* Render given tile */
    boolean render(MapChunkCache cache, HDMapTile tile, String mapname);

    boolean isBiomeDataNeeded();

    boolean isHightestBlockYDataNeeded();

    boolean isRawBiomeDataNeeded();

    boolean isBlockTypeDataNeeded();

    double getScale();

    int getModelScale();

    void addClientConfiguration(JSONObject mapObject);

    void transformWorldToMapCoord(Vector3D input, Vector3D rslt);

    int hashCode();
}
