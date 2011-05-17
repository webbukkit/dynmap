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

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.config.ConfigurationNode;
import org.dynmap.debug.Debug;

public class MapManager {
    protected static final Logger log = Logger.getLogger("Minecraft");
    protected static final String LOG_PREFIX = "[dynmap] ";

    public AsynchronousQueue<MapTile> tileQueue;
    public AsynchronousQueue<ImageWriter> writeQueue;

    public Map<String, DynmapWorld> worlds = new HashMap<String, DynmapWorld>();
    public Map<String, DynmapWorld> inactiveworlds = new HashMap<String, DynmapWorld>();
    private BukkitScheduler scheduler;
    private DynmapPlugin plug_in;
    private boolean do_timesliced_render = false;
    private double timeslice_interval = 0.0;
    private boolean do_sync_render = false;    /* Do incremental renders on sync thread too */
    /* Which timesliced renders are active */
    private HashMap<String, FullWorldRenderState> active_renders = new HashMap<String, FullWorldRenderState>();

    /* lock for our data structures */
    public static final Object lock = new Object();

    public static MapManager mapman;    /* Our singleton */

    private static class ImageWriter {
        Runnable run;
    }

    private class FullWorldRenderState implements Runnable {
        DynmapWorld world;    /* Which world are we rendering */
        Location loc;        /* Start location */
        int    map_index = -1;    /* Which map are we on */
        MapType map;
        HashSet<MapTile> found = null;
        HashSet<MapTile> rendered = null;
        LinkedList<MapTile> renderQueue = null;
        MapTile tile0 = null;

        /* Full world, all maps render */
        FullWorldRenderState(DynmapWorld dworld, Location l) {
            world = dworld;
            loc = l;
            found = new HashSet<MapTile>();
            rendered = new HashSet<MapTile>();
            renderQueue = new LinkedList<MapTile>();
        }

        /* Single tile render - used for incremental renders */
        FullWorldRenderState(MapTile t) {
            world = worlds.get(t.getWorld().getName());
            tile0 = t;
        }

        public void run() {
            MapTile tile;

            if(tile0 == null) {    /* Not single tile render */
                /* If render queue is empty, start next map */
                if(renderQueue.isEmpty()) {
                    found.clear();
                    rendered.clear();
                    map_index++;    /* Next map */
                    if(map_index >= world.maps.size()) {    /* Last one done? */
                        log.info(LOG_PREFIX + "Full render finished.");
                        active_renders.remove(world.world.getName());
                        return;
                    }
                    map = world.maps.get(map_index);

                    /* Now, prime the render queue */
                    for (MapTile mt : map.getTiles(loc)) {
                        if (!found.contains(mt)) {
                            found.add(mt);
                            renderQueue.add(mt);
                        }
                    }
                }
                tile = renderQueue.pollFirst();
            }
            else {    /* Else, single tile render */
                tile = tile0;
            }

            DynmapChunk[] requiredChunks = tile.getMap().getRequiredChunks(tile);
            LinkedList<DynmapChunk> loadedChunks = new LinkedList<DynmapChunk>();
            World w = world.world;
            // Load the required chunks.
            for (DynmapChunk chunk : requiredChunks) {
                boolean wasLoaded = w.isChunkLoaded(chunk.x, chunk.z);
                boolean didload = w.loadChunk(chunk.x, chunk.z, false);
                if ((!wasLoaded) && didload)
                    loadedChunks.add(chunk);
            }
            if(tile0 != null) {    /* Single tile? */
                render(tile);    /* Just render */
            }
            else {
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
            /* And unload what we loaded */
            while (!loadedChunks.isEmpty()) {
                DynmapChunk c = loadedChunks.pollFirst();
                /* It looks like bukkit "leaks" entities - they don't get removed from the world-level table
                 * when chunks are unloaded but not saved - removing them seems to do the trick */
                Chunk cc = w.getChunkAt(c.x, c.z);
                if(cc != null) {
                    for(Entity e: cc.getEntities())
                        e.remove();
                }
                /* Since we only remember ones we loaded, and we're synchronous, no player has
                 * moved, so it must be safe (also prevent chunk leak, which appears to happen
                 * because isChunkInUse defined "in use" as being within 256 blocks of a player,
                 * while the actual in-use chunk area for a player where the chunks are managed
                 * by the MC base server is 21x21 (or about a 160 block radius) */
                w.unloadChunk(c.x, c.z, false, false);
            }
            if(tile0 == null) {    /* fullrender */
                /* Schedule the next tile to be worked */
                scheduler.scheduleSyncDelayedTask(plug_in, this, (int)(timeslice_interval*20));
            }
        }
    }

