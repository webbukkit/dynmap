package org.dynmap.kzedmap;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.dynmap.DynmapChunk;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.debug.Debug;

public class KzedMap extends MapType {
    protected static final Logger log = Logger.getLogger("Minecraft");

    /* dimensions of a map tile */
    public static final int tileWidth = 128;
    public static final int tileHeight = 128;

    /*
     * (logical!) dimensions of a zoomed out map tile must be twice the size of
     * the normal tile
     */
    public static final int zTileWidth = 256;
    public static final int zTileHeight = 256;

    /* map x, y, z for projection origin */
    public static final int anchorx = 0;
    public static final int anchory = 127;
    public static final int anchorz = 0;

    MapTileRenderer[] renderers;
    ZoomedTileRenderer zoomrenderer;

    public KzedMap(Map<String, Object> configuration) {
        renderers = loadRenderers(configuration);
        zoomrenderer = new ZoomedTileRenderer(configuration);
    }

    private MapTileRenderer[] loadRenderers(Map<String, Object> configuration) {
        List<?> configuredRenderers = (List<?>) configuration.get("renderers");
        ArrayList<MapTileRenderer> renderers = new ArrayList<MapTileRenderer>();
        for (Object configuredRendererObj : configuredRenderers) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> configuredRenderer = (Map<String, Object>) configuredRendererObj;
                String typeName = (String) configuredRenderer.get("class");
                log.info("Loading renderer '" + typeName.toString() + "'...");
                Class<?> mapTypeClass = Class.forName(typeName);
                Constructor<?> constructor = mapTypeClass.getConstructor(Map.class);
                MapTileRenderer mapTileRenderer = (MapTileRenderer) constructor.newInstance(configuredRenderer);
                renderers.add(mapTileRenderer);
            } catch (Exception e) {
                Debug.error("Error loading renderer", e);
                e.printStackTrace();
            }
        }
        MapTileRenderer[] result = new MapTileRenderer[renderers.size()];
        renderers.toArray(result);
        return result;
    }

    @Override
    public MapTile[] getTiles(Location l) {
        World world = l.getWorld();
        
        int x = l.getBlockX();
        int y = l.getBlockY();
        int z = l.getBlockZ();

        int dx = x - anchorx;
        int dy = y - anchory;
        int dz = z - anchorz;
        int px = dx + dz;
        int py = dx - dz - dy;

        int tx = tilex(px);
        int ty = tiley(py);

        ArrayList<MapTile> tiles = new ArrayList<MapTile>();

        addTile(tiles, world, tx, ty);

        boolean ledge = tilex(px - 4) != tx;
        boolean tedge = tiley(py - 4) != ty;
        boolean redge = tilex(px + 4) != tx;
        boolean bedge = tiley(py + 4) != ty;

        if (ledge)
            addTile(tiles, world, tx - tileWidth, ty);
        if (redge)
            addTile(tiles, world, tx + tileWidth, ty);
        if (tedge)
            addTile(tiles, world, tx, ty - tileHeight);
        if (bedge)
            addTile(tiles, world, tx, ty + tileHeight);

        if (ledge && tedge)
            addTile(tiles, world, tx - tileWidth, ty - tileHeight);
        if (ledge && bedge)
            addTile(tiles, world, tx - tileWidth, ty + tileHeight);
        if (redge && tedge)
            addTile(tiles, world, tx + tileWidth, ty - tileHeight);
        if (redge && bedge)
            addTile(tiles, world, tx + tileWidth, ty + tileHeight);

        MapTile[] result = new MapTile[tiles.size()];
        tiles.toArray(result);
        return result;
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        if (tile instanceof KzedMapTile) {
            KzedMapTile t = (KzedMapTile) tile;
            World world = tile.getWorld();
            MapTileRenderer renderer = t.renderer;
            return new MapTile[] {
                new KzedMapTile(world, this, renderer, t.px - tileWidth, t.py),
                new KzedMapTile(world, this, renderer, t.px + tileWidth, t.py),
                new KzedMapTile(world, this, renderer, t.px, t.py - tileHeight),
                new KzedMapTile(world, this, renderer, t.px, t.py + tileHeight) };
        }
        return new MapTile[0];
    }

    public void addTile(ArrayList<MapTile> tiles, World world, int px, int py) {
        for (int i = 0; i < renderers.length; i++) {
            tiles.add(new KzedMapTile(world, this, renderers[i], px, py));
        }
    }

    public void invalidateTile(MapTile tile) {
        onTileInvalidated.trigger(tile);
    }

    /**
     * Test if point x,z is inside rectangle with corner at r0x,r0z and with
     * size vectors s1x,s1z and s2x,s2z
     * 
     */
    private boolean testPointInRectangle(int x, int z, int r0x, int r0z, int s1x, int s1z,
            int s2x, int s2z) {
        int xr = x - r0x;
        int zr = z - r0z;   /* Get position relative to rectangle corner */
        int dots1 = xr*s1x + zr*s1z;
        int dots2 = xr*s2x + zr*s2z;
        /* If dot product of relative point and each side is between zero and dot product
         * of each side and itself, we're inside 
         */
        if((dots1 >= 0) && (dots1 <= (s1x*s1x+s1z*s1z)) &&
                (dots2 >= 0) && (dots2 <= (s2x*s2x+s2z*s2z))) {
            return true;
        }
        return false;
    }
    @Override
    public DynmapChunk[] getRequiredChunks(MapTile tile) {
        if (tile instanceof KzedMapTile) {
            KzedMapTile t = (KzedMapTile) tile;
            
            int ix = KzedMap.anchorx + t.px / 2 + t.py / 2;
            //int iy = 127;
            int iz = KzedMap.anchorz + t.px / 2 - t.py / 2;
            
            int x1 = ix - KzedMap.tileHeight / 2;
            int x2 = ix + KzedMap.tileWidth / 2 + KzedMap.tileHeight / 2;

            int z1 = iz - KzedMap.tileHeight / 2;
            int z2 = iz + KzedMap.tileWidth / 2 + KzedMap.tileHeight / 2;

            int x, z;

            /* Actual pattern of chunks needed is create by the slanted
             * square prism corresponding to the render path of the tile.
             * Top of prism (corresponding to y=127) is diamond shape from
             * ix, iz to ix+64,iz+64 to ix+128,iz to ix+64,iz-64
             * Bottom is same shape, offset by -64 on x, +64 on z (net
             * render path to y=0), correspond to ix-64, iz+64 to
             * ix,iz+128 to ix+64,iz+64 to ix,iz.  Projection of
             * the prism on to the x,z plane (which is all that matters for
             * chunks) yields a diagonal rectangular area from ix-64(x1),iz+64 
             * to ix,iz+128(z2) to ix+128(x2),iz to ix+64,iz-64(z1).  
             * Chunks outside this are not needed - we scan a simple rectangle
             * (chunk grid aligned) and skip adding the ones that are outside. 
             * This results in 42% less chunks being loaded.
             */
            ArrayList<DynmapChunk> chunks = new ArrayList<DynmapChunk>();

            for (x = x1; x < x2; x += 16) {
                for (z = z1; z < z2; z += 16) {
                    /* If any of the chunk corners are inside the rectangle, we need it */
                    if((!testPointInRectangle(x, z, x1, iz + KzedMap.tileWidth/2,
                            KzedMap.tileWidth/2, KzedMap.tileHeight/2,
                            KzedMap.tileWidth, -KzedMap.tileHeight)) &&
                        (!testPointInRectangle(x+15, z, x1, iz + KzedMap.tileWidth/2,
                            KzedMap.tileWidth/2, KzedMap.tileHeight/2,
                            KzedMap.tileWidth, -KzedMap.tileHeight)) &&
                        (!testPointInRectangle(x+15, z+15, x1, iz + KzedMap.tileWidth/2,
                            KzedMap.tileWidth/2, KzedMap.tileHeight/2,
                            KzedMap.tileWidth, -KzedMap.tileHeight)) &&
                        (!testPointInRectangle(x, z+15, x1, iz + KzedMap.tileWidth/2,
                            KzedMap.tileWidth/2, KzedMap.tileHeight/2,
                            KzedMap.tileWidth, -KzedMap.tileHeight)))
                        continue;
                    DynmapChunk chunk = new DynmapChunk(x / 16, z / 16);
                    chunks.add(chunk);
                }
            }
            
            DynmapChunk[] result = new DynmapChunk[chunks.size()];
            chunks.toArray(result);
            return result;
        } else {
            return new DynmapChunk[0];
        }
    }

    @Override
    public boolean render(MapTile tile, File outputFile) {
        if (tile instanceof KzedZoomedMapTile) {
            zoomrenderer.render((KzedZoomedMapTile) tile, outputFile);
            return true;
        } else if (tile instanceof KzedMapTile) {
            return ((KzedMapTile) tile).renderer.render((KzedMapTile) tile, outputFile);
        }
        return false;
    }

    /* tile X for position x */
    static int tilex(int x) {
        if (x < 0)
            return x - (tileWidth + (x % tileWidth));
        else
            return x - (x % tileWidth);
    }

    /* tile Y for position y */
    static int tiley(int y) {
        if (y < 0)
            return y - (tileHeight + (y % tileHeight));
        else
            return y - (y % tileHeight);
    }

    /* zoomed-out tile X for tile position x */
    static int ztilex(int x) {
        if (x < 0)
            return x + x % zTileWidth;
        else
            return x - (x % zTileWidth);
    }

    /* zoomed-out tile Y for tile position y */
    static int ztiley(int y) {
        if (y < 0)
            return y + y % zTileHeight;
        // return y - (zTileHeight + (y % zTileHeight));
        else
            return y - (y % zTileHeight);
    }
}
