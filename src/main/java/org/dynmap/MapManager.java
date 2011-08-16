package org.dynmap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.command.CommandSender;
import org.dynmap.DynmapWorld.AutoGenerateOption;
import org.dynmap.debug.Debug;
import org.dynmap.hdmap.HDMapManager;
import org.dynmap.utils.LegacyMapChunkCache;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.NewMapChunkCache;
import org.dynmap.utils.SnapshotCache;
import org.dynmap.utils.TileFlags;

public class MapManager {
    public AsynchronousQueue<MapTile> tileQueue;

    private static final int DEFAULT_CHUNKS_PER_TICK = 200;
    private static final int DEFAULT_ZOOMOUT_PERIOD = 60;
    public List<DynmapWorld> worlds = new ArrayList<DynmapWorld>();
    public Map<String, DynmapWorld> worldsLookup = new HashMap<String, DynmapWorld>();
    private BukkitScheduler scheduler;
    private DynmapPlugin plug_in;
    private long timeslice_int = 0; /* In milliseconds */
    private int max_chunk_loads_per_tick = DEFAULT_CHUNKS_PER_TICK;
    
    private int zoomout_period = DEFAULT_ZOOMOUT_PERIOD;	/* Zoom-out tile processing period, in seconds */
    /* Which fullrenders are active */
    private HashMap<String, FullWorldRenderState> active_renders = new HashMap<String, FullWorldRenderState>();

    /* Chunk load handling */
    private Object loadlock = new Object();
    private int chunks_in_cur_tick = 0;
    private long cur_tick;
    
    /* Tile hash manager */
    public TileHashManager hashman;
    /* lock for our data structures */
    public static final Object lock = new Object();

    public static MapManager mapman;    /* Our singleton */
    public HDMapManager hdmapman;
    public SnapshotCache sscache;
    
    /* Thread pool for processing renders */
    private DynmapScheduledThreadPoolExecutor render_pool;
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

