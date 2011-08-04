package org.dynmap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
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
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldListener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.debug.Debug;
import org.dynmap.debug.Debugger;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.permissions.NijikokunPermissions;
import org.dynmap.permissions.OpPermissions;
import org.dynmap.permissions.PermissionProvider;
import org.dynmap.web.HttpServer;
import org.dynmap.web.handlers.ClientConfigurationHandler;
import org.dynmap.web.handlers.FilesystemHandler;

public class DynmapPlugin extends JavaPlugin {
    public HttpServer webServer = null;
    public MapManager mapManager = null;
    public PlayerList playerList;
    public ConfigurationNode configuration;
    public HashSet<String> enabledTriggers = new HashSet<String>();
    public PermissionProvider permissions;
    public ComponentManager componentManager = new ComponentManager();
    public Events events = new Events();
    public String deftemplatesuffix = "";
    /* Flag to let code know that we're doing reload - make sure we don't double-register event handlers */
    public boolean is_reload = false;
    private boolean generate_only = false;
    private static boolean ignore_chunk_loads = false; /* Flag keep us from processing our own chunk loads */

    public static File dataDirectory;
    public static File tilesDirectory;
    
    public MapManager getMapManager() {
        return mapManager;
    }

    public HttpServer getWebServer() {
        return webServer;
    }

    /* Add/Replace branches in configuration tree with contribution from a separate file */
    @SuppressWarnings("unchecked")
    private void mergeConfigurationBranch(ConfigurationNode cfgnode, String branch, boolean replace_existing, boolean islist) {
        Object srcbranch = cfgnode.getObject(branch);
        if(srcbranch == null)
            return;
        /* See if top branch is in configuration - if not, just add whole thing */
        Object destbranch = configuration.getObject(branch);
        if(destbranch == null) {    /* Not found */
            configuration.put(branch, srcbranch);   /* Add new tree to configuration */
            return;
        }
        /* If list, merge by "name" attribute */
        if(islist) {
            List<ConfigurationNode> dest = configuration.getNodes(branch);
            List<ConfigurationNode> src = cfgnode.getNodes(branch);
            /* Go through new records : see what to do with each */
            for(ConfigurationNode node : src) {
                String name = node.getString("name", null);
                if(name == null) continue;
                /* Walk destination - see if match */
                boolean matched = false;
                for(ConfigurationNode dnode : dest) {
                    String dname = dnode.getString("name", null);
                    if(dname == null) continue;
                    if(dname.equals(name)) {    /* Match? */
                        if(replace_existing) {
                            dnode.clear();
                            dnode.putAll(node);
                        }
                        matched = true;
                        break;
                    }
                }
                /* If no match, add to end */
                if(!matched) {
                    dest.add(node);
                }
            }
            configuration.put(branch,dest);
        }
        /* If configuration node, merge by key */
        else {
            ConfigurationNode src = cfgnode.getNode(branch);
            ConfigurationNode dest = configuration.getNode(branch);
            for(String key : src.keySet()) {    /* Check each contribution */
                if(dest.containsKey(key)) { /* Exists? */
                    if(replace_existing) {  /* If replacing, do so */
                        dest.put(key, src.getObject(key));
                    }
                }
                else {  /* Else, always add if not there */
                    dest.put(key, src.getObject(key));
                }
            }
        }
    }
    /* Table of default templates - all are resources in dynmap.jar unnder templates/, and go in templates directory when needed */
    private static final String[] stdtemplates = { "normal.txt", "nether.txt", "skylands.txt", "normal-lowres.txt", 
        "nether-lowres.txt", "skylands-lowres.txt", "normal-hires.txt", "nether-hires.txt", "skylands-hires.txt"
    };
    
