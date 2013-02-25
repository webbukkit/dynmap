package org.dynmap.bukkit;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.dynmap.DynmapAPI;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWebChatEvent;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.MapType;
import org.dynmap.PlayerList;
import org.dynmap.bukkit.permissions.BukkitPermissions;
import org.dynmap.bukkit.permissions.NijikokunPermissions;
import org.dynmap.bukkit.permissions.OpPermissions;
import org.dynmap.bukkit.permissions.PEXPermissions;
import org.dynmap.bukkit.permissions.PermBukkitPermissions;
import org.dynmap.bukkit.permissions.PermissionProvider;
import org.dynmap.bukkit.permissions.bPermPermissions;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.hdmap.HDMap;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.VisibilityLimit;

public class DynmapPlugin extends JavaPlugin implements DynmapAPI {
    private DynmapCore core;
    private PermissionProvider permissions;
    private String version;
    public SnapshotCache sscache;
    private boolean has_spout = false;
    public PlayerList playerList;
    private MapManager mapManager;
    public static DynmapPlugin plugin;
    public SpoutPluginBlocks spb;
    public PluginManager pm;
    private Metrics metrics;
    private BukkitEnableCoreCallback enabCoreCB = new BukkitEnableCoreCallback();
    private Method ismodloaded;
    private HashMap<String, BukkitWorld> world_by_name = new HashMap<String, BukkitWorld>();
    private HashSet<String> modsused = new HashSet<String>();
    /* Lookup cache */
    private World last_world;
    private BukkitWorld last_bworld;
    
    private BukkitVersionHelper helper = BukkitVersionHelper.getHelper();
    
    private final BukkitWorld getWorldByName(String name) {
        if((last_world != null) && (last_world.getName().equals(name))) {
            return last_bworld;
        }
        return world_by_name.get(name);
    }
    private final BukkitWorld getWorld(World w) {
        if(last_world == w) {
            return last_bworld;
        }
        BukkitWorld bw = world_by_name.get(w.getName());
        if(bw == null) {
            bw = new BukkitWorld(w);
            world_by_name.put(w.getName(), bw);
        }
        else if(bw.isLoaded() == false) {
            bw.setWorldLoaded(w);
        }
        last_world = w;
        last_bworld = bw;

        return bw;
    }
    private final void removeWorld(World w) {
        world_by_name.remove(w.getName());
        if(w == last_world) {
            last_world = null;
            last_bworld = null;
        }
    }
    
    private class BukkitEnableCoreCallback extends DynmapCore.EnableCoreCallbacks {
        @Override
        public void configurationLoaded() {
            /* Check for Spout */
            if(detectSpout()) {
                if(core.configuration.getBoolean("spout/enabled", true)) {
                    has_spout = true;
                    Log.info("Detected Spout");
                    if(spb == null) {
                        spb = new SpoutPluginBlocks(DynmapPlugin.this);
                    }
                    modsused.add("SpoutPlugin");
                }
                else {
                    Log.info("Detected Spout - Support Disabled");
                }
            }
            if(!has_spout) {    /* If not, clean up old spout texture, if needed */
                File st = new File(core.getDataFolder(), "renderdata/spout-texture.txt");
                if(st.exists())
                    st.delete();
            }
            
        }
    }
    
    private static class BlockToCheck {
        Location loc;
        int typeid;
        byte data;
        String trigger;
    };
    private LinkedList<BlockToCheck> blocks_to_check = null;
    private LinkedList<BlockToCheck> blocks_to_check_accum = new LinkedList<BlockToCheck>();
    
    public DynmapPlugin() {
        plugin = this;
        try {
            Class<?> c = Class.forName("cpw.mods.fml.common.Loader");
            ismodloaded = c.getMethod("isModLoaded", String.class);
        } catch (NoSuchMethodException nsmx) {
        } catch (ClassNotFoundException e) {
        }
    }
    
    /**
     * Server access abstraction class
     */
    public class BukkitServer implements DynmapServerInterface {
        /* Chunk load handling */
        private Object loadlock = new Object();
        private int chunks_in_cur_tick = 0;
        private long cur_tick;

        @Override
        public int getBlockIDAt(String wname, int x, int y, int z) {
            World w = getServer().getWorld(wname);
            if((w != null) && w.isChunkLoaded(x >> 4, z >> 4)) {
                return w.getBlockTypeIdAt(x,  y,  z);
            }
            return -1;
        }

