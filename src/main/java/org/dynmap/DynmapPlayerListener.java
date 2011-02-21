package org.dynmap;

import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerListener;

public class DynmapPlayerListener extends PlayerListener {
    DynmapPlugin plugin;

    public DynmapPlayerListener(DynmapPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPlayerCommand(PlayerChatEvent event) {
        String[] split = event.getMessage().split(" ");
        if (split[0].equalsIgnoreCase("/dynmap")) {
            if (split.length > 1) {
                if (split[1].equals("render")) {
                    Player player = event.getPlayer();
                    plugin.mapManager.touch(player.getLocation());
                    event.setCancelled(true);
                } else if (split[1].equals("hide")) {
                    Player player = event.getPlayer();
                    if (split.length == 2) {
                        plugin.playerList.hide(player.getName());
                        player.sendMessage("You are now hidden on Dynmap.");
                    } else {
                        for (int i = 2; i < split.length; i++) {
                            plugin.playerList.hide(split[i]);
                            player.sendMessage(split[i] + " is now hidden on Dynmap.");
                        }
                    }
                    event.setCancelled(true);
                } else if (split[1].equals("show")) {
                    Player player = event.getPlayer();
                    if (split.length == 2) {
                        plugin.playerList.show(player.getName());
                        player.sendMessage("You are now visible on Dynmap.");
                    } else {
                        for (int i = 2; i < split.length; i++) {
                            plugin.playerList.show(split[i]);
                            player.sendMessage(split[i] + " is now visible on Dynmap.");
                        }
                    }
                    event.setCancelled(true);
                } else if (split[1].equals("fullrender")) {
                    Player player = event.getPlayer();
                    if (player == null || player.isOp()) {
                        if (split.length > 3) {
                            for (int i = 2; i < split.length; i++) {
                                World w = plugin.getServer().getWorld(split[i]);
                                plugin.mapManager.renderFullWorld(new Location(w, 0, 0, 0));
                            }
                        } else if (player != null) {
                            plugin.mapManager.renderFullWorld(player.getLocation());
                        }
                    } else if (player != null) {
                        player.sendMessage("Only OPs are allowed to use this command!");
                    }
                    event.setCancelled(true);
                }
            }
        }
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