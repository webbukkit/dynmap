package org.dynmap;

import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerListener;

public class DynmapPlayerListener extends PlayerListener {
	private static final Logger log = Logger.getLogger("Minecraft");
	private MapManager mgr;
	
	public DynmapPlayerListener(MapManager mgr) {
		this.mgr = mgr;
	}
	
	@Override
	public void onPlayerCommand(PlayerChatEvent event) {
		String[] split = event.getMessage().split(" ");
        Player player = event.getPlayer();
        if (split[0].equalsIgnoreCase("/map_render")) {
        	mgr.touch(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
        	event.setCancelled(true);
        }
	}
}