package org.dynmap.markers.impl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import org.dynmap.DynmapCore;
import org.dynmap.common.DynmapChatColor;
import org.dynmap.common.DynmapListenerManager;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

public class MarkerSignManager {

    private static MarkerSignManager mgr = null;
    private static DynmapCore plugin = null;
    private static String defSignSet = null;

    private static class SignRec {
        String wname;
        int x, y, z;
        Marker m;
    }
    
    private static class SignListener implements DynmapListenerManager.SignChangeEventListener, Runnable {
        @Override
        public void signChangeEvent(String material, String wname, int x, int y, int z, String[] lines, DynmapPlayer p) {
            if(mgr == null)
                return;			
			
            if(!lines[0].equalsIgnoreCase("[dynmap]")) {  /* If not dynmap sign, quit */
                return;
            }
			
            /* If allowed to do marker signs */
            if((p == null) || ((plugin != null) && (plugin.checkPlayerPermission(p, "marker.sign")))) {
                String id = getSignMarkerID(wname, x, y, z);  /* Get marker ID */
                String set = defSignSet;
                String icon = MarkerIcon.SIGN;
                String label = "";
                lines[0] = ""; /* Blank out [dynmap] */
                for(int i = 1; i < 4; i++) {    /* Check other lines for icon: or set: */
                    String v = plugin.getServer().stripChatColor(lines[i]);
                    if(v.startsWith("icon:")) { /* icon: */
                        icon = v.substring(5);
                        lines[i] = "";
                    }
                    else if(v.startsWith("set:")) { /* set: */
                        set = v.substring(4);
                        lines[i] = "";
                    }
                    else if(v.length() > 0) {
                        if(label.length() > 0) {
                            label = label + "<br/>";
                        }
                        label = label + escapeMarkup(v);
                    }
                }
                /* Get the set and see if the marker is already defined */
                MarkerSet ms = MarkerAPIImpl.api.getMarkerSet(set);
                if(ms == null) {
                    if(p != null) p.sendMessage("Bad marker set - [dynmap] sign invalid");
                    lines[0] = DynmapChatColor.RED + "<Bad Marker Set>";
                    return;
                }
                MarkerIcon mi = MarkerAPIImpl.api.getMarkerIcon(icon);  /* Get icon */
                if(mi == null) {
                    if(p != null) p.sendMessage("Bad marker icon - [dynmap] sign invalid");
                    lines[0] = DynmapChatColor.RED + "<Bad Marker Icon>";
                    return;
                }
                Marker marker = ms.findMarker(id);
                /* If exists, update it */
                if(marker != null) {
                    marker.setLabel(label, true);
                    marker.setMarkerIcon(mi);
                }
                else {  /* Make new marker */
                    marker = ms.createMarker(id, label, true, wname, (double)x + 0.5, (double)y + 0.5, (double)z + 0.5,
                                             mi, true);
                    if(marker == null) {
                        if(p != null) p.sendMessage("Bad marker - [dynmap] sign invalid");
                        lines[0] = DynmapChatColor.RED + "<Bad Marker>";
                        return;
                    }
                    if(sign_cache != null) {
                        SignRec r = new SignRec();
                        r.wname = wname;
                        r.x = x;
                        r.y = y;
                        r.z = z;
                        r.m = marker;
                        sign_cache.put(id,  r); /* Add to cache */
                    }
                }
            }
        }
        private HashMap<String, SignRec> sign_cache = null;
        
        public void run() {
            if(mgr == null)
                return;
            if(sign_cache == null) {    /* Initialize sign cache */
                sign_cache = new HashMap<String, SignRec>();
                Set<MarkerSet> sets = MarkerAPIImpl.api.getMarkerSets();
                for(MarkerSet ms : sets) {
                    for(Marker m : ms.getMarkers()) {
                        String id = m.getMarkerID();
                        try {
                            if(id.startsWith("_sign_")) {
                                SignRec rec = new SignRec();
                                /* Parse out the coordinates and world name */
                                int off = id.lastIndexOf('_');
                                if(off > 0) {
                                    rec.z = Integer.parseInt(id.substring(off+1));
                                    id = id.substring(0,  off);
                                }
                                off = id.lastIndexOf('_');
                                if(off > 0) {
                                    rec.y = Integer.parseInt(id.substring(off+1));
                                    id = id.substring(0,  off);
                                }
                                off = id.lastIndexOf('_');
                                if(off > 0) {
                                    rec.x = Integer.parseInt(id.substring(off+1));
                                    id = id.substring(0,  off);
                                }
                                rec.wname = id.substring(6);
                                rec.m = m;
                                sign_cache.put(m.getMarkerID(), rec);
                            }
                        } catch (NumberFormatException nfx) {
                        }
                    }
                }
            }
            /* Traverse cache - see if anyone is gone */
            for(Iterator<Entry<String, SignRec>> iter = sign_cache.entrySet().iterator(); iter.hasNext(); ) {
                Entry<String, SignRec> ent = iter.next();
                SignRec r = ent.getValue();
                /* If deleted marker, remove */
                if(r.m.getMarkerSet() == null) {
                    iter.remove();
                }
                else {
                    if(plugin.getServer().isSignAt(r.wname, r.x, r.y, r.z) == 0) {
                        r.m.deleteMarker();
                        iter.remove();
                    }
                }
            }
            plugin.getServer().scheduleServerTask(sl, 60*20);
        }
    }
    private static SignListener sl = null;  /* Do once - /dynmap reload doesn't reset listeners */

    private static String escapeMarkup(String v) {
        v = v.replace("&", "&amp;");
        v = v.replace("\"", "&quote;");
        v = v.replace("<", "&lt;");
        v = v.replace(">", "&gt;");
        return v;
    }
    
    public static MarkerSignManager initializeSignManager(DynmapCore plugin, String defsignset) {
        mgr = new MarkerSignManager();
        defSignSet = defsignset;
        if(sl == null) {
            sl = new SignListener();
            plugin.listenerManager.addListener(EventType.SIGN_CHANGE, sl);
            plugin.getServer().scheduleServerTask(sl, 200);
        }
        MarkerSignManager.plugin = plugin;
        return mgr;
    }
    public static void terminateSignManager(DynmapCore plugin) {
        mgr = null;
    }
    
    private static String getSignMarkerID(String wname, int x, int y, int z) {
        return "_sign_" + wname + "_" + x + "_" + y + "_" + z;
    }
        
}
