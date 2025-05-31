package org.dynmap.neoforge_1_21_1;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.PlayerList;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.neoforge_1_21_1.permissions.FilePermissions;
import org.dynmap.neoforge_1_21_1.permissions.OpPermissions;
import org.dynmap.neoforge_1_21_1.permissions.PermissionProvider;
import org.dynmap.permissions.PermissionsHandler;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.DynmapLogger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public class DynmapPlugin {
	DynmapCore core;
	PermissionProvider permissions;
	private boolean core_enabled;
	public GenericChunkCache sscache;
	public PlayerList playerList;
	MapManager mapManager;
	private static net.minecraft.server.MinecraftServer server;
	public static DynmapPlugin plugin;
	HashMap<String, Integer> sortWeights = new HashMap<String, Integer>();
	// Drop world load ticket after 30 seconds
	private long worldIdleTimeoutNS = 30 * 1000000000L;
	private HashMap<String, NeoForgeWorld> worlds = new HashMap<String, NeoForgeWorld>();
	private LevelAccessor last_world;
	private NeoForgeWorld last_fworld;
	private Map<String, NeoForgePlayer> players = new HashMap<String, NeoForgePlayer>();
	// TODO private ForgeMetrics metrics;
	private NeoForgeServer fserver;
	private boolean tickregistered = false;
	private boolean useSaveFolder = true;

	private static final String[] TRIGGER_DEFAULTS = { "blockupdate", "chunkpopulate", "chunkgenerate" };

	public static class BlockUpdateRec {
		LevelAccessor w;
		String wid;
		int x, y, z;
	}

	ConcurrentLinkedQueue<BlockUpdateRec> blockupdatequeue = new ConcurrentLinkedQueue<BlockUpdateRec>();

	public static DynmapBlockState[] stateByID;

	private Map<String, LongOpenHashSet> knownloadedchunks = new HashMap<String, LongOpenHashSet>();
	private boolean didInitialKnownChunks = false;

	private void addKnownChunk(NeoForgeWorld fw, ChunkPos pos) {
		LongOpenHashSet cset = knownloadedchunks.get(fw.getName());
		if (cset == null) {
			cset = new LongOpenHashSet();
			knownloadedchunks.put(fw.getName(), cset);
		}
		cset.add(pos.toLong());
	}

	private void removeKnownChunk(NeoForgeWorld fw, ChunkPos pos) {
		LongOpenHashSet cset = knownloadedchunks.get(fw.getName());
		if (cset != null) {
			cset.remove(pos.toLong());
		}
	}

	private boolean checkIfKnownChunk(NeoForgeWorld fw, ChunkPos pos) {
		LongOpenHashSet cset = knownloadedchunks.get(fw.getName());
		if (cset != null) {
			return cset.contains(pos.toLong());
		}
		return false;
	}

	private static Registry<Biome> reg = null;

	private static Registry<Biome> getBiomeReg() {
		if (reg == null) {
			reg = server.registryAccess().registryOrThrow(Registries.BIOME);
		}
		return reg;
	}

	/**
	 * Initialize block states (org.dynmap.blockstate.DynmapBlockState)
	 */
	public void initializeBlockStates() {
		stateByID = new DynmapBlockState[512 * 32]; // Simple map - scale as needed
		Arrays.fill(stateByID, DynmapBlockState.AIR); // Default to air

		IdMapper<BlockState> bsids = Block.BLOCK_STATE_REGISTRY;

		DynmapBlockState basebs = null;
		Block baseb = null;
		int baseidx = 0;

		Iterator<BlockState> iter = bsids.iterator();
		DynmapBlockState.Builder bld = new DynmapBlockState.Builder();
		while (iter.hasNext()) {
			BlockState bs = iter.next();
			int idx = bsids.getId(bs);
			if (idx >= stateByID.length) {
				int plen = stateByID.length;
				stateByID = Arrays.copyOf(stateByID, idx * 11 / 10); // grow array by 10%
				Arrays.fill(stateByID, plen, stateByID.length, DynmapBlockState.AIR);
			}
			Block b = bs.getBlock();
			// If this is new block vs last, it's the base block state
			if (b != baseb) {
				basebs = null;
				baseidx = idx;
				baseb = b;
			}
			ResourceLocation ui = BuiltInRegistries.BLOCK.getKey(b);

			if (ui == null) {
				continue;
			}
			String bn = ui.getNamespace() + ":" + ui.getPath();
			// Only do defined names, and not "air"
			if (!bn.equals(DynmapBlockState.AIR_BLOCK)) {
				String statename = "";
				for (net.minecraft.world.level.block.state.properties.Property<?> p : bs.getProperties()) {
					if (statename.length() > 0) {
						statename += ",";
					}
					statename += p.getName() + "=" + bs.getValue(p).toString();
				}
				int lightAtten = 15;
				try { // Workaround for mods with broken block state logic...
					lightAtten = bs.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? 15
							: (bs.propagatesSkylightDown(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? 0 : 1);
				} catch (Exception x) {
					Log.warning(String.format("Exception while checking lighting data for block state: %s[%s]", bn,
							statename));
					Log.verboseinfo("Exception: " + x.toString());
				}
				// Log.info("statename=" + bn + "[" + statename + "], lightAtten=" +
				// lightAtten);
				// Fill in base attributes
				bld.setBaseState(basebs).setStateIndex(idx - baseidx).setBlockName(bn).setStateName(statename)
						.setLegacyBlockID(idx).setAttenuatesLight(lightAtten);
				if (bs.getSoundType() != null) {
					bld.setMaterial(bs.getSoundType().toString());
				}
				if (bs.isSolid()) {
					bld.setSolid();
				}
				if (bs.isAir()) {
					bld.setAir();
				}
				if (bs.is(BlockTags.LOGS)) {
					bld.setLog();
				}
				if (bs.is(BlockTags.LEAVES)) {
					bld.setLeaves();
				}
				if ((!bs.getFluidState().isEmpty()) && !(bs.getBlock() instanceof LiquidBlock)) {
					bld.setWaterlogged();
				}
				DynmapBlockState dbs = bld.build(); // Build state
				stateByID[idx] = dbs;
				if (basebs == null) {
					basebs = dbs;
				}
			}
		}
		for (int gidx = 0; gidx < DynmapBlockState.getGlobalIndexMax(); gidx++) {
			DynmapBlockState bs = DynmapBlockState.getStateByGlobalIndex(gidx);
			// Log.info(gidx + ":" + bs.toString() + ", gidx=" + bs.globalStateIndex + ",
			// sidx=" + bs.stateIndex);
		}
	}

	// public static final Item getItemByID(int id) {
	// return Item.getItemById(id);
	// }

	private static Biome[] biomelist = null;

	public static final Biome[] getBiomeList() {
		if (biomelist == null) {
			biomelist = new Biome[256];
			Iterator<Biome> iter = getBiomeReg().iterator();
			while (iter.hasNext()) {
				Biome b = iter.next();
				int bidx = getBiomeReg().getId(b);
				if (bidx >= biomelist.length) {
					biomelist = Arrays.copyOf(biomelist, bidx + biomelist.length);
				}
				biomelist[bidx] = b;
			}
		}
		return biomelist;
	}

	// public static final NetworkManager getNetworkManager(ServerPlayNetHandler nh)
	// {
	// return nh.netManager;
	// }

	NeoForgePlayer getOrAddPlayer(ServerPlayer p) {
		String name = p.getName().getString();
		NeoForgePlayer fp = players.get(name);
		if (fp != null) {
			fp.player = p;
		} else {
			fp = new NeoForgePlayer(this, p);
			players.put(name, fp);
		}
		return fp;
	}

	/**
	 * TODO: depends on forge chunk manager
	 * private static class WorldBusyRecord {
	 * long last_ts;
	 * Ticket ticket;
	 * }
	 * private static HashMap<Integer, WorldBusyRecord> busy_worlds = new
	 * HashMap<Integer, WorldBusyRecord>();
	 *
	 * private void setBusy(World w) {
	 * setBusy(w, null);
	 * }
	 * static void setBusy(World w, Ticket t) {
	 * if(w == null) return;
	 * if (!DynmapMod.useforcedchunks) return;
	 * WorldBusyRecord wbr = busy_worlds.get(w.provider.getDimension());
	 * if(wbr == null) { // Not busy, make ticket and keep spawn loaded
	 * Debug.debug("World " + w.getWorldInfo().getWorldName() + "/"+
	 * w.provider.getDimensionType().getName() + " is busy");
	 * wbr = new WorldBusyRecord();
	 * if(t != null)
	 * wbr.ticket = t;
	 * else
	 * wbr.ticket = ForgeChunkManager.requestTicket(DynmapMod.instance, w,
	 * ForgeChunkManager.Type.NORMAL);
	 * if(wbr.ticket != null) {
	 * BlockPos cc = w.getSpawnPoint();
	 * ChunkPos ccip = new ChunkPos(cc.getX() >> 4, cc.getZ() >> 4);
	 * ForgeChunkManager.forceChunk(wbr.ticket, ccip);
	 * busy_worlds.put(w.provider.getDimension(), wbr); // Add to busy list
	 * }
	 * }
	 * wbr.last_ts = System.nanoTime();
	 * }
	 *
	 * private void doIdleOutOfWorlds() {
	 * if (!DynmapMod.useforcedchunks) return;
	 * long ts = System.nanoTime() - worldIdleTimeoutNS;
	 * for(Iterator<WorldBusyRecord> itr = busy_worlds.values().iterator();
	 * itr.hasNext();) {
	 * WorldBusyRecord wbr = itr.next();
	 * if(wbr.last_ts < ts) {
	 * World w = wbr.ticket.world;
	 * Debug.debug("World " + w.getWorldInfo().getWorldName() + "/" +
	 * wbr.ticket.world.provider.getDimensionType().getName() + " is idle");
	 * if (wbr.ticket != null)
	 * ForgeChunkManager.releaseTicket(wbr.ticket); // Release hold on world
	 * itr.remove();
	 * }
	 * }
	 * }
	 */

	public static class OurLog implements DynmapLogger {
		Logger log;
		public static final String DM = "[Dynmap] ";

		OurLog() {
			log = LogManager.getLogger("Dynmap");
		}

		@Override
		public void info(String s) {
			log.info(DM + s);
		}

		@Override
		public void severe(Throwable t) {
			log.fatal(t);
		}

		@Override
		public void severe(String s) {
			log.fatal(DM + s);
		}

		@Override
		public void severe(String s, Throwable t) {
			log.fatal(DM + s, t);
		}

		@Override
		public void verboseinfo(String s) {
			log.info(DM + s);
		}

		@Override
		public void warning(String s) {
			log.warn(DM + s);
		}

		@Override
		public void warning(String s, Throwable t) {
			log.warn(DM + s, t);
		}
	}

	public DynmapPlugin(MinecraftServer srv) {
		plugin = this;
		this.server = srv;
		fserver = new NeoForgeServer(this, srv);
	}

	public boolean isOp(String player) {
		String[] ops = server.getPlayerList().getOps().getUserList();
		for (String op : ops) {
			if (op.equalsIgnoreCase(player)) {
				return true;
			}
		}
		return (server.isSingleplayer() && player.equalsIgnoreCase(server.getSingleplayerProfile().getName()));
	}

	boolean hasPerm(ServerPlayer psender, String permission) {
		PermissionsHandler ph = PermissionsHandler.getHandler();
		if ((psender != null) && (ph != null) && ph.hasPermission(psender.getName().getString(), permission)) {
			return true;
		}
		return permissions.has(psender, permission);
	}

	boolean hasPermNode(ServerPlayer psender, String permission) {
		PermissionsHandler ph = PermissionsHandler.getHandler();
		if ((psender != null) && (ph != null) && ph.hasPermissionNode(psender.getName().getString(), permission)) {
			return true;
		}
		return permissions.hasPermissionNode(psender, permission);
	}

	Set<String> hasOfflinePermissions(String player, Set<String> perms) {
		Set<String> rslt = null;
		PermissionsHandler ph = PermissionsHandler.getHandler();
		if (ph != null) {
			rslt = ph.hasOfflinePermissions(player, perms);
		}
		Set<String> rslt2 = hasOfflinePermissions(player, perms);
		if ((rslt != null) && (rslt2 != null)) {
			Set<String> newrslt = new HashSet<String>(rslt);
			newrslt.addAll(rslt2);
			rslt = newrslt;
		} else if (rslt2 != null) {
			rslt = rslt2;
		}
		return rslt;
	}

	boolean hasOfflinePermission(String player, String perm) {
		PermissionsHandler ph = PermissionsHandler.getHandler();
		if (ph != null) {
			if (ph.hasOfflinePermission(player, perm)) {
				return true;
			}
		}
		return permissions.hasOfflinePermission(player, perm);
	}

	public class TexturesPayload {
		public long timestamp;
		public String profileId;
		public String profileName;
		public boolean isPublic;
		public Map<String, ProfileTexture> textures;

	}

	public class ProfileTexture {
		public String url;
	}

	public void loadExtraBiomes(String mcver) {
		int cnt = 0;
		BiomeMap.loadWellKnownByVersion(mcver);

		Biome[] list = getBiomeList();

		for (int i = 0; i < list.length; i++) {
			Biome bb = list[i];
			if (bb != null) {
				ResourceLocation regid = getBiomeReg().getKey(bb);
				String id = regid.getPath();
				String rl = regid.toString();
				float tmp = bb.getBaseTemperature(), hum = bb.getModifiedClimateSettings().downfall();
				int watermult = bb.getWaterColor();
				Log.verboseinfo(
						"biome[" + i + "]: hum=" + hum + ", tmp=" + tmp + ", mult=" + Integer.toHexString(watermult));

				BiomeMap bmap = BiomeMap.NULL;
				if (rl != null) { // If resource location, lookup by this
					bmap = BiomeMap.byBiomeResourceLocation(rl);
				} else {
					bmap = BiomeMap.byBiomeID(i);
				}
				if (bmap.isDefault() || (bmap == BiomeMap.NULL)) {
					bmap = new BiomeMap((rl != null) ? BiomeMap.NO_INDEX : i, id, tmp, hum, rl);
					Log.verboseinfo("Add custom biome [" + bmap.toString() + "] (" + i + ")");
					cnt++;
				} else {
					bmap.setTemperature(tmp);
					bmap.setRainfall(hum);
				}
				if (watermult != -1) {
					bmap.setWaterColorMultiplier(watermult);
					Log.verboseinfo("Set watercolormult for " + bmap.toString() + " (" + i + ") to "
							+ Integer.toHexString(watermult));
				}
				bmap.setBiomeObject(bb);
			}
		}
		if (cnt > 0)
			Log.info("Added " + cnt + " custom biome mappings");
	}

	private String[] getBiomeNames() {
		Biome[] list = getBiomeList();
		String[] lst = new String[list.length];
		for (int i = 0; i < list.length; i++) {
			Biome bb = list[i];
			if (bb != null) {
				lst[i] = bb.toString();
			}
		}
		return lst;
	}

	public void onEnable() {
		/* Get MC version */
		String mcver = server.getServerVersion();
		/* Load extra biomes */
		loadExtraBiomes(mcver);
		/* Set up player login/quit event handler */
		registerPlayerLoginListener();

		/* Initialize permissions handler */
		permissions = FilePermissions.create();
		if (permissions == null) {
			permissions = new OpPermissions(new String[] { "webchat", "marker.icons", "marker.list", "webregister",
					"stats", "hide.self", "show.self" });
		}
		/* Get and initialize data folder */
		File dataDirectory = new File("dynmap");

		if (dataDirectory.exists() == false) {
			dataDirectory.mkdirs();
		}

		/* Instantiate core */
		if (core == null) {
			core = new DynmapCore();
		}

		/* Inject dependencies */
		core.setPluginJarFile(DynmapMod.jarfile);
		core.setPluginVersion(DynmapMod.ver);
		core.setMinecraftVersion(mcver);
		core.setDataFolder(dataDirectory);
		core.setServer(fserver);
		core.setTriggerDefault(TRIGGER_DEFAULTS);
		core.setBiomeNames(getBiomeNames());

		if (!core.initConfiguration(null)) {
			return;
		}
		// Extract default permission example, if needed
		File filepermexample = new File(core.getDataFolder(), "permissions.yml.example");
		core.createDefaultFileFromResource("/permissions.yml.example", filepermexample);

		DynmapCommonAPIListener.apiInitialized(core);
	}

	private static int test(CommandSource source) throws CommandSyntaxException {
		Log.warning(source.toString());
		return 1;
	}

	private DynmapCommand dynmapCmd;
	private DmapCommand dmapCmd;
	private DmarkerCommand dmarkerCmd;
	private DynmapExpCommand dynmapexpCmd;

	public void onStarting(CommandDispatcher<CommandSourceStack> cd) {
		/* Register command hander */
		dynmapCmd = new DynmapCommand(this);
		dmapCmd = new DmapCommand(this);
		dmarkerCmd = new DmarkerCommand(this);
		dynmapexpCmd = new DynmapExpCommand(this);
		dynmapCmd.register(cd);
		dmapCmd.register(cd);
		dmarkerCmd.register(cd);
		dynmapexpCmd.register(cd);

		Log.info("Register commands");
	}

	public void onStart() {
		initializeBlockStates();
		/* Enable core */
		if (!core.enableCore(null)) {
			return;
		}
		core_enabled = true;
		VersionCheck.runCheck(core);
		// Get per tick time limit
		fserver.onStart(core.getMaxTickUseMS() * 1000000);

		/* Register tick handler */
		if (!tickregistered) {
			NeoForge.EVENT_BUS.register(fserver);
			tickregistered = true;
		}

		playerList = core.playerList;
		sscache = new GenericChunkCache(core.getSnapShotCacheSize(), core.useSoftRefInSnapShotCache());
		/* Get map manager from core */
		mapManager = core.getMapManager();

		/* Load saved world definitions */
		loadWorlds();

		/* Initialized the currently loaded worlds */
		for (ServerLevel world : server.getAllLevels()) {
			NeoForgeWorld w = this.getWorld(world);
		}
		for (NeoForgeWorld w : worlds.values()) {
			if (core.processWorldLoad(w)) { /* Have core process load first - fire event listeners if good load after */
				if (w.isLoaded()) {
					core.listenerManager.processWorldEvent(EventType.WORLD_LOAD, w);
				}
			}
		}
		core.updateConfigHashcode();

		/* Register our update trigger events */
		registerEvents();
		Log.info("Register events");

		// DynmapCommonAPIListener.apiInitialized(core);

		Log.info("Enabled");
	}

	public void onDisable() {
		DynmapCommonAPIListener.apiTerminated();

		// if (metrics != null) {
		// metrics.stop();
		// metrics = null;
		// }
		/* Save worlds */
		saveWorlds();

		/* Purge tick queue */
		fserver.runqueue.clear();

		/* Disable core */
		core.disableCore();
		core_enabled = false;

		if (sscache != null) {
			sscache.cleanup();
			sscache = null;
		}

		Log.info("Disabled");
	}

	void onCommand(CommandSourceStack commandSourceStack, String cmd, String[] args) {
		DynmapCommandSender dsender;
		ServerPlayer psender;
		try {
			psender = commandSourceStack.getPlayerOrException();
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException x) {
			psender = null;
		}

		if (psender != null) {
			dsender = new NeoForgePlayer(this, psender);
		} else {
			dsender = new NeoForgeCommandSender(commandSourceStack);
		}
		try {
			core.processCommand(dsender, cmd, cmd, args);
		} catch (Exception x) {
			dsender.sendMessage("Command internal error: " + x.getMessage());
			Log.severe("Error with command: " + cmd + Arrays.deepToString(args), x);
		}
	}

	public class PlayerTracker {
		@SubscribeEvent
		public void onPlayerLogin(PlayerLoggedInEvent event) {
			if (!core_enabled)
				return;
			final DynmapPlayer dp = getOrAddPlayer((ServerPlayer) event.getEntity());
			/* This event can be called from off server thread, so push processing there */
			core.getServer().scheduleServerTask(new Runnable() {
				public void run() {
					core.listenerManager.processPlayerEvent(EventType.PLAYER_JOIN, dp);
				}
			}, 2);
		}

		@SubscribeEvent
		public void onPlayerLogout(PlayerLoggedOutEvent event) {
			if (!core_enabled)
				return;
			final DynmapPlayer dp = getOrAddPlayer((ServerPlayer) event.getEntity());
			final String name = event.getEntity().getName().getString();
			/* This event can be called from off server thread, so push processing there */
			core.getServer().scheduleServerTask(new Runnable() {
				public void run() {
					core.listenerManager.processPlayerEvent(EventType.PLAYER_QUIT, dp);
					players.remove(name);
				}
			}, 0);
		}

		@SubscribeEvent
		public void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
			if (!core_enabled)
				return;
			getOrAddPlayer((ServerPlayer) event.getEntity()); // Freshen player object reference
		}

		@SubscribeEvent
		public void onPlayerRespawn(PlayerRespawnEvent event) {
			if (!core_enabled)
				return;
			getOrAddPlayer((ServerPlayer) event.getEntity()); // Freshen player object reference
		}
	}

	private PlayerTracker playerTracker = null;

	private void registerPlayerLoginListener() {
		if (playerTracker == null) {
			playerTracker = new PlayerTracker();
			NeoForge.EVENT_BUS.register(playerTracker);
		}
	}

	public class WorldTracker {
		@SubscribeEvent(priority = EventPriority.LOWEST)
		public void handleWorldLoad(LevelEvent.Load event) {
			if (!core_enabled)
				return;
			LevelAccessor w = event.getLevel();
			if (!(w instanceof ServerLevel))
				return;
			final NeoForgeWorld fw = getWorld((ServerLevel) w);
			// This event can be called from off server thread, so push processing there
			core.getServer().scheduleServerTask(new Runnable() {
				public void run() {
					if (core.processWorldLoad(fw)) // Have core process load first - fire event listeners if good load
													// after
						core.listenerManager.processWorldEvent(EventType.WORLD_LOAD, fw);
				}
			}, 0);
		}

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public void handleWorldUnload(LevelEvent.Unload event) {
			if (!core_enabled)
				return;
			LevelAccessor w = event.getLevel();
			if (!(w instanceof ServerLevel))
				return;
			final NeoForgeWorld fw = getWorld((ServerLevel) w);
			if (fw != null) {
				// This event can be called from off server thread, so push processing there
				core.getServer().scheduleServerTask(new Runnable() {
					public void run() {
						core.listenerManager.processWorldEvent(EventType.WORLD_UNLOAD, fw);
						core.processWorldUnload(fw);
					}
				}, 0);
				// Set world unloaded (needs to be immediate, since it may be invalid after
				// event)
				fw.setWorldUnloaded();
				// Clean up tracker
				// WorldUpdateTracker wut = updateTrackers.remove(fw.getName());
				// if(wut != null) wut.world = null;
			}
		}

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public void handleChunkLoad(ChunkEvent.Load event) {
			if (!onchunkgenerate)
				return;

			LevelAccessor w = event.getLevel();
			if (!(w instanceof ServerLevel))
				return;
			ChunkAccess c = event.getChunk();
			if ((c != null) && (c.getPersistedStatus() == ChunkStatus.FULL) && (c instanceof LevelChunk)) {
				NeoForgeWorld fw = getWorld((ServerLevel) w, false);
				if (fw != null) {
					addKnownChunk(fw, c.getPos());
				}
			}
		}

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public void handleChunkUnload(ChunkEvent.Unload event) {
			if (!onchunkgenerate)
				return;

			LevelAccessor w = event.getLevel();
			if (!(w instanceof ServerLevel))
				return;
			ChunkAccess c = event.getChunk();
			if (c != null) {
				NeoForgeWorld fw = getWorld((ServerLevel) w, false);
				ChunkPos cp = c.getPos();
				if (fw != null) {
					if (!checkIfKnownChunk(fw, cp)) {
						int ymax = Integer.MIN_VALUE;
						int ymin = Integer.MAX_VALUE;
						LevelChunkSection[] sections = c.getSections();
						for (int i = 0; i < sections.length; i++) {
							if ((sections[i] != null) && (sections[i].hasOnlyAir() == false)) {
								int sy = c.getSectionYFromSectionIndex(i);
								if (sy < ymin)
									ymin = sy;
								if ((sy + 16) > ymax)
									ymax = sy + 16;
							}
						}
						int x = cp.x << 4;
						int z = cp.z << 4;
						// If not empty AND not initial scan
						if (ymax != Integer.MIN_VALUE) {
							// Log.info(String.format("chunkkeyerate(unload)(%s,%d,%d,%d,%d,%d,%s)",
							// fw.getName(), x, ymin, z, x+15, ymax, z+15));
							mapManager.touchVolume(fw.getName(), x, ymin, z, x + 15, ymax, z + 15, "chunkgenerate");
						}
					}
					removeKnownChunk(fw, cp);
				}
			}
		}

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public void handleChunkDataSave(ChunkDataEvent.Save event) {
			if (!onchunkgenerate)
				return;

			LevelAccessor w = event.getLevel();
			if (!(w instanceof ServerLevel))
				return;
			ChunkAccess c = event.getChunk();
			if (c != null) {
				NeoForgeWorld fw = getWorld((ServerLevel) w, false);
				ChunkPos cp = c.getPos();
				if (fw != null) {
					if (!checkIfKnownChunk(fw, cp)) {
						int ymax = Integer.MIN_VALUE;
						int ymin = Integer.MAX_VALUE;
						LevelChunkSection[] sections = c.getSections();
						for (int i = 0; i < sections.length; i++) {
							if ((sections[i] != null) && (sections[i].hasOnlyAir() == false)) {
								int sy = c.getSectionYFromSectionIndex(i);
								if (sy < ymin)
									ymin = sy;
								if ((sy + 16) > ymax)
									ymax = sy + 16;
							}
						}
						int x = cp.x << 4;
						int z = cp.z << 4;
						// If not empty AND not initial scan
						if (ymax != Integer.MIN_VALUE) {
							// Log.info(String.format("chunkkeyerate(save)(%s,%d,%d,%d,%d,%d,%s)",
							// fw.getName(), x, ymin, z, x+15, ymax, z+15));
							mapManager.touchVolume(fw.getName(), x, ymin, z, x + 15, ymax, z + 15, "chunkgenerate");
						}
						// If cooked, add to known
						if ((c.getPersistedStatus() == ChunkStatus.FULL) && (c instanceof LevelChunk)) {
							addKnownChunk(fw, cp);
						}
					}
				}
			}
		}

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public void handleBlockToolModificationEvent(BlockEvent.BlockToolModificationEvent event) {
			handleBlockEvent(event);
		}

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public void handleBreakEvent(BlockEvent.BreakEvent event) {
			handleBlockEvent(event);
		}

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public void handleEntityMultiPlaceEvent(BlockEvent.EntityMultiPlaceEvent event) {
			handleBlockEvent(event);
		}

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public void handleEntityPlaceEvent(BlockEvent.EntityPlaceEvent event) {
			handleBlockEvent(event);
		}

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public void handleFarmlandTrampleEvent(BlockEvent.FarmlandTrampleEvent event) {
			handleBlockEvent(event);
		}

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public void handleFluidPlaceBlockEvent(BlockEvent.FluidPlaceBlockEvent event) {
			handleBlockEvent(event);
		}

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public void handleNeighborNotifyEvent(BlockEvent.NeighborNotifyEvent event) {
			handleBlockEvent(event);
		}

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public void handlePortalSpawnEvent(BlockEvent.PortalSpawnEvent event) {
			handleBlockEvent(event);
		}

		private void handleBlockEvent(BlockEvent event) {
			if (!core_enabled)
				return;
			if (!onblockchange)
				return;
			BlockUpdateRec r = new BlockUpdateRec();
			r.w = event.getLevel();
			if (!(r.w instanceof ServerLevel))
				return; // band-aid to prevent errors in unsupported 'running in client' scenario
			NeoForgeWorld fw = getWorld((ServerLevel) r.w, false);
			if (fw == null)
				return;
			r.wid = fw.getName();
			BlockPos p = event.getPos();
			r.x = p.getX();
			r.y = p.getY();
			r.z = p.getZ();
			blockupdatequeue.add(r);
		}
	}

	private WorldTracker worldTracker = null;
	private boolean onblockchange = false;
	private boolean onchunkpopulate = false;
	private boolean onchunkgenerate = false;
	boolean onblockchange_with_id = false;

	private void registerEvents() {
		// To trigger rendering.
		onblockchange = core.isTrigger("blockupdate");
		onchunkpopulate = core.isTrigger("chunkpopulate");
		onchunkgenerate = core.isTrigger("chunkgenerate");
		onblockchange_with_id = core.isTrigger("blockupdate-with-id");
		if (onblockchange_with_id)
			onblockchange = true;
		if ((worldTracker == null) && (onblockchange || onchunkpopulate || onchunkgenerate)) {
			worldTracker = new WorldTracker();
			NeoForge.EVENT_BUS.register(worldTracker);
		}
		// Prime the known full chunks
		if (onchunkgenerate && (server.getAllLevels() != null)) {
			for (ServerLevel world : server.getAllLevels()) {
				NeoForgeWorld fw = getWorld(world);
				if (fw == null)
					continue;
				Long2ObjectLinkedOpenHashMap<ChunkHolder> chunks = world.getChunkSource().chunkMap.visibleChunkMap;
				for (Entry<Long, ChunkHolder> k : chunks.long2ObjectEntrySet()) {
					long key = k.getKey().longValue();
					ChunkHolder ch = k.getValue();
					ChunkAccess c = null;
					try {
						c = ch.getChunkToSend();
					} catch (Exception x) {
					}
					if (c == null)
						continue;
					ChunkStatus cs = c.getPersistedStatus();
					ChunkPos pos = ch.getPos();
					if (cs == ChunkStatus.FULL) { // Cooked?
						// Add it as known
						addKnownChunk(fw, pos);
					}
				}
			}
		}
	}

	private NeoForgeWorld getWorldByName(String name) {
		return worlds.get(name);
	}

	NeoForgeWorld getWorld(ServerLevel w) {
		return getWorld(w, true);
	}

	private NeoForgeWorld getWorld(ServerLevel w, boolean add_if_not_found) {
		if (last_world == w) {
			return last_fworld;
		}
		String wname = NeoForgeWorld.getWorldName(w);

		for (NeoForgeWorld fw : worlds.values()) {
			if (fw.getRawName().equals(wname)) {
				last_world = w;
				last_fworld = fw;
				if (fw.isLoaded() == false) {
					fw.setWorldLoaded(w);
				}
				fw.updateWorld(w);
				return fw;
			}
		}
		NeoForgeWorld fw = null;
		if (add_if_not_found) {
			/* Add to list if not found */
			fw = new NeoForgeWorld(w);
			worlds.put(fw.getName(), fw);
		}
		last_world = w;
		last_fworld = fw;
		return fw;
	}

	private void saveWorlds() {
		File f = new File(core.getDataFolder(), NeoForgeWorld.SAVED_WORLDS_FILE);
		ConfigurationNode cn = new ConfigurationNode(f);
		ArrayList<HashMap<String, Object>> lst = new ArrayList<HashMap<String, Object>>();
		for (DynmapWorld fw : core.mapManager.getWorlds()) {
			HashMap<String, Object> vals = new HashMap<String, Object>();
			vals.put("name", fw.getRawName());
			vals.put("height", fw.worldheight);
			vals.put("miny", fw.minY);
			vals.put("sealevel", fw.sealevel);
			vals.put("nether", fw.isNether());
			vals.put("the_end", ((NeoForgeWorld) fw).isTheEnd());
			vals.put("title", fw.getTitle());
			lst.add(vals);
		}
		cn.put("worlds", lst);
		cn.put("useSaveFolderAsName", useSaveFolder);
		cn.put("maxWorldHeight", NeoForgeWorld.getMaxWorldHeight());

		cn.save();
	}

	private void loadWorlds() {
		File f = new File(core.getDataFolder(), NeoForgeWorld.SAVED_WORLDS_FILE);
		if (f.canRead() == false) {
			useSaveFolder = true;
			return;
		}
		ConfigurationNode cn = new ConfigurationNode(f);
		cn.load();
		// If defined, use maxWorldHeight
		NeoForgeWorld.setMaxWorldHeight(cn.getInteger("maxWorldHeight", 256));

		// If setting defined, use it
		if (cn.containsKey("useSaveFolderAsName")) {
			useSaveFolder = cn.getBoolean("useSaveFolderAsName", useSaveFolder);
		}
		List<Map<String, Object>> lst = cn.getMapList("worlds");
		if (lst == null) {
			Log.warning(String.format("Discarding bad %s", NeoForgeWorld.SAVED_WORLDS_FILE));
			return;
		}

		for (Map<String, Object> world : lst) {
			try {
				String name = (String) world.get("name");
				int height = (Integer) world.get("height");
				Integer miny = (Integer) world.get("miny");
				int sealevel = (Integer) world.get("sealevel");
				boolean nether = (Boolean) world.get("nether");
				boolean theend = (Boolean) world.get("the_end");
				String title = (String) world.get("title");
				if (name != null) {
					NeoForgeWorld fw = new NeoForgeWorld(name, height, sealevel, nether, theend, title,
							(miny != null) ? miny : 0);
					fw.setWorldUnloaded();
					core.processWorldLoad(fw);
					worlds.put(fw.getName(), fw);
				}
			} catch (Exception x) {
				Log.warning(String.format("Unable to load saved worlds from %s", NeoForgeWorld.SAVED_WORLDS_FILE));
				return;
			}
		}
	}

	public void serverStarted() {
		this.onStart();
		if (core != null) {
			core.serverStarted();
		}
	}

	public MinecraftServer getMCServer() {
		return server;
	}
}

