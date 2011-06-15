package org.dynmap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.command.CommandSender;
import org.dynmap.debug.Debug;
import org.dynmap.utils.LegacyMapChunkCache;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.NewMapChunkCache;

public class MapManager {
    public AsynchronousQueue<MapTile> tileQueue;

    public List<DynmapWorld> worlds = new ArrayList<DynmapWorld>();
    public Map<String, DynmapWorld> worldsLookup = new HashMap<String, DynmapWorld>();
    private BukkitScheduler scheduler;
    private DynmapPlugin plug_in;
    private long timeslice_int = 0; /* In milliseconds */
    /* Which fullrenders are active */
    private HashMap<String, FullWorldRenderState> active_renders = new HashMap<String, FullWorldRenderState>();
    /* Tile hash manager */
    public TileHashManager hashman;
    /* lock for our data structures */
    public static final Object lock = new Object();

    public static MapManager mapman;    /* Our singleton */

    /* Thread pool for processing renders */
    private DynmapScheduledThreadPoolExecutor renderpool;
    private static final int POOL_SIZE = 3;    

    private HashMap<String, MapStats> mapstats = new HashMap<String, MapStats>();
    
    private static class MapStats {
        int loggedcnt;
        int renderedcnt;
        int updatedcnt;
        int transparentcnt;
    }

    public DynmapWorld getWorld(String name) {
        DynmapWorld world = worldsLookup.get(name);
        return world;
    }
    
    public Collection<DynmapWorld> getWorlds() {
        return worlds;
    }
    
    private class DynmapScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {
        DynmapScheduledThreadPoolExecutor() {
            super(POOL_SIZE);
        }

        protected void afterExecute(Runnable r, Throwable x) {
            if(r instanceof FullWorldRenderState) {
                ((FullWorldRenderState)r).cleanup();
            }
            if(x != null) {
                Log.severe("Exception during render job: " + r);
                x.printStackTrace();
            }
        }
    }
    /* This always runs on render pool threads - no bukkit calls from here */ 
    private class FullWorldRenderState implements Runnable {
        DynmapWorld world;    /* Which world are we rendering */
        Location loc;        
        int    map_index = -1;    /* Which map are we on */
        MapType map;
        HashSet<MapTile> found = null;
        HashSet<MapTile> rendered = null;
        LinkedList<MapTile> renderQueue = null;
        MapTile tile0 = null;
        MapTile tile = null;
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

        public String toString() {
            return "world=" + world.world.getName() + ", map=" + map + " tile=" + tile;
        }
        
