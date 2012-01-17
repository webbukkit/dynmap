package org.dynmap;

import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.common.DynmapListenerManager.WorldEventListener;
import org.dynmap.common.DynmapListenerManager.PlayerEventListener;
import org.dynmap.common.DynmapPlayer;
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
    private MarkerSet offlineset;
    private MarkerIcon offlineicon;
    private MarkerSet spawnbedset;
    private MarkerIcon spawnbedicon;
    private String spawnbedformat;
    private static final String OFFLINE_PLAYERS_SETID = "offline_players";
    private static final String PLAYER_SPAWN_BED_SETID = "spawn_beds";
    
    public MarkersComponent(final DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);
        /* Register API with plugin, if needed */
        if(core.markerAPIInitialized()) {
            api = (MarkerAPIImpl)core.getMarkerAPI();
        }
        else {
            api = MarkerAPIImpl.initializeMarkerAPI(core);
            core.registerMarkerAPI(api);
        }
        /* If configuration has enabled sign support, prime it too */
        if(configuration.getBoolean("enablesigns", false)) {
            signmgr = MarkerSignManager.initializeSignManager(core);
        }
        /* If we're posting spawn point markers, initialize and add world listener */
        if(configuration.getBoolean("showspawn", false)) {
            String ico = configuration.getString("spawnicon", MarkerIcon.WORLD);
            spawnlbl = configuration.getString("spawnlabel", "Spawn");
            spawnicon = api.getMarkerIcon(ico); /* Load it */
            if(spawnicon == null) {
                spawnicon = api.getMarkerIcon(MarkerIcon.WORLD);
            }
            /* Add listener for world loads */
            WorldEventListener wel = new WorldEventListener() {
                @Override
                public void worldEvent(DynmapWorld w) {
                    DynmapLocation loc = w.getSpawnLocation();    /* Get location of spawn */
                    if(loc != null)
                        addUpdateWorld(w, loc);
                }
            };
            core.listenerManager.addListener(EventType.WORLD_LOAD, wel);
            /* Add listener for spawn changes */
            core.listenerManager.addListener(EventType.WORLD_SPAWN_CHANGE, wel);
            
            /* Initialize already loaded worlds */
            for(DynmapWorld w : core.getMapManager().getWorlds()) {
                DynmapLocation loc = w.getSpawnLocation();
                if(loc != null)
                    addUpdateWorld(w, loc);
            }
        }
        /* If showing offline players as markers */
        if(configuration.getBoolean("showofflineplayers", false)) {
            /* Make set, if needed */
            offlineset = api.getMarkerSet(OFFLINE_PLAYERS_SETID);
            if(offlineset == null) {
                offlineset = api.createMarkerSet(OFFLINE_PLAYERS_SETID, configuration.getString("offlinelabel", "Offline"), null, true);
            }
            offlineset.setHideByDefault(configuration.getBoolean("offlinehidebydefault", true));
            offlineset.setMinZoom(configuration.getInteger("offlineminzoom", 0));
            
            offlineicon = api.getMarkerIcon(configuration.getString("offlineicon", "offlineuser"));
            
            /* Add listener for players coming and going */
            core.listenerManager.addListener(EventType.PLAYER_JOIN, new PlayerEventListener() {
                @Override
                public void playerEvent(DynmapPlayer p) {
                    Marker m = offlineset.findMarker(p.getName());
                    if(m != null) {
                        m.deleteMarker();
                    }
                }
            });
            core.listenerManager.addListener(EventType.PLAYER_QUIT, new PlayerEventListener() {
                @Override
                public void playerEvent(DynmapPlayer p) {
                    String pname = p.getName();
                    Marker m = offlineset.findMarker(pname);
                    if(m != null) {
                        m.deleteMarker();
                    }
                    if(core.playerList.isVisiblePlayer(pname)) {
                        DynmapLocation loc = p.getLocation();
                        m = offlineset.createMarker(p.getName(), core.getServer().stripChatColor(p.getDisplayName()), false,
                                                loc.world, loc.x, loc.y, loc.z, offlineicon, true);
                    }
                }
            });
        }
        else {
            /* Make set, if needed */
            offlineset = api.getMarkerSet(OFFLINE_PLAYERS_SETID);
            if(offlineset != null) {
                offlineset.deleteMarkerSet();
            }
        }
        /* If showing player spawn bed locations as markers */
        if(configuration.getBoolean("showspawnbeds", false)) {
            /* Make set, if needed */
            spawnbedset = api.getMarkerSet(PLAYER_SPAWN_BED_SETID);
            if(spawnbedset == null) {
                spawnbedset = api.createMarkerSet(PLAYER_SPAWN_BED_SETID, configuration.getString("spawnbedlabel", "Spawn Beds"), null, true);
            }
            spawnbedset.setHideByDefault(configuration.getBoolean("spawnbedhidebydefault", true));
            spawnbedset.setMinZoom(configuration.getInteger("spawnbedminzoom", 0));
            
            spawnbedicon = api.getMarkerIcon(configuration.getString("spawnbedicon", "bed"));
            spawnbedformat = configuration.getString("spawnbedformat", "%name%'s bed");
            
            /* Add listener for players coming and going */
            core.listenerManager.addListener(EventType.PLAYER_JOIN, new PlayerEventListener() {
                @Override
                public void playerEvent(DynmapPlayer p) {                    
                    updatePlayer(p);
                }
            });
            core.listenerManager.addListener(EventType.PLAYER_QUIT, new PlayerEventListener() {
                @Override
                public void playerEvent(DynmapPlayer p) {                    
                    Marker m = spawnbedset.findMarker(p.getName()+"_bed");
                    if(m != null) {
                        m.deleteMarker();
                    }
                }
            });
            core.listenerManager.addListener(EventType.PLAYER_BED_LEAVE, new PlayerEventListener() {
                @Override
                public void playerEvent(final DynmapPlayer p) {                    
                    core.getServer().scheduleServerTask(new Runnable() {
                        public void run() {
                            updatePlayer(p);
                        }
                    }, 0);
                }
            });
        }
        else {
            /* Make set, if needed */
            spawnbedset = api.getMarkerSet(PLAYER_SPAWN_BED_SETID);
            if(spawnbedset != null) {
                spawnbedset.deleteMarkerSet();
            }
        }
    }
    
    private void updatePlayer(DynmapPlayer p) {
        DynmapLocation bl = p.getBedSpawnLocation();
        Marker m = spawnbedset.findMarker(p.getName()+"_bed");
        if(bl == null) {    /* No bed location */
            if(m != null) {
                m.deleteMarker();
            }
        }
        else {
            if(m != null)
                m.setLocation(bl.world, bl.x, bl.y, bl.z);
            else
                m = spawnbedset.createMarker(p.getName()+"_bed", spawnbedformat.replace("%name%", core.getServer().stripChatColor(p.getDisplayName())), false,
                        bl.world, bl.x, bl.y, bl.z,
                        spawnbedicon, true);
        }
    }

    
    private void addUpdateWorld(DynmapWorld w, DynmapLocation loc) {
        MarkerSet ms = api.getMarkerSet(MarkerSet.DEFAULT);
        if(ms != null) {
            String spawnid = "_spawn_" + w.getName();
            Marker m = ms.findMarker(spawnid);    /* See if defined */
            if(m == null) { /* Not defined yet, add it */
                ms.createMarker(spawnid, spawnlbl, w.getName(), loc.x, loc.y, loc.z,
                                spawnicon, false);
            }
            else {
                m.setLocation(w.getName(), loc.z, loc.y, loc.z);
            }
        }
    }

    @Override
    public void dispose() {
        if(signmgr != null) {
            MarkerSignManager.terminateSignManager(this.core);
            signmgr = null;
        }
        /* Don't unregister API - other plugins might be using it, and we want to keep non-persistent markers */
    }
}
