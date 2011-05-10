package org.dynmap;

import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerQuitEvent;

public class DynmapPlayerChatListener extends PlayerListener {
    DynmapPlugin plugin;

    public DynmapPlayerChatListener(DynmapPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPlayerChat(PlayerChatEvent event) {
    	//MikePrimm - this breaks us with HeroChat and the like - it's not ideal, but option for folks that need it helps
        if(event.isCancelled()) {
        	if(!plugin.ignoreChatCancel())
        		return;
        }

        plugin.mapManager.pushUpdate(new Client.ChatMessage("player", event.getPlayer().getDisplayName(), event.getMessage()));
    }

    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.mapManager.pushUpdate(new Client.PlayerJoinMessage(event.getPlayer().getDisplayName()));
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.mapManager.pushUpdate(new Client.PlayerQuitMessage(event.getPlayer().getDisplayName()));
    }

}