class DynmapCommandHandler {
	private String cmd;
	private DynmapPlugin plugin;

	public DynmapCommandHandler(String cmd, DynmapPlugin p) {
		this.cmd = cmd;
		this.plugin = p;
	}

	public void register(CommandDispatcher<CommandSourceStack> cd) {
		cd.register(Commands.literal(cmd)
				.then(RequiredArgumentBuilder
						.<CommandSourceStack, String>argument("args", StringArgumentType.greedyString())
						.executes((ctx) -> this.execute(plugin.getMCServer(), ctx.getSource(), ctx.getInput())))
				.executes((ctx) -> this.execute(plugin.getMCServer(), ctx.getSource(), ctx.getInput())));
	}

	// @Override
	public int execute(MinecraftServer server, CommandSourceStack commandSourceStack,
			String cmdline) {
		String[] args = cmdline.split("\\s+");
		plugin.onCommand(commandSourceStack, cmd, Arrays.copyOfRange(args, 1, args.length));
		return 1;
	}

	// @Override
	public String getUsage(CommandSource arg0) {
		return "Run /" + cmd + " help for details on using command";
	}
}

class DynmapCommand extends DynmapCommandHandler {
	DynmapCommand(DynmapPlugin p) {
		super("dynmap", p);
	}
}

class DmapCommand extends DynmapCommandHandler {
	DmapCommand(DynmapPlugin p) {
		super("dmap", p);
	}
}

class DmarkerCommand extends DynmapCommandHandler {
	DmarkerCommand(DynmapPlugin p) {
		super("dmarker", p);
	}
}

class DynmapExpCommand extends DynmapCommandHandler {
	DynmapExpCommand(DynmapPlugin p) {
		super("dynmapexp", p);
	}
}