    private static final String CUSTOM_PREFIX = "custom-";
    /* Load templates from template folder */
    private void loadTemplates() {
        File templatedir = new File(dataDirectory, "templates");
        templatedir.mkdirs();
        /* First, prime the templates directory with default standard templates, if needed */
        for(String stdtemplate : stdtemplates) {
            File f = new File(templatedir, stdtemplate);
            createDefaultFileFromResource("/templates/" + stdtemplate, f);
        }
        /* Now process files */
        String[] templates = templatedir.list();
        /* Go through list - process all ones not starting with 'custom' first */
        for(String tname: templates) {
            /* If matches naming convention */
            if(tname.endsWith(".txt") && (!tname.startsWith(CUSTOM_PREFIX))) {
                File tf = new File(templatedir, tname);
                org.bukkit.util.config.Configuration cfg = new org.bukkit.util.config.Configuration(tf);
                cfg.load();
                ConfigurationNode cn = new ConfigurationNode(cfg);
                /* Supplement existing values (don't replace), since configuration.txt is more custom than these */
                mergeConfigurationBranch(cn, "templates", false, false);
            }
        }
        /* Go through list again - this time do custom- ones */
        for(String tname: templates) {
            /* If matches naming convention */
            if(tname.endsWith(".txt") && tname.startsWith(CUSTOM_PREFIX)) {
                File tf = new File(templatedir, tname);
                org.bukkit.util.config.Configuration cfg = new org.bukkit.util.config.Configuration(tf);
                cfg.load();
                ConfigurationNode cn = new ConfigurationNode(cfg);
                /* This are overrides - replace even configuration.txt content */
                mergeConfigurationBranch(cn, "templates", true, false);
            }
        }
    }
    
    @Override
    public void onEnable() {
        /* Start with clean events */
        events = new Events();
        
        permissions = NijikokunPermissions.create(getServer(), "dynmap");
        if (permissions == null)
            permissions = new OpPermissions(new String[] { "fullrender", "cancelrender", "radiusrender", "resetstats", "reload" });

        dataDirectory = this.getDataFolder();
        /* Load block models */
        HDBlockModels.loadModels(dataDirectory);
        /* Load texture mappings */
        TexturePack.loadTextureMapping(dataDirectory);
        
        /* Initialize confguration.txt if needed */
        File f = new File(this.getDataFolder(), "configuration.txt");
        if(!createDefaultFileFromResource("/configuration.txt", f)) {
            this.setEnabled(false);
            return;
        }
        /* Load configuration.txt */
        org.bukkit.util.config.Configuration bukkitConfiguration = new org.bukkit.util.config.Configuration(f);
        bukkitConfiguration.load();
        configuration = new ConfigurationNode(bukkitConfiguration);

        /* Now, process worlds.txt - merge it in as an override of existing values (since it is only user supplied values) */
        f = new File(this.getDataFolder(), "worlds.txt");
        if(!createDefaultFileFromResource("/worlds.txt", f)) {
            this.setEnabled(false);
            return;
        }
        org.bukkit.util.config.Configuration cfg = new org.bukkit.util.config.Configuration(f);
        cfg.load();
        ConfigurationNode cn = new ConfigurationNode(cfg);
        mergeConfigurationBranch(cn, "worlds", true, true);

        /* Now, process templates */
        loadTemplates();

        Log.verbose = configuration.getBoolean("verbose", true);
        deftemplatesuffix = configuration.getString("deftemplatesuffix", "");
        
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
        Log.verboseinfo("Loaded " + componentManager.components.size() + " components.");

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
        boolean allow_symlinks = configuration.getBoolean("allow-symlinks", false);
        boolean checkbannedips = configuration.getBoolean("check-banned-ips", true);
        int maxconnections = configuration.getInteger("max-sessions", 30);
        if(maxconnections < 2) maxconnections = 2;
        if(allow_symlinks)
        	Log.verboseinfo("Web server is permitting symbolic links");
        else
        	Log.verboseinfo("Web server is not permitting symbolic links");        	
        webServer = new HttpServer(bindAddress, port, checkbannedips, maxconnections);
        webServer.handlers.put("/", new FilesystemHandler(getFile(configuration.getString("webpath", "web")), allow_symlinks));
        webServer.handlers.put("/tiles/", new FilesystemHandler(tilesDirectory, allow_symlinks));
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
        if (componentManager != null) {
            int componentCount = componentManager.components.size();
            for(Component component : componentManager.components) {
                component.dispose();
            }
            componentManager.clear();
            Log.info("Unloaded " + componentCount + " components.");
        }
        
        if (mapManager != null) {
            mapManager.stopRendering();
            mapManager = null;
        }

        if (webServer != null) {
            webServer.shutdown();
            webServer = null;
        }
        
        Debug.clearDebuggers();
    }
    
    public boolean isTrigger(String s) {
        return enabledTriggers.contains(s);
    }

    private boolean onplace;
    private boolean onbreak;
    private boolean onblockform;
    private boolean onblockfade;
    private boolean onblockspread;
    private boolean onleaves;
    private boolean onburn;

