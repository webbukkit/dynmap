package org.dynmap;

import java.io.File;
import java.util.HashMap;
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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
import org.dynmap.regions.RegionHandler;
import org.dynmap.web.HttpServer;
import org.dynmap.web.Json;
import org.dynmap.web.handlers.ClientConfigurationHandler;
import org.dynmap.web.handlers.ClientUpdateHandler;
import org.dynmap.web.handlers.FilesystemHandler;
import org.dynmap.web.handlers.SendMessageHandler;
import org.json.simple.JSONObject;

public class DynmapPlugin extends JavaPlugin {
    public HttpServer webServer = null;
    public MapManager mapManager = null;
    public PlayerList playerList;
    public ConfigurationNode configuration;
    public HashSet<String> enabledTriggers = new HashSet<String>();
    public PermissionProvider permissions;
    public ComponentManager componentManager = new ComponentManager();
    public Events events = new Events();

    public static File dataDirectory;
    public static File tilesDirectory;
    
    public MapManager getMapManager() {
        return mapManager;
    }

    public HttpServer getWebServer() {
        return webServer;
    }
    
    @Override
    public void onEnable() {
        permissions = NijikokunPermissions.create(getServer(), "dynmap");
        if (permissions == null)
            permissions = new OpPermissions(new String[] { "fullrender", "reload" });

        dataDirectory = this.getDataFolder();

        org.bukkit.util.config.Configuration bukkitConfiguration = new org.bukkit.util.config.Configuration(new File(this.getDataFolder(), "configuration.txt"));
        bukkitConfiguration.load();
        configuration = new ConfigurationNode(bukkitConfiguration);

        processWorldTemplates(configuration);
        
        loadDebuggers();

        tilesDirectory = getFile(configuration.getString("tilespath", "web/tiles"));
        if (!tilesDirectory.isDirectory() && !tilesDirectory.mkdirs()) {
            Log.warning("Could not create directory for tiles ('" + tilesDirectory + "').");
        }

        playerList = new PlayerList(getServer(), getFile("hiddenplayers.txt"), configuration);
        playerList.load();

        mapManager = new MapManager(this, configuration);
        mapManager.startRendering();

        loadWebserver();

        enabledTriggers.clear();
        List<String> triggers = configuration.getStrings("render-triggers", new ArrayList<String>());
        if (triggers != null)
        {
            for (Object trigger : triggers) {
                enabledTriggers.add((String) trigger);
            }
        }
        
        // Load components.
        for(Component component : configuration.<Component>createInstances("components", new Class<?>[] { DynmapPlugin.class }, new Object[] { this })) {
            componentManager.add(component);
        }
        Log.info("Loaded " + componentManager.components.size() + " components.");

        registerEvents();

        if (!configuration.getBoolean("disable-webserver", false)) {
            startWebserver();
        }

        /* Print version info */
        PluginDescriptionFile pdfFile = this.getDescription();
        Log.info("version " + pdfFile.getVersion() + " is enabled" );
        
        events.<Object>trigger("initialized", null);
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
        webServer.handlers.put("/up/configuration", new ClientConfigurationHandler(this));
    }
    
    public void startWebserver() {
        try {
            webServer.startServer();
        } catch (IOException e) {
            Log.severe("Failed to start WebServer on " + webServer.getAddress() + ":" + webServer.getPort() + "!");
        }
    }