    private static class OurThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.setName("Dynmap Render Thread");
            return t;
        }
    }
    
    private class DynmapScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {
        DynmapScheduledThreadPoolExecutor() {
            super(POOL_SIZE);
            this.setThreadFactory(new OurThreadFactory());
            /* Set shutdown policy to stop everything */
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
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
        @Override
        public void execute(final Runnable r) {
            try {
                super.execute(new Runnable() {
                    public void run() {
                        try {
                            r.run();
                        } catch (Exception x) {
                            Log.severe("Exception during render job: " + r);
                            x.printStackTrace();                        
                        }
                    }
                });
            } catch (RejectedExecutionException rxe) {  /* Pool shutdown - nominal for reload or unload */
            }    
        }
        @Override
        public ScheduledFuture<?> schedule(final Runnable command, long delay, TimeUnit unit) {
            try {
                return super.schedule(new Runnable() {
                    public void run() {
                        try {
                            command.run();
                        } catch (Exception x) {
                            Log.severe("Exception during render job: " + command);
                            x.printStackTrace();                        
                        }
                    }
                }, delay, unit);
            } catch (RejectedExecutionException rxe) {
                return null;    /* Pool shut down when we reload or unload */
            }
        }
    }
    /* This always runs on render pool threads - no bukkit calls from here */ 
    private class FullWorldRenderState implements Runnable {
        DynmapWorld world;    /* Which world are we rendering */
        Location loc;        
        int    map_index = -1;    /* Which map are we on */
        MapType map;
        TileFlags found = null;
        TileFlags rendered = null;
        LinkedList<MapTile> renderQueue = null;
        MapTile tile0 = null;
        MapTile tile = null;
        int rendercnt = 0;
        CommandSender sender;
        long timeaccum;
        HashSet<MapType> renderedmaps = new HashSet<MapType>();
        String activemaps;
        List<String> activemaplist;
        /* Min and max limits for chunk coords (for radius limit) */
        int cxmin, cxmax, czmin, czmax;
        String rendertype;
        boolean cancelled;
        String mapname;

        /* Full world, all maps render */
        FullWorldRenderState(DynmapWorld dworld, Location l, CommandSender sender, String mapname) {
            this(dworld, l, sender, mapname, -1);
            rendertype = "Full render";
        }
        
        /* Full world, all maps render, with optional render radius */
        FullWorldRenderState(DynmapWorld dworld, Location l, CommandSender sender, String mapname, int radius) {
            world = dworld;
            loc = l;
            found = new TileFlags();
            rendered = new TileFlags();
            renderQueue = new LinkedList<MapTile>();
            this.sender = sender;
            if(radius < 0) {
                cxmin = czmin = Integer.MIN_VALUE;
                cxmax = czmax = Integer.MAX_VALUE;
                rendertype = "Full render";
            }
            else {
                cxmin = (l.getBlockX() - radius)>>4;
                czmin = (l.getBlockZ() - radius)>>4;
                cxmax = (l.getBlockX() + radius+15)>>4;
                czmax = (l.getBlockZ() + radius+15)>>4;
                rendertype = "Radius render";
            }
            this.mapname = mapname;
        }

        /* Single tile render - used for incremental renders */
        FullWorldRenderState(MapTile t) {
            world = getWorld(t.getWorld().getName());
            tile0 = t;
            cxmin = czmin = Integer.MIN_VALUE;
            cxmax = czmax = Integer.MAX_VALUE;
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
            
            if(cancelled) {
            	cleanup();
            	return;
            }
            if(tile0 == null) {    /* Not single tile render */
                /* If render queue is empty, start next map */
                if(renderQueue.isEmpty()) {
                    if(map_index >= 0) { /* Finished a map? */
                        double msecpertile = (double)timeaccum / (double)((rendercnt>0)?rendercnt:1)/(double)activemaplist.size();
                        if(activemaplist.size() > 1) 
                            sender.sendMessage(rendertype + " of maps [" + activemaps + "] of '" +
                               world.world.getName() + "' completed - " + rendercnt + " tiles rendered each (" + String.format("%.2f", msecpertile) + " msec/map-tile).");
                        else
                            sender.sendMessage(rendertype + " of map '" + activemaps + "' of '" +
                                 world.world.getName() + "' completed - " + rendercnt + " tiles rendered (" + String.format("%.2f", msecpertile) + " msec/tile).");
                    }                	
                    found.clear();
                    rendered.clear();
                    rendercnt = 0;
                    timeaccum = 0;
                    /* Advance to next unrendered map */
                    while(map_index < world.maps.size()) {
                        map_index++;    /* Move to next one */
                        if(map_index >= world.maps.size()) break;
                        /* If single map render, see if this is our target */
                        if(mapname != null) {
                            if(world.maps.get(map_index).getName().equals(mapname)) {
                                break;
                            }
                        }
                        else {
                            if(renderedmaps.contains(world.maps.get(map_index)) == false)
                                break;
                        }
                    }
                    if(map_index >= world.maps.size()) {    /* Last one done? */
                        sender.sendMessage(rendertype + " of '" + world.world.getName() + "' finished.");
                        cleanup();
                        return;
                    }
                    map = world.maps.get(map_index);
                    activemaplist = map.getMapNamesSharingRender(world);
                    /* Build active map list */
                    activemaps = "";
                    for(String n : activemaplist) {
                        if((mapname != null) && (!mapname.equals(n)))
                            continue;
                        if(activemaps.length() > 0)
                            activemaps += ",";
                        activemaps += n;
                    }
                    /* Mark all the concurrently rendering maps rendered */
                    renderedmaps.addAll(map.getMapsSharingRender(world));

                    /* Now, prime the render queue */
                    for (MapTile mt : map.getTiles(loc)) {
                        if (!found.getFlag(mt.tileOrdinalX(), mt.tileOrdinalY())) {
                            found.setFlag(mt.tileOrdinalX(), mt.tileOrdinalY(), true);
                            renderQueue.add(mt);
                        }
                    }
                    if(world.seedloc != null) {
                        for(Location seed : world.seedloc) {
                            for (MapTile mt : map.getTiles(seed)) {
                                if (!found.getFlag(mt.tileOrdinalX(),mt.tileOrdinalY())) {
                                    found.setFlag(mt.tileOrdinalX(),mt.tileOrdinalY(), true);
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
            /* Get list of chunks required for tile */
            List<DynmapChunk> requiredChunks = tile.getRequiredChunks();
            /* If we are doing radius limit render, see if any are inside limits */
            if(cxmin != Integer.MIN_VALUE) {
                boolean good = false;
                for(DynmapChunk c : requiredChunks) {
                    if((c.x >= cxmin) && (c.x <= cxmax) && (c.z >= czmin) && (c.z <= czmax)) {
                        good = true;
                        break;
                    }
                }
                if(!good) requiredChunks = Collections.emptyList();
            }
            /* Fetch chunk cache from server thread */
            MapChunkCache cache = createMapChunkCache(world, requiredChunks, tile.isBlockTypeDataNeeded(), 
                                                      tile.isHightestBlockYDataNeeded(), tile.isBiomeDataNeeded(), 
                                                      tile.isRawBiomeDataNeeded());
            if(cache == null) {
                cleanup();
                return; /* Cancelled/aborted */
            }
            if(tile0 != null) {    /* Single tile? */
                if(cache.isEmpty() == false)
                    tile.render(cache, null);
            }
            else {
            	/* Switch to not checking if rendered tile is blank - breaks us on skylands, where tiles can be nominally blank - just work off chunk cache empty */
                if (cache.isEmpty() == false) {
                	tile.render(cache, mapname);
                    found.setFlag(tile.tileOrdinalX(),tile.tileOrdinalY(),false);
                    rendered.setFlag(tile.tileOrdinalX(), tile.tileOrdinalY(), true);
                    for (MapTile adjTile : map.getAdjecentTiles(tile)) {
                        if (!found.getFlag(adjTile.tileOrdinalX(),adjTile.tileOrdinalY()) && 
                                !rendered.getFlag(adjTile.tileOrdinalX(),adjTile.tileOrdinalY())) {
                            found.setFlag(adjTile.tileOrdinalX(), adjTile.tileOrdinalY(), true);
                            renderQueue.add(adjTile);
                        }
                    }
                }
                found.setFlag(tile.tileOrdinalX(), tile.tileOrdinalY(), false);
                if(!cache.isEmpty()) {
                    rendercnt++;
                    timeaccum += System.currentTimeMillis() - tstart;
                    if((rendercnt % 100) == 0) {
                        double msecpertile = (double)timeaccum / (double)rendercnt / (double)activemaplist.size();
                        if(activemaplist.size() > 1) 
                            sender.sendMessage(rendertype + " of maps [" + activemaps + "] of '" +
                                               w.getName() + "' in progress - " + rendercnt + " tiles rendered each (" + String.format("%.2f", msecpertile) + " msec/map-tile).");
                        else
                            sender.sendMessage(rendertype + " of map '" + activemaps + "' of '" +
                                               w.getName() + "' in progress - " + rendercnt + " tiles rendered (" + String.format("%.2f", msecpertile) + " msec/tile).");
                    }
                }
            }
            /* And unload what we loaded */
            cache.unloadChunks();
            if(tile0 == null) {    /* fullrender */
                long tend = System.currentTimeMillis();
                if(timeslice_int > (tend-tstart)) { /* We were fast enough */
                    scheduleDelayedJob(this, timeslice_int - (tend-tstart));
                }
                else {  /* Schedule to run ASAP */
                    scheduleDelayedJob(this, 0);
                }
            }
            else {
                cleanup();
            }
        }
        
        public void cancelRender() {
        	cancelled = true;
        }
    }

    private class CheckWorldTimes implements Runnable {
        public void run() {
            Future<Integer> f = scheduler.callSyncMethod(plug_in, new Callable<Integer>() {
                public Integer call() throws Exception {
                    for(DynmapWorld w : worlds) {
                        int new_servertime = (int)(w.world.getTime() % 24000);
                        /* Check if we went from night to day */
                        boolean wasday = w.servertime >= 0 && w.servertime < 13700;
                        boolean isday = new_servertime >= 0 && new_servertime < 13700;
                        w.servertime = new_servertime;
                        if(wasday != isday) {
                            pushUpdate(w.world, new Client.DayNight(isday));            
                        }
                    }
                    return 0;
                }
            });
            try {
                f.get();
            } catch (Exception ix) {
                Log.severe(ix);
            }
            scheduleDelayedJob(this, 5000);
        }
    }
    
    private class DoZoomOutProcessing implements Runnable {
        public void run() {
            Debug.debug("DoZoomOutProcessing started");
            ArrayList<DynmapWorld> wl = new ArrayList<DynmapWorld>(worlds);
            for(DynmapWorld w : wl) {
                w.freshenZoomOutFiles();
            }
            scheduleDelayedJob(this, zoomout_period*1000);
            Debug.debug("DoZoomOutProcessing finished");
        }
    }
    
    public MapManager(DynmapPlugin plugin, ConfigurationNode configuration) {
        plug_in = plugin;
        mapman = this;
        /* Clear color scheme */
        ColorScheme.reset();
        /* Initialize HD map manager */
        hdmapman = new HDMapManager();  
        hdmapman.loadHDShaders(plugin);
        hdmapman.loadHDPerspectives(plugin);
        hdmapman.loadHDLightings(plugin);
        sscache = new SnapshotCache(configuration.getInteger("snapshotcachesize", 500));
        
        this.tileQueue = new AsynchronousQueue<MapTile>(new Handler<MapTile>() {
            @Override
            public void handle(MapTile t) {
                scheduleDelayedJob(new FullWorldRenderState(t), 0);
            }
        }, (int) (configuration.getDouble("renderinterval", 0.5) * 1000));

        /* On dedicated thread, so default to no delays */
        timeslice_int = (long)(configuration.getDouble("timesliceinterval", 0.0) * 1000);
        max_chunk_loads_per_tick = configuration.getInteger("maxchunkspertick", DEFAULT_CHUNKS_PER_TICK);
        if(max_chunk_loads_per_tick < 5) max_chunk_loads_per_tick = 5;
        /* Get zoomout processing periond in seconds */
        zoomout_period = configuration.getInteger("zoomoutperiod", DEFAULT_ZOOMOUT_PERIOD);
        if(zoomout_period < 5) zoomout_period = 5;
        
        scheduler = plugin.getServer().getScheduler();

        hashman = new TileHashManager(DynmapPlugin.tilesDirectory, configuration.getBoolean("enabletilehash", true));
        
        tileQueue.start();
        
        for (World world : plug_in.getServer().getWorlds()) {
            activateWorld(world);
        }        
    }

    void renderFullWorld(Location l, CommandSender sender, String mapname) {
        DynmapWorld world = getWorld(l.getWorld().getName());
        if (world == null) {
            sender.sendMessage("Could not render: world '" + l.getWorld().getName() + "' not defined in configuration.");
            return;
        }
        String wname = l.getWorld().getName();
        FullWorldRenderState rndr;
        synchronized(lock) {
            rndr = active_renders.get(wname);
            if(rndr != null) {
                sender.sendMessage(rndr.rendertype + " of world '" + wname + "' already active.");
                return;
            }
            rndr = new FullWorldRenderState(world,l,sender, mapname);    /* Make new activation record */
            active_renders.put(wname, rndr);    /* Add to active table */
        }
        /* Schedule first tile to be worked */
        scheduleDelayedJob(rndr, 0);

        sender.sendMessage("Full render starting on world '" + wname + "'...");
    }

    void renderWorldRadius(Location l, CommandSender sender, String mapname, int radius) {
        DynmapWorld world = getWorld(l.getWorld().getName());
        if (world == null) {
            sender.sendMessage("Could not render: world '" + l.getWorld().getName() + "' not defined in configuration.");
            return;
        }
        String wname = l.getWorld().getName();
        FullWorldRenderState rndr;
        synchronized(lock) {
            rndr = active_renders.get(wname);
            if(rndr != null) {
                sender.sendMessage(rndr.rendertype + " of world '" + wname + "' already active.");
                return;
            }
            rndr = new FullWorldRenderState(world,l,sender, mapname, radius);    /* Make new activation record */
            active_renders.put(wname, rndr);    /* Add to active table */
        }
        /* Schedule first tile to be worked */
        scheduleDelayedJob(rndr, 0);
        sender.sendMessage("Render of " + radius + " block radius starting on world '" + wname + "'...");
    }

    void cancelRender(World w, CommandSender sender) {
    	synchronized(lock) {
    		if(w != null) {
    			FullWorldRenderState rndr;
    			rndr = active_renders.get(w.getName());
    			if(rndr != null) {
    				rndr.cancelRender();	/* Cancel render */
    				if(sender != null) {
    					sender.sendMessage("Cancelled render for '" + w.getName() + "'");
    				}
    			}
    		}
    		else {	/* Else, cancel all */
    			for(String wid : active_renders.keySet()) {
    				FullWorldRenderState rnd = active_renders.get(wid);
					rnd.cancelRender();
    				if(sender != null) {
    					sender.sendMessage("Cancelled render for '" + wid + "'");
    				}
    			}
    		}
    	}
    }
    
    public void activateWorld(World w) {
        ConfigurationNode worldConfiguration = plug_in.getWorldConfiguration(w);
        if (!worldConfiguration.getBoolean("enabled", false)) {
            Log.info("World '" + w.getName() + "' disabled");
            return;
        }
        String worldName = w.getName();

        DynmapWorld dynmapWorld = new DynmapWorld();
        dynmapWorld.world = w;
        dynmapWorld.configuration = worldConfiguration;
        Log.verboseinfo("Loading maps of world '" + worldName + "'...");
        for(MapType map : worldConfiguration.<MapType>createInstances("maps", new Class<?>[0], new Object[0])) {
            if(map.getName() != null)
                dynmapWorld.maps.add(map);
        }
        Log.info("Loaded " + dynmapWorld.maps.size() + " maps of world '" + worldName + "'.");
        
        List<ConfigurationNode> loclist = worldConfiguration.getNodes("fullrenderlocations");
        dynmapWorld.seedloc = new ArrayList<Location>();
        dynmapWorld.servertime = (int)(w.getTime() % 24000);
        dynmapWorld.sendposition = worldConfiguration.getBoolean("sendposition", true);
        dynmapWorld.sendhealth = worldConfiguration.getBoolean("sendhealth", true);
        dynmapWorld.bigworld = worldConfiguration.getBoolean("bigworld", false);
        dynmapWorld.setExtraZoomOutLevels(worldConfiguration.getInteger("extrazoomout", 0));
        dynmapWorld.worldtilepath = new File(DynmapPlugin.tilesDirectory, w.getName());
        if(loclist != null) {
            for(ConfigurationNode loc : loclist) {
                Location lx = new Location(w, loc.getDouble("x", 0), loc.getDouble("y", 64), loc.getDouble("z", 0));
                dynmapWorld.seedloc.add(lx);
            }
        }
        /* Load visibility limits, if any are defined */
        List<ConfigurationNode> vislimits = worldConfiguration.getNodes("visibilitylimits");
        if(vislimits != null) {
            dynmapWorld.visibility_limits = new ArrayList<MapChunkCache.VisibilityLimit>();
            for(ConfigurationNode vis : vislimits) {
                MapChunkCache.VisibilityLimit lim = new MapChunkCache.VisibilityLimit();
                lim.x0 = vis.getInteger("x0", 0);
                lim.x1 = vis.getInteger("x1", 0);
                lim.z0 = vis.getInteger("z0", 0);
                lim.z1 = vis.getInteger("z1", 0);
                dynmapWorld.visibility_limits.add(lim);
                /* Also, add a seed location for the middle of each visible area */
                dynmapWorld.seedloc.add(new Location(w, (lim.x0+lim.x1)/2, 64, (lim.z0+lim.z1)/2));
            }            
        }
        /* Load hidden limits, if any are defined */
        List<ConfigurationNode> hidelimits = worldConfiguration.getNodes("hiddenlimits");
        if(hidelimits != null) {
            dynmapWorld.hidden_limits = new ArrayList<MapChunkCache.VisibilityLimit>();
            for(ConfigurationNode vis : hidelimits) {
                MapChunkCache.VisibilityLimit lim = new MapChunkCache.VisibilityLimit();
                lim.x0 = vis.getInteger("x0", 0);
                lim.x1 = vis.getInteger("x1", 0);
                lim.z0 = vis.getInteger("z0", 0);
                lim.z1 = vis.getInteger("z1", 0);
                dynmapWorld.hidden_limits.add(lim);
            }            
        }
        String autogen = worldConfiguration.getString("autogenerate-to-visibilitylimits", "none");
        if(autogen.equals("permanent")) {
            dynmapWorld.do_autogenerate = AutoGenerateOption.PERMANENT;
        }
        else if(autogen.equals("map-only")) {
            dynmapWorld.do_autogenerate = AutoGenerateOption.FORMAPONLY;
        }
        else {
            dynmapWorld.do_autogenerate = AutoGenerateOption.NONE;
        }
        if((dynmapWorld.do_autogenerate != AutoGenerateOption.NONE) && (dynmapWorld.visibility_limits == null)) {
            Log.info("Warning: Automatic world generation to visible limits option requires that visibitylimits be set - option disabled");
            dynmapWorld.do_autogenerate = AutoGenerateOption.NONE;
        }
        String hiddenchunkstyle = worldConfiguration.getString("hidestyle", "stone");
        if(hiddenchunkstyle.equals("air"))
            dynmapWorld.hiddenchunkstyle = MapChunkCache.HiddenChunkStyle.FILL_AIR;
        else if(hiddenchunkstyle.equals("ocean"))
            dynmapWorld.hiddenchunkstyle = MapChunkCache.HiddenChunkStyle.FILL_OCEAN;
        else
            dynmapWorld.hiddenchunkstyle = MapChunkCache.HiddenChunkStyle.FILL_STONE_PLAIN;
            
    
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

    public int touchVolume(Location l0, Location l1) {
        DynmapWorld world = getWorld(l0.getWorld().getName());
        if (world == null)
            return 0;
        int invalidates = 0;
        for (int i = 0; i < world.maps.size(); i++) {
            MapTile[] tiles = world.maps.get(i).getTiles(l0, l1);
            for (int j = 0; j < tiles.length; j++) {
                invalidateTile(tiles[j]);
                invalidates++;
            }
        }
        return invalidates;
    }

    public void invalidateTile(MapTile tile) {
        if(tileQueue.push(tile))
            Debug.debug("Invalidating tile " + tile.getFilename());
    }

    public static void scheduleDelayedJob(Runnable job, long delay_in_msec) {
        if((mapman != null) && (mapman.render_pool != null)) {
            if(delay_in_msec > 0)
                mapman.render_pool.schedule(job, delay_in_msec, TimeUnit.MILLISECONDS);
            else
                mapman.render_pool.execute(job);
        }
    }
                                                                       
    public void startRendering() {
        tileQueue.start();
        render_pool = new DynmapScheduledThreadPoolExecutor();
        scheduleDelayedJob(new DoZoomOutProcessing(), 60000);
        scheduleDelayedJob(new CheckWorldTimes(), 5000);
    }

    public void stopRendering() {
        /* Tell all worlds to cancel any zoom out processing */
        for(DynmapWorld w: worlds)
            w.cancelZoomOutFreshen();
        render_pool.shutdown();
        try {
            render_pool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ix) {
        }
        tileQueue.stop();
        mapman = null;
        hdmapman = null;
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
    public MapChunkCache createMapChunkCache(DynmapWorld w, List<DynmapChunk> chunks,
            boolean blockdata, boolean highesty, boolean biome, boolean rawbiome) {
        MapChunkCache c = null;
        try {
            if(!use_legacy)
                c = new NewMapChunkCache();
        } catch (NoClassDefFoundError ncdfe) {
            use_legacy = true;
        }
        if(c == null)
            c = new LegacyMapChunkCache();
        if(w.visibility_limits != null) {
            for(MapChunkCache.VisibilityLimit limit: w.visibility_limits) {
                c.setVisibleRange(limit);
            }
            c.setHiddenFillStyle(w.hiddenchunkstyle);
            c.setAutoGenerateVisbileRanges(w.do_autogenerate);
        }
        if(w.hidden_limits != null) {
            for(MapChunkCache.VisibilityLimit limit: w.hidden_limits) {
                c.setHiddenRange(limit);
            }
            c.setHiddenFillStyle(w.hiddenchunkstyle);
        }

        c.setChunks(w.world, chunks);
        if(c.setChunkDataTypes(blockdata, biome, highesty, rawbiome) == false)
            Log.severe("CraftBukkit build does not support biome APIs");
        if(chunks.size() == 0) {    /* No chunks to get? */
            return c;
        }

    	synchronized(loadlock) {
    		final MapChunkCache cc = c;
    		long now = System.currentTimeMillis();
    		
    		if(cur_tick != (now/50)) {	/* New tick? */
    			chunks_in_cur_tick = max_chunk_loads_per_tick;
    			cur_tick = now/50;
    		}
    		
            while(!cc.isDoneLoading()) {
            	final int cntin = chunks_in_cur_tick;
            	Future<Integer> f = scheduler.callSyncMethod(plug_in, new Callable<Integer>() {
            		public Integer call() throws Exception {
		                return Integer.valueOf(cntin - cc.loadChunks(cntin));
            		}
            	});
            	try {
            		chunks_in_cur_tick = f.get();
            	} catch (Exception ix) {
            		Log.severe(ix);
            		return null;
            	}
        		if(chunks_in_cur_tick == 0) {
        			chunks_in_cur_tick = max_chunk_loads_per_tick;
        			try { Thread.sleep(50); } catch (InterruptedException ix) {}
        		}
            }
    	}
        return c;
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
        sender.sendMessage("  Cache hit rate: " + sscache.getHitRate() + "%");
        sender.sendMessage("  Triggered update queue size: " + tileQueue.size());
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
        sscache.resetStats();
        sender.sendMessage("Tile Render Statistics reset");
    }    
}
