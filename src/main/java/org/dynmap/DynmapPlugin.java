package org.dynmap;

import java.util.logging.Logger;
import java.io.IOException;

import java.io.File;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.block.BlockListener;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.*;
import org.bukkit.util.config.Configuration;
import org.dynmap.debug.BukkitPlayerDebugger;
import org.dynmap.web.WebServer;

public class DynmapPlugin extends JavaPlugin {

	protected static final Logger log = Logger.getLogger("Minecraft");

	private WebServer webServer = null;
	private MapManager mapManager = null;
	private PlayerList playerList;
	
	private BukkitPlayerDebugger debugger = new BukkitPlayerDebugger(this);
	
	public static File dataRoot;
	
	public DynmapPlugin(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File folder, File plugin, ClassLoader cLoader) {
		super(pluginLoader, instance, desc, folder, plugin, cLoader);
		dataRoot = folder;
	}

	public World getWorld() {
		return getServer().getWorlds()[0];
	}
	
	public MapManager getMapManager() {
		return mapManager;
	}
	
	public WebServer getWebServer() {
		return webServer;
	}

	public void onEnable() {
		Configuration configuration = new Configuration(new File(this.getDataFolder(), "configuration.txt"));
		configuration.load();
		
		debugger.enable();
		playerList = new PlayerList(getServer());
		playerList.load();
		
		mapManager = new MapManager(getWorld(), debugger, configuration);
		mapManager.startManager();

		try {
			webServer = new WebServer(mapManager, getWorld(), playerList, debugger, configuration);
		} catch(IOException e) {
			log.info("position failed to start WebServer (IOException)");
		}
		
		registerEvents();
	}

	public void onDisable() {
		mapManager.stopManager();

		if(webServer != null) {
			webServer.shutdown();
			webServer = null;
		}
		debugger.disable();
	}

	public void registerEvents() {
		BlockListener blockListener = new DynmapBlockListener(mapManager);
		getServer().getPluginManager().registerEvent(Event.Type.BLOCK_PLACED, blockListener, Priority.Normal, this);
		getServer().getPluginManager().registerEvent(Event.Type.BLOCK_DAMAGED, blockListener, Priority.Normal, this);
		
		getServer().getPluginManager().registerEvent(Event.Type.PLAYER_COMMAND, new DynmapPlayerListener(mapManager, playerList), Priority.Normal, this);
		getServer().getPluginManager().registerEvent(Event.Type.PLAYER_CHAT, new DynmapPlayerListener(mapManager, playerList), Priority.Normal, this);
	}
}