        public void cleanup() {
            if(tile0 == null) {
                synchronized(lock) {
                    active_renders.remove(world.world.getName());
                }
            }
        }
        public void run() {
            long tstart = System.currentTimeMillis();
            
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
                        Log.info("Full render of '" + world.world.getName() + "' finished.");
                        cleanup();
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
                    if(world.seedloc != null) {
                        for(Location seed : world.seedloc) {
                            for (MapTile mt : map.getTiles(seed)) {
                                if (!found.contains(mt)) {
                                    found.add(mt);
                                    renderQueue.add(mt);
                                }
                            }
                        }
                    }
                }
                tile = renderQueue.pollFirst();
            }
            else {    /* Else, single tile render */
                tile = tile0;
            }
            World w = world.world;
            /* Fetch chunk cache from server thread */
            DynmapChunk[] requiredChunks = tile.getMap().getRequiredChunks(tile);
            MapChunkCache cache = createMapChunkCache(w, requiredChunks);
            if(cache == null) {
                cleanup();
                return; /* Cancelled/aborted */
            }
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
                long tend = System.currentTimeMillis();
                if(timeslice_int > (tend-tstart)) { /* We were fast enough */
                    renderpool.schedule(this, timeslice_int - (tend-tstart), TimeUnit.MILLISECONDS);
                }
                else {  /* Schedule to run ASAP */
                    renderpool.execute(this);
                }
            }
            else {
                cleanup();
            }
        }
    }

    private class CheckWorldTimes implements Runnable {
        public void run() {
            for(DynmapWorld w : worlds) {
                int new_servertime = (int)(w.world.getTime() % 24000);
                /* Check if we went from night to day */
                boolean wasday = w.servertime >= 0 && w.servertime < 13700;
                boolean isday = new_servertime >= 0 && new_servertime < 13700;
                w.servertime = new_servertime;
                if(wasday != isday) {
                    MapManager.mapman.pushUpdate(w.world, new Client.DayNight(isday));            
                }
            }
        }
    }
    
    public MapManager(DynmapPlugin plugin, ConfigurationNode configuration) {
        plug_in = plugin;
        mapman = this;

        this.tileQueue = new AsynchronousQueue<MapTile>(new Handler<MapTile>() {
            @Override
            public void handle(MapTile t) {
                renderpool.execute(new FullWorldRenderState(t));
            }
        }, (int) (configuration.getDouble("renderinterval", 0.5) * 1000));

        /* On dedicated thread, so default to no delays */
        timeslice_int = (long)(configuration.getDouble("timesliceinterval", 0.0) * 1000);

        scheduler = plugin.getServer().getScheduler();

        hashman = new TileHashManager(DynmapPlugin.tilesDirectory, configuration.getBoolean("enabletilehash", true));
        
        tileQueue.start();
        
        for (World world : plug_in.getServer().getWorlds()) {
            activateWorld(world);
        }
        
        scheduler.scheduleSyncRepeatingTask(plugin, new CheckWorldTimes(), 5*20, 5*20); /* Check very 5 seconds */
        
    }

    void renderFullWorld(Location l) {
        DynmapWorld world = getWorld(l.getWorld().getName());
        if (world == null) {
            Log.severe("Could not render: world '" + l.getWorld().getName() + "' not defined in configuration.");
            return;
        }
        String wname = l.getWorld().getName();
        FullWorldRenderState rndr;
        synchronized(lock) {
            rndr = active_renders.get(wname);
            if(rndr != null) {
                Log.info("Full world render of world '" + wname + "' already active.");
                return;
            }
            rndr = new FullWorldRenderState(world,l);    /* Make new activation record */
            active_renders.put(wname, rndr);    /* Add to active table */
        }
        /* Schedule first tile to be worked */
        renderpool.execute(rndr);
        Log.info("Full render starting on world '" + wname + "'...");
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
        
        List<ConfigurationNode> loclist = worldConfiguration.getNodes("fullrenderlocations");
        dynmapWorld.seedloc = new ArrayList<Location>();
        dynmapWorld.servertime = (int)(w.getTime() % 24000);
        dynmapWorld.sendposition = worldConfiguration.getBoolean("sendposition", true);
        dynmapWorld.sendhealth = worldConfiguration.getBoolean("sendhealth", true);
        if(loclist != null) {
            for(ConfigurationNode loc : loclist) {
                Location lx = new Location(w, loc.getDouble("x", 0), loc.getDouble("y", 64), loc.getDouble("z", 0));
                dynmapWorld.seedloc.add(lx);
            }
        }
    
        // TODO: Make this less... weird...
        // Insert the world on the same spot as in the configuration.
        HashMap<String, Integer> indexLookup = new HashMap<String, Integer>();
        List<ConfigurationNode> nodes = plug_in.configuration.getNodes("worlds");
        for (int i = 0; i < nodes.size(); i++) {
            ConfigurationNode node = nodes.get(i);
            indexLookup.put(node.getString("name"), i);
        }
        Integer worldIndex = indexLookup.get(worldName);
        if(worldIndex == null) {
        	worlds.add(dynmapWorld);	/* Put at end if no world section */
        }
        else {
        	int insertIndex;
        	for(insertIndex = 0; insertIndex < worlds.size(); insertIndex++) {
        		Integer nextWorldIndex = indexLookup.get(worlds.get(insertIndex).world.getName());
        		if (nextWorldIndex == null || worldIndex < nextWorldIndex.intValue()) {
        			break;
       			}
        	}
        	worlds.add(insertIndex, dynmapWorld);
        }
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
        renderpool = new DynmapScheduledThreadPoolExecutor();
    }

    public void stopRendering() {
        if(renderpool != null) {
            renderpool.shutdown();
            renderpool = null;
        }
        tileQueue.stop();
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

    private static boolean use_legacy = false;
    /**
     * Render processor helper - used by code running on render threads to request chunk snapshot cache from server/sync thread
     */
    public MapChunkCache createMapChunkCache(final World w, final DynmapChunk[] chunks) {
        Callable<MapChunkCache> job = new Callable<MapChunkCache>() {
            public MapChunkCache call() {
                MapChunkCache c = null;
                try {
                    if(!use_legacy)
                        c = new NewMapChunkCache();
                } catch (NoClassDefFoundError ncdfe) {
                    use_legacy = true;
                }
                if(c == null)
                    c = new LegacyMapChunkCache();
                c.loadChunks(w, chunks);
                return c;
            }
        };
        Future<MapChunkCache> rslt = scheduler.callSyncMethod(plug_in, job);
        try {
            return rslt.get();
        } catch (Exception x) {
            Log.info("createMapChunk - " + x);
            return null;
        }
    }
    /**
     *  Update map tile statistics
     */
    public void updateStatistics(MapTile tile, String subtype, boolean rendered, boolean updated, boolean transparent) {
        synchronized(lock) {
            String k = tile.getKey();
            if(subtype != null)
                k += "." + subtype;
            MapStats ms = mapstats.get(k);
            if(ms == null) {
                ms = new MapStats();
                mapstats.put(k, ms);
            }
            ms.loggedcnt++;
            if(rendered)
                ms.renderedcnt++;
            if(updated)
                ms.updatedcnt++;
            if(transparent)
                ms.transparentcnt++;
        }
    }
    /**
     * Print statistics command
     */
    public void printStats(CommandSender sender, String prefix) {
        sender.sendMessage("Tile Render Statistics:");
        MapStats tot = new MapStats();
        synchronized(lock) {
            for(String k: new TreeSet<String>(mapstats.keySet())) {
                if((prefix != null) && !k.startsWith(prefix))
                    continue;
                MapStats ms = mapstats.get(k);
                sender.sendMessage("  " + k + ": processed=" + ms.loggedcnt + ", rendered=" + ms.renderedcnt + 
                               ", updated=" + ms.updatedcnt + ", transparent=" + ms.transparentcnt);
                tot.loggedcnt += ms.loggedcnt;
                tot.renderedcnt += ms.renderedcnt;
                tot.updatedcnt += ms.updatedcnt;
                tot.transparentcnt += ms.transparentcnt;
            }
        }
        sender.sendMessage("  TOTALS: processed=" + tot.loggedcnt + ", rendered=" + tot.renderedcnt + 
                           ", updated=" + tot.updatedcnt + ", transparent=" + tot.transparentcnt);
    }
    /**
     * Reset statistics
     */
    public void resetStats(CommandSender sender, String prefix) {
        synchronized(lock) {
            for(String k : mapstats.keySet()) {
                if((prefix != null) && !k.startsWith(prefix))
                    continue;
                MapStats ms = mapstats.get(k);
                ms.loggedcnt = 0;
                ms.renderedcnt = 0;
                ms.updatedcnt = 0;
                ms.transparentcnt = 0;
            }
        }
        sender.sendMessage("Tile Render Statistics reset");
    }
}
