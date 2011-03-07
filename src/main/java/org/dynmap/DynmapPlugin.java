package org.dynmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockDamageLevel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldEvent;
import org.bukkit.event.world.WorldListener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;
import org.dynmap.Event.Listener;
import org.dynmap.debug.Debug;
import org.dynmap.debug.Debugger;
import org.dynmap.web.HttpServer;
import org.dynmap.web.Json;
import org.dynmap.web.handlers.ClientConfigurationHandler;
import org.dynmap.web.handlers.ClientUpdateHandler;
import org.dynmap.web.handlers.FilesystemHandler;
import org.dynmap.web.handlers.SendMessageHandler;
import org.dynmap.web.handlers.SendMessageHandler.Message;

public class DynmapPlugin extends JavaPlugin {

    protected static final Logger log = Logger.getLogger("Minecraft");

    public HttpServer webServer = null;
    public MapManager mapManager = null;
    public PlayerList playerList;
    public Configuration configuration;
    public HashSet<String> enabledTriggers = new HashSet<String>();

    public Timer timer;

    public static File dataDirectory;
    public static File tilesDirectory;

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
        dataDirectory = this.getDataFolder();
        
        configuration = new Configuration(new File(this.getDataFolder(), "configuration.txt"));
        configuration.load();

        loadDebuggers();
        
        tilesDirectory = getFile(configuration.getString("tilespath", "web/tiles"));
        if (!tilesDirectory.isDirectory() && !tilesDirectory.mkdirs()) {
            log.warning("Could not create directory for tiles ('" + tilesDirectory + "').");
        }

        playerList = new PlayerList(getServer(), getFile("hiddenplayers.txt"));
        playerList.load();

        mapManager = new MapManager(this, configuration);
        mapManager.startRendering();

        if (!configuration.getBoolean("disable-webserver", false)) {
            loadWebserver();
        }

        if (configuration.getBoolean("jsonfile", false)) {
            jsonConfig();
            int jsonInterval = configuration.getInt("jsonfile-interval", 1) * 1000;
            timer = new Timer();
            timer.scheduleAtFixedRate(new JsonTimerTask(this, configuration), jsonInterval, jsonInterval);
        }

        enabledTriggers.clear();
        for (Object trigger : configuration.getList("render-triggers")) {
            enabledTriggers.add((String) trigger);
        }

