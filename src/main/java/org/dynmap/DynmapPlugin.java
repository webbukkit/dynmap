package org.dynmap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldListener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.Event.Listener;
import org.dynmap.debug.Debug;
import org.dynmap.debug.Debugger;
import org.dynmap.permissions.NijikokunPermissions;
import org.dynmap.permissions.OpPermissions;
import org.dynmap.permissions.PermissionProvider;
import org.dynmap.web.HttpServer;
import org.dynmap.web.Json;
import org.dynmap.web.handlers.ClientConfigurationHandler;
import org.dynmap.web.handlers.ClientUpdateHandler;
import org.dynmap.web.handlers.FilesystemHandler;
import org.dynmap.web.handlers.SendMessageHandler;
import org.dynmap.web.handlers.RegionHandler;

public class DynmapPlugin extends JavaPlugin {
    public HttpServer webServer = null;
    public MapManager mapManager = null;
    public PlayerList playerList;
    public ConfigurationNode configuration;
    public HashSet<String> enabledTriggers = new HashSet<String>();
    public PermissionProvider permissions;
    public HeroChatHandler hchand;

    public Timer timer;

    public static File dataDirectory;
    public static File tilesDirectory;
    
    public MapManager getMapManager() {
        return mapManager;
    }

    public HttpServer getWebServer() {
        return webServer;
    }

