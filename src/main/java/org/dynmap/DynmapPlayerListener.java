package org.dynmap;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.util.config.ConfigurationNode;

public class DynmapPlayerListener extends PlayerListener {
    private MapManager mgr;
    private PlayerList playerList;
    private ConfigurationNode configuration;

    public DynmapPlayerListener(MapManager mgr, PlayerList playerList, ConfigurationNode configuration) {
        this.mgr = mgr;
        this.playerList = playerList;
        this.configuration = configuration;
    }

    @Override
    public void onPlayerCommand(PlayerChatEvent event) {
        String[] split = event.getMessage().split(" ");
        if (split[0].equalsIgnoreCase("/dynmap")) {
            if (split.length > 1) {
                if (configuration.getProperty("disabledcommands") instanceof Iterable<?>) {
                    for(String s : (Iterable<String>)configuration.getProperty("disabledcommands")) {
                        if (split[1].equals(s)) {
                            return;
                        }
                    }
                }
                
                if (split[1].equals("render")) {
                    Player player = event.getPlayer();
                    mgr.touch(player.getLocation());
                    event.setCancelled(true);
                } else if (split[1].equals("hide")) {
                    if (split.length == 2) {
                        playerList.hide(event.getPlayer().getName());
                    } else {
                        for (int i = 2; i < split.length; i++) {
                            playerList.hide(split[i]);
                        }
                    }
                    event.setCancelled(true);
                } else if (split[1].equals("show")) {
                    if (split.length == 2) {
                        playerList.show(event.getPlayer().getName());
                    } else {
                        for (int i = 2; i < split.length; i++) {
                            playerList.show(split[i]);
                        }
                    }
                    event.setCancelled(true);
                } else if (split[1].equals("fullrender")) {
                    Player player = event.getPlayer();
                    mgr.renderFullWorld(player.getLocation());
                }
            }
        }
    }

    /**
     * Called when a player sends a chat message
     * 
     * @param event
     *            Relevant event details
     */
    public void onPlayerChat(PlayerChatEvent event) {
        mgr.updateQueue.pushUpdate(new Client.ChatMessage(event.getPlayer().getName(), event.getMessage()));
    }
}