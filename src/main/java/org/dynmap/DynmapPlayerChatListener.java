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
        plugin.mapManager.pushUpdate(new Client.ChatMessage(event.getPlayer().getName(), event.getMessage()));
    }

    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        String joinMessage = plugin.configuration.getString("joinmessage", "%playername% joined");
        joinMessage = joinMessage.replaceAll("%playername%", event.getPlayer().getName());
        plugin.mapManager.pushUpdate(new Client.ChatMessage("Server", joinMessage));
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        String quitMessage = plugin.configuration.getString("quitmessage", "%playername% quit");
        quitMessage = quitMessage.replaceAll("%playername%", event.getPlayer().getName());
        plugin.mapManager.pushUpdate(new Client.ChatMessage("Server", quitMessage));
    }

}