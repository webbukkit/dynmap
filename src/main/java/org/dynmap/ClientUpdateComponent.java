package org.dynmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class ClientUpdateComponent extends Component {
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
        
        s(u, "servertime", world.getTime() % 24000);
        s(u, "hasStorm", world.hasStorm());
        s(u, "isThundering", world.isThundering());

        s(u, "players", new JSONArray());
        Player[] players = plugin.playerList.getVisiblePlayers();
        for(int i=0;i<players.length;i++) {
            Player p = players[i];
            Location pl = p.getLocation();
            JSONObject jp = new JSONObject();
            s(jp, "type", "player");
            s(jp, "name", ChatColor.stripColor(p.getDisplayName()));
            s(jp, "account", p.getName());
            /* Don't leak player location for world not visible on maps, or if sendposition disbaled */
            DynmapWorld pworld = MapManager.mapman.worldsLookup.get(p.getWorld().getName());
            /* Fix typo on 'sendpositon' to 'sendposition', keep bad one in case someone used it */
            if(configuration.getBoolean("sendposition", true) && configuration.getBoolean("sendpositon", true) &&
                    (pworld != null) && pworld.sendposition) {
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
            if (configuration.getBoolean("sendhealth", false) && (pworld != null) && pworld.sendhealth) {
                s(jp, "health", p.getHealth());
                s(jp, "armor", Armor.getArmorPoints(p));
            }
            else {
                s(jp, "health", 0);
                s(jp, "armor", 0);
            }
            a(u, "players", jp);
        }

        s(u, "updates", new JSONArray());
        for(Object update : plugin.mapManager.getWorldUpdates(worldName, since)) {
            a(u, "updates", (Client.Update)update);
        }
    }

}