    public MapManager(DynmapPlugin plugin, ConfigurationNode configuration) {

        mapman = this;

        this.tileQueue = new AsynchronousQueue<MapTile>(new Handler<MapTile>() {
            @Override
            public void handle(MapTile t) {
                if(do_sync_render)
                    scheduler.scheduleSyncDelayedTask(plug_in,
                        new FullWorldRenderState(t), 1);
                else
                    render(t);
            }
        }, (int) (configuration.getDouble("renderinterval", 0.5) * 1000));

        this.writeQueue = new AsynchronousQueue<ImageWriter>(
            new Handler<ImageWriter>() {
                @Override
                public void handle(ImageWriter w) {
                    w.run.run();
                }
            }, 10);

        do_timesliced_render = configuration.getBoolean("timeslicerender", true);
        timeslice_interval = configuration.getDouble("timesliceinterval", 0.5);
        do_sync_render = configuration.getBoolean("renderonsync", true);

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

        scheduler = plugin.getServer().getScheduler();
        plug_in = plugin;

        tileQueue.start();
        writeQueue.start();
    }



    void renderFullWorld(Location l) {
        DynmapWorld world = worlds.get(l.getWorld().getName());
        if (world == null) {
            log.severe(LOG_PREFIX + "Could not render: world '" + l.getWorld().getName() + "' not defined in configuration.");
            return;
        }
        if(do_timesliced_render) {
            String wname = l.getWorld().getName();
            FullWorldRenderState rndr = active_renders.get(wname);
            if(rndr != null) {
                log.info(LOG_PREFIX + "Full world render of world '" + wname + "' already active.");
                return;
            }
            rndr = new FullWorldRenderState(world,l);    /* Make new activation record */
            active_renders.put(wname, rndr);    /* Add to active table */
            /* Schedule first tile to be worked */
            scheduler.scheduleSyncDelayedTask(plug_in, rndr, (int)(timeslice_interval*20));
            log.info(LOG_PREFIX + "Full render starting on world '" + wname + "' (timesliced)...");

            return;
        }
        World w = world.world;

        log.info(LOG_PREFIX + "Full render starting on world '" + w.getName() + "'...");
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
        log.info(LOG_PREFIX + "Full render finished.");
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
            log.info(LOG_PREFIX + "Activated world '" + w.getName() + "' in Dynmap.");
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
                log.info(LOG_PREFIX + "Loading map '" + typeName.toString() + "'...");
                Class<?> mapTypeClass = Class.forName(typeName);
                Constructor<?> constructor = mapTypeClass.getConstructor(Map.class);
                MapType mapType = (MapType) constructor.newInstance(configuredMap);
                mapType.onTileInvalidated.addListener(invalitateListener);
                mapTypes.add(mapType);
            } catch (Exception e) {
                log.log(Level.SEVERE, LOG_PREFIX + "Error loading maptype", e);
                e.printStackTrace();
            }
        }
        MapType[] result = new MapType[mapTypes.size()];
        mapTypes.toArray(result);
        return result;
    }

    public int touch(Location l) {
        DynmapWorld world = worlds.get(l.getWorld().getName());
        if (world == null)
            return 0;
        int invalidates = 0;
        for (int i = 0; i < world.maps.size(); i++) {
            MapTile[] tiles = world.maps.get(i).getTiles(l);
            for (int j = 0; j < tiles.length; j++) {
                invalidateTile(tiles[j]);
                invalidates++;
            }
        }
        return invalidates;
    }

    public void invalidateTile(MapTile tile) {
        Debug.debug("Invalidating tile " + tile.getFilename());
        tileQueue.push(tile);
    }

    public void startRendering() {
        tileQueue.start();
        writeQueue.start();
    }

    public void stopRendering() {
        tileQueue.stop();
        writeQueue.stop();
    }

    public boolean render(MapTile tile) {
        boolean result = tile.getMap().render(tile, getTileFile(tile));
        //Do update after async file write

        return result;
    }

    private HashMap<World, File> worldTileDirectories = new HashMap<World, File>();
    public File getTileFile(MapTile tile) {
        World world = tile.getWorld();
        File worldTileDirectory = worldTileDirectories.get(world);
        if (worldTileDirectory == null) {
            worldTileDirectory = new File(DynmapPlugin.tilesDirectory, tile.getWorld().getName());
            worldTileDirectories.put(world, worldTileDirectory);
        }
        if (!worldTileDirectory.isDirectory() && !worldTileDirectory.mkdirs()) {
            log.warning(LOG_PREFIX + "Could not create directory for tiles ('" + worldTileDirectory + "').");
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

    public void enqueueImageWrite(Runnable run) {
        ImageWriter handler = new ImageWriter();
        handler.run = run;
        writeQueue.push(handler);
    }

    public boolean doSyncRender() {
        return do_sync_render;
    }
}
