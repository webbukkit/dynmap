package org.dynmap.bukkit;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWebChatEvent;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.PlayerList;
import org.dynmap.bukkit.permissions.BukkitPermissions;
import org.dynmap.bukkit.permissions.NijikokunPermissions;
import org.dynmap.bukkit.permissions.OpPermissions;
import org.dynmap.bukkit.permissions.PermissionProvider;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.markers.MarkerAPI;

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

    public DynmapPlugin() {
        plugin = this;
    }
    
    /**
     * Server access abstraction class
     */
    public class BukkitServer implements DynmapServerInterface {
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
                            DynmapWorld w = new BukkitWorld(evt.getWorld());
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
                    break;
                case BLOCK_BREAK:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR)
                        public void onBlockBreak(BlockBreakEvent evt) {
                            Block b = evt.getBlock();
                            Location l = b.getLocation();
                            core.listenerManager.processBlockEvent(EventType.BLOCK_BREAK, b.getType().getId(),
                                    l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
                        }
                    }, DynmapPlugin.this);
                    break;
                case SIGN_CHANGE:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR)
                        public void onSignChange(SignChangeEvent evt) {
                            Block b = evt.getBlock();
                            Location l = b.getLocation();
                            String[] lines = evt.getLines();    /* Note: changes to this change event - intentional */
                            DynmapPlayer dp = null;
                            Player p = evt.getPlayer();
                            if(p != null) dp = new BukkitPlayer(p);
                            core.listenerManager.processSignChangeEvent(EventType.SIGN_CHANGE, b.getType().getId(),
                                    l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), lines, dp);
                        }
                    }, DynmapPlugin.this);
                    break;
                default:
                    Log.severe("Unhandled event type: " + type);
                    return false;
            }
            return true;
        }
        @Override
        public boolean sendWebChatEvent(String source, String name, String msg) {
            DynmapWebChatEvent evt = new DynmapWebChatEvent(source, name, msg);
            getServer().getPluginManager().callEvent(evt);
            return (evt.isCancelled() == false);
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
            World w = getServer().getWorld(wname);  /* FInd world */
            if(w != null) {
                return new BukkitWorld(w);
            }
            return null;
        }
        @Override
        public DynmapPlayer getOfflinePlayer(String name) {
            OfflinePlayer op = getServer().getOfflinePlayer(name);
            if(op != null) {
                return new BukkitPlayer(op);
            }
            return null;
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
            Location loc = player.getLocation();
            return toLoc(loc);
        }
        @Override
        public String getWorld() {
            if(player == null) {
                return null;
            }
            World w = player.getWorld();
            if(w != null)
                return w.getName();
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
    
    @Override
    public void onEnable() {
        pm = this.getServer().getPluginManager();
        
        PluginDescriptionFile pdfFile = this.getDescription();
        version = pdfFile.getVersion();

             
        /* Set up player login/quit event handler */
        registerPlayerLoginListener();

        permissions = NijikokunPermissions.create(getServer(), "dynmap");
        if (permissions == null)
            permissions = BukkitPermissions.create("dynmap");
        if (permissions == null)
            permissions = new OpPermissions(new String[] { "fullrender", "cancelrender", "radiusrender", "resetstats", "reload", "purgequeue", "pause", "ips-for-id", "ids-for-ip", "add-id-for-ip", "del-id-for-ip" });
        /* Get and initialize data folder */
        File dataDirectory = this.getDataFolder();
        if(dataDirectory.exists() == false)
            dataDirectory.mkdirs();
 
        /* Check for Spout */
        if(detectSpout()) {
            has_spout = true;
            Log.info("Detected Spout");
            spb = new SpoutPluginBlocks();
            spb.processSpoutBlocks(dataDirectory);
        }

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
        core.setPluginVersion(version);
        core.setMinecraftVersion(mcver);
        core.setDataFolder(dataDirectory);
        core.setServer(new BukkitServer());
        
        /* Enable core */
        if(!core.enableCore()) {
            this.setEnabled(false);
            return;
        }
        playerList = core.playerList;
        sscache = new SnapshotCache(core.getSnapShotCacheSize());

        /* Get map manager from core */
        mapManager = core.getMapManager();
        /* Initialized the currently loaded worlds */
        for (World world : getServer().getWorlds()) {
            BukkitWorld w = new BukkitWorld(world);
            if(core.processWorldLoad(w))    /* Have core process load first - fire event listeners if good load after */
                core.listenerManager.processWorldEvent(EventType.WORLD_LOAD, w);
        }
    
        /* Register our update trigger events */
        registerEvents();

        Log.info("Enabled");
    }
    
    @Override
    public void onDisable() {
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
        
        return core.triggerRenderOfVolume(l0.getWorld().getName(), Math.min(x0, x1), Math.min(y0, y1),
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
        return new DynmapLocation(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }
    
    private void registerPlayerLoginListener() {
        Listener pl = new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent evt) {
                DynmapPlayer dp = new BukkitPlayer(evt.getPlayer());                
                core.listenerManager.processPlayerEvent(EventType.PLAYER_JOIN, dp);
            }
            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent evt) {
                DynmapPlayer dp = new BukkitPlayer(evt.getPlayer());
                core.listenerManager.processPlayerEvent(EventType.PLAYER_QUIT, dp);
            }
        };
        pm.registerEvents(pl, this);
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

    private void registerEvents() {
        Listener blockTrigger = new Listener() {
            @EventHandler(priority=EventPriority.MONITOR)
            public void onBlockPlace(BlockPlaceEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onplace) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockplace");
                }
            }

            @EventHandler(priority=EventPriority.MONITOR)
            public void onBlockBreak(BlockBreakEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onbreak) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockbreak");
                }
            }

            @EventHandler(priority=EventPriority.MONITOR)
            public void onLeavesDecay(LeavesDecayEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onleaves) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "leavesdecay");
                }
            }
            
            @EventHandler(priority=EventPriority.MONITOR)
            public void onBlockBurn(BlockBurnEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onburn) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockburn");
                }
            }
            
            @EventHandler(priority=EventPriority.MONITOR)
            public void onBlockForm(BlockFormEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onblockform) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockform");
                }
            }

            @EventHandler(priority=EventPriority.MONITOR)
            public void onBlockFade(BlockFadeEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onblockfade) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockfade");
                }
            }
            
            @EventHandler(priority=EventPriority.MONITOR)
            public void onBlockSpread(BlockSpreadEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onblockspread) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockspread");
                }
            }

            @EventHandler(priority=EventPriority.MONITOR)
            public void onBlockFromTo(BlockFromToEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getToBlock().getLocation();
                String wn = loc.getWorld().getName();
                sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onblockfromto)
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockfromto");
                loc = event.getBlock().getLocation();
                wn = loc.getWorld().getName();
                sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onblockfromto)
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockfromto");
            }
            
            @EventHandler(priority=EventPriority.MONITOR)
            public void onBlockPhysics(BlockPhysicsEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onblockphysics) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockphysics");
                }
            }

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
                String wn = loc.getWorld().getName();
                int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
                sscache.invalidateSnapshot(wn, x, y, z);
                if(onpiston)
                    mapManager.touch(wn, x, y, z, "pistonretract");
                for(int i = 0; i < 2; i++) {
                    x += dir.getModX();
                    y += dir.getModY();
                    z += dir.getModZ();
                    sscache.invalidateSnapshot(wn, x, y, z);
                    if(onpiston)
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
                String wn = loc.getWorld().getName();
                int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
                sscache.invalidateSnapshot(wn, x, y, z);
                if(onpiston)
                    mapManager.touch(wn, x, y, z, "pistonretract");
                for(int i = 0; i < 1+event.getLength(); i++) {
                    x += dir.getModX();
                    y += dir.getModY();
                    z += dir.getModZ();
                    sscache.invalidateSnapshot(wn, x, y, z);
                    if(onpiston)
                        mapManager.touch(wn, x, y, z, "pistonretract");
                }
            }
        };
        
        // To trigger rendering.
        onplace = core.isTrigger("blockplaced");
        onbreak = core.isTrigger("blockbreak");
        if(core.isTrigger("snowform")) Log.info("The 'snowform' trigger has been deprecated due to Bukkit changes - use 'blockformed'");
        onleaves = core.isTrigger("leavesdecay");
        onburn = core.isTrigger("blockburn");
        onblockform = core.isTrigger("blockformed");
        onblockfade = core.isTrigger("blockfaded");
        onblockspread = core.isTrigger("blockspread");
        onblockfromto = core.isTrigger("blockfromto");
        onblockphysics = core.isTrigger("blockphysics");
        onpiston = core.isTrigger("pistonmoved");
        
        pm.registerEvents(blockTrigger, this);
        
        /* Register player event trigger handlers */
        Listener playerTrigger = new Listener() {
            @EventHandler(priority=EventPriority.MONITOR)
            public void onPlayerJoin(PlayerJoinEvent event) {
                if(onplayerjoin) {
                    Location loc = event.getPlayer().getLocation();
                    mapManager.touch(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "playerjoin");
                }
                core.listenerManager.processPlayerEvent(EventType.PLAYER_JOIN, new BukkitPlayer(event.getPlayer()));
            }
            @EventHandler(priority=EventPriority.MONITOR)
            public void onPlayerQuit(PlayerQuitEvent event) {
                core.listenerManager.processPlayerEvent(EventType.PLAYER_QUIT, new BukkitPlayer(event.getPlayer()));
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
                    mapManager.touch(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "playermove");
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
                String wname = loc.getWorld().getName();
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
                core.updateConfigHashcode();
                BukkitWorld w = new BukkitWorld(event.getWorld());
                if(core.processWorldLoad(w))    /* Have core process load first - fire event listeners if good load after */
                    core.listenerManager.processWorldEvent(EventType.WORLD_LOAD, w);
            }
            @EventHandler(priority=EventPriority.MONITOR)
            public void onWorldUnload(WorldUnloadEvent event) {
                core.updateConfigHashcode();
                DynmapWorld w = core.getWorld(event.getWorld().getName());
                if(w != null)
                    core.listenerManager.processWorldEvent(EventType.WORLD_UNLOAD, w);
            }
        };
        // To link configuration to real loaded worlds.
        pm.registerEvents(worldTrigger, this);

        ongeneratechunk = core.isTrigger("chunkgenerated");
        if(ongeneratechunk) {
            Listener chunkTrigger = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR)
                public void onChunkPopulate(ChunkPopulateEvent event) {
                    Chunk c = event.getChunk();
                    /* Touch extreme corners */
                    int x = c.getX() << 4;
                    int z = c.getZ() << 4;
                    mapManager.touchVolume(event.getWorld().getName(), x, 0, z, x+15, 128, z+16, "chunkpopulate");
                }
            };
            pm.registerEvents(chunkTrigger, this);
        }
    }

    private boolean detectSpout() {
        Plugin p = this.getServer().getPluginManager().getPlugin("Spout");
        return (p != null);
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
}
