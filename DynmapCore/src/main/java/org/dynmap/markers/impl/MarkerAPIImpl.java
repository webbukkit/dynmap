package org.dynmap.markers.impl;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.Event;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.Client;
import org.dynmap.Client.ComponentMessage;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.hdmap.HDPerspective;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.CircleMarker;
import org.dynmap.markers.EnterExitMarker;
import org.dynmap.markers.EnterExitMarker.EnterExitText;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerDescription;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerIcon.MarkerSize;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PlayerSet;
import org.dynmap.markers.PolyLineMarker;
import org.dynmap.utils.BufferOutputStream;
import org.dynmap.web.Json;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation class for MarkerAPI - should not be called directly
 */
public class MarkerAPIImpl implements MarkerAPI, Event.Listener<DynmapWorld> {
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private File markerpersist;
    private File markerpersist_old;
    private File markerdir; /* Local store for markers (internal) */
    private HashMap<String, MarkerIconImpl> markericons = new HashMap<String, MarkerIconImpl>();
    private ConcurrentHashMap<String, MarkerSetImpl> markersets = new ConcurrentHashMap<String, MarkerSetImpl>();
    private HashMap<String, List<DynmapLocation>> pointaccum = new HashMap<String, List<DynmapLocation>>();
    private HashMap<String, PlayerSetImpl> playersets = new HashMap<String, PlayerSetImpl>();
    private DynmapCore core;
    static MarkerAPIImpl api;

    /* Built-in icons */
    private static final String[] builtin_icons = {
        "anchor", "bank", "basket", "bed", "beer", "bighouse", "blueflag", "bomb", "bookshelf", "bricks", "bronzemedal", "bronzestar",
        "building", "cake", "camera", "cart", "caution", "chest", "church", "coins", "comment", "compass", "construction",
        "cross", "cup", "cutlery", "default", "diamond", "dog", "door", "down", "drink", "exclamation", "factory",
        "fire", "flower", "gear", "goldmedal", "goldstar", "greenflag", "hammer", "heart", "house", "key", "king",
        "left", "lightbulb", "lighthouse", "lock", "minecart", "orangeflag", "pin", "pinkflag", "pirateflag", "pointdown", "pointleft",
        "pointright", "pointup", "portal", "purpleflag", "queen", "redflag", "right", "ruby", "scales", "skull", "shield", "sign",
        "silvermedal", "silverstar", "star", "sun", "temple", "theater", "tornado", "tower", "tree", "truck", "up",
        "walk", "warning", "world", "wrench", "yellowflag", "offlineuser"
    };

    /* Component messages for client updates */
    public static class MarkerComponentMessage extends ComponentMessage {
        public String ctype = "markers";
    }
    
    public static class MarkerUpdated extends MarkerComponentMessage {
        public String msg;
        public double x, y, z;
        public String id;
        public String label;
        public String icon;
        public String set;
        public boolean markup;
        public String desc;
        public String dim;
        public int minzoom;
        public int maxzoom;
        
        public MarkerUpdated(Marker m, boolean deleted) {
            this.id = m.getMarkerID();
            this.label = m.getLabel();
            this.x = m.getX();
            this.y = m.getY();
            this.z = m.getZ();
            this.set = m.getMarkerSet().getMarkerSetID();
            this.icon = m.getMarkerIcon().getMarkerIconID();
            this.markup = m.isLabelMarkup();
            this.desc = m.getDescription();
            this.dim = m.getMarkerIcon().getMarkerIconSize().getSize();
            this.minzoom = m.getMinZoom();
            this.maxzoom = m.getMaxZoom();
            if(deleted) 
                msg = "markerdeleted";
            else
                msg = "markerupdated";
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof MarkerUpdated) {
                MarkerUpdated m = (MarkerUpdated)o;
                return m.id.equals(id) && m.set.equals(set);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return id.hashCode() ^ set.hashCode();
        }

    }

    public static class AreaMarkerUpdated extends MarkerComponentMessage {
        public String msg;
        public double ytop, ybottom;
        public double[] x;
        public double[] z;
        public int weight;
        public double opacity;
        public String color;
        public double fillopacity;
        public String fillcolor;
        public String id;
        public String label;
        public String set;
        public String desc;
        public int minzoom;
        public int maxzoom;
        public boolean markup;
        
        public AreaMarkerUpdated(AreaMarker m, boolean deleted) {
            this.id = m.getMarkerID();
            this.label = m.getLabel();
            this.ytop = m.getTopY();
            this.ybottom = m.getBottomY();
            int cnt = m.getCornerCount();
            x = new double[cnt];
            z = new double[cnt];
            for(int i = 0; i < cnt; i++) {
                x[i] = m.getCornerX(i);
                z[i] = m.getCornerZ(i);
            }
            color = String.format("#%06X", m.getLineColor());
            weight = m.getLineWeight();
            opacity = m.getLineOpacity();
            fillcolor = String.format("#%06X", m.getFillColor());
            fillopacity = m.getFillOpacity();
            desc = m.getDescription();
            this.minzoom = m.getMinZoom();
            this.maxzoom = m.getMaxZoom();
            this.markup = m.isLabelMarkup();

            this.set = m.getMarkerSet().getMarkerSetID();
            if(deleted) 
                msg = "areadeleted";
            else
                msg = "areaupdated";
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof AreaMarkerUpdated) {
                AreaMarkerUpdated m = (AreaMarkerUpdated)o;
                return m.id.equals(id) && m.set.equals(set);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return id.hashCode() ^ set.hashCode();
        }
    }

    public static class PolyLineMarkerUpdated extends MarkerComponentMessage {
        public String msg;
        public double[] x;
        public double[] y;
        public double[] z;
        public int weight;
        public double opacity;
        public String color;
        public String id;
        public String label;
        public String set;
        public String desc;
        public int minzoom;
        public int maxzoom;
        
        public PolyLineMarkerUpdated(PolyLineMarker m, boolean deleted) {
            this.id = m.getMarkerID();
            this.label = m.getLabel();
            int cnt = m.getCornerCount();
            x = new double[cnt];
            y = new double[cnt];
            z = new double[cnt];
            for(int i = 0; i < cnt; i++) {
                x[i] = m.getCornerX(i);
                y[i] = m.getCornerY(i);
                z[i] = m.getCornerZ(i);
            }
            color = String.format("#%06X", m.getLineColor());
            weight = m.getLineWeight();
            opacity = m.getLineOpacity();
            desc = m.getDescription();
            this.minzoom = m.getMinZoom();
            this.maxzoom = m.getMaxZoom();

            this.set = m.getMarkerSet().getMarkerSetID();
            if(deleted) 
                msg = "linedeleted";
            else
                msg = "lineupdated";
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof PolyLineMarkerUpdated) {
                PolyLineMarkerUpdated m = (PolyLineMarkerUpdated)o;
                return m.id.equals(id) && m.set.equals(set);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return id.hashCode() ^ set.hashCode();
        }
    }

    public static class CircleMarkerUpdated extends MarkerComponentMessage {
        public String msg;
        public double x;
        public double y;
        public double z;
        public double xr;
        public double zr;
        public int weight;
        public double opacity;
        public String color;
        public double fillopacity;
        public String fillcolor;
        public String id;
        public String label;
        public String set;
        public String desc;
        public int minzoom;
        public int maxzoom;
        
        public CircleMarkerUpdated(CircleMarker m, boolean deleted) {
            this.id = m.getMarkerID();
            this.label = m.getLabel();
            this.x = m.getCenterX();
            this.y = m.getCenterY();
            this.z = m.getCenterZ();
            this.xr = m.getRadiusX();
            this.zr = m.getRadiusZ();
            color = String.format("#%06X", m.getLineColor());
            weight = m.getLineWeight();
            opacity = m.getLineOpacity();
            fillcolor = String.format("#%06X", m.getFillColor());
            fillopacity = m.getFillOpacity();
            desc = m.getDescription();
            this.minzoom = m.getMinZoom();
            this.maxzoom = m.getMaxZoom();

            this.set = m.getMarkerSet().getMarkerSetID();
            if(deleted) 
                msg = "circledeleted";
            else
                msg = "circleupdated";
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof CircleMarkerUpdated) {
                CircleMarkerUpdated m = (CircleMarkerUpdated)o;
                return m.id.equals(id) && m.set.equals(set);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return id.hashCode() ^ set.hashCode();
        }
    }

