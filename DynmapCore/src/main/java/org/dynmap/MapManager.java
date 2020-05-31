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
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.debug.Debug;
import org.dynmap.exporter.OBJExport;
import org.dynmap.hdmap.HDMapManager;
import org.dynmap.markers.EnterExitMarker;
import org.dynmap.markers.EnterExitMarker.EnterExitText;
import org.dynmap.markers.impl.MarkerAPIImpl;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageBaseTileEnumCB;
import org.dynmap.storage.MapStorageTileSearchEndCB;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.Polygon;
import org.dynmap.utils.TileFlags;

public class MapManager {
    public AsynchronousQueue<MapTile> tileQueue;

    private static final int DEFAULT_CHUNKS_PER_TICK = 200;
    private static final int DEFAULT_ZOOMOUT_PERIOD = 60;
    public List<DynmapWorld> worlds = new CopyOnWriteArrayList<DynmapWorld>();
    private List<String> disabled_worlds = new ArrayList<String>();
    public Map<String, DynmapWorld> worldsLookup = new HashMap<String, DynmapWorld>();
    private DynmapCore core;
    private long timeslice_int = 0; /* In milliseconds */
    private int max_chunk_loads_per_tick = DEFAULT_CHUNKS_PER_TICK;
    private int parallelrendercnt = 0;
    private int progressinterval = 100;
    private int tileupdatedelay = 30;
    private int savependingperiod = 15 * 60; // every 15 minutes, by default
    private boolean saverestorepending = true;
    private boolean pauseupdaterenders = false;
    private boolean hideores = false;
    private boolean useBrightnessTable = false;
    private boolean usenormalpriority = false;
    private HashMap<String, String> blockalias = new HashMap<String, String>();
    
    private boolean pausefullrenders = false;

    // TPS based render pauses
    private double tpslimit_updaterenders = 18.0;
    private double tpslimit_fullrenders = 18.0;
    private double tpslimit_zoomout = 18.0;
    private boolean tpspauseupdaterenders = false;
    private boolean tpspausefullrenders = false;
    private boolean tpspausezoomout = false;

    // User enter/exit processing
    private static final int DEFAULT_ENTEREXIT_PERIOD = 1000;	// 1 second
    private static final int DEFAULT_TITLE_FADEIN = 10;	// 10 ticks = 1/2 second
    private static final int DEFAULT_TITLE_STAY = 70;	// 70 ticks = 3 1/2 second
    private static final int DEFAULT_TITLE_FADEOUT = 20;	// 20 ticks = 1 second    
    private static final boolean DEFAULT_ENTEREXIT_USETITLE = true;
    private static final boolean DEFAULT_ENTEREPLACESEXITS = false;
    private int enterexitperiod = DEFAULT_ENTEREXIT_PERIOD;	// Enter/exit processing period
    private int titleFadeIn = DEFAULT_TITLE_FADEIN;
    private int titleStay = DEFAULT_TITLE_STAY;
    private int titleFadeOut = DEFAULT_TITLE_FADEOUT;
    private boolean enterexitUseTitle = DEFAULT_ENTEREXIT_USETITLE;
    private boolean enterReplacesExits = DEFAULT_ENTEREPLACESEXITS;
    
    private HashMap<UUID, HashSet<EnterExitMarker>> entersetstate = new HashMap<UUID, HashSet<EnterExitMarker>>();
    
    private static class TextQueueRec {
    	EnterExitText txt;
    	boolean isEnter;
    }
    private static class SendQueueRec {
    	DynmapPlayer player;
    	ArrayList<TextQueueRec> queue = new ArrayList<TextQueueRec>();
    	int tickdelay;    	
    };    
    private HashMap<UUID, SendQueueRec> entersetsendqueue = new HashMap<UUID, SendQueueRec>();

    private boolean did_start = false;
    
    private int zoomout_period = DEFAULT_ZOOMOUT_PERIOD;	/* Zoom-out tile processing period, in seconds */
    /* Which fullrenders are active */
    private HashMap<String, FullWorldRenderState> active_renders = new HashMap<String, FullWorldRenderState>();

    /* Chunk load performance numbers */
    AtomicInteger chunk_caches_created = new AtomicInteger(0);
    AtomicInteger chunks_read[];
    AtomicLong chunks_read_times[];
    
    /* lock for our data structures */
    public static final Object lock = new Object();

    public static MapManager mapman;    /* Our singleton */
    public HDMapManager hdmapman;
    
    /* Thread pool for processing renders */
    private DynmapScheduledThreadPoolExecutor render_pool;
    private static final int POOL_SIZE = 3;    

    /* Touch event queues */
    private static class TouchEvent {
        int x, y, z;
        String world;
        String reason;
        @Override
        public int hashCode() {
            return (x << 16) ^ (y << 24) ^ z;
        }
        @Override
        public boolean equals(Object o) {
            if(this == o) return true;
            TouchEvent te = (TouchEvent)o;
            if((x != te.x) || (y != te.y) || (z != te.z) || (world.equals(te.world) == false))
                return false;
            return true;
        }        
    }
    private static class TouchVolumeEvent {
        int xmin, ymin, zmin;
        int xmax, ymax, zmax;
        String world;
        String reason;
    }
    private ConcurrentHashMap<TouchEvent, Object> touch_events = new ConcurrentHashMap<TouchEvent, Object>();
    private LinkedList<TouchVolumeEvent> touch_volume_events = new LinkedList<TouchVolumeEvent>();
    private Object touch_lock = new Object();
    
    private HashMap<String, MapStats> mapstats = new HashMap<String, MapStats>();
    
    private static class MapStats {
        int loggedcnt;
        int renderedcnt;
        int updatedcnt;
        int transparentcnt;
    }
    /* synchronized using 'lock' */
    private HashMap<String, TriggerStats> trigstats = new HashMap<String, TriggerStats>();
    
    
    private static class TriggerStats {
        long callsmade;
        long callswithtiles;
        long tilesqueued;
    }

    public DynmapWorld getWorld(String name) {
        DynmapWorld world = worldsLookup.get(name);
        if(world == null)
            world = worldsLookup.get(DynmapWorld.normalizeWorldName(name));
        return world;
    }
    
    public Collection<DynmapWorld> getWorlds() {
        return worlds;
    }
    
    public Collection<String> getDisabledWorlds() {
        return disabled_worlds;
    }

    public int getDefTileUpdateDelay() {
        return tileupdatedelay;
    }

