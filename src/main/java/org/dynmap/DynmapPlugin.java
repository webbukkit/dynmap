package org.dynmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.CustomEventListener;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Type;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.WorldListener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.debug.Debug;
import org.dynmap.debug.Debugger;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.impl.MarkerAPIImpl;
import org.dynmap.permissions.BukkitPermissions;
import org.dynmap.permissions.NijikokunPermissions;
import org.dynmap.permissions.OpPermissions;
import org.dynmap.permissions.PermissionProvider;
import org.dynmap.web.HttpServer;
import org.dynmap.web.handlers.ClientConfigurationHandler;
import org.dynmap.web.handlers.FilesystemHandler;

public class DynmapPlugin extends JavaPlugin implements DynmapAPI {
    public HttpServer webServer = null;
    public MapManager mapManager = null;
    public PlayerList playerList;
    public ConfigurationNode configuration;
    public HashSet<String> enabledTriggers = new HashSet<String>();
    public PermissionProvider permissions;
    public ComponentManager componentManager = new ComponentManager();
    public PlayerFaces playerfacemgr;
    public Events events = new Events();
    public String deftemplatesuffix = "";
    boolean swampshading = false;
    boolean waterbiomeshading = false;
    boolean fencejoin = false;
    public CompassMode compassmode = CompassMode.PRE19;

    public enum CompassMode {
        PRE19,  /* Default for 1.8 and earlier (east is Z+) */
        NEWROSE,    /* Use same map orientation, fix rose */
        NEWNORTH    /* Use new map orientation */
    };

    /* Flag to let code know that we're doing reload - make sure we don't double-register event handlers */
    public boolean is_reload = false;
    private boolean generate_only = false;
    private static boolean ignore_chunk_loads = false; /* Flag keep us from processing our own chunk loads */

    private HashMap<Event.Type, List<Listener>> event_handlers = new HashMap<Event.Type, List<Listener>>();

    private MarkerAPIImpl   markerapi;
    
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
        "nether-lowres.txt", "skylands-lowres.txt", "normal-hires.txt", "nether-hires.txt", "skylands-hires.txt",
        "normal-vlowres.txt", "skylands-vlowres.txt", "nether-vlowres.txt", "the_end.txt", "the_end-vlowres.txt",
        "the_end-lowres.txt", "the_end-hires.txt"
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
            permissions = BukkitPermissions.create("dynmap");
        if (permissions == null)
            permissions = new OpPermissions(new String[] { "fullrender", "cancelrender", "radiusrender", "resetstats", "reload", "purgequeue" });

        dataDirectory = this.getDataFolder();
        
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

        /* Load block models */
        HDBlockModels.loadModels(dataDirectory, configuration);
        /* Load texture mappings */
        TexturePack.loadTextureMapping(dataDirectory, configuration);
        
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
        /* Default swamp shading off for 1.8, on after */
        swampshading = configuration.getBoolean("swampshaded", !getServer().getVersion().contains("(MC: 1.8"));
        /* Default water biome shading off for 1.8, on after */
        waterbiomeshading = configuration.getBoolean("waterbiomeshaded", !getServer().getVersion().contains("(MC: 1.8"));
        /* Default fence-to-block-join off for 1.8, on after */
        fencejoin = configuration.getBoolean("fence-to-block-join", !getServer().getVersion().contains("(MC: 1.8"));
        /* Default compassmode to pre19, to newrose after */
        String cmode = configuration.getString("compass-mode", getServer().getVersion().contains("(MC: 1.8")?"pre19":"newrose");
        if(cmode.equals("newnorth"))
            compassmode = CompassMode.NEWNORTH;
        else if(cmode.equals("newrose"))
            compassmode = CompassMode.NEWROSE;
        else
            compassmode = CompassMode.PRE19;
        
        loadDebuggers();