    public void onEnable() {
        permissions = NijikokunPermissions.create(getServer(), "dynmap");
        if (permissions == null)
            permissions = new OpPermissions(new String[] { "fullrender" });

        dataDirectory = this.getDataFolder();

        org.bukkit.util.config.Configuration bukkitConfiguration = new org.bukkit.util.config.Configuration(new File(this.getDataFolder(), "configuration.txt"));
        bukkitConfiguration.load();
        configuration = new ConfigurationNode(bukkitConfiguration);

        loadDebuggers();

        tilesDirectory = getFile(configuration.getString("tilespath", "web/tiles"));
        if (!tilesDirectory.isDirectory() && !tilesDirectory.mkdirs()) {
            Log.warning("Could not create directory for tiles ('" + tilesDirectory + "').");
        }

        playerList = new PlayerList(getServer(), getFile("hiddenplayers.txt"), configuration);
        playerList.load();

        mapManager = new MapManager(this, configuration);
        mapManager.startRendering();

        if (!configuration.getBoolean("disable-webserver", false)) {
            loadWebserver();
        }

        if (configuration.getBoolean("jsonfile", false)) {
            jsonConfig();
            int jsonInterval = configuration.getInteger("jsonfile-interval", 1) * 1000;
            timer = new Timer();
            timer.scheduleAtFixedRate(new JsonTimerTask(this, configuration), jsonInterval, jsonInterval);
        }

        hchand = new HeroChatHandler(configuration, this, getServer());

        enabledTriggers.clear();
        List<String> triggers = configuration.getStrings("render-triggers", new ArrayList<String>());
        if (triggers != null)
        {
            for (Object trigger : triggers) {
                enabledTriggers.add((String) trigger);
            }
        }

        registerEvents();

        /* Print version info */
        PluginDescriptionFile pdfFile = this.getDescription();
        Log.info("version " + pdfFile.getVersion() + " is enabled" );
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
        int port = configuration.getInteger("webserver-port", 8123);

        webServer = new HttpServer(bindAddress, port);
        webServer.handlers.put("/", new FilesystemHandler(getFile(configuration.getString("webpath", "web"))));
        webServer.handlers.put("/tiles/", new FilesystemHandler(tilesDirectory));
        webServer.handlers.put("/up/", new ClientUpdateHandler(mapManager, playerList, getServer(), configuration.getBoolean("health-in-json", false)));
        webServer.handlers.put("/up/configuration", new ClientConfigurationHandler(configuration.getNode("web")));
        /* See if regions configuration branch is present */
        for(ConfigurationNode type : configuration.getNodes("web/components")) {
            if(type.getString("type").equalsIgnoreCase("regions")) {
                String fname = type.getString("filename", "regions.yml");
                fname = "/standalone/" + fname.substring(0, fname.lastIndexOf('.')) + "_"; /* Find our path base */
                webServer.handlers.put(fname + "*", new RegionHandler(type));
            }
        }
        
        if (configuration.getNode("web").getBoolean("allowwebchat", false)) {
            SendMessageHandler messageHandler = new SendMessageHandler() {{
                maximumMessageInterval = (configuration.getNode("web").getInteger("webchat-interval", 1) * 1000);
                spamMessage = "\""+configuration.getNode("web").getString("spammessage", "You may only chat once every %interval% seconds.")+"\"";
                onMessageReceived.addListener(new Listener<SendMessageHandler.Message>() {
                    @Override
                    public void triggered(Message t) {
                        webChat(t.name, t.message);
                    }
                });
            }};

            webServer.handlers.put("/up/sendmessage", messageHandler);
        }

        try {
            webServer.startServer();
        } catch (IOException e) {
            Log.severe("Failed to start WebServer on " + bindAddress + ":" + port + "!");
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
            if (isTrigger("blockplaced"))
                pm.registerEvent(Event.Type.BLOCK_PLACE, renderTrigger, Priority.Monitor, this);
            if (isTrigger("blockbreak"))
                pm.registerEvent(Event.Type.BLOCK_BREAK, renderTrigger, Priority.Monitor, this);
        }
        {
            PlayerListener renderTrigger = new PlayerListener() {
                @Override
                public void onPlayerJoin(PlayerJoinEvent event) {
                    mm.touch(event.getPlayer().getLocation());
                }

                @Override
                public void onPlayerMove(PlayerMoveEvent event) {
                    mm.touch(event.getPlayer().getLocation());
                }
            };
            if (isTrigger("playerjoin"))
                pm.registerEvent(Event.Type.PLAYER_JOIN, renderTrigger, Priority.Monitor, this);
            if (isTrigger("playermove"))
                pm.registerEvent(Event.Type.PLAYER_MOVE, renderTrigger, Priority.Monitor, this);
        }
        {
            WorldListener renderTrigger = new WorldListener() {
                @Override
                public void onChunkLoad(ChunkLoadEvent event) {
                    int x = event.getChunk().getX() * 16 + 8;
                    int z = event.getChunk().getZ() * 16 + 8;
                    mm.touch(new Location(event.getWorld(), x, 127, z));
                }

                /*
                 * @Override public void onChunkGenerated(ChunkLoadEvent event)
                 * { int x = event.getChunk().getX() * 16 + 8; int z =
                 * event.getChunk().getZ() * 16 + 8; mm.touch(new
                 * Location(event.getWorld(), x, 127, z)); }
                 */
            };
            if (isTrigger("chunkloaded"))
                pm.registerEvent(Event.Type.CHUNK_LOAD, renderTrigger, Priority.Monitor, this);
            //if (isTrigger("chunkgenerated")) pm.registerEvent(Event.Type.CHUNK_GENERATED, renderTrigger, Priority.Monitor, this);
        }

        // To announce when players have joined/quit/chatted.
        if (configuration.getNode("web").getBoolean("allowchat", false)) {
            // To handle webchat.
            PlayerListener playerListener = new DynmapPlayerChatListener(this);
            //getServer().getPluginManager().registerEvent(Event.Type.PLAYER_COMMAND, playerListener, Priority.Normal, this);
            pm.registerEvent(Event.Type.PLAYER_CHAT, playerListener, Priority.Monitor, this);
            pm.registerEvent(Event.Type.PLAYER_LOGIN, playerListener, Priority.Monitor, this);
            pm.registerEvent(Event.Type.PLAYER_JOIN, playerListener, Priority.Monitor, this);
            pm.registerEvent(Event.Type.PLAYER_QUIT, playerListener, Priority.Monitor, this);
        }

        // To link configuration to real loaded worlds.
        WorldListener worldListener = new WorldListener() {
            @Override
            public void onWorldLoad(WorldLoadEvent event) {
                mm.activateWorld(event.getWorld());
            }
        };
        pm.registerEvent(Event.Type.WORLD_LOAD, worldListener, Priority.Monitor, this);
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
        List<ConfigurationNode> debuggersConfiguration = configuration.getNodes("debuggers");
        Debug.clearDebuggers();
        for (ConfigurationNode debuggerConfiguration : debuggersConfiguration) {
            try {
                Class<?> debuggerClass = Class.forName((String) debuggerConfiguration.getString("class"));
                Constructor<?> constructor = debuggerClass.getConstructor(JavaPlugin.class, Map.class);
                Debugger debugger = (Debugger) constructor.newInstance(this, debuggerConfiguration);
                Debug.addDebugger(debugger);
            } catch (Exception e) {
                Log.severe("Error loading debugger: " + e);
                e.printStackTrace();
                continue;
            }
        }
    }

