package org.dynmap;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.util.config.ConfigurationNode;
import org.dynmap.debug.Debugger;
import org.dynmap.kzedmap.KzedMap;

public class MapManager extends Thread {
	protected static final Logger log = Logger.getLogger("Minecraft");

	private World world;
	private Debugger debugger;
	private MapType[] maps;
	public StaleQueue staleQueue;
	public ChatQueue chatQueue;
	public PlayerList playerList;

	/* lock for our data structures */
	public static final Object lock = new Object();

	/* whether the worker thread should be running now */
	private boolean running = false;

	/* path to image tile directory */
	public File tileDirectory;
	
	/* web files location */
	public File webDirectory;
	
	/* bind web server to ip-address */
	public String bindaddress = "0.0.0.0";
	
	/* port to run web server on */
	public int serverport = 8123;
	
	/* time to pause between rendering tiles (ms) */
	public int renderWait = 500;
	
	public void debug(String msg)
	{
		debugger.debug(msg);
	}
	
	private static File combinePaths(File parent, String path) { return combinePaths(parent, new File(path)); } 
	
	private static File combinePaths(File parent, File path) {
		if (path.isAbsolute()) return path;
		return new File(parent, path.getPath());
	}
	
	public MapManager(World world, Debugger debugger, ConfigurationNode configuration)
	{
		this.world = world;
		this.debugger = debugger;
		this.staleQueue = new StaleQueue();
		this.chatQueue = new ChatQueue();
		
		tileDirectory = combinePaths(DynmapPlugin.dataRoot, configuration.getString("tilespath", "web/tiles"));
		webDirectory = combinePaths(DynmapPlugin.dataRoot, configuration.getString("webpath", "web"));
		renderWait = (int)(configuration.getDouble("renderinterval", 0.5) * 1000);
		
		if (!tileDirectory.isDirectory())
			tileDirectory.mkdirs();
		
		maps = loadMapTypes(configuration);
	}
	
	private MapType[] loadMapTypes(ConfigurationNode configuration) {
		List<?> configuredMaps = (List<?>)configuration.getProperty("maps");
		ArrayList<MapType> mapTypes = new ArrayList<MapType>();
		for(Object configuredMapObj : configuredMaps) {
			try {
				@SuppressWarnings("unchecked")
				Map<String, Object> configuredMap = (Map<String, Object>)configuredMapObj;
				String typeName = (String)configuredMap.get("class");
				log.info("Loading map '" + typeName.toString() + "'...");
				Class<?> mapTypeClass = Class.forName(typeName);
				Constructor<?> constructor = mapTypeClass.getConstructor(MapManager.class, World.class, Debugger.class, Map.class);
				MapType mapType = (MapType)constructor.newInstance(this, world, debugger, configuredMap);
				mapTypes.add(mapType);
			} catch (Exception e) {
				debugger.error("Error loading map", e);
			}
		}
		MapType[] result = new MapType[mapTypes.size()];
		mapTypes.toArray(result);
		return result;
	}
	
	/* initialize and start map manager */
	public void startManager()
	{		
		synchronized(lock) {
		running = true;
		this.start();
		try {
			this.setPriority(MIN_PRIORITY);
			log.info("Set minimum priority for worker thread");
		} catch(SecurityException e) {
			log.info("Failed to set minimum priority for worker thread!");
		}
		}
	}

	/* stop map manager */
	public void stopManager()
	{
		synchronized(lock) {
			if(!running)
				return;
	
			log.info("Stopping map renderer...");
			running = false;
	
			try {
				this.join();
			} catch(InterruptedException e) {
				log.info("Waiting for map renderer to stop is interrupted");
			}
		}
	}

	/* the worker/renderer thread */
	public void run()
	{
		try {
			log.info("Map renderer has started.");
	
			while(running) {
				boolean found = false;
	
				MapTile t = staleQueue.popStaleTile();
				if(t != null) {
					debugger.debug("rendering tile " + t + "...");
					t.getMap().render(t);
	
					staleQueue.onTileUpdated(t);
	
					try {
						Thread.sleep(renderWait);
					} catch(InterruptedException e) {
					}
	
					found = true;
				}
	
				if(!found) {
					try {
						Thread.sleep(500);
					} catch(InterruptedException e) {
					}
				}
			}
	
			log.info("Map renderer has stopped.");
		} catch(Exception ex) {
			debugger.error("Exception on rendering-thread: " + ex.toString());
		}
	}

	public void touch(int x, int y, int z) {
		for (int i = 0; i < maps.length; i++) {
			maps[i].touch(new Location(world, x, y, z));
		}
	}
	
	public void invalidateTile(MapTile tile) {
		debugger.debug("invalidating tile " + tile.getName());
		staleQueue.pushStaleTile(tile);
	}
	
	public void addChatEvent(PlayerChatEvent event)
	{
		chatQueue.pushChatMessage(event);
	}
}
