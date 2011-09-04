package org.dynmap.markers.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;
import org.bukkit.util.config.ConfigurationNode;
import org.dynmap.Client;
import org.dynmap.ClientUpdateEvent;
import org.dynmap.DynmapPlugin;
import org.dynmap.DynmapWorld;
import org.dynmap.Event;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.Client.ComponentMessage;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.web.Json;

/**
 * Implementation class for MarkerAPI - should not be called directly
 */
public class MarkerAPIImpl implements MarkerAPI, Event.Listener<DynmapWorld> {
    private File markerpersist;
    private File markerpersist_old;
    private File markerdir; /* Local store for markers (internal) */
    private File markertiledir; /* Marker directory for web server (under tiles) */
    private HashMap<String, MarkerIconImpl> markericons = new HashMap<String, MarkerIconImpl>();
    private HashMap<String, MarkerSetImpl> markersets = new HashMap<String, MarkerSetImpl>();
    
    private Server server;
    static MarkerAPIImpl api;

    /* Built-in icons */
    private static final String[] builtin_icons = {
        "anchor", "bank", "basket", "beer", "bighouse", "blueflag", "bomb", "bookshelf", "bricks", "bronzemedal", "bronzestar",
        "building", "cake", "camera", "cart", "caution", "chest", "church", "coins", "comment", "compass", "construction",
        "cross", "cup", "cutlery", "default", "diamond", "dog", "door", "down", "drink", "exclamation", "factory",
        "fire", "flower", "gear", "goldmedal", "goldstar", "greenflag", "hammer", "heart", "house", "key", "king",
        "left", "lightbulb", "lighthouse", "lock", "orangeflag", "pin", "pinkflag", "pirateflag", "pointdown", "pointleft",
        "pointright", "pointup", "purpleflag", "queen", "redflag", "right", "ruby", "scales", "skull", "shield", "sign",
        "silvermedal", "silverstar", "star", "sun", "temple", "theater", "tornado", "tower", "tree", "truck", "up",
        "walk", "warning", "world", "wrench", "yellowflag"
    };

    /* Component messages for client updates */
    public static class MarkerComponentMessage extends ComponentMessage {
        public String ctype = "markers";
    }
    
    public static class MarkerUpdated extends MarkerComponentMessage {
        public String msg;
        public int x, y, z;
        public String id;
        public String label;
        public String icon;
        public String set;
        
        public MarkerUpdated(Marker m, boolean deleted) {
            this.id = m.getMarkerID();
            this.label = m.getLabel();
            this.x = m.getX();
            this.y = m.getY();
            this.z = m.getZ();
            this.set = m.getMarkerSet().getMarkerSetID();
            this.icon = m.getMarkerIcon().getMarkerIconID();
            if(deleted) 
                msg = "markerdeleted";
            else
                msg = "markerupdated";
        }
    }
    
    public static class MarkerSetUpdated extends MarkerComponentMessage {
        public String msg;
        public String id;
        public String label;
        public MarkerSetUpdated(MarkerSet markerset, boolean deleted) {
            this.id = markerset.getMarkerSetID();
            this.label = markerset.getMarkerSetLabel();
            if(deleted)
                msg = "setdeleted";
            else
                msg = "setupdated";
        }
    }

    /**
     * Singleton initializer
     */
    public static MarkerAPIImpl initializeMarkerAPI(DynmapPlugin plugin) {
        if(api != null) {
            api.cleanup(plugin);
        }
        api = new MarkerAPIImpl();
        api.server = plugin.getServer();
        /* Initialize persistence file name */
        api.markerpersist = new File(plugin.getDataFolder(), "markers.yml");
        api.markerpersist_old = new File(plugin.getDataFolder(), "markers.yml.old");
        /* Load persistence */
        api.loadMarkers();
        /* Fill in default icons and sets, if needed */
        for(int i = 0; i < builtin_icons.length; i++) {
            String id = builtin_icons[i];
            if(api.getMarkerIcon(id) == null) {
                api.createBuiltinMarkerIcon(id, id);
            }
        }
        if(api.getMarkerSet(MarkerSet.DEFAULT) == null) {
            api.createMarkerSet(MarkerSet.DEFAULT, "Markers", null, true);
        }
        /* Build paths for markers */
        api.markerdir = new File(plugin.getDataFolder(), "markers");
        if(api.markerdir.mkdirs() == false) {   /* Create directory if needed */
            Log.severe("Error creating markers directory - " + api.markerdir.getPath());
        }
        api.markertiledir = new File(DynmapPlugin.tilesDirectory, "_markers_");
        if(api.markertiledir.mkdirs() == false) {   /* Create directory if needed */
            Log.severe("Error creating markers directory - " + api.markertiledir.getPath());
        }
        /* Now publish marker files to the tiles directory */
        for(MarkerIcon ico : api.getMarkerIcons()) {
            api.publishMarkerIcon(ico);
        }
        /* Freshen files */
        api.freshenMarkerFiles();
        /* Add listener so we update marker files for other worlds as they become active */
        plugin.events.addListener("worldactivated", api);
        
        return api;
    }
    