        @Override
        public void scheduleServerTask(Runnable run, long delay) {
            getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.this, run, delay);
        }
        @Override
        public DynmapPlayer[] getOnlinePlayers() {
            Player[] players = getServer().getOnlinePlayers();
            DynmapPlayer[] dplay = new DynmapPlayer[players.length];
            for(int i = 0; i < players.length; i++)
                dplay[i] = new BukkitPlayer(players[i]);
            return dplay;
        }
        @Override
        public void reload() {
            PluginManager pluginManager = getServer().getPluginManager();
            pluginManager.disablePlugin(DynmapPlugin.this);
            pluginManager.enablePlugin(DynmapPlugin.this);
        }
        @Override
        public DynmapPlayer getPlayer(String name) {
            Player p = getServer().getPlayerExact(name);
            if(p != null) {
                return new BukkitPlayer(p);
            }
            return null;
        }
        @Override
        public Set<String> getIPBans() {
            return getServer().getIPBans();
        }
        @Override
        public <T> Future<T> callSyncMethod(Callable<T> task) {
            return getServer().getScheduler().callSyncMethod(DynmapPlugin.this, task);
        }
        @Override
        public String getServerName() {
            return getServer().getServerName();
        }
        @Override
        public boolean isPlayerBanned(String pid) {
            OfflinePlayer p = getServer().getOfflinePlayer(pid);
            if((p != null) && p.isBanned())
                return true;
            return false;
        }
        @Override
        public String stripChatColor(String s) {
            return ChatColor.stripColor(s);
        }
        private Set<EventType> registered = new HashSet<EventType>();
        @Override
        public boolean requestEventNotification(EventType type) {
            if(registered.contains(type))
                return true;
            switch(type) {
                case WORLD_LOAD:
                case WORLD_UNLOAD:
                    /* Already called for normal world activation/deactivation */
                    break;
                case WORLD_SPAWN_CHANGE:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR)
                        public void onSpawnChange(SpawnChangeEvent evt) {
                            BukkitWorld w = getWorld(evt.getWorld());
                            core.listenerManager.processWorldEvent(EventType.WORLD_SPAWN_CHANGE, w);
                        }
                    }, DynmapPlugin.this);
                    break;
                case PLAYER_JOIN:
                case PLAYER_QUIT:
                    /* Already handled */
                    break;
                case PLAYER_BED_LEAVE:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR)
                        public void onPlayerBedLeave(PlayerBedLeaveEvent evt) {
                            DynmapPlayer p = new BukkitPlayer(evt.getPlayer());
                            core.listenerManager.processPlayerEvent(EventType.PLAYER_BED_LEAVE, p);
                        }
                    }, DynmapPlugin.this);
                    break;
                case PLAYER_CHAT:
                    try {
                        Class.forName("org.bukkit.event.player.AsyncPlayerChatEvent");
                        pm.registerEvents(new Listener() {
                            @EventHandler(priority=EventPriority.MONITOR)
                            public void onPlayerChat(AsyncPlayerChatEvent evt) {
                                if(evt.isCancelled()) return;
                                final Player p = evt.getPlayer();
                                final String msg = evt.getMessage();
                                getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.this, new Runnable() {
                                    public void run() {
                                        DynmapPlayer dp = null;
                                        if(p != null)
                                            dp = new BukkitPlayer(p);
                                        core.listenerManager.processChatEvent(EventType.PLAYER_CHAT, dp, msg);
                                    }
                                });
                            }
                        }, DynmapPlugin.this);
                    } catch (ClassNotFoundException cnfx) {
                        pm.registerEvents(new Listener() {
                            @EventHandler(priority=EventPriority.MONITOR)
                            public void onPlayerChat(PlayerChatEvent evt) {
                                if(evt.isCancelled()) return;
                                DynmapPlayer p = null;
                                if(evt.getPlayer() != null)
                                    p = new BukkitPlayer(evt.getPlayer());
                                core.listenerManager.processChatEvent(EventType.PLAYER_CHAT, p, evt.getMessage());
                            }
                        }, DynmapPlugin.this);
                    }
                    break;
                case BLOCK_BREAK:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR)
                        public void onBlockBreak(BlockBreakEvent evt) {
                            if(evt.isCancelled()) return;
                            Block b = evt.getBlock();
                            if(b == null) return;   /* Work around for stupid mods.... */
                            Location l = b.getLocation();
                            core.listenerManager.processBlockEvent(EventType.BLOCK_BREAK, b.getType().getId(),
                                getWorld(l.getWorld()).getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
                        }
                    }, DynmapPlugin.this);
                    break;
                case SIGN_CHANGE:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR)
                        public void onSignChange(SignChangeEvent evt) {
                            if(evt.isCancelled()) return;
                            Block b = evt.getBlock();
                            Location l = b.getLocation();
                            String[] lines = evt.getLines();    /* Note: changes to this change event - intentional */
                            DynmapPlayer dp = null;
                            Player p = evt.getPlayer();
                            if(p != null) dp = new BukkitPlayer(p);
                            core.listenerManager.processSignChangeEvent(EventType.SIGN_CHANGE, b.getType().getId(),
                                getWorld(l.getWorld()).getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), lines, dp);
                        }
                    }, DynmapPlugin.this);
                    break;
                default:
                    Log.severe("Unhandled event type: " + type);
                    return false;
            }
            registered.add(type);
            return true;
        }
        @Override
        public boolean sendWebChatEvent(String source, String name, String msg) {
            DynmapWebChatEvent evt = new DynmapWebChatEvent(source, name, msg);
            getServer().getPluginManager().callEvent(evt);
            return ((evt.isCancelled() == false) && (evt.isProcessed() == false));
        }
        @Override
        public void broadcastMessage(String msg) {
            getServer().broadcastMessage(msg);
        }
        @Override
        public String[] getBiomeIDs() {
            BiomeMap[] b = BiomeMap.values();
            String[] bname = new String[b.length];
            for(int i = 0; i < bname.length; i++)
                bname[i] = b[i].toString();
            return bname;
        }
        @Override
        public double getCacheHitRate() {
            return sscache.getHitRate();
        }
        @Override
        public void resetCacheStats() {
            sscache.resetStats();
        }
        @Override
        public DynmapWorld getWorldByName(String wname) {
            return DynmapPlugin.this.getWorldByName(wname);
        }
        @Override
        public DynmapPlayer getOfflinePlayer(String name) {
            OfflinePlayer op = getServer().getOfflinePlayer(name);
            if(op != null) {
                return new BukkitPlayer(op);
            }
            return null;
        }
        @Override
        public Set<String> checkPlayerPermissions(String player, Set<String> perms) {
            OfflinePlayer p = getServer().getOfflinePlayer(player);
            if(p.isBanned())
                return new HashSet<String>();
            Set<String> rslt = permissions.hasOfflinePermissions(player, perms);
            if (rslt == null) {
                rslt = new HashSet<String>();
                if(p.isOp()) {
                    rslt.addAll(perms);
                }
            }
            return rslt;
        }
        @Override
        public boolean checkPlayerPermission(String player, String perm) {
            OfflinePlayer p = getServer().getOfflinePlayer(player);
            if(p.isBanned())
                return false;
            boolean rslt = permissions.hasOfflinePermission(player, perm);
            return rslt;
        }
        /**
         * Render processor helper - used by code running on render threads to request chunk snapshot cache from server/sync thread
         */
        @Override
        public MapChunkCache createMapChunkCache(DynmapWorld w, List<DynmapChunk> chunks, 
                boolean blockdata, boolean highesty, boolean biome, boolean rawbiome) {
            MapChunkCache c = w.getChunkCache(chunks);
            if(c == null) { /* Can fail if not currently loaded */
                return null;
            }
            if(w.visibility_limits != null) {
                for(VisibilityLimit limit: w.visibility_limits) {
                    c.setVisibleRange(limit);
                }
                c.setHiddenFillStyle(w.hiddenchunkstyle);
                c.setAutoGenerateVisbileRanges(w.do_autogenerate);
            }
            if(w.hidden_limits != null) {
                for(VisibilityLimit limit: w.hidden_limits) {
                    c.setHiddenRange(limit);
                }
                c.setHiddenFillStyle(w.hiddenchunkstyle);
            }
            if(c.setChunkDataTypes(blockdata, biome, highesty, rawbiome) == false) {
                Log.severe("CraftBukkit build does not support biome APIs");
            }
            if(chunks.size() == 0) {    /* No chunks to get? */
                c.loadChunks(0);
                return c;
            }

            final MapChunkCache cc = c;

            while(!cc.isDoneLoading()) {
                synchronized(loadlock) {
                    long now = System.currentTimeMillis();
                    
                    if(cur_tick != (now/50)) {  /* New tick? */
                        chunks_in_cur_tick = mapManager.getMaxChunkLoadsPerTick();
                        cur_tick = now/50;
                    }
                }
                Future<Boolean> f = core.getServer().callSyncMethod(new Callable<Boolean>() {
                    public Boolean call() throws Exception {
                        boolean exhausted;
                        synchronized(loadlock) {
                            if(chunks_in_cur_tick > 0) {
                                chunks_in_cur_tick -= cc.loadChunks(chunks_in_cur_tick);
                            }
                            exhausted = (chunks_in_cur_tick == 0);
                        }
                        return exhausted;
                    }
                });
                Boolean delay;
                try {
                    delay = f.get();
                } catch (CancellationException cx) {
                    return null;
                } catch (ExecutionException ex) {
                    Log.severe("Exception while fetching chunks: ", ex.getCause());
                    return null;
                } catch (Exception ix) {
                    Log.severe(ix);
                    return null;
                }
                if((delay != null) && delay.booleanValue()) {
                    try { Thread.sleep(25); } catch (InterruptedException ix) {}
                }
            }
            /* If cancelled due to world unload return nothing */
            if(w.isLoaded() == false)
                return null;
            return c;
        }
        @Override
        public int getMaxPlayers() {
            return getServer().getMaxPlayers();
        }
        @Override
        public int getCurrentPlayers() {
            return getServer().getOnlinePlayers().length;
        }
        @Override
        public boolean isModLoaded(String name) {
            if(ismodloaded != null) {
                try {
                    Object rslt =ismodloaded.invoke(null,  name);
                    if(rslt instanceof Boolean) {
                        if(((Boolean)rslt).booleanValue()) {
                            modsused.add(name);
                            return true;
                        }
                    }
                } catch (IllegalArgumentException iax) {
                } catch (IllegalAccessException e) {
                } catch (InvocationTargetException e) {
                }
            }
            return false;
        }
    }
    /**
     * Player access abstraction class
     */
    public class BukkitPlayer extends BukkitCommandSender implements DynmapPlayer {
        private Player player;
        private OfflinePlayer offplayer;
        
        public BukkitPlayer(Player p) {
            super(p);
            player = p;
            offplayer = p.getPlayer();
        }
        public BukkitPlayer(OfflinePlayer p) {
            super(null);
            offplayer = p;
        }
        @Override
        public boolean isConnected() {
            return offplayer.isOnline();
        }
        @Override
        public String getName() {
            return offplayer.getName();
        }
        @Override
        public String getDisplayName() {
            if(player != null)
                return player.getDisplayName();
            else
                return offplayer.getName();
        }
        @Override
        public boolean isOnline() {
            return offplayer.isOnline();
        }
        @Override
        public DynmapLocation getLocation() {
            if(player == null) {
                return null;
            }
            Location loc = player.getEyeLocation(); // Use eye location, since we show head 
            return toLoc(loc);
        }
        @Override
        public String getWorld() {
            if(player == null) {
                return null;
            }
            World w = player.getWorld();
            if(w != null)
                return DynmapPlugin.this.getWorld(w).getName();
            return null;
        }
        @Override
        public InetSocketAddress getAddress() {
            if(player != null)
                return player.getAddress();
            return null;
        }
        @Override
        public boolean isSneaking() {
            if(player != null)
                return player.isSneaking();
            return false;
        }
        @Override
        public int getHealth() {
            if(player != null)
                return player.getHealth();
            else
                return 0;
        }
        @Override
        public int getArmorPoints() {
            if(player != null)
                return Armor.getArmorPoints(player);
            else
                return 0;
        }
        @Override
        public DynmapLocation getBedSpawnLocation() {
            Location loc = offplayer.getBedSpawnLocation();
            if(loc != null) {
                return toLoc(loc);
            }
            return null;
        }
        @Override
        public long getLastLoginTime() {
            return offplayer.getLastPlayed();
        }
        @Override
        public long getFirstLoginTime() {
            return offplayer.getFirstPlayed();
        }
        @Override
        public boolean isInvisible() {
            if(player != null) {
                return player.hasPotionEffect(PotionEffectType.INVISIBILITY);
            }
            return false;
        }
    }
    /* Handler for generic console command sender */
    public class BukkitCommandSender implements DynmapCommandSender {
        private CommandSender sender;

        public BukkitCommandSender(CommandSender send) {
            sender = send;
        }
        
        @Override
        public boolean hasPrivilege(String privid) {
            if(sender != null)
                return permissions.has(sender, privid);
            return false;
        }

        @Override
        public void sendMessage(String msg) {
            if(sender != null)
                sender.sendMessage(msg);
        }

        @Override
        public boolean isConnected() {
            if(sender != null)
                return true;
            return false;
        }
        @Override
        public boolean isOp() {
            if(sender != null)
                return sender.isOp();
            else
                return false;
        }
    }
    
    public void loadExtraBiomes() {
        int cnt = 0;
        
        /* Find array of biomes in biomebase */
        Object[] biomelist = helper.getBiomeBaseList();
        /* Loop through list, starting afer well known biomes */
        for(int i = BiomeMap.LAST_WELL_KNOWN+1; i < biomelist.length; i++) {
            Object bb = biomelist[i];
            if(bb != null) {
                String id =  helper.getBiomeBaseIDString(bb);
                if(id == null) {
                   id = "BIOME_" + i;
                }
                float tmp = helper.getBiomeBaseTemperature(bb);
                float hum = helper.getBiomeBaseHumidity(bb);

                BiomeMap m = new BiomeMap(i, id, tmp, hum);
                Log.verboseinfo("Add custom biome [" + m.toString() + "] (" + i + ")");
                cnt++;
            }
        }
        if(cnt > 0) {
            Log.info("Added " + cnt + " custom biome mappings");
        }
    }
    
    @Override
    public void onEnable() {
        pm = this.getServer().getPluginManager();
        
        PluginDescriptionFile pdfFile = this.getDescription();
        version = pdfFile.getVersion();

        /* Load extra biomes, if any */
        loadExtraBiomes();
             
        /* Set up player login/quit event handler */
        registerPlayerLoginListener();

        /* Build default permissions from our plugin */
        Map<String, Boolean> perdefs = new HashMap<String, Boolean>();
        List<Permission> pd = plugin.getDescription().getPermissions();
        for(Permission p : pd) {
            perdefs.put(p.getName(), p.getDefault() == PermissionDefault.TRUE);
        }
        
        permissions = PEXPermissions.create(getServer(), "dynmap");
        if (permissions == null)
            permissions = bPermPermissions.create(getServer(), "dynmap", perdefs);
        if (permissions == null)
            permissions = PermBukkitPermissions.create(getServer(), "dynmap", perdefs);
        if (permissions == null)
            permissions = NijikokunPermissions.create(getServer(), "dynmap");
        if (permissions == null)
            permissions = BukkitPermissions.create("dynmap", perdefs);
        if (permissions == null)
            permissions = new OpPermissions(new String[] { "fullrender", "cancelrender", "radiusrender", "resetstats", "reload", "purgequeue", "pause", "ips-for-id", "ids-for-ip", "add-id-for-ip", "del-id-for-ip" });
        /* Get and initialize data folder */
        File dataDirectory = this.getDataFolder();
        if(dataDirectory.exists() == false)
            dataDirectory.mkdirs();
 
        /* Get MC version */
        String bukkitver = getServer().getVersion();
        String mcver = "1.0.0";
        int idx = bukkitver.indexOf("(MC: ");
        if(idx > 0) {
            mcver = bukkitver.substring(idx+5);
            idx = mcver.indexOf(")");
            if(idx > 0) mcver = mcver.substring(0, idx);
        }
        
        /* Instantiate core */
        if(core == null)
            core = new DynmapCore();
        /* Inject dependencies */
        core.setPluginVersion(version, "CraftBukkit");
        core.setMinecraftVersion(mcver);
        core.setDataFolder(dataDirectory);
        core.setServer(new BukkitServer());
        
        /* Load configuration */
        if(!core.initConfiguration(enabCoreCB)) {
            this.setEnabled(false);
            return;
        }
        /* See if we need to wait before enabling core */
        if(!readyToEnable()) {
            Listener pl = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onPluginEnabled(PluginEnableEvent evt) {
                    if (!readyToEnable()) {
                        spb.markPluginEnabled(evt.getPlugin());
                        if (readyToEnable()) { /* If we;re ready now, finish enable */
                            doEnable();   /* Finish enable */
                        }
                    }
                }
            };
            pm.registerEvents(pl, this);
        }
        else {
            doEnable();
        }
    }
    
    private boolean readyToEnable() {
        if (spb != null) {
            return spb.isReady();
        }
        return true;
    }
    
    private void doEnable() {
        /* Prep spout support, if needed */
        if(spb != null) {
            spb.processSpoutBlocks(this, core);
        }
        
        /* Enable core */
        if(!core.enableCore(enabCoreCB)) {
            this.setEnabled(false);
            return;
        }
        playerList = core.playerList;
        sscache = new SnapshotCache(core.getSnapShotCacheSize());

        /* Get map manager from core */
        mapManager = core.getMapManager();
        /* Initialized the currently loaded worlds */
        for (World world : getServer().getWorlds()) {
            BukkitWorld w = getWorld(world);
            if(core.processWorldLoad(w))    /* Have core process load first - fire event listeners if good load after */
                core.listenerManager.processWorldEvent(EventType.WORLD_LOAD, w);
        }
    
        /* Register our update trigger events */
        registerEvents();

        /* Submit metrics to mcstats.org */
        initMetrics();
        
        Log.info("Enabled");
    }
    
    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics = null;
        }
        
        /* Disable core */
        core.disableCore();

        if(sscache != null) {
            sscache.cleanup();
            sscache = null; 
        }
        Log.info("Disabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        DynmapCommandSender dsender;
        if(sender instanceof Player) {
            dsender = new BukkitPlayer((Player)sender);
        }
        else {
            dsender = new BukkitCommandSender(sender);
        }
        return core.processCommand(dsender, cmd.getName(), commandLabel, args);
    }

    
    @Override
    public final MarkerAPI getMarkerAPI() {
        return core.getMarkerAPI();
    }

    @Override
    public final boolean markerAPIInitialized() {
        return core.markerAPIInitialized();
    }

    @Override
    public final boolean sendBroadcastToWeb(String sender, String msg) {
        return core.sendBroadcastToWeb(sender, msg);
    }

    @Override
    public final int triggerRenderOfVolume(String wid, int minx, int miny, int minz,
            int maxx, int maxy, int maxz) {
        return core.triggerRenderOfVolume(wid, minx, miny, minz, maxx, maxy, maxz);
    }

    @Override
    public final int triggerRenderOfBlock(String wid, int x, int y, int z) {
        return core.triggerRenderOfBlock(wid, x, y, z);
    }

    @Override
    public final void setPauseFullRadiusRenders(boolean dopause) {
        core.setPauseFullRadiusRenders(dopause);
    }

    @Override
    public final boolean getPauseFullRadiusRenders() {
        return core.getPauseFullRadiusRenders();
    }

    @Override
    public final void setPauseUpdateRenders(boolean dopause) {
        core.setPauseUpdateRenders(dopause);
    }

    @Override
    public final boolean getPauseUpdateRenders() {
        return core.getPauseUpdateRenders();
    }

    @Override
    public final void setPlayerVisiblity(String player, boolean is_visible) {
        core.setPlayerVisiblity(player, is_visible);
    }

    @Override
    public final boolean getPlayerVisbility(String player) {
        return core.getPlayerVisbility(player);
    }

    @Override
    public final void postPlayerMessageToWeb(String playerid, String playerdisplay,
            String message) {
        core.postPlayerMessageToWeb(playerid, playerdisplay, message);
    }

    @Override
    public final void postPlayerJoinQuitToWeb(String playerid, String playerdisplay,
            boolean isjoin) {
        core.postPlayerJoinQuitToWeb(playerid, playerdisplay, isjoin);
    }

    @Override
    public final String getDynmapCoreVersion() {
        return core.getDynmapCoreVersion();
    }

    @Override
    public final int triggerRenderOfVolume(Location l0, Location l1) {
        int x0 = l0.getBlockX(), y0 = l0.getBlockY(), z0 = l0.getBlockZ();
        int x1 = l1.getBlockX(), y1 = l1.getBlockY(), z1 = l1.getBlockZ();
        
        return core.triggerRenderOfVolume(getWorld(l0.getWorld()).getName(), Math.min(x0, x1), Math.min(y0, y1),
                Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1));
    }

    @Override
    public final void setPlayerVisiblity(Player player, boolean is_visible) {
        core.setPlayerVisiblity(player.getName(), is_visible);
    }

    @Override
    public final boolean getPlayerVisbility(Player player) {
        return core.getPlayerVisbility(player.getName());
    }

    @Override
    public final void postPlayerMessageToWeb(Player player, String message) {
        core.postPlayerMessageToWeb(player.getName(), player.getDisplayName(), message);
    }

    @Override
    public void postPlayerJoinQuitToWeb(Player player, boolean isjoin) {
        core.postPlayerJoinQuitToWeb(player.getName(), player.getDisplayName(), isjoin);
    }

    @Override
    public String getDynmapVersion() {
        return version;
    }
    
    private static DynmapLocation toLoc(Location l) {
        return new DynmapLocation(DynmapWorld.normalizeWorldName(l.getWorld().getName()), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }
    
    private void registerPlayerLoginListener() {
        Listener pl = new Listener() {
            @EventHandler(priority=EventPriority.MONITOR)
            public void onPlayerJoin(PlayerJoinEvent evt) {
                DynmapPlayer dp = new BukkitPlayer(evt.getPlayer());                
                core.listenerManager.processPlayerEvent(EventType.PLAYER_JOIN, dp);
            }
            @EventHandler(priority=EventPriority.MONITOR)
            public void onPlayerQuit(PlayerQuitEvent evt) {
                DynmapPlayer dp = new BukkitPlayer(evt.getPlayer());
                core.listenerManager.processPlayerEvent(EventType.PLAYER_QUIT, dp);
            }
        };
        pm.registerEvents(pl, this);
    }

    private class BlockCheckHandler implements Runnable {
        public void run() {
            BlockToCheck btt;
            while(blocks_to_check.isEmpty() != true) {
                btt = blocks_to_check.pop();
                Location loc = btt.loc;
                World w = loc.getWorld();
                if(!w.isChunkLoaded(loc.getBlockX()>>4, loc.getBlockZ()>>4))
                    continue;
                int bt = w.getBlockTypeIdAt(loc);
                /* Avoid stationary and moving water churn */
                if(bt == 9) bt = 8;
                if(btt.typeid == 9) btt.typeid = 8;
                if((bt != btt.typeid) || (btt.data != w.getBlockAt(loc).getData())) {
                    String wn = getWorld(w).getName();
                    sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), btt.trigger);
                }
            }
            blocks_to_check = null;
            /* Kick next run, if one is needed */
            startIfNeeded();
        }
        public void startIfNeeded() {
            if((blocks_to_check == null) && (blocks_to_check_accum.isEmpty() == false)) { /* More pending? */
                blocks_to_check = blocks_to_check_accum;
                blocks_to_check_accum = new LinkedList<BlockToCheck>();
                getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.this, this, 10);
            }
        }
    }
    private BlockCheckHandler btth = new BlockCheckHandler();

    private void checkBlock(Block b, String trigger) {
        BlockToCheck btt = new BlockToCheck();
        btt.loc = b.getLocation();
        btt.typeid = b.getTypeId();
        btt.data = b.getData();
        btt.trigger = trigger;
        blocks_to_check_accum.add(btt); /* Add to accumulator */
        btth.startIfNeeded();
    }
    
    private boolean onplace;
    private boolean onbreak;
    private boolean onblockform;
    private boolean onblockfade;
    private boolean onblockspread;
    private boolean onblockfromto;
    private boolean onblockphysics;
    private boolean onleaves;
    private boolean onburn;
    private boolean onpiston;
    private boolean onplayerjoin;
    private boolean onplayermove;
    private boolean ongeneratechunk;
    private boolean onexplosion;
    private boolean onstructuregrow;
    private boolean onblockgrow;
    private boolean onblockredstone;

    private void registerEvents() {
        
        // To trigger rendering.
        onplace = core.isTrigger("blockplaced");
        onbreak = core.isTrigger("blockbreak");
        onleaves = core.isTrigger("leavesdecay");
        onburn = core.isTrigger("blockburn");
        onblockform = core.isTrigger("blockformed");
        onblockfade = core.isTrigger("blockfaded");
        onblockspread = core.isTrigger("blockspread");
        onblockfromto = core.isTrigger("blockfromto");
        onblockphysics = core.isTrigger("blockphysics");
        onpiston = core.isTrigger("pistonmoved");
        onblockfade = core.isTrigger("blockfaded");
        onblockredstone = core.isTrigger("blockredstone");
        
        if(onplace) {
            Listener placelistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onBlockPlace(BlockPlaceEvent event) {
                    if(event.isCancelled())
                        return;
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockplace");
                }
            };
            pm.registerEvents(placelistener, this);
        }
        
        if(onbreak) {
            Listener breaklistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onBlockBreak(BlockBreakEvent event) {
                    if(event.isCancelled())
                        return;
                    Block b = event.getBlock();
                    if(b == null) return;   /* Stupid mod workaround */
                    Location loc = b.getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockbreak");
                }
            };
            pm.registerEvents(breaklistener, this);
        }
        
        if(onleaves) {
            Listener leaveslistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onLeavesDecay(LeavesDecayEvent event) {
                    if(event.isCancelled())
                        return;
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    if(onleaves) {
                        mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "leavesdecay");
                    }
                }
            };
            pm.registerEvents(leaveslistener, this);
        }

        if(onburn) {
            Listener burnlistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onBlockBurn(BlockBurnEvent event) {
                    if(event.isCancelled())
                        return;
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    if(onburn) {
                        mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockburn");
                    }
                }
            };
            pm.registerEvents(burnlistener, this);
        }
        
        if(onblockphysics) {
            Listener physlistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onBlockPhysics(BlockPhysicsEvent event) {
                    if(event.isCancelled())
                        return;
                    Block b = event.getBlock();
                    Material m = b.getType();
                    if(m == null) return;
                    switch(m) {
                        case STATIONARY_WATER:
                        case WATER:
                        case STATIONARY_LAVA:
                        case LAVA:
                        case GRAVEL:
                        case SAND:
                            checkBlock(b, "blockphysics");
                            break;
                        default:
                            break;
                    }
                }
            };
            pm.registerEvents(physlistener, this);
        }
        
        if(onblockfromto) {
            Listener fromtolistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onBlockFromTo(BlockFromToEvent event) {
                    if(event.isCancelled())
                        return;
                    Block b = event.getBlock();
                    Material m = b.getType();
                    if((m != Material.WOOD_PLATE) && (m != Material.STONE_PLATE) && (m != null)) 
                        checkBlock(b, "blockfromto");
                    b = event.getToBlock();
                    m = b.getType();
                    if((m != Material.WOOD_PLATE) && (m != Material.STONE_PLATE) && (m != null)) 
                        checkBlock(b, "blockfromto");
                }
            };
            pm.registerEvents(fromtolistener, this);
        }
        
        if(onpiston) {
            Listener pistonlistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onBlockPistonRetract(BlockPistonRetractEvent event) {
                    if(event.isCancelled())
                        return;
                    Block b = event.getBlock();
                    Location loc = b.getLocation();
                    BlockFace dir;
                    try {   /* Workaround Bukkit bug = http://leaky.bukkit.org/issues/1227 */
                        dir = event.getDirection();
                    } catch (ClassCastException ccx) {
                        dir = BlockFace.NORTH;
                    }
                    String wn = getWorld(loc.getWorld()).getName();
                    int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
                    sscache.invalidateSnapshot(wn, x, y, z);
                    if(onpiston)
                        mapManager.touch(wn, x, y, z, "pistonretract");
                    for(int i = 0; i < 2; i++) {
                        x += dir.getModX();
                        y += dir.getModY();
                        z += dir.getModZ();
                        sscache.invalidateSnapshot(wn, x, y, z);
                        mapManager.touch(wn, x, y, z, "pistonretract");
                    }
                }

                @EventHandler(priority=EventPriority.MONITOR)
                public void onBlockPistonExtend(BlockPistonExtendEvent event) {
                    if(event.isCancelled())
                        return;
                    Block b = event.getBlock();
                    Location loc = b.getLocation();
                    BlockFace dir;
                    try {   /* Workaround Bukkit bug = http://leaky.bukkit.org/issues/1227 */
                        dir = event.getDirection();
                    } catch (ClassCastException ccx) {
                        dir = BlockFace.NORTH;
                    }
                    String wn = getWorld(loc.getWorld()).getName();
                    int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
                    sscache.invalidateSnapshot(wn, x, y, z);
                    if(onpiston)
                        mapManager.touch(wn, x, y, z, "pistonretract");
                    for(int i = 0; i < 1+event.getLength(); i++) {
                        x += dir.getModX();
                        y += dir.getModY();
                        z += dir.getModZ();
                        sscache.invalidateSnapshot(wn, x, y, z);
                        mapManager.touch(wn, x, y, z, "pistonretract");
                    }
                }
            };
            pm.registerEvents(pistonlistener, this);
        }
        
        if(onblockspread) {
            Listener spreadlistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onBlockSpread(BlockSpreadEvent event) {
                    if(event.isCancelled())
                        return;
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockspread");
                }
            };
            pm.registerEvents(spreadlistener, this);
        }
        
        if(onblockform) {
            Listener formlistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onBlockForm(BlockFormEvent event) {
                    if(event.isCancelled())
                        return;
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockform");
                }
            };
            pm.registerEvents(formlistener, this);
        }
        
        if(onblockfade) {
            Listener fadelistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onBlockFade(BlockFadeEvent event) {
                    if(event.isCancelled())
                        return;
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockfade");
                }
            };
            pm.registerEvents(fadelistener, this);
        }
        
        onblockgrow = core.isTrigger("blockgrow");
        
        if(onblockgrow) {
            try {
                Class.forName("org.bukkit.event.block.BlockGrowEvent");
                Listener growTrigger = new Listener() {
                    @EventHandler(priority=EventPriority.MONITOR)
                    public void onBlockGrow(BlockGrowEvent event) {
                        if(event.isCancelled())
                            return;
                        Location loc = event.getBlock().getLocation();
                        String wn = getWorld(loc.getWorld()).getName();
                        sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                        mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockgrow");
                    }
                };
                pm.registerEvents(growTrigger, this);
            } catch (ClassNotFoundException cnfx) {
                /* Pre-R5 - no grow event yet */
            }
        }
        
        if(onblockredstone) {
            Listener redstoneTrigger = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onBlockRedstone(BlockRedstoneEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockredstone");
                }
            };
            pm.registerEvents(redstoneTrigger,  this);
        }
        
        /* Register player event trigger handlers */
        Listener playerTrigger = new Listener() {
            @EventHandler(priority=EventPriority.MONITOR)
            public void onPlayerJoin(PlayerJoinEvent event) {
                if(onplayerjoin) {
                    Location loc = event.getPlayer().getLocation();
                    mapManager.touch(getWorld(loc.getWorld()).getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "playerjoin");
                }
            }
        };

        onplayerjoin = core.isTrigger("playerjoin");
        onplayermove = core.isTrigger("playermove");
        pm.registerEvents(playerTrigger, this);
        
        if(onplayermove) {
            Listener playermove = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onPlayerMove(PlayerMoveEvent event) {
                    Location loc = event.getPlayer().getLocation();
                    mapManager.touch(getWorld(loc.getWorld()).getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "playermove");
                }
            };
            pm.registerEvents(playermove, this);
            Log.warning("playermove trigger enabled - this trigger can cause excessive tile updating: use with caution");
        }
        /* Register entity event triggers */
        Listener entityTrigger = new Listener() {
            @EventHandler(priority=EventPriority.MONITOR)
            public void onEntityExplode(EntityExplodeEvent event) {
                Location loc = event.getLocation();
                String wname = getWorld(loc.getWorld()).getName();
                int minx, maxx, miny, maxy, minz, maxz;
                minx = maxx = loc.getBlockX();
                miny = maxy = loc.getBlockY();
                minz = maxz = loc.getBlockZ();
                /* Calculate volume impacted by explosion */
                List<Block> blocks = event.blockList();
                for(Block b: blocks) {
                    Location l = b.getLocation();
                    int x = l.getBlockX();
                    if(x < minx) minx = x;
                    if(x > maxx) maxx = x;
                    int y = l.getBlockY();
                    if(y < miny) miny = y;
                    if(y > maxy) maxy = y;
                    int z = l.getBlockZ();
                    if(z < minz) minz = z;
                    if(z > maxz) maxz = z;
                }
                sscache.invalidateSnapshot(wname, minx, miny, minz, maxx, maxy, maxz);
                if(onexplosion) {
                    mapManager.touchVolume(wname, minx, miny, minz, maxx, maxy, maxz, "entityexplode");
                }
            }
        };
        onexplosion = core.isTrigger("explosion");
        pm.registerEvents(entityTrigger, this);
        
        /* Register world event triggers */
        Listener worldTrigger = new Listener() {
            @EventHandler(priority=EventPriority.MONITOR)
            public void onWorldLoad(WorldLoadEvent event) {
                BukkitWorld w = getWorld(event.getWorld());
                if(core.processWorldLoad(w))    /* Have core process load first - fire event listeners if good load after */
                    core.listenerManager.processWorldEvent(EventType.WORLD_LOAD, w);
            }
            @EventHandler(priority=EventPriority.MONITOR)
            public void onWorldUnload(WorldUnloadEvent event) {
                BukkitWorld w = getWorld(event.getWorld());
                if(w != null) {
                    core.listenerManager.processWorldEvent(EventType.WORLD_UNLOAD, w);
                    w.setWorldUnloaded();
                    core.processWorldUnload(w);
                }
            }
            @EventHandler(priority=EventPriority.MONITOR)
            public void onStructureGrow(StructureGrowEvent event) {
                Location loc = event.getLocation();
                String wname = getWorld(loc.getWorld()).getName();
                int minx, maxx, miny, maxy, minz, maxz;
                minx = maxx = loc.getBlockX();
                miny = maxy = loc.getBlockY();
                minz = maxz = loc.getBlockZ();
                /* Calculate volume impacted by explosion */
                List<BlockState> blocks = event.getBlocks();
                for(BlockState b: blocks) {
                    int x = b.getX();
                    if(x < minx) minx = x;
                    if(x > maxx) maxx = x;
                    int y = b.getY();
                    if(y < miny) miny = y;
                    if(y > maxy) maxy = y;
                    int z = b.getZ();
                    if(z < minz) minz = z;
                    if(z > maxz) maxz = z;
                }
                sscache.invalidateSnapshot(wname, minx, miny, minz, maxx, maxy, maxz);
                if(onstructuregrow) {
                    mapManager.touchVolume(wname, minx, miny, minz, maxx, maxy, maxz, "structuregrow");
                }
            }
        };
        onstructuregrow = core.isTrigger("structuregrow");
        // To link configuration to real loaded worlds.
        pm.registerEvents(worldTrigger, this);

        ongeneratechunk = core.isTrigger("chunkgenerated");
        if(ongeneratechunk) {
            Listener chunkTrigger = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onChunkPopulate(ChunkPopulateEvent event) {
                    Chunk c = event.getChunk();
                    ChunkSnapshot cs = c.getChunkSnapshot();
                    int ymax = 0;
                    for(int i = 0; i < c.getWorld().getMaxHeight() / 16; i++) {
                        if(!cs.isSectionEmpty(i)) {
                            ymax = (i+1)*16;
                        }
                    }
                    /* Touch extreme corners */
                    int x = c.getX() << 4;
                    int z = c.getZ() << 4;
                    mapManager.touchVolume(getWorld(event.getWorld()).getName(), x, 0, z, x+15, ymax, z+16, "chunkpopulate");
                }
            };
            pm.registerEvents(chunkTrigger, this);
        }
    }

    private boolean detectSpout() {
        Plugin p = this.getServer().getPluginManager().getPlugin("Spout");
        if(p != null) {
            return p.isEnabled();
        }
        return false;
    }
    
    public boolean hasSpout() {
        return has_spout;
    }

    @Override
    public void assertPlayerInvisibility(String player, boolean is_invisible,
            String plugin_id) {
        core.assertPlayerInvisibility(player, is_invisible, plugin_id);
    }

    @Override
    public void assertPlayerInvisibility(Player player, boolean is_invisible,
            Plugin plugin) {
        core.assertPlayerInvisibility(player.getName(), is_invisible, plugin.getDescription().getName());
    }

    @Override
    public void assertPlayerVisibility(String player, boolean is_visible,
            String plugin_id) {
        core.assertPlayerVisibility(player, is_visible, plugin_id);
    }

    @Override
    public void assertPlayerVisibility(Player player, boolean is_visible,
            Plugin plugin) {
        core.assertPlayerVisibility(player.getName(), is_visible, plugin.getDescription().getName());
    }
    @Override
    public boolean setDisableChatToWebProcessing(boolean disable) {
        return core.setDisableChatToWebProcessing(disable);
    }
    @Override
    public boolean testIfPlayerVisibleToPlayer(String player, String player_to_see) {
        return core.testIfPlayerVisibleToPlayer(player, player_to_see);
    }
    @Override
    public boolean testIfPlayerInfoProtected() {
        return core.testIfPlayerInfoProtected();
    }
    
    private void initMetrics() {
        try {
            metrics = new Metrics(this);
            
            Metrics.Graph features = metrics.createGraph("Features Used");
            
            features.addPlotter(new Metrics.Plotter("Internal Web Server") {
                @Override
                public int getValue() {
                    if (!core.configuration.getBoolean("disable-webserver", false))
                        return 1;
                    return 0;
                }
            });
            features.addPlotter(new Metrics.Plotter("Spout") {
                @Override
                public int getValue() {
                    if(plugin.has_spout)
                        return 1;
                    return 0;
                }
            });
            features.addPlotter(new Metrics.Plotter("Login Security") {
                @Override
                public int getValue() {
                    if(core.configuration.getBoolean("login-enabled", false))
                        return 1;
                    return 0;
                }
            });
            features.addPlotter(new Metrics.Plotter("Player Info Protected") {
                @Override
                public int getValue() {
                    if(core.player_info_protected)
                        return 1;
                    return 0;
                }
            });
            
            Metrics.Graph maps = metrics.createGraph("Map Data");
            maps.addPlotter(new Metrics.Plotter("Worlds") {
                @Override
                public int getValue() {
                    if(core.mapManager != null)
                        return core.mapManager.getWorlds().size();
                    return 0;
                }
            });
            maps.addPlotter(new Metrics.Plotter("Maps") {
                @Override
                public int getValue() {
                    int cnt = 0;
                    if(core.mapManager != null) {
                        for(DynmapWorld w :core.mapManager.getWorlds()) {
                            cnt += w.maps.size();
                        }
                    }
                    return cnt;
                }
            });
            maps.addPlotter(new Metrics.Plotter("HD Maps") {
                @Override
                public int getValue() {
                    int cnt = 0;
                    if(core.mapManager != null) {
                        for(DynmapWorld w :core.mapManager.getWorlds()) {
                            for(MapType mt : w.maps) {
                                if(mt instanceof HDMap) {
                                    cnt++;
                                }
                            }
                        }
                    }
                    return cnt;
                }
            });
            for (String mod : modsused) {
                features.addPlotter(new Metrics.Plotter(mod + " Blocks") {
                    @Override
                    public int getValue() {
                        return 1;
                    }
                });
            }
            
            metrics.start();
        } catch (IOException e) {
            // Failed to submit the stats :-(
        }
    }
}
