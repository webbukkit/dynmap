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
import org.bukkit.event.Event;
import org.bukkit.event.Event.Type;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.WorldListener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
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
    public BukkitEventProcessor bep;

    private MapManager mapManager;
    public static DynmapPlugin plugin;

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
                    bep.registerEvent(Type.SPAWN_CHANGE, new WorldListener() {
                        @Override
                        public void onSpawnChange(SpawnChangeEvent evt) {
                            DynmapWorld w = new BukkitWorld(evt.getWorld());
                            core.listenerManager.processWorldEvent(EventType.WORLD_SPAWN_CHANGE, w);
                        }
                    });
                    break;
                case PLAYER_JOIN:
                case PLAYER_QUIT:
                    /* Already handled */
                    break;
                case PLAYER_BED_LEAVE:
                    bep.registerEvent(Type.PLAYER_BED_LEAVE, new PlayerListener() {
                        @Override
                        public void onPlayerBedLeave(PlayerBedLeaveEvent evt) {
                            DynmapPlayer p = new BukkitPlayer(evt.getPlayer());
                            core.listenerManager.processPlayerEvent(EventType.PLAYER_BED_LEAVE, p);
                        }
                    });
                    break;
                case PLAYER_CHAT:
                    bep.registerEvent(Type.PLAYER_CHAT, new PlayerListener() {
                        @Override
                        public void onPlayerChat(PlayerChatEvent evt) {
                            DynmapPlayer p = null;
                            if(evt.getPlayer() != null)
                                p = new BukkitPlayer(evt.getPlayer());
                            core.listenerManager.processChatEvent(EventType.PLAYER_CHAT, p, evt.getMessage());
                        }
                    });
                    break;
                case BLOCK_BREAK:
                    bep.registerEvent(Type.BLOCK_BREAK, new BlockListener() {
                        @Override
                        public void onBlockBreak(BlockBreakEvent evt) {
                            Block b = evt.getBlock();
                            Location l = b.getLocation();
                            core.listenerManager.processBlockEvent(EventType.BLOCK_BREAK, b.getType().getId(),
                                    l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
                        }
                    });
                    break;
                case SIGN_CHANGE:
                    bep.registerEvent(Type.SIGN_CHANGE, new BlockListener() {
                        @Override
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
                    });
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

    }
    /**
     * Player access abstraction class
     */
    public class BukkitPlayer extends BukkitCommandSender implements DynmapPlayer {
        private Player player;
        
        public BukkitPlayer(Player p) {
            super(p);
            player = p;
        }
        @Override
        public boolean isConnected() {
            return player.isOnline();
        }
        @Override
        public String getName() {
            return player.getName();
        }
        @Override
        public String getDisplayName() {
            return player.getDisplayName();
        }
        @Override
        public boolean isOnline() {
            return player.isOnline();
        }
        @Override
        public DynmapLocation getLocation() {
            Location loc = player.getLocation();
            return toLoc(loc);
        }
        @Override
        public String getWorld() {
            World w = player.getWorld();
            if(w != null)
                return w.getName();
            return null;
        }
        @Override
        public InetSocketAddress getAddress() {
            return player.getAddress();
        }
        @Override
        public boolean isSneaking() {
            return player.isSneaking();
        }
        @Override
        public int getHealth() {
            return player.getHealth();
        }
        @Override
        public int getArmorPoints() {
            return Armor.getArmorPoints(player);
        }
        @Override
        public DynmapLocation getBedSpawnLocation() {
            Location loc = player.getBedSpawnLocation();
            if(loc != null) {
                return toLoc(loc);
            }
            return null;
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
            return permissions.has(sender, privid);
        }

        @Override
        public void sendMessage(String msg) {
            sender.sendMessage(msg);
        }

        @Override
        public boolean isConnected() {
            return true;
        }
        @Override
        public boolean isOp() {
            return sender.isOp();
        }
    }
    
    @Override
    public void onEnable() {
        PluginDescriptionFile pdfFile = this.getDescription();
        version = pdfFile.getVersion();

        /* Initialize event processor */
        if(bep == null)
            bep = new BukkitEventProcessor(this);
 
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
    }
    
    @Override
    public void onDisable() {
        /* Reset registered listeners */
        bep.cleanup();
        /* Disable core */
        core.disableCore();
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
        PlayerListener pl = new PlayerListener() {
            public void onPlayerJoin(PlayerJoinEvent evt) {
                DynmapPlayer dp = new BukkitPlayer(evt.getPlayer());                
                core.listenerManager.processPlayerEvent(EventType.PLAYER_JOIN, dp);
            }
            public void onPlayerQuit(PlayerQuitEvent evt) {
                DynmapPlayer dp = new BukkitPlayer(evt.getPlayer());
                core.listenerManager.processPlayerEvent(EventType.PLAYER_QUIT, dp);
            }
        };
        bep.registerEvent(Type.PLAYER_JOIN, pl);
        bep.registerEvent(Type.PLAYER_QUIT, pl);
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
    private boolean onloadchunk;
    private boolean onexplosion;

    private void registerEvents() {
        BlockListener blockTrigger = new BlockListener() {
            @Override
            public void onBlockPlace(BlockPlaceEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                mapManager.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onplace) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockplace");
                }
            }

            @Override
            public void onBlockBreak(BlockBreakEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                mapManager.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onbreak) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockbreak");
                }
            }

            @Override
            public void onLeavesDecay(LeavesDecayEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                mapManager.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onleaves) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "leavesdecay");
                }
            }
            
            @Override
            public void onBlockBurn(BlockBurnEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                mapManager.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onburn) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockburn");
                }
            }
            
            @Override
            public void onBlockForm(BlockFormEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                mapManager.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onblockform) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockform");
                }
            }

            @Override
            public void onBlockFade(BlockFadeEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                mapManager.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onblockfade) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockfade");
                }
            }
            
            @Override
            public void onBlockSpread(BlockSpreadEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                mapManager.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onblockspread) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockspread");
                }
            }

            @Override
            public void onBlockFromTo(BlockFromToEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getToBlock().getLocation();
                String wn = loc.getWorld().getName();
                mapManager.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onblockfromto)
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockfromto");
                loc = event.getBlock().getLocation();
                wn = loc.getWorld().getName();
                mapManager.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onblockfromto)
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockfromto");
            }
            
            @Override
            public void onBlockPhysics(BlockPhysicsEvent event) {
                if(event.isCancelled())
                    return;
                Location loc = event.getBlock().getLocation();
                String wn = loc.getWorld().getName();
                mapManager.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if(onblockphysics) {
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockphysics");
                }
            }

            @Override
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
                mapManager.sscache.invalidateSnapshot(wn, x, y, z);
                if(onpiston)
                    mapManager.touch(wn, x, y, z, "pistonretract");
                for(int i = 0; i < 2; i++) {
                    x += dir.getModX();
                    y += dir.getModY();
                    z += dir.getModZ();
                    mapManager.sscache.invalidateSnapshot(wn, x, y, z);
                    if(onpiston)
                        mapManager.touch(wn, x, y, z, "pistonretract");
                }
            }
            @Override
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
                mapManager.sscache.invalidateSnapshot(wn, x, y, z);
                if(onpiston)
                    mapManager.touch(wn, x, y, z, "pistonretract");
                for(int i = 0; i < 1+event.getLength(); i++) {
                    x += dir.getModX();
                    y += dir.getModY();
                    z += dir.getModZ();
                    mapManager.sscache.invalidateSnapshot(wn, x, y, z);
                    if(onpiston)
                        mapManager.touch(wn, x, y, z, "pistonretract");
                }
            }
        };
        
        // To trigger rendering.
        onplace = core.isTrigger("blockplaced");
        bep.registerEvent(Event.Type.BLOCK_PLACE, blockTrigger);
            
        onbreak = core.isTrigger("blockbreak");
        bep.registerEvent(Event.Type.BLOCK_BREAK, blockTrigger);
            
        if(core.isTrigger("snowform")) Log.info("The 'snowform' trigger has been deprecated due to Bukkit changes - use 'blockformed'");
            
        onleaves = core.isTrigger("leavesdecay");
        bep.registerEvent(Event.Type.LEAVES_DECAY, blockTrigger);
            
        onburn = core.isTrigger("blockburn");
        bep.registerEvent(Event.Type.BLOCK_BURN, blockTrigger);

        onblockform = core.isTrigger("blockformed");
        bep.registerEvent(Event.Type.BLOCK_FORM, blockTrigger);
            
        onblockfade = core.isTrigger("blockfaded");
        bep.registerEvent(Event.Type.BLOCK_FADE, blockTrigger);
            
        onblockspread = core.isTrigger("blockspread");
        bep.registerEvent(Event.Type.BLOCK_SPREAD, blockTrigger);

        onblockfromto = core.isTrigger("blockfromto");
        bep.registerEvent(Event.Type.BLOCK_FROMTO, blockTrigger);

        onblockphysics = core.isTrigger("blockphysics");
        bep.registerEvent(Event.Type.BLOCK_PHYSICS, blockTrigger);

        onpiston = core.isTrigger("pistonmoved");
        bep.registerEvent(Event.Type.BLOCK_PISTON_EXTEND, blockTrigger);
        bep.registerEvent(Event.Type.BLOCK_PISTON_RETRACT, blockTrigger);
        /* Register player event trigger handlers */
        PlayerListener playerTrigger = new PlayerListener() {
            @Override
            public void onPlayerJoin(PlayerJoinEvent event) {
                if(onplayerjoin) {
                    Location loc = event.getPlayer().getLocation();
                    mapManager.touch(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "playerjoin");
                }
                core.listenerManager.processPlayerEvent(EventType.PLAYER_JOIN, new BukkitPlayer(event.getPlayer()));
            }
            @Override
            public void onPlayerQuit(PlayerQuitEvent event) {
                core.listenerManager.processPlayerEvent(EventType.PLAYER_QUIT, new BukkitPlayer(event.getPlayer()));
            }

            @Override
            public void onPlayerMove(PlayerMoveEvent event) {
                Location loc = event.getPlayer().getLocation();
                mapManager.touch(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "playermove");
            }
        };

        onplayerjoin = core.isTrigger("playerjoin");
        onplayermove = core.isTrigger("playermove");
        bep.registerEvent(Event.Type.PLAYER_JOIN, playerTrigger);
        bep.registerEvent(Event.Type.PLAYER_QUIT, playerTrigger);
        if(onplayermove)
            bep.registerEvent(Event.Type.PLAYER_MOVE, playerTrigger);

        /* Register entity event triggers */
        EntityListener entityTrigger = new EntityListener() {
            @Override
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
                mapManager.sscache.invalidateSnapshot(wname, minx, miny, minz, maxx, maxy, maxz);
                if(onexplosion) {
                    mapManager.touchVolume(wname, minx, miny, minz, maxx, maxy, maxz, "entityexplode");
                }
            }
        };
        onexplosion = core.isTrigger("explosion");
        bep.registerEvent(Event.Type.ENTITY_EXPLODE, entityTrigger);
        
        
        /* Register world event triggers */
        WorldListener worldTrigger = new WorldListener() {
            @Override
            public void onChunkLoad(ChunkLoadEvent event) {
                if(DynmapCore.ignore_chunk_loads)
                    return;
                Chunk c = event.getChunk();
                /* Touch extreme corners */
                int x = c.getX() << 4;
                int z = c.getZ() << 4;
                mapManager.touchVolume(event.getWorld().getName(), x, 0, z, x+15, 128, z+16, "chunkload");
            }
            @Override
            public void onChunkPopulate(ChunkPopulateEvent event) {
                Chunk c = event.getChunk();
                /* Touch extreme corners */
                int x = c.getX() << 4;
                int z = c.getZ() << 4;
                mapManager.touchVolume(event.getWorld().getName(), x, 0, z, x+15, 128, z+16, "chunkpopulate");
            }
            @Override
            public void onWorldLoad(WorldLoadEvent event) {
                core.updateConfigHashcode();
                BukkitWorld w = new BukkitWorld(event.getWorld());
                if(core.processWorldLoad(w))    /* Have core process load first - fire event listeners if good load after */
                    core.listenerManager.processWorldEvent(EventType.WORLD_LOAD, w);
            }
            @Override
            public void onWorldUnload(WorldUnloadEvent event) {
                core.updateConfigHashcode();
                DynmapWorld w = core.getWorld(event.getWorld().getName());
                if(w != null)
                    core.listenerManager.processWorldEvent(EventType.WORLD_UNLOAD, w);
            }
        };

        ongeneratechunk = core.isTrigger("chunkgenerated");
        if(ongeneratechunk) {
            bep.registerEvent(Event.Type.CHUNK_POPULATED, worldTrigger);
        }
        onloadchunk = core.isTrigger("chunkloaded");
        if(onloadchunk) { 
            bep.registerEvent(Event.Type.CHUNK_LOAD, worldTrigger);
        }

        // To link configuration to real loaded worlds.
        bep.registerEvent(Event.Type.WORLD_LOAD, worldTrigger);
        bep.registerEvent(Event.Type.WORLD_UNLOAD, worldTrigger);
    }

}