        tilesDirectory = getFile(configuration.getString("tilespath", "web/tiles"));
        if (!tilesDirectory.isDirectory() && !tilesDirectory.mkdirs()) {
            Log.warning("Could not create directory for tiles ('" + tilesDirectory + "').");
        }

        playerList = new PlayerList(getServer(), getFile("hiddenplayers.txt"), configuration);
        playerList.load();
        PlayerListener pl = new PlayerListener() {
            public void onPlayerJoin(PlayerJoinEvent evt) {
                playerList.updateOnlinePlayers(null);
            }
            public void onPlayerQuit(PlayerQuitEvent evt) {
                playerList.updateOnlinePlayers(evt.getPlayer());
            }
        };
        registerEvent(Type.PLAYER_JOIN, pl);
        registerEvent(Type.PLAYER_QUIT, pl);

        mapManager = new MapManager(this, configuration);
        mapManager.startRendering();

        playerfacemgr = new PlayerFaces(this);
        
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
        /* Load customized response headers, if any */
        ConfigurationNode custhttp = configuration.getNode("http-response-headers");
        HashMap<String, String> custhdrs = new HashMap<String,String>();
        if(custhttp != null) {
            for(String k : custhttp.keySet()) {
                String v = custhttp.getString(k);
                if(v != null) {
                    custhdrs.put(k, v);
                }
            }
        }
        HttpServer.setCustomHeaders(custhdrs);
        
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
        /* Clean up all registered handlers */
        for(Event.Type t : event_handlers.keySet()) {
            List<Listener> ll = event_handlers.get(t);
            ll.clear(); /* Empty list - we use presence of list to remember that we've registered with Bukkit */
        }
        playerfacemgr = null;
        
        /* Don't clean up markerAPI - other plugins may still be accessing it */
        
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
    private boolean onpiston;
    private boolean onplayerjoin;
    private boolean onplayermove;
    private boolean ongeneratechunk;
    private boolean onloadchunk;
    private boolean onexplosion;

