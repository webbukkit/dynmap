package org.dynmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;

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
            s(jp, "name", p.getDisplayName());
            s(jp, "account", p.getName());
            s(jp, "world", p.getWorld().getName());
            s(jp, "x", pl.getX());
            s(jp, "y", pl.getY());
            s(jp, "z", pl.getZ());
            if (configuration.getBoolean("sendhealth", false)) {
                s(jp, "health", p.getHealth());
                s(jp, "armor", Armor.getArmorPoints(p));
            }
            a(u, "players", jp);
        }

        s(u, "updates", new JSONArray());
        for(Object update : plugin.mapManager.getWorldUpdates(worldName, since)) {
            a(u, "updates", (Client.Update)update);
        }
    }

}
