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
import java.util.TreeMap;
import java.util.TreeSet;

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
        "left", "lightbulb", "lighthouse", "lock", "minecart", "orangeflag", "pin", "pinkflag", "pirateflag", "pointdown", "pointleft",
        "pointright", "pointup", "portal", "purpleflag", "queen", "redflag", "right", "ruby", "scales", "skull", "shield", "sign",
        "silvermedal", "silverstar", "star", "sun", "temple", "theater", "tornado", "tower", "tree", "truck", "up",
        "walk", "warning", "world", "wrench", "yellowflag"
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
        api.markerdir = new File(plugin.getDataFolder(), "markers");
        if(api.markerdir.isDirectory() == false) {
            if(api.markerdir.mkdirs() == false) {   /* Create directory if needed */
                Log.severe("Error creating markers directory - " + api.markerdir.getPath());
            }
        }
        api.markertiledir = new File(DynmapPlugin.tilesDirectory, "_markers_");
        if(api.markertiledir.isDirectory() == false) {
            if(api.markertiledir.mkdirs() == false) {   /* Create directory if needed */
                Log.severe("Error creating markers directory - " + api.markertiledir.getPath());
            }
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

    void publishMarkerIcon(MarkerIcon ico) {
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
        "add", "movehere", "update", "delete", "list", "icons", "addset", "updateset", "deleteset", "listsets", "addicon", "updateicon",
        "deleteicon"
    }));
    private static final String ARG_LABEL = "label";
    private static final String ARG_ID = "id";
    private static final String ARG_NEWLABEL = "newlabel";
    private static final String ARG_FILE = "file";
    private static final String ARG_HIDE = "hide";
    private static final String ARG_ICON = "icon";
    private static final String ARG_SET = "set";
    private static final String ARG_PRIO = "prio";

    /* Parse argument strings : handle 'attrib:value' and quoted strings */
    private static Map<String,String> parseArgs(String[] args, CommandSender snd) {
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
    

    
    public static boolean onCommand(DynmapPlugin plugin, CommandSender sender, Command cmd, String commandLabel, String[] args) {
        String id, setid, file, label, newlabel, iconid, prio;
        
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
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                iconid = parms.get(ARG_ICON);
                setid = parms.get(ARG_SET);
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                
                Location loc = player.getLocation();
                /* Fill in defaults for missing parameters */
                if(iconid == null) {
                    iconid = MarkerIcon.DEFAULT;
                }
                if(setid == null) {
                    setid = MarkerSet.DEFAULT;
                }
                /* Add new marker */
                MarkerSet set = api.getMarkerSet(setid);
                if(set == null) {
                    sender.sendMessage("Error: invalid marker set - " + setid);
                    return true;
                }
                MarkerIcon ico = api.getMarkerIcon(iconid);
                if(ico == null) {
                    sender.sendMessage("Error: invalid icon - " + iconid);
                    return true;
                }
                Marker m = set.createMarker(id, label, 
                        loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), ico, true);
                if(m == null) {
                    sender.sendMessage("Error creating marker");
                }
                else {
                    sender.sendMessage("Added marker id:'" + m.getMarkerID() + "' (" + m.getLabel() + ") to set '" + set.getMarkerSetID() + "'");
                }
            }
            else {
                sender.sendMessage("Marker label required");
            }
        }
        /* Update position of bookmark - must have ID parameter */
        else if(c.equals("movehere") && plugin.checkPlayerPermission(sender, "marker.movehere")) {
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
                    sender.sendMessage("Error: invalid marker set - " + setid);
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
                Location loc = player.getLocation();
                marker.setLocation(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
                sender.sendMessage("Updated location of marker id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
            }
            else {
                sender.sendMessage("<label> or id:<marker-id> required");
            }
        }
        /* Update other attributes of marker - must have ID parameter */
        else if(c.equals("update") && plugin.checkPlayerPermission(sender, "marker.update")) {
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
                    sender.sendMessage("Error: invalid marker set - " + setid);
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
                    marker.setLabel(newlabel);
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
                sender.sendMessage("Updated marker id:" + marker.getMarkerID() + " (" + marker.getLabel() + ")");
            }
            else {
                sender.sendMessage("<label> or id:<marker-id> required");
            }
        }
        /* Delete marker - must have ID parameter */
        else if(c.equals("delete") && plugin.checkPlayerPermission(sender, "marker.delete")) {
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
                    sender.sendMessage("Error: invalid marker set - " + setid);
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
        }
        /* List markers */
        else if(c.equals("list") && plugin.checkPlayerPermission(sender, "marker.list")) {
            /* Parse arguements */
            Map<String,String> parms = parseArgs(args, sender);
            if(parms == null) return true;
            setid = parms.get(ARG_SET);
            if(setid == null) {
                setid = MarkerSet.DEFAULT;
            }
            MarkerSet set = api.getMarkerSet(setid);
            if(set == null) {
                sender.sendMessage("Error: invalid marker set - " + setid);
                return true;
            }
            Set<Marker> markers = set.getMarkers();
            TreeMap<String, Marker> sortmarkers = new TreeMap<String, Marker>();
            for(Marker m : markers) {
                sortmarkers.put(m.getMarkerID(), m);
            }
            for(String s : sortmarkers.keySet()) {
                Marker m = sortmarkers.get(s);
                sender.sendMessage(m.getMarkerID() + ": label:\"" + m.getLabel() + "\", set:" + m.getMarkerSet().getMarkerSetID() + 
                                   ", world:" + m.getWorld() + ", x:" + m.getX() + ", y:" + m.getY() + ", z:" + m.getZ());
            }
        }
        /* List icons */
        else if(c.equals("icons") && plugin.checkPlayerPermission(sender, "marker.icons")) {
            Set<String> iconids = new TreeSet<String>(api.markericons.keySet());
            for(String s : iconids) {
                MarkerIcon ico = api.markericons.get(s);
                sender.sendMessage(ico.getMarkerIconID() + ": label:\"" + ico.getMarkerIconLabel() + "\", builtin:" + ico.isBuiltIn());
            }
        }
        else if(c.equals("addset") && plugin.checkPlayerPermission(sender, "marker.addset")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                prio = parms.get(ARG_PRIO);
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
                    sender.sendMessage("Error creating marker set");
                }
                else {
                    String h = parms.get(ARG_HIDE);
                    if((h != null) && (h.equals("true")))
                        set.setHideByDefault(true);
                    if(prio != null) {
                        try {
                            set.setLayerPriority(Integer.valueOf(prio));
                        } catch (NumberFormatException nfx) {
                            sender.sendMessage("Invalid priority: " + prio);
                        }
                    }
                    sender.sendMessage("Added marker set id:'" + set.getMarkerSetID() + "' (" + set.getMarkerSetLabel() + ")");
                }
            }
            else {
                sender.sendMessage("<label> or id:<set-id> required");
            }
        }
        else if(c.equals("updateset") && plugin.checkPlayerPermission(sender, "marker.updateset")) {
            if(args.length > 1) {
                /* Parse arguements */
                Map<String,String> parms = parseArgs(args, sender);
                if(parms == null) return true;
                id = parms.get(ARG_ID);
                label = parms.get(ARG_LABEL);
                prio = parms.get(ARG_PRIO);
                if((id == null) && (label == null)) {
                    sender.sendMessage("<label> or id:<marker-id> required");
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
                if(prio != null) {
                    try {
                        set.setLayerPriority(Integer.valueOf(prio));
                    } catch (NumberFormatException nfx) {
                        sender.sendMessage("Invalid priority: " + prio);
                    }
                }
                sender.sendMessage("Marker set '" + set.getMarkerSetID() + "' updated");
            }
            else {
                sender.sendMessage("<label> or id:<set-id> required");
            }
        }
        else if(c.equals("deleteset") && plugin.checkPlayerPermission(sender, "marker.deleteset")) {
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
                sender.sendMessage("Deleted marker set");
            }
            else {
                sender.sendMessage("<label> or id:<set-id> required");
            }
        }
        /* List sets */
        else if(c.equals("listsets") && plugin.checkPlayerPermission(sender, "marker.listsets")) {
            Set<String> setids = new TreeSet<String>(api.markersets.keySet());
            for(String s : setids) {
                MarkerSet set = api.markersets.get(s);
                sender.sendMessage(set.getMarkerSetID() + ": label:\"" + set.getMarkerSetLabel() + "\", hide:" + set.getHideByDefault() + ", prio:" + set.getLayerPriority());
            }
        }
        /* Add new icon */
        else if(c.equals("addicon") && plugin.checkPlayerPermission(sender, "marker.addicon")) {
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
                MarkerIcon ico = api.getMarkerIcon(id);
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
        }
        else if(c.equals("updateicon") && plugin.checkPlayerPermission(sender, "marker.updateicon")) {
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
                    ico = api.getMarkerIcon(id);
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
        }
        else if(c.equals("deleteicon") && plugin.checkPlayerPermission(sender, "marker.deleteicon")) {
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
                    MarkerIcon ico = api.getMarkerIcon(id);
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
            msdata.put("hide", ms.getHideByDefault());
            msdata.put("layerprio", ms.getLayerPriority());
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
        f = new File(api.markertiledir, ico.getMarkerIconID() + ".png");
        f.delete();
        
        /* Remove from marker icons */
        api.markericons.remove(ico.getMarkerIconID());
        saveMarkers();
    }

}
