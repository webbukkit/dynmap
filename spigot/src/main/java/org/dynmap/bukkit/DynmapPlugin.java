package org.dynmap.bukkit;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
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
import org.bukkit.block.Sign;
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
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWebChatEvent;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.MapType;
import org.dynmap.PlayerList;
import org.dynmap.bukkit.helper.BukkitVersionHelper;
import org.dynmap.bukkit.helper.BukkitWorld;
import org.dynmap.bukkit.helper.SnapshotCache;
import org.dynmap.bukkit.permissions.BukkitPermissions;
import org.dynmap.bukkit.permissions.NijikokunPermissions;
import org.dynmap.bukkit.permissions.OpPermissions;
import org.dynmap.bukkit.permissions.PEXPermissions;
import org.dynmap.bukkit.permissions.PermBukkitPermissions;
import org.dynmap.bukkit.permissions.GroupManagerPermissions;
import org.dynmap.bukkit.permissions.PermissionProvider;
import org.dynmap.bukkit.permissions.VaultPermissions;
import org.dynmap.bukkit.permissions.bPermPermissions;
import org.dynmap.bukkit.permissions.LuckPermsPermissions;
import org.dynmap.bukkit.permissions.LuckPerms5Permissions;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.hdmap.HDMap;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.modsupport.ModSupportImpl;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.Polygon;
import org.dynmap.utils.VisibilityLimit;
import skinsrestorer.bukkit.SkinsRestorer;

public class DynmapPlugin extends JavaPlugin implements DynmapAPI {
    private DynmapCore core;
    private PermissionProvider permissions;
    private String version;
    public PlayerList playerList;
    private MapManager mapManager;
    public static DynmapPlugin plugin;
    public PluginManager pm;
    private Metrics metrics;
    private BukkitEnableCoreCallback enabCoreCB = new BukkitEnableCoreCallback();
    private Method ismodloaded;
    private Method instance;
    private Method getindexedmodlist;
    private Method getversion;
    private HashMap<String, BukkitWorld> world_by_name = new HashMap<String, BukkitWorld>();
    private HashSet<String> modsused = new HashSet<String>();
    // TPS calculator
    private double tps;
    private long lasttick;
    private long perTickLimit;
    private long cur_tick_starttime;
    private long avgticklen = 50000000;
    
    private int chunks_in_cur_tick = 0;
    private long cur_tick;
    private long prev_tick;

    private HashMap<String, Integer> sortWeights = new HashMap<String, Integer>();
    /* Lookup cache */
    private World last_world;
    private BukkitWorld last_bworld;
    
    private BukkitVersionHelper helper;
    
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
    final void removeWorld(World w) {
        world_by_name.remove(w.getName());
        if(w == last_world) {
            last_world = null;
            last_bworld = null;
        }
    }
    
    private class BukkitEnableCoreCallback extends DynmapCore.EnableCoreCallbacks {
        @Override
        public void configurationLoaded() {
            File st = new File(core.getDataFolder(), "renderdata/spout-texture.txt");
            if(st.exists()) {
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
            instance = c.getMethod("instance");
            getindexedmodlist = c.getMethod("getIndexedModList");
            c = Class.forName("cpw.mods.fml.common.ModContainer");
            getversion = c.getMethod("getVersion");
        } catch (NoSuchMethodException nsmx) {
        } catch (ClassNotFoundException e) {
        }
    }
    
    /**
     * Server access abstraction class
     */
    public class BukkitServer extends DynmapServerInterface {
        @Override
        public int getBlockIDAt(String wname, int x, int y, int z) {
            World w = getServer().getWorld(wname);
            if((w != null) && w.isChunkLoaded(x >> 4, z >> 4)) {
                return w.getBlockTypeIdAt(x,  y,  z);
            }
            return -1;
        }
		
        @Override
        public int isSignAt(String wname, int x, int y, int z) {
            World w = getServer().getWorld(wname);
            if((w != null) && w.isChunkLoaded(x >> 4, z >> 4)) {
                Block b = w.getBlockAt(x, y, z);
                BlockState s = b.getState();

                if (s instanceof Sign) {
                    return 1;
                } else {
                    return 0;
                }
            }
            return -1;
        }