    private static final Set<String> commands = new HashSet<String>(Arrays.asList(new String[] {
        "render",
        "hide",
        "show",
        "fullrender" }));

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("dynmap"))
            return false;
        Player player = null;
        if (sender instanceof Player)
            player = (Player) sender;
        if (args.length > 0) {
            String c = args[0];
            if (!commands.contains(c)) {
                return false;
            }

            if (c.equals("render") && checkPlayerPermission(sender,"render")) {
                if (player != null) {
                    int invalidates = mapManager.touch(player.getLocation());
                    sender.sendMessage("Queued " + invalidates + " tiles" + (invalidates == 0
                            ? " (world is not loaded?)"
                            : "..."));
                    return true;
                }
            } else if (c.equals("hide")) {
                if (args.length == 1) {
                    if(player != null && checkPlayerPermission(sender,"hide.self")) {
                        playerList.setVisible(player.getName(),false);
                        sender.sendMessage("You are now hidden on Dynmap.");
                        return true;
                    }
                } else if (checkPlayerPermission(sender,"hide.others")) {
                    for (int i = 1; i < args.length; i++) {
                        playerList.setVisible(args[i],false);
                        sender.sendMessage(args[i] + " is now hidden on Dynmap.");
                    }
                    return true;
                }
            } else if (c.equals("show")) {
                if (args.length == 1) {
                    if(player != null && checkPlayerPermission(sender,"show.self")) {
                        playerList.setVisible(player.getName(),true);
                        sender.sendMessage("You are now visible on Dynmap.");
                        return true;
                    }
                } else if (checkPlayerPermission(sender,"show.others")) {
                    for (int i = 1; i < args.length; i++) {
                        playerList.setVisible(args[i],true);
                        sender.sendMessage(args[i] + " is now visible on Dynmap.");
                    }
                    return true;
                }
            } else if (c.equals("fullrender") && checkPlayerPermission(sender,"fullrender")) {
                if (args.length > 1) {
                    for (int i = 1; i < args.length; i++) {
                        World w = getServer().getWorld(args[i]);
                        if(w != null)
                            mapManager.renderFullWorld(new Location(w, 0, 0, 0));
                    }
                    return true;
                } else if (player != null) {
                    Location loc = player.getLocation();
                    if(loc != null)
                        mapManager.renderFullWorld(loc);
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    private boolean checkPlayerPermission(CommandSender sender, String permission) {
        if (!(sender instanceof Player) || sender.isOp()) {
            return true;
        } else if (!permissions.has(sender, permission.toLowerCase())) {
            sender.sendMessage("You don't have permission to use this command!");
            return false;
        }
        return true;
    }

    private void jsonConfig() {
        File outputFile;
        ConfigurationNode clientConfig = configuration.getNode("web");
        File webpath = new File(configuration.getString("webpath", "web"), "standalone/dynmap_config.json");
        if (webpath.isAbsolute())
            outputFile = webpath;
        else
            outputFile = new File(getDataFolder(), webpath.toString());

        try {
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(Json.stringifyJson(clientConfig).getBytes());
            fos.close();
        } catch (FileNotFoundException ex) {
            Log.severe("Exception while writing JSON-configuration-file.", ex);
        } catch (IOException ioe) {
            Log.severe("Exception while writing JSON-configuration-file.", ioe);
        }
    }

    public void webChat(String name, String message) {
        mapManager.pushUpdate(new Client.ChatMessage("web", name, message));
        Log.info("[WEB]" + name + ": " + message);
        /* Let HeroChat take a look - only broadcast to players if it doesn't handle it */
        if(hchand.sendWebMessageToHeroChat(name, message) == false)
            getServer().broadcastMessage("[WEB]" + name + ": " + message);
    }
}
