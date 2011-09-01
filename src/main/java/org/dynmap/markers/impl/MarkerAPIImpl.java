package org.dynmap.markers.impl;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;
import org.bukkit.util.config.ConfigurationNode;
import org.dynmap.DynmapPlugin;
import org.dynmap.Log;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

/**
 * Implementation class for MarkerAPI - should not be called directly
 */
public class MarkerAPIImpl implements MarkerAPI {
    private File markerpersist;
    private HashMap<String, MarkerIconImpl> markericons = new HashMap<String, MarkerIconImpl>();
    private HashMap<String, MarkerSetImpl> markersets = new HashMap<String, MarkerSetImpl>();
    
    static MarkerAPIImpl api;
    
    /**
     * Singleton initializer
     */
    public static MarkerAPI initializeMarkerAPI(DynmapPlugin plugin) {
        if(api != null) {
            api.cleanup();
        }
        api = new MarkerAPIImpl();
        /* Initialize persistence file name */
        api.markerpersist = new File(plugin.getDataFolder(), "markers.yml");
        /* Load persistence */
        api.loadMarkers();
        /* Fill in default icons and sets, if needed */
        if(api.getMarkerIcon(MarkerIcon.DEFAULT) == null) {
            api.createMarkerIcon(MarkerIcon.DEFAULT, "Marker", null);
        }
        if(api.getMarkerSet(MarkerSet.DEFAULT) == null) {
            api.createMarkerSet(MarkerSet.DEFAULT, "Markers", null, true);
        }
        return api;
    }
    
    /**
     * Cleanup
     */
    private void cleanup() {
        for(MarkerIconImpl icn : markericons.values())
            icn.cleanup();
        markericons.clear();
        for(MarkerSetImpl set : markersets.values())
            set.cleanup();
        markersets.clear();
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
    public MarkerIcon createMarkerIcon(String id, String label, File markerfile) {
        if(markericons.containsKey(id)) return null;    /* Exists? */
        MarkerIconImpl ico = new MarkerIconImpl(id, label);
        
        markericons.put(id, ico);   /* Add to set */
        
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
            Log.info("saveMarkers()");
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
            /* And write it out */
            if(!conf.save())
                Log.severe("Error writing markers - " + api.markerpersist.getPath());
        }
    }
    
    /**
     * Load persistence
     */
    private boolean loadMarkers() {
        cleanup();
        
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
    }
    /**
     * Signal marker set update
     * @param markerset - updated marker set
     * @param update - type of update
     */
    static void markerSetUpdated(MarkerSetImpl markerset, MarkerUpdate update) {
        Log.info("markerSetUpdated(" + markerset.getMarkerSetID() + "," + update + ")");
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
        if(api == null) return false;
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
}