        @Override
        public void scheduleServerTask(Runnable run, long delay) {
            getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.this, run, delay);
        }
        @Override
        public DynmapPlayer[] getOnlinePlayers() {
            Player[] players = helper.getOnlinePlayers();
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
            if(DynmapPlugin.this.isEnabled())
                return getServer().getScheduler().callSyncMethod(DynmapPlugin.this, task);
            else
                return null;
        }
        private boolean noservername = false;
        @Override
        public String getServerName() {
        	try {
        		if (!noservername)
        			return getServer().getServerName();
        	} catch (NoSuchMethodError x) {	// Missing in 1.14 spigot - no idea why removed...
        		noservername = true;
        	}
    		return getServer().getMotd();
        }
        @Override
        public boolean isPlayerBanned(String pid) {
            OfflinePlayer p = getServer().getOfflinePlayer(pid);
            if((p != null) && p.isBanned())
                return true;
            return false;
        }
        @Override
        public boolean isServerThread() {
            return Bukkit.getServer().isPrimaryThread();
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
                        @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
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
                        @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                        public void onPlayerBedLeave(PlayerBedLeaveEvent evt) {
                            DynmapPlayer p = new BukkitPlayer(evt.getPlayer());
                            core.listenerManager.processPlayerEvent(EventType.PLAYER_BED_LEAVE, p);
                        }
                    }, DynmapPlugin.this);
                    break;
                case PLAYER_CHAT:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                        public void onPlayerChat(AsyncPlayerChatEvent evt) {
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
                    break;
                case BLOCK_BREAK:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                        public void onBlockBreak(BlockBreakEvent evt) {
                            Block b = evt.getBlock();
                            if(b == null) return;   /* Work around for stupid mods.... */
                            Location l = b.getLocation();
                            core.listenerManager.processBlockEvent(EventType.BLOCK_BREAK, b.getType().name(),
                                getWorld(l.getWorld()).getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
                        }
                    }, DynmapPlugin.this);
                    break;
                case SIGN_CHANGE:
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
                        public void onSignChange(SignChangeEvent evt) {
                            Block b = evt.getBlock();
                            Location l = b.getLocation();
                            String[] lines = evt.getLines();    /* Note: changes to this change event - intentional */
                            DynmapPlayer dp = null;
                            Player p = evt.getPlayer();
                            if(p != null) dp = new BukkitPlayer(p);
                            core.listenerManager.processSignChangeEvent(EventType.SIGN_CHANGE, b.getType().name(),
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
            return SnapshotCache.sscache.getHitRate();
        }
        @Override
        public void resetCacheStats() {
            SnapshotCache.sscache.resetStats();
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
                Future<Boolean> f = core.getServer().callSyncMethod(new Callable<Boolean>() {
                    public Boolean call() throws Exception {
                        boolean exhausted = true;
                        
                        if (prev_tick != cur_tick) {
                            prev_tick = cur_tick;
                            cur_tick_starttime = System.nanoTime();
                        }                            
                        if(chunks_in_cur_tick > 0) {
                            boolean done = false;
                            while (!done) {
                                int cnt = chunks_in_cur_tick;
                                if (cnt > 5) cnt = 5;
                                chunks_in_cur_tick -= cc.loadChunks(cnt);
                                exhausted = (chunks_in_cur_tick == 0) || ((System.nanoTime() - cur_tick_starttime) > perTickLimit);
                                done = exhausted || cc.isDoneLoading();
                            }
                        }
                        return exhausted;
                    }
                });
                if (f == null) {
                    return null;
                }
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
            return helper.getOnlinePlayers().length;
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
        @Override
        public String getModVersion(String name) {
            if((instance != null) && (getindexedmodlist != null) && (getversion != null)) {
                try {
                    Object inst = instance.invoke(null);
                    Map<?,?> modmap = (Map<?,?>) getindexedmodlist.invoke(inst);
                    Object mod = modmap.get(name);
                    if (mod != null) {
                        return (String) getversion.invoke(mod);
                    }
                } catch (IllegalArgumentException iax) {
                } catch (IllegalAccessException e) {
                } catch (InvocationTargetException e) {
                }
            }
            return null;
        }


        @Override
        public double getServerTPS() {
            return tps;
        }
        
        @Override
        public String getServerIP() {
            return Bukkit.getServer().getIp();
        }

        @Override
        public Map<Integer, String> getBlockIDMap() {
            String[] bsn = helper.getBlockNames();
            HashMap<Integer, String> map = new HashMap<Integer, String>();
            for (int i = 0; i < bsn.length; i++) {
                if (bsn[i] != null) {
                	if (bsn[i].indexOf(':') < 0)
                		map.put(i, "minecraft:" + bsn[i]);
                	else
                		map.put(i, bsn[i]);
                }
            }
            return map;
        }
    }
    /**
     * Player access abstraction class
     */
    public class BukkitPlayer extends BukkitCommandSender implements DynmapPlayer {
        private Player player;
        private OfflinePlayer offplayer;
        private String skinurl;
        private UUID uuid;
        
        public BukkitPlayer(Player p) {
            super(p);
            player = p;
            offplayer = p.getPlayer();
            uuid = p.getUniqueId();
            skinurl = helper.getSkinURL(p);
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
        public double getHealth() {
            if(player != null) {
            	return Math.ceil(2.0 * player.getHealth() / player.getMaxHealth() * player.getHealthScale()) / 2.0;
            }
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
        @Override
        public int getSortWeight() {
            Integer wt = sortWeights.get(getName());
            if (wt != null)
                return wt;
            return 0;
        }
        @Override
        public void setSortWeight(int wt) {
            if (wt == 0) {
                sortWeights.remove(getName());
            }
            else {
                sortWeights.put(getName(), wt);
            }
        }
        @Override
        public String getSkinURL() {
        	return skinurl;
        }
        @Override
        public UUID getUUID() {
        	return uuid;
        }
        /**
         * Send title and subtitle text (called from server thread)
         */
        @Override
        public void sendTitleText(String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTIcks) {
        	if (player != null) {
        		helper.sendTitleText(player, title, subtitle, fadeInTicks, stayTicks, fadeOutTIcks);
        	}
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
        @Override
        public boolean hasPermissionNode(String node) {
            if (sender != null) {
                return sender.hasPermission(node);
            }
            return false;
        }
    }
    
    public void loadExtraBiomes(String mcver) {
        int cnt = 0;
        
        BiomeMap.loadWellKnownByVersion(mcver);
        /* Find array of biomes in biomebase */
        Object[] biomelist = helper.getBiomeBaseList();
        //Log.info("biomelist length = " + biomelist.length);
        /* Loop through list, skipping well known biomes */
        for(int i = 0; i < biomelist.length; i++) {
            Object bb = biomelist[i];
            if(bb != null) {
                float tmp = helper.getBiomeBaseTemperature(bb);
                float hum = helper.getBiomeBaseHumidity(bb);
                int watermult = helper.getBiomeBaseWaterMult(bb);
                //Log.info("biome[" + i + "]: hum=" + hum + ", tmp=" + tmp + ", mult=" + Integer.toHexString(watermult));
                
                BiomeMap bmap = BiomeMap.byBiomeID(i);
                if (bmap.isDefault()) {
                    String id =  helper.getBiomeBaseIDString(bb);
                    if(id == null) {
                        id = "BIOME_" + i;
                    }
                    bmap = new BiomeMap(i, id, tmp, hum);
                    //Log.info("Add custom biome [" + bmap.toString() + "] (" + i + ")");
                    cnt++;
                }
                else {
                    bmap.setTemperature(tmp);
                    bmap.setRainfall(hum);
                }
                if (watermult != -1) {
                	bmap.setWaterColorMultiplier(watermult);
                	//Log.info("Set watercolormult for " + bmap.toString() + " (" + i + ") to " + Integer.toHexString(watermult));
                }
            }
        }
        if(cnt > 0) {
            Log.info("Added " + cnt + " custom biome mappings");
        }
    }
    
    @Override
    public void onLoad() {
        Log.setLogger(this.getLogger(), "");
        
        helper = Helper.getHelper();
        pm = this.getServer().getPluginManager();
        
        ModSupportImpl.init();
    }
    
    @Override
    public void onEnable() {
        if(core != null){
            if(core.getMarkerAPI() != null){
                getLogger().info("Starting Scheduled Write Job (markerAPI).");
                core.restartMarkerSaveJob();
            }
        }
        if (helper == null) {
            Log.info("Dynmap is disabled (unsupported platform)");
            return;
        }
        PluginDescriptionFile pdfFile = this.getDescription();
        version = pdfFile.getVersion();

        /* Get MC version */
        String bukkitver = getServer().getVersion();
        String mcver = "1.0.0";
        int idx = bukkitver.indexOf("(MC: ");
        if(idx > 0) {
            mcver = bukkitver.substring(idx+5);
            idx = mcver.indexOf(")");
            if(idx > 0) mcver = mcver.substring(0, idx);
        }

        // Initialize block states
        helper.initializeBlockStates();
        
        /* Load extra biomes, if any */
        loadExtraBiomes(mcver);
             
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
            permissions = GroupManagerPermissions.create(getServer(), "dynmap");
        if (permissions == null)
            permissions = LuckPermsPermissions.create(getServer(), "dynmap");
        if (permissions == null)
            permissions = LuckPerms5Permissions.create(getServer(), "dynmap");
        if (permissions == null)
            permissions = VaultPermissions.create(this, "dynmap");
        if (permissions == null)
            permissions = BukkitPermissions.create("dynmap", perdefs);
        if (permissions == null)
            permissions = new OpPermissions(new String[] { "fullrender", "cancelrender", "radiusrender", "resetstats", "reload", "purgequeue", "pause", "ips-for-id", "ids-for-ip", "add-id-for-ip", "del-id-for-ip" });
        /* Get and initialize data folder */
        File dataDirectory = this.getDataFolder();
        if(dataDirectory.exists() == false)
            dataDirectory.mkdirs();
         
        /* Instantiate core */
        if(core == null)
            core = new DynmapCore();
        /* Inject dependencies */
        core.setPluginJarFile(this.getFile());
        core.setPluginVersion(version, "CraftBukkit");
        core.setMinecraftVersion(mcver);
        core.setDataFolder(dataDirectory);
        core.setServer(new BukkitServer());
        core.setBiomeNames(helper.getBiomeNames());
        
        /* Load configuration */
        if(!core.initConfiguration(enabCoreCB)) {
            this.setEnabled(false);
            return;
        }

        /* Skins support via SkinsRestorer */
        SkinsRestorerSkinUrlProvider skinUrlProvider = null;

        if (core.configuration.getBoolean("skinsrestorer-integration", false)) {
            SkinsRestorer skinsRestorer = (SkinsRestorer) getServer().getPluginManager().getPlugin("SkinsRestorer");

            if (skinsRestorer == null) {
                Log.warning("SkinsRestorer integration can't be enabled because SkinsRestorer not installed");
            } else {
                skinUrlProvider = new SkinsRestorerSkinUrlProvider(skinsRestorer);
                Log.info("SkinsRestorer integration enabled");
            }
        }

        core.setSkinUrlProvider(skinUrlProvider);

        /* See if we need to wait before enabling core */
        if(!readyToEnable()) {
            Listener pl = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onPluginEnabled(PluginEnableEvent evt) {
                    if (!readyToEnable()) {
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
        // Start tps calculation
        lasttick = System.nanoTime();
        tps = 20.0;
        perTickLimit = core.getMaxTickUseMS() * 1000000;

        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                processTick();
            }
        }, 1, 1);
    }
    
    private boolean readyToEnable() {
        return true;
    }
    
    private void doEnable() {
        /* Enable core */
        if(!core.enableCore(enabCoreCB)) {
            this.setEnabled(false);
            return;
        }
        playerList = core.playerList;
        SnapshotCache.sscache = new SnapshotCache(core.getSnapShotCacheSize(), core.useSoftRefInSnapShotCache());

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
        
        /* Core is ready - notify API availability */
        DynmapCommonAPIListener.apiInitialized(this);

        Log.info("Enabled");
    }
    
    @Override
    public void onDisable() {
        /* Core is being disabled - notify API disable */
        DynmapCommonAPIListener.apiTerminated();

        if (metrics != null) {
            metrics = null;
        }
        
        /* Disable core */
        core.disableCore();

        if(SnapshotCache.sscache != null) {
        	SnapshotCache.sscache.cleanup();
        	SnapshotCache.sscache = null; 
        }
        Log.info("Disabled");
    }
    
    private void processTick() {
        long now = System.nanoTime();
        long elapsed = now - lasttick;
        lasttick = now;
        avgticklen = ((avgticklen * 99) / 100) + (elapsed / 100);
        tps = (double)1E9 / (double)avgticklen;
        if (mapManager != null) {
            chunks_in_cur_tick = mapManager.getMaxChunkLoadsPerTick();
        }
        cur_tick++;
        
        // Tick core
        if (core != null) {
            core.serverTick(tps);
        }
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
        if (core != null)
        	return core.processCommand(dsender, cmd.getName(), commandLabel, args);
        else
        	return false;
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
    	SnapshotCache.sscache.invalidateSnapshot(wid, minx, miny, minz, maxx, maxy, maxz);
        return core.triggerRenderOfVolume(wid, minx, miny, minz, maxx, maxy, maxz);
    }

    @Override
    public final int triggerRenderOfBlock(String wid, int x, int y, int z) {
    	SnapshotCache.sscache.invalidateSnapshot(wid, x, y, z);
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
            @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
            public void onPlayerJoin(PlayerJoinEvent evt) {
                final DynmapPlayer dp = new BukkitPlayer(evt.getPlayer());
                // Give other handlers a change to prep player (nicknames and such from Essentials)
                getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.this, new Runnable() {
                    @Override
                    public void run() {
                        core.listenerManager.processPlayerEvent(EventType.PLAYER_JOIN, dp);
                    }
                }, 2);
            }
            @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
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
                    SnapshotCache.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
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
        onblockredstone = core.isTrigger("blockredstone");
        
        if(onplace) {
            Listener placelistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockPlace(BlockPlaceEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    SnapshotCache.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockplace");
                }
            };
            pm.registerEvents(placelistener, this);
        }
        
        if(onbreak) {
            Listener breaklistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockBreak(BlockBreakEvent event) {
                    Block b = event.getBlock();
                    if(b == null) return;   /* Stupid mod workaround */
                    Location loc = b.getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    SnapshotCache.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockbreak");
                }
            };
            pm.registerEvents(breaklistener, this);
        }
        
        if(onleaves) {
            Listener leaveslistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onLeavesDecay(LeavesDecayEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    SnapshotCache.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    if(onleaves) {
                        mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "leavesdecay");
                    }
                }
            };
            pm.registerEvents(leaveslistener, this);
        }

