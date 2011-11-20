package org.dynmap.markers.impl;

import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.plugin.PluginManager;
import org.dynmap.DynmapPlugin;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

public class MarkerSignManager {

    private static MarkerSignManager mgr = null;
    private static DynmapPlugin plugin = null;
    
    private static class SignListener extends BlockListener {
        @Override
        public void onSignChange(SignChangeEvent evt) {
            if(evt.isCancelled() || (mgr == null))
                return;
            if(!evt.getLine(0).equalsIgnoreCase("[dynmap]")) {  /* If not dynmap sign, quit */
                return;
            }
            Player p = evt.getPlayer();
            /* If allowed to do marker signs */
            if((p == null) || ((plugin != null) && (plugin.checkPlayerPermission(p, "marker.sign")))) {
                Location loc = evt.getBlock().getLocation();
                String id = getSignMarkerID(loc);  /* Get marker ID */
                String set = MarkerSet.DEFAULT;
                String icon = MarkerIcon.SIGN;
                String label = "";
                evt.setLine(0, ""); /* Blank out [dynmap] */
                for(int i = 1; i < 4; i++) {    /* Check other lines for icon: or set: */
                    String v = ChatColor.stripColor(evt.getLine(i));
                    if(v.startsWith("icon:")) { /* icon: */
                        icon = v.substring(5);
                        evt.setLine(i, "");
                    }
                    else if(v.startsWith("set:")) { /* set: */
                        set = v.substring(4);
                        evt.setLine(i, "");
                    }
                    else if(label.length() == 0) {
                        label = escapeMarkup(v);
                    }
                    else {
                        label = label + "<br/>" + escapeMarkup(v);
                    }
                }
                /* Get the set and see if the marker is already defined */
                MarkerSet ms = MarkerAPIImpl.api.getMarkerSet(set);
                if(ms == null) {
                    if(p != null) p.sendMessage("Bad marker set - [dynmap] sign invalid");
                    evt.setLine(0, ChatColor.RED + "<Bad Marker Set>");
                    return;
                }
                MarkerIcon mi = MarkerAPIImpl.api.getMarkerIcon(icon);  /* Get icon */
                if(mi == null) {
                    if(p != null) p.sendMessage("Bad marker icon - [dynmap] sign invalid");
                    evt.setLine(0, ChatColor.RED + "<Bad Marker Icon>");
                    return;
                }
                Marker marker = ms.findMarker(id);
                /* If exists, update it */
                if(marker != null) {
                    marker.setLabel(label, true);
                    marker.setMarkerIcon(mi);
                }
                else {  /* Make new marker */
                    marker = ms.createMarker(id, label, true, loc.getWorld().getName(), loc.getX() + 0.5, loc.getY() + 0.5, loc.getZ() + 0.5,
                                             mi, true);
                    if(marker == null) {
                        if(p != null) p.sendMessage("Bad marker - [dynmap] sign invalid");
                        evt.setLine(0, ChatColor.RED + "<Bad Marker>");
                        return;
                    }
                }
            }
        }
        @Override
        public void onBlockBreak(BlockBreakEvent evt) {
            if(evt.isCancelled() || (mgr == null))
                return;
            Block blk = evt.getBlock();
            Material m = blk.getType();
            if((m == Material.SIGN) || (m == Material.SIGN_POST) || (m == Material.WALL_SIGN)) {    /* If sign */
                Location loc = blk.getLocation();
                String id = getSignMarkerID(loc);  /* Marker sign? */
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
    
    public static MarkerSignManager initializeSignManager(DynmapPlugin plugin) {
        mgr = new MarkerSignManager();
        if(sl == null) {
            sl = new SignListener();
            PluginManager pm = plugin.getServer().getPluginManager();
            pm.registerEvent(Event.Type.BLOCK_BREAK, sl, Event.Priority.Monitor, plugin);
            pm.registerEvent(Event.Type.SIGN_CHANGE, sl, Event.Priority.Low, plugin);
        }
        MarkerSignManager.plugin = plugin;
        return mgr;
    }
    public static void terminateSignManager(DynmapPlugin plugin) {
        mgr = null;
    }
    
    private static String getSignMarkerID(Location loc) {
        return "_sign_" + loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }
}