        registerEvents();
    }

    public void loadWebserver() {
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

        boolean allowchat = configuration.getBoolean("allowchat", true);
        if (allowchat == true) {
            SendMessageHandler messageHandler = new SendMessageHandler();
	        messageHandler.onMessageReceived.addListener(new Listener<SendMessageHandler.Message>() {
	            @Override
	            public void triggered(Message t) {
	                mapManager.pushUpdate(new Client.WebChatMessage(t.name, t.message));
	                log.info("[WEB]" + t.name + ": " + t.message);
	                getServer().broadcastMessage("[WEB]" + t.name + ": " + t.message);
	            }
	        });
	        webServer.handlers.put("/up/sendmessage", messageHandler);
        }

        try {
            webServer.startServer();
        } catch (IOException e) {
            log.severe("Failed to start WebServer on " + bindAddress + ":" + port + "!");
        }
    }

    public void onDisable() {
        mapManager.stopRendering();

        if (webServer != null) {
            webServer.shutdown();
            webServer = null;
        }

        if (timer != null) {
            timer.cancel();
        }

        Debug.clearDebuggers();
    }

    public boolean isTrigger(String s) {
        return enabledTriggers.contains(s);
    }
    
    public void registerEvents() {
        final PluginManager pm = getServer().getPluginManager();
        final MapManager mm = mapManager;
        
        // To trigger rendering.
        {
            BlockListener renderTrigger = new BlockListener() {
                @Override
                public void onBlockPlace(BlockPlaceEvent event) {
                    mm.touch(event.getBlockPlaced().getLocation());
                }

                @Override
                public void onBlockBreak(BlockBreakEvent event) {
                    mm.touch(event.getBlock().getLocation());
                }
            };
            if (isTrigger("blockplaced")) pm.registerEvent(Event.Type.BLOCK_PLACED, renderTrigger, Priority.Monitor, this);
            if (isTrigger("blockbreak")) pm.registerEvent(Event.Type.BLOCK_BREAK, renderTrigger, Priority.Monitor, this);
        }
        {
            PlayerListener renderTrigger = new PlayerListener() {
                @Override
                public void onPlayerJoin(PlayerEvent event) {
                    mm.touch(event.getPlayer().getLocation());
                }
                @Override
                public void onPlayerMove(PlayerMoveEvent event) {
                    mm.touch(event.getPlayer().getLocation());
                }
            };
            if (isTrigger("playerjoin")) pm.registerEvent(Event.Type.PLAYER_JOIN, renderTrigger, Priority.Monitor, this);
            if (isTrigger("playermove")) pm.registerEvent(Event.Type.PLAYER_MOVE, renderTrigger, Priority.Monitor, this);
        }
        {
            WorldListener renderTrigger = new WorldListener() {
                @Override
                public void onChunkLoaded(ChunkLoadEvent event) {
                    int x = event.getChunk().getX() * 16 + 8;
                    int z = event.getChunk().getZ() * 16 + 8;
                    mm.touch(new Location(event.getWorld(), x, 127, z));
                }
                
                /*@Override
                public void onChunkGenerated(ChunkLoadEvent event) {
                    int x = event.getChunk().getX() * 16 + 8;
                    int z = event.getChunk().getZ() * 16 + 8;
                    mm.touch(new Location(event.getWorld(), x, 127, z));
                }*/
            };
            if (isTrigger("chunkloaded")) pm.registerEvent(Event.Type.CHUNK_LOADED, renderTrigger, Priority.Monitor, this);
            //if (isTrigger("chunkgenerated")) pm.registerEvent(Event.Type.CHUNK_GENERATED, renderTrigger, Priority.Monitor, this);
        }
        
        // To link configuration to real loaded worlds.
        WorldListener worldListener = new WorldListener() {
            @Override
            public void onWorldLoaded(WorldEvent event) {
                mm.activateWorld(event.getWorld());
            }
        };
        pm.registerEvent(Event.Type.WORLD_LOADED, worldListener, Priority.Monitor, this);

        // To handle webchat.
        PlayerListener playerListener = new DynmapPlayerListener(this);
        //getServer().getPluginManager().registerEvent(Event.Type.PLAYER_COMMAND, playerListener, Priority.Normal, this);
        pm.registerEvent(Event.Type.PLAYER_CHAT, playerListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.PLAYER_LOGIN, playerListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.PLAYER_JOIN, playerListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.PLAYER_QUIT, playerListener, Priority.Monitor, this);

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
        return combinePaths(getDataFolder(), path);
    }

    protected void loadDebuggers() {
        Object debuggersConfiguration = configuration.getProperty("debuggers");
        Debug.clearDebuggers();
        if (debuggersConfiguration != null) {
            for (Object debuggerConfiguration : (List<?>) debuggersConfiguration) {
                Map<?, ?> debuggerConfigurationMap = (Map<?, ?>) debuggerConfiguration;
                try {
                    Class<?> debuggerClass = Class.forName((String) debuggerConfigurationMap.get("class"));
                    Constructor<?> constructor = debuggerClass.getConstructor(JavaPlugin.class, Map.class);
                    Debugger debugger = (Debugger) constructor.newInstance(this, debuggerConfigurationMap);
                    Debug.addDebugger(debugger);
                } catch (Exception e) {
                    log.severe("Error loading debugger: " + e);
                    e.printStackTrace();
                    continue;
                }

            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("dynmap"))
            return false;
        Player player = null;
        if (sender instanceof Player)
            player = (Player) sender;
        if (args.length > 0) {
            if (args[0].equals("render")) {
                if (sender instanceof Player) {
                    mapManager.touch(((Player) sender).getLocation());
                    return true;
                }
            } else if (args[0].equals("hide")) {
                if (args.length == 1 && player != null) {
                    playerList.hide(player.getName());
                    sender.sendMessage("You are now hidden on Dynmap.");
                    return true;
                } else {
                    for (int i = 1; i < args.length; i++) {
                        playerList.hide(args[i]);
                        sender.sendMessage(args[i] + " is now hidden on Dynmap.");
                    }
                    return true;
                }
            } else if (args[0].equals("show")) {
                if (args.length == 1 && player != null) {
                    playerList.show(player.getName());
                    sender.sendMessage("You are now visible on Dynmap.");
                    return true;
                } else {
                    for (int i = 1; i < args.length; i++) {
                        playerList.show(args[i]);
                        sender.sendMessage(args[i] + " is now visible on Dynmap.");
                    }
                    return true;
                }
            } else if (args[0].equals("fullrender")) {
                if (player == null || player.isOp()) {
                    if (args.length > 2) {
                        for (int i = 1; i < args.length; i++) {
                            World w = getServer().getWorld(args[i]);
                            mapManager.renderFullWorld(new Location(w, 0, 0, 0));
                        }
                        return true;
                    } else if (player != null) {
                        mapManager.renderFullWorld(player.getLocation());
                        return true;
                    }
                } else if (player != null) {
                    player.sendMessage("Only OPs are allowed to use this command!");
                    return true;
                }
            }
        }
        return false;
    }

    private void jsonConfig() {
        File outputFile;
        Map<?, ?> clientConfig = (Map<?, ?>) configuration.getProperty("web");
        File webpath = new File(configuration.getString("webpath", "web"), "dynmap_config.json");
        if (webpath.isAbsolute())
            outputFile = webpath;
        else
            outputFile = new File(getDataFolder(), webpath.toString());

        try {
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(Json.stringifyJson(clientConfig).getBytes());
            fos.close();
        } catch (FileNotFoundException ex) {
            log.log(Level.SEVERE, "Exception while writing JSON-configuration-file.", ex);
        } catch (IOException ioe) {
            log.log(Level.SEVERE, "Exception while writing JSON-configuration-file.", ioe);
        }
    }
}
