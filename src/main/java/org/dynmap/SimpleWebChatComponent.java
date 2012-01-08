package org.dynmap;

import static org.dynmap.JSONUtils.s;

import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.json.simple.JSONObject;

public class SimpleWebChatComponent extends Component {

    public SimpleWebChatComponent(final DynmapPlugin plugin, final ConfigurationNode configuration) {
        super(plugin, configuration);
        plugin.events.addListener("webchat", new Event.Listener<ChatEvent>() {
            @Override
            public void triggered(ChatEvent t) {
                DynmapWebChatEvent evt = new DynmapWebChatEvent(t.source, t.name, t.message);
                plugin.getServer().getPluginManager().callEvent(evt);
                if(evt.isCancelled() == false) {
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
        });
        
        plugin.events.addListener("buildclientconfiguration", new Event.Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                s(t, "allowchat", configuration.getBoolean("allowchat", false));
            }
        });
        
        if (configuration.getBoolean("allowchat", false)) {
            PlayerChatListener playerListener = new PlayerChatListener();
            plugin.registerEvent(org.bukkit.event.Event.Type.PLAYER_CHAT, playerListener);
            plugin.registerEvent(org.bukkit.event.Event.Type.PLAYER_LOGIN, playerListener);
            plugin.registerEvent(org.bukkit.event.Event.Type.PLAYER_JOIN, playerListener);
            plugin.registerEvent(org.bukkit.event.Event.Type.PLAYER_QUIT, playerListener);
        }
    }
    
    protected class PlayerChatListener extends PlayerListener {
        @Override
        public void onPlayerChat(PlayerChatEvent event) {
            if(event.isCancelled()) return;
            if(plugin.mapManager != null)
                plugin.mapManager.pushUpdate(new Client.ChatMessage("player", "", event.getPlayer().getDisplayName(), event.getMessage(), event.getPlayer().getName()));
        }

        @Override
        public void onPlayerJoin(PlayerJoinEvent event) {
            if((plugin.mapManager != null) && (plugin.playerList != null) && (plugin.playerList.isVisiblePlayer(event.getPlayer()))) {
                plugin.mapManager.pushUpdate(new Client.PlayerJoinMessage(event.getPlayer().getDisplayName(), event.getPlayer().getName()));
            }
        }

        @Override
        public void onPlayerQuit(PlayerQuitEvent event) {
            if((plugin.mapManager != null) && (plugin.playerList != null) && (plugin.playerList.isVisiblePlayer(event.getPlayer()))) {
                plugin.mapManager.pushUpdate(new Client.PlayerQuitMessage(event.getPlayer().getDisplayName(), event.getPlayer().getName()));
            }
        }
    }

}
