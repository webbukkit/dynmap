package org.dynmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.dynmap.utils.BlockLightLevel;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class ClientUpdateComponent extends Component {
    private BlockLightLevel bll = new BlockLightLevel();
    
    public ClientUpdateComponent(final DynmapPlugin plugin, ConfigurationNode configuration) {
        super(plugin, configuration);
        plugin.events.addListener("buildclientupdate", new Event.Listener<ClientUpdateEvent>() {
            @Override
            public void triggered(ClientUpdateEvent e) {
                buildClientUpdate(e);
            }
        });
    }
    
    protected void buildClientUpdate(ClientUpdateEvent e) {
        World world = e.world.world;
        JSONObject u = e.update;
        long since = e.timestamp;
        String worldName = world.getName();
        int hideifshadow = configuration.getInteger("hideifshadow", 15);
        int hideifunder = configuration.getInteger("hideifundercover", 15);
        boolean hideifsneaking = configuration.getBoolean("hideifsneaking", false);

        s(u, "confighash", plugin.getConfigHashcode());

        s(u, "servertime", world.getTime() % 24000);
        s(u, "hasStorm", world.hasStorm());
        s(u, "isThundering", world.isThundering());

        s(u, "players", new JSONArray());
        Player[] players = plugin.playerList.getVisiblePlayers();
        for(int i=0;i<players.length;i++) {
            Player p = players[i];
            Location pl = p.getLocation();
            JSONObject jp = new JSONObject();
            boolean hide = false;
            
            s(jp, "type", "player");
            s(jp, "name", Client.stripColor(p.getDisplayName()));
            s(jp, "account", p.getName());
            if(hideifshadow < 15) {
                if(pl.getBlock().getLightLevel() <= hideifshadow)
                    hide = true;
            }
            if(hideifunder < 15) {
                if(bll.isReady()) { /* If we can get real sky level */
                    if(bll.getSkyLightLevel(pl.getBlock()) <= hideifunder)
                        hide = true;
                }
                else {
                    if(pl.getWorld().getHighestBlockYAt(pl) > pl.getBlockY())
                        hide = true;
                }
            }
            if(hideifsneaking && p.isSneaking())
                hide = true;
            
            /* Don't leak player location for world not visible on maps, or if sendposition disbaled */
            DynmapWorld pworld = MapManager.mapman.worldsLookup.get(p.getWorld().getName());
            /* Fix typo on 'sendpositon' to 'sendposition', keep bad one in case someone used it */
            if(configuration.getBoolean("sendposition", true) && configuration.getBoolean("sendpositon", true) &&
                    (pworld != null) && pworld.sendposition && (!hide)) {
                s(jp, "world", p.getWorld().getName());
                s(jp, "x", pl.getX());
                s(jp, "y", pl.getY());
                s(jp, "z", pl.getZ());
            }
            else {
                s(jp, "world", "-some-other-bogus-world-");
                s(jp, "x", 0.0);
                s(jp, "y", 64.0);
                s(jp, "z", 0.0);
            }
            /* Only send health if enabled AND we're on visible world */
            if (configuration.getBoolean("sendhealth", false) && (pworld != null) && pworld.sendhealth && (!hide)) {
                s(jp, "health", p.getHealth());
                s(jp, "armor", Armor.getArmorPoints(p));
            }
            else {
                s(jp, "health", 0);
                s(jp, "armor", 0);
            }
            a(u, "players", jp);
        }
        if(configuration.getBoolean("includehiddenplayers", false)) {
            Set<Player> hidden = plugin.playerList.getHiddenPlayers();
            for(Player p : hidden) {
                JSONObject jp = new JSONObject();
                s(jp, "type", "player");
                s(jp, "name", Client.stripColor(p.getDisplayName()));
                s(jp, "account", p.getName());
                s(jp, "world", "-hidden-player-");
                s(jp, "x", 0.0);
                s(jp, "y", 64.0);
                s(jp, "z", 0.0);
                s(jp, "health", 0);
                s(jp, "armor", 0);
                a(u, "players", jp);
            }
        }

        s(u, "updates", new JSONArray());
        for(Object update : plugin.mapManager.getWorldUpdates(worldName, since)) {
            a(u, "updates", (Client.Update)update);
        }
    }

}
