package org.dynmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapListenerManager;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.debug.Debug;
import org.dynmap.debug.Debugger;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.hdmap.HDMapManager;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.impl.MarkerAPIImpl;
import org.dynmap.web.BanIPFilter;
import org.dynmap.web.CustomHeaderFilter;
import org.dynmap.web.FilterHandler;
import org.dynmap.web.HandlerRouter;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.FileResource;
import org.yaml.snakeyaml.Yaml;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;

public class DynmapCore {
    private DynmapServerInterface server;
    private String version;
    private Server webServer = null;
    private HandlerRouter router = null;
    public MapManager mapManager = null;
    public PlayerList playerList;
    public ConfigurationNode configuration;
    public ComponentManager componentManager = new ComponentManager();
    public DynmapListenerManager listenerManager = new DynmapListenerManager(this);
    public PlayerFaces playerfacemgr;
    public Events events = new Events();
    public String deftemplatesuffix = "";
    boolean swampshading = false;
    boolean waterbiomeshading = false;
    boolean fencejoin = false;
    boolean bettergrass = false;
    private HashSet<String> enabledTriggers = new HashSet<String>();
        
    public CompassMode compassmode = CompassMode.PRE19;
    private int     config_hashcode;    /* Used to signal need to reload web configuration (world changes, config update, etc) */
    private int fullrenderplayerlimit;  /* Number of online players that will cause fullrender processing to pause */
    private boolean didfullpause;
    private Map<String, LinkedList<String>> ids_by_ip = new HashMap<String, LinkedList<String>>();
    private boolean persist_ids_by_ip = false;

    public enum CompassMode {
        PRE19,  /* Default for 1.8 and earlier (east is Z+) */
        NEWROSE,    /* Use same map orientation, fix rose */
        NEWNORTH    /* Use new map orientation */
    };

    /* Flag to let code know that we're doing reload - make sure we don't double-register event handlers */
    public boolean is_reload = false;
    public static boolean ignore_chunk_loads = false; /* Flag keep us from processing our own chunk loads */

    private MarkerAPIImpl   markerapi;
    
    private File dataDirectory;
    private File tilesDirectory;
    private String plugin_ver;
    private String mc_ver;

    /* Constructor for core */
    public DynmapCore() {
    }
    
    /* Cleanup method */
    public void cleanup() {
        server = null;
        markerapi = null;
    }
    
    /* Dependencies - need to be supplied by plugin wrapper */
    public void setPluginVersion(String pluginver) {
        plugin_ver = pluginver;
    }
    public void setDataFolder(File dir) {
        dataDirectory = dir;
    }
    public final File getDataFolder() {
        return dataDirectory;
    }
    public final File getTilesFolder() {
        return tilesDirectory;
    }
    public void setMinecraftVersion(String mcver) {
        mc_ver = mcver;
    }
    public void setServer(DynmapServerInterface srv) {
        server = srv;
    }
    public final DynmapServerInterface getServer() { return server; }
    
    public final MapManager getMapManager() {
        return mapManager;
    }

