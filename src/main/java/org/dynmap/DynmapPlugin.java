package org.dynmap;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.logging.Logger;

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
import org.dynmap.debug.Debug;
import org.dynmap.debug.LogDebugger;
import org.dynmap.web.HttpServer;
import org.dynmap.web.handlers.ClientConfigurationHandler;
import org.dynmap.web.handlers.ClientUpdateHandler;
import org.dynmap.web.handlers.FilesystemHandler;

public class DynmapPlugin extends JavaPlugin {

    protected static final Logger log = Logger.getLogger("Minecraft");

    private HttpServer webServer = null;
    private MapManager mapManager = null;
    private PlayerList playerList;
    private Configuration configuration;

    public static File tilesDirectory;
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

        try {
            webServer.startServer();
        } catch (IOException e) {
            log.severe("Failed to start WebServer on " + bindAddress + ":" + port + "!");
        }

        registerEvents();
    }

    public void onDisable() {
        mapManager.stopRendering();

        if (webServer != null) {
            webServer.shutdown();
            webServer = null;
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
}
