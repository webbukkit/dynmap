package org.dynmap.debug;

import java.util.HashSet;

import org.bukkit.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class BukkitPlayerDebugger implements Debugger {
	private JavaPlugin plugin;
	private HashSet<Player> debugees = new HashSet<Player>();
	private String debugCommand;
	private String undebugCommand;
	private String prepend;
	
	public BukkitPlayerDebugger(JavaPlugin plugin) {
		this.plugin = plugin;
		
		PluginDescriptionFile pdfFile = plugin.getDescription();
		debugCommand = "/debug_" + pdfFile.getName();
		undebugCommand = "/undebug_" + pdfFile.getName();
		prepend = pdfFile.getName() + ": ";
	}
	
	public void enable() {
		plugin.getServer().getPluginManager().registerEvent(Event.Type.PLAYER_COMMAND, new CommandListener(), Priority.Normal, plugin);
		plugin.getServer().getPluginManager().registerEvent(Event.Type.PLAYER_QUIT, new CommandListener(), Priority.Normal, plugin);
	}
	
	public void disable() {
		clearDebugees();
	}
	
	public void addDebugee(Player p) {
		debugees.add(p);
	}
	
	public void removeDebugee(Player p) {
		debugees.remove(p);
	}
	
	public void clearDebugees() {
		debugees.clear();
	}
	
	public void debug(String message) {
		for (Player p : debugees) {
			p.sendMessage(prepend + message);
		}
	}
	
	protected class CommandListener extends PlayerListener {
		@Override
		public void onPlayerCommand(PlayerChatEvent event) {
			String[] split = event.getMessage().split(" ");
	        Player player = event.getPlayer();
	        if (split[0].equalsIgnoreCase(debugCommand)) {
	        	addDebugee(player);
	        	event.setCancelled(true);
	        } else if (split[0].equalsIgnoreCase(undebugCommand)) {
	        	removeDebugee(player);
	        	event.setCancelled(true);
	        }
		}
		
		@Override
		public void onPlayerQuit(PlayerEvent event) {
			removeDebugee(event.getPlayer());
		}
	}
}
