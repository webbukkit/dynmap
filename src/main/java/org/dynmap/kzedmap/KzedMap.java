package org.dynmap.kzedmap;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.dynmap.DynmapChunk;
import org.dynmap.MapManager;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.debug.Debugger;

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

    public static java.util.Map<Integer, Color[]> colors;
    MapTileRenderer[] renderers;
    ZoomedTileRenderer zoomrenderer;

    public KzedMap(MapManager manager, World world, Debugger debugger, Map<String, Object> configuration) {
        super(manager, world, debugger);
        if (colors == null) {
            colors = loadColorSet("colors.txt");
        }

        renderers = loadRenderers(configuration);
        zoomrenderer = new ZoomedTileRenderer(debugger, configuration);
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
                Constructor<?> constructor = mapTypeClass.getConstructor(Debugger.class, Map.class);
                MapTileRenderer mapTileRenderer = (MapTileRenderer) constructor.newInstance(getDebugger(), configuredRenderer);
                renderers.add(mapTileRenderer);
            } catch (Exception e) {
                getDebugger().error("Error loading renderer", e);
            }
        }
        MapTileRenderer[] result = new MapTileRenderer[renderers.size()];
        renderers.toArray(result);
        return result;
    }

    @Override
    public MapTile[] getTiles(Location l) {
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

        addTile(tiles, tx, ty);

        boolean ledge = tilex(px - 4) != tx;
        boolean tedge = tiley(py - 4) != ty;
        boolean redge = tilex(px + 4) != tx;
        boolean bedge = tiley(py + 4) != ty;

        if (ledge)
            addTile(tiles, tx - tileWidth, ty);
        if (redge)
            addTile(tiles, tx + tileWidth, ty);
        if (tedge)
            addTile(tiles, tx, ty - tileHeight);
        if (bedge)
            addTile(tiles, tx, ty + tileHeight);

        if (ledge && tedge)
            addTile(tiles, tx - tileWidth, ty - tileHeight);
        if (ledge && bedge)
            addTile(tiles, tx - tileWidth, ty + tileHeight);
        if (redge && tedge)
            addTile(tiles, tx + tileWidth, ty - tileHeight);
        if (redge && bedge)
            addTile(tiles, tx + tileWidth, ty + tileHeight);

        MapTile[] result = new MapTile[tiles.size()];
        tiles.toArray(result);
        return result;
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        if (tile instanceof KzedMapTile) {
            KzedMapTile t = (KzedMapTile) tile;
            MapTileRenderer renderer = t.renderer;
            return new MapTile[] {
                new KzedMapTile(this, renderer, t.px - tileWidth, t.py),
                new KzedMapTile(this, renderer, t.px + tileWidth, t.py),
                new KzedMapTile(this, renderer, t.px, t.py - tileHeight),
                new KzedMapTile(this, renderer, t.px, t.py + tileHeight) };
        }
        return new MapTile[0];
    }

    public void addTile(ArrayList<MapTile> tiles, int px, int py) {
        for (int i = 0; i < renderers.length; i++) {
            tiles.add(new KzedMapTile(this, renderers[i], px, py));
        }
    }

    public void invalidateTile(MapTile tile) {
        getMapManager().invalidateTile(tile);
    }

    @Override
    public DynmapChunk[] getRequiredChunks(MapTile tile) {
        if (tile instanceof KzedMapTile) {
            KzedMapTile t = (KzedMapTile) tile;
            int x1 = t.mx - KzedMap.tileHeight / 2;
            int x2 = t.mx + KzedMap.tileWidth / 2 + KzedMap.tileHeight / 2;

            int z1 = t.mz - KzedMap.tileHeight / 2;
            int z2 = t.mz + KzedMap.tileWidth / 2 + KzedMap.tileHeight / 2;

            int x, z;

            ArrayList<DynmapChunk> chunks = new ArrayList<DynmapChunk>();
            for (x = x1; x < x2; x += 16) {
                for (z = z1; z < z2; z += 16) {
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
    public boolean render(MapTile tile) {
        if (tile instanceof KzedZoomedMapTile) {
            zoomrenderer.render((KzedZoomedMapTile) tile, getMapManager().tileDirectory.getAbsolutePath());
            return true;
        } else if (tile instanceof KzedMapTile) {
            return ((KzedMapTile) tile).renderer.render((KzedMapTile) tile, getMapManager().tileDirectory.getAbsolutePath());
        }
        return false;
    }

    @Override
    public boolean isRendered(MapTile tile) {
        if (tile instanceof KzedMapTile) {
            File tileFile = new File(DefaultTileRenderer.getPath((KzedMapTile) tile, getMapManager().tileDirectory.getAbsolutePath()));
            return tileFile.exists();
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

    public java.util.Map<Integer, Color[]> loadColorSet(String colorsetpath) {
        java.util.Map<Integer, Color[]> colors = new HashMap<Integer, Color[]>();

        InputStream stream;

        try {
            /* load colorset */
            File cfile = new File(colorsetpath);
            if (cfile.isFile()) {
                getDebugger().debug("Loading colors from '" + colorsetpath + "'...");
                stream = new FileInputStream(cfile);
            } else {
                getDebugger().debug("Loading colors from jar...");
                stream = KzedMap.class.getResourceAsStream("/colors.txt");
            }

            Scanner scanner = new Scanner(stream);
            int nc = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith("#") || line.equals("")) {
                    continue;
                }

                String[] split = line.split("\t");
                if (split.length < 17) {
                    continue;
                }

                Integer id = new Integer(split[0]);

                Color[] c = new Color[4];

                /* store colors by raycast sequence number */
                c[0] = new Color(Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3]), Integer.parseInt(split[4]));
                c[3] = new Color(Integer.parseInt(split[5]), Integer.parseInt(split[6]), Integer.parseInt(split[7]), Integer.parseInt(split[8]));
                c[1] = new Color(Integer.parseInt(split[9]), Integer.parseInt(split[10]), Integer.parseInt(split[11]), Integer.parseInt(split[12]));
                c[2] = new Color(Integer.parseInt(split[13]), Integer.parseInt(split[14]), Integer.parseInt(split[15]), Integer.parseInt(split[16]));

                colors.put(id, c);
                nc += 1;
            }
            scanner.close();
        } catch (Exception e) {
            getDebugger().error("Could not load colors", e);
            return null;
        }
        return colors;
    }
}