    private static class OurThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            if(!mapman.usenormalpriority)
                t.setPriority(Thread.MIN_PRIORITY);
            t.setName("Dynmap Render Thread");
            return t;
        }
    }
    
    private class DynmapScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {
        DynmapScheduledThreadPoolExecutor() {
            super(POOL_SIZE + parallelrendercnt);
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
    private static final String RENDERTYPE_FULLRENDER = "Full render";
    private static final String RENDERTYPE_RADIUSRENDER = "Radius render";
    private static final String RENDERTYPE_UPDATERENDER = "Update render";
    
    /* This always runs on render pool threads - no bukkit calls from here */ 
    private class FullWorldRenderState implements Runnable {
        DynmapWorld world;    /* Which world are we rendering */
        DynmapLocation loc;        
        int    map_index = -1;    /* Which map are we on */
        MapType map;
        TileFlags found = null;
        TileFlags rendered = null;
        LinkedList<MapTile> renderQueue = null;
        MapTile tile0 = null;
        int rendercnt = 0;
        int skipcnt = 0;
        DynmapCommandSender sender;
        String player;
        long timeaccum;
        HashSet<MapType> renderedmaps = new HashSet<MapType>();
        String activemaps;
        int activemapcnt;
        /* Min and max limits for chunk coords (for radius limit) */
        int cxmin, cxmax, czmin, czmax;
        String rendertype;
        boolean cancelled;
        boolean shutdown = false;
        boolean pausedforworld = false;
        boolean updaterender = false;
        boolean resume = false;
        boolean quiet = false;
        String mapname;
        AtomicLong total_render_ns = new AtomicLong(0L);
        AtomicInteger rendercalls = new AtomicInteger(0);
        long lastPendingSaveTS = 0; // Timestamp of last pending state save (msec)
        HashSet<String> storedTileIds = new HashSet<>();

        /* Full world, all maps render */
        FullWorldRenderState(DynmapWorld dworld, DynmapLocation l, DynmapCommandSender sender, String mapname, boolean updaterender, boolean resume) {
            this(dworld, l, sender, mapname, -1);
            if(updaterender) {
                rendertype = RENDERTYPE_UPDATERENDER;
                this.updaterender = true;
            }
            else {
                rendertype = RENDERTYPE_FULLRENDER;
            }
            this.resume = resume;

            final CountDownLatch latch = new CountDownLatch(1);

            if (resume) { // if resume render
                final MapStorage ms = world.getMapStorage();
                ms.enumMapBaseTiles(world, map, new MapStorageBaseTileEnumCB() {
                    @Override
                    public void tileFound(MapStorageTile tile, MapType.ImageEncoding enc) {
                        String tileId = String.format("%s_%s_%d_%d", tile.world.getName(), tile.map.getName(), tile.x, tile.y);
                        //sender.sendMessage("Tile found: " + tileId);
                        storedTileIds.add(tileId);
                    }
                }, new MapStorageTileSearchEndCB() {
                    @Override
                    public void searchEnded() {
                        latch.countDown();
                    }
                });

                try {
                    latch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    sender.sendMessage(e.toString());
                }
            }
        }
        
        /* Full world, all maps render, with optional render radius */
        FullWorldRenderState(DynmapWorld dworld, DynmapLocation l, DynmapCommandSender sender, String mapname, int radius) {
            world = dworld;
            loc = l;
            found = new TileFlags();
            rendered = new TileFlags();
            renderQueue = new LinkedList<MapTile>();
            this.sender = sender;
            if(sender instanceof DynmapPlayer)
                this.player = ((DynmapPlayer)sender).getName();
            else
                this.player = "";
            if(radius < 0) {
                cxmin = czmin = Integer.MIN_VALUE;
                cxmax = czmax = Integer.MAX_VALUE;
                rendertype = RENDERTYPE_FULLRENDER;
            }
            else {
                cxmin = ((int)l.x - radius)>>4;
                czmin = ((int)l.z - radius)>>4;
                cxmax = ((int)l.x + radius + 1)>>4;
                czmax = ((int)l.z + radius + 1)>>4;
                rendertype = RENDERTYPE_RADIUSRENDER;
            }
            this.mapname = mapname;
        }

        /* Single tile render - used for incremental renders */
        FullWorldRenderState(MapTile t) {
            world = getWorld(t.getDynmapWorld().getName());
            tile0 = t;
            cxmin = czmin = Integer.MIN_VALUE;
            cxmax = czmax = Integer.MAX_VALUE;
        }

        FullWorldRenderState(ConfigurationNode n) throws Exception {
            String w = n.getString("world", "");
            world = getWorld(w);
            if(world == null) throw new Exception();
            loc = new DynmapLocation();
            loc.world = world.getName();
            loc.x = (int)n.getDouble("locX", 0.0);
            loc.y = (int)n.getDouble("locY", 0.0);
            loc.z = (int)n.getDouble("locZ", 0.0);
            map_index = n.getInteger("mapindex", -1);
            if(map_index >= 0) {
                String m = n.getString("map","");
                map = world.maps.get(map_index);
                if((map == null) || (map.getName().equals(m) == false)) {
                    throw new Exception();
                }
            }
            else {
                map_index = -1;
                map = null;
            }
            found = new TileFlags();
            List<String> sl = n.getStrings("found", null);
            if(sl != null)
                found.load(sl);
            rendered = new TileFlags();
            sl = n.getStrings("rendered", null);
            if(sl != null)
                rendered.load(sl);
            renderQueue = new LinkedList<MapTile>();
            List<ConfigurationNode> tl = n.getNodes("queue");
            if(tl != null) {
                for(ConfigurationNode cn : tl) {
                    MapTile mt = MapTile.restoreTile(world, cn);
                    if(mt != null) {
                        renderQueue.add(mt);
                    }
                }
            }
            rendercnt = n.getInteger("count", 0);
            timeaccum = n.getInteger("timeaccum", 0);
            renderedmaps = new HashSet<MapType>();
            sl = n.getStrings("renderedmaps", null);
            if(sl != null) {
                for(String s : sl) {
                    for(int i = 0; i < world.maps.size(); i++) {
                        MapType mt = world.maps.get(i);
                        if(mt.getName().equals(s)) {
                            renderedmaps.add(mt);
                            break;
                        }
                    }
                }
                if(sl.size() > renderedmaps.size()) {   /* Missed one or more? */
                    throw new Exception();
                }
            }
            activemaps = n.getString("activemaps", "");
            activemapcnt = n.getInteger("activemapcnt", 0);
            cxmin = n.getInteger("cxmin", 0);
            cxmax = n.getInteger("cxmax", 0);
            czmin = n.getInteger("czmin", 0);
            czmax = n.getInteger("czmax", 0);
            rendertype = n.getString("rendertype", "");
            mapname = n.getString("mapname", null);
            player = n.getString("player", "");
            updaterender = rendertype.equals(RENDERTYPE_UPDATERENDER);
            sender = null;
            if(player.length() > 0) {
                sender = core.getServer().getPlayer(player);
            }
        }
        
        public HashMap<String,Object> saveState() {
            HashMap<String,Object> v = new HashMap<String,Object>();
            
            v.put("world", world.getName());
            v.put("locX", loc.x);
            v.put("locY", loc.y);
            v.put("locZ", loc.z);
            if(map != null) {
                v.put("mapindex", map_index);
                v.put("map", map.getName());
            }
            else {
                v.put("mapindex", -1);
                v.put("map", "");
            }
            v.put("found", found.save());
            v.put("rendered", rendered.save());
            LinkedList<ConfigurationNode> queue = new LinkedList<ConfigurationNode>();
            for(MapTile tq : renderQueue) {
                ConfigurationNode n = tq.saveTile();
                if(n != null)
                    queue.add(n);
            }
            v.put("queue", queue);
            v.put("count", rendercnt);
            v.put("timeaccum", timeaccum);
            LinkedList<String> rmaps = new LinkedList<String>();
            for(MapType mt : renderedmaps) {
                rmaps.add(mt.getName());
            }
            v.put("renderedmaps", rmaps);
            v.put("activemaps", activemaps);
            v.put("activemapcnt", activemapcnt);
            v.put("cxmin", cxmin);
            v.put("cxmax", cxmax);
            v.put("czmin", czmin);
            v.put("czmax", czmax);
            v.put("rendertype", rendertype);
            if(mapname != null)
                v.put("mapname", mapname);
            v.put("player", player);
            return v;
        }
        
        public String toString() {
            return "world=" + world.getName() + ", map=" + map;
        }
        
        public void cleanup() {
            if(tile0 == null) {
                synchronized(lock) {
                    String wn = world.getName();
                    FullWorldRenderState rs = active_renders.get(wn);
                    if (rs == this) {
                        active_renders.remove(wn);
                    }
                }
            }
            else {
                tileQueue.done(tile0);            	
            }
        }
        
        public void saveRefresh() {
            if (saverestorepending && world.isLoaded() && (savependingperiod > 0)) {
                savePending(this.world, true);    // Save the pending data for the given world
            }
        }
        
        public void run() {
            long tstart = System.currentTimeMillis();
            MapTile tile = null;
            List<MapTile> tileset = null;
            
            if(cancelled) {
            	cleanup();
            	if (!shutdown) {
            	    saveRefresh();
            	}
            	return;
            }
            if(tile0 == null) {    /* Not single tile render */
                if (saverestorepending && world.isLoaded() && (savependingperiod > 0) && ((lastPendingSaveTS + (1000 *savependingperiod))  < System.currentTimeMillis())) {
                    savePending(this.world, true);    // Save the pending data for the given world
                    lastPendingSaveTS = System.currentTimeMillis();
                }
                if(pausefullrenders || tpspausefullrenders) {    /* Update renders are paused? */
                    scheduleDelayedJob(this, 20*5); /* Delay 5 seconds and retry */
                    return;
                }
                else if(world.isLoaded() == false) {    /* Update renders are paused? */
                    if(!pausedforworld) {
                        pausedforworld = true;
                        Log.info("Paused " + rendertype + " for world '" + world.getName() + "' - world unloaded");
                    }
                    scheduleDelayedJob(this, 20*5); /* Delay 5 seconds and retry */
                    return;
                }
                else if(pausedforworld) {
                    pausedforworld = false;
                    Log.info("Unpaused " + rendertype + " for world '" + world.getName() + "' - world reloaded");
                }
                /* If render queue is empty, start next map */
                if(renderQueue.isEmpty()) {
                    if(map_index >= 0) { /* Finished a map? */
                        double msecpertile = (double)timeaccum / (double)((rendercnt>0)?rendercnt:1)/(double)activemapcnt;
                        int rndcalls = rendercalls.get();
                        if(rndcalls == 0) rndcalls = 1;
                        double rendtime = total_render_ns.doubleValue() * 0.000001 / rndcalls;
                        if(activemapcnt > 1) {
                            if (skipcnt > 1)
                                sendMessage(String.format("%s of maps [%s] of '%s' completed - %d tiles rendered each (%.2f msec/map-tile, %.2f msec per render) (%d tiles skipped)",
                                    rendertype, activemaps, world.getName(), rendercnt, msecpertile, rendtime, skipcnt));
                            else
                                sendMessage(String.format("%s of maps [%s] of '%s' completed - %d tiles rendered each (%.2f msec/map-tile, %.2f msec per render)",
                                    rendertype, activemaps, world.getName(), rendercnt, msecpertile, rendtime));
                        }
                        else {
                            if (skipcnt > 1)
                                sendMessage(String.format("%s of map '%s' of '%s' completed - %d tiles rendered (%.2f msec/map-tile, %.2f msec per render) (%d tiles skipped)",
                                    rendertype, activemaps, world.getName(), rendercnt, msecpertile, rendtime, skipcnt));
                            else
                                sendMessage(String.format("%s of map '%s' of '%s' completed - %d tiles rendered (%.2f msec/map-tile, %.2f msec per render)",
                                    rendertype, activemaps, world.getName(), rendercnt, msecpertile, rendtime));
                        }
                        skipcnt = 0;
                        /* Now, if fullrender, use the render bitmap to purge obsolete tiles */
                        if(rendertype.equals(RENDERTYPE_FULLRENDER)) {
                            if(activemapcnt == 1) {
                                map.purgeOldTiles(world, rendered);
                            }
                            else {
                                for(MapType mt : map.getMapsSharingRender(world)) {
                                    mt.purgeOldTiles(world, rendered);
                                }
                            }
                        }
                    }                	
                    found.clear();
                    rendered.clear();
                    rendercnt = 0;
                    timeaccum = 0;
                    total_render_ns.set(0);
                    rendercalls.set(0);
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
                        sendMessage(rendertype + " of '" + world.getName() + "' finished.");
                        cleanup();
                        saveRefresh();
                        return;
                    }
                    map = world.maps.get(map_index);
                    List<String> activemaplist = map.getMapNamesSharingRender(world);
                    /* Build active map list */
                    activemaps = "";
                    if(mapname != null) {
                        activemaps = mapname;
                        activemapcnt = 1;
                    }
                    else {
                        activemapcnt = 0;
                        for(String n : activemaplist) {
                            if(activemaps.length() > 0)
                                activemaps += ",";
                            activemaps += n;
                            activemapcnt++;
                        }
                    }
                    /* Mark all the concurrently rendering maps rendered */
                    renderedmaps.addAll(map.getMapsSharingRender(world));

                    /* Now, prime the render queue */
                    for (MapTile mt : map.getTiles(world, (int)loc.x, (int)loc.y, (int)loc.z)) {
                        if (!found.getFlag(mt.tileOrdinalX(), mt.tileOrdinalY())) {
                            found.setFlag(mt.tileOrdinalX(), mt.tileOrdinalY(), true);
                            renderQueue.add(mt);
                        }
                    }
                    if(!updaterender) { /* Only add other seed points for fullrender */
                        /* Add spawn location too (helps with some worlds where 0,64,0 may not be generated */
                        DynmapLocation sloc = world.getSpawnLocation();
                        for (MapTile mt : map.getTiles(world, (int)sloc.x, (int)sloc.y, (int)sloc.z)) {
                            if (!found.getFlag(mt.tileOrdinalX(), mt.tileOrdinalY())) {
                                found.setFlag(mt.tileOrdinalX(), mt.tileOrdinalY(), true);
                                renderQueue.add(mt);
                            }
                        }
                        if(world.seedloc != null) {
                            for(DynmapLocation seed : world.seedloc) {
                                for (MapTile mt : map.getTiles(world, (int)seed.x, (int)seed.y, (int)seed.z)) {
                                    if (!found.getFlag(mt.tileOrdinalX(),mt.tileOrdinalY())) {
                                        found.setFlag(mt.tileOrdinalX(),mt.tileOrdinalY(), true);
                                        renderQueue.add(mt);
                                    }
                                }
                            }
                        }
                    }
                }
                if(parallelrendercnt > 1) { /* Doing parallel renders? */
                    tileset = new ArrayList<MapTile>();
                    for(int i = 0; i < parallelrendercnt; i++) {
                        tile = renderQueue.pollFirst();
                        if(tile != null)
                            tileset.add(tile);
                    }
                }
                else {
                    tile = renderQueue.pollFirst();
                }
            }
            else {    /* Else, single tile render */
                if(pauseupdaterenders || tpspauseupdaterenders) {
                    scheduleDelayedJob(this, 5*20); /* Retry after 5 seconds */
                    return;
                }
                tile = tile0;
            }

            boolean notdone = true;
            
            if(tileset != null) {
                long save_timeaccum = timeaccum;
                List<Future<Boolean>> rslt = new ArrayList<Future<Boolean>>();
                final int cnt = tileset.size();
                for(int i = 1; i < cnt; i++) {   /* Do all but first on other threads */
                    final MapTile mt = tileset.get(i);
                    if((mapman != null) && (mapman.render_pool != null)) {
                        final long ts = tstart;
                        Future<Boolean> future = mapman.render_pool.submit(new Callable<Boolean>() {
                            public Boolean call() {
                                return processTile(mt, ts, cnt);
                            }
                        });
                        rslt.add(future);
                    }
                }
                /* Now, do our render (first one) */
                tile = tileset.get(0);
                notdone = processTile(tile, tstart, cnt);
                if ((!notdone) && (tile0 == null)) {    // Not completed? Push back on queue
                    renderQueue.push(tile);
                }
                /* Now, join with others */
                for(int i = 0; i < rslt.size(); i++) {
                    boolean rsltflag = false;
                    try {
                        rsltflag = rslt.get(i).get();
                    } catch (CancellationException cx) {
                        rsltflag = false;
                    } catch (ExecutionException xx) {
                        Log.severe("Execution exception while processing tile: ", xx.getCause());
                        rsltflag = false;
                    } catch (InterruptedException ix) {
                        rsltflag = false;
                    }
                    notdone = notdone && rsltflag;
                    if ((!rsltflag) && (tile0 == null)) {    // Not completed? Push back on queue
                        tile = tileset.get(i+1);
                        renderQueue.push(tile);
                    }
                }
                timeaccum = save_timeaccum + System.currentTimeMillis() - tstart;
            }
            else {
                notdone = processTile(tile, tstart, 1);
                if ((!notdone) && (tile0 == null)) {    // Not completed? Push back on queue
                    renderQueue.push(tile);
                }
            }
            
            if(notdone) {
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
            else {
                //cleanup();
                shutdownRender();
            }
        }

        private boolean processTile(MapTile tile, long tstart, int parallelcnt) {
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
            MapChunkCache cache = core.getServer().createMapChunkCache(world, requiredChunks, tile.isBlockTypeDataNeeded(), 
                                                      tile.isHightestBlockYDataNeeded(), tile.isBiomeDataNeeded(), 
                                                      tile.isRawBiomeDataNeeded());
            if(cache == null) {
                /* If world unloaded, don't cancel */
                if(world.isLoaded() == false) {
                    return true;
                }
                return false; /* Cancelled/aborted */
            }
            /* Update stats */
            chunk_caches_created.incrementAndGet();
            for (MapChunkCache.ChunkStats cs : MapChunkCache.ChunkStats.values()) {
                chunks_read[cs.ordinal()].addAndGet(cache.getChunksLoaded(cs));
                chunks_read_times[cs.ordinal()].addAndGet(cache.getTotalRuntimeNanos(cs));
            }

            boolean skipTile = false;
            if (resume) {
                String tileId = String.format("%s_%s_%d_%d", tile.world.getName(), map.getName(), tile.tileOrdinalX(), tile.tileOrdinalY());
                skipTile = storedTileIds.contains(tileId);
            }

            if(tile0 != null) {    /* Single tile? */
                if(cache.isEmpty() == false) {
                    if (skipTile) {
                        skipcnt++;
                    } else {
                        tile.render(cache, null);
                    }
                }
            }
            else {
        		/* Remove tile from tile queue, since we're processing it already */
            	tileQueue.remove(tile);
                /* Switch to not checking if rendered tile is blank - breaks us on skylands, where tiles can be nominally blank - just work off chunk cache empty */
                if (cache.isEmpty() == false) {
                    boolean upd;
                    if (skipTile) {
                        upd = false;
                        skipcnt++;
                    } else {
                        long rt0 = System.nanoTime();
                        upd = tile.render(cache, mapname);
                        total_render_ns.addAndGet(System.nanoTime()-rt0);
                        rendercalls.incrementAndGet();
                    }
                    synchronized(lock) {
                        rendered.setFlag(tile.tileOrdinalX(), tile.tileOrdinalY(), true);
                        if(upd || (!updaterender)) {    /* If updated or not an update render */
                            /* Add adjacent unrendered tiles to queue */
                            for (MapTile adjTile : map.getAdjecentTiles(tile)) {
                                if (!found.getFlag(adjTile.tileOrdinalX(),adjTile.tileOrdinalY())) {
                                    found.setFlag(adjTile.tileOrdinalX(), adjTile.tileOrdinalY(), true);
                                    renderQueue.add(adjTile);
                                }
                            }
                        }
                    }
                }
                synchronized(lock) {
                    if(!cache.isEmpty()) {
                        rendercnt++;
                        timeaccum += System.currentTimeMillis() - tstart;
                        if (((rendercnt % progressinterval) == 0) && (!quiet)) {
                            int rndcalls = rendercalls.get();
                            if (rndcalls == 0) rndcalls = 1;
                            double rendtime = total_render_ns.doubleValue() * 0.000001 / rndcalls;
                            double msecpertile = (double)timeaccum / (double)rendercnt / (double)activemapcnt;
                            if(activemapcnt > 1) {
                                if (skipcnt > 1)
                                    sendMessage(String.format("%s of maps [%s] of '%s' in progress - %d tiles rendered each (%.2f msec/map-tile, %.2f msec per render) (%d tiles skipped)",
                                            rendertype, activemaps, world.getName(), rendercnt, msecpertile, rendtime, skipcnt));
                                else
                                    sendMessage(String.format("%s of maps [%s] of '%s' in progress - %d tiles rendered each (%.2f msec/map-tile, %.2f msec per render)",
                                            rendertype, activemaps, world.getName(), rendercnt, msecpertile, rendtime));
                            } else {
                                if (skipcnt > 1)
                                    sendMessage(String.format("%s of map '%s' of '%s' in progress - %d tiles rendered (%.2f msec/tile, %.2f msec per render) (%d tiles skipped)",
                                            rendertype, activemaps, world.getName(), rendercnt, msecpertile, rendtime, skipcnt));
                                else
                                    sendMessage(String.format("%s of map '%s' of '%s' in progress - %d tiles rendered (%.2f msec/tile, %.2f msec per render)",
                                            rendertype, activemaps, world.getName(), rendercnt, msecpertile, rendtime));
                            }
                            skipcnt = 0;
                        }
                    }
                }
            }
            /* And unload what we loaded */
            cache.unloadChunks();
            
            return true;
        }
        
        public void cancelRender() {
        	cancelled = true;
        	storedTileIds.clear();
        }

        public void shutdownRender() {
            shutdown = true;
            cancelRender();
        }
        public void sendMessage(String msg) {
            if(sender != null) {
                sender.sendMessage(msg);
            }
            else {
                Log.info(msg);
            }
        }
    }

    private class ProcessOBJExport implements Runnable {
        private OBJExport exp;
        private DynmapCommandSender sender;
        
        public void run() {
            exp.processExport(sender);
        }
    }
    
    private class CheckWorldTimes implements Runnable {
    	HashMap<String, Polygon> last_worldborder = new HashMap<String, Polygon>();
        public void run() {
            Future<Integer> f = core.getServer().callSyncMethod(new Callable<Integer>() {
                public Integer call() throws Exception {
                    long now_nsec = System.nanoTime();
                    for(DynmapWorld w : worlds) {
                        if(w.isLoaded()) {
                            int new_servertime = (int)(w.getTime() % 24000);
                            /* Check if we went from night to day */
                            boolean wasday = w.servertime >= 0 && w.servertime < 13700;
                            boolean isday = new_servertime >= 0 && new_servertime < 13700;
                            w.servertime = new_servertime;
                            if(wasday != isday) {
                                pushUpdate(w, new Client.DayNight(isday));            
                            }
                            // Check world border
                            Polygon wb = w.getWorldBorder();
                            Polygon oldwb = last_worldborder.get(w.getName());
                            if (((wb == null) && (oldwb == null)) ||
                            		wb.equals(oldwb)) {	// No change
                            }
                            else { 
                                core.listenerManager.processWorldEvent(EventType.WORLD_SPAWN_CHANGE, w);
                            }
                        }
                        /* Tick invalidated tiles processing */
                        for(MapTypeState mts : w.mapstate) {
                            mts.tickMapTypeState(now_nsec);
                        }
                    }
                    return 0;
                }
            });
            if (f == null) {
                return;
            }
            try {
                f.get();
            } catch (CancellationException cx) {
                return;
            } catch (ExecutionException ex) {
                Log.severe("Error while checking world times: ", ex.getCause());
            } catch (Exception ix) {
                Log.severe(ix);
            }
            scheduleDelayedJob(this, 5000);
        }
    }
    
    private class DoZoomOutProcessing implements Runnable {
        public void run() {
            if (!tpspausezoomout) {
                Debug.debug("DoZoomOutProcessing started");
                ArrayList<DynmapWorld> wl = new ArrayList<DynmapWorld>(worlds);
                for(DynmapWorld w : wl) {
                    w.freshenZoomOutFiles();
                }
                Debug.debug("DoZoomOutProcessing finished");
                scheduleDelayedJob(this, zoomout_period*1000);
            }
            else {
                scheduleDelayedJob(this, 5*1000);
            }
        }
    }
    
    private class DoTouchProcessing implements Runnable {
        public void run() {
            processTouchEvents();
            /* Try to keep update queue above accelerate level, if we have enough */
            int cnt = 2*tileQueue.accelDequeueThresh;
            if(cnt < 100) cnt = 100;
            cnt = cnt - tileQueue.size();
            if(cnt > 0) {
                addNextTilesToUpdate(cnt);
            }
            scheduleDelayedJob(this, 1000); /* Once per second */
        }
    }
    
    private void sendPlayerEnterExit(DynmapPlayer player, EnterExitText txt) {
		core.getServer().scheduleServerTask(new Runnable() {
			public void run() {
				if (enterexitUseTitle) {
					player.sendTitleText(txt.title, txt.subtitle, titleFadeIn, titleStay, titleFadeOut);
				}
				else {
					if (txt.title != null) player.sendMessage(txt.title);
					if (txt.subtitle != null) player.sendMessage(txt.subtitle);
				}
			}
		}, 0);    	
    }
    
    private void enqueueMessage(UUID uuid, DynmapPlayer player, EnterExitText txt, boolean isEnter) {
    	SendQueueRec rec = entersetsendqueue.get(uuid);
    	if (rec == null) {
    		rec = new SendQueueRec();
    		rec.player = player;
    		rec.tickdelay = 0;
    		entersetsendqueue.put(uuid, rec);
    	}
    	TextQueueRec txtrec = new TextQueueRec();
    	txtrec.isEnter = isEnter;
    	txtrec.txt = txt;
    	rec.queue.add(txtrec);
    	// If enter replaces exits, and we just added enter, purge exits
    	if (enterReplacesExits && isEnter) {
    		ArrayList<TextQueueRec> newlst = new ArrayList<TextQueueRec>();
    		for (TextQueueRec r : rec.queue) {
    			if (r.isEnter) newlst.add(r);	// Keep the enter records
    		}
    		rec.queue = newlst;
    	}
    }
    
    private class DoUserMoveProcessing implements Runnable {
        public void run() {
            HashMap<UUID, HashSet<EnterExitMarker>> newstate = new HashMap<UUID, HashSet<EnterExitMarker>>();
        	DynmapPlayer[] pl = core.playerList.getOnlinePlayers();
        	for (DynmapPlayer player : pl) {
        		if (player == null) continue;
        		UUID puuid = player.getUUID();
        		HashSet<EnterExitMarker> newset = new HashSet<EnterExitMarker>();
        		DynmapLocation dl = player.getLocation();
        		if (dl != null) {
        			MarkerAPIImpl.getEnteredMarkers(dl.world, dl.x, dl.y, dl.z, newset);
        		}
        		HashSet<EnterExitMarker> oldset = entersetstate.get(puuid);
        		// See which we just left
        		if (oldset != null) {
            		for (EnterExitMarker m : oldset) {
            			EnterExitText txt = m.getFarewellText();
            			if ((txt != null) && (newset.contains(m) == false)) {
            				enqueueMessage(puuid, player, txt, false);
            			}
            		}        			
        		}
        		// See which we just entered
        		for (EnterExitMarker m : newset) {
        			EnterExitText txt = m.getGreetingText();
        			if ((txt != null) && ((oldset == null) || (oldset.contains(m) == false))) {
        				enqueueMessage(puuid, player, txt, true);
        			}
        		}
        		newstate.put(puuid, newset);
        	}
        	entersetstate = newstate;	// Replace old with new
        	
        	// Go through queues - send pending messages
        	List<UUID> keys = new ArrayList<UUID>(entersetsendqueue.keySet());
        	for (UUID id : keys) {
        		SendQueueRec rec = entersetsendqueue.get(id);
        		// Check delay - count down if needed
        		if (rec.tickdelay > enterexitperiod) {
        			rec.tickdelay -= enterexitperiod;
        			continue;
        		}
        		rec.tickdelay = 0;
        		// If something to send, send it
        		if (rec.queue.size() > 0) {
        			TextQueueRec txt = rec.queue.remove(0);
        			sendPlayerEnterExit(rec.player, txt.txt);	// And send it
        			rec.tickdelay = 50 * (titleFadeIn + 10);	// Delay by fade in time plus 1/2 second       			
        		}
        		else {	// Else, if we are empty and exhausted delay, remove it
        			entersetsendqueue.remove(id);
        		}
        	}			
        	if (enterexitperiod > 0) {
        		scheduleDelayedJob(this, enterexitperiod);
        	}
        }
    }

    private void addNextTilesToUpdate(int cnt) {
        ArrayList<MapTile> tiles = new ArrayList<MapTile>();
        TileFlags.TileCoord coord = new TileFlags.TileCoord();
        while(cnt > 0) {
            tiles.clear();
            for(DynmapWorld w : worlds) {
                for(MapTypeState mts : w.mapstate) {
                    if(mts.getNextInvalidTileCoord(coord)) {
                        mts.type.addMapTiles(tiles, w, coord.x, coord.y);
                        mts.validateTile(coord.x, coord.y);
                    }
                }
            }
            if(tiles.size() == 0) {
                return;
            }
            for(MapTile mt : tiles) {
                tileQueue.push(mt);
                cnt--;
            }
        }
    }

    public MapManager(DynmapCore core, ConfigurationNode configuration) {
        this.core = core;
        mapman = this;
        
        chunks_read = new AtomicInteger[MapChunkCache.ChunkStats.values().length];
        chunks_read_times = new AtomicLong[MapChunkCache.ChunkStats.values().length];
        for (int i = 0; i < MapChunkCache.ChunkStats.values().length; i++) {
            chunks_read[i] = new AtomicInteger(0);
            chunks_read_times[i] = new AtomicLong(0L);
        }

        /* Get block hiding data, if any */
        hideores = configuration.getBoolean("hideores", false);
        useBrightnessTable = configuration.getBoolean("use-brightness-table", false);
        
        blockalias = new HashMap<String, String>();
        if (hideores) {
            setBlockAlias(DynmapBlockState.GOLD_ORE_BLOCK, DynmapBlockState.STONE_BLOCK); // Gold ore
            setBlockAlias(DynmapBlockState.IRON_ORE_BLOCK, DynmapBlockState.STONE_BLOCK);  // Iron ore
            setBlockAlias(DynmapBlockState.COAL_ORE_BLOCK, DynmapBlockState.STONE_BLOCK);  // Coal ore
            setBlockAlias(DynmapBlockState.LAPIS_ORE_BLOCK, DynmapBlockState.STONE_BLOCK);  // Lapis ore
            setBlockAlias(DynmapBlockState.DIAMOND_ORE_BLOCK, DynmapBlockState.STONE_BLOCK);  // Diamond ore
            setBlockAlias(DynmapBlockState.REDSTONE_ORE_BLOCK, DynmapBlockState.STONE_BLOCK);  // Redstone ore
            setBlockAlias(DynmapBlockState.LIT_REDSTONE_ORE_BLOCK, DynmapBlockState.STONE_BLOCK);  // Glowing Redstone ore
            setBlockAlias(DynmapBlockState.EMERALD_ORE_BLOCK, DynmapBlockState.STONE_BLOCK);  // Emerald ore
            setBlockAlias(DynmapBlockState.QUARTZ_ORE_BLOCK, DynmapBlockState.STONE_BLOCK);  // Nether quartz ore
        }
        ConfigurationNode ba = configuration.getNode("block-alias");
        if (ba != null) {
            for (String id : ba.keySet()) {
                String srcname = id.trim();
                String newname = ba.getString(id, srcname);
                if (srcname != newname) {
                    setBlockAlias(srcname, newname);
                }
            }
        }
        
        /* See what priority to use */
        usenormalpriority = configuration.getBoolean("usenormalthreadpriority", false);
                
        /* Initialize HD map manager */
        hdmapman = new HDMapManager();  
        hdmapman.loadHDShaders(core);
        hdmapman.loadHDPerspectives(core);
        hdmapman.loadHDLightings(core);
        parallelrendercnt = configuration.getInteger("parallelrendercnt", 0);
        progressinterval = configuration.getInteger("progressloginterval", 100);
        if(progressinterval < 100) progressinterval = 100;
        saverestorepending = configuration.getBoolean("saverestorepending", true);
        tileupdatedelay = configuration.getInteger("tileupdatedelay", 30);
        
        tpslimit_updaterenders = configuration.getDouble("update-min-tps", 18.0);
        if (tpslimit_updaterenders > 19.5) tpslimit_updaterenders = 19.5;
        tpslimit_fullrenders = configuration.getDouble("fullrender-min-tps", 18.0);
        if (tpslimit_fullrenders > 19.5) tpslimit_fullrenders = 19.5;
        tpslimit_zoomout = configuration.getDouble("zoomout-min-tps", 18.0);
        if (tpslimit_zoomout > 19.5) tpslimit_zoomout = 19.5;
        // Load enter/exit processing settings
        enterexitperiod = configuration.getInteger("enterexitperiod", DEFAULT_ENTEREXIT_PERIOD);
        titleFadeIn = configuration.getInteger("titleFadeIn", DEFAULT_TITLE_FADEIN);
        titleStay = configuration.getInteger("titleStay", DEFAULT_TITLE_STAY);
        titleFadeOut = configuration.getInteger("titleFadeOut", DEFAULT_TITLE_FADEOUT);
        enterexitUseTitle = configuration.getBoolean("enterexitUseTitle", DEFAULT_ENTEREXIT_USETITLE);
        enterReplacesExits = configuration.getBoolean("enterReplacesExits", DEFAULT_ENTEREPLACESEXITS);
        // Load the save pending job period
        savependingperiod = configuration.getInteger("save-pending-period", 900);
        if ((savependingperiod > 0) && (savependingperiod < 60)) savependingperiod = 60;
        
        this.tileQueue = new AsynchronousQueue<MapTile>(
                new Handler<MapTile>() {
                @Override
                public void handle(MapTile t) {
                    FullWorldRenderState job = new FullWorldRenderState(t);
                    if(!scheduleDelayedJob(job, 0))
                        job.cleanup();
                }
            }, 
            (int) (configuration.getDouble("renderinterval", 0.5) * 1000),
            configuration.getInteger("renderacceleratethreshold", 30),
            (int)(configuration.getDouble("renderaccelerateinterval", 0.2) * 1000), 
            configuration.getInteger("tiles-rendered-at-once", (Runtime.getRuntime().availableProcessors()+1)/2),
            usenormalpriority);

        /* On dedicated thread, so default to no delays */
        timeslice_int = (long)(configuration.getDouble("timesliceinterval", 0.0) * 1000);
        max_chunk_loads_per_tick = configuration.getInteger("maxchunkspertick", DEFAULT_CHUNKS_PER_TICK);
        if(max_chunk_loads_per_tick < 5) max_chunk_loads_per_tick = 5;
        /* Get zoomout processing periond in seconds */
        zoomout_period = configuration.getInteger("zoomoutperiod", DEFAULT_ZOOMOUT_PERIOD);
        if(zoomout_period < 5) zoomout_period = 5;
        
        tileQueue.start();
    }

    void renderFullWorld(DynmapLocation l, DynmapCommandSender sender, String mapname, boolean update, boolean resume) {
        DynmapWorld world = getWorld(l.world);
        if (world == null) {
            sender.sendMessage("Could not render: world '" + l.world + "' not defined in configuration.");
            return;
        }
        String wname = l.world;
        FullWorldRenderState rndr;
        synchronized(lock) {
            rndr = active_renders.get(wname);
            if(rndr != null) {
                sender.sendMessage(rndr.rendertype + " of world '" + wname + "' already active.");
                return;
            }
            rndr = new FullWorldRenderState(world,l,sender, mapname, update, resume);    /* Make new activation record */
            active_renders.put(wname, rndr);    /* Add to active table */
        }
        /* Schedule first tile to be worked */
        scheduleDelayedJob(rndr, 0);

        if(update)
            sender.sendMessage("Update render starting on world '" + wname + "'...");
        else if (resume)
            sender.sendMessage("Full render resuming on world '" + wname + "'...");
        else
            sender.sendMessage("Full render starting on world '" + wname + "'...");
    }

    void renderWorldRadius(DynmapLocation l, DynmapCommandSender sender, String mapname, int radius) {
        DynmapWorld world = getWorld(l.world);
        if (world == null) {
            sender.sendMessage("Could not render: world '" + l.world + "' not defined in configuration.");
            return;
        }
        String wname = l.world;
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
    
    public void startOBJExport(OBJExport exp, DynmapCommandSender sender) {
        ProcessOBJExport e = new ProcessOBJExport();
        e.exp = exp;
        e.sender = sender;
        scheduleDelayedJob(e, 0);
    }

    void cancelRender(String w, DynmapCommandSender sender) {
    	synchronized(lock) {
    		if(w != null) {
    			FullWorldRenderState rndr;
    			rndr = active_renders.remove(w);
    			if(rndr != null) {
    				rndr.cancelRender();	/* Cancel render */
    				if(sender != null) {
    					sender.sendMessage("Cancelled render for '" + w + "'");
    				}
    			}
    		}
    		else {	/* Else, cancel all */
    		    String[] wids = active_renders.keySet().toArray(new String[0]);
    			for(String wid : wids) {
    				FullWorldRenderState rnd = active_renders.remove(wid);
					rnd.cancelRender();
    				if(sender != null) {
    					sender.sendMessage("Cancelled render for '" + wid + "'");
    				}
    			}
    		}
    	}
    }
    
    void purgeQueue(DynmapCommandSender sender, String worldname) {
        DynmapWorld world = null;
        if (worldname != null) {
            world = this.getWorld(worldname);
            if (world == null) {
                sender.sendMessage("World '" + worldname + "' not found.");
                return;
            }
        }
        if(tileQueue != null) {
            int cnt = 0;
            List<MapTile> popped = tileQueue.popAll();
            if(popped != null) {
                cnt = popped.size();
                if (worldname != null) {
                    for (MapTile mt : popped) {
                        if (mt.world != world) {
                            tileQueue.push(mt); // Push tiles for worlds other than the one we're purging
                        }
                    }
                }
                popped.clear();
            }
            for(DynmapWorld dw : this.worlds) {
                if ((worldname != null) && (dw != world)) {
                    continue;
                }
                for(MapTypeState mts : dw.mapstate) {
                    cnt += mts.getInvCount();
                    mts.clear();
                }
            }
            sender.sendMessage("Purged " + cnt + " tiles from queue");
        }
    }
    
    void purgeMap(final DynmapCommandSender sender, final String worldname, final String mapname) {
        final DynmapWorld world = getWorld(worldname);
        if (world == null) {
            sender.sendMessage("Could not purge map: world '" + worldname + "' not defined in configuration.");
            return;
        }
        MapType mt = null;
        for (MapType mtp : world.maps) {
            if (mtp.getName().equals(mapname)) {
                mt = mtp;
                break;
            }
        }
        if (mt == null) {
            sender.sendMessage("Could not purge map: map '" + mapname + "' not defined in configuration.");
            return;
        }
        final MapType mtf = mt;
        Runnable purgejob = new Runnable() {
            public void run() {
                world.purgeMap(mtf);
                sender.sendMessage("Purge of tiles for map '" + mapname + "' for world '" + worldname + "' completed");
            }
        };
        /* Schedule first tile to be worked */
        scheduleDelayedJob(purgejob, 0);

        sender.sendMessage("Map tile purge starting on map '" + mapname + "' for world '" + worldname + "'...");
    }

    void purgeWorld(final DynmapCommandSender sender, final String worldname) {
        final DynmapWorld world = getWorld(worldname);
        if (world == null) {
            sender.sendMessage("Could not purge world: world '" + worldname + "' not defined in configuration.");
            return;
        }
        // Cancel any pending render
        cancelRender(worldname, sender);
        // And purge update queue for world
        purgeQueue(sender, worldname);
        
        Runnable purgejob = new Runnable() {
            public void run() {
                world.purgeTree();
                sender.sendMessage("Purge of files for world '" + worldname + "' completed");
            }
        };
        /* Schedule first tile to be worked */
        scheduleDelayedJob(purgejob, 0);

        sender.sendMessage("World purge starting on world '" + worldname + "'...");
    }

    public boolean activateWorld(DynmapWorld dynmapWorld) {
        String worldname = dynmapWorld.getName();
        ConfigurationNode worldconfig = core.getWorldConfiguration(dynmapWorld);
        if(!dynmapWorld.loadConfiguration(core, worldconfig)) {
            Log.info("World '" + worldname + "' disabled");
            disabled_worlds.add(worldname);   /* Add to disabled world list */
            DynmapWorld oldw = worldsLookup.remove(worldname);
            if(oldw != null)
                worlds.remove(oldw);
            return false;
        }
        /* Remove from disabled, if it was there before */
        disabled_worlds.remove(worldname);
        // TODO: Make this less... weird...
        // Insert the world on the same spot as in the configuration.
        HashMap<String, Integer> indexLookup = new HashMap<String, Integer>();
        List<Map<String,Object>> nodes = core.world_config.getMapList("worlds");
        for (int i = 0; i < nodes.size(); i++) {
            Map<String,Object> node = nodes.get(i);
            indexLookup.put((String)node.get("name"), i);
        }
        Integer worldIndex = indexLookup.get(worldname);
        if(worldIndex == null) {
        	worlds.add(dynmapWorld);	/* Put at end if no world section */
        }
        else {
        	int insertIndex;
        	for(insertIndex = 0; insertIndex < worlds.size(); insertIndex++) {
        		Integer nextWorldIndex = indexLookup.get(worlds.get(insertIndex).getName());
        		if (nextWorldIndex == null || worldIndex < nextWorldIndex.intValue()) {
        			break;
       			}
        	}
        	worlds.add(insertIndex, dynmapWorld);
        }
        worldsLookup.put(worldname, dynmapWorld);
        core.events.trigger("worldactivated", dynmapWorld);
        /* If world is loaded, also handle this */
        if(dynmapWorld.isLoaded()) {
            loadWorld(dynmapWorld);
        }
        dynmapWorld.activateZoomOutFreshen();
        return true;
    }

    public void deactivateWorld(String wname) {
        //cancelRender(wname, null);  /* Cancel any render */
        DynmapWorld w = worldsLookup.get(wname);
        if (w != null) {
            if(saverestorepending)
                savePending(w, false);  // Save any pending jobs
            else
                cancelRender(wname, null);  /* Cancel any render */
        }
        w = worldsLookup.remove(wname); /* Remove from lookup */
        if(w != null) { /* If found, remove from active list */
            worlds.remove(w);
        }
        disabled_worlds.remove(wname);
    }
    
    public void loadWorld(DynmapWorld dynmapWorld) {
        /* Now, restore any pending renders for this world */
        if(saverestorepending)
            loadPending(dynmapWorld);
    }

    public void unloadWorld(DynmapWorld dynmapWorld) {
        if(saverestorepending)
            savePending(dynmapWorld, false);
    }

    private void loadPending(DynmapWorld w) {
        String wname = w.getName();
        File f = new File(core.getDataFolder(), wname + ".pending");
        if(f.exists()) {
            ConfigurationNode cn = new ConfigurationNode(f);
            cn.load();
            /* Get the saved tile definitions */
            int cnt = 0;
            List<ConfigurationNode> tiles = cn.getNodes("tiles");
            if(tiles != null) {
                for(ConfigurationNode tile : tiles) {
                    MapTile mt = MapTile.restoreTile(w, tile);  /* Restore tile, if possible */
                    if(mt != null) {
                        if(tileQueue.push(mt)) {
                            cnt++;
                        }
                    }
                }
            }
            /* Get invalid tiles */
            ConfigurationNode invmap = cn.getNode("invalid");
            if(invmap != null) {
                for(MapTypeState mts : w.mapstate) {
                    List<String> v = invmap.getStrings(mts.type.getPrefix(), null);
                    if(v != null) {
                        mts.restore(v);
                        cnt += mts.getInvCount();
                    }
                }
            }
            if(cnt > 0) {
                Log.info("Loaded " + cnt + " pending tile renders for world '" + wname + "'");
            }
            /* Get invalidated zoom out tiles pending */
            invmap = cn.getNode("invZoomOut");
            if (invmap != null) {
                for(MapTypeState mts : w.mapstate) {
                    List<List<String>> v = invmap.getList(mts.type.getPrefix());
                    if (v != null) {
                        mts.restoreZoomOut(v);
                    }
                }
            }
            /* Get saved render job, if any */
            ConfigurationNode job = cn.getNode("job");
            if((job != null) && (active_renders.get(wname) == null)) {
                try {
                    FullWorldRenderState j = new FullWorldRenderState(job);
                    active_renders.put(wname, j);
                    if(did_start)   /* Past initial start */
                        scheduleDelayedJob(j, 5000);
                    Log.info(j.rendertype + " for world '" + wname + "' restored");
                } catch (Exception x) {
                    Log.info("Unable to restore render job for world '" + wname + "' - map configuration changed");
                }
            }
        }
    }
    
    public boolean isRenderJobActive(String wname) {
        return active_renders.containsKey(wname);
    }

    private void savePending(DynmapWorld w, boolean keepQueue) {
        List<MapTile> mt = tileQueue.popAll();
        File f = new File(core.getDataFolder(), w.getName() + ".pending");
        ConfigurationNode saved = new ConfigurationNode();
        ArrayList<ConfigurationNode> savedtiles = new ArrayList<ConfigurationNode>();
        for(MapTile tile : mt) {
            if(tile.getDynmapWorld() != w) {
                tileQueue.push(tile);
                continue;
            }
            ConfigurationNode tilenode = tile.saveTile();
            if(tilenode != null) {
                savedtiles.add(tilenode);
            }
            if (keepQueue) {
                tileQueue.push(tile);
            }
        }
        int cnt = savedtiles.size();
        if(cnt > 0) { /* Something to save? */
            saved.put("tiles", savedtiles);
        }
        /* Save invalidated tiles */
        HashMap<String, Object> invalid = new HashMap<String,Object>();
        for(MapTypeState mts : w.mapstate) {
            invalid.put(mts.type.getPrefix(), mts.save());
            cnt += mts.getInvCount();
        }
        if(cnt > 0) {
            saved.put("invalid", invalid);
            if (!keepQueue) {
                Log.info("Saved " + cnt + " pending tile renders in world '" + w.getName() + "'");
            }
        }
        /* Save invalidated zoom out tiles pending */
        HashMap<String, List<List<String>>> invzooms = new HashMap<String,List<List<String>>>();
        for(MapTypeState mts : w.mapstate) {
            List<List<String>> szo = mts.saveZoomOut();
            if (szo != null) {
                invzooms.put(mts.type.getPrefix(), szo);
            }
        }
        if (!invzooms.isEmpty()) {
            saved.put("invZoomOut", invzooms);
        }
        
        FullWorldRenderState job = active_renders.get(w.getName());
        if(job != null) {
            saved.put("job", job.saveState());
            if (!keepQueue) {
                active_renders.remove(w.getName());
                job.shutdownRender();
                Log.info(job.rendertype + " job saved for world '" + w.getName() + "'");
            }
        }
        if (saved.isEmpty()) {
            f.delete();
        }
        else {
            saved.save(f);
        }
    }

    public void touch(String wname, int x, int y, int z, String reason) {
        TouchEvent evt = new TouchEvent();
        evt.world = wname;
        evt.x = x;
        evt.y = y;
        evt.z = z;
        evt.reason = reason;
        touch_events.putIfAbsent(evt, reason);
    }

    public void touchVolume(String wname, int minx, int miny, int minz, int maxx, int maxy, int maxz, String reason) {
        TouchVolumeEvent evt = new TouchVolumeEvent();
        evt.world = wname;
        evt.xmin = minx;
        evt.xmax = maxx;
        evt.ymin = miny;
        evt.ymax = maxy;
        evt.zmin = minz;
        evt.zmax = maxz;
        evt.reason = reason;
        synchronized(touch_lock) {
            touch_volume_events.add(evt);
        }
    }

//    public boolean invalidateTile(MapTile tile) {
//        return tileQueue.push(tile);
//    }

    public static boolean scheduleDelayedJob(Runnable job, long delay_in_msec) {
        if((mapman != null) && (mapman.render_pool != null)) {
            if(delay_in_msec > 0)
                mapman.render_pool.schedule(job, delay_in_msec, TimeUnit.MILLISECONDS);
            else
                mapman.render_pool.execute(job);
            return true;
        }
        else
            return false;
    }
                                                                       
    public void startRendering() {
        render_pool = new DynmapScheduledThreadPoolExecutor();
        tileQueue.start();
        scheduleDelayedJob(new DoZoomOutProcessing(), 60000);
        scheduleDelayedJob(new CheckWorldTimes(), 5000);
        scheduleDelayedJob(new DoTouchProcessing(), 1000);
        // If enabled, start enter/exit processing
        if (enterexitperiod > 0) {
        	Log.info("Starting enter/exit processing");
        	scheduleDelayedJob(new DoUserMoveProcessing(), enterexitperiod);
        }
        /* Resume pending jobs */
        for(FullWorldRenderState job : active_renders.values()) {
            scheduleDelayedJob(job, 5000);
            Log.info("Resumed render starting on world '" + job.world.getName() + "'...");
        }
        did_start = true;
    }

    public void stopRendering() {
        // Stop the tile queue
        tileQueue.stop();
        /* Tell all worlds to cancel any zoom out processing */
        for(DynmapWorld w: worlds)
            w.cancelZoomOutFreshen();
        /* Unload all worlds, and save any pending */
        for(DynmapWorld w : worlds) {
            if(w.isLoaded()) {
                w.setWorldUnloaded();
                if(saverestorepending) {
                    savePending(w, false);
                }
            }
        }
        // Shutdown render pool
        render_pool.shutdown();
        try {
            render_pool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ix) {
        }
        mapman = null;
        hdmapman = null;
        did_start = false;
    }

    public void pushUpdate(Client.Update update) {
        int sz = worlds.size();
        for(int i = 0; i < sz; i++) {
            worlds.get(i).updates.pushUpdate(update);
        }
    }

    public void pushUpdate(DynmapWorld world, Client.Update update) {
        pushUpdate(world.getName(), update);
    }

    public void pushUpdate(String worldName, Client.Update update) {
        DynmapWorld world = getWorld(worldName);
        if(world != null)
            world.updates.pushUpdate(update);
    }

    public Client.Update[] getWorldUpdates(String worldName, long since) {
        DynmapWorld world = getWorld(worldName);
        if (world == null)
            return new Client.Update[0];
        return world.updates.getUpdatedObjects(since);
    }

    /**
     *  Update map tile statistics
     * @param tile - map tile
     * @param prefix - map prefix
     * @param rendered - true if tile rendered
     * @param updated - true if tile updated
     * @param transparent - true if tile transparent
     */
    public void updateStatistics(MapTile tile, String prefix, boolean rendered, boolean updated, boolean transparent) {
        synchronized(lock) {
            String k = tile.getDynmapWorld().getName() + "." + prefix;
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
     * @param sender - command sender
     * @param prefix - map ID prefix for stats to print
     */
    public void printStats(DynmapCommandSender sender, String prefix) {
        sender.sendMessage("Tile Render Statistics:");
        MapStats tot = new MapStats();
        int invcnt = 0;
        for(DynmapWorld dw : this.worlds) {
            for(MapTypeState mts : dw.mapstate) {
                invcnt += mts.getInvCount();
            }
        }
        synchronized(lock) {
            for(String k: new TreeSet<String>(mapstats.keySet())) {
                if((prefix != null) && !k.startsWith(prefix))
                    continue;
                MapStats ms = mapstats.get(k);
                sender.sendMessage(String.format("  %s: processed=%d, rendered=%d, updated=%d, transparent=%d",
                        k, ms.loggedcnt, ms.renderedcnt, ms.updatedcnt, ms.transparentcnt));
                tot.loggedcnt += ms.loggedcnt;
                tot.renderedcnt += ms.renderedcnt;
                tot.updatedcnt += ms.updatedcnt;
                tot.transparentcnt += ms.transparentcnt;
            }
        }
        sender.sendMessage(String.format("  TOTALS: processed=%d, rendered=%d, updated=%d, transparent=%d",
                tot.loggedcnt, tot.renderedcnt, tot.updatedcnt, tot.transparentcnt));
        sender.sendMessage(String.format("  Triggered update queue size: %d + %d", tileQueue.size(), invcnt));
        String act = "";
        for(String wn : active_renders.keySet())
        	act += wn + " ";
        sender.sendMessage(String.format("  Active render jobs: %s", act));
        /* Chunk load stats */
        sender.sendMessage("Chunk Loading Statistics:");
        sender.sendMessage(String.format("  Cache hit rate: %.2f%%", core.getServer().getCacheHitRate()));
        for (MapChunkCache.ChunkStats cs : MapChunkCache.ChunkStats.values()) {
            int cnt = chunks_read[cs.ordinal()].get();
            if (cnt == 0) cnt = 1;
            long ts = chunks_read_times[cs.ordinal()].get();
            sender.sendMessage(String.format("  Chunks processed: %s: count=%d, %.2f msec/chunk", cs.getLabel(), cnt, 0.000001 * (ts / cnt)));
        }
    }
    /**
     * Print trigger statistics command
     * @param sender - command sender
     */
    public void printTriggerStats(DynmapCommandSender sender) {
        sender.sendMessage("Render Trigger Statistics:");
        synchronized(lock) {
            for(String k: new TreeSet<String>(trigstats.keySet())) {
                TriggerStats ts = trigstats.get(k);
                sender.sendMessage("  " + k + ": calls=" + ts.callsmade + ", calls-adding-tiles=" + ts.callswithtiles + ", tiles-added=" + ts.tilesqueued);
            }
        }
    }

    /**
     * Reset statistics
     * @param sender - command sender
     * @param prefix - prefix of map IDs to be reset
     */
    public void resetStats(DynmapCommandSender sender, String prefix) {
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
            for(String k : trigstats.keySet()) {
                TriggerStats ts = trigstats.get(k);
                ts.callsmade = 0;
                ts.callswithtiles = 0;
                ts.tilesqueued = 0;
            }
            chunk_caches_created.set(0);
            for (int i = 0; i < chunks_read.length; i++) {
                chunks_read[i].set(0);
                chunks_read_times[i].set(0);
            }
        }
        core.getServer().resetCacheStats();
        sender.sendMessage("Tile Render Statistics reset");
    }    

    public boolean getSmoothLighting() {
        return core.smoothlighting;
    }
    
    public boolean getBetterGrass() {
        return core.bettergrass;
    }
    
    public boolean getHideOres() {
        return hideores;
    }
    /* Map block ID to aliased ID - used to hide ores */
    public String getBlockAlias(String blockname) {
        String v = blockalias.get(blockname);
        if (v == null) {
            v = blockname;
        }
        return v;
    }
    /* Get names of aliased blocks */
    public Set<String> getAliasedBlocks() {
        return blockalias.keySet();
    }
    /* Set block ID alias */
    public void setBlockAlias(String blockname, String newblockname) {
        if (blockname.indexOf(':') < 0)
            blockname = "minecraft:" + blockname;
        if (newblockname.indexOf(':') < 0)
            newblockname = "minecraft:" + newblockname;
        blockalias.put(blockname, newblockname);
    }
    /*
     * Pause full/radius render processing
     * @param dopause - true to pause, false to unpause
     */
    void setPauseFullRadiusRenders(boolean dopause) {
        if(dopause != pausefullrenders) {
            pausefullrenders = dopause;
            Log.info("Full/radius render pause set to " + dopause);
        }
    }
    /*
     * Test if full renders are paused
     */
    boolean getPauseFullRadiusRenders() {
        return pausefullrenders;
    }
    /*
     * Pause update render processing
     * @param dopause - true to pause, false to unpause
     */
    void setPauseUpdateRenders(boolean dopause) {
        if(dopause != pauseupdaterenders) {
            pauseupdaterenders = dopause;
            Log.info("Update render pause set to " + dopause);
        }
    }
    /*
     * Test if update renders are paused
     */
    boolean getPauseUpdateRenders() {
        return pauseupdaterenders;
    }
    
    /* Connect any jobs tied to this player back to the player (resumes output to player) */
    void connectTasksToPlayer(DynmapPlayer p) {
        String pn = p.getName();
        for(FullWorldRenderState job : active_renders.values()) {
            if(pn.equals(job.player)) {
                job.sender = p;
            }
        }
    }
    
    /**
     * Process touch events
     */
    private void processTouchEvents() {
        ArrayList<TouchEvent> te = null;
        ArrayList<TouchVolumeEvent> tve = null;

        if(touch_events.isEmpty() == false) {
            te = new ArrayList<TouchEvent>(touch_events.keySet());
            for(int i = 0; i < te.size(); i++) {
                touch_events.remove(te.get(i));
            }
        }

        synchronized(touch_lock) {
            if(touch_volume_events.isEmpty() == false) {
                tve = new ArrayList<TouchVolumeEvent>(touch_volume_events);
                touch_volume_events.clear();
            }
        }
        DynmapWorld world = null;
        String wname = "";

        /* If any touch events, process them */
        if(te != null) {
            for(TouchEvent evt : te) {
                int invalidates = 0;
                /* If different world, look it up */
                if(evt.world.equals(wname) == false) {
                    wname = evt.world;
                    world = getWorld(wname);
                }
                if(world == null) continue;
                for (MapTypeState mts : world.mapstate) {
                    List<TileFlags.TileCoord> tiles = mts.type.getTileCoords(world, evt.x, evt.y, evt.z);
                    invalidates += mts.invalidateTiles(tiles);
                }
                if(evt.reason != null) {
                    synchronized(lock) {
                        TriggerStats ts = trigstats.get(evt.reason);
                        if(ts == null) {
                            ts = new TriggerStats();
                            trigstats.put(evt.reason, ts);
                        }
                        ts.callsmade++;
                        if(invalidates > 0) {
                            ts.callswithtiles++;
                            ts.tilesqueued += invalidates;
                        }
                    }
                }
            }
            te.clear(); /* Clean up set */
        }

        /* If any volume touches */
        if(tve != null) {
            for(TouchVolumeEvent evt : tve) {
                /* If different world, look it up */
                if(evt.world.equals(wname) == false) {
                    wname = evt.world;
                    world = getWorld(wname);
                }
                if(world == null) continue;
                int invalidates = 0;
                for (MapTypeState mts : world.mapstate) {
                    List<TileFlags.TileCoord> tiles = mts.type.getTileCoords(world, evt.xmin, evt.ymin, evt.zmin, evt.xmax, evt.ymax, evt.zmax);
                    invalidates += mts.invalidateTiles(tiles);
                }
                if(evt.reason != null) {
                    synchronized(lock) {
                        TriggerStats ts = trigstats.get(evt.reason);
                        if(ts == null) {
                            ts = new TriggerStats();
                            trigstats.put(evt.reason, ts);
                        }
                        ts.callsmade++;
                        if(invalidates > 0) {
                            ts.callswithtiles++;
                            ts.tilesqueued += invalidates;
                        }
                    }
                }
            }
            /* Clean up */
            tve.clear();
        }
    }
    
    public int getMaxChunkLoadsPerTick() {
        return max_chunk_loads_per_tick;
    }
    
    public void updateTPS(double tps) {
        // Pause if needed for update renders
        tpspauseupdaterenders = (tps < tpslimit_updaterenders);
        // Pause if needed for fullrenders
        tpspausefullrenders = (tps < tpslimit_fullrenders);
        // Pause if needed for zoom out
        tpspausezoomout = (tps < tpslimit_zoomout);
    }
    
    public boolean getTPSFullRenderPause() {
        return tpspausefullrenders;
    }
    public boolean getTPSUpdateRenderPause() {
        return tpspauseupdaterenders;
    }
    public boolean getTPSZoomOutPause() {
        return tpspausezoomout;
    }
    public void setJobsQuiet(DynmapCommandSender sender) {
        DynmapPlayer player = null;
        if (sender instanceof DynmapPlayer) {
            player = (DynmapPlayer) sender;
        }
        synchronized (lock) {
            for (FullWorldRenderState job : active_renders.values()) {
                if (job.sender instanceof DynmapPlayer) {
                    DynmapPlayer js = (DynmapPlayer) job.sender;
                    if ((player != null) && (player.getName().equals(js.getName()))) {
                        job.quiet = true;
                    }
                }
                else if (player == null) {  // If both are console
                    job.quiet = true;
                }
            }
        }
    }
    public boolean useBrightnessTable() {
        return useBrightnessTable;
    }
}
