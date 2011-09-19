package org.dynmap;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.WorldListener;
import org.bukkit.event.world.WorldLoadEvent;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.impl.MarkerAPIImpl;
import org.dynmap.markers.impl.MarkerSignManager;

/**
 * Markers component - ties in the component system, both on the server and client
 */
public class MarkersComponent extends ClientComponent {
    private MarkerAPIImpl api;
    private MarkerSignManager signmgr;
    private MarkerIcon spawnicon;
    private String spawnlbl;
    
    public MarkersComponent(DynmapPlugin plugin, ConfigurationNode configuration) {
        super(plugin, configuration);
        /* Register API with plugin, if needed */
        if(plugin.markerAPIInitialized()) {
            api = (MarkerAPIImpl)plugin.getMarkerAPI();
        }
        else {
            api = MarkerAPIImpl.initializeMarkerAPI(plugin);
            plugin.registerMarkerAPI(api);
        }
        /* If configuration has enabled sign support, prime it too */
        if(configuration.getBoolean("enablesigns", false)) {
            signmgr = MarkerSignManager.initializeSignManager(plugin);
        }
        /* If we're posting spawn point markers, initialize and add world listener */
        if(configuration.getBoolean("showspawn", false)) {
            String ico = configuration.getString("spawnicon", MarkerIcon.WORLD);
            spawnlbl = configuration.getString("spawnlabel", "Spawn");
            spawnicon = api.getMarkerIcon(ico); /* Load it */
            if(spawnicon == null) {
                spawnicon = api.getMarkerIcon(MarkerIcon.WORLD);
            }
            WorldListener wl = new WorldListener() {
                public void onWorldLoad(WorldLoadEvent event) {
                    World w = event.getWorld(); /* Get the world */
                    Location loc = w.getSpawnLocation();    /* Get location of spawn */
                    if(loc != null)
                        addUpdateWorld(w, loc);
                }
                public void onSpawnChange(SpawnChangeEvent event) {
                    World w = event.getWorld(); /* Get the world */
                    Location loc = w.getSpawnLocation();    /* Get location of spawn */
                    if(loc != null)
                        addUpdateWorld(w, loc);
                }
            };
            plugin.registerEvent(org.bukkit.event.Event.Type.WORLD_LOAD, wl);
            plugin.registerEvent(org.bukkit.event.Event.Type.SPAWN_CHANGE, wl);
            /* Initialize already loaded worlds */
            for(DynmapWorld w : plugin.getMapManager().getWorlds()) {
                World world = w.world;
                Location loc = world.getSpawnLocation();
                if(loc != null)
                    addUpdateWorld(world, loc);
            }
        }
    }
    
    private void addUpdateWorld(World w, Location loc) {
        MarkerSet ms = api.getMarkerSet(MarkerSet.DEFAULT);
        if(ms != null) {
            String spawnid = "_spawn_" + w.getName();
            Marker m = ms.findMarker(spawnid);    /* See if defined */
            if(m == null) { /* Not defined yet, add it */
                ms.createMarker(spawnid, spawnlbl, w.getName(), loc.getX(), loc.getY(), loc.getZ(),
                                spawnicon, false);
            }
            else {
                m.setLocation(w.getName(), loc.getX(), loc.getY(), loc.getZ());
            }
        }
    }

    @Override
    public void dispose() {
        if(signmgr != null) {
            MarkerSignManager.terminateSignManager(this.plugin);
            signmgr = null;
        }
        /* Don't unregister API - other plugins might be using it, and we want to keep non-persistent markers */
    }
}
