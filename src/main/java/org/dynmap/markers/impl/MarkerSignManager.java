package org.dynmap.markers.impl;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.plugin.PluginManager;
import org.dynmap.DynmapPlugin;
import org.dynmap.Log;

public class MarkerSignManager {

    private static MarkerSignManager mgr = null;
    
    private static class SignListener extends BlockListener {
        @Override
        public void onSignChange(SignChangeEvent evt) {
            if(evt.isCancelled() || (mgr == null))
                return;
            Log.info("onSignChange: '" + evt.getLine(0) + "','" + evt.getLine(1) + "','" + evt.getLine(2) + "','" + evt.getLine(3) + "'");
        }
        @Override
        public void onBlockPlace(BlockPlaceEvent evt) {
            if(evt.isCancelled() || (mgr == null))
                return;
        }
        @Override
        public void onBlockBreak(BlockBreakEvent evt) {
            if(evt.isCancelled() || (mgr == null))
                return;
        }
    }
    private static SignListener sl = null;  /* Do once - /dynmap reload doesn't reset listeners */

    public static MarkerSignManager initializeSignManager(DynmapPlugin plugin) {
        mgr = new MarkerSignManager();
        if(sl == null) {
            sl = new SignListener();
            PluginManager pm = plugin.getServer().getPluginManager();
            pm.registerEvent(Event.Type.BLOCK_PLACE, sl, Event.Priority.Low, plugin);
            pm.registerEvent(Event.Type.BLOCK_BREAK, sl, Event.Priority.Low, plugin);
            pm.registerEvent(Event.Type.SIGN_CHANGE, sl, Event.Priority.Low, plugin);
        }
        return mgr;
    }
    public static void terminateSignManager(DynmapPlugin plugin) {
        mgr = null;
    }
    
    private static String getSignMarkerID(Location loc) {
        return "$sign-" + loc.getWorld().getName() + "/" + loc.getBlockX() + "/" + loc.getBlockY() + "/" + loc.getBlockZ();
    }
}
