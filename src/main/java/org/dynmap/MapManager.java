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

    public AsynchronousQueue<MapTile> tileQueue;
    
    public Map<String, DynmapWorld> worlds = new HashMap<String, DynmapWorld>();
    public Map<String, DynmapWorld> inactiveworlds = new HashMap<String, DynmapWorld>();

    /* lock for our data structures */
    public static final Object lock = new Object();

    public MapManager(DynmapPlugin plugin, ConfigurationNode configuration) {
        this.tileQueue = new AsynchronousQueue<MapTile>(new Handler<MapTile>() {
            @Override
            public void handle(MapTile t) {
                render(t);
            }
        }, (int) (configuration.getDouble("renderinterval", 0.5) * 1000));
        
        for(Object worldConfigurationObj : (List<?>)configuration.getProperty("worlds")) {
            Map<?, ?> worldConfiguration = (Map<?, ?>)worldConfigurationObj;
            String worldName = (String)worldConfiguration.get("name");
            DynmapWorld world = new DynmapWorld();
            if (worldConfiguration.get("maps") != null) {
                for(MapType map : loadMapTypes((List<?>)worldConfiguration.get("maps"))) {
                    world.maps.add(map);
                }
            }
            inactiveworlds.put(worldName, world);
            
            World bukkitWorld = plugin.getServer().getWorld(worldName);
            if (bukkitWorld != null)
                activateWorld(bukkitWorld);
        }
        
        tileQueue.start();
    }

    void renderFullWorld(Location l) {
        DynmapWorld world = worlds.get(l.getWorld().getName());
        if (world == null) {
            log.severe("Could not render: world '" + l.getWorld().getName() + "' not defined in configuration.");
            return;
        }
        World w = world.world;
        log.info("Full render starting on world '" + w.getName() + "'...");
        for (MapType map : world.maps) {
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
                    w.unloadChunk(c.x, c.z, false, true);
                }

                // Load the required chunks.
                for (DynmapChunk chunk : requiredChunks) {
                    boolean wasLoaded = w.isChunkLoaded(chunk.x, chunk.z);
                    w.loadChunk(chunk.x, chunk.z, false);
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
            }

            // Unload remaining chunks to clean-up.
            while (!loadedChunks.isEmpty()) {
                DynmapChunk c = loadedChunks.pollFirst();
                w.unloadChunk(c.x, c.z, false, true);
            }
        }
        log.info("Full render finished.");
    }

    public void activateWorld(World w) {
        DynmapWorld world = inactiveworlds.get(w.getName());
        if (world == null) {
            world = worlds.get(w.getName());
        } else {
            inactiveworlds.remove(w.getName());
        }
        if (world != null) {
            world.world = w;
            worlds.put(w.getName(), world);
            log.info("Activated world '" + w.getName() + "' in Dynmap.");
        }
    }
    
    private MapType[] loadMapTypes(List<?> mapConfigurations) {
        Event.Listener<MapTile> invalitateListener = new Event.Listener<MapTile>() {
            @Override
            public void triggered(MapTile t) {
                invalidateTile(t);
            }
        };
        ArrayList<MapType> mapTypes = new ArrayList<MapType>();
        for (Object configuredMapObj : mapConfigurations) {
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
        DynmapWorld world = worlds.get(l.getWorld().getName());
        if (world == null)
            return;
        for (int i = 0; i < world.maps.size(); i++) {
            MapTile[] tiles = world.maps.get(i).getTiles(l);
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
        pushUpdate(tile.getWorld(), new Client.Tile(tile.getFilename()));
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
        if (!worldTileDirectory.isDirectory() && !worldTileDirectory.mkdirs()) {
            log.warning("Could not create directory for tiles ('" + worldTileDirectory + "').");
        }
        return new File(worldTileDirectory, tile.getFilename()); 
    }

    public void pushUpdate(Object update) {
        for(DynmapWorld world : worlds.values()) {
            world.updates.pushUpdate(update);
        }
    }
    
    public void pushUpdate(World world, Object update) {
        pushUpdate(world.getName(), update);
    }
    
    public void pushUpdate(String worldName, Object update) {
        DynmapWorld world = worlds.get(worldName);
        world.updates.pushUpdate(update);
    }
    
    public Object[] getWorldUpdates(String worldName, long since) {
        DynmapWorld world = worlds.get(worldName);
        if (world == null)
            return new Object[0];
        return world.updates.getUpdatedObjects(since);
    }
}
