package org.dynmap.fabric_1_16_4;

import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.network.MessageType;
import net.minecraft.server.BannedIpList;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.UserCache;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.DynmapListenerManager;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.fabric_1_16_4.event.ServerChatEvents;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.VisibilityLimit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Server access abstraction class
 */
public class FabricServer extends DynmapServerInterface {
    /* Server thread scheduler */
    private final Object schedlock = new Object();
    private final DynmapPlugin plugin;
    private final MinecraftServer server;
    private final Registry<Biome> biomeRegistry;
    private long cur_tick;
    private long next_id;
    private long cur_tick_starttime;
    private PriorityQueue<TaskRecord> runqueue = new PriorityQueue<TaskRecord>();

    public FabricServer(DynmapPlugin plugin, MinecraftServer server) {
        this.plugin = plugin;
        this.server = server;
        this.biomeRegistry = server.getRegistryManager().get(Registry.BIOME_KEY);
    }

    private GameProfile getProfileByName(String player) {
        UserCache cache = server.getUserCache();
        return cache.findByName(player);
    }

    public final Registry<Biome> getBiomeRegistry() {
        return biomeRegistry;
    }

    private Biome[] biomelist = null;

    public final Biome[] getBiomeList(Registry<Biome> biomeRegistry) {
        if (biomelist == null) {
            biomelist = new Biome[256];
            Iterator<Biome> iter = biomeRegistry.iterator();
            while (iter.hasNext()) {
                Biome b = iter.next();
                int bidx = biomeRegistry.getRawId(b);
                if (bidx >= biomelist.length) {
                    biomelist = Arrays.copyOf(biomelist, bidx + biomelist.length);
                }
                biomelist[bidx] = b;
            }
        }
        return biomelist;
    }

    @Override
    public int getBlockIDAt(String wname, int x, int y, int z) {
        return -1;
    }

    @Override
    public int isSignAt(String wname, int x, int y, int z) {
        return -1;
    }

    @Override
    public void scheduleServerTask(Runnable run, long delay) {
        /* Add task record to queue */
        synchronized (schedlock) {
            TaskRecord tr = new TaskRecord(cur_tick + delay, next_id++, new FutureTask<Object>(run, null));
            runqueue.add(tr);
        }
    }

    @Override
    public DynmapPlayer[] getOnlinePlayers() {
        if (server.getPlayerManager() == null) return new DynmapPlayer[0];

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        int playerCount = players.size();
        DynmapPlayer[] dplay = new DynmapPlayer[players.size()];

        for (int i = 0; i < playerCount; i++) {
            ServerPlayerEntity player = players.get(i);
            dplay[i] = plugin.getOrAddPlayer(player);
        }

        return dplay;
    }

    @Override
    public void reload() {
        plugin.onDisable();
        plugin.onEnable();
        plugin.onStart();
    }

    @Override
    public DynmapPlayer getPlayer(String name) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

        for (ServerPlayerEntity player : players) {

            if (player.getName().getString().equalsIgnoreCase(name)) {
                return plugin.getOrAddPlayer(player);
            }
        }