    public void registerEvents() {
        final PluginManager pm = getServer().getPluginManager();
        final MapManager mm = mapManager;

        // To trigger rendering.
        {
            BlockListener renderTrigger = new BlockListener() {
                
                @Override
                public void onBlockPlace(BlockPlaceEvent event) {
                    if(event.isCancelled())
                        return;
                    if(onplace)
                        mm.touch(event.getBlockPlaced().getLocation());
                    mm.sscache.invalidateSnapshot(event.getBlock().getLocation());
                }

                @Override
                public void onBlockBreak(BlockBreakEvent event) {
                    if(event.isCancelled())
                        return;
                    if(onbreak)
                        mm.touch(event.getBlock().getLocation());
                    mm.sscache.invalidateSnapshot(event.getBlock().getLocation());
                }

                @Override
                public void onLeavesDecay(LeavesDecayEvent event) {
                    if(event.isCancelled())
                        return;
                    if(onleaves)
                        mm.touch(event.getBlock().getLocation());              
                    mm.sscache.invalidateSnapshot(event.getBlock().getLocation());
                }
                
                @Override
                public void onBlockBurn(BlockBurnEvent event) {
                    if(event.isCancelled())
                        return;
                    if(onburn)
                        mm.touch(event.getBlock().getLocation());
                    mm.sscache.invalidateSnapshot(event.getBlock().getLocation());
                }
                
                @Override
                public void onBlockForm(BlockFormEvent event) {
                    if(event.isCancelled())
                        return;
                    if(onblockform)
                        mm.touch(event.getBlock().getLocation());
                    mm.sscache.invalidateSnapshot(event.getBlock().getLocation());
                }
                @Override
                public void onBlockFade(BlockFadeEvent event) {
                    if(event.isCancelled())
                        return;
                    if(onblockfade)
                        mm.touch(event.getBlock().getLocation());
                    mm.sscache.invalidateSnapshot(event.getBlock().getLocation());
                }
                @Override
                public void onBlockSpread(BlockSpreadEvent event) {
                    if(event.isCancelled())
                        return;
                    if(onblockspread)
                        mm.touch(event.getBlock().getLocation());
                    mm.sscache.invalidateSnapshot(event.getBlock().getLocation());
                }
            };
            onplace = isTrigger("blockplaced");
            pm.registerEvent(org.bukkit.event.Event.Type.BLOCK_PLACE, renderTrigger, org.bukkit.event.Event.Priority.Monitor, this);
            onbreak = isTrigger("blockbreak");
            pm.registerEvent(org.bukkit.event.Event.Type.BLOCK_BREAK, renderTrigger, org.bukkit.event.Event.Priority.Monitor, this);
            if(isTrigger("snowform")) Log.info("The 'snowform' trigger has been deprecated due to Bukkit changes - use 'blockformed'");
            onleaves = isTrigger("leavesdecay");
            pm.registerEvent(org.bukkit.event.Event.Type.LEAVES_DECAY, renderTrigger, org.bukkit.event.Event.Priority.Monitor, this);
            onburn = isTrigger("blockburn");
            pm.registerEvent(org.bukkit.event.Event.Type.BLOCK_BURN, renderTrigger, org.bukkit.event.Event.Priority.Monitor, this);

            onblockform = isTrigger("blockformed");
            try {
                Class.forName("org.bukkit.event.block.BlockFormEvent");
                pm.registerEvent(org.bukkit.event.Event.Type.BLOCK_FORM, renderTrigger, org.bukkit.event.Event.Priority.Monitor, this);
            } catch (ClassNotFoundException cnfx) {
                if(onblockform)
                    Log.info("BLOCK_FORM event not supported by this version of CraftBukkit - event disabled");
            }
            onblockfade = isTrigger("blockfaded");
            try {
                Class.forName("org.bukkit.event.block.BlockFadeEvent");
                pm.registerEvent(org.bukkit.event.Event.Type.BLOCK_FADE, renderTrigger, org.bukkit.event.Event.Priority.Monitor, this);
            } catch (ClassNotFoundException cnfx) {
                if(onblockfade)
                    Log.info("BLOCK_FADE event not supported by this version of CraftBukkit - event disabled");
            }
            onblockspread = isTrigger("blockspread");
            try {
                Class.forName("org.bukkit.event.block.BlockSpreadEvent");
                pm.registerEvent(org.bukkit.event.Event.Type.BLOCK_SPREAD, renderTrigger, org.bukkit.event.Event.Priority.Monitor, this);
            } catch (ClassNotFoundException cnfx) {
                if(onblockspread)
                    Log.info("BLOCK_SPREAD event not supported by this version of CraftBukkit - event disabled");
            }
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
                    if(ignore_chunk_loads)
                        return;
                    if(generate_only) {
                        if(!isNewChunk(event))
                            return;
                        /* Touch extreme corners */
                        int x = event.getChunk().getX() * 16;
                        int z = event.getChunk().getZ() * 16;
                        mm.touch(new Location(event.getWorld(), x, 0, z));
                        mm.touch(new Location(event.getWorld(), x+15, 127, z));
                        mm.touch(new Location(event.getWorld(), x+15, 0, z+15));
                        mm.touch(new Location(event.getWorld(), x, 127, z+15));
                    }
                    else {
                        int x = event.getChunk().getX() * 16 + 8;
                        int z = event.getChunk().getZ() * 16 + 8;
                        mm.touch(new Location(event.getWorld(), x, 127, z));
                    }
                }
                private boolean isNewChunk(ChunkLoadEvent event) {
                    return event.isNewChunk();
                }
            };
            boolean ongenerate = isTrigger("chunkgenerated");
            if(ongenerate) {
                try {   /* Test if new enough bukkit to allow this */
                    ChunkLoadEvent.class.getDeclaredMethod("isNewChunk", new Class[0]);
                } catch (NoSuchMethodException nsmx) {
                    Log.info("Warning: CraftBukkit build does not support function needed for 'chunkgenerated' trigger - disabling");
                    ongenerate = false;
                }
            }
            if(isTrigger("chunkloaded")) {
                generate_only = false;
                pm.registerEvent(org.bukkit.event.Event.Type.CHUNK_LOAD, renderTrigger, org.bukkit.event.Event.Priority.Monitor, this);
            }
            else if(ongenerate) {
                generate_only = true;
                pm.registerEvent(org.bukkit.event.Event.Type.CHUNK_LOAD, renderTrigger, org.bukkit.event.Event.Priority.Monitor, this);
            }
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
                Constructor<?> constructor = debuggerClass.getConstructor(JavaPlugin.class, ConfigurationNode.class);
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
        "cancelrender",
        "radiusrender",
        "reload",
        "stats",
        "resetstats" }));

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
                }
                else {
                    sender.sendMessage("Command can only be issued by player.");
                }
            }
            else if(c.equals("radiusrender") && checkPlayerPermission(sender,"radiusrender")) {
                if (player != null) {
                    int radius = 0;
                    if(args.length > 1) {
                        radius = Integer.parseInt(args[1]); /* Parse radius */
                        if(radius < 0)
                            radius = 0;
                    }
                    Location loc = player.getLocation();
                    if(loc != null)
                        mapManager.renderWorldRadius(loc, sender, radius);
                }
                else {
                    sender.sendMessage("Command can only be issued by player.");
                }
            } else if (c.equals("hide")) {
                if (args.length == 1) {
                    if(player != null && checkPlayerPermission(sender,"hide.self")) {
                        playerList.setVisible(player.getName(),false);
                        sender.sendMessage("You are now hidden on Dynmap.");
                    }
                } else if (checkPlayerPermission(sender,"hide.others")) {
                    for (int i = 1; i < args.length; i++) {
                        playerList.setVisible(args[i],false);
                        sender.sendMessage(args[i] + " is now hidden on Dynmap.");
                    }
                }
            } else if (c.equals("show")) {
                if (args.length == 1) {
                    if(player != null && checkPlayerPermission(sender,"show.self")) {
                        playerList.setVisible(player.getName(),true);
                        sender.sendMessage("You are now visible on Dynmap.");
                    }
                } else if (checkPlayerPermission(sender,"show.others")) {
                    for (int i = 1; i < args.length; i++) {
                        playerList.setVisible(args[i],true);
                        sender.sendMessage(args[i] + " is now visible on Dynmap.");
                    }
                }
            } else if (c.equals("fullrender") && checkPlayerPermission(sender,"fullrender")) {
                if (args.length > 1) {
                    for (int i = 1; i < args.length; i++) {
                        World w = getServer().getWorld(args[i]);
                        if(w != null)
                            mapManager.renderFullWorld(new Location(w, 0, 0, 0),sender);
                        else
                            sender.sendMessage("World '" + args[i] + "' not defined/loaded");
                    }
                } else if (player != null) {
                    Location loc = player.getLocation();
                    if(loc != null)
                        mapManager.renderFullWorld(loc, sender);
                } else {
                    sender.sendMessage("World name is required");
                }
            } else if (c.equals("cancelrender") && checkPlayerPermission(sender,"cancelrender")) {
                if (args.length > 1) {
                    for (int i = 1; i < args.length; i++) {
                        World w = getServer().getWorld(args[i]);
                        if(w != null)
                            mapManager.cancelRender(w,sender);
                        else
                            sender.sendMessage("World '" + args[i] + "' not defined/loaded");
                    }
                } else if (player != null) {
                    Location loc = player.getLocation();
                    if(loc != null)
                        mapManager.cancelRender(loc.getWorld(), sender);
                } else {
                    sender.sendMessage("World name is required");
                }
            } else if (c.equals("reload") && checkPlayerPermission(sender, "reload")) {
                sender.sendMessage("Reloading Dynmap...");
                reload();
                sender.sendMessage("Dynmap reloaded");
            } else if (c.equals("stats") && checkPlayerPermission(sender, "stats")) {
                if(args.length == 1)
                    mapManager.printStats(sender, null);
                else
                    mapManager.printStats(sender, args[1]);
            } else if (c.equals("resetstats") && checkPlayerPermission(sender, "resetstats")) {
                if(args.length == 1)
                    mapManager.resetStats(sender, null);
                else
                    mapManager.resetStats(sender, args[1]);
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

    public ConfigurationNode getWorldConfiguration(World world) {
        ConfigurationNode finalConfiguration = new ConfigurationNode();
        finalConfiguration.put("name", world.getName());
        finalConfiguration.put("title", world.getName());
        
        ConfigurationNode worldConfiguration = getWorldConfigurationNode(world.getName());
        
        // Get the template.
        ConfigurationNode templateConfiguration = null;
        if (worldConfiguration != null) {
            String templateName = worldConfiguration.getString("template");
            if (templateName != null) {
                templateConfiguration = getTemplateConfigurationNode(templateName);
            }
        }
        
        // Template not found, using default template.
        if (templateConfiguration == null) {
            templateConfiguration = getDefaultTemplateConfigurationNode(world);
        }
        
        // Merge the finalConfiguration, templateConfiguration and worldConfiguration.
        finalConfiguration.extend(templateConfiguration);
        finalConfiguration.extend(worldConfiguration);
        
        Log.verboseinfo("Configuration of world " + world.getName());
        for(Map.Entry<String, Object> e : finalConfiguration.entrySet()) {
            Log.verboseinfo(e.getKey() + ": " + e.getValue());
        }
        
        return finalConfiguration;
    }
    
    private ConfigurationNode getDefaultTemplateConfigurationNode(World world) {
        Environment environment = world.getEnvironment();
        String environmentName = environment.name().toLowerCase();
        if(deftemplatesuffix.length() > 0) {
            environmentName += "-" + deftemplatesuffix;
        }
        Log.verboseinfo("Using environment as template: " + environmentName);
        return getTemplateConfigurationNode(environmentName);
    }
    
    private ConfigurationNode getWorldConfigurationNode(String worldName) {
        for(ConfigurationNode worldNode : configuration.getNodes("worlds")) {
            if (worldName.equals(worldNode.getString("name"))) {
                return worldNode;
            }
        }
        return new ConfigurationNode();
    }
    
    private ConfigurationNode getTemplateConfigurationNode(String templateName) {
        ConfigurationNode templatesNode = configuration.getNode("templates");
        if (templatesNode != null) {
            return templatesNode.getNode(templateName);
        }
        return null;
    }
    
    public void reload() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.disablePlugin(this);
        pluginManager.enablePlugin(this);
    }
    
    public String getWebPath() {
        return configuration.getString("webpath", "web");
    }
    
    public static void setIgnoreChunkLoads(boolean ignore) {
        ignore_chunk_loads = ignore;
    }
    /* Uses resource to create default file, if file does not yet exist */
    public boolean createDefaultFileFromResource(String resourcename, File deffile) {
        if(deffile.canRead())
            return true;
        Log.info(deffile.getPath() + " not found - creating default");
        InputStream in = getClass().getResourceAsStream(resourcename);
        if(in == null) {
            Log.severe("Unable to find default resource - " + resourcename);
            return false;
        }
        else {
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(deffile);
                byte[] buf = new byte[512];
                int len;
                while((len = in.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
            } catch (IOException iox) {
                Log.severe("ERROR creatomg default for " + deffile.getPath());
                return false;
            } finally {
                if(fos != null)
                    try { fos.close(); } catch (IOException iox) {}
                if(in != null)
                    try { in.close(); } catch (IOException iox) {}
            }
            return true;
        }
    }
}