    public void registerEvents() {

        
        BlockListener blockTrigger = new BlockListener() {
            @Override
            public void onBlockPlace(BlockPlaceEvent event) {
                Location loc = event.getBlock().getLocation();
                mapManager.sscache.invalidateSnapshot(loc);
                if(onplace) {
                    mapManager.touch(loc);
                }
            }

            @Override
            public void onBlockBreak(BlockBreakEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                mapManager.sscache.invalidateSnapshot(loc);
                if(onbreak) {
                    mapManager.touch(loc);
                }
            }

            @Override
            public void onLeavesDecay(LeavesDecayEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                mapManager.sscache.invalidateSnapshot(loc);
                if(onleaves) {
                    mapManager.touch(loc);              
                }
            }
            
            @Override
            public void onBlockBurn(BlockBurnEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                mapManager.sscache.invalidateSnapshot(loc);
                if(onburn) {
                    mapManager.touch(loc);
                }
            }
            
            @Override
            public void onBlockForm(BlockFormEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                mapManager.sscache.invalidateSnapshot(loc);
                if(onblockform) {
                    mapManager.touch(loc);
                }
            }

            @Override
            public void onBlockFade(BlockFadeEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                mapManager.sscache.invalidateSnapshot(loc);
                if(onblockfade) {
                    mapManager.touch(loc);
                }
            }
            
            @Override
            public void onBlockSpread(BlockSpreadEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                mapManager.sscache.invalidateSnapshot(loc);
                if(onblockspread) {
                    mapManager.touch(loc);
                }
            }
            
            @Override
            public void onBlockPistonRetract(BlockPistonRetractEvent event) {
                if(event.isCancelled())
                    return;
                Block b = event.getBlock();
                Location loc = b.getLocation();
                mapManager.sscache.invalidateSnapshot(loc);
                BlockFace dir;
                try {   /* Workaround Bukkit bug = http://leaky.bukkit.org/issues/1227 */
                    dir = event.getDirection();
                } catch (ClassCastException ccx) {
                    dir = BlockFace.NORTH;
                }
                if(onpiston) {
                        mapManager.touchVolume(loc, b.getRelative(dir, 2).getLocation());
                }
                for(int i = 0; i < 2; i++) {
                    b = b.getRelative(dir, 1);
                    mapManager.sscache.invalidateSnapshot(b.getLocation());
                }
            }
            @Override
            public void onBlockPistonExtend(BlockPistonExtendEvent event) {
                if(event.isCancelled())
                    return;
                Block b = event.getBlock();
                Location loc = b.getLocation();
                mapManager.sscache.invalidateSnapshot(loc);
                BlockFace dir;
                try {   /* Workaround Bukkit bug = http://leaky.bukkit.org/issues/1227 */
                    dir = event.getDirection();
                } catch (ClassCastException ccx) {
                    dir = BlockFace.NORTH;
                }
                if(onpiston) {
                    mapManager.touchVolume(loc, b.getRelative(dir, 1+event.getLength()).getLocation());
                }
                for(int i = 0; i < 1+event.getLength(); i++) {
                     b = b.getRelative(dir, 1);
                     mapManager.sscache.invalidateSnapshot(b.getLocation());
                }
            }
        };
        
        // To trigger rendering.
        onplace = isTrigger("blockplaced");
        registerEvent(Event.Type.BLOCK_PLACE, blockTrigger);
            
        onbreak = isTrigger("blockbreak");
        registerEvent(Event.Type.BLOCK_BREAK, blockTrigger);
            
        if(isTrigger("snowform")) Log.info("The 'snowform' trigger has been deprecated due to Bukkit changes - use 'blockformed'");
            
        onleaves = isTrigger("leavesdecay");
        registerEvent(Event.Type.LEAVES_DECAY, blockTrigger);
            
        onburn = isTrigger("blockburn");
        registerEvent(Event.Type.BLOCK_BURN, blockTrigger);

        onblockform = isTrigger("blockformed");
        registerEvent(Event.Type.BLOCK_FORM, blockTrigger);
            
        onblockfade = isTrigger("blockfaded");
        registerEvent(Event.Type.BLOCK_FADE, blockTrigger);
            
        onblockspread = isTrigger("blockspread");
        registerEvent(Event.Type.BLOCK_SPREAD, blockTrigger);

        onpiston = isTrigger("pistonmoved");
        registerEvent(Event.Type.BLOCK_PISTON_EXTEND, blockTrigger);
        registerEvent(Event.Type.BLOCK_PISTON_RETRACT, blockTrigger);
        /* Register player event trigger handlers */
        PlayerListener playerTrigger = new PlayerListener() {
            @Override
            public void onPlayerJoin(PlayerJoinEvent event) {
                mapManager.touch(event.getPlayer().getLocation());
            }

            @Override
            public void onPlayerMove(PlayerMoveEvent event) {
                mapManager.touch(event.getPlayer().getLocation());
            }
        };

        onplayerjoin = isTrigger("playerjoin");
        onplayermove = isTrigger("playermove");
        if(onplayerjoin)
            registerEvent(Event.Type.PLAYER_JOIN, playerTrigger);
        if(onplayermove)
            registerEvent(Event.Type.PLAYER_MOVE, playerTrigger);

        /* Register entity event triggers */
        EntityListener entityTrigger = new EntityListener() {
            @Override
            public void onEntityExplode(EntityExplodeEvent event) {
                List<Block> blocks = event.blockList();
                for(Block b: blocks) {
                    Location loc = b.getLocation();
                    mapManager.sscache.invalidateSnapshot(loc);
                    if(onexplosion) {
                        mapManager.touch(loc);
                    }
                }
            }
        };
        onexplosion = isTrigger("explosion");
        registerEvent(Event.Type.ENTITY_EXPLODE, entityTrigger);
        
        
        /* Register world event triggers */
        WorldListener worldTrigger = new WorldListener() {
            @Override
            public void onChunkLoad(ChunkLoadEvent event) {
                if(ignore_chunk_loads)
                    return;
                /* Touch extreme corners */
                int x = event.getChunk().getX() << 4;
                int z = event.getChunk().getZ() << 4;
                mapManager.touchVolume(new Location(event.getWorld(), x, 0, z), new Location(event.getWorld(), x+16, 128, z+16));
            }
            @Override
            public void onChunkPopulate(ChunkPopulateEvent event) {
                int x = event.getChunk().getX() << 4;
                int z = event.getChunk().getZ() << 4;
                mapManager.touchVolume(new Location(event.getWorld(), x, 0, z), new Location(event.getWorld(), x+16, 128, z+16));
            }
            @Override
            public void onWorldLoad(WorldLoadEvent event) {
                mapManager.activateWorld(event.getWorld());
            }
        };

        ongeneratechunk = isTrigger("chunkgenerated");
        if(ongeneratechunk) {
            registerEvent(Event.Type.CHUNK_POPULATED, worldTrigger);
        }
        onloadchunk = isTrigger("chunkloaded");
        if(onloadchunk) { 
            registerEvent(Event.Type.CHUNK_LOAD, worldTrigger);
        }

        // To link configuration to real loaded worlds.
        registerEvent(Event.Type.WORLD_LOAD, worldTrigger);
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

    /* Parse argument strings : handle quoted strings */
    public static String[] parseArgs(String[] args, CommandSender snd) {
        ArrayList<String> rslt = new ArrayList<String>();
        /* Build command line, so we can parse our way - make sure there is trailing space */
        String cmdline = "";
        for(int i = 0; i < args.length; i++) {
            cmdline += args[i] + " ";
        }
        boolean inquote = false;
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < cmdline.length(); i++) {
            char c = cmdline.charAt(i);
            if(inquote) {   /* If in quote, accumulate until end or another quote */
                if(c == '\"') { /* End quote */
                    inquote = false;
                }
                else {
                    sb.append(c);
                }
            }
            else if(c == '\"') {    /* Start of quote? */
                inquote = true;
            }
            else if(c == ' ') { /* Ending space? */
                rslt.add(sb.toString());
                sb.setLength(0);
            }
            else {
                sb.append(c);
            }
        }
        if(inquote) {   /* If still in quote, syntax error */
            snd.sendMessage("Error: unclosed doublequote");
            return null;
        }
        return rslt.toArray(new String[rslt.size()]);
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
        "resetstats",
        "sendtoweb",
        "purgequeue" }));

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if(cmd.getName().equalsIgnoreCase("dmarker")) {
            return MarkerAPIImpl.onCommand(this, sender, cmd, commandLabel, args);
        }
        if (!cmd.getName().equalsIgnoreCase("dynmap"))
            return false;
        Player player = null;
        if (sender instanceof Player)
            player = (Player) sender;
        /* Re-parse args - handle doublequotes */
        args = parseArgs(args, sender);
        
        if(args == null)
            return false;

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
                    String mapname = null;
                    if(args.length > 1) {
                        radius = Integer.parseInt(args[1]); /* Parse radius */
                        if(radius < 0)
                            radius = 0;
                        if(args.length > 2)
                            mapname = args[2];
                    }
                    Location loc = player.getLocation();
                    if(loc != null)
                        mapManager.renderWorldRadius(loc, sender, mapname, radius);
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
                String map = null;
                if (args.length > 1) {
                    for (int i = 1; i < args.length; i++) {
                        int dot = args[i].indexOf(":");
                        DynmapWorld w;
                        String wname = args[i];
                        if(dot >= 0) {
                            wname = args[i].substring(0, dot);
                            map = args[i].substring(dot+1);
                        }
                        w = mapManager.getWorld(wname);
                        if(w != null) {
                            Location spawn = w.world.getSpawnLocation();
                            Location loc = new Location(w.world, w.configuration.getDouble("center/x", spawn.getX()), w.configuration.getDouble("center/y", spawn.getY()), w.configuration.getDouble("center/z", spawn.getZ()));
                            mapManager.renderFullWorld(loc,sender, map);
                        }
                        else
                            sender.sendMessage("World '" + wname + "' not defined/loaded");
                    }
                } else if (player != null) {
                    Location loc = player.getLocation();
                    if(args.length > 1)
                        map = args[1];
                    if(loc != null)
                        mapManager.renderFullWorld(loc, sender, map);
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
            } else if (c.equals("purgequeue") && checkPlayerPermission(sender, "purgequeue")) {
                mapManager.purgeQueue(sender);
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
            } else if (c.equals("sendtoweb") && checkPlayerPermission(sender, "sendtoweb")) {
                String msg = "";
                for(int i = 1; i < args.length; i++) {
                    msg += args[i] + " ";
                }
                this.sendBroadcastToWeb("dynmap", msg);
            }
            return true;
        }
        return false;
    }

    public boolean checkPlayerPermission(CommandSender sender, String permission) {
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

    
    private BlockListener ourBlockEventHandler = new BlockListener() {
        
        @Override
        public void onBlockPlace(BlockPlaceEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockPlace(event);
                }
            }
        }

        @Override
        public void onBlockBreak(BlockBreakEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockBreak(event);
                }
            }
        }

        @Override
        public void onLeavesDecay(LeavesDecayEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onLeavesDecay(event);
                }
            }
        }
        
        @Override
        public void onBlockBurn(BlockBurnEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockBurn(event);
                }
            }
        }
        
        @Override
        public void onBlockForm(BlockFormEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockForm(event);
                }
            }
        }
        @Override
        public void onBlockFade(BlockFadeEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockFade(event);
                }
            }
        }
        @Override
        public void onBlockSpread(BlockSpreadEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockSpread(event);
                }
            }
        }
        @Override
        public void onBlockPistonRetract(BlockPistonRetractEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockPistonRetract(event);
                }
            }
        }
        @Override
        public void onBlockPistonExtend(BlockPistonExtendEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((BlockListener)l).onBlockPistonExtend(event);
                }
            }
        }
    };
    private PlayerListener ourPlayerEventHandler = new PlayerListener() {
        @Override
        public void onPlayerJoin(PlayerJoinEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((PlayerListener)l).onPlayerJoin(event);
                }
            }
        }
        @Override
        public void onPlayerLogin(PlayerLoginEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((PlayerListener)l).onPlayerLogin(event);
                }
            }
        }

        @Override
        public void onPlayerMove(PlayerMoveEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((PlayerListener)l).onPlayerMove(event);
                }
            }
        }
        
        @Override
        public void onPlayerQuit(PlayerQuitEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((PlayerListener)l).onPlayerQuit(event);
                }
            }
        }
        
        @Override
        public void onPlayerChat(PlayerChatEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((PlayerListener)l).onPlayerChat(event);
                }
            }
        }
    };

    private WorldListener ourWorldEventHandler = new WorldListener() {
        @Override
        public void onWorldLoad(WorldLoadEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((WorldListener)l).onWorldLoad(event);
                }
            }
        }
        @Override
        public void onChunkLoad(ChunkLoadEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((WorldListener)l).onChunkLoad(event);
                }
            }
        }
        @Override
        public void onChunkPopulate(ChunkPopulateEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((WorldListener)l).onChunkPopulate(event);
                }
            }
        }
        @Override
        public void onSpawnChange(SpawnChangeEvent event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((WorldListener)l).onSpawnChange(event);
                }
            }
        }
    };
    
    private CustomEventListener ourCustomEventHandler = new CustomEventListener() {
        @Override
        public void onCustomEvent(Event event) {
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((CustomEventListener)l).onCustomEvent(event);
                }
            }
        }
    };
    
    private EntityListener ourEntityEventHandler = new EntityListener() {
        @Override
        public void onEntityExplode(EntityExplodeEvent event) {
            if(event.isCancelled())
                return;
            /* Call listeners */
            List<Listener> ll = event_handlers.get(event.getType());
            if(ll != null) {
                for(Listener l : ll) {
                    ((EntityListener)l).onEntityExplode(event);
                }
            }
        }
    };
    
    /**
     * Register event listener - this will be cleaned up properly on a /dynmap reload, unlike
     * registering with Bukkit directly
     */
    public void registerEvent(Event.Type type, Listener listener) {
        List<Listener> ll = event_handlers.get(type);
        PluginManager pm = getServer().getPluginManager();
        if(ll == null) {
            switch(type) {  /* See if it is a type we're brokering */
                case PLAYER_LOGIN:
                case PLAYER_CHAT:
                case PLAYER_JOIN:
                case PLAYER_QUIT:
                case PLAYER_MOVE:
                    pm.registerEvent(type, ourPlayerEventHandler, Event.Priority.Monitor, this);
                    break;
                case BLOCK_PLACE:
                case BLOCK_BREAK:
                case LEAVES_DECAY:
                case BLOCK_BURN:
                case BLOCK_FORM:
                case BLOCK_FADE:
                case BLOCK_SPREAD:
                case BLOCK_PISTON_EXTEND:
                case BLOCK_PISTON_RETRACT:
                    pm.registerEvent(type, ourBlockEventHandler, Event.Priority.Monitor, this);
                    break;
                case WORLD_LOAD:
                case CHUNK_LOAD:
                case CHUNK_POPULATED:
                case SPAWN_CHANGE:
                    pm.registerEvent(type, ourWorldEventHandler, Event.Priority.Monitor, this);
                    break;
                case CUSTOM_EVENT:
                    pm.registerEvent(type, ourCustomEventHandler, Event.Priority.Monitor, this);
                    break;
                case ENTITY_EXPLODE:
                    pm.registerEvent(type, ourEntityEventHandler, Event.Priority.Monitor, this);
                    break;
                default:
                    Log.severe("registerEvent() in DynmapPlugin does not handle " + type);
                    return;
            }
            ll = new ArrayList<Listener>();
            event_handlers.put(type, ll);   /* Add list for this event */
        }
        ll.add(listener);
    }
    /**
     * ** This is the public API for other plugins to use for accessing the Marker API **
     * This method can return null if the 'markers' component has not been configured - 
     * a warning message will be issued to the server.log in this event.
     * 
     * @return MarkerAPI, or null if not configured
     */
    public MarkerAPI getMarkerAPI() {
        if(markerapi == null) {
            Log.warning("Marker API has been requested, but is not enabled.  Uncomment or add 'markers' component to configuration.txt.");
        }
        return markerapi;
    }
    public boolean markerAPIInitialized() {
        return (markerapi != null);
    }
    /**
     * Send generic message to all web users
     * @param sender - label for sender of message ("[<sender>] nessage") - if null, no from notice
     * @param msg - message to be sent
     */
    public boolean sendBroadcastToWeb(String sender, String msg) {
        if(mapManager != null) {
            mapManager.pushUpdate(new Client.ChatMessage("plugin", sender, "", msg, ""));
            return true;
        }
        return false;
    }
    /**
     * Trigger update on tiles associated with given locations.  If two locations provided,
     * the volume is the rectangular prism ("cuboid") with the two locations on opposite corners.
     * 
     * @param l0 - first location (required)
     * @param l1 - second location (if null, only single point invalidated (l0))
     * @return number of tiles queued to be rerendered
     */
    public int triggerRenderOfVolume(Location l0, Location l1) {
        if(mapManager != null) {
            if(l1 == null)
                return mapManager.touch(l0);
            else
                return mapManager.touchVolume(l0, l1);
        }
        return 0;
    }

    /**
     * Register markers API - used by component to supply marker API to plugin
     */
    public void registerMarkerAPI(MarkerAPIImpl api) {
        markerapi = api;
    }
}