        return null;
    }

    @Override
    public Set<String> getIPBans() {
        BannedIpList bl = server.getPlayerManager().getIpBanList();
        Set<String> ips = new HashSet<String>();

        for (String s : bl.getNames()) {
            ips.add(s);
        }

        return ips;
    }

    @Override
    public <T> Future<T> callSyncMethod(Callable<T> task) {
        return callSyncMethod(task, 0);
    }

    public <T> Future<T> callSyncMethod(Callable<T> task, long delay) {
        FutureTask<T> ft = new FutureTask<T>(task);

        /* Add task record to queue */
        synchronized (schedlock) {
            TaskRecord tr = new TaskRecord(cur_tick + delay, next_id++, ft);
            runqueue.add(tr);
        }

        return ft;
    }

    void clearTaskQueue() {
        this.runqueue.clear();
    }

    @Override
    public String getServerName() {
        String sn;
        if (server.isSinglePlayer())
            sn = "Integrated";
        else
            sn = server.getServerIp();
        if (sn == null) sn = "Unknown Server";
        return sn;
    }

    @Override
    public boolean isPlayerBanned(String pid) {
        BannedPlayerList bl = server.getPlayerManager().getUserBanList();
        return bl.contains(getProfileByName(pid));
    }

    @Override
    public String stripChatColor(String s) {
        return plugin.patternControlCode.matcher(s).replaceAll("");
    }

    private Set<DynmapListenerManager.EventType> registered = new HashSet<DynmapListenerManager.EventType>();

    @Override
    public boolean requestEventNotification(DynmapListenerManager.EventType type) {
        if (registered.contains(type)) {
            return true;
        }

        switch (type) {
            case WORLD_LOAD:
            case WORLD_UNLOAD:
                /* Already called for normal world activation/deactivation */
                break;

            case WORLD_SPAWN_CHANGE:
                /*TODO
                pm.registerEvents(new Listener() {
                    @EventHandler(priority=EventPriority.MONITOR)
                    public void onSpawnChange(SpawnChangeEvent evt) {
                        DynmapWorld w = new BukkitWorld(evt.getWorld());
                        core.listenerManager.processWorldEvent(EventType.WORLD_SPAWN_CHANGE, w);
                    }
                }, DynmapPlugin.this);
                */
                break;

            case PLAYER_JOIN:
            case PLAYER_QUIT:
                /* Already handled */
                break;

            case PLAYER_BED_LEAVE:
                /*TODO
                pm.registerEvents(new Listener() {
                    @EventHandler(priority=EventPriority.MONITOR)
                    public void onPlayerBedLeave(PlayerBedLeaveEvent evt) {
                        DynmapPlayer p = new BukkitPlayer(evt.getPlayer());
                        core.listenerManager.processPlayerEvent(EventType.PLAYER_BED_LEAVE, p);
                    }
                }, DynmapPlugin.this);
                */
                break;

            case PLAYER_CHAT:
                if (plugin.chathandler == null) {
                    plugin.setChatHandler(new DynmapPlugin.ChatHandler(plugin));
                    ServerChatEvents.EVENT.register((player, message) -> plugin.chathandler.handleChat(player, message));
                }
                break;

            case BLOCK_BREAK:
                /*TODO
                pm.registerEvents(new Listener() {
                    @EventHandler(priority=EventPriority.MONITOR)
                    public void onBlockBreak(BlockBreakEvent evt) {
                        if(evt.isCancelled()) return;
                        Block b = evt.getBlock();
                        if(b == null) return;
                        Location l = b.getLocation();
                        core.listenerManager.processBlockEvent(EventType.BLOCK_BREAK, b.getType().getId(),
                                BukkitWorld.normalizeWorldName(l.getWorld().getName()), l.getBlockX(), l.getBlockY(), l.getBlockZ());
                    }
                }, DynmapPlugin.this);
                */
                break;

            case SIGN_CHANGE:
                /*TODO
                pm.registerEvents(new Listener() {
                    @EventHandler(priority=EventPriority.MONITOR)
                    public void onSignChange(SignChangeEvent evt) {
                        if(evt.isCancelled()) return;
                        Block b = evt.getBlock();
                        Location l = b.getLocation();
                        String[] lines = evt.getLines();
                        DynmapPlayer dp = null;
                        Player p = evt.getPlayer();
                        if(p != null) dp = new BukkitPlayer(p);
                        core.listenerManager.processSignChangeEvent(EventType.SIGN_CHANGE, b.getType().getId(),
                                BukkitWorld.normalizeWorldName(l.getWorld().getName()), l.getBlockX(), l.getBlockY(), l.getBlockZ(), lines, dp);
                    }
                }, DynmapPlugin.this);
                */
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
        return DynmapCommonAPIListener.fireWebChatEvent(source, name, msg);
    }

    @Override
    public void broadcastMessage(String msg) {
        Text component = new LiteralText(msg);
        server.getPlayerManager().broadcastChatMessage(component, MessageType.SYSTEM, Util.NIL_UUID);
        Log.info(stripChatColor(msg));
    }

    @Override
    public String[] getBiomeIDs() {
        BiomeMap[] b = BiomeMap.values();
        String[] bname = new String[b.length];

        for (int i = 0; i < bname.length; i++) {
            bname[i] = b[i].toString();
        }

        return bname;
    }

    @Override
    public double getCacheHitRate() {
        if (plugin.sscache != null)
            return plugin.sscache.getHitRate();
        return 0.0;
    }

    @Override
    public void resetCacheStats() {
        if (plugin.sscache != null)
            plugin.sscache.resetStats();
    }

    @Override
    public DynmapWorld getWorldByName(String wname) {
        return plugin.getWorldByName(wname);
    }

    @Override
    public DynmapPlayer getOfflinePlayer(String name) {
        /*
        OfflinePlayer op = getServer().getOfflinePlayer(name);
        if(op != null) {
            return new BukkitPlayer(op);
        }
        */
        return null;
    }

    @Override
    public Set<String> checkPlayerPermissions(String player, Set<String> perms) {
        PlayerManager scm = server.getPlayerManager();
        if (scm == null) return Collections.emptySet();
        BannedPlayerList bl = scm.getUserBanList();
        if (bl == null) return Collections.emptySet();
        if (bl.contains(getProfileByName(player))) {
            return Collections.emptySet();
        }
        Set<String> rslt = plugin.hasOfflinePermissions(player, perms);
        if (rslt == null) {
            rslt = new HashSet<String>();
            if (plugin.isOp(player)) {
                rslt.addAll(perms);
            }
        }
        return rslt;
    }

    @Override
    public boolean checkPlayerPermission(String player, String perm) {
        PlayerManager scm = server.getPlayerManager();
        if (scm == null) return false;
        BannedPlayerList bl = scm.getUserBanList();
        if (bl == null) return false;
        if (bl.contains(getProfileByName(player))) {
            return false;
        }
        return plugin.hasOfflinePermission(player, perm);
    }

    /**
     * Render processor helper - used by code running on render threads to request chunk snapshot cache from server/sync thread
     */
    @Override
    public MapChunkCache createMapChunkCache(DynmapWorld w, List<DynmapChunk> chunks,
                                             boolean blockdata, boolean highesty, boolean biome, boolean rawbiome) {
        FabricMapChunkCache c = (FabricMapChunkCache) w.getChunkCache(chunks);
        if (c == null) {
            return null;
        }
        if (w.visibility_limits != null) {
            for (VisibilityLimit limit : w.visibility_limits) {
                c.setVisibleRange(limit);
            }

            c.setHiddenFillStyle(w.hiddenchunkstyle);
        }

        if (w.hidden_limits != null) {
            for (VisibilityLimit limit : w.hidden_limits) {
                c.setHiddenRange(limit);
            }

            c.setHiddenFillStyle(w.hiddenchunkstyle);
        }

        if (!c.setChunkDataTypes(blockdata, biome, highesty, rawbiome)) {
            Log.severe("CraftBukkit build does not support biome APIs");
        }

        if (chunks.size() == 0)     /* No chunks to get? */ {
            c.loadChunks(0);
            return c;
        }

        //Now handle any chunks in server thread that are already loaded (on server thread)
        final FabricMapChunkCache cc = c;
        Future<Boolean> f = this.callSyncMethod(new Callable<Boolean>() {
            public Boolean call() throws Exception {
                // Update busy state on world
                FabricWorld fw = (FabricWorld) cc.getWorld();
                //TODO
                //setBusy(fw.getWorld());
                cc.getLoadedChunks();
                return true;
            }
        }, 0);
        try {
            f.get();
        } catch (CancellationException cx) {
            return null;
        } catch (ExecutionException xx) {
            Log.severe("Exception while loading chunks", xx.getCause());
            return null;
        } catch (Exception ix) {
            Log.severe(ix);
            return null;
        }
        if (!w.isLoaded()) {
            return null;
        }
        // Now, do rest of chunk reading from calling thread
        c.readChunks(chunks.size());

        return c;
    }

    @Override
    public int getMaxPlayers() {
        return server.getMaxPlayerCount();
    }

    @Override
    public int getCurrentPlayers() {
        return server.getPlayerManager().getCurrentPlayerCount();
    }

    public void tickEvent(MinecraftServer server) {
        cur_tick_starttime = System.nanoTime();
        long elapsed = cur_tick_starttime - plugin.lasttick;
        plugin.lasttick = cur_tick_starttime;
        plugin.avgticklen = ((plugin.avgticklen * 99) / 100) + (elapsed / 100);
        plugin.tps = (double) 1E9 / (double) plugin.avgticklen;
        // Tick core
        if (plugin.core != null) {
            plugin.core.serverTick(plugin.tps);
        }

        boolean done = false;
        TaskRecord tr = null;

        while (!plugin.blockupdatequeue.isEmpty()) {
            DynmapPlugin.BlockUpdateRec r = plugin.blockupdatequeue.remove();
            BlockState bs = r.w.getBlockState(new BlockPos(r.x, r.y, r.z));
            int idx = Block.STATE_IDS.getRawId(bs);
            if (!org.dynmap.hdmap.HDBlockModels.isChangeIgnoredBlock(DynmapPlugin.stateByID[idx])) {
                if (plugin.onblockchange_with_id)
                    plugin.mapManager.touch(r.wid, r.x, r.y, r.z, "blockchange[" + idx + "]");
                else
                    plugin.mapManager.touch(r.wid, r.x, r.y, r.z, "blockchange");
            }
        }

        long now;

        synchronized (schedlock) {
            cur_tick++;
            now = System.nanoTime();
            tr = runqueue.peek();
            /* Nothing due to run */
            if ((tr == null) || (tr.getTickToRun() > cur_tick) || ((now - cur_tick_starttime) > plugin.perTickLimit)) {
                done = true;
            } else {
                tr = runqueue.poll();
            }
        }
        while (!done) {
            tr.run();

            synchronized (schedlock) {
                tr = runqueue.peek();
                now = System.nanoTime();
                /* Nothing due to run */
                if ((tr == null) || (tr.getTickToRun() > cur_tick) || ((now - cur_tick_starttime) > plugin.perTickLimit)) {
                    done = true;
                } else {
                    tr = runqueue.poll();
                }
            }
        }
        while (!plugin.msgqueue.isEmpty()) {
            DynmapPlugin.ChatMessage cm = plugin.msgqueue.poll();
            DynmapPlayer dp = null;
            if (cm.sender != null)
                dp = plugin.getOrAddPlayer(cm.sender);
            else
                dp = new FabricPlayer(plugin, null);

            plugin.core.listenerManager.processChatEvent(DynmapListenerManager.EventType.PLAYER_CHAT, dp, cm.message);
        }
        // Check for generated chunks
        if ((cur_tick % 20) == 0) {
        }
    }

    private <T> Predicate<T> distinctByKeyAndNonNull(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> t != null && seen.add(keyExtractor.apply(t));
    }

    private Optional<ModContainer> getModContainerById(String id) {
        return FabricLoader.getInstance().getModContainer(id);
    }

    @Override
    public boolean isModLoaded(String name) {
        return FabricLoader.getInstance().getModContainer(name).isPresent();
    }

    @Override
    public String getModVersion(String name) {
        Optional<ModContainer> mod = getModContainerById(name);    // Try case sensitive lookup
        return mod.map(modContainer -> modContainer.getMetadata().getVersion().getFriendlyString()).orElse(null);
    }

    @Override
    public double getServerTPS() {
        return plugin.tps;
    }

    @Override
    public String getServerIP() {
        if (server.isSinglePlayer())
            return "0.0.0.0";
        else
            return server.getServerIp();
    }

    @Override
    public File getModContainerFile(String name) {
        Optional<ModContainer> container = getModContainerById(name);    // Try case sensitive lookup
        if (container.isPresent()) {
            Path path = container.get().getRootPath();
            if (path.getFileSystem().provider().getScheme().equals("jar")) {
                path = Paths.get(path.getFileSystem().toString());
            }
            return path.toFile();
        }
        return null;
    }

    @Override
    public List<String> getModList() {
        return FabricLoader.getInstance()
                .getAllMods()
                .stream()
                .map(container -> container.getMetadata().getId())
                .collect(Collectors.toList());
    }

    @Override
    public Map<Integer, String> getBlockIDMap() {
        Map<Integer, String> map = new HashMap<Integer, String>();
        return map;
    }

    @Override
    public InputStream openResource(String modid, String rname) {
        if (modid == null) modid = "minecraft";

        if ("minecraft".equals(modid)) {
            return MinecraftServer.class.getClassLoader().getResourceAsStream(rname);
        } else {
            if (rname.startsWith("/") || rname.startsWith("\\")) {
                rname = rname.substring(1);
            }

            final String finalModid = modid;
            final String finalRname = rname;
            return getModContainerById(modid).map(container -> {
                try {
                    return Files.newInputStream(container.getPath(finalRname));
                } catch (IOException e) {
                    Log.severe("Failed to load resource of mod :" + finalModid, e);
                    return null;
                }
            }).orElse(null);
        }
    }

    /**
     * Get block unique ID map (module:blockid)
     */
    @Override
    public Map<String, Integer> getBlockUniqueIDMap() {
        HashMap<String, Integer> map = new HashMap<String, Integer>();
        return map;
    }

    /**
     * Get item unique ID map (module:itemid)
     */
    @Override
    public Map<String, Integer> getItemUniqueIDMap() {
        HashMap<String, Integer> map = new HashMap<String, Integer>();
        return map;
    }

}
