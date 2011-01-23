package org.dynmap;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerListener;

public class DynmapPlayerListener extends PlayerListener {
	private MapManager mgr;
	private PlayerList playerList;
	
	public DynmapPlayerListener(MapManager mgr, PlayerList playerList) {
		this.mgr = mgr;
		this.playerList = playerList;
	}
	
	@Override
	public void onPlayerCommand(PlayerChatEvent event) {
		String[] split = event.getMessage().split(" ");
        if (split[0].equalsIgnoreCase("/dynmap")) {
        	if (split.length > 1) {
        		if (split[1].equals("render")) {
        			Player player = event.getPlayer();
		        	mgr.touch(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
		        	event.setCancelled(true);
        		} else if (split[1].equals("hide")) {
        			if (split.length == 2) {
        				playerList.hide(event.getPlayer().getName());
        			} else for (int i=2;i<split.length;i++)
        				playerList.hide(split[i]);
        			event.setCancelled(true);
        		} else if (split[1].equals("show")) {
        			if (split.length == 2) {
        				playerList.show(event.getPlayer().getName());
        			} else for (int i=2;i<split.length;i++)
        				playerList.show(split[i]);
        			event.setCancelled(true);
        		}
        	}
        }
	}
}