    /* Add/Replace branches in configuration tree with contribution from a separate file */
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
                ConfigurationNode cn = new ConfigurationNode(tf);
                cn.load();
                /* Supplement existing values (don't replace), since configuration.txt is more custom than these */
                mergeConfigurationBranch(cn, "templates", false, false);
            }
        }
        /* Go through list again - this time do custom- ones */
        for(String tname: templates) {
            /* If matches naming convention */
            if(tname.endsWith(".txt") && tname.startsWith(CUSTOM_PREFIX)) {
                File tf = new File(templatedir, tname);
                ConfigurationNode cn = new ConfigurationNode(tf);
                cn.load();
                /* This are overrides - replace even configuration.txt content */
                mergeConfigurationBranch(cn, "templates", true, false);
            }
        }
    }
        
    public boolean enableCore() {
        /* Start with clean events */
        events = new Events();

        /* Load plugin version info */
        loadVersion();
        
        /* Initialize confguration.txt if needed */
        File f = new File(dataDirectory, "configuration.txt");
        if(!createDefaultFileFromResource("/configuration.txt", f)) {
            return false;
        }
        
        /* Load configuration.txt */
        configuration = new ConfigurationNode(f);
        configuration.load();

        /* Add options to avoid 0.29 re-render (fixes very inconsistent with previous maps) */
        HDMapManager.usegeneratedtextures = configuration.getBoolean("use-generated-textures", false);
        HDMapManager.waterlightingfix = configuration.getBoolean("correct-water-lighting", false);
        HDMapManager.biomeshadingfix = configuration.getBoolean("correct-biome-shading", false);

        /* Load block models */
        HDBlockModels.loadModels(dataDirectory, configuration);
        /* Load texture mappings */
        TexturePack.loadTextureMapping(dataDirectory, configuration);
        
        /* Now, process worlds.txt - merge it in as an override of existing values (since it is only user supplied values) */
        f = new File(dataDirectory, "worlds.txt");
        if(!createDefaultFileFromResource("/worlds.txt", f)) {
            return false;
        }
        ConfigurationNode cn = new ConfigurationNode(f);
        cn.load();
        mergeConfigurationBranch(cn, "worlds", true, true);

        /* Now, process templates */
        loadTemplates();

        Log.verbose = configuration.getBoolean("verbose", true);
        deftemplatesuffix = configuration.getString("deftemplatesuffix", "");
        /* Default swamp shading off for 1.8, on after */
        boolean post_1_8 = !mc_ver.contains("1.8.");
        swampshading = configuration.getBoolean("swampshaded", post_1_8);
        /* Default water biome shading off for 1.8, on after */
        waterbiomeshading = configuration.getBoolean("waterbiomeshaded", post_1_8);
        /* Default fence-to-block-join off for 1.8, on after */
        fencejoin = configuration.getBoolean("fence-to-block-join", post_1_8);

        /* Default compassmode to pre19, to newrose after */
        String cmode = configuration.getString("compass-mode", post_1_8?"newrose":"pre19");
        if(cmode.equals("newnorth"))
            compassmode = CompassMode.NEWNORTH;
        else if(cmode.equals("newrose"))
            compassmode = CompassMode.NEWROSE;
        else
            compassmode = CompassMode.PRE19;
        /* Default better-grass */
        bettergrass = configuration.getBoolean("better-grass", false);
        /* Load full render processing player limit */
        fullrenderplayerlimit = configuration.getInteger("fullrenderplayerlimit", 0);
        /* If we're persisting ids-by-ip, load it */
        persist_ids_by_ip = configuration.getBoolean("persist-ids-by-ip", true);
        if(persist_ids_by_ip)
            loadIDsByIP();
        
        loadDebuggers();

        tilesDirectory = getFile(configuration.getString("tilespath", "web/tiles"));
        if (!tilesDirectory.isDirectory() && !tilesDirectory.mkdirs()) {
            Log.warning("Could not create directory for tiles ('" + tilesDirectory + "').");
        }

        playerList = new PlayerList(getServer(), getFile("hiddenplayers.txt"), configuration);
        playerList.load();

        mapManager = new MapManager(this, configuration);
        mapManager.startRendering();

        playerfacemgr = new PlayerFaces(this);
        
        updateConfigHashcode(); /* Initialize/update config hashcode */
        
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
        for(Component component : configuration.<Component>createInstances("components", new Class<?>[] { DynmapCore.class }, new Object[] { this })) {
            componentManager.add(component);
        }
        Log.verboseinfo("Loaded " + componentManager.components.size() + " components.");

        if (!configuration.getBoolean("disable-webserver", false)) {
            startWebserver();
        }
        
        /* Add login/logoff listeners */
        listenerManager.addListener(EventType.PLAYER_JOIN, new DynmapListenerManager.PlayerEventListener() {
            @Override
            public void playerEvent(DynmapPlayer p) {
                playerJoined(p);
            }
        });
        listenerManager.addListener(EventType.PLAYER_QUIT, new DynmapListenerManager.PlayerEventListener() {
            @Override
            public void playerEvent(DynmapPlayer p) {
                playerQuit(p);
            }
        });
        
        /* Print version info */
        Log.info("version " + plugin_ver + " is enabled - core version " + version );

        events.<Object>trigger("initialized", null);
        
        return true;
    }

    private void playerJoined(DynmapPlayer p) {
        playerList.updateOnlinePlayers(null);
        if(fullrenderplayerlimit > 0) {
            if((getServer().getOnlinePlayers().length+1) >= fullrenderplayerlimit) {
                if(getPauseFullRadiusRenders() == false) {  /* If not paused, pause it */
                    setPauseFullRadiusRenders(true);
                    Log.info("Pause full/radius renders - player limit reached");
                    didfullpause = true;
                }
            }
        }
        /* Add player info to IP-to-ID table */
        InetSocketAddress addr = p.getAddress();
        if(addr != null) {
            String ip = addr.getAddress().getHostAddress();
            LinkedList<String> ids = ids_by_ip.get(ip);
            if(ids == null) {
                ids = new LinkedList<String>();
                ids_by_ip.put(ip, ids);
            }
            String pid = p.getName();
            if(ids.indexOf(pid) != 0) {
                ids.remove(pid);    /* Remove from list */
                ids.addFirst(pid);  /* Put us first on list */
            }
        }
        /* And re-attach to active jobs */
        if(mapManager != null)
            mapManager.connectTasksToPlayer(p);
    }

    /* Called by plugin each time a player quits the server */
    private void playerQuit(DynmapPlayer p) {
        playerList.updateOnlinePlayers(p.getName());
        if(fullrenderplayerlimit > 0) {
            if((getServer().getOnlinePlayers().length-1) < fullrenderplayerlimit) {
                if(didfullpause) {  /* Only unpause if we did the pause */
                    setPauseFullRadiusRenders(false);
                    Log.info("Resume full/radius renders - below player limit");
                    didfullpause = false;
                }
            }
        }
    }
    
    public void updateConfigHashcode() {
        config_hashcode = (int)System.currentTimeMillis();
    }
    
    public int getConfigHashcode() {
        return config_hashcode;
    }

    private FileResource createFileResource(String path) {
        try {
            return new FileResource(new URL("file://" + path));
        } catch(Exception e) {
            Log.info("Could not create file resource");
            return null;
        }
    }
    
    public void loadWebserver() {
        webServer = new Server(new InetSocketAddress(configuration.getString("webserver-bindaddress", "0.0.0.0"), configuration.getInteger("webserver-port", 8123)));
        webServer.setStopAtShutdown(true);
        webServer.setGracefulShutdown(1000);
        
        final boolean allow_symlinks = configuration.getBoolean("allow-symlinks", false);
        int maxconnections = configuration.getInteger("max-sessions", 30);
        if(maxconnections < 2) maxconnections = 2;
        router = new HandlerRouter() {{
            this.addHandler("/", new ResourceHandler() {{
                this.setAliases(allow_symlinks);
                this.setWelcomeFiles(new String[] { "index.html" });
                this.setDirectoriesListed(true);
                this.setBaseResource(createFileResource(getFile(getWebPath()).getAbsolutePath()));
            }});
            this.addHandler("/tiles/*", new ResourceHandler() {{
                this.setAliases(allow_symlinks);
                this.setWelcomeFiles(new String[] { });
                this.setDirectoriesListed(true);
                this.setBaseResource(createFileResource(tilesDirectory.getAbsolutePath()));
            }});
        }};

        if(allow_symlinks)
            Log.verboseinfo("Web server is permitting symbolic links");
        else
            Log.verboseinfo("Web server is not permitting symbolic links");

        List<Filter> filters = new LinkedList<Filter>();
        
        /* Check for banned IPs */
        boolean checkbannedips = configuration.getBoolean("check-banned-ips", true);
        if (checkbannedips) {
            filters.add(new BanIPFilter(this));
        }

        /* Load customized response headers, if any */
        filters.add(new CustomHeaderFilter(configuration.getNode("http-response-headers")));

        webServer.setHandler(new FilterHandler(router, filters));

        addServlet("/up/configuration", new org.dynmap.servlet.ClientConfigurationServlet(this));

    }
    
    public Set<String> getIPBans() {
        return getServer().getIPBans();
    }
    
    public void addServlet(String path, HttpServlet servlet) {
        new ServletHolder(servlet);
        router.addServlet(path, servlet);
     }

    
    public void startWebserver() {
        try {
            webServer.start();
        } catch (Exception e) {
            Log.severe("Failed to start WebServer!", e);
        }
    }

    public void disableCore() {
        if(persist_ids_by_ip)
            saveIDsByIP();
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
            try {
                webServer.stop();
                while(webServer.isStopping())
                    Thread.sleep(100);
            } catch (Exception e) {
                Log.severe("Failed to stop WebServer!", e);
            }
            webServer = null;
        }
        playerfacemgr = null;
        /* Clean up registered listeners */
        listenerManager.cleanup();
        
        /* Don't clean up markerAPI - other plugins may still be accessing it */
        
        Debug.clearDebuggers();
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
                Constructor<?> constructor = debuggerClass.getConstructor(DynmapCore.class, ConfigurationNode.class);
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
    public static String[] parseArgs(String[] args, DynmapCommandSender snd) {
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
        "updaterender",
        "reload",
        "stats",
        "triggerstats",
        "resetstats",
        "sendtoweb",
        "pause",
        "purgequeue",
        "ids-for-ip",
        "ips-for-id",
        "add-id-for-ip",
        "del-id-for-ip"}));

    public boolean processCommand(DynmapCommandSender sender, String cmd, String commandLabel, String[] args) {
        if(cmd.equalsIgnoreCase("dmarker")) {
            return MarkerAPIImpl.onCommand(this, sender, cmd, commandLabel, args);
        }
        if (!cmd.equalsIgnoreCase("dynmap"))
            return false;
        DynmapPlayer player = null;
        if (sender instanceof DynmapPlayer)
            player = (DynmapPlayer) sender;
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
                    DynmapLocation loc = player.getLocation();
                    
                    mapManager.touch(loc.world, (int)loc.x, (int)loc.y, (int)loc.z, "render");
                    
                    sender.sendMessage("Tile render queued.");
                }
                else {
                    sender.sendMessage("Command can only be issued by player.");
                }
            }
            else if(c.equals("radiusrender") && checkPlayerPermission(sender,"radiusrender")) {
                int radius = 0;
                String mapname = null;
                DynmapLocation loc = null;
                if(args.length == 2) {  /* Just radius */
                    radius = Integer.parseInt(args[1]); /* Parse radius */
                    if(radius < 0)
                        radius = 0;
                    if(args.length > 2)
                        mapname = args[2];
                    if (player != null)
                        loc = player.getLocation();
                    else
                        sender.sendMessage("Command require <world> <x> <z> <radius> if issued from console.");
                }
                else if(args.length > 3) {  /* <world> <x> <z> */
                    DynmapWorld w = mapManager.worldsLookup.get(args[1]);   /* Look up world */
                    if(w == null) {
                        sender.sendMessage("World '" + args[1] + "' not defined/loaded");
                    }
                    int x = 0, z = 0;
                    x = Integer.parseInt(args[2]);
                    z = Integer.parseInt(args[3]);
                    if(args.length > 4)
                        radius = Integer.parseInt(args[4]);
                    if(args.length > 5)
                        mapname = args[5];
                    if(w != null)
                        loc = new DynmapLocation(w.getName(), x, 64, z);
                }
                if(loc != null)
                    mapManager.renderWorldRadius(loc, sender, mapname, radius);
            } else if(c.equals("updaterender") && checkPlayerPermission(sender,"updaterender")) {
                String mapname = null;
                DynmapLocation loc = null;
                if(args.length <= 3) {  /* Just command, or command plus map */
                    if(args.length > 2)
                        mapname = args[2];
                    if (player != null)
                        loc = player.getLocation();
                    else
                        sender.sendMessage("Command require <world> <x> <z> <radius> if issued from console.");
                }
                else {  /* <world> <x> <z> */
                    DynmapWorld w = mapManager.worldsLookup.get(args[1]);   /* Look up world */
                    if(w == null) {
                        sender.sendMessage("World '" + args[1] + "' not defined/loaded");
                    }
                    int x = 0, z = 0;
                    x = Integer.parseInt(args[2]);
                    z = Integer.parseInt(args[3]);
                    if(args.length > 4)
                        mapname = args[4];
                    if(w != null)
                        loc = new DynmapLocation(w.getName(), x, 64, z);
                }
                if(loc != null)
                    mapManager.renderFullWorld(loc, sender, mapname, true);
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
                            DynmapLocation spawn = w.getSpawnLocation();
                            DynmapLocation loc = new DynmapLocation(wname, w.configuration.getDouble("center/x", spawn.x), w.configuration.getDouble("center/y", spawn.y), w.configuration.getDouble("center/z", spawn.z));
                            mapManager.renderFullWorld(loc,sender, map, false);
                        }
                        else
                            sender.sendMessage("World '" + wname + "' not defined/loaded");
                    }
                } else if (player != null) {
                    DynmapLocation loc = player.getLocation();
                    if(args.length > 1)
                        map = args[1];
                    if(loc != null)
                        mapManager.renderFullWorld(loc, sender, map, false);
                } else {
                    sender.sendMessage("World name is required");
                }
            } else if (c.equals("cancelrender") && checkPlayerPermission(sender,"cancelrender")) {
                if (args.length > 1) {
                    for (int i = 1; i < args.length; i++) {
                        DynmapWorld w = mapManager.getWorld(args[i]);
                        if(w != null)
                            mapManager.cancelRender(w.getName(), sender);
                        else
                            sender.sendMessage("World '" + args[i] + "' not defined/loaded");
                    }
                } else if (player != null) {
                    DynmapLocation loc = player.getLocation();
                    if(loc != null)
                        mapManager.cancelRender(loc.world, sender);
                } else {
                    sender.sendMessage("World name is required");
                }
            } else if (c.equals("purgequeue") && checkPlayerPermission(sender, "purgequeue")) {
                mapManager.purgeQueue(sender);
            } else if (c.equals("reload") && checkPlayerPermission(sender, "reload")) {
                sender.sendMessage("Reloading Dynmap...");
                getServer().reload();
                sender.sendMessage("Dynmap reloaded");
            } else if (c.equals("stats") && checkPlayerPermission(sender, "stats")) {
                if(args.length == 1)
                    mapManager.printStats(sender, null);
                else
                    mapManager.printStats(sender, args[1]);
            } else if (c.equals("triggerstats") && checkPlayerPermission(sender, "stats")) {
                mapManager.printTriggerStats(sender);
            } else if (c.equals("pause") && checkPlayerPermission(sender, "pause")) {
                if(args.length == 1) {
                }
                else if(args[1].equals("full")) {
                    setPauseFullRadiusRenders(true);
                    setPauseUpdateRenders(false);
                }
                else if(args[1].equals("update")) {
                    setPauseFullRadiusRenders(false);
                    setPauseUpdateRenders(true);
                }
                else if(args[1].equals("all")) {
                    setPauseFullRadiusRenders(true);
                    setPauseUpdateRenders(true);
                }
                else {
                    setPauseFullRadiusRenders(false);
                    setPauseUpdateRenders(false);
                }
                if(getPauseFullRadiusRenders())
                    sender.sendMessage("Full/Radius renders are PAUSED");
                else
                    sender.sendMessage("Full/Radius renders are ACTIVE");
                if(getPauseUpdateRenders())
                    sender.sendMessage("Update renders are PAUSED");
                else
                    sender.sendMessage("Update renders are ACTIVE");
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
            } else if(c.equals("ids-for-ip") && checkPlayerPermission(sender, "ids-for-ip")) {
                if(args.length > 1) {
                    List<String> ids = getIDsForIP(args[1]);
                    sender.sendMessage("IDs logged in from address " + args[1] + " (most recent to least):");
                    if(ids != null) {
                        for(String id : ids)
                            sender.sendMessage("  " + id);
                    }
                }
                else {
                    sender.sendMessage("IP address required as parameter");
                }
            } else if(c.equals("ips-for-id") && checkPlayerPermission(sender, "ips-for-id")) {
                if(args.length > 1) {
                    sender.sendMessage("IP addresses logged for player " + args[1] + ":");
                    for(String ip: ids_by_ip.keySet()) {
                        LinkedList<String> ids = ids_by_ip.get(ip);
                        if((ids != null) && ids.contains(args[1])) {
                            sender.sendMessage("  " + ip);
                        }
                    }
                }
                else {
                    sender.sendMessage("Player ID required as parameter");
                }
            } else if((c.equals("add-id-for-ip") && checkPlayerPermission(sender, "add-id-for-ip")) ||
                    (c.equals("del-id-for-ip") && checkPlayerPermission(sender, "del-id-for-ip"))) {
                if(args.length > 2) {
                    String ipaddr = "";
                    try {
                        InetAddress ip = InetAddress.getByName(args[2]);
                        ipaddr = ip.getHostAddress();
                    } catch (UnknownHostException uhx) {
                        sender.sendMessage("Invalid address : " + args[2]);
                        return false;
                    }
                    LinkedList<String> ids = ids_by_ip.get(ipaddr);
                    if(ids == null) {
                        ids = new LinkedList<String>();
                        ids_by_ip.put(ipaddr, ids);
                    }
                    ids.remove(args[1]); /* Remove existing, if any */
                    if(c.equals("add-id-for-ip")) {
                        ids.addFirst(args[1]);  /* And add us first */
                        sender.sendMessage("Added player ID '" + args[1] + "' to address '" + ipaddr + "'");
                    }
                    else {
                        sender.sendMessage("Removed player ID '" + args[1] + "' from address '" + ipaddr + "'");
                    }
                    saveIDsByIP();
                }
                else {
                    sender.sendMessage("Needs player ID and IP address");
                }
            }
            return true;
        }
        return false;
    }

    public boolean checkPlayerPermission(DynmapCommandSender sender, String permission) {
        if (!(sender instanceof DynmapPlayer) || sender.isOp()) {
            return true;
        } else if (!sender.hasPrivilege(permission.toLowerCase())) {
            sender.sendMessage("You don't have permission to use this command!");
            return false;
        }
        return true;
    }

    public ConfigurationNode getWorldConfiguration(DynmapWorld world) {
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
    
    private ConfigurationNode getDefaultTemplateConfigurationNode(DynmapWorld world) {
        String environmentName = world.getEnvironment();
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

    /*
     * Add in any missing sections to existing file, using resource
     */
    public boolean updateUsingDefaultResource(String resourcename, File deffile, String basenode) {
        InputStream in = getClass().getResourceAsStream(resourcename);
        if(in == null) {
            Log.severe("Unable to find resource - " + resourcename);
            return false;
        }
        if(deffile.canRead() == false) {    /* Doesn't exist? */
            return createDefaultFileFromResource(resourcename, deffile);
        }
        /* Load default from resource */
        ConfigurationNode def_fc = new ConfigurationNode(in);
        /* Load existing from file */
        ConfigurationNode fc = new ConfigurationNode(deffile);
        fc.load();
        /* Now, get the list associated with the base node default */
        List<Map<String,Object>> existing = fc.getMapList(basenode);
        Set<String> existing_names = new HashSet<String>();
        /* Make map, indexed by 'name' in map */
        if(existing != null) {
            for(Map<String,Object> m : existing) {
                Object name = m.get("name");
                if(name instanceof String)
                    existing_names.add((String)name);
            }
        }
        boolean did_update = false;
        /* Now, loop through defaults, and see if any are missing */
        List<Map<String,Object>> defmaps = def_fc.getMapList(basenode);
        if(defmaps != null) {
            for(Map<String,Object> m : defmaps) {
                Object name = m.get("name");
                if(name instanceof String) {
                    /* If not an existing one, need to add it */
                    if(existing_names.contains((String)name) == false) {
                        existing.add(m);
                        did_update = true;
                    }
                }
            }
        }
        /* If we did update, save existing */
        if(did_update) {
            fc.put(basenode, existing);
            fc.save(deffile);
            Log.info("Updated file " + deffile.getPath());
        }
        return true;
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
     * Register markers API - used by component to supply marker API to plugin
     */
    public void registerMarkerAPI(MarkerAPIImpl api) {
        markerapi = api;
    }
    /*
     * Pause full/radius render processing
     * @param dopause - true to pause, false to unpause
     */
    public void setPauseFullRadiusRenders(boolean dopause) {
        mapManager.setPauseFullRadiusRenders(dopause);
    }
    /*
     * Test if full renders are paused
     */
    public boolean getPauseFullRadiusRenders() {
        return mapManager.getPauseFullRadiusRenders();
    }
    /*
     * Pause update render processing
     * @param dopause - true to pause, false to unpause
     */
    public void setPauseUpdateRenders(boolean dopause) {
        mapManager.setPauseUpdateRenders(dopause);
    }
    /*
     * Test if update renders are paused
     */
    public boolean getPauseUpdateRenders() {
        return mapManager.getPauseUpdateRenders();
    }
    /**
     * Get list of IDs seen on give IP (most recent to least recent)
     */
    public List<String> getIDsForIP(InetAddress addr) {
        return getIDsForIP(addr.getHostAddress());
    }
    /**
     * Get list of IDs seen on give IP (most recent to least recent)
     */
    public List<String> getIDsForIP(String ip) {
        LinkedList<String> ids = ids_by_ip.get(ip);
        if(ids != null)
            return new ArrayList<String>(ids);
        return null;
    }

    private void loadIDsByIP() {
        File f = new File(getDataFolder(), "ids-by-ip.txt");
        if(f.exists() == false)
            return;
        ConfigurationNode fc = new ConfigurationNode(new File(getDataFolder(), "ids-by-ip.txt"));
        try {
            fc.load();
            ids_by_ip.clear();
            for(String k : fc.keySet()) {
                List<String> ids = fc.getList(k);
                if(ids != null) {
                    k = k.replace("_", ".");
                    ids_by_ip.put(k, new LinkedList<String>(ids));
                }
            }
        } catch (Exception iox) {
            Log.severe("Error loading " + f.getPath() + " - " + iox.getMessage());
        }
    }
    private void saveIDsByIP() {
        File f = new File(getDataFolder(), "ids-by-ip.txt");
        ConfigurationNode fc = new ConfigurationNode();
        for(String k : ids_by_ip.keySet()) {
            List<String> v = ids_by_ip.get(k);
            if(v != null) {
                k = k.replace(".", "_");
                fc.put(k, v);
            }
        }
        try {
            fc.save(f);
        } catch (Exception x) {
            Log.severe("Error saving " + f.getPath() + " - " + x.getMessage());
        }
    }

    public void setPlayerVisiblity(String player, boolean is_visible) {
        playerList.setVisible(player, is_visible);
    }

    public boolean getPlayerVisbility(String player) {
        return playerList.isVisiblePlayer(player);
    }

    public void postPlayerMessageToWeb(String playerid, String playerdisplay, String message) {
        if(playerdisplay == null) playerdisplay = playerid;
        if(mapManager != null)
            mapManager.pushUpdate(new Client.ChatMessage("player", "", playerid, message, playerdisplay));
    }

    public void postPlayerJoinQuitToWeb(String playerid, String playerdisplay, boolean isjoin) {
        if(playerdisplay == null) playerdisplay = playerid;
        if((mapManager != null) && (playerList != null) && (playerList.isVisiblePlayer(playerid))) {
            if(isjoin)
                mapManager.pushUpdate(new Client.PlayerJoinMessage(playerid, playerdisplay));
            else
                mapManager.pushUpdate(new Client.PlayerQuitMessage(playerid, playerdisplay));
        }
    }

    public String getDynmapCoreVersion() {
        return version;
    }

    public int triggerRenderOfBlock(String wid, int x, int y, int z) {
        if(mapManager != null)
            mapManager.touch(wid, x, y, z, "api");
        return 0;
    }
    
    public int triggerRenderOfVolume(String wid, int minx, int miny, int minz, int maxx, int maxy, int maxz) {
        if(mapManager != null) {
            if((minx == maxx) && (miny == maxy) && (minz == maxz))
                mapManager.touch(wid, minx, miny, minz, "api");
            else
                mapManager.touchVolume(wid, minx, miny, minz, maxx, maxy, maxz, "api");
        }
        return 0;
    }    

    public boolean isTrigger(String s) {
        return enabledTriggers.contains(s);
    }
    
    public DynmapWorld getWorld(String wid) {
        if(mapManager != null)
            return mapManager.getWorld(wid);
        return null;
    }
    /* Called by plugin when world loaded */
    public boolean processWorldLoad(DynmapWorld w) {
        return mapManager.activateWorld(w);
    }
    
    /* Load core version */
    private void loadVersion() {
        InputStream in = getClass().getResourceAsStream("/core.yml");
        if(in == null)
            return;
        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String,Object> val = (Map<String,Object>)yaml.load(in);
        if(val != null)
            version = (String)val.get("version");
    }
}
