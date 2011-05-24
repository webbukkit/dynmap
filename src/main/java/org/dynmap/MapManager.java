package org.dynmap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitScheduler;
import org.dynmap.debug.Debug;

public class MapManager {
    public AsynchronousQueue<MapTile> tileQueue;
    public AsynchronousQueue<ImageWriter> writeQueue;

    public List<DynmapWorld> worlds = new ArrayList<DynmapWorld>();
    public Map<String, DynmapWorld> worldsLookup = new HashMap<String, DynmapWorld>();
    private BukkitScheduler scheduler;
    private DynmapPlugin plug_in;
    private double timeslice_interval = 0.0;
    /* Which timesliced renders are active */
    private HashMap<String, FullWorldRenderState> active_renders = new HashMap<String, FullWorldRenderState>();

    /* lock for our data structures */
    public static final Object lock = new Object();

    public static MapManager mapman;    /* Our singleton */

    private static class ImageWriter {
        Runnable run;
    }

    public DynmapWorld getWorld(String name) {
        DynmapWorld world = worldsLookup.get(name);
        return world;
    }
    
    public Collection<DynmapWorld> getWorlds() {
        return worlds;
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
        int rendercnt = 0;

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
            world = getWorld(t.getWorld().getName());
            tile0 = t;
        }

        public void run() {
            MapTile tile;

            if(tile0 == null) {    /* Not single tile render */
                /* If render queue is empty, start next map */
                if(renderQueue.isEmpty()) {
                    if(map_index >= 0) { /* Finished a map? */
                        Log.info("Full render of map '" + world.maps.get(map_index).getClass().getSimpleName() + "' of world '" +
                                 world.world.getName() + "' completed - " + rendercnt + " tiles rendered.");
                    }                	
                    found.clear();
                    rendered.clear();
                    rendercnt = 0;
                    map_index++;    /* Next map */
                    if(map_index >= world.maps.size()) {    /* Last one done? */
                        Log.info("Full render finished.");
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
            MapChunkCache cache = new MapChunkCache(world.world, requiredChunks);
            World w = world.world;
            if(tile0 != null) {    /* Single tile? */
                render(cache, tile);    /* Just render */
            }
            else {
                if (render(cache, tile)) {
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
                rendercnt++;
                if((rendercnt % 100) == 0) {
                    Log.info("Full render of map '" + world.maps.get(map_index).getClass().getSimpleName() + "' on world '" +
                            w.getName() + "' in progress - " + rendercnt + " tiles rendered, " + renderQueue.size() + " tiles pending.");
                }
            }
            /* And unload what we loaded */
            cache.unloadChunks();
            if(tile0 == null) {    /* fullrender */
                /* Schedule the next tile to be worked */
                scheduler.scheduleSyncDelayedTask(plug_in, this, (int)(timeslice_interval*20));
            }
        }
    }

    public MapManager(DynmapPlugin plugin, ConfigurationNode configuration) {
        plug_in = plugin;
        mapman = this;

        this.tileQueue = new AsynchronousQueue<MapTile>(new Handler<MapTile>() {
            @Override
            public void handle(MapTile t) {
                scheduler.scheduleSyncDelayedTask(plug_in,
                    new FullWorldRenderState(t), 1);
            }
        }, (int) (configuration.getDouble("renderinterval", 0.5) * 1000));

        this.writeQueue = new AsynchronousQueue<ImageWriter>(
            new Handler<ImageWriter>() {
                @Override
                public void handle(ImageWriter w) {
                    w.run.run();
                }
            }, 10);

        timeslice_interval = configuration.getDouble("timesliceinterval", 0.5);

        scheduler = plugin.getServer().getScheduler();

        tileQueue.start();
        writeQueue.start();
        
        for (World world : plug_in.getServer().getWorlds()) {
            activateWorld(world);
        }
    }

    void renderFullWorld(Location l) {
        DynmapWorld world = getWorld(l.getWorld().getName());
        if (world == null) {
            Log.severe("Could not render: world '" + l.getWorld().getName() + "' not defined in configuration.");
            return;
        }
        String wname = l.getWorld().getName();
        FullWorldRenderState rndr = active_renders.get(wname);
        if(rndr != null) {
            Log.info("Full world render of world '" + wname + "' already active.");
            return;
        }
        rndr = new FullWorldRenderState(world,l);    /* Make new activation record */
        active_renders.put(wname, rndr);    /* Add to active table */
        /* Schedule first tile to be worked */
        scheduler.scheduleSyncDelayedTask(plug_in, rndr, (int)(timeslice_interval*20));
        Log.info("Full render starting on world '" + wname + "' (timesliced)...");
    }

    public void activateWorld(World w) {
        ConfigurationNode worldConfiguration = plug_in.getWorldConfiguration(w);
        if (!worldConfiguration.getBoolean("enabled", false)) {
            Log.info("World '" + w.getName() + "' disabled");
            return;
        }
        String worldName = w.getName();
        
        Event.Listener<MapTile> invalitateListener = new Event.Listener<MapTile>() {
            @Override
            public void triggered(MapTile t) {
                invalidateTile(t);
            }
        };
        
        DynmapWorld dynmapWorld = new DynmapWorld();
        dynmapWorld.world = w;
        dynmapWorld.configuration = worldConfiguration;
        Log.info("Loading maps of world '" + worldName + "'...");
        for(MapType map : worldConfiguration.<MapType>createInstances("maps", new Class<?>[0], new Object[0])) {
            map.onTileInvalidated.addListener(invalitateListener);
            dynmapWorld.maps.add(map);
        }
        Log.info("Loaded " + dynmapWorld.maps.size() + " maps of world '" + worldName + "'.");
        worlds.add(dynmapWorld);
        worldsLookup.put(w.getName(), dynmapWorld);
        plug_in.events.trigger("worldactivated", dynmapWorld);
    }

    public int touch(Location l) {
        DynmapWorld world = getWorld(l.getWorld().getName());
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

    public boolean render(MapChunkCache cache, MapTile tile) {
        boolean result = tile.getMap().render(cache, tile, getTileFile(tile));
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
            Log.warning("Could not create directory for tiles ('" + worldTileDirectory + "').");
        }
        return new File(worldTileDirectory, tile.getFilename());
    }

    public void pushUpdate(Object update) {
        for(DynmapWorld world : getWorlds()) {
            world.updates.pushUpdate(update);
        }
    }

    public void pushUpdate(World world, Object update) {
        pushUpdate(world.getName(), update);
    }

    public void pushUpdate(String worldName, Object update) {
        DynmapWorld world = getWorld(worldName);
        world.updates.pushUpdate(update);
    }

    public Object[] getWorldUpdates(String worldName, long since) {
        DynmapWorld world = getWorld(worldName);
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
        return true;
    }
}