        if(onburn) {
            Listener burnlistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockBurn(BlockBurnEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    SnapshotCache.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    if(onburn) {
                        mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockburn");
                    }
                }
            };
            pm.registerEvents(burnlistener, this);
        }
        
        if(onblockphysics) {
            Listener physlistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockPhysics(BlockPhysicsEvent event) {
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
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockFromTo(BlockFromToEvent event) {
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
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockPistonRetract(BlockPistonRetractEvent event) {
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
                    SnapshotCache.sscache.invalidateSnapshot(wn, x, y, z);
                    if(onpiston)
                        mapManager.touch(wn, x, y, z, "pistonretract");
                    for(int i = 0; i < 2; i++) {
                        x += dir.getModX();
                        y += dir.getModY();
                        z += dir.getModZ();
                        SnapshotCache.sscache.invalidateSnapshot(wn, x, y, z);
                        mapManager.touch(wn, x, y, z, "pistonretract");
                    }
                }

                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockPistonExtend(BlockPistonExtendEvent event) {
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
                    SnapshotCache.sscache.invalidateSnapshot(wn, x, y, z);
                    if(onpiston)
                        mapManager.touch(wn, x, y, z, "pistonretract");
                    for(int i = 0; i < 1+event.getLength(); i++) {
                        x += dir.getModX();
                        y += dir.getModY();
                        z += dir.getModZ();
                        SnapshotCache.sscache.invalidateSnapshot(wn, x, y, z);
                        mapManager.touch(wn, x, y, z, "pistonretract");
                    }
                }
            };
            pm.registerEvents(pistonlistener, this);
        }
        
        if(onblockspread) {
            Listener spreadlistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockSpread(BlockSpreadEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    SnapshotCache.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockspread");
                }
            };
            pm.registerEvents(spreadlistener, this);
        }
        
        if(onblockform) {
            Listener formlistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockForm(BlockFormEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    SnapshotCache.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockform");
                }
            };
            pm.registerEvents(formlistener, this);
        }
        
        if(onblockfade) {
            Listener fadelistener = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockFade(BlockFadeEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    SnapshotCache.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockfade");
                }
            };
            pm.registerEvents(fadelistener, this);
        }
        
        onblockgrow = core.isTrigger("blockgrow");
        
        if(onblockgrow) {
            Listener growTrigger = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockGrow(BlockGrowEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    SnapshotCache.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockgrow");
                }
            };
            pm.registerEvents(growTrigger, this);
        }
        
        if(onblockredstone) {
            Listener redstoneTrigger = new Listener() {
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
                public void onBlockRedstone(BlockRedstoneEvent event) {
                    Location loc = event.getBlock().getLocation();
                    String wn = getWorld(loc.getWorld()).getName();
                    SnapshotCache.sscache.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockredstone");
                }
            };
            pm.registerEvents(redstoneTrigger,  this);
        }
        
        /* Register player event trigger handlers */
        Listener playerTrigger = new Listener() {
            @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
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
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
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
            @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
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
                SnapshotCache.sscache.invalidateSnapshot(wname, minx, miny, minz, maxx, maxy, maxz);
                if(onexplosion) {
                    mapManager.touchVolume(wname, minx, miny, minz, maxx, maxy, maxz, "entityexplode");
                }
            }
        };
        onexplosion = core.isTrigger("explosion");
        pm.registerEvents(entityTrigger, this);
        
        /* Register world event triggers */
        Listener worldTrigger = new Listener() {
            @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
            public void onWorldLoad(WorldLoadEvent event) {
                BukkitWorld w = getWorld(event.getWorld());
                if(core.processWorldLoad(w))    /* Have core process load first - fire event listeners if good load after */
                    core.listenerManager.processWorldEvent(EventType.WORLD_LOAD, w);
            }
            @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
            public void onWorldUnload(WorldUnloadEvent event) {
                BukkitWorld w = getWorld(event.getWorld());
                if(w != null) {
                    core.listenerManager.processWorldEvent(EventType.WORLD_UNLOAD, w);
                    w.setWorldUnloaded();
                    core.processWorldUnload(w);
                }
            }
            @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
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
                SnapshotCache.sscache.invalidateSnapshot(wname, minx, miny, minz, maxx, maxy, maxz);
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
                @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
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
        metrics = new Metrics(this);

        metrics.addCustomChart(new Metrics.MultiLineChart("features_used", () -> {
            Map<String, Integer> hashMap = new HashMap<>();
            hashMap.put("internal_web_server", core.configuration.getBoolean("disable-webserver", false) ? 0 : 1);
            hashMap.put("login_security", core.configuration.getBoolean("login-enabled", false) ? 1 : 0);
            hashMap.put("player_info_protected", core.player_info_protected ? 1 : 0);
            for (String mod : modsused)
                hashMap.put(mod + "_blocks", 1);
            return hashMap;
        }));

        metrics.addCustomChart(new Metrics.MultiLineChart("map_data", () -> {
            Map<String, Integer> hashMap = new HashMap<>();
            hashMap.put("worlds", core.mapManager != null ? core.mapManager.getWorlds().size() : 0);
            int maps = 0, hdmaps = 0;
            if (core.mapManager != null)
                for (DynmapWorld w : core.mapManager.getWorlds()) {
                    for (MapType mt : w.maps)
                        if (mt instanceof HDMap)
                            ++hdmaps;
                    maps += w.maps.size();
                }
            hashMap.put("maps", maps);
            hashMap.put("hd_maps", hdmaps);
            return hashMap;
        }));
    }
    @Override
    public void processSignChange(String material, String world, int x, int y, int z,
            String[] lines, String playerid) {
        core.processSignChange(material, world, x, y, z, lines, playerid);
    }
    
    Polygon getWorldBorder(World w) {
        return helper.getWorldBorder(w);
    }
    
    public static boolean migrateChunks() {
        if ((plugin != null) && (plugin.core != null)) {
            return plugin.core.migrateChunks();
        }
        return false;
    }
}
