package org.dynmap.markers.impl;

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
    
    private static final int SIGNPOST_ID = 63;
    private static final int WALLSIGN_ID = 68;
        
    private static class SignListener implements DynmapListenerManager.BlockEventListener, DynmapListenerManager.SignChangeEventListener {
        @Override
        public void signChangeEvent(int blkid, String wname, int x, int y, int z, String[] lines, DynmapPlayer p) {
            if(mgr == null)
                return;
            if(!lines[0].equalsIgnoreCase("[dynmap]")) {  /* If not dynmap sign, quit */
                return;
            }
            /* If allowed to do marker signs */
            if((p == null) || ((plugin != null) && (plugin.checkPlayerPermission(p, "marker.sign")))) {
                String id = getSignMarkerID(wname, x, y, z);  /* Get marker ID */
                String set = MarkerSet.DEFAULT;
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
                }
            }
        }
        @Override
        public void blockEvent(int blkid, String wname, int x, int y, int z) {
            if(mgr == null)
                return;
            if((blkid == WALLSIGN_ID) || (blkid == SIGNPOST_ID)) {    /* If sign */
                String id = getSignMarkerID(wname, x, y, z);  /* Marker sign? */
                Set<MarkerSet> sets = MarkerAPIImpl.api.getMarkerSets();
                for(MarkerSet ms : sets) {
                    Marker marker = ms.findMarker(id);   /* See if in this set */
                    if(marker != null) {
                        marker.deleteMarker();
                    }
                }
            }
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
    
    public static MarkerSignManager initializeSignManager(DynmapCore plugin) {
        mgr = new MarkerSignManager();
        if(sl == null) {
            sl = new SignListener();
            plugin.listenerManager.addListener(EventType.BLOCK_BREAK, sl);
            plugin.listenerManager.addListener(EventType.SIGN_CHANGE, sl);
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
