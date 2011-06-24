package org.dynmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;

import org.dynmap.Event.Listener;
import org.json.simple.JSONObject;

public class ClientConfigurationComponent extends Component {
    public ClientConfigurationComponent(final DynmapPlugin plugin, ConfigurationNode configuration) {
        super(plugin, configuration);
        plugin.events.<JSONObject>addListener("buildclientconfiguration", new Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                ConfigurationNode c = plugin.configuration;
                s(t, "updaterate", c.getFloat("updaterate", 1.0f));
                s(t, "showplayerfacesinmenu", c.getBoolean("showplayerfacesinmenu", true));
                s(t, "joinmessage", c.getString("joinmessage", "%playername% joined"));
                s(t, "quitmessage", c.getString("quitmessage", "%playername% quit"));
                s(t, "spammessage", c.getString("spammessage", "You may only chat once every %interval% seconds."));
                s(t, "webprefix", c.getString("webprefix", "[WEB] "));
                s(t, "defaultzoom", c.getInteger("defaultzoom", 0));
                s(t, "sidebaropened", c.getBoolean("sidebaropened", false));
                
                DynmapWorld defaultWorld = null;
                for(DynmapWorld world : plugin.mapManager.getWorlds()) {
                    if (defaultWorld == null) defaultWorld = world;
                    ConfigurationNode wn = world.configuration;
                    JSONObject wo = new JSONObject();
                    s(wo, "name", wn.getString("name"));
                    s(wo, "title", wn.getString("title"));
                    s(wo, "center/x", wn.getFloat("center/x", 0.0f));
                    s(wo, "center/y", wn.getFloat("center/y", 64.0f));
                    s(wo, "center/z", wn.getFloat("center/z", 0.0f));
                    s(wo, "bigworld", world.bigworld);
                    s(wo, "extrazoomout", world.getExtraZoomOutLevels());
                    a(t, "worlds", wo);
                    
                    for(MapType mt : world.maps) {
                        mt.buildClientConfiguration(wo);
                    }
                }
                s(t, "defaultworld", c.getString("defaultworld", defaultWorld == null ? "world" : defaultWorld.world.getName()));
            }
        });
    }
    
}
