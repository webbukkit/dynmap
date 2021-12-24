package org.dynmap;

import static org.dynmap.JSONUtils.s;

import org.dynmap.common.DynmapListenerManager;
import org.dynmap.common.DynmapListenerManager.ChatEventListener;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.common.DynmapPlayer;
import org.json.simple.JSONObject;

public class SimpleWebChatComponent extends Component {

    public SimpleWebChatComponent(final DynmapCore plugin, final ConfigurationNode configuration) {
        super(plugin, configuration);
        plugin.events.addListener("webchat", new Event.Listener<ChatEvent>() {
            @Override
            public void triggered(ChatEvent t) {
                if(plugin.getServer().sendWebChatEvent(t.source, t.name, t.message)) {
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
                    if (core.mapManager != null) {
                        core.mapManager.pushUpdate(new Client.ChatMessage("web", null, t.name, t.message, null));
                    }
                }
            }
        });
        
        plugin.events.addListener("buildclientconfiguration", new Event.Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                s(t, "allowchat", configuration.getBoolean("allowchat", false));
            }
        });
        
        if (configuration.getBoolean("allowchat", false)) {
            plugin.listenerManager.addListener(EventType.PLAYER_CHAT, new ChatEventListener() {
                @Override
                public void chatEvent(DynmapPlayer p, String msg) {
                    if(core.disable_chat_to_web) return;
                    msg = core.scanAndReplaceLog4JMacro(msg);
                    if(core.mapManager != null)
                        core.mapManager.pushUpdate(new Client.ChatMessage("player", "", p.getDisplayName(), msg, p.getName()));
                }
            });
            plugin.listenerManager.addListener(EventType.PLAYER_JOIN, new DynmapListenerManager.PlayerEventListener() {
                @Override
                public void playerEvent(DynmapPlayer p) {
                    if(core.disable_chat_to_web) return;
                    if((core.mapManager != null) && (core.playerList != null) && (core.playerList.isVisiblePlayer(p.getName()))) {
                        core.mapManager.pushUpdate(new Client.PlayerJoinMessage(p.getDisplayName(), p.getName()));
                    }
                }
            });
            plugin.listenerManager.addListener(EventType.PLAYER_QUIT, new DynmapListenerManager.PlayerEventListener() {
                @Override
                public void playerEvent(DynmapPlayer p) {
                    if(core.disable_chat_to_web) return;
                    if((core.mapManager != null) && (core.playerList != null) && (core.playerList.isVisiblePlayer(p.getName()))) {
                        core.mapManager.pushUpdate(new Client.PlayerQuitMessage(p.getDisplayName(), p.getName()));
                    }
                }
            });
        }
    }
}
