package org.dynmap.herochat;

import static org.dynmap.JSONUtils.s;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.dynmap.ChatEvent;
import org.dynmap.Client;
import org.dynmap.Component;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapPlugin;
import org.dynmap.DynmapWebChatEvent;
import org.dynmap.Event;
import org.json.simple.JSONObject;

public class HeroWebChatComponent extends Component {
    HeroChatHandler handler;
    public HeroWebChatComponent(final DynmapPlugin plugin, ConfigurationNode configuration) {
        super(plugin, configuration);
        handler = new HeroChatHandler(configuration, plugin, plugin.getServer());
        plugin.events.addListener("webchat", new Event.Listener<ChatEvent>() {
            @Override
            public void triggered(ChatEvent t) {
                DynmapWebChatEvent evt = new DynmapWebChatEvent(t.source, t.name, t.message);
                plugin.getServer().getPluginManager().callEvent(evt);
                if(evt.isCancelled() == false) {
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
        PlayerChatListener playerListener = new PlayerChatListener();
        plugin.registerEvent(org.bukkit.event.Event.Type.PLAYER_LOGIN, playerListener);
        plugin.registerEvent(org.bukkit.event.Event.Type.PLAYER_JOIN, playerListener);
        plugin.registerEvent(org.bukkit.event.Event.Type.PLAYER_QUIT, playerListener);
    }
    
    protected class PlayerChatListener extends PlayerListener {
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
