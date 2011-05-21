package org.dynmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.l;
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
                s(t, "allowchat", c.getBoolean("allowchat", true));
                s(t, "allowwebchat", c.getBoolean("allowwebchat", true));
                s(t, "webchat-interval", c.getFloat("webchat-interval", 5.0f));
                s(t, "showplayerfacesinmenu", c.getBoolean("showplayerfacesinmenu", true));
                s(t, "joinmessage", c.getString("joinmessage", "%playername% joined"));
                s(t, "quitmessage", c.getString("joinmessage", "%playername% quit"));
                s(t, "spammessage", c.getString("joinmessage", "You may only chat once every %interval% seconds."));
                
                for(ConfigurationNode wn : plugin.configuration.getNodes("worlds")) {
                    DynmapWorld world = plugin.mapManager.getWorld(wn.getString("name"));
                    JSONObject wo = new JSONObject();
                    s(wo, "name", wn.getString("name"));
                    s(wo, "title", wn.getString("title"));
                    s(wo, "center/x", wn.getFloat("center/x", 0.0f));
                    s(wo, "center/y", wn.getFloat("center/y", 0.0f));
                    s(wo, "center/z", wn.getFloat("center/z", 0.0f));
                    a(t, "worlds", wo);
                    
                    for(MapType mt : world.maps) {
                        mt.buildClientConfiguration(wo);
                    }
                }
            }
        });
    }
    
}
