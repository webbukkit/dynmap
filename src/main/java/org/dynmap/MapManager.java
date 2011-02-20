package org.dynmap;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.config.ConfigurationNode;
import org.dynmap.debug.Debug;

public class MapManager {
    protected static final Logger log = Logger.getLogger("Minecraft");

    private MapType[] mapTypes;
    public AsynchronousQueue<MapTile> tileQueue;
    
    public Map<String, UpdateQueue> worldUpdateQueues = new HashMap<String, UpdateQueue>();
    public ArrayList<String> worlds = new ArrayList<String>();
    
    public PlayerList playerList;

    /* lock for our data structures */
    public static final Object lock = new Object();

    public MapManager(ConfigurationNode configuration) {
        this.tileQueue = new AsynchronousQueue<MapTile>(new Handler<MapTile>() {
            @Override
            public void handle(MapTile t) {
                render(t);
            }
        }, (int) (configuration.getDouble("renderinterval", 0.5) * 1000));
        
        mapTypes = loadMapTypes(configuration);
        
        tileQueue.start();
    }

    void renderFullWorld(Location l) {
        World world = l.getWorld();
        log.info("Full render starting...");
        for (MapType map : mapTypes) {
            int requiredChunkCount = 200;
            HashSet<MapTile> found = new HashSet<MapTile>();
            HashSet<MapTile> rendered = new HashSet<MapTile>();
            LinkedList<MapTile> renderQueue = new LinkedList<MapTile>();
            LinkedList<DynmapChunk> loadedChunks = new LinkedList<DynmapChunk>();

            for (MapTile tile : map.getTiles(l)) {
                if (!found.contains(tile)) {
                    found.add(tile);
                    renderQueue.add(tile);
                }
            }
            while (!renderQueue.isEmpty()) {
                MapTile tile = renderQueue.pollFirst();

                DynmapChunk[] requiredChunks = tile.getMap().getRequiredChunks(tile);

                if (requiredChunks.length > requiredChunkCount)
                    requiredChunkCount = requiredChunks.length;
                // Unload old chunks.
                while (loadedChunks.size() >= requiredChunkCount - requiredChunks.length) {
                    DynmapChunk c = loadedChunks.pollFirst();
                    world.unloadChunk(c.x, c.z, false, true);
                }

                // Load the required chunks.
                for (DynmapChunk chunk : requiredChunks) {
                    boolean wasLoaded = world.isChunkLoaded(chunk.x, chunk.z);
                    world.loadChunk(chunk.x, chunk.z, false);
                    if (!wasLoaded)
                        loadedChunks.add(chunk);
                }

                if (render(tile)) {
                    found.remove(tile);
                    rendered.add(tile);
                    for (MapTile adjTile : map.getAdjecentTiles(tile)) {
                        if (!found.contains(adjTile) && !rendered.contains(adjTile)) {
                            found.add(adjTile);
                            renderQueue.add(adjTile);
                        }
                    }
                }
                found.remove(tile);
                System.gc();
            }

            // Unload remaining chunks to clean-up.
            while (!loadedChunks.isEmpty()) {
                DynmapChunk c = loadedChunks.pollFirst();
                world.unloadChunk(c.x, c.z, false, true);
            }
        }
        log.info("Full render finished.");
    }

    private MapType[] loadMapTypes(ConfigurationNode configuration) {
        Event.Listener<MapTile> invalitateListener = new Event.Listener<MapTile>() {
            @Override
            public void triggered(MapTile t) {
                invalidateTile(t);
            }
        };
        
        List<?> configuredMaps = (List<?>) configuration.getProperty("maps");
        ArrayList<MapType> mapTypes = new ArrayList<MapType>();
        for (Object configuredMapObj : configuredMaps) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> configuredMap = (Map<String, Object>) configuredMapObj;
                String typeName = (String) configuredMap.get("class");
                log.info("Loading map '" + typeName.toString() + "'...");
                Class<?> mapTypeClass = Class.forName(typeName);
                Constructor<?> constructor = mapTypeClass.getConstructor(Map.class);
                MapType mapType = (MapType) constructor.newInstance(configuredMap);
                mapType.onTileInvalidated.addListener(invalitateListener);
                mapTypes.add(mapType);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Error loading maptype", e);
                e.printStackTrace();
            }
        }
        MapType[] result = new MapType[mapTypes.size()];
        mapTypes.toArray(result);
        return result;
    }
    
    public void touch(Location l) {
        Debug.debug("Touched " + l.toString());
        for (int i = 0; i < mapTypes.length; i++) {
            MapTile[] tiles = mapTypes[i].getTiles(l);
            for (int j = 0; j < tiles.length; j++) {
                invalidateTile(tiles[j]);
            }
        }
    }

    public void invalidateTile(MapTile tile) {
        Debug.debug("Invalidating tile " + tile.getFilename());
        tileQueue.push(tile);
    }
    
    public void startRendering() {
        tileQueue.start();
    }
    
    public void stopRendering() {
        tileQueue.stop();
    }
    
    public boolean render(MapTile tile) {
        boolean result = tile.getMap().render(tile, getTileFile(tile));
        pushUpdate(tile.getWorld(), new Client.Tile(tile.getFilename(), System.currentTimeMillis()));
        return result;
    }
    
    
    private HashMap<World, File> worldTileDirectories = new HashMap<World, File>();
    private File getTileFile(MapTile tile) {
        World world = tile.getWorld();
        File worldTileDirectory = worldTileDirectories.get(world);
        if (worldTileDirectory == null) {
            worldTileDirectory = new File(DynmapPlugin.tilesDirectory, tile.getWorld().getName());
            worldTileDirectories.put(world, worldTileDirectory);
        }
        worldTileDirectory.mkdirs();
        return new File(worldTileDirectory, tile.getFilename()); 
    }

    public void pushUpdate(Object update) {
        for(int i=0;i<worlds.size();i++) {
            UpdateQueue queue = worldUpdateQueues.get(worlds.get(i));
            queue.pushUpdate(update);
        }
    }
    
    public void pushUpdate(World world, Object update) {
        pushUpdate(world.getName(), update);
    }
    
    public void pushUpdate(String world, Object update) {
        UpdateQueue updateQueue = worldUpdateQueues.get(world);
        if (updateQueue == null) {
            worldUpdateQueues.put(world, updateQueue = new UpdateQueue());
            worlds.add(world);
        }
        updateQueue.pushUpdate(update);
    }
    
    public Object[] getWorldUpdates(String worldName, long since) {
        UpdateQueue queue = worldUpdateQueues.get(worldName);
        if (queue == null)
            return new Object[0];
        return queue.getUpdatedObjects(since);
    }
}
