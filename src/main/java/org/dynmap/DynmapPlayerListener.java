package org.dynmap;

import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerListener;

public class DynmapPlayerListener extends PlayerListener {
    DynmapPlugin plugin;

    public DynmapPlayerListener(DynmapPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPlayerChat(PlayerChatEvent event) {
        plugin.mapManager.pushUpdate(new Client.ChatMessage(event.getPlayer().getName(), event.getMessage()));
    }

    @Override
    public void onPlayerJoin(PlayerEvent event) {
        String joinMessage = plugin.configuration.getString("joinmessage", "%playername% joined");
        joinMessage = joinMessage.replaceAll("%playername%", event.getPlayer().getName());
        plugin.mapManager.pushUpdate(new Client.ChatMessage("Server", joinMessage));
    }

    @Override
    public void onPlayerQuit(PlayerEvent event) {
        String quitMessage = plugin.configuration.getString("quitmessage", "%playername% quit");
        quitMessage = quitMessage.replaceAll("%playername%", event.getPlayer().getName());
        plugin.mapManager.pushUpdate(new Client.ChatMessage("Server", quitMessage));
    }

}