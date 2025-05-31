package org.dynmap.neoforge_1_21_1;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.regex.Pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.UserBanList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.neoforge_1_21_1.DynmapPlugin.BlockUpdateRec;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.VisibilityLimit;

import com.mojang.authlib.GameProfile;

/**
 * Server access abstraction class
 */
public class NeoForgeServer extends DynmapServerInterface {
	private DynmapPlugin plugin;
	private MinecraftServer server;
	/* Server thread scheduler */
	private Object schedlock = new Object();
	private long cur_tick;
	private long next_id;
	private long cur_tick_starttime;
	PriorityQueue<TaskRecord> runqueue = new PriorityQueue<TaskRecord>();
	private ChatHandler chathandler;
	private ConcurrentLinkedQueue<ChatMessage> msgqueue = new ConcurrentLinkedQueue<ChatMessage>();
	private HashSet<String> modsused = new HashSet<String>();
	// TPS calculator
	private double tps;
	long lasttick;
	long avgticklen;
	// Per tick limit, in nsec
	private long perTickLimit = (50000000); // 50 ms

	private static final Pattern patternControlCode = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");

	public NeoForgeServer(DynmapPlugin plugin, MinecraftServer server) {
		this.plugin = plugin;
		this.server = server;
	}

	void onStart(long perTickLimit) {
		this.perTickLimit = perTickLimit;
		// Prep TPS
		lasttick = System.nanoTime();
		tps = 20.0;
	}

	private GameProfile getProfileByName(String player) {
		GameProfileCache cache = server.getProfileCache();
		Optional<GameProfile> val = cache.get(player);
		return val.isPresent() ? val.get() : null;
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
		TaskRecord tr = new TaskRecord();
		tr.future = new FutureTask<Object>(run, null);

		/* Add task record to queue */
		synchronized (schedlock) {
			tr.id = next_id++;
			tr.ticktorun = cur_tick + delay;
			runqueue.add(tr);
		}
	}

