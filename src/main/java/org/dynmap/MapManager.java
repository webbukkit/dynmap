package org.dynmap;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.config.ConfigurationNode;
import org.dynmap.debug.Debugger;

public class MapManager extends Thread {
    protected static final Logger log = Logger.getLogger("Minecraft");

    private World world;
    private Debugger debugger;
    private MapType[] maps;
    public StaleQueue staleQueue;
    public UpdateQueue updateQueue;
    public PlayerList playerList;

    /* lock for our data structures */
    public static final Object lock = new Object();

    /* whether the worker thread should be running now */
    private boolean running = false;

    /* path to image tile directory */
    public File tileDirectory;

    /* web files location */
    public File webDirectory;

    /* bind web server to ip-address */
    public String bindaddress = "0.0.0.0";

    /* port to run web server on */
    public int serverport = 8123;

    /* time to pause between rendering tiles (ms) */
    public int renderWait = 500;

    public boolean loadChunks = true;

    public void debug(String msg) {
        debugger.debug(msg);
    }

    private static File combinePaths(File parent, String path) {
        return combinePaths(parent, new File(path));
    }

    private static File combinePaths(File parent, File path) {
        if (path.isAbsolute())
            return path;
        return new File(parent, path.getPath());
    }

    public MapManager(World world, Debugger debugger, ConfigurationNode configuration) {
        this.world = world;
        this.debugger = debugger;
        this.staleQueue = new StaleQueue();
        this.updateQueue = new UpdateQueue();

        tileDirectory = combinePaths(DynmapPlugin.dataRoot, configuration.getString("tilespath", "web/tiles"));
        webDirectory = combinePaths(DynmapPlugin.dataRoot, configuration.getString("webpath", "web"));
        renderWait = (int) (configuration.getDouble("renderinterval", 0.5) * 1000);
        loadChunks = configuration.getBoolean("loadchunks", true);

        if (!tileDirectory.isDirectory())
            tileDirectory.mkdirs();

        maps = loadMapTypes(configuration);
    }

    void renderFullWorldAsync(Location l) {
        fullmapTiles.clear();
        fullmapTilesRendered.clear();
        debugger.debug("Full render starting...");
        for (MapType map : maps) {
            for (MapTile tile : map.getTiles(l)) {
                fullmapTiles.add(tile);
                invalidateTile(tile);
            }
        }
        debugger.debug("Full render finished.");
    }

    void renderFullWorld(Location l) {
        debugger.debug("Full render starting...");
        for (MapType map : maps) {
            HashSet<MapTile> found = new HashSet<MapTile>();
            LinkedList<MapTile> renderQueue = new LinkedList<MapTile>();

            for (MapTile tile : map.getTiles(l)) {
                if (!(found.contains(tile) || map.isRendered(tile))) {
                    found.add(tile);
                    renderQueue.add(tile);
                }
            }
            while (!renderQueue.isEmpty()) {
                MapTile tile = renderQueue.pollFirst();

                loadRequiredChunks(tile);
                debugger.debug("renderQueue: " + renderQueue.size() + "/" + found.size());
                if (map.render(tile)) {
                    found.remove(tile);
                    updateQueue.pushUpdate(new Client.Tile(tile.getName()));
                    for (MapTile adjTile : map.getAdjecentTiles(tile)) {
                        if (!(found.contains(adjTile) || map.isRendered(adjTile))) {
                            found.add(adjTile);
                            renderQueue.add(adjTile);
                        }
                    }
                }
                found.remove(tile);
                System.gc();
            }
        }
        debugger.debug("Full render finished.");
    }

    public HashSet<MapTile> fullmapTiles = new HashSet<MapTile>();
    public boolean fullmapRenderStarting = false;
    public HashSet<MapTile> fullmapTilesRendered = new HashSet<MapTile>();

    void handleFullMapRender(MapTile tile) {
        if (!fullmapTiles.contains(tile)) {
            debugger.debug("Non fullmap-render tile: " + tile);
            return;
        }
        fullmapTilesRendered.add(tile);
        MapType map = tile.getMap();
        MapTile[] adjecenttiles = map.getAdjecentTiles(tile);
        for (int i = 0; i < adjecenttiles.length; i++) {
            MapTile adjecentTile = adjecenttiles[i];
            if (!fullmapTiles.contains(adjecentTile)) {
                fullmapTiles.add(adjecentTile);
                staleQueue.pushStaleTile(adjecentTile);
            }
        }
        debugger.debug("Queue size: " + staleQueue.size() + "+" + fullmapTilesRendered.size() + "/" + fullmapTiles.size());
    }

