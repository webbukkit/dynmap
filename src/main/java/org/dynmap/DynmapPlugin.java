package org.dynmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.logging.Logger;
import java.util.Timer;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;
import org.dynmap.Event.Listener;
import org.dynmap.debug.Debug;
import org.dynmap.debug.LogDebugger;
import org.dynmap.web.HttpServer;
import org.dynmap.web.handlers.ClientConfigurationHandler;
import org.dynmap.web.handlers.ClientUpdateHandler;
import org.dynmap.web.handlers.FilesystemHandler;
import org.dynmap.web.handlers.SendMessageHandler;
import org.dynmap.web.handlers.SendMessageHandler.Message;
import org.dynmap.web.Json;

public class DynmapPlugin extends JavaPlugin {

    protected static final Logger log = Logger.getLogger("Minecraft");

    private HttpServer webServer = null;
    private MapManager mapManager = null;
    private PlayerList playerList;
    private Configuration configuration;

    public static File tilesDirectory;
	private Timer timer;
    public static File dataRoot;

    public DynmapPlugin(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File folder, File plugin, ClassLoader cLoader) {
        super(pluginLoader, instance, desc, folder, plugin, cLoader);
        dataRoot = folder;
    }

    public World getWorld() {
        return getServer().getWorlds().get(0);
    }

    public MapManager getMapManager() {
        return mapManager;
    }

    public HttpServer getWebServer() {
        return webServer;
    }

    public void onEnable() {
        Debug.addDebugger(new LogDebugger());

        configuration = new Configuration(new File(this.getDataFolder(), "configuration.txt"));
        configuration.load();

        tilesDirectory = getFile(configuration.getString("tilespath", "web/tiles"));
        tilesDirectory.mkdirs();

        playerList = new PlayerList(getServer());
        playerList.load();

        mapManager = new MapManager(configuration);
        mapManager.startRendering();

		if(!configuration.getBoolean("disable-webserver", true)) {
			InetAddress bindAddress;
			{
				String address = configuration.getString("webserver-bindaddress", "0.0.0.0");
				try {
					bindAddress = address.equals("0.0.0.0")
							? null
							: InetAddress.getByName(address);
				} catch (UnknownHostException e) {
					bindAddress = null;
				}
			}
			int port = configuration.getInt("webserver-port", 8123);

			webServer = new HttpServer(bindAddress, port);
			webServer.handlers.put("/", new FilesystemHandler(getFile(configuration.getString("webpath", "web"))));
			webServer.handlers.put("/tiles/", new FilesystemHandler(tilesDirectory));
			webServer.handlers.put("/up/", new ClientUpdateHandler(mapManager, playerList, getServer()));
			webServer.handlers.put("/up/configuration", new ClientConfigurationHandler((Map<?, ?>) configuration.getProperty("web")));

			SendMessageHandler messageHandler = new SendMessageHandler();
			messageHandler.onMessageReceived.addListener(new Listener<SendMessageHandler.Message>() {
				@Override
				public void triggered(Message t) {
					log.info("[WEB] " + t.name + ": " + t.message);
					getServer().broadcastMessage("[WEB] " + t.name + ": " + t.message);
				}
			});
			webServer.handlers.put("/up/sendmessage", messageHandler);

			try {
				webServer.startServer();
			} catch (IOException e) {
				log.severe("Failed to start WebServer on " + bindAddress + ":" + port + "!");
			}
		}

		if(configuration.getBoolean("jsonfile", false)) {
			jsonConfig();
			int jsonInterval = configuration.getInt("jsonfile-interval", 1) * 1000;
			 timer = new Timer();
			 timer.scheduleAtFixedRate(new JsonTimerTask(this, configuration), jsonInterval, jsonInterval);
		}

        registerEvents();
    }

    public void onDisable() {
        mapManager.stopRendering();

        if (webServer != null) {
            webServer.shutdown();
            webServer = null;
        }

		if(timer != null) {
			timer.cancel();
		}

        Debug.clearDebuggers();
    }

    public void registerEvents() {
        BlockListener blockListener = new DynmapBlockListener(mapManager);
        getServer().getPluginManager().registerEvent(Event.Type.BLOCK_PLACED, blockListener, Priority.Monitor, this);
        getServer().getPluginManager().registerEvent(Event.Type.BLOCK_DAMAGED, blockListener, Priority.Monitor, this);

        PlayerListener playerListener = new DynmapPlayerListener(mapManager, playerList, configuration);
        getServer().getPluginManager().registerEvent(Event.Type.PLAYER_COMMAND, playerListener, Priority.Normal, this);
        getServer().getPluginManager().registerEvent(Event.Type.PLAYER_CHAT, playerListener, Priority.Normal, this);
    }

    private static File combinePaths(File parent, String path) {
        return combinePaths(parent, new File(path));
    }

    private static File combinePaths(File parent, File path) {
        if (path.isAbsolute())
            return path;
        return new File(parent, path.getPath());
    }

    public File getFile(String path) {
        return combinePaths(DynmapPlugin.dataRoot, path);
    }

	private void jsonConfig()
	{
		File outputFile;
		Map<?, ?> clientConfig =  (Map<?, ?>) configuration.getProperty("web");
		File webpath = new File(configuration.getString("webpath", "web"), "dynmap_config.json");
		if(webpath.isAbsolute())
			outputFile = webpath;
		else
			outputFile = new File(DynmapPlugin.dataRoot, webpath.toString());

		try
		{
			FileOutputStream fos = new FileOutputStream(outputFile);
			fos.write(Json.stringifyJson(clientConfig).getBytes());
			fos.close();
		}
		catch(FileNotFoundException ex)
		{
			System.out.println("FileNotFoundException : " + ex);
		}
		catch(IOException ioe)
		{
			System.out.println("IOException : " + ioe);
		}
	}
}