	@Override
	public DynmapPlayer[] getOnlinePlayers() {
		if (server.getPlayerList() == null)
			return new DynmapPlayer[0];
		List<ServerPlayer> playlist = server.getPlayerList().getPlayers();
		int pcnt = playlist.size();
		DynmapPlayer[] dplay = new DynmapPlayer[pcnt];

		for (int i = 0; i < pcnt; i++) {
			ServerPlayer p = playlist.get(i);
			dplay[i] = plugin.getOrAddPlayer(p);
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
		List<ServerPlayer> players = server.getPlayerList().getPlayers();

		for (ServerPlayer p : players) {
			if (p.getName().getString().equalsIgnoreCase(name)) {
				return plugin.getOrAddPlayer(p);
			}
		}

		return null;
	}

	@Override
	public Set<String> getIPBans() {
		UserBanList bl = server.getPlayerList().getBans();
		Set<String> ips = new HashSet<String>();

		for (String s : bl.getUserList()) {
			ips.add(s);
		}

		return ips;
	}

	@Override
	public <T> Future<T> callSyncMethod(Callable<T> task) {
		return callSyncMethod(task, 0);
	}

	public <T> Future<T> callSyncMethod(Callable<T> task, long delay) {
		TaskRecord tr = new TaskRecord();
		FutureTask<T> ft = new FutureTask<T>(task);
		tr.future = ft;

		/* Add task record to queue */
		synchronized (schedlock) {
			tr.id = next_id++;
			tr.ticktorun = cur_tick + delay;
			runqueue.add(tr);
		}

		return ft;
	}

	@Override
	public String getServerName() {
		String sn;
		if (server.isSingleplayer())
			sn = "Integrated";
		else
			sn = server.getLocalIp();
		if (sn == null)
			sn = "Unknown Server";
		return sn;
	}

	@Override
	public boolean isPlayerBanned(String pid) {
		UserBanList bl = server.getPlayerList().getBans();
		return bl.isBanned(getProfileByName(pid));
	}

	@Override
	public String stripChatColor(String s) {
		return patternControlCode.matcher(s).replaceAll("");
	}

	private Set<EventType> registered = new HashSet<EventType>();

	@Override
	public boolean requestEventNotification(EventType type) {
		if (registered.contains(type)) {
			return true;
		}

		switch (type) {
			case WORLD_LOAD:
			case WORLD_UNLOAD:
				/* Already called for normal world activation/deactivation */
				break;

			case WORLD_SPAWN_CHANGE:
				/* TODO
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
				/* TODO
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
				if (chathandler == null) {
					chathandler = new ChatHandler();
					NeoForge.EVENT_BUS.register(chathandler);
				}
				break;

			case BLOCK_BREAK:
				/* TODO
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
				/* TODO
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
		Component component = Component.literal(msg);
		server.getPlayerList().broadcastSystemMessage(component, false);
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
		return this.getWorldByName(wname);
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
		net.minecraft.server.players.PlayerList scm = server.getPlayerList();
		if (scm == null)
			return Collections.emptySet();
		UserBanList bl = scm.getBans();
		if (bl == null)
			return Collections.emptySet();
		if (bl.isBanned(getProfileByName(player))) {
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
		net.minecraft.server.players.PlayerList scm = server.getPlayerList();
		if (scm == null)
			return false;
		UserBanList bl = scm.getBans();
		if (bl == null)
			return false;
		if (bl.isBanned(getProfileByName(player))) {
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
		NeoForgeMapChunkCache c = (NeoForgeMapChunkCache) w.getChunkCache(chunks);
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

		if (chunks.size() == 0) /* No chunks to get? */
		{
			c.loadChunks(0);
			return c;
		}

		// Now handle any chunks in server thread that are already loaded (on server thread)
		final NeoForgeMapChunkCache cc = c;
		Future<Boolean> f = this.callSyncMethod(new Callable<Boolean>() {
			public Boolean call() throws Exception {
				// Update busy state on world
				NeoForgeWorld fw = (NeoForgeWorld) cc.getWorld();
				// TODO
				// setBusy(fw.getWorld());
				cc.getLoadedChunks();
				return true;
			}
		}, 0);
		try {
			f.get();
		} catch (CancellationException cx) {
			return null;
		} catch (InterruptedException cx) {
			return null;
		} catch (ExecutionException xx) {
			Log.severe("Exception while loading chunks", xx.getCause());
			return null;
		} catch (Exception ix) {
			Log.severe(ix);
			return null;
		}
		if (w.isLoaded() == false) {
			return null;
		}
		// Now, do rest of chunk reading from calling thread
		c.readChunks(chunks.size());

		return c;
	}

	@Override
	public int getMaxPlayers() {
		return server.getMaxPlayers();
	}

	@Override
	public int getCurrentPlayers() {
		return server.getPlayerList().getPlayerCount();
	}

	@SubscribeEvent
	public void tickEvent(ServerTickEvent.Post event) {
		cur_tick_starttime = System.nanoTime();
		long elapsed = cur_tick_starttime - lasttick;
		lasttick = cur_tick_starttime;
		avgticklen = ((avgticklen * 99) / 100) + (elapsed / 100);
		tps = (double) 1E9 / (double) avgticklen;
		// Tick core
		if (plugin.core != null) {
			plugin.core.serverTick(tps);
		}

		boolean done = false;
		TaskRecord tr = null;

		while (!plugin.blockupdatequeue.isEmpty()) {
			BlockUpdateRec r = plugin.blockupdatequeue.remove();
			BlockState bs = r.w.getBlockState(new BlockPos(r.x, r.y, r.z));
			int idx = Block.BLOCK_STATE_REGISTRY.getId(bs);
			if ((idx >= 0) && (!org.dynmap.hdmap.HDBlockModels.isChangeIgnoredBlock(DynmapPlugin.stateByID[idx]))) {
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
			if ((tr == null) || (tr.ticktorun > cur_tick) || ((now - cur_tick_starttime) > perTickLimit)) {
				done = true;
			} else {
				tr = runqueue.poll();
			}
		}
		while (!done) {
			tr.future.run();

			synchronized (schedlock) {
				tr = runqueue.peek();
				now = System.nanoTime();
				/* Nothing due to run */
				if ((tr == null) || (tr.ticktorun > cur_tick) || ((now - cur_tick_starttime) > perTickLimit)) {
					done = true;
				} else {
					tr = runqueue.poll();
				}
			}
		}
		while (!msgqueue.isEmpty()) {
			ChatMessage cm = msgqueue.poll();
			DynmapPlayer dp = null;
			if (cm.sender != null)
				dp = plugin.getOrAddPlayer(cm.sender);
			else
				dp = new NeoForgePlayer(plugin, null);

			plugin.core.listenerManager.processChatEvent(EventType.PLAYER_CHAT, dp, cm.message);
		}
		// Check for generated chunks
		if ((cur_tick % 20) == 0) {
		}
	}

	@Override
	public boolean isModLoaded(String name) {
		boolean loaded = ModList.get().isLoaded(name);
		if (loaded) {
			modsused.add(name);
		}
		return loaded;
	}

	@Override
	public String getModVersion(String name) {
		Optional<? extends ModContainer> mod = ModList.get().getModContainerById(name); // Try case sensitive lookup
		if (mod.isPresent()) {
			ArtifactVersion vi = mod.get().getModInfo().getVersion();
			return vi.getMajorVersion() + "." + vi.getMinorVersion() + "." + vi.getIncrementalVersion();
		}
		return null;
	}

	@Override
	public double getServerTPS() {
		return tps;
	}

	@Override
	public String getServerIP() {
		if (server.isSingleplayer())
			return "0.0.0.0";
		else
			return server.getLocalIp();
	}

	@Override
	public File getModContainerFile(String name) {
		ModFileInfo mfi = LoadingModList.get().getModFileById(name); // Try case sensitive lookup
		if (mfi != null) {
			try {
				File f = mfi.getFile().getFilePath().toFile();
				return f;
			} catch (UnsupportedOperationException ex) {
				// TODO Implement proper jar in jar method for fetching data
				/*
				Log.info("Searching for: " + name);
				for (IModInfo e : ModList.get().getMods()) {
					Log.info("in: " + e.getModId().toString());
					Log.info("resource: " + ModList.get().getModFileById(e.getModId()).getFile()
							.findResource(String.valueOf(mfi.getFile().getFilePath())));
				}
				 */
				Log.warning("jar in jar method found, skipping: " + ex.getMessage());
			}
		}
		return null;
	}

	@Override
	public List<String> getModList() {
		List<ModInfo> mil = LoadingModList.get().getMods();
		List<String> lst = new ArrayList<String>();
		for (ModInfo mi : mil) {
			lst.add(mi.getModId());
		}
		return lst;
	}

	@Override
	public Map<Integer, String> getBlockIDMap() {
		Map<Integer, String> map = new HashMap<Integer, String>();
		return map;
	}

	@Override
	public InputStream openResource(String modid, String rname) {
		// NeoForge removed ModContainer#getMod with no replacement
		/* if (modid == null)
			modid = "minecraft";
		
		Optional<? extends ModContainer> mc = ModList.get().getModContainerById(modid);
		Object mod = (mc.isPresent()) ? mc.get().getMod() : null;
		if (mod != null) {
			ClassLoader cl = mod.getClass().getClassLoader();
			if (cl == null)
				cl = ClassLoader.getSystemClassLoader();
			InputStream is = cl.getResourceAsStream(rname);
			if (is != null) {
				return is;
			}
		}
		List<ModInfo> mcl = LoadingModList.get().getMods();
		for (ModInfo mci : mcl) {
			mc = ModList.get().getModContainerById(mci.getModId());
			mod = (mc.isPresent()) ? mc.get().getMod() : null;
			if (mod == null)
				continue;
			ClassLoader cl = mod.getClass().getClassLoader();
			if (cl == null)
				cl = ClassLoader.getSystemClassLoader();
			InputStream is = cl.getResourceAsStream(rname);
			if (is != null) {
				return is;
			}
		} */
		return null;
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

	private static class TaskRecord implements Comparable<Object> {
		private long ticktorun;
		private long id;
		private FutureTask<?> future;

		@Override
		public int compareTo(Object o) {
			TaskRecord tr = (TaskRecord) o;

			if (this.ticktorun < tr.ticktorun) {
				return -1;
			} else if (this.ticktorun > tr.ticktorun) {
				return 1;
			} else if (this.id < tr.id) {
				return -1;
			} else if (this.id > tr.id) {
				return 1;
			} else {
				return 0;
			}
		}
	}

	private class ChatMessage {
		String message;
		ServerPlayer sender;
	}

	public class ChatHandler {
		@SubscribeEvent
		public void handleChat(ServerChatEvent event) {
			String msg = event.getMessage().getString();
			if (!msg.startsWith("/")) {
				ChatMessage cm = new ChatMessage();
				cm.message = msg;
				cm.sender = event.getPlayer();
				msgqueue.add(cm);
			}
		}
	}

}
