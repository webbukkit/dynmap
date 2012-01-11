package org.dynmap;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Type;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerQuitEvent;
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
    private MarkerSet offlineset;
    private MarkerIcon offlineicon;
    private MarkerSet spawnbedset;
    private MarkerIcon spawnbedicon;
    
    private static final String OFFLINE_PLAYERS_SETID = "offline_players";
    private static final String PLAYER_SPAWN_BED_SETID = "spawn_beds";
    
    public MarkersComponent(final DynmapPlugin plugin, ConfigurationNode configuration) {
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
                        addUpdateWorld(w, new DynmapLocation(w.getName(), loc.getX(), loc.getY(), loc.getZ()));
                }
                public void onSpawnChange(SpawnChangeEvent event) {
                    World w = event.getWorld(); /* Get the world */
                    Location loc = w.getSpawnLocation();    /* Get location of spawn */
                    if(loc != null)
                        addUpdateWorld(w, new DynmapLocation(w.getName(), loc.getX(), loc.getY(), loc.getZ()));
                }
            };
            plugin.registerEvent(org.bukkit.event.Event.Type.WORLD_LOAD, wl);
            plugin.registerEvent(org.bukkit.event.Event.Type.SPAWN_CHANGE, wl);
            /* Initialize already loaded worlds */
            for(DynmapWorld w : plugin.getMapManager().getWorlds()) {
                DynmapLocation loc = w.getSpawnLocation();
                if(loc != null)
                    addUpdateWorld(w.getWorld(), loc);
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
            PlayerListener pl = new PlayerListener() {
                @Override
                public void onPlayerJoin(PlayerJoinEvent event) {
                    Player p = event.getPlayer();
                    Marker m = offlineset.findMarker(p.getName());
                    if(m != null) {
                        m.deleteMarker();
                    }
                }
                @Override
                public void onPlayerQuit(PlayerQuitEvent event) {
                    Player p = event.getPlayer();
                    Marker m = offlineset.findMarker(p.getName());
                    if(m != null) {
                        m.deleteMarker();
                    }
                    if(plugin.playerList.isVisiblePlayer(p)) {
                        Location loc = p.getLocation();
                        m = offlineset.createMarker(p.getName(), ChatColor.stripColor(p.getDisplayName()), false,
                                                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                                                offlineicon, true);
                    }
                }
            };
            plugin.registerEvent(Type.PLAYER_JOIN, pl);
            plugin.registerEvent(Type.PLAYER_QUIT, pl);
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
            final String spawnbedformat = configuration.getString("spawnbedformat", "%name%'s bed");
            
            /* Add listener for players coming and going */
            PlayerListener pl = new PlayerListener() {
                private void updatePlayer(Player p) {
                    Location bl = p.getBedSpawnLocation();
                    Marker m = spawnbedset.findMarker(p.getName()+"_bed");
                    if(bl == null) {    /* No bed location */
                        if(m != null) {
                            m.deleteMarker();
                        }
                    }
                    else {
                        if(m != null)
                            m.setLocation(bl.getWorld().getName(), bl.getX(), bl.getY(), bl.getZ());
                        else
                            m = spawnbedset.createMarker(p.getName()+"_bed", spawnbedformat.replace("%name%", ChatColor.stripColor(p.getDisplayName())), false,
                                    bl.getWorld().getName(), bl.getX(), bl.getY(), bl.getZ(),
                                    spawnbedicon, true);
                    }
                }
                @Override
                public void onPlayerJoin(PlayerJoinEvent event) {
                    Player p = event.getPlayer();
                    updatePlayer(p);
                }
                @Override
                public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
                    final Player p = event.getPlayer();
                    plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                        public void run() {
                            updatePlayer(p);
                        }
                    });
                }
                @Override
                public void onPlayerQuit(PlayerQuitEvent event) {
                    Player p = event.getPlayer();
                    Marker m = spawnbedset.findMarker(p.getName()+"_bed");
                    if(m != null) {
                        m.deleteMarker();
                    }
                }
            };
            plugin.registerEvent(Type.PLAYER_JOIN, pl);
            plugin.registerEvent(Type.PLAYER_QUIT, pl);
            plugin.registerEvent(Type.PLAYER_BED_LEAVE, pl);
        }
        else {
            /* Make set, if needed */
            spawnbedset = api.getMarkerSet(PLAYER_SPAWN_BED_SETID);
            if(spawnbedset != null) {
                spawnbedset.deleteMarkerSet();
            }
        }
    }
    
    private void addUpdateWorld(World w, DynmapLocation loc) {
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
            MarkerSignManager.terminateSignManager(this.plugin);
            signmgr = null;
        }
        /* Don't unregister API - other plugins might be using it, and we want to keep non-persistent markers */
    }
}