    /**
     * Cleanup
     */
    public void cleanup(DynmapPlugin plugin) {
        plugin.events.removeListener("worldactivated", api);

        for(MarkerIconImpl icn : markericons.values())
            icn.cleanup();
        markericons.clear();
        for(MarkerSetImpl set : markersets.values())
            set.cleanup();
        markersets.clear();
    }

    private MarkerIcon createBuiltinMarkerIcon(String id, String label) {
        if(markericons.containsKey(id)) return null;    /* Exists? */
        MarkerIconImpl ico = new MarkerIconImpl(id, label, true);
        markericons.put(id, ico);   /* Add to set */
        return ico;
    }

    private void publishMarkerIcon(MarkerIcon ico) {
        byte[] buf = new byte[512];
        InputStream in = null;
        File infile = new File(markerdir, ico.getMarkerIconID() + ".png");  /* Get source file name */
        File outfile = new File(markertiledir, ico.getMarkerIconID() + ".png"); /* Destination */
        OutputStream out = null;
        try {
            out = new FileOutputStream(outfile);
        } catch (IOException iox) {
            Log.severe("Cannot write marker to tilespath - " + outfile.getPath());
            return;
        }
        if(ico.isBuiltIn()) {
            in = getClass().getResourceAsStream("/markers/" + ico.getMarkerIconID() + ".png");
        }
        else if(infile.canRead()) {  /* If it exists and is readable */
            try {
                in = new FileInputStream(infile);   
            } catch (IOException iox) {
                Log.severe("Error opening marker " + infile.getPath() + " - " + iox);
            }
        }
        if(in == null) {    /* Not found, use default marker */
            in = getClass().getResourceAsStream("/markers/marker.png");
            if(in == null)
                return;
        }
        /* Copy to destination */
        try {
            int len;
            while((len = in.read(buf)) > 0) {
               out.write(buf, 0, len); 
            }
        } catch (IOException iox) {
            Log.severe("Error writing marker to tilespath - " + outfile.getPath());
        } finally {
            if(in != null) try { in.close(); } catch (IOException x){}
            if(out != null) try { out.close(); } catch (IOException x){}
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

    @Override
    public MarkerIcon createMarkerIcon(String id, String label, InputStream marker_png) {
        if(markericons.containsKey(id)) return null;    /* Exists? */
        MarkerIconImpl ico = new MarkerIconImpl(id, label, false);
        /* Copy icon resource into marker directory */
        File f = new File(markerdir, id + ".png");
        FileOutputStream fos = null;
        try {
            byte[] buf = new byte[512];
            int len;
            fos = new FileOutputStream(f);
            while((len = marker_png.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
        } catch (IOException iox) {
            Log.severe("Error copying marker - " + f.getPath());
            return null;
        } finally {
            if(fos != null) try { fos.close(); } catch (IOException x) {}
        }
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
    
    /**
     * Save persistence for markers
     */
    static void saveMarkers() {
        if(api != null) {
            Configuration conf = new Configuration(api.markerpersist);  /* Make configuration object */
            /* First, save icon definitions */
            HashMap<String, Object> icons = new HashMap<String,Object>();
            for(String id : api.markericons.keySet()) {
                MarkerIconImpl ico = api.markericons.get(id);
                Map<String,Object> dat = ico.getPersistentData();
                if(dat != null) {
                    icons.put(id, dat);
                }
            }
            conf.setProperty("icons", icons);
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
            conf.setProperty("sets", sets);
            /* And shift old file file out */
            if(api.markerpersist_old.exists()) api.markerpersist_old.delete();
            if(api.markerpersist.exists()) api.markerpersist.renameTo(api.markerpersist_old);
            /* And write it out */
            if(!conf.save())
                Log.severe("Error writing markers - " + api.markerpersist.getPath());
            /* Refresh JSON files */
            api.freshenMarkerFiles();
        }
    }

    private void freshenMarkerFiles() {
        if(MapManager.mapman != null) {
            for(DynmapWorld w : MapManager.mapman.worlds) {
                writeMarkersFile(w.world.getName());
            }
        }
    }
    
    /**
     * Load persistence
     */
    private boolean loadMarkers() {        
        Configuration conf = new Configuration(api.markerpersist);  /* Make configuration object */
        conf.load();    /* Load persistence */
        /* Get icons */
        Map<String,ConfigurationNode> icons = conf.getNodes("icons");
        if(icons == null) return false;
        for(String id : icons.keySet()) {
            MarkerIconImpl ico = new MarkerIconImpl(id);
            if(ico.loadPersistentData(icons.get(id))) {
                markericons.put(id, ico);
            }
        }
        /* Get marker sets */
        Map<String,ConfigurationNode> sets = conf.getNodes("sets");
        if(sets != null) {
            for(String id: sets.keySet()) {
                MarkerSetImpl set = new MarkerSetImpl(id);
                if(set.loadPersistentData(sets.get(id))) {
                    markersets.put(id, set);
                }
            }
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
        Log.info("markerUpdated(" + marker.getMarkerID() + "," + update + ")");
        /* Freshen marker file for the world for this marker */
        if(api != null)
            api.writeMarkersFile(marker.getWorld());
        /* Enqueue client update */
        if(MapManager.mapman != null)
            MapManager.mapman.pushUpdate(marker.getWorld(), new MarkerUpdated(marker, update == MarkerUpdate.DELETED));
    }
    /**
     * Signal marker set update
     * @param markerset - updated marker set
     * @param update - type of update
     */
    static void markerSetUpdated(MarkerSetImpl markerset, MarkerUpdate update) {
        Log.info("markerSetUpdated(" + markerset.getMarkerSetID() + "," + update + ")");
        /* Freshen all marker files */
        if(api != null)
            api.freshenMarkerFiles();
        /* Enqueue client update */
        if(MapManager.mapman != null)
            MapManager.mapman.pushUpdate(new MarkerSetUpdated(markerset, update == MarkerUpdate.DELETED));
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

    private static final Set<String> commands = new HashSet<String>(Arrays.asList(new String[] {
        "add", "update", "delete", "list" 
    }));

    public static boolean onCommand(DynmapPlugin plugin, CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if(api == null) {
            sender.sendMessage("Markers component is not enabled.");
            return false;
        }
        if(args.length == 0)
            return false;
        Player player = null;
        if (sender instanceof Player)
            player = (Player) sender;
        /* Check if valid command */
        String c = args[0];
        if (!commands.contains(c)) {
            return false;
        }
        /* Process commands */
        if(c.equals("add") && plugin.checkPlayerPermission(sender, "marker.add")) {
            if(player == null) {
                sender.sendMessage("Command can only be used by player");
            }
            else if(args.length > 1) {
                String lbl = args[1];
                if(lbl.charAt(0) == '"') {  /* Starts with doublequote */
                    lbl = lbl.substring(1); /* Trim it off */
                    int idx = 2;
                    while(lbl.indexOf('"') < 0) {
                        if(idx < args.length) {
                            lbl = lbl + " " + args[idx];
                            idx++;
                        }
                        else {
                            break;
                        }
                    }
                    idx = lbl.indexOf('"');
                    if(idx >= 0) lbl = lbl.substring(0, idx);
                }
                Location loc = player.getLocation();
                /* Add new marker (generic ID and default set) */
                MarkerSet set = api.getMarkerSet(MarkerSet.DEFAULT);
                MarkerIcon ico = api.getMarkerIcon(MarkerIcon.DEFAULT);
                Marker m = set.createMarker(null, lbl, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), ico, true);
                if(m == null) {
                    sender.sendMessage("Error creating marker");
                }
                else {
                    sender.sendMessage("Added marker id='" + m.getMarkerID() + "' (" + m.getLabel() + ") to marker set " + set.getMarkerSetID());
                }
            }
            else {
                sender.sendMessage("Marker label required");
            }
        }
        else {
            return false;
        }
        return true;
    }

    /**
     * Write markers file for given world
     */
    public void writeMarkersFile(String wname) {
        Map<String, Object> markerdata = new HashMap<String, Object>();

        File f = new File(markertiledir, "marker_" + wname + ".json");
        File fnew = new File(markertiledir, "marker_" + wname + ".json.new");
                
        Map<String, Object> worlddata = new HashMap<String, Object>();
        worlddata.put("timestamp", Long.valueOf(System.currentTimeMillis()));   /* Add timestamp */

        for(MarkerSet ms : markersets.values()) {
            HashMap<String, Object> msdata = new HashMap<String, Object>();
            msdata.put("label", ms.getMarkerSetLabel());
            HashMap<String, Object> markers = new HashMap<String, Object>();
            for(Marker m : ms.getMarkers()) {
                if(m.getWorld().equals(wname) == false) continue;
                
                HashMap<String, Object> mdata = new HashMap<String, Object>();
                mdata.put("x", m.getX());
                mdata.put("y", m.getY());
                mdata.put("z", m.getZ());
                mdata.put("icon", m.getMarkerIcon().getMarkerIconID());
                mdata.put("label", m.getLabel());
                /* Add to markers */
                markers.put(m.getMarkerID(), mdata);
            }
            msdata.put("markers", markers); /* Add markers to set data */
            
            markerdata.put(ms.getMarkerSetID(), msdata);    /* Add marker set data to world marker data */
        }
        worlddata.put("sets", markerdata);

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(fnew);
            fos.write(Json.stringifyJson(worlddata).getBytes());
        } catch (FileNotFoundException ex) {
            Log.severe("Exception while writing JSON-file.", ex);
        } catch (IOException ioe) {
            Log.severe("Exception while writing JSON-file.", ioe);
        } finally {
            if(fos != null) try { fos.close(); } catch (IOException x) {}
            if(f.exists()) f.delete();
            fnew.renameTo(f);
        }
    }

    @Override
    public void triggered(DynmapWorld t) {
        /* Update markers for now-active world */
        writeMarkersFile(t.world.getName());
    }
}
