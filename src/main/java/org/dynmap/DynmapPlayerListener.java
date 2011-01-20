package org.dynmap;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerListener;

public class DynmapPlayerListener extends PlayerListener {
	private MapManager mgr;
	
	public DynmapPlayerListener(MapManager mgr) {
		this.mgr = mgr;
	}
	
	@Override
	public void onPlayerCommand(PlayerChatEvent event) {
		String[] split = event.getMessage().split(" ");
        Player player = event.getPlayer();
        if (split[0].equalsIgnoreCase("/map")) {
        	if (split.length > 1) {
        		if (split[1].equals("render")) {
		        	mgr.touch(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
		        	event.setCancelled(true);
        		}
        	}
        }
	}
}