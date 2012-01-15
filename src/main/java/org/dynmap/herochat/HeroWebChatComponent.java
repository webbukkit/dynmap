package org.dynmap.herochat;

import static org.dynmap.JSONUtils.s;

import org.dynmap.ChatEvent;
import org.dynmap.Client;
import org.dynmap.Component;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.Event;
import org.dynmap.common.DynmapListenerManager;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.common.DynmapPlayer;
import org.json.simple.JSONObject;

public class HeroWebChatComponent extends Component {
    HeroChatHandler handler;
    public HeroWebChatComponent(final DynmapCore plugin, ConfigurationNode configuration) {
        super(plugin, configuration);
        handler = new HeroChatHandler(configuration, plugin);
        plugin.events.addListener("webchat", new Event.Listener<ChatEvent>() {
            @Override
            public void triggered(ChatEvent t) {
                if(plugin.getServer().sendWebChatEvent(t.source, t.name, t.message)) {
                    /* Let HeroChat take a look - only broadcast to players if it doesn't handle it */
                    if (!handler.sendWebMessageToHeroChat(t.name, t.message)) {
                        String msg;
                        String msgfmt = plugin.configuration.getString("webmsgformat", null);
                        if(msgfmt != null) {
                            msgfmt = unescapeString(msgfmt);
                            msg = msgfmt.replace("%playername%", t.name).replace("%message%", t.message);
                        }
                        else {
                            msg = unescapeString(plugin.configuration.getString("webprefix", "\u00A72[WEB] ")) + t.name + ": " + unescapeString(plugin.configuration.getString("websuffix", "\u00A7f")) + t.message;
                        }
                        plugin.getServer().broadcastMessage(msg);
                    }
                }
            }
        });
        
        plugin.events.addListener("buildclientconfiguration", new Event.Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                s(t, "allowchat", true);
            }
        });
        
        // Also make HeroChat announce joins and quits.
        core.listenerManager.addListener(EventType.PLAYER_JOIN, new DynmapListenerManager.PlayerEventListener() {
            @Override
            public void playerEvent(DynmapPlayer p) { 
                if((core.mapManager != null) && (core.playerList != null) && (core.playerList.isVisiblePlayer(p.getName()))) {
                    core.mapManager.pushUpdate(new Client.PlayerJoinMessage(p.getDisplayName(), p.getName()));
                }
            }
        });
        core.listenerManager.addListener(EventType.PLAYER_QUIT, new DynmapListenerManager.PlayerEventListener() {
            @Override
            public void playerEvent(DynmapPlayer p) { 
                if((core.mapManager != null) && (core.playerList != null) && (core.playerList.isVisiblePlayer(p.getName()))) {
                    core.mapManager.pushUpdate(new Client.PlayerQuitMessage(p.getDisplayName(), p.getName()));
                }
            }
        });
        
    }
}