    public static class MarkerSetUpdated extends MarkerComponentMessage {
        public String msg;
        public String id;
        public String label;
        public int layerprio;
        public int minzoom;
        public int maxzoom;
        public Boolean showlabels;
        public MarkerSetUpdated(MarkerSet markerset, boolean deleted) {
            this.id = markerset.getMarkerSetID();
            this.label = markerset.getMarkerSetLabel();
            this.layerprio = markerset.getLayerPriority();
            this.minzoom = markerset.getMinZoom();
            this.maxzoom = markerset.getMaxZoom();
            this.showlabels = markerset.getLabelShow();
            if(deleted)
                msg = "setdeleted";
            else
                msg = "setupdated";
        }
        @Override
        public boolean equals(Object o) {
            if(o instanceof MarkerSetUpdated) {
                MarkerSetUpdated m = (MarkerSetUpdated)o;
                return m.id.equals(id);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
    
    private boolean stop = false;
    private Set<String> dirty_worlds = new HashSet<String>();
    private boolean dirty_markers = false;
    
    private class DoFileWrites implements Runnable {
        public void run() {
            if(stop)
                return;
            lock.readLock().lock();
            try {
                /* Write markers first - drives JSON updates too */
                if(dirty_markers) {
                    doSaveMarkers();
                    dirty_markers = false;
                }
                /* Process any dirty worlds */
                if(!dirty_worlds.isEmpty()) {
                    for(String world : dirty_worlds) {
                        writeMarkersFile(world);
                    }
                    dirty_worlds.clear();
                }
            } finally {
                lock.readLock().unlock();
            }
            core.getServer().scheduleServerTask(this, 20);
        }
    }

    /**
     * Singleton initializer
     * @param core - core object
     * @return API object
     */
    public static MarkerAPIImpl initializeMarkerAPI(DynmapCore core) {
        if(api != null) {
            api.cleanup(core);
        }
        api = new MarkerAPIImpl();
        api.core = core;
        /* Initialize persistence file name */
        api.markerpersist = new File(core.getDataFolder(), "markers.yml");
        api.markerpersist_old = new File(core.getDataFolder(), "markers.yml.old");
        /* Fill in default icons and sets, if needed */
        for(int i = 0; i < builtin_icons.length; i++) {
            String id = builtin_icons[i];
            api.createBuiltinMarkerIcon(id, id);
        }
        /* Load persistence */
        api.loadMarkers();
        /* Initialize default marker set, if needed */
        MarkerSet set = api.getMarkerSet(MarkerSet.DEFAULT);
        if(set == null) {
            set = api.createMarkerSet(MarkerSet.DEFAULT, "Markers", null, true);
        }
        
        /* Build paths for markers */
        api.markerdir = new File(core.getDataFolder(), "markers");
        if(api.markerdir.isDirectory() == false) {
            if(api.markerdir.mkdirs() == false) {   /* Create directory if needed */
                Log.severe("Error creating markers directory - " + api.markerdir.getPath());
            }
        }        
        return api;
    }
    /**
     * Singleton initializer complete (after rendder pool available
     * @param core - core object
     * @return API object
     */
    public static void completeInitializeMarkerAPI(MarkerAPIImpl api) {
        MapManager.scheduleDelayedJob(new Runnable() {
        	public void run() {
                /* Now publish marker files to the tiles directory */
                for(MarkerIcon ico : api.getMarkerIcons()) {
                    api.publishMarkerIcon(ico);
                }
                /* Freshen files */
                api.freshenMarkerFiles();
                /* Add listener so we update marker files for other worlds as they become active */
                api.core.events.addListener("worldactivated", api);

                api.scheduleWriteJob(); /* Start write job */        		

        		Log.info("Finish marker initialization");
    		}
        }, 0);
    }
    
    public void scheduleWriteJob() {
        core.getServer().scheduleServerTask(new DoFileWrites(), 20);
    }
    
    /**
     * Cleanup
     * @param plugin - core object
     */
    public void cleanup(DynmapCore plugin) {
        plugin.events.removeListener("worldactivated", api);

        stop = true;
        lock.readLock().lock();
        try {
            if(dirty_markers) {
                doSaveMarkers();
                dirty_markers = false;
            }
        } finally {
            lock.readLock().unlock();
        }
        lock.writeLock().lock();
        try {
            for(MarkerIconImpl icn : markericons.values())
                icn.cleanup();
            markericons.clear();
            for(MarkerSetImpl set : markersets.values())
                set.cleanup();
            markersets.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private MarkerIcon createBuiltinMarkerIcon(String id, String label) {
        if(markericons.containsKey(id)) return null;    /* Exists? */
        MarkerIconImpl ico = new MarkerIconImpl(id, label, true);
        markericons.put(id, ico);   /* Add to set */
        return ico;
    }

    void publishMarkerIcon(MarkerIcon ico) {
        byte[] buf = new byte[512];
        InputStream in = null;
        File infile = new File(markerdir, ico.getMarkerIconID() + ".png");  /* Get source file name */
        BufferedImage im = null;
                
        if(ico.isBuiltIn()) {
            in = getClass().getResourceAsStream("/markers/" + ico.getMarkerIconID() + ".png");
        }
        else if(infile.canRead()) {  /* If it exists and is readable */
            try {
                im = ImageIO.read(infile);
            } catch (IOException e) {
            } catch (IndexOutOfBoundsException e) {
            }
            if(im != null) {
                MarkerIconImpl icon = (MarkerIconImpl)ico;
                int w = im.getWidth();  /* Get width */
                if(w <= 8) {    /* Small size? */
                    icon.setMarkerIconSize(MarkerSize.MARKER_8x8);
                }
                else if(w > 16) {
                    icon.setMarkerIconSize(MarkerSize.MARKER_32x32);
                }
                else {
                    icon.setMarkerIconSize(MarkerSize.MARKER_16x16);
                }
                im.flush();
            }

            try {
                in = new FileInputStream(infile);   
            } catch (IOException iox) {
                Log.severe("Error opening marker " + infile.getPath() + " - " + iox);
            }
        }
        if(in == null) {    /* Not found, use default marker */
            in = getClass().getResourceAsStream("/markers/marker.png");
            if(in == null) {
                return;
            }
        }
        /* Copy to destination */
        try {
            BufferOutputStream bos = new BufferOutputStream();
            int len;
            while((len = in.read(buf)) > 0) {
               bos.write(buf, 0, len); 
            }
            core.getDefaultMapStorage().setMarkerImage(ico.getMarkerIconID(), bos);
        } catch (IOException iox) {
            Log.severe("Error writing marker to tilespath");
        } finally {
            if(in != null) try { in.close(); } catch (IOException x){}
        }
    }
    
    @Override
    public Set<MarkerSet> getMarkerSets() {
        return new HashSet<MarkerSet>(markersets.values());
    }

    @Override
    public MarkerSet getMarkerSet(String id) {
        return markersets.get(id);
    }

    @Override
    public MarkerSet createMarkerSet(String id, String lbl, Set<MarkerIcon> iconlimit, boolean persistent) {
        if(markersets.containsKey(id)) return null; /* Exists? */
        
        MarkerSetImpl set = new MarkerSetImpl(id, lbl, iconlimit, persistent);

        markersets.put(id, set);    /* Add to list */
        if(persistent) {
            saveMarkers();
        }
        markerSetUpdated(set, MarkerUpdate.CREATED); /* Notify update */
        
        return set;
    }

    @Override
    public Set<MarkerIcon> getMarkerIcons() {
        return new HashSet<MarkerIcon>(markericons.values());
    }

    @Override
    public MarkerIcon getMarkerIcon(String id) {
        return markericons.get(id);
    }

    boolean loadMarkerIconStream(String id, InputStream in) {
        /* Copy icon resource into marker directory */
        File f = new File(markerdir, id + ".png");
        FileOutputStream fos = null;
        try {
            byte[] buf = new byte[512];
            int len;
            fos = new FileOutputStream(f);
            while((len = in.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
        } catch (IOException iox) {
            Log.severe("Error copying marker - " + f.getPath());
            return false;
        } finally {
            if(fos != null) try { fos.close(); } catch (IOException x) {}
        }
        return true;
    }
    @Override
    public MarkerIcon createMarkerIcon(String id, String label, InputStream marker_png) {
        if(markericons.containsKey(id)) return null;    /* Exists? */
        MarkerIconImpl ico = new MarkerIconImpl(id, label, false);
        /* Copy icon resource into marker directory */
        if(!loadMarkerIconStream(id, marker_png))
            return null;
        markericons.put(id, ico);   /* Add to set */

        /* Publish the marker */
        publishMarkerIcon(ico);
        
        saveMarkers();  /* Save results */
        
        return ico;
    }

    static MarkerIconImpl getMarkerIconImpl(String id) {
        if(api != null) {
            return api.markericons.get(id);
        }
        return null;
    }

    @Override
    public Set<PlayerSet> getPlayerSets() {
        return new HashSet<PlayerSet>(playersets.values());
    }

    @Override
    public PlayerSet getPlayerSet(String id) {
        return playersets.get(id);
    }

    @Override
    public PlayerSet createPlayerSet(String id, boolean symmetric, Set<String> players, boolean persistent) {
        if(playersets.containsKey(id)) return null; /* Exists? */
        
        PlayerSetImpl set = new PlayerSetImpl(id, symmetric, players, persistent);

        playersets.put(id, set);    /* Add to list */
        if(persistent) {
            saveMarkers();
        }
        playerSetUpdated(set, MarkerUpdate.CREATED); /* Notify update */
        
        return set;
    }

    /**
     * Save persistence for markers
     */
    static void saveMarkers() {
        if(api != null) {
            api.dirty_markers = true;
        }
    }
    
    private void doSaveMarkers() {
        if(api != null) {
            final ConfigurationNode conf = new ConfigurationNode(api.markerpersist);  /* Make configuration object */
            /* First, save icon definitions */
            HashMap<String, Object> icons = new HashMap<String,Object>();
            for(String id : api.markericons.keySet()) {
                MarkerIconImpl ico = api.markericons.get(id);
                Map<String,Object> dat = ico.getPersistentData();
                if(dat != null) {
                    icons.put(id, dat);
                }
            }
            conf.put("icons", icons);
            /* Then, save persistent sets */
            HashMap<String, Object> sets = new HashMap<String, Object>();
            for(String id : api.markersets.keySet()) {
                MarkerSetImpl set = api.markersets.get(id);
                if(set.isMarkerSetPersistent()) {
                    Map<String, Object> dat = set.getPersistentData();
                    if(dat != null) {
                        sets.put(id, dat);
                    }
                }
            }
            conf.put("sets", sets);
            /* Then, save persistent player sets */
            HashMap<String, Object> psets = new HashMap<String, Object>();
            for(String id : api.playersets.keySet()) {
                PlayerSetImpl set = api.playersets.get(id);
                if(set.isPersistentSet()) {
                    Map<String, Object> dat = set.getPersistentData();
                    if(dat != null) {
                        psets.put(id, dat);
                    }
                }
            }
            conf.put("playersets", psets);
            
            MapManager.scheduleDelayedJob(new Runnable() {
                public void run() {
                    /* And shift old file file out */
                    if(api.markerpersist_old.exists()) api.markerpersist_old.delete();
                    if(api.markerpersist.exists()) api.markerpersist.renameTo(api.markerpersist_old);
                    /* And write it out */
                    if(!conf.save())
                        Log.severe("Error writing markers - " + api.markerpersist.getPath());
                }
            }, 0);
            /* Refresh JSON files */
            api.freshenMarkerFiles();
        }
    }

    private void freshenMarkerFiles() {
        if(MapManager.mapman != null) {
            for(DynmapWorld w : MapManager.mapman.worlds) {
                dirty_worlds.add(w.getName());
            }
        }
    }
    
    /**
     * Load persistence
     */
    private boolean loadMarkers() {        
        ConfigurationNode conf = new ConfigurationNode(api.markerpersist);  /* Make configuration object */
        conf.load();    /* Load persistence */
        lock.writeLock().lock();
        try {
            /* Get icons */
            ConfigurationNode icons = conf.getNode("icons");
            if(icons == null) return false;
            for(String id : icons.keySet()) {
                MarkerIconImpl ico = new MarkerIconImpl(id);
                if(ico.loadPersistentData(icons.getNode(id))) {
                    markericons.put(id, ico);
                }
            }
            /* Get marker sets */
            ConfigurationNode sets = conf.getNode("sets");
            if(sets != null) {
                for(String id: sets.keySet()) {
                    MarkerSetImpl set = new MarkerSetImpl(id);
                    if(set.loadPersistentData(sets.getNode(id))) {
                        markersets.put(id, set);
                    }
                }
            }
            /* Get player sets */
            ConfigurationNode psets = conf.getNode("playersets");
            if(psets != null) {
                for(String id: psets.keySet()) {
                    PlayerSetImpl set = new PlayerSetImpl(id);
                    if(set.loadPersistentData(sets.getNode(id))) {
                        playersets.put(id, set);
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
        
        return true;
    }
    
    enum MarkerUpdate { CREATED, UPDATED, DELETED };
    
    /**
     * Signal marker update
     * @param marker - updated marker
     * @param update - type of update
     */
    static void markerUpdated(MarkerImpl marker, MarkerUpdate update) {
        /* Freshen marker file for the world for this marker */
        if(api != null)
            api.dirty_worlds.add(marker.getNormalizedWorld());
        /* Enqueue client update */
        if(MapManager.mapman != null)
            MapManager.mapman.pushUpdate(marker.getNormalizedWorld(), new MarkerUpdated(marker, update == MarkerUpdate.DELETED));
    }
    /**
     * Signal area marker update
     * @param marker - updated marker
     * @param update - type of update
     */
    static void areaMarkerUpdated(AreaMarkerImpl marker, MarkerUpdate update) {
        /* Freshen marker file for the world for this marker */
        if(api != null)
            api.dirty_worlds.add(marker.getNormalizedWorld());
        /* Enqueue client update */
        if(MapManager.mapman != null)
            MapManager.mapman.pushUpdate(marker.getNormalizedWorld(), new AreaMarkerUpdated(marker, update == MarkerUpdate.DELETED));
    }
    /**
     * Signal poly-line marker update
     * @param marker - updated marker
     * @param update - type of update
     */
    static void polyLineMarkerUpdated(PolyLineMarkerImpl marker, MarkerUpdate update) {
        /* Freshen marker file for the world for this marker */
        if(api != null)
            api.dirty_worlds.add(marker.getNormalizedWorld());
        /* Enqueue client update */
        if(MapManager.mapman != null)
            MapManager.mapman.pushUpdate(marker.getNormalizedWorld(), new PolyLineMarkerUpdated(marker, update == MarkerUpdate.DELETED));
    }
    /**
     * Signal circle marker update
     * @param marker - updated marker
     * @param update - type of update
     */
    static void circleMarkerUpdated(CircleMarkerImpl marker, MarkerUpdate update) {
        /* Freshen marker file for the world for this marker */
        if(api != null)
            api.dirty_worlds.add(marker.getNormalizedWorld());
        /* Enqueue client update */
        if(MapManager.mapman != null)
            MapManager.mapman.pushUpdate(marker.getNormalizedWorld(), new CircleMarkerUpdated(marker, update == MarkerUpdate.DELETED));
    }
    /**
     * Signal marker set update
     * @param markerset - updated marker set
     * @param update - type of update
     */
    static void markerSetUpdated(MarkerSetImpl markerset, MarkerUpdate update) {
        /* Freshen all marker files */
        if(api != null)
            api.freshenMarkerFiles();
        /* Enqueue client update */
        if(MapManager.mapman != null)
            MapManager.mapman.pushUpdate(new MarkerSetUpdated(markerset, update == MarkerUpdate.DELETED));
    }
    /**
     * Signal player set update
     * @param playerset - updated player set
     * @param update - type of update
     */
    static void playerSetUpdated(PlayerSetImpl pset, MarkerUpdate update) {
        if(api != null)
            api.core.events.trigger("playersetupdated", null);
    }
    
    /**
     * Remove marker set
     */
    static void removeMarkerSet(MarkerSetImpl markerset) {
        if(api != null) {
            api.markersets.remove(markerset.getMarkerSetID());  /* Remove set from list */
            if(markerset.isMarkerSetPersistent()) {   /* If persistent */
                MarkerAPIImpl.saveMarkers();        /* Drive save */
            }
            markerSetUpdated(markerset, MarkerUpdate.DELETED); /* Signal delete of set */
        }
    }

    /**
     * Remove player set
     */
    static void removePlayerSet(PlayerSetImpl pset) {
        if(api != null) {
            api.playersets.remove(pset.getSetID());  /* Remove set from list */
            if(pset.isPersistentSet()) {   /* If persistent */
                MarkerAPIImpl.saveMarkers();        /* Drive save */
            }
            playerSetUpdated(pset, MarkerUpdate.DELETED); /* Signal delete of set */
        }
    }

    private static boolean processAreaArgs(DynmapCommandSender sender, AreaMarker marker, Map<String,String> parms) {
        String val = null;
        String val2 = null;
        try {
            double ytop = marker.getTopY();
            double ybottom = marker.getBottomY();
            int scolor = marker.getLineColor();
            int fcolor = marker.getFillColor();
            double sopacity = marker.getLineOpacity();
            double fopacity = marker.getFillOpacity();
            int sweight = marker.getLineWeight();
            boolean boost = marker.getBoostFlag();
            int minzoom = marker.getMinZoom();
            int maxzoom = marker.getMaxZoom();
            EnterExitText greet = marker.getGreetingText();
            EnterExitText farew = marker.getFarewellText();

            val = parms.get(ARG_STROKECOLOR);
            if(val != null)
                scolor = Integer.parseInt(val, 16);
            val = parms.get(ARG_FILLCOLOR);
            if(val != null)
                fcolor = Integer.parseInt(val, 16);
            val = parms.get(ARG_STROKEOPACITY);
            if(val != null)
                sopacity = Double.parseDouble(val);
            val = parms.get(ARG_FILLOPACITY);
            if(val != null)
                fopacity = Double.parseDouble(val);
            val = parms.get(ARG_STROKEWEIGHT);
            if(val != null)
                sweight = Integer.parseInt(val);
            val = parms.get(ARG_YTOP);
            if(val != null)
                ytop = Double.parseDouble(val);
            val = parms.get(ARG_YBOTTOM);
            if(val != null)
                ybottom = Double.parseDouble(val);
            val = parms.get(ARG_MINZOOM);
            if (val != null)
                minzoom = Integer.parseInt(val);
            val = parms.get(ARG_MAXZOOM);
            if (val != null)
                maxzoom = Integer.parseInt(val);
            val = parms.get(ARG_BOOST);
            if(val != null) {
                if(api.core.checkPlayerPermission(sender, "marker.boost")) {
                    boost = val.equals("true");
                }
                else {
                    sender.sendMessage("No permission to set boost flag");
                    return false;
                }
            }
            marker.setLineStyle(sweight, sopacity, scolor);
            marker.setFillStyle(fopacity, fcolor);
            if(ytop >= ybottom)
                marker.setRangeY(ytop, ybottom);
            else
                marker.setRangeY(ybottom, ytop);
            marker.setBoostFlag(boost);
            marker.setMinZoom(minzoom);
            marker.setMaxZoom(maxzoom);
            // Handle greeting
            val = parms.get(ARG_GREETING);
            val2 = parms.get(ARG_GREETINGSUB);
            if ((val != null) || (val2 != null)) {
            	String title = (val != null) ? ((val.length() > 0) ? val : null) : ((greet != null) ? greet.title : null);
            	String subtitle = (val2 != null) ? ((val2.length() > 0) ? val2 : null) : ((greet != null) ? greet.subtitle : null);
            	marker.setGreetingText(title, subtitle);
            }
            // Handle farewell
            val = parms.get(ARG_FAREWELL);
            val2 = parms.get(ARG_FAREWELLSUB);
            if ((val != null) || (val2 != null)) {
            	String title = (val != null) ? ((val.length() > 0) ? val : null) : ((farew != null) ? farew.title : null);
            	String subtitle = (val2 != null) ? ((val2.length() > 0) ? val2 : null) : ((farew != null) ? farew.subtitle : null);
            	marker.setFarewellText(title, subtitle);
            }
        } catch (NumberFormatException nfx) {
            sender.sendMessage("Invalid parameter format: " + val);
            return false;
        }
        return true;
    }

    private static boolean processPolyArgs(DynmapCommandSender sender, PolyLineMarker marker, Map<String,String> parms) {
        String val = null;
        try {
            int scolor = marker.getLineColor();
            double sopacity = marker.getLineOpacity();
            int sweight = marker.getLineWeight();
            int minzoom = marker.getMinZoom();
            int maxzoom = marker.getMaxZoom();

            val = parms.get(ARG_STROKECOLOR);
            if(val != null)
                scolor = Integer.parseInt(val, 16);
            val = parms.get(ARG_STROKEOPACITY);
            if(val != null)
                sopacity = Double.parseDouble(val);
            val = parms.get(ARG_STROKEWEIGHT);
            if(val != null)
                sweight = Integer.parseInt(val);
            val = parms.get(ARG_MINZOOM);
            if(val != null)
                minzoom = Integer.parseInt(val);
            val = parms.get(ARG_MAXZOOM);
            if(val != null)
                maxzoom = Integer.parseInt(val);
            marker.setLineStyle(sweight, sopacity, scolor);
            marker.setMinZoom(minzoom);
            marker.setMaxZoom(maxzoom);
        } catch (NumberFormatException nfx) {
            sender.sendMessage("Invalid parameter format: " + val);
            return false;
        }
        return true;
    }

    private static boolean processCircleArgs(DynmapCommandSender sender, CircleMarker marker, Map<String,String> parms) {
        String val = null, val2 = null;
        try {
            int scolor = marker.getLineColor();
            int fcolor = marker.getFillColor();
            double sopacity = marker.getLineOpacity();
            double fopacity = marker.getFillOpacity();
            int sweight = marker.getLineWeight();
            double xr = marker.getRadiusX();
            double zr = marker.getRadiusZ();
            double x = marker.getCenterX();
            double y = marker.getCenterY();
            double z = marker.getCenterZ();
            String world = marker.getWorld();
            boolean boost = marker.getBoostFlag();
            int minzoom = marker.getMinZoom();
            int maxzoom = marker.getMaxZoom();
            EnterExitText greet = marker.getGreetingText();
            EnterExitText farew = marker.getFarewellText();
            
            val = parms.get(ARG_STROKECOLOR);
            if(val != null)
                scolor = Integer.parseInt(val, 16);
            val = parms.get(ARG_FILLCOLOR);
            if(val != null)
                fcolor = Integer.parseInt(val, 16);
            val = parms.get(ARG_STROKEOPACITY);
            if(val != null)
                sopacity = Double.parseDouble(val);
            val = parms.get(ARG_FILLOPACITY);
            if(val != null)
                fopacity = Double.parseDouble(val);
            val = parms.get(ARG_STROKEWEIGHT);
            if(val != null)
                sweight = Integer.parseInt(val);
            val = parms.get(ARG_X);
            if(val != null)
                x = Double.parseDouble(val);
            val = parms.get(ARG_Y);
            if(val != null)
                y = Double.parseDouble(val);
            val = parms.get(ARG_Z);
            if(val != null)
                z = Double.parseDouble(val);
            val = parms.get(ARG_WORLD);
            if(val != null)
                world = val;
            val = parms.get(ARG_RADIUSX);
            if(val != null)
                xr = Double.parseDouble(val);
            val = parms.get(ARG_RADIUSZ);
            if(val != null)
                zr = Double.parseDouble(val);
            val = parms.get(ARG_RADIUS);
            if(val != null)
                xr = zr = Double.parseDouble(val);
            val = parms.get(ARG_BOOST);
            if(val != null) {
                if(api.core.checkPlayerPermission(sender, "marker.boost")) {
                    boost = val.equals("true");
                }
                else {
                    sender.sendMessage("No permission to set boost flag");
                    return false;
                }
            }
            val = parms.get(ARG_MINZOOM);
            if (val != null) {
                minzoom = Integer.parseInt(val);
            }
            val = parms.get(ARG_MAXZOOM);
            if (val != null) {
                maxzoom = Integer.parseInt(val);
            }
            marker.setCenter(world, x, y, z);
            marker.setLineStyle(sweight, sopacity, scolor);
            marker.setFillStyle(fopacity, fcolor);
            marker.setRadius(xr, zr);
            marker.setBoostFlag(boost);
            marker.setMinZoom(minzoom);
            marker.setMaxZoom(maxzoom);
            // Handle greeting
            val = parms.get(ARG_GREETING);
            val2 = parms.get(ARG_GREETINGSUB);
            if ((val != null) || (val2 != null)) {
            	String title = (val != null) ? ((val.length() > 0) ? val : null) : ((greet != null) ? greet.title : null);
            	String subtitle = (val2 != null) ? ((val2.length() > 0) ? val2 : null) : ((greet != null) ? greet.subtitle : null);
            	marker.setGreetingText(title, subtitle);
            }
            // Handle farewell
            val = parms.get(ARG_FAREWELL);
            val2 = parms.get(ARG_FAREWELLSUB);
            if ((val != null) || (val2 != null)) {
            	String title = (val != null) ? ((val.length() > 0) ? val : null) : ((farew != null) ? farew.title : null);
            	String subtitle = (val2 != null) ? ((val2.length() > 0) ? val2 : null) : ((farew != null) ? farew.subtitle : null);
            	marker.setFarewellText(title, subtitle);
            }
            
        } catch (NumberFormatException nfx) {
            sender.sendMessage("Invalid parameter format: " + val);
            return false;
        }
        return true;
    }

    private static final Set<String> commands = new HashSet<String>(Arrays.asList(new String[] {
        "add", "movehere", "update", "delete", "list", "icons", "addset", "updateset", "deleteset", "listsets", "addicon", "updateicon",
        "deleteicon", "addcorner", "clearcorners", "addarea", "listareas", "deletearea", "updatearea",
        "addline", "listlines", "deleteline", "updateline", "addcircle", "listcircles", "deletecircle", "updatecircle",
        "getdesc", "resetdesc", "appenddesc", "importdesc", "getlabel", "importlabel"
    }));
    private static final String ARG_LABEL = "label";
    private static final String ARG_MARKUP = "markup";
    private static final String ARG_ID = "id";
    private static final String ARG_TYPE = "type";
    private static final String ARG_NEWLABEL = "newlabel";
    private static final String ARG_FILE = "file";
    private static final String ARG_HIDE = "hide";
    private static final String ARG_ICON = "icon";
    private static final String ARG_DEFICON = "deficon";
    private static final String ARG_SET = "set";
    private static final String ARG_NEWSET = "newset";
    private static final String ARG_PRIO = "prio";
    private static final String ARG_MINZOOM = "minzoom";
    private static final String ARG_MAXZOOM = "maxzoom";
    private static final String ARG_STROKEWEIGHT = "weight";
    private static final String ARG_STROKECOLOR = "color";
    private static final String ARG_STROKEOPACITY = "opacity";
    private static final String ARG_FILLCOLOR = "fillcolor";
    private static final String ARG_FILLOPACITY = "fillopacity";
    private static final String ARG_YTOP = "ytop";
    private static final String ARG_YBOTTOM = "ybottom";
    private static final String ARG_RADIUSX = "radiusx";
    private static final String ARG_RADIUSZ = "radiusz";
    private static final String ARG_RADIUS = "radius";
    private static final String ARG_SHOWLABEL = "showlabels";
    private static final String ARG_X = "x";
    private static final String ARG_Y = "y";
    private static final String ARG_Z = "z";
    private static final String ARG_WORLD = "world";
    private static final String ARG_BOOST = "boost";
    private static final String ARG_DESC = "desc";
    private static final String ARG_GREETING = "greeting";
    private static final String ARG_GREETINGSUB = "greetingsub";
    private static final String ARG_FAREWELL = "farewell";
    private static final String ARG_FAREWELLSUB = "farewellsub";
    
    
    /* Parse argument strings : handle 'attrib:value' and quoted strings */
    private static Map<String,String> parseArgs(String[] args, DynmapCommandSender snd) {
        HashMap<String,String> rslt = new HashMap<String,String>();
        /* Build command line, so we can parse our way - make sure there is trailing space */
        String cmdline = "";
        for(int i = 1; i < args.length; i++) {
            cmdline += args[i] + " ";
        }
        boolean inquote = false;
        StringBuilder sb = new StringBuilder();
        String varid = null;
        for(int i = 0; i < cmdline.length(); i++) {
            char c = cmdline.charAt(i);
            if(inquote) {   /* If in quote, accumulate until end or another quote */
                if(c == '\"') { /* End quote */
                    inquote = false;
                    if(varid == null) { /* No varid? */
                        rslt.put(ARG_LABEL, sb.toString());
                    }
                    else {
                        rslt.put(varid, sb.toString());
                        varid = null;
                    }
                    sb.setLength(0);
                }
                else {
                    sb.append(c);
                }
            }
            else if(c == '\"') {    /* Start of quote? */
                inquote = true;
            }
            else if(c == ':') { /* var:value */
                varid = sb.toString();  /* Save variable ID */
                sb.setLength(0);
            }
            else if(c == ' ') { /* Ending space? */
                if(varid == null) { /* No varid? */
                    if(sb.length() > 0) {
                        rslt.put(ARG_LABEL, sb.toString());
                    }
                }
                else {
                    rslt.put(varid, sb.toString());
                    varid = null;
                }
                sb.setLength(0);
            }
            else {
                sb.append(c);
            }
        }
        if(inquote) {   /* If still in quote, syntax error */
            snd.sendMessage("Error: unclosed doublequote");
            return null;
        }
        return rslt;
    }
    
    public static boolean onCommand(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        if(api == null) {
            sender.sendMessage("Markers component is not enabled.");
            return false;
        }
        if(args.length == 0)
            return false;
        DynmapPlayer player = null;
        if (sender instanceof DynmapPlayer)
            player = (DynmapPlayer) sender;
        /* Check if valid command */
        String c = args[0];
        if (!commands.contains(c)) {
            return false;
        }
        /* Process commands read commands */
        api.lock.readLock().lock();
        try {
            /* List markers */
            if(c.equals("list") && plugin.checkPlayerPermission(sender, "marker.list")) {
                return processListMarker(plugin, sender, cmd, commandLabel, args);
            }
            /* List icons */
            else if(c.equals("icons") && plugin.checkPlayerPermission(sender, "marker.icons")) {
                return processListIcon(plugin, sender, cmd, commandLabel, args);
            }
            /* List sets */
            else if(c.equals("listsets") && plugin.checkPlayerPermission(sender, "marker.listsets")) {
                return processListSet(plugin, sender, cmd, commandLabel, args);
            }
            /* List areas */
            else if(c.equals("listareas") && plugin.checkPlayerPermission(sender, "marker.listareas")) {
                return processListArea(plugin, sender, cmd, commandLabel, args);
            }
            /* List poly-lines */
            else if(c.equals("listlines") && plugin.checkPlayerPermission(sender, "marker.listlines")) {
                return processListLine(plugin, sender, cmd, commandLabel, args);
            }
            /* List circles */
            else if(c.equals("listcircles") && plugin.checkPlayerPermission(sender, "marker.listcircles")) {
                return processListCircle(plugin, sender, cmd, commandLabel, args);
            }
            /* Get label for given item - must have ID and type parameter */
            else if(c.equals("getlabel") && plugin.checkPlayerPermission(sender, "marker.getlabel")) {
                return processGetLabel(plugin, sender, cmd, commandLabel, args);
            }
        } finally {
            api.lock.readLock().unlock();
        }
        // Handle modify commands
        api.lock.writeLock().lock();
        try {
            if(c.equals("add") && api.core.checkPlayerPermission(sender, "marker.add")) {
                return processAddMarker(plugin, sender, cmd, commandLabel, args, player);
            }
            /* Update position of bookmark - must have ID parameter */
            else if(c.equals("movehere") && plugin.checkPlayerPermission(sender, "marker.movehere")) {
                return processMoveHere(plugin, sender, cmd, commandLabel, args, player);
            }
            /* Update other attributes of marker - must have ID parameter */
            else if(c.equals("update") && plugin.checkPlayerPermission(sender, "marker.update")) {
                return processUpdateMarker(plugin, sender, cmd, commandLabel, args);
            }
            /* Delete marker - must have ID parameter */
            else if(c.equals("delete") && plugin.checkPlayerPermission(sender, "marker.delete")) {
                return processDeleteMarker(plugin, sender, cmd, commandLabel, args);
            }
            else if(c.equals("addset") && plugin.checkPlayerPermission(sender, "marker.addset")) {
                return processAddSet(plugin, sender, cmd, commandLabel, args, player);
            }
            else if(c.equals("updateset") && plugin.checkPlayerPermission(sender, "marker.updateset")) {
                return processUpdateSet(plugin, sender, cmd, commandLabel, args);
            }
            else if(c.equals("deleteset") && plugin.checkPlayerPermission(sender, "marker.deleteset")) {
                return processDeleteSet(plugin, sender, cmd, commandLabel, args);
            }
            /* Add new icon */
            else if(c.equals("addicon") && plugin.checkPlayerPermission(sender, "marker.addicon")) {
                return processAddIcon(plugin, sender, cmd, commandLabel, args);
            }
            else if(c.equals("updateicon") && plugin.checkPlayerPermission(sender, "marker.updateicon")) {
                return processUpdateIcon(plugin, sender, cmd, commandLabel, args);
            }
            else if(c.equals("deleteicon") && plugin.checkPlayerPermission(sender, "marker.deleteicon")) {
                return processDeleteIcon(plugin, sender, cmd, commandLabel, args);
            }
            /* Add point to accumulator */
            else if(c.equals("addcorner") && plugin.checkPlayerPermission(sender, "marker.addarea")) {
                return processAddCorner(plugin, sender, cmd, commandLabel, args, player);
            }
            else if(c.equals("clearcorners") && plugin.checkPlayerPermission(sender, "marker.addarea")) {
                return processClearCorners(plugin, sender, cmd, commandLabel, args, player);
            }
            else if(c.equals("addarea") && plugin.checkPlayerPermission(sender, "marker.addarea")) {
                return processAddArea(plugin, sender, cmd, commandLabel, args, player);
            }
            /* Delete area - must have ID parameter */
            else if(c.equals("deletearea") && plugin.checkPlayerPermission(sender, "marker.deletearea")) {
                return processDeleteArea(plugin, sender, cmd, commandLabel, args);
            }
            /* Update other attributes of area - must have ID parameter */
            else if(c.equals("updatearea") && plugin.checkPlayerPermission(sender, "marker.updatearea")) {
                return processUpdateArea(plugin, sender, cmd, commandLabel, args);
            }
            else if(c.equals("addline") && plugin.checkPlayerPermission(sender, "marker.addline")) {
                return processAddLine(plugin, sender, cmd, commandLabel, args, player);
            }
            /* Delete poly-line - must have ID parameter */
            else if(c.equals("deleteline") && plugin.checkPlayerPermission(sender, "marker.deleteline")) {
                return processDeleteLine(plugin, sender, cmd, commandLabel, args);
            }
            /* Update other attributes of poly-line - must have ID parameter */
            else if(c.equals("updateline") && plugin.checkPlayerPermission(sender, "marker.updateline")) {
                return processUpdateLine(plugin, sender, cmd, commandLabel, args);
            }
            else if(c.equals("addcircle") && plugin.checkPlayerPermission(sender, "marker.addcircle")) {
                return processAddCircle(plugin, sender, cmd, commandLabel, args, player);
            }
            /* Delete circle - must have ID parameter */
            else if(c.equals("deletecircle") && plugin.checkPlayerPermission(sender, "marker.deletecircle")) {
                return processDeleteCircle(plugin, sender, cmd, commandLabel, args);
            }
            /* Update other attributes of circle - must have ID parameter */
            else if(c.equals("updatecircle") && plugin.checkPlayerPermission(sender, "marker.updatecircle")) {
                return processUpdateCircle(plugin, sender, cmd, commandLabel, args);
            }
            /* Get description for given item - must have ID and type parameter */
            else if(c.equals("getdesc") && plugin.checkPlayerPermission(sender, "marker.getdesc")) {
                return processGetDesc(plugin, sender, cmd, commandLabel, args);
            }
            /* Reset description for given item - must have ID and type parameter */
            else if(c.equals("resetdesc") && plugin.checkPlayerPermission(sender, "marker.resetdesc")) {
                return processResetDesc(plugin, sender, cmd, commandLabel, args);
            }
            /* Append to description for given item - must have ID and type parameter */
            else if(c.equals("appenddesc") && plugin.checkPlayerPermission(sender, "marker.appenddesc")) {
                return processAppendDesc(plugin, sender, cmd, commandLabel, args);
            }
            /* Import description for given item from file - must have ID and type parameter */
            else if(c.equals("importdesc") && plugin.checkPlayerPermission(sender, "marker.importdesc")) {
                return processImportDesc(plugin, sender, cmd, commandLabel, args);
            }
            /* Import description for given item from file - must have ID and type parameter */
            else if(c.equals("importlabel") && plugin.checkPlayerPermission(sender, "marker.importlabel")) {
                return processImportLabel(plugin, sender, cmd, commandLabel, args);
            }
            else {
                return false;
            }
        } finally {
            api.lock.writeLock().unlock();
        }
    }

    private static boolean processAddMarker(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args, DynmapPlayer player) {
        String id, setid, label, iconid, markup;
        String x, y, z, world, normalized_world;

        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            iconid = parms.get(ARG_ICON);
            setid = parms.get(ARG_SET);
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            markup = parms.get(ARG_MARKUP);
            x = parms.get(ARG_X);
            y = parms.get(ARG_Y);
            z = parms.get(ARG_Z);
            String minzoom = parms.get(ARG_MINZOOM);
            int min_zoom = -1;
            if (minzoom != null) {
                try {
                    min_zoom = Integer.parseInt(minzoom);
                } catch (NumberFormatException nfx) {
                    sender.sendMessage("Invalid minzoom: " + minzoom);
                    return true;
                }
            }
            String maxzoom = parms.get(ARG_MAXZOOM);
            int max_zoom = -1;
            if (maxzoom != null) {
                try {
                    max_zoom = Integer.parseInt(maxzoom);
                } catch (NumberFormatException nfx) {
                    sender.sendMessage("Invalid maxzoom: " + maxzoom);
                    return true;
                }
            }
            world = DynmapWorld.normalizeWorldName(parms.get(ARG_WORLD));
            if(world != null) {
                normalized_world = DynmapWorld.normalizeWorldName(world);
                if(api.core.getWorld(normalized_world) == null) {
                    sender.sendMessage("Invalid world ID: " + world);
                    return true;
                }
            }
            DynmapLocation loc = null;
            if((x == null) && (y == null) && (z == null) && (world == null)) {
                if(player == null) {
                    sender.sendMessage("Must be issued by player, or x, y, z, and world parameters are required");
                    return true;
                }
                loc = player.getLocation();
            }
            else if((x != null) && (y != null) && (z != null) && (world != null)) {
                try {
                    loc = new DynmapLocation(world, Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
                } catch (NumberFormatException nfx) {
                    sender.sendMessage("Coordinates x, y, and z must be numbers");
                    return true;
                }
            }
            else {
                sender.sendMessage("Must be issued by player, or x, y, z, and world parameters are required");
                return true;
            }
            /* Fill in defaults for missing parameters */
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            /* Add new marker */
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            MarkerIcon ico = null;
            if(iconid == null) {
                ico = set.getDefaultMarkerIcon();
            }
            if(ico == null) {
                if(iconid == null) {
                    iconid = MarkerIcon.DEFAULT;
                }
                ico = api.getMarkerIcon(iconid);
            }
            if(ico == null) {
                sender.sendMessage("Error: invalid icon - " + iconid);
                return true;
            }
            if (minzoom != null) {
                
            }
            boolean isMarkup = "true".equals(markup);
            Marker m = set.createMarker(id, label, isMarkup,
                    loc.world, loc.x, loc.y, loc.z, ico, true);
            if(m == null) {
                sender.sendMessage("Error creating marker");
            }
            else {
                if (min_zoom >= 0) {
                    m.setMinZoom(min_zoom);
                }
                if (max_zoom >= 0) {
                    m.setMaxZoom(max_zoom);
                }
                sender.sendMessage("Added marker id:'" + m.getMarkerID() + "' (" + m.getLabel() + ") to set '" + set.getMarkerSetID() + "'");
            }
        }
        else {
            sender.sendMessage("Marker label required");
        }
        return true;
    }
    
    private static boolean processMoveHere(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args, DynmapPlayer player) {
        String id, label, setid;
        if(player == null) {
            sender.sendMessage("Command can only be used by player");
        }
        else if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            setid = parms.get(ARG_SET);
            if((id == null) && (label == null)) {
                sender.sendMessage("<label> or id:<marker-id> required");
                return true;
            }
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            Marker marker;
            if(id != null) {
                marker = set.findMarker(id);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: marker not found - " + id);
                    return true;
                }
            }
            else {
                marker = set.findMarkerByLabel(label);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: marker not found - " + label);
                    return true;
                }
            }
            DynmapLocation loc = player.getLocation();
            marker.setLocation(loc.world, loc.x, loc.y, loc.z);
            sender.sendMessage("Updated location of marker id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
        }
        else {
            sender.sendMessage("<label> or id:<marker-id> required");
        }
        return true;
    }
    
    private static boolean processUpdateMarker(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String id, setid, label, newlabel, iconid, markup;
        String x, y, z, world;
        String newset;
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            markup = parms.get(ARG_MARKUP);
            setid = parms.get(ARG_SET);
            newset = parms.get(ARG_NEWSET);
            x = parms.get(ARG_X);
            y = parms.get(ARG_Y);
            z = parms.get(ARG_Z);
            String minzoom = parms.get(ARG_MINZOOM);
            int min_zoom = -1;
            if (minzoom != null) {
                try {
                    min_zoom = Integer.parseInt(minzoom);
                } catch (NumberFormatException nfx) {
                    sender.sendMessage("Invalid minzoom: " + minzoom);
                    return true;
                }
            }
            String maxzoom = parms.get(ARG_MAXZOOM);
            int max_zoom = -1;
            if (maxzoom != null) {
                try {
                    max_zoom = Integer.parseInt(maxzoom);
                } catch (NumberFormatException nfx) {
                    sender.sendMessage("Invalid maxzoom: " + maxzoom);
                    return true;
                }
            }
            world = parms.get(ARG_WORLD);
            if(world != null) {
                if(api.core.getWorld(world) == null) {
                    sender.sendMessage("Invalid world ID: " + world);
                    return true;
                }
            }
            DynmapLocation loc = null;
            if((x != null) && (y != null) && (z != null) && (world != null)) {
                try {
                    loc = new DynmapLocation(world, Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
                } catch (NumberFormatException nfx) {
                    sender.sendMessage("Coordinates x, y, and z must be numbers");
                    return true;
                }
            }

            if((id == null) && (label == null)) {
                sender.sendMessage("<label> or id:<marker-id> required");
                return true;
            }
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            Marker marker;
            if(id != null) {
                marker = set.findMarker(id);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: marker not found - " + id);
                    return true;
                }
            }
            else {
                marker = set.findMarkerByLabel(label);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: marker not found - " + label);
                    return true;
                }
            }
            newlabel = parms.get(ARG_NEWLABEL);
            if(newlabel != null) {    /* Label set? */
                marker.setLabel(newlabel, "true".equals(markup));
            }
            else if(markup != null) {
                marker.setLabel(marker.getLabel(), "true".equals(markup));
            }
            iconid = parms.get(ARG_ICON);
            if(iconid != null) {
                MarkerIcon ico = api.getMarkerIcon(iconid);
                if(ico == null) {
                    sender.sendMessage("Error: invalid icon - " + iconid);
                    return true;
                }
                marker.setMarkerIcon(ico);
            }
            if(loc != null)
                marker.setLocation(loc.world, loc.x, loc.y, loc.z);
            if (min_zoom >= 0) {
                marker.setMinZoom(min_zoom);
            }
            if (max_zoom >= 0) {
                marker.setMaxZoom(max_zoom);
            }
            if(newset != null) {
                MarkerSet ms = api.getMarkerSet(newset);
                if(ms == null) {
                    sender.sendMessage("Error: invalid new marker set - " + newset);
                    return true;
                }
                marker.setMarkerSet(ms);
            }
            sender.sendMessage("Updated marker id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
        }
        else {
            sender.sendMessage("<label> or id:<marker-id> required");
        }
        return true;
    }
    
    private static boolean processDeleteMarker(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String id, label, setid;
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            setid = parms.get(ARG_SET);
            if((id == null) && (label == null)) {
                sender.sendMessage("<label> or id:<marker-id> required");
                return true;
            }
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            Marker marker;
            if(id != null) {
                marker = set.findMarker(id);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: marker not found - " + id);
                    return true;
                }
            }
            else {
                marker = set.findMarkerByLabel(label);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: marker not found - " + label);
                    return true;
                }
            }
            marker.deleteMarker();
            sender.sendMessage("Deleted marker id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
        }
        else {
            sender.sendMessage("<label> or id:<marker-id> required");
        }
        return true;
    }
    
    private static boolean processListMarker(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String setid;
        /* Parse arguements */
        Map<String,String> parms = parseArgs(args, sender);
        if(parms == null) return true;
        setid = parms.get(ARG_SET);
        if(setid == null) {
            setid = MarkerSet.DEFAULT;
        }
        MarkerSet set = api.getMarkerSet(setid);
        if(set == null) {
            sender.sendMessage("Error: invalid set - " + setid);
            return true;
        }
        Set<Marker> markers = set.getMarkers();
        TreeMap<String, Marker> sortmarkers = new TreeMap<String, Marker>();
        for(Marker m : markers) {
            sortmarkers.put(m.getMarkerID(), m);
        }
        for(String s : sortmarkers.keySet()) {
            Marker m = sortmarkers.get(s);
            String msg = m.getMarkerID() + ": label:\"" + m.getLabel() + "\", set:" + m.getMarkerSet().getMarkerSetID() + 
                    ", world:" + m.getWorld() + ", x:" + m.getX() + ", y:" + m.getY() + ", z:" + m.getZ() + 
                    ", icon:" + m.getMarkerIcon().getMarkerIconID() + ", markup:" + m.isLabelMarkup();
            if (m.getMinZoom() >= 0) {
                msg += ", minzoom:" + m.getMinZoom();
            }
            if (m.getMaxZoom() >= 0) {
                msg += ", maxzoom:" + m.getMaxZoom();
            }
            sender.sendMessage(msg);
        }
        return true;
    }
    
    private static boolean processListIcon(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        Set<String> iconids = new TreeSet<String>(api.markericons.keySet());
        for(String s : iconids) {
            MarkerIcon ico = api.markericons.get(s);
            sender.sendMessage(ico.getMarkerIconID() + ": label:\"" + ico.getMarkerIconLabel() + "\", builtin:" + ico.isBuiltIn());
        }

        return true;
    }
    
    private static boolean processAddSet(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args, DynmapPlayer player) {
        String id, label, prio, minzoom, maxzoom, deficon;
        
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            prio = parms.get(ARG_PRIO);
            minzoom = parms.get(ARG_MINZOOM);
            maxzoom = parms.get(ARG_MAXZOOM);
            deficon = parms.get(ARG_DEFICON);
            if(deficon == null) {
                deficon = MarkerIcon.DEFAULT;
            }
            if((id == null) && (label == null)) {
                sender.sendMessage("<label> or id:<marker-id> required");
                return true;
            }
            if(label == null)
                label = id;
            if(id == null)
                id = label;
            /* See if marker set exists */
            MarkerSet set = api.getMarkerSet(id);
            if(set != null) {
                sender.sendMessage("Error: set already exists - id:" + set.getMarkerSetID());
                return true;
            }
            /* Create new set */
            set = api.createMarkerSet(id, label, null, true);
            if(set == null) {
                sender.sendMessage("Error creating set");
            }
            else {
                String h = parms.get(ARG_HIDE);
                if((h != null) && (h.equals("true")))
                    set.setHideByDefault(true);
                String showlabels = parms.get(ARG_SHOWLABEL);
                if(showlabels != null) {
                    if(showlabels.equals("true"))
                        set.setLabelShow(true);
                    else if(showlabels.equals("false"))
                        set.setLabelShow(false);
                }
                if(prio != null) {
                    try {
                        set.setLayerPriority(Integer.valueOf(prio));
                    } catch (NumberFormatException nfx) {
                        sender.sendMessage("Invalid priority: " + prio);
                    }
                }
                MarkerIcon mi = MarkerAPIImpl.getMarkerIconImpl(deficon);
                if(mi != null) {
                    set.setDefaultMarkerIcon(mi);
                }
                else {
                    sender.sendMessage("Invalid default icon: " + deficon);
                }
                if(minzoom != null) {
                    try {
                        set.setMinZoom(Integer.valueOf(minzoom));
                    } catch (NumberFormatException nfx) {
                        sender.sendMessage("Invalid min zoom: " + minzoom);
                    }
                }
                if(maxzoom != null) {
                    try {
                        set.setMaxZoom(Integer.valueOf(maxzoom));
                    } catch (NumberFormatException nfx) {
                        sender.sendMessage("Invalid max zoom: " + maxzoom);
                    }
                }
                sender.sendMessage("Added set id:'" + set.getMarkerSetID() + "' (" + set.getMarkerSetLabel() + ")");
            }
        }
        else {
            sender.sendMessage("<label> or id:<set-id> required");
        }
        return true;
    }

    private static boolean processUpdateSet(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String id, label, prio, minzoom, maxzoom, deficon, newlabel;
        
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            prio = parms.get(ARG_PRIO);
            minzoom = parms.get(ARG_MINZOOM);
            maxzoom = parms.get(ARG_MAXZOOM);
            deficon = parms.get(ARG_DEFICON);
            if((id == null) && (label == null)) {
                sender.sendMessage("<label> or id:<set-id> required");
                return true;
            }
            MarkerSet set = null;
            if(id != null) {
                set = api.getMarkerSet(id);
                if(set == null) {
                    sender.sendMessage("Error: set does not exist - id:" + id);
                    return true;
                }
            }
            else {
                Set<MarkerSet> sets = api.getMarkerSets();
                for(MarkerSet s : sets) {
                    if(s.getMarkerSetLabel().equals(label)) {
                        set = s;
                        break;
                    }
                }
                if(set == null) {
                    sender.sendMessage("Error: matching set not found");
                    return true;                        
                }
            }
            newlabel = parms.get(ARG_NEWLABEL);
            if(newlabel != null) {
                set.setMarkerSetLabel(newlabel);
            }
            String hide = parms.get(ARG_HIDE);
            if(hide != null) {
                set.setHideByDefault(hide.equals("true"));
            }
            String showlabels = parms.get(ARG_SHOWLABEL);
            if(showlabels != null) {
                if(showlabels.equals("true"))
                    set.setLabelShow(true);
                else if(showlabels.equals("false"))
                    set.setLabelShow(false);
                else
                    set.setLabelShow(null);
            }
            if(deficon != null) {
                MarkerIcon mi = null;
                if(deficon.equals("") == false) {
                    mi = MarkerAPIImpl.getMarkerIconImpl(deficon);
                    if(mi == null) {
                        sender.sendMessage("Error: invalid marker icon - " + deficon);
                    }
                }
                set.setDefaultMarkerIcon(mi);
            }

            if(prio != null) {
                try {
                    set.setLayerPriority(Integer.valueOf(prio));
                } catch (NumberFormatException nfx) {
                    sender.sendMessage("Invalid priority: " + prio);
                }
            }
            if(minzoom != null) {
                try {
                    set.setMinZoom(Integer.valueOf(minzoom));
                } catch (NumberFormatException nfx) {
                    sender.sendMessage("Invalid min zoom: " + minzoom);
                }
            }
            if(maxzoom != null) {
                try {
                    set.setMaxZoom(Integer.valueOf(maxzoom));
                } catch (NumberFormatException nfx) {
                    sender.sendMessage("Invalid max zoom: " + maxzoom);
                }
            }
            sender.sendMessage("Set '" + set.getMarkerSetID() + "' updated");
        }
        else {
            sender.sendMessage("<label> or id:<set-id> required");
        }
        return true;
    }
    
    private static boolean processDeleteSet(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String id, label;
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            if((id == null) && (label == null)) {
                sender.sendMessage("<label> or id:<set-id> required");
                return true;
            }
            if(id != null) {
                MarkerSet set = api.getMarkerSet(id);
                if(set == null) {
                    sender.sendMessage("Error: set does not exist - id:" + id);
                    return true;
                }
                set.deleteMarkerSet();
            }
            else {
                Set<MarkerSet> sets = api.getMarkerSets();
                MarkerSet set = null;
                for(MarkerSet s : sets) {
                    if(s.getMarkerSetLabel().equals(label)) {
                        set = s;
                        break;
                    }
                }
                if(set == null) {
                    sender.sendMessage("Error: matching set not found");
                    return true;                        
                }
                set.deleteMarkerSet();
            }
            sender.sendMessage("Deleted set");
        }
        else {
            sender.sendMessage("<label> or id:<set-id> required");
        }
        return true;
    }
    
    private static boolean processListSet(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        Set<String> setids = new TreeSet<String>(api.markersets.keySet());
        for(String s : setids) {
            MarkerSet set = api.markersets.get(s);
            Boolean b = set.getLabelShow();
            MarkerIcon defi = set.getDefaultMarkerIcon();
            String msg = set.getMarkerSetID() + ": label:\"" + set.getMarkerSetLabel() + "\", hide:" + set.getHideByDefault() + ", prio:" + set.getLayerPriority();
            if (defi != null) {
                msg += ", deficon:" + defi.getMarkerIconID();
            }
            if (b != null) {
                msg += ", showlabels:" + b;
            }
            if (set.getMinZoom() >= 0) {
                msg += ", minzoom:" + set.getMinZoom();
            }
            if (set.getMaxZoom() >= 0) {
                msg += ", maxzoom:" + set.getMaxZoom();
            }
            sender.sendMessage(msg);
        }
        return true;
    }
    
    private static boolean processAddIcon(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String id, file, label;
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            file = parms.get(ARG_FILE);
            label = parms.get(ARG_LABEL);
            if(id == null) {
                sender.sendMessage("id:<icon-id> required");
                return true;
            }
            if(file == null) {
                sender.sendMessage("file:\"filename\" required");
                return true;
            }
            if(label == null)
                label = id;
            MarkerIcon ico = MarkerAPIImpl.getMarkerIconImpl(id);
            if(ico != null) {
                sender.sendMessage("Icon '" + id + "' already defined.");
                return true;
            }
            /* Open stream to filename */
            File iconf = new File(file);
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(iconf);
                /* Create new icon */
                MarkerIcon mi = api.createMarkerIcon(id, label, fis);
                if(mi == null) {
                    sender.sendMessage("Error creating icon");
                    return true;
                }
            } catch (IOException iox) {
                sender.sendMessage("Error loading icon file - " + iox);
            } finally {
                if(fis != null) {
                    try { fis.close(); } catch (IOException iox) {}
                }
            }
        }
        else {
            sender.sendMessage("id:<icon-id> and file:\"filename\" required");
        }
        return true;
    }

