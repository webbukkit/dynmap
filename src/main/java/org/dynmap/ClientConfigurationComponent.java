package org.dynmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;

import org.bukkit.Location;
import org.dynmap.Event.Listener;
import org.json.simple.JSONObject;

public class ClientConfigurationComponent extends Component {
    public ClientConfigurationComponent(final DynmapPlugin plugin, ConfigurationNode configuration) {
        super(plugin, configuration);
        plugin.events.<JSONObject>addListener("buildclientconfiguration", new Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                ConfigurationNode c = plugin.configuration;
                s(t, "confighash", plugin.getConfigHashcode());
                s(t, "updaterate", c.getFloat("updaterate", 1.0f));
                s(t, "showplayerfacesinmenu", c.getBoolean("showplayerfacesinmenu", true));
                s(t, "joinmessage", c.getString("joinmessage", "%playername% joined"));
                s(t, "quitmessage", c.getString("quitmessage", "%playername% quit"));
                s(t, "spammessage", c.getString("spammessage", "You may only chat once every %interval% seconds."));
                s(t, "webprefix", unescapeString(c.getString("webprefix", "[WEB] ")));
                s(t, "defaultzoom", c.getInteger("defaultzoom", 0));
                s(t, "sidebaropened", c.getString("sidebaropened", "false"));
                s(t, "dynmapversion", plugin.getDescription().getVersion());
                s(t, "cyrillic", c.getBoolean("cyrillic-support", false));
                String sn = plugin.getServer().getServerName();
                if(sn.equals("Unknown Server"))
                    sn = "Minecraft Dynamic Map";
                s(t, "title", c.getString("webpage-title", sn));
                
                DynmapWorld defaultWorld = null;
                String defmap = null;
                for(DynmapWorld world : plugin.mapManager.getWorlds()) {
                    if (defaultWorld == null) defaultWorld = world;
                    ConfigurationNode wn = world.configuration;
                    JSONObject wo = new JSONObject();
                    s(wo, "name", wn.getString("name"));
                    s(wo, "title", wn.getString("title"));
                    Location spawn = world.world.getSpawnLocation();
                    s(wo, "center/x", wn.getDouble("center/x", spawn.getX()));
                    s(wo, "center/y", wn.getDouble("center/y", spawn.getY()));
                    s(wo, "center/z", wn.getDouble("center/z", spawn.getZ()));
                    s(wo, "bigworld", world.bigworld);
                    s(wo, "extrazoomout", world.getExtraZoomOutLevels());
                    a(t, "worlds", wo);
                    
                    for(MapType mt : world.maps) {
                        mt.buildClientConfiguration(wo, world);
                        if(defmap == null) defmap = mt.getName();
                    }
                }
                s(t, "defaultworld", c.getString("defaultworld", defaultWorld == null ? "world" : defaultWorld.world.getName()));
                s(t, "defaultmap", c.getString("defaultmap", defmap == null ? "surface" : defmap));
            }
        });
    }
    
}