    @Override
    public void onDisable() {
        int componentCount = componentManager.components.size();
        for(Component component : componentManager.components) {
            component.dispose();
        }
        componentManager.clear();
        Log.info("Unloaded " + componentCount + " components.");
        
        mapManager.stopRendering();

        if (webServer != null) {
            webServer.shutdown();
            webServer = null;
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
                pm.registerEvent(org.bukkit.event.Event.Type.BLOCK_PLACE, renderTrigger, org.bukkit.event.Event.Priority.Monitor, this);
            if (isTrigger("blockbreak"))
                pm.registerEvent(org.bukkit.event.Event.Type.BLOCK_BREAK, renderTrigger, org.bukkit.event.Event.Priority.Monitor, this);
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
                pm.registerEvent(org.bukkit.event.Event.Type.PLAYER_JOIN, renderTrigger, org.bukkit.event.Event.Priority.Monitor, this);
            if (isTrigger("playermove"))
                pm.registerEvent(org.bukkit.event.Event.Type.PLAYER_MOVE, renderTrigger, org.bukkit.event.Event.Priority.Monitor, this);
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
                pm.registerEvent(org.bukkit.event.Event.Type.CHUNK_LOAD, renderTrigger, org.bukkit.event.Event.Priority.Monitor, this);
            //if (isTrigger("chunkgenerated")) pm.registerEvent(Event.Type.CHUNK_GENERATED, renderTrigger, Priority.Monitor, this);
        }

        // To link configuration to real loaded worlds.
        WorldListener worldListener = new WorldListener() {
            @Override
            public void onWorldLoad(WorldLoadEvent event) {
                mm.activateWorld(event.getWorld());
            }
        };
        pm.registerEvent(org.bukkit.event.Event.Type.WORLD_LOAD, worldListener, org.bukkit.event.Event.Priority.Monitor, this);
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
        "fullrender",
        "reload" }));

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
            } else if (c.equals("reload") && checkPlayerPermission(sender, "reload")) {
                sender.sendMessage("Reloading Dynmap...");
                onDisable();
                onEnable();
                sender.sendMessage("Dynmap reloaded");
                return true;
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
    /* Prepare for sky worlds... */
    private static final String[] templateworldtypes = { "normal", "nether" };
    private static final Environment[] templateworldenv = { Environment.NORMAL, Environment.NETHER };
    
    private void processWorldTemplates(ConfigurationNode node) {
        ConfigurationNode template = node.getNode("template");
        if(template == null)
            return;
        List<ConfigurationNode> worlds = node.getNodes("worlds");
        boolean worldsupdated = false;        
        /* Initialize even if no worlds section */
        if(worlds == null) {
            worlds = new ArrayList<ConfigurationNode>();
            worldsupdated = true;
        }
        /* Iternate by world type - so that order in templateworldtypes drives our default order */
        for(int wtype = 0; wtype < templateworldtypes.length; wtype++) {
            ConfigurationNode typetemplate = template.getNode(templateworldtypes[wtype]);
            if(typetemplate == null)
                continue;
            for(World w : getServer().getWorlds()) {    /* Roll through worlds */
                String wn = w.getName();                
                /* Find node for this world, if any */
                ConfigurationNode world = null;
                int index;
                for(index = 0; index < worlds.size(); index++) {
                    ConfigurationNode ww = worlds.get(index);
                    if(wn.equals(ww.getString("name", ""))) {
                        world = ww;
                        break;
                    }
                }
                /* Check type of world - skip if not right for current template */
                if(w.getEnvironment() != templateworldenv[wtype])
                    continue;
                /* World not found - need to use template */
                if(world == null) {
                    ConfigurationNode newworldnode = new ConfigurationNode(new HashMap<String,Object>(typetemplate));   /* Copy it */
                    newworldnode.put("name", w.getName());
                    newworldnode.put("title", w.getName());
                    worlds.add(newworldnode);
                    worldsupdated = true;
                    Log.info("World '" + w.getName() + "' configuration inherited from template");
                }
                else {  /* Else, definition is there, but may be incomplete */
                    boolean wupd = false;
                    List<ConfigurationNode> tempmaps = typetemplate.getList("maps");
                    if((tempmaps != null) && (world.getNode("maps") == null)) {    /* World with no maps section */
                        world.put("maps", tempmaps);
                        Log.info("World '" + w.getName() + "' configuration inherited maps from template");
                        wupd = true;
                    }
                    ConfigurationNode tempcenter = typetemplate.getNode("center");
                    if((tempcenter != null) && (world.getNode("center") == null)) {   /* World with no center */
                        world.put("center", new ConfigurationNode(new HashMap<String,Object>(tempcenter)));
                        Log.info("World '" + w.getName() + "' configuration inherited center from template");
                        wupd = true;                    
                    }
                    if(world.getString("title", null) == null) {
                        world.put("title", w.getName());
                        wupd = true;                    
                    }
                    if(wupd) {
                        worldsupdated = true;
                        worlds.set(index, world);
                    }
                }
            }
        }
        if(worldsupdated) {
            node.put("worlds", worlds);
        }
    }
}
