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
        if(event.isCancelled()) return;
        if(plugin.mapManager != null)
            plugin.mapManager.pushUpdate(new Client.ChatMessage("player", "", 
                                                            event.getPlayer().getDisplayName(), event.getMessage(),
                                                            event.getPlayer().getName()));
    }

    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        if(plugin.mapManager != null)
            plugin.mapManager.pushUpdate(new Client.PlayerJoinMessage(event.getPlayer().getDisplayName(), event.getPlayer().getName()));
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        if(plugin.mapManager != null)
            plugin.mapManager.pushUpdate(new Client.PlayerQuitMessage(event.getPlayer().getDisplayName(), event.getPlayer().getName()));
    }

}