    private static boolean processUpdateIcon(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String id, label, newlabel, file;
        
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            newlabel = parms.get(ARG_NEWLABEL);
            file = parms.get(ARG_FILE);
            if((id == null) && (label == null)) {
                sender.sendMessage("<label> or id:<icon-id> required");
                return true;
            }
            MarkerIcon ico = null;
            if(id != null) {
                ico = MarkerAPIImpl.getMarkerIconImpl(id);
                if(ico == null) {
                    sender.sendMessage("Error: icon does not exist - id:" + id);
                    return true;
                }
            }
            else {
                Set<MarkerIcon> icons = api.getMarkerIcons();
                for(MarkerIcon ic : icons) {
                    if(ic.getMarkerIconLabel().equals(label)) {
                        ico = ic;
                        break;
                    }
                }
                if(ico == null) {
                    sender.sendMessage("Error: matching icon not found");
                    return true;                        
                }
            }
            if(newlabel != null) {
                ico.setMarkerIconLabel(newlabel);
            }
            /* Handle new file */
            if(file != null) {
                File iconf = new File(file);
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(iconf);
                    ico.setMarkerIconImage(fis);                        
                } catch (IOException iox) {
                    sender.sendMessage("Error loading icon file - " + iox);
                } finally {
                    if(fis != null) {
                        try { fis.close(); } catch (IOException iox) {}
                    }
                }
            }
            sender.sendMessage("Icon '" + ico.getMarkerIconID() + "' updated");
        }
        else {
            sender.sendMessage("<label> or id:<icon-id> required");
        }
        return true;
    }

    private static boolean processDeleteIcon(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String id, label;
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            if((id == null) && (label == null)) {
                sender.sendMessage("<label> or id:<icon-id> required");
                return true;
            }
            if(id != null) {
                MarkerIcon ico = MarkerAPIImpl.getMarkerIconImpl(id);
                if(ico == null) {
                    sender.sendMessage("Error: icon does not exist - id:" + id);
                    return true;
                }
                ico.deleteIcon();
            }
            else {
                Set<MarkerIcon> icos = api.getMarkerIcons();
                MarkerIcon ico = null;
                for(MarkerIcon ic : icos) {
                    if(ic.getMarkerIconLabel().equals(label)) {
                        ico = ic;
                        break;
                    }
                }
                if(ico == null) {
                    sender.sendMessage("Error: matching icon not found");
                    return true;                        
                }
                ico.deleteIcon();
            }
            sender.sendMessage("Deleted marker icon");
        }
        else {
            sender.sendMessage("<label> or id:<icon-id> required");
        }
        return true;
    }

    private static boolean processAddCorner(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args, DynmapPlayer player) {
        String id;
        
        DynmapLocation loc = null;
        if(player == null) {
            id = "-console-";
        }
        else {
            id = player.getName();
            loc = player.getLocation();
        }
        List<DynmapLocation> ll = api.pointaccum.get(id); /* Find list */
        
        if(args.length > 3) {   /* Enough for coord */
            String w = null;
            if(args.length == 4) {  /* No world */
                if(ll == null) {    /* No points?  Error */
                    sender.sendMessage("First added corner needs world ID after coordinates");
                    return true;
                }
                else {
                    w = ll.get(0).world;   /* Use same world */
                }
            }
            else {  /* Get world ID */
                w = args[4];
                if(api.core.getWorld(w) == null) {
                    sender.sendMessage("Invalid world ID: " + args[3]);
                    return true;
                }
            }
            try {
                loc = new DynmapLocation(w, Double.parseDouble(args[1]), Double.parseDouble(args[2]), Double.parseDouble(args[3]));
            } catch (NumberFormatException nfx) {
                sender.sendMessage("Bad format: /dmarker addcorner <x> <y> <z> <world>");
                return true;
            }
        }
        if(loc == null) {
            sender.sendMessage("Console must supply corner coordinates: <x> <y> <z> <world>");
            return true;
        }
        if(ll == null) {
            ll = new ArrayList<DynmapLocation>();
            api.pointaccum.put(id, ll);
        }
        else {  /* Else, if list exists, see if world matches */
            if(ll.get(0).world.equals(loc.world) == false) {
                ll.clear(); /* Reset list - point on new world */
            }
        }
        ll.add(loc);
        sender.sendMessage("Added corner #" + ll.size() + " at {" + loc.x + "," + loc.y + "," + loc.z + "} to list");

        return true;
    }
    
    private static boolean processClearCorners(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args, DynmapPlayer player) {
        String id;
        
        if(player == null) {
            id = "-console-";
        }
        else {
            id = player.getName();
        }
        api.pointaccum.remove(id);
        sender.sendMessage("Cleared corner list");

        return true;
    }
    
    private static boolean processAddArea(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args, DynmapPlayer player) {
        String pid, setid, id, label, markup;
        if(player == null) {
            pid = "-console-";
        }
        else {
            pid = player.getName();
        }
        List<DynmapLocation> ll = api.pointaccum.get(pid); /* Find list */
        if((ll == null) || (ll.size() < 2)) {   /* Not enough points? */
            sender.sendMessage("At least two corners must be added with /dmarker addcorner before an area can be added");
            return true;
        }
        /* Parse arguements */
        Map<String,String> parms = parseArgs(args, sender);
        if(parms == null) return true;
        setid = parms.get(ARG_SET);
        id = parms.get(ARG_ID);
        label = parms.get(ARG_LABEL);
        markup = parms.get(ARG_MARKUP);
        /* Fill in defaults for missing parameters */
        if(setid == null) {
            setid = MarkerSet.DEFAULT;
        }
        /* Add new marker */
        MarkerSet set = api.getMarkerSet(setid);
        if(set == null) {
            sender.sendMessage("Error: invalid set - " + setid);
            return true;
        }
        /* Make coord list */
        double[] xx = new double[ll.size()];
        double[] zz = new double[ll.size()];
        for(int i = 0; i < ll.size(); i++) {
            DynmapLocation loc = ll.get(i);
            xx[i] = loc.x;
            zz[i] = loc.z;
        }
        /* Make area marker */
        AreaMarker m = set.createAreaMarker(id, label, "true".equals(markup), ll.get(0).world, xx, zz, true);
        if(m == null) {
            sender.sendMessage("Error creating area");
        }
        else {
            /* Process additional attributes, if any */
            processAreaArgs(sender, m, parms);
            
            sender.sendMessage("Added area id:'" + m.getMarkerID() + "' (" + m.getLabel() + ") to set '" + set.getMarkerSetID() + "'");
            api.pointaccum.remove(pid); /* Clear corner list */
        }
        return true;
    }
    
    private static boolean processListArea(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String setid;
        /* Parse arguements */
        Map<String,String> parms = parseArgs(args, sender);
        if(parms == null) return true;
        setid = parms.get(ARG_SET);
        if(setid == null) {
            setid = MarkerSet.DEFAULT;
        }
        MarkerSet set = api.getMarkerSet(setid);
        if(set == null) {
            sender.sendMessage("Error: invalid set - " + setid);
            return true;
        }
        Set<AreaMarker> markers = set.getAreaMarkers();
        TreeMap<String, AreaMarker> sortmarkers = new TreeMap<String, AreaMarker>();
        for(AreaMarker m : markers) {
            sortmarkers.put(m.getMarkerID(), m);
        }
        for(String s : sortmarkers.keySet()) {
            AreaMarker m = sortmarkers.get(s);
            String msg = m.getMarkerID() + ": label:\"" + m.getLabel() + "\", set:" + m.getMarkerSet().getMarkerSetID() + 
                    ", world:" + m.getWorld() + 
                    ", weight:" + m.getLineWeight() + ", color:" + String.format("%06x", m.getLineColor()) +
                    ", opacity:" + m.getLineOpacity() + ", fillcolor:" + String.format("%06x", m.getFillColor()) +
                    ", fillopacity:" + m.getFillOpacity() + ", boost:" + m.getBoostFlag() + ", markup:" + m.isLabelMarkup();
            if (m.getMinZoom() >= 0) {
                msg += ", minzoom:" + m.getMinZoom();
            }
            if (m.getMaxZoom() >= 0) {
                msg += ", maxzoom:" + m.getMaxZoom();
            }
        	EnterExitText t = m.getGreetingText();
            if (t != null) {
            	if (t.title != null) msg += ", greeting:\"" + t.title + "\"";
            	if (t.subtitle != null) msg += ", greetingsub:\"" + t.subtitle + "\"";
            }
        	t = m.getFarewellText();
            if (t != null) {
            	if (t.title != null) msg += ", farewell:\"" + t.title + "\"";
            	if (t.subtitle != null) msg += ", farewellsub:\"" + t.subtitle + "\"";
            }
            sender.sendMessage(msg);
        }
        return true;
    }
    
    private static boolean processDeleteArea(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String id, label, setid;
        
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            setid = parms.get(ARG_SET);
            if((id == null) && (label == null)) {
                sender.sendMessage("<label> or id:<area-id> required");
                return true;
            }
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            AreaMarker marker;
            if(id != null) {
                marker = set.findAreaMarker(id);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: area not found - " + id);
                    return true;
                }
            }
            else {
                marker = set.findAreaMarkerByLabel(label);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: area not found - " + label);
                    return true;
                }
            }
            marker.deleteMarker();
            sender.sendMessage("Deleted area id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
        }
        else {
            sender.sendMessage("<label> or id:<area-id> required");
        }
        return true;
    }
    
    private static boolean processUpdateArea(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String id, label, setid, newlabel, markup;
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            markup = parms.get(ARG_MARKUP);
            setid = parms.get(ARG_SET);
            if((id == null) && (label == null)) {
                sender.sendMessage("<label> or id:<area-id> required");
                return true;
            }
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            AreaMarker marker;
            if(id != null) {
                marker = set.findAreaMarker(id);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: area not found - " + id);
                    return true;
                }
            }
            else {
                marker = set.findAreaMarkerByLabel(label);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: area not found - " + label);
                    return true;
                }
            }
            newlabel = parms.get(ARG_NEWLABEL);
            if(newlabel != null) {    /* Label set? */
                marker.setLabel(newlabel, "true".equals(markup));
            }
            else if(markup != null) {
                marker.setLabel(marker.getLabel(), "true".equals(markup));
            }
            if(!processAreaArgs(sender,marker, parms))
                return true;
            sender.sendMessage("Updated area id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
        }
        else {
            sender.sendMessage("<label> or id:<area-id> required");
        }
        return true;
    }
    
    private static boolean processAddLine(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args, DynmapPlayer player) {
        String setid;
        String pid, id, label, markup;
        
        if(player == null) {
            pid = "-console-";
        }
        else {
            pid = player.getName();
        }
        List<DynmapLocation> ll = api.pointaccum.get(pid); /* Find list */
        if((ll == null) || (ll.size() < 2)) {   /* Not enough points? */
            sender.sendMessage("At least two corners must be added with /dmarker addcorner before a line can be added");
            return true;
        }
        /* Parse arguements */
        Map<String,String> parms = parseArgs(args, sender);
        if(parms == null) return true;
        setid = parms.get(ARG_SET);
        id = parms.get(ARG_ID);
        label = parms.get(ARG_LABEL);
        markup = parms.get(ARG_MARKUP);
        /* Fill in defaults for missing parameters */
        if(setid == null) {
            setid = MarkerSet.DEFAULT;
        }
        /* Add new marker */
        MarkerSet set = api.getMarkerSet(setid);
        if(set == null) {
            sender.sendMessage("Error: invalid set - " + setid);
            return true;
        }
        /* Make coord list */
        double[] xx = new double[ll.size()];
        double[] yy = new double[ll.size()];
        double[] zz = new double[ll.size()];
        for(int i = 0; i < ll.size(); i++) {
            DynmapLocation loc = ll.get(i);
            xx[i] = loc.x;
            yy[i] = loc.y;
            zz[i] = loc.z;
        }
        /* Make poly-line marker */
        PolyLineMarker m = set.createPolyLineMarker(id, label, "true".equals(markup), ll.get(0).world, xx, yy, zz, true);
        if(m == null) {
            sender.sendMessage("Error creating line");
        }
        else {
            /* Process additional attributes, if any */
            processPolyArgs(sender, m, parms);
            
            sender.sendMessage("Added line id:'" + m.getMarkerID() + "' (" + m.getLabel() + ") to set '" + set.getMarkerSetID() + "'");
            api.pointaccum.remove(pid); /* Clear corner list */
        }

        return true;
    }

    private static boolean processListLine(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String setid;
        /* Parse arguements */
        Map<String,String> parms = parseArgs(args, sender);
        if(parms == null) return true;
        setid = parms.get(ARG_SET);
        if(setid == null) {
            setid = MarkerSet.DEFAULT;
        }
        MarkerSet set = api.getMarkerSet(setid);
        if(set == null) {
            sender.sendMessage("Error: invalid set - " + setid);
            return true;
        }
        Set<PolyLineMarker> markers = set.getPolyLineMarkers();
        TreeMap<String, PolyLineMarker> sortmarkers = new TreeMap<String, PolyLineMarker>();
        for(PolyLineMarker m : markers) {
            sortmarkers.put(m.getMarkerID(), m);
        }
        for(String s : sortmarkers.keySet()) {
            PolyLineMarker m = sortmarkers.get(s);
            String ptlist = "{ ";
            for(int i = 0; i < m.getCornerCount(); i++) {
                ptlist += "{" + m.getCornerX(i) + "," + m.getCornerY(i) + "," + m.getCornerZ(i) + "} ";
            }
            ptlist += "}";
            String msg = m.getMarkerID() + ": label:\"" + m.getLabel() + "\", set:" + m.getMarkerSet().getMarkerSetID() + 
                    ", world:" + m.getWorld() + ", corners:" + ptlist + 
                    ", weight: " + m.getLineWeight() + ", color:" + String.format("%06x", m.getLineColor()) +
                    ", opacity: " + m.getLineOpacity() + ", markup:" + m.isLabelMarkup();
            if (m.getMinZoom() >= 0) {
                msg += ", minzoom:" + m.getMinZoom();
            }
            if (m.getMaxZoom() >= 0) {
                msg += ", maxzoom:" + m.getMaxZoom();
            }
            sender.sendMessage(msg);
        }
        return true;
    }
    private static boolean processDeleteLine(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String id, setid, label;
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            setid = parms.get(ARG_SET);
            if((id == null) && (label == null)) {
                sender.sendMessage("<label> or id:<line-id> required");
                return true;
            }
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            PolyLineMarker marker;
            if(id != null) {
                marker = set.findPolyLineMarker(id);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: line not found - " + id);
                    return true;
                }
            }
            else {
                marker = set.findPolyLineMarkerByLabel(label);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: line not found - " + label);
                    return true;
                }
            }
            marker.deleteMarker();
            sender.sendMessage("Deleted poly-line id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
        }
        else {
            sender.sendMessage("<label> or id:<line-id> required");
        }
        return true;
    }
    private static boolean processUpdateLine(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String id, setid, label, newlabel, markup;
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            markup = parms.get(ARG_MARKUP);
            setid = parms.get(ARG_SET);
            if((id == null) && (label == null)) {
                sender.sendMessage("<label> or id:<line-id> required");
                return true;
            }
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            PolyLineMarker marker;
            if(id != null) {
                marker = set.findPolyLineMarker(id);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: line not found - " + id);
                    return true;
                }
            }
            else {
                marker = set.findPolyLineMarkerByLabel(label);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: line not found - " + label);
                    return true;
                }
            }
            newlabel = parms.get(ARG_NEWLABEL);
            if(newlabel != null) {    /* Label set? */
                marker.setLabel(newlabel, "true".equals(markup));
            }
            else if(markup != null) {
                marker.setLabel(marker.getLabel(), "true".equals(markup));
            }
            if(!processPolyArgs(sender,marker, parms))
                return true;
            sender.sendMessage("Updated line id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
        }
        else {
            sender.sendMessage("<label> or id:<line-id> required");
        }

        return true;
    }
    
    private static boolean processAddCircle(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args, DynmapPlayer player) {
        String id, setid, label, markup;
        String x, y, z, world;
        
        /* Parse arguements */
        Map<String,String> parms = parseArgs(args, sender);
        if(parms == null) return true;
        setid = parms.get(ARG_SET);
        id = parms.get(ARG_ID);
        label = parms.get(ARG_LABEL);
        markup = parms.get(ARG_MARKUP);
        x = parms.get(ARG_X);
        y = parms.get(ARG_Y);
        z = parms.get(ARG_Z);
        world = parms.get(ARG_WORLD);
        if(world != null) {
            if(api.core.getWorld(world) == null) {
                sender.sendMessage("Invalid world ID: " + world);
                return true;
            }
        }
        DynmapLocation loc = null;
        if((x == null) && (y == null) && (z == null) && (world == null)) {
            if(player == null) {
                sender.sendMessage("Must be issued by player, or x, y, z, and world parameters are required");
                return true;
            }
            loc = player.getLocation();
        }
        else if((x != null) && (y != null) && (z != null) && (world != null)) {
            try {
                loc = new DynmapLocation(world, Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
            } catch (NumberFormatException nfx) {
                sender.sendMessage("Coordinates x, y, and z must be numbers");
                return true;
            }
        }
        else {
            sender.sendMessage("Must be issued by player, or x, y, z, and world parameters are required");
            return true;
        }
        /* Fill in defaults for missing parameters */
        if(setid == null) {
            setid = MarkerSet.DEFAULT;
        }
        /* Add new marker */
        MarkerSet set = api.getMarkerSet(setid);
        if(set == null) {
            sender.sendMessage("Error: invalid set - " + setid);
            return true;
        }
        
        /* Make circle marker */
        CircleMarker m = set.createCircleMarker(id, label, "true".equals(markup), loc.world, loc.x, loc.y, loc.z, 1, 1, true);
        if(m == null) {
            sender.sendMessage("Error creating circle");
        }
        else {
            /* Process additional attributes, if any */
            if(!processCircleArgs(sender, m, parms)) {
                return true;
            }
            
            sender.sendMessage("Added circle id:'" + m.getMarkerID() + "' (" + m.getLabel() + ") to set '" + set.getMarkerSetID() + "'");
        }

        return true;
    }
    private static boolean processListCircle(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String setid;
        /* Parse arguements */
        Map<String,String> parms = parseArgs(args, sender);
        if(parms == null) return true;
        setid = parms.get(ARG_SET);
        if(setid == null) {
            setid = MarkerSet.DEFAULT;
        }
        MarkerSet set = api.getMarkerSet(setid);
        if(set == null) {
            sender.sendMessage("Error: invalid set - " + setid);
            return true;
        }
        Set<CircleMarker> markers = set.getCircleMarkers();
        TreeMap<String, CircleMarker> sortmarkers = new TreeMap<String, CircleMarker>();
        for(CircleMarker m : markers) {
            sortmarkers.put(m.getMarkerID(), m);
        }
        for(String s : sortmarkers.keySet()) {
            CircleMarker m = sortmarkers.get(s);
            String msg = m.getMarkerID() + ": label:\"" + m.getLabel() + "\", set:" + m.getMarkerSet().getMarkerSetID() + 
                    ", world:" + m.getWorld() + ", center:" + m.getCenterX() + "/" + m.getCenterY() + "/" + m.getCenterZ() +
                    ", radiusx:" + m.getRadiusX() + ", radiusz:" + m.getRadiusZ() +
                    ", weight: " + m.getLineWeight() + ", color:" + String.format("%06x", m.getLineColor()) +
                    ", opacity: " + m.getLineOpacity() + ", fillcolor: " + String.format("%06x", m.getFillColor()) +
                    ", fillopacity: " + m.getFillOpacity() + ", boost:" + m.getBoostFlag() + ", markup:" + m.isLabelMarkup();
            if (m.getMinZoom() >= 0) {
                msg += ", minzoom:" + m.getMinZoom();
            }
            if (m.getMaxZoom() >= 0) {
                msg += ", maxzoom:" + m.getMaxZoom();
            }
        	EnterExitText t = m.getGreetingText();
            if (t != null) {
            	if (t.title != null) msg += ", greeting:\"" + t.title + "\"";
            	if (t.subtitle != null) msg += ", greetingsub:\"" + t.subtitle + "\"";
            }
        	t = m.getFarewellText();
            if (t != null) {
            	if (t.title != null) msg += ", farewell:\"" + t.title + "\"";
            	if (t.subtitle != null) msg += ", farewellsub:\"" + t.subtitle + "\"";
            }            
            sender.sendMessage(msg);
        }
        return true;
    }
    private static boolean processDeleteCircle(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String id, setid, label;
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            setid = parms.get(ARG_SET);
            if((id == null) && (label == null)) {
                sender.sendMessage("<label> or id:<circle-id> required");
                return true;
            }
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            CircleMarker marker;
            if(id != null) {
                marker = set.findCircleMarker(id);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: circle not found - " + id);
                    return true;
                }
            }
            else {
                marker = set.findCircleMarkerByLabel(label);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: circle not found - " + label);
                    return true;
                }
            }
            marker.deleteMarker();
            sender.sendMessage("Deleted circle id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
        }
        else {
            sender.sendMessage("<label> or id:<circle-id> required");
        }
        return true;
    }
    private static boolean processUpdateCircle(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        String id, setid, label, newlabel, markup;
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            id = parms.get(ARG_ID);
            label = parms.get(ARG_LABEL);
            markup = parms.get(ARG_MARKUP);
            setid = parms.get(ARG_SET);
            if((id == null) && (label == null)) {
                sender.sendMessage("<label> or id:<area-id> required");
                return true;
            }
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid set - " + setid);
                return true;
            }
            CircleMarker marker;
            if(id != null) {
                marker = set.findCircleMarker(id);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: circle not found - " + id);
                    return true;
                }
            }
            else {
                marker = set.findCircleMarkerByLabel(label);
                if(marker == null) {    /* No marker */
                    sender.sendMessage("Error: circle not found - " + label);
                    return true;
                }
            }
            newlabel = parms.get(ARG_NEWLABEL);
            if(newlabel != null) {    /* Label set? */
                marker.setLabel(newlabel, "true".equals(markup));
            }
            else if(markup != null) {
                marker.setLabel(marker.getLabel(), "true".equals(markup));
            }
            if(!processCircleArgs(sender,marker, parms))
                return true;
            sender.sendMessage("Updated circle id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
        }
        else {
            sender.sendMessage("<label> or id:<circle-id> required");
        }
        return true;
    }

    private static MarkerDescription findMarkerDescription(DynmapCommandSender sender, Map<String, String> parms) {
        MarkerDescription md = null;
        String id, setid, label, type;
        id = parms.get(ARG_ID);
        label = parms.get(ARG_LABEL);
        setid = parms.get(ARG_SET);
        if((id == null) && (label == null)) {
            sender.sendMessage("<label> or id:<area-id> required");
            return null;
        }
        type = parms.get(ARG_TYPE);
        if (type == null) type = "icon";            
        if(setid == null) {
            setid = MarkerSet.DEFAULT;
        }
        MarkerSet set = api.getMarkerSet(setid);
        if(set == null) {
            sender.sendMessage("Error: invalid set - " + setid);
            return null;
        }
        if (id != null) {
            if (type.equals("icon")) {
                md = set.findMarker(id);
            }
            else if (type.equals("area")) {
                md = set.findAreaMarker(id);
            }
            else if (type.equals("circle")) {
                md = set.findCircleMarker(id);
            }
            else if (type.equals("line")) {
                md = set.findPolyLineMarker(id);
            }
            else {
                sender.sendMessage("Error: invalid type - " + type);
                return null;
            }
            if(md == null) {    /* No marker */
                sender.sendMessage("Error: marker not found - " + id);
                return null;
            }
        }
        else {
            if (type.equals("icon")) {
                md = set.findMarkerByLabel(label);
            }
            else if (type.equals("area")) {
                md = set.findAreaMarkerByLabel(label);
            }
            else if (type.equals("circle")) {
                md = set.findCircleMarkerByLabel(label);
            }
            else if (type.equals("line")) {
                md = set.findPolyLineMarkerByLabel(label);
            }
            else {
                sender.sendMessage("Error: invalid type - " + type);
                return null;
            }
            if(md == null) {    /* No marker */
                sender.sendMessage("Error: marker not found - " + label);
                return null;
            }
        }
        return md;
    }
    /** Process getdesc for given item */
    private static boolean processGetDesc(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            
            MarkerDescription md = findMarkerDescription(sender, parms);
            if (md == null) {
                return true;
            }
            String desc = md.getDescription();
            if (desc == null) {
                sender.sendMessage("<null>");
            }
            else {
                sender.sendMessage(desc);
            }
        }
        else {
            sender.sendMessage("<label> or id:<id> required");
        }
        return true;
    }
    /** Process resetdesc for given item */
    private static boolean processResetDesc(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            
            MarkerDescription md = findMarkerDescription(sender, parms);
            if (md == null) {
                return true;
            }
            md.setDescription(null);
            sender.sendMessage("Description cleared");
        }
        else {
            sender.sendMessage("<label> or id:<id> required");
        }
        return true;
    }
    /** Process appenddesc for given item */
    private static boolean processAppendDesc(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            
            MarkerDescription md = findMarkerDescription(sender, parms);
            if (md == null) {
                return true;
            }
            String desc = parms.get(ARG_DESC);
            if (desc == null) {
                sender.sendMessage("Error: no 'desc:' parameter");
                return true;
            }
            String d = md.getDescription();
            if (d == null) {
                d = desc + "\n";
            }
            else {
                d = d + desc + "\n";
            }
            md.setDescription(d);
            
            sender.sendMessage(md.getDescription());
        }
        else {
            sender.sendMessage("<label> or id:<id> required");
        }
        return true;
    }
    /** Process getlabel for given item */
    private static boolean processGetLabel(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            
            MarkerDescription md = findMarkerDescription(sender, parms);
            if (md == null) {
                return true;
            }
            String desc = md.getLabel();
            if (desc == null) {
                sender.sendMessage("<null>");
            }
            else {
                sender.sendMessage(desc);
            }
        }
        else {
            sender.sendMessage("<label> or id:<id> required");
        }
        return true;
    }
    /** Process importdesc for given item */
    private static boolean processImportDesc(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            
            MarkerDescription md = findMarkerDescription(sender, parms);
            if (md == null) {
                return true;
            }
            String f = parms.get(ARG_FILE);
            if (f == null) {
                sender.sendMessage("Error: no '" + ARG_FILE + "' parameter");
                return true;
            }
            FileReader fr = null;
            String val = null;
            try {
                fr = new FileReader(f);
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[512];
                int len;
                while ((len = fr.read(buf)) > 0) {
                    sb.append(buf, 0, len);
                }
                val = sb.toString();
            } catch (FileNotFoundException fnfx) {
                sender.sendMessage("Error: file '" + f + "' not found");
                return true;
            } catch (IOException iox) {
                sender.sendMessage("Error reading file '" + f + "'");
                return true;
            } finally {
                if (fr != null) {
                    try { fr.close(); } catch (IOException iox) {}
                }
            }
            md.setDescription(val);
            
            sender.sendMessage("Description imported from '" + f + "'");
        }
        else {
            sender.sendMessage("<label> or id:<id> required");
        }
        return true;
    }
    /** Process importlabel for given item */
    private static boolean processImportLabel(DynmapCore plugin, DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        if(args.length > 1) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            
            MarkerDescription md = findMarkerDescription(sender, parms);
            if (md == null) {
                return true;
            }
            String f = parms.get(ARG_FILE);
            if (f == null) {
                sender.sendMessage("Error: no '" + ARG_FILE + "' parameter");
                return true;
            }
            FileReader fr = null;
            String val = null;
            try {
                fr = new FileReader(f);
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[512];
                int len;
                while ((len = fr.read(buf)) > 0) {
                    sb.append(buf, 0, len);
                }
                val = sb.toString();
            } catch (FileNotFoundException fnfx) {
                sender.sendMessage("Error: file '" + f + "' not found");
                return true;
            } catch (IOException iox) {
                sender.sendMessage("Error reading file '" + f + "'");
                return true;
            } finally {
                if (fr != null) {
                    try { fr.close(); } catch (IOException iox) {}
                }
            }
            md.setLabel(val, true);
            
            sender.sendMessage("Label with markup imported from '" + f + "'");
        }
        else {
            sender.sendMessage("<label> or id:<id> required");
        }
        return true;
    }
    /**
     * Write markers file for given world
     */
    private void writeMarkersFile(final String wname) {
        Map<String, Object> markerdata = new HashMap<String, Object>();
                
        final Map<String, Object> worlddata = new HashMap<String, Object>();
        worlddata.put("timestamp", Long.valueOf(System.currentTimeMillis()));   /* Add timestamp */

        for(MarkerSet ms : markersets.values()) {
            HashMap<String, Object> msdata = new HashMap<String, Object>();
            msdata.put("label", ms.getMarkerSetLabel());
            msdata.put("hide", ms.getHideByDefault());
            msdata.put("layerprio", ms.getLayerPriority());
            if (ms.getMinZoom() >= 0) {
                msdata.put("minzoom", ms.getMinZoom());
            }
            if (ms.getMaxZoom() >= 0) {
                msdata.put("maxzoom", ms.getMaxZoom());
            }
            if(ms.getLabelShow() != null) {
                msdata.put("showlabels", ms.getLabelShow());
            }
            HashMap<String, Object> markers = new HashMap<String, Object>();
            for(Marker m : ms.getMarkers()) {
                if(m.getWorld().equals(wname) == false) continue;
                
                HashMap<String, Object> mdata = new HashMap<String, Object>();
                mdata.put("x", m.getX());
                mdata.put("y", m.getY());
                mdata.put("z", m.getZ());
                MarkerIcon mi = m.getMarkerIcon();
                if(mi == null)
                    mi = MarkerAPIImpl.getMarkerIconImpl(MarkerIcon.DEFAULT);
                mdata.put("icon", mi.getMarkerIconID());
                mdata.put("dim", mi.getMarkerIconSize().getSize());
                mdata.put("label", Client.sanitizeHTML(m.getLabel()));
                mdata.put("markup", m.isLabelMarkup());
                if(m.getDescription() != null)
                    mdata.put("desc", Client.sanitizeHTML(m.getDescription()));
                if (m.getMinZoom() >= 0) {
                    mdata.put("minzoom", m.getMinZoom());
                }
                if (m.getMaxZoom() >= 0) {
                    mdata.put("maxzoom", m.getMaxZoom());
                }
                /* Add to markers */
                markers.put(m.getMarkerID(), mdata);
            }
            msdata.put("markers", markers); /* Add markers to set data */

            HashMap<String, Object> areas = new HashMap<String, Object>();
            for(AreaMarker m : ms.getAreaMarkers()) {
                if(m.getWorld().equals(wname) == false) continue;
                
                HashMap<String, Object> mdata = new HashMap<String, Object>();
                int cnt = m.getCornerCount();
                List<Double> xx = new ArrayList<Double>();
                List<Double> zz = new ArrayList<Double>();
                for(int i = 0; i < cnt; i++) {
                    xx.add(m.getCornerX(i));
                    zz.add(m.getCornerZ(i));
                }
                mdata.put("x", xx);
                mdata.put("ytop", m.getTopY());
                mdata.put("ybottom", m.getBottomY());
                mdata.put("z", zz);
                mdata.put("color", String.format("#%06X", m.getLineColor()));
                mdata.put("fillcolor", String.format("#%06X", m.getFillColor()));
                mdata.put("opacity", m.getLineOpacity());
                mdata.put("fillopacity", m.getFillOpacity());
                mdata.put("weight", m.getLineWeight());
                mdata.put("label", Client.sanitizeHTML(m.getLabel()));
                mdata.put("markup", m.isLabelMarkup());
                if(m.getDescription() != null)
                    mdata.put("desc", Client.sanitizeHTML(m.getDescription()));
                if (m.getMinZoom() >= 0) {
                    mdata.put("minzoom", m.getMinZoom());
                }
                if (m.getMaxZoom() >= 0) {
                    mdata.put("maxzoom", m.getMaxZoom());
                }
                /* Add to markers */
                areas.put(m.getMarkerID(), mdata);
            }
            msdata.put("areas", areas); /* Add areamarkers to set data */

            HashMap<String, Object> lines = new HashMap<String, Object>();
            for(PolyLineMarker m : ms.getPolyLineMarkers()) {
                if(m.getWorld().equals(wname) == false) continue;
                
                HashMap<String, Object> mdata = new HashMap<String, Object>();
                int cnt = m.getCornerCount();
                List<Double> xx = new ArrayList<Double>();
                List<Double> yy = new ArrayList<Double>();
                List<Double> zz = new ArrayList<Double>();
                for(int i = 0; i < cnt; i++) {
                    xx.add(m.getCornerX(i));
                    yy.add(m.getCornerY(i));
                    zz.add(m.getCornerZ(i));
                }
                mdata.put("x", xx);
                mdata.put("y", yy);
                mdata.put("z", zz);
                mdata.put("color", String.format("#%06X", m.getLineColor()));
                mdata.put("opacity", m.getLineOpacity());
                mdata.put("weight", m.getLineWeight());
                mdata.put("label", Client.sanitizeHTML(m.getLabel()));
                mdata.put("markup", m.isLabelMarkup());
                if(m.getDescription() != null)
                    mdata.put("desc", Client.sanitizeHTML(m.getDescription()));
                if (m.getMinZoom() >= 0) {
                    mdata.put("minzoom", m.getMinZoom());
                }
                if (m.getMaxZoom() >= 0) {
                    mdata.put("maxzoom", m.getMaxZoom());
                }
                /* Add to markers */
                lines.put(m.getMarkerID(), mdata);
            }
            msdata.put("lines", lines); /* Add polylinemarkers to set data */

            HashMap<String, Object> circles = new HashMap<String, Object>();
            for(CircleMarker m : ms.getCircleMarkers()) {
                if(m.getWorld().equals(wname) == false) continue;
                
                HashMap<String, Object> mdata = new HashMap<String, Object>();
                mdata.put("x", m.getCenterX());
                mdata.put("y", m.getCenterY());
                mdata.put("z", m.getCenterZ());
                mdata.put("xr", m.getRadiusX());
                mdata.put("zr", m.getRadiusZ());
                mdata.put("color", String.format("#%06X", m.getLineColor()));
                mdata.put("fillcolor", String.format("#%06X", m.getFillColor()));
                mdata.put("opacity", m.getLineOpacity());
                mdata.put("fillopacity", m.getFillOpacity());
                mdata.put("weight", m.getLineWeight());
                mdata.put("label", Client.sanitizeHTML(m.getLabel()));
                mdata.put("markup", m.isLabelMarkup());
                if(m.getDescription() != null)
                    mdata.put("desc", Client.sanitizeHTML(m.getDescription()));
                if (m.getMinZoom() >= 0) {
                    mdata.put("minzoom", m.getMinZoom());
                }
                if (m.getMaxZoom() >= 0) {
                    mdata.put("maxzoom", m.getMaxZoom());
                }
                /* Add to markers */
                circles.put(m.getMarkerID(), mdata);
            }
            msdata.put("circles", circles); /* Add circle markers to set data */

            markerdata.put(ms.getMarkerSetID(), msdata);    /* Add marker set data to world marker data */
        }
        worlddata.put("sets", markerdata);

        MapManager.scheduleDelayedJob(new Runnable() {
            public void run() {
                core.getDefaultMapStorage().setMarkerFile(wname, Json.stringifyJson(worlddata));
            }
        }, 0);
    }

    @Override
    public void triggered(DynmapWorld t) {
        /* Update markers for now-active world */
        dirty_worlds.add(t.getName());
    }

    /* Remove icon */
    static void removeIcon(MarkerIcon ico) {
        MarkerIcon def = api.getMarkerIcon(MarkerIcon.DEFAULT);
        /* Need to scrub all uses of this icon from markers */
        for(MarkerSet s : api.markersets.values()) {
            for(Marker m : s.getMarkers()) {
                if(m.getMarkerIcon() == ico) {
                    m.setMarkerIcon(def);    /* Set to default */
                }
            }
            Set<MarkerIcon> allowed = s.getAllowedMarkerIcons();
            if((allowed != null) && (allowed.contains(ico))) {
                s.removeAllowedMarkerIcon(ico);
            }
        }
        /* Remove files */
        File f = new File(api.markerdir, ico.getMarkerIconID() + ".png");
        f.delete();
        api.core.getDefaultMapStorage().setMarkerImage(ico.getMarkerIconID(), null);
        
        /* Remove from marker icons */
        api.markericons.remove(ico.getMarkerIconID());
        saveMarkers();
    }
    /**
     * Test if given player can see another player on the map (based on player sets and privileges).
     * @param player - player attempting to observe
     * @param player_to_see - player to be observed by 'player'
     * @return true if can be seen on map, false if cannot be seen
     */
    public boolean testIfPlayerVisible(String player, String player_to_see)
    {
        if(api == null) return false;
        /* Go through player sets - see if any are applicable */
        for(Entry<String, PlayerSetImpl> s : playersets.entrySet()) {
            PlayerSetImpl ps = s.getValue();
            if(!ps.isPlayerInSet(player_to_see)) { /* Is in set? */
                continue;
            }
            if(ps.isSymmetricSet() && ps.isPlayerInSet(player)) {   /* If symmetric, and observer is there */
                return true;
            }
            if(core.checkPermission(player, "playerset." + s.getKey())) {   /* If player has privilege */
                return true;
            }
        }
        return false;
    }
    /**
     * Get set of player visible to given player
     * @param player - player to check
     * @return set of visible players
     */
    public Set<String> getPlayersVisibleToPlayer(String player) {
        player = player.toLowerCase();
        HashSet<String> pset = new HashSet<String>();
        pset.add(player);
        /* Go through player sets - see if any are applicable */
        for(Entry<String, PlayerSetImpl> s : playersets.entrySet()) {
            PlayerSetImpl ps = s.getValue();
            if(ps.isSymmetricSet() && ps.isPlayerInSet(player)) {   /* If symmetric, and observer is there */
                pset.addAll(ps.getPlayers());
            }
            else if(core.checkPermission(player, "playerset." + s.getKey())) {   /* If player has privilege */
                pset.addAll(ps.getPlayers());
            }
        }
        return pset;
    }
    /**
     * Test if any markers with 'boost=true' intersect given map tile
     * @param w - world
     * @param perspective - perspective for transforming world to tile coordinates
     * @param tile_x - X coordinate of tile corner, in map coords
     * @param tile_y - Y coordinate of tile corner, in map coords
     * @param tile_dim - Tile dimension, in map units
     * @return true if intersected, false if not
     */
    public static boolean testTileForBoostMarkers(DynmapWorld w, HDPerspective perspective, double tile_x, double tile_y, double tile_dim) {
        if (api == null) return false;
        for(MarkerSetImpl ms : api.markersets.values()) {
            if(ms.testTileForBoostMarkers(w, perspective, tile_x, tile_y, tile_dim)) {
                return true;
            }
        }
        return false;
    }
    /**
     * Build entered marker set based on given location
     * @param worldid - world
     * @param x
     * @param y
     * @param z
     * @param entered
     */
    public static void getEnteredMarkers(String worldid, double x, double y, double z, Set<EnterExitMarker> entered) {
        if (api == null) return;
        for(MarkerSetImpl ms : api.markersets.values()) {
        	ms.addEnteredMarkers(entered, worldid, x, y, z);
        }
    }
}