    private boolean hasEnoughMemory() {
        return Runtime.getRuntime().freeMemory() >= 100 * 1024 * 1024;
    }

    private void waitForMemory() {
        if (!hasEnoughMemory()) {
            debugger.debug("Waiting for memory...");
            // Wait until there is at least 50mb of free memory.
            do {
                System.gc();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (!hasEnoughMemory());
            debugger.debug(Runtime.getRuntime().freeMemory() / (1024 * 1024) + "MB of memory free, will continue...");
        }
    }

    private void loadRequiredChunks(MapTile tile) {
        if (!loadChunks)
            return;
        waitForMemory();

        // Actually load the chunks.
        for (DynmapChunk chunk : tile.getMap().getRequiredChunks(tile)) {
            if (!world.isChunkLoaded(chunk.x, chunk.y))
                world.loadChunk(chunk.x, chunk.y);
        }
    }

    private MapType[] loadMapTypes(ConfigurationNode configuration) {
        List<?> configuredMaps = (List<?>) configuration.getProperty("maps");
        ArrayList<MapType> mapTypes = new ArrayList<MapType>();
        for (Object configuredMapObj : configuredMaps) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> configuredMap = (Map<String, Object>) configuredMapObj;
                String typeName = (String) configuredMap.get("class");
                log.info("Loading map '" + typeName.toString() + "'...");
                Class<?> mapTypeClass = Class.forName(typeName);
                Constructor<?> constructor = mapTypeClass.getConstructor(MapManager.class, World.class, Debugger.class, Map.class);
                MapType mapType = (MapType) constructor.newInstance(this, world, debugger, configuredMap);
                mapTypes.add(mapType);
            } catch (Exception e) {
                debugger.error("Error loading map", e);
            }
        }
        MapType[] result = new MapType[mapTypes.size()];
        mapTypes.toArray(result);
        return result;
    }

    /* initialize and start map manager */
    public void startManager() {
        synchronized (lock) {
            running = true;
            this.start();
            try {
                this.setPriority(MIN_PRIORITY);
                log.info("Set minimum priority for worker thread");
            } catch (SecurityException e) {
                log.info("Failed to set minimum priority for worker thread!");
            }
        }
    }

    /* stop map manager */
    public void stopManager() {
        synchronized (lock) {
            if (!running)
                return;

            log.info("Stopping map renderer...");
            running = false;

            try {
                this.join();
            } catch (InterruptedException e) {
                log.info("Waiting for map renderer to stop is interrupted");
            }
        }
    }

    /* the worker/renderer thread */
    public void run() {
        try {
            log.info("Map renderer has started.");

            while (running) {
                MapTile t = staleQueue.popStaleTile();
                if (t != null) {
                    loadRequiredChunks(t);

                    debugger.debug("Rendering tile " + t + "...");
                    boolean isNonEmptyTile = t.getMap().render(t);
                    updateQueue.pushUpdate(new Client.Tile(t.getName()));

                    if (isNonEmptyTile)
                        handleFullMapRender(t);

                    try {
                        Thread.sleep(renderWait);
                    } catch (InterruptedException e) {
                    }
                } else {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }
                }
            }

            log.info("Map renderer has stopped.");
        } catch (Exception ex) {
            debugger.error("Exception on rendering-thread: " + ex.toString());
            ex.printStackTrace();
        }
    }

    public void touch(int x, int y, int z) {
        for (int i = 0; i < maps.length; i++) {
            MapTile[] tiles = maps[i].getTiles(new Location(world, x, y, z));
            for (int j = 0; j < tiles.length; j++) {
                invalidateTile(tiles[j]);
            }
        }
    }

    public void invalidateTile(MapTile tile) {
        debugger.debug("Invalidating tile " + tile.getName());
        staleQueue.pushStaleTile(tile);
    }
}
