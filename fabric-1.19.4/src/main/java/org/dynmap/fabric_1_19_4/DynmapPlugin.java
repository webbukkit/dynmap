package org.dynmap.fabric_1_19_4;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.IdList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import org.dynmap.*;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapListenerManager;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.fabric_1_19_4.command.DmapCommand;
import org.dynmap.fabric_1_19_4.command.DmarkerCommand;
import org.dynmap.fabric_1_19_4.command.DynmapCommand;
import org.dynmap.fabric_1_19_4.command.DynmapExpCommand;
import org.dynmap.fabric_1_19_4.event.BlockEvents;
import org.dynmap.fabric_1_19_4.event.CustomServerChunkEvents;
import org.dynmap.fabric_1_19_4.event.CustomServerLifecycleEvents;
import org.dynmap.fabric_1_19_4.event.PlayerEvents;
import org.dynmap.fabric_1_19_4.mixin.BiomeEffectsAccessor;
import org.dynmap.fabric_1_19_4.permissions.*;
import org.dynmap.permissions.PermissionsHandler;
import org.dynmap.renderer.DynmapBlockState;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

public class DynmapPlugin {
    // FIXME: Fix package-private fields after splitting is done
    DynmapCore core;
    private PermissionProvider permissions;
    private boolean core_enabled;
    public GenericChunkCache sscache;
    public PlayerList playerList;
    MapManager mapManager;
    /**
     * Server is set when running and unset at shutdown.
     */
    private net.minecraft.server.MinecraftServer server;
    public static DynmapPlugin plugin;
    ChatHandler chathandler;
    private HashMap<String, Integer> sortWeights = new HashMap<String, Integer>();
    private HashMap<String, FabricWorld> worlds = new HashMap<String, FabricWorld>();
    private WorldAccess last_world;
    private FabricWorld last_fworld;
    private Map<String, FabricPlayer> players = new HashMap<String, FabricPlayer>();
    private FabricServer fserver;
    private boolean tickregistered = false;
    // TPS calculator
    double tps;
    long lasttick;
    long avgticklen;
    // Per tick limit, in nsec
    long perTickLimit = (50000000); // 50 ms
    private boolean useSaveFolder = true;

    private static final String[] TRIGGER_DEFAULTS = {"blockupdate", "chunkpopulate", "chunkgenerate"};

    static final Pattern patternControlCode = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");

    DynmapPlugin() {
        plugin = this;
        // Fabric events persist between server instances
        ServerLifecycleEvents.SERVER_STARTING.register(this::serverStart);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        CustomServerLifecycleEvents.SERVER_STARTED_PRE_WORLD_LOAD.register(this::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::serverStop);
    }

    int getSortWeight(String name) {
        return sortWeights.getOrDefault(name, 0);
    }

    void setSortWeight(String name, int wt) {
        sortWeights.put(name, wt);
    }

    void dropSortWeight(String name) {
        sortWeights.remove(name);
    }

    public static class BlockUpdateRec {
        WorldAccess w;
        String wid;
        int x, y, z;
    }

    ConcurrentLinkedQueue<BlockUpdateRec> blockupdatequeue = new ConcurrentLinkedQueue<BlockUpdateRec>();

    public static DynmapBlockState[] stateByID;

    /**
     * Initialize block states (org.dynmap.blockstate.DynmapBlockState)
     */
    public void initializeBlockStates() {
        stateByID = new DynmapBlockState[512 * 32];    // Simple map - scale as needed
        Arrays.fill(stateByID, DynmapBlockState.AIR); // Default to air

        IdList<BlockState> bsids = Block.STATE_IDS;

        DynmapBlockState basebs = null;
        Block baseb = null;
        int baseidx = 0;

        Iterator<BlockState> iter = bsids.iterator();
    	DynmapBlockState.Builder bld = new DynmapBlockState.Builder();
        while (iter.hasNext()) {
            BlockState bs = iter.next();
            int idx = bsids.getRawId(bs);
            if (idx >= stateByID.length) {
                int plen = stateByID.length;
    			stateByID = Arrays.copyOf(stateByID, idx*11/10); // grow array by 10%    			
                Arrays.fill(stateByID, plen, stateByID.length, DynmapBlockState.AIR);
            }
            Block b = bs.getBlock();
            // If this is new block vs last, it's the base block state
            if (b != baseb) {
                basebs = null;
                baseidx = idx;
                baseb = b;
            }

            Identifier ui = Registries.BLOCK.getId(b);
            if (ui == null) {
                continue;
            }
            String bn = ui.getNamespace() + ":" + ui.getPath();
            // Only do defined names, and not "air"
            if (!bn.equals(DynmapBlockState.AIR_BLOCK)) {
                Material mat = bs.getMaterial();
                String statename = "";
                for (net.minecraft.state.property.Property<?> p : bs.getProperties()) {
                    if (statename.length() > 0) {
                        statename += ",";
                    }
                    statename += p.getName() + "=" + bs.get(p).toString();
                }
                int lightAtten = bs.isOpaqueFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN) ? 15 : (bs.isTransparent(EmptyBlockView.INSTANCE, BlockPos.ORIGIN) ? 0 : 1);
                //Log.info("statename=" + bn + "[" + statename + "], lightAtten=" + lightAtten);
                // Fill in base attributes
                bld.setBaseState(basebs).setStateIndex(idx - baseidx).setBlockName(bn).setStateName(statename).setMaterial(mat.toString()).setLegacyBlockID(idx).setAttenuatesLight(lightAtten);
				if (mat.isSolid()) { bld.setSolid(); }
				if (mat == Material.AIR) { bld.setAir(); }
				if (mat == Material.WOOD) { bld.setLog(); }
				if (mat == Material.LEAVES) { bld.setLeaves(); }
				if ((!bs.getFluidState().isEmpty()) && !(bs.getBlock() instanceof FluidBlock)) {
					bld.setWaterlogged();
				}
                DynmapBlockState dbs = bld.build(); // Build state
                stateByID[idx] = dbs;
                if (basebs == null) { basebs = dbs; }
            }
        }
//        for (int gidx = 0; gidx < DynmapBlockState.getGlobalIndexMax(); gidx++) {
//            DynmapBlockState bs = DynmapBlockState.getStateByGlobalIndex(gidx);
//            Log.info(gidx + ":" + bs.toString() + ", gidx=" + bs.globalStateIndex + ", sidx=" + bs.stateIndex);
//        }
    }

    public static final Item getItemByID(int id) {
        return Item.byRawId(id);
    }

    FabricPlayer getOrAddPlayer(ServerPlayerEntity player) {
        String name = player.getName().getString();
        FabricPlayer fp = players.get(name);
        if (fp != null) {
            fp.player = player;
        } else {
            fp = new FabricPlayer(this, player);
            players.put(name, fp);
        }
        return fp;
    }

    static class ChatMessage {
        String message;
        ServerPlayerEntity sender;
    }

    ConcurrentLinkedQueue<ChatMessage> msgqueue = new ConcurrentLinkedQueue<ChatMessage>();

    public static class ChatHandler {
        private final DynmapPlugin plugin;

        ChatHandler(DynmapPlugin plugin) {
            this.plugin = plugin;
        }

        public void handleChat(ServerPlayerEntity player, String message) {
            if (!message.startsWith("/")) {
                ChatMessage cm = new ChatMessage();
                cm.message = message;
                cm.sender = player;
                plugin.msgqueue.add(cm);
            }
        }
    }

    public FabricServer getFabricServer() {
        return fserver;
    }

    private void serverStart(MinecraftServer server) {
        // Set the server so we don't NPE during setup
        this.server = server;
        this.fserver = new FabricServer(this, server);
        this.onEnable();
    }

    private void serverStarted(MinecraftServer server) {
        this.onStart();
        if (core != null) {
            core.serverStarted();
        }
    }

    private void serverStop(MinecraftServer server) {
        this.onDisable();
        this.server = null;
    }

    public boolean isOp(String player) {
        String[] ops = server.getPlayerManager().getOpList().getNames();

        for (String op : ops) {
            if (op.equalsIgnoreCase(player)) {
                return true;
            }
        }

        // TODO: Consider whether cheats are enabled for integrated server
        return server.isSingleplayer() && server.isHost(server.getPlayerManager().getPlayer(player).getGameProfile());
    }

    boolean hasPerm(PlayerEntity psender, String permission) {
        PermissionsHandler ph = PermissionsHandler.getHandler();
        if ((ph != null) && (psender != null) && ph.hasPermission(psender.getName().getString(), permission)) {
            return true;
        }
        return permissions.has(psender, permission);
    }

    boolean hasPermNode(PlayerEntity psender, String permission) {
        PermissionsHandler ph = PermissionsHandler.getHandler();
        if ((ph != null) && (psender != null) && ph.hasPermissionNode(psender.getName().getString(), permission)) {
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

    void setChatHandler(ChatHandler chatHandler) {
        plugin.chathandler = chatHandler;
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

        Registry<Biome> biomeRegistry = getFabricServer().getBiomeRegistry();
        Biome[] list = getFabricServer().getBiomeList(biomeRegistry);

        for (int i = 0; i < list.length; i++) {
            Biome bb = list[i];
            if (bb != null) {
                String id = biomeRegistry.getId(bb).getPath();
                String rl = biomeRegistry.getId(bb).toString();
                float tmp = bb.getTemperature(), hum = bb.weather.downfall();
                int watermult = ((BiomeEffectsAccessor) bb.getEffects()).getWaterColor();
                Log.verboseinfo("biome[" + i + "]: hum=" + hum + ", tmp=" + tmp + ", mult=" + Integer.toHexString(watermult));

                BiomeMap bmap = BiomeMap.NULL;
                if (rl != null) {	// If resource location, lookup by this
                	bmap = BiomeMap.byBiomeResourceLocation(rl);
                }
                else {
                	bmap = BiomeMap.byBiomeID(i);
                }
                if (bmap.isDefault() || (bmap == BiomeMap.NULL)) {
                    bmap = new BiomeMap((rl != null) ? BiomeMap.NO_INDEX : i, id, tmp, hum, rl);
                    Log.verboseinfo("Add custom biome [" + bmap.toString() + "] (" + i + ")");
                    cnt++;
                }
                else {
                    bmap.setTemperature(tmp);
                    bmap.setRainfall(hum);
                }
                if (watermult != -1) {
                    bmap.setWaterColorMultiplier(watermult);
                    Log.verboseinfo("Set watercolormult for " + bmap.toString() + " (" + i + ") to " + Integer.toHexString(watermult));
                }
                bmap.setBiomeObject(bb);
            }
        }
        if (cnt > 0)
            Log.info("Added " + cnt + " custom biome mappings");
    }

    private String[] getBiomeNames() {
        Registry<Biome> biomeRegistry = getFabricServer().getBiomeRegistry();
        Biome[] list = getFabricServer().getBiomeList(biomeRegistry);
        String[] lst = new String[list.length];
        for (int i = 0; i < list.length; i++) {
            Biome bb = list[i];
            if (bb != null) {
                lst[i] = biomeRegistry.getId(bb).getPath();
            }
        }
        return lst;
    }

    public void onEnable() {
        /* Get MC version */
        String mcver = server.getVersion();

        /* Load extra biomes */
        loadExtraBiomes(mcver);
        /* Set up player login/quit event handler */
        registerPlayerLoginListener();

        /* Initialize permissions handler */
        if (FabricLoader.getInstance().isModLoaded("luckperms")) {
            Log.info("Using luckperms for access control");
            permissions = new LuckPermissions();
        }
        else if (FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0")) {
            Log.info("Using fabric-permissions-api for access control");
            permissions = new FabricPermissions();
        } else {
            /* Initialize permissions handler */
            permissions = FilePermissions.create();
            if (permissions == null) {
                permissions = new OpPermissions(new String[]{"webchat", "marker.icons", "marker.list", "webregister", "stats", "hide.self", "show.self"});
            }
        }
        /* Get and initialize data folder */
        File dataDirectory = new File("dynmap");

        if (!dataDirectory.exists()) {
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

    private DynmapCommand dynmapCmd;
    private DmapCommand dmapCmd;
    private DmarkerCommand dmarkerCmd;
    private DynmapExpCommand dynmapexpCmd;

    public void registerCommands(CommandDispatcher<ServerCommandSource> cd) {
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
        perTickLimit = core.getMaxTickUseMS() * 1000000;
        // Prep TPS
        lasttick = System.nanoTime();
        tps = 20.0;

        /* Register tick handler */
        if (!tickregistered) {
            ServerTickEvents.END_SERVER_TICK.register(server -> fserver.tickEvent(server));
            tickregistered = true;
        }

        playerList = core.playerList;
        sscache = new GenericChunkCache(core.getSnapShotCacheSize(), core.useSoftRefInSnapShotCache());
        /* Get map manager from core */
        mapManager = core.getMapManager();

        /* Load saved world definitions */
        loadWorlds();

        for (FabricWorld w : worlds.values()) {
            if (core.processWorldLoad(w)) {   /* Have core process load first - fire event listeners if good load after */
                if (w.isLoaded()) {
                    core.listenerManager.processWorldEvent(DynmapListenerManager.EventType.WORLD_LOAD, w);
                }
            }
        }
        core.updateConfigHashcode();

        /* Register our update trigger events */
        registerEvents();
        Log.info("Register events");

        //DynmapCommonAPIListener.apiInitialized(core);

        Log.info("Enabled");
    }

    public void onDisable() {
        DynmapCommonAPIListener.apiTerminated();

        //if (metrics != null) {
        //	metrics.stop();
        //	metrics = null;
        //}
        /* Save worlds */
        saveWorlds();

        /* Purge tick queue */
        fserver.clearTaskQueue();

        /* Disable core */
        core.disableCore();
        core_enabled = false;

        if (sscache != null) {
            sscache.cleanup();
            sscache = null;
        }

        Log.info("Disabled");
    }

    // TODO: Clean a bit
    public void handleCommand(ServerCommandSource commandSource, String cmd, String[] args) throws CommandSyntaxException {
        DynmapCommandSender dsender;
        ServerPlayerEntity psender = null;

        // getPlayer throws a CommandSyntaxException, so getEntity and instanceof for safety
        if (commandSource.getEntity() instanceof ServerPlayerEntity) {
            psender = commandSource.getPlayerOrThrow();
        }

        if (psender != null) {
            // FIXME: New Player? Why not query the current player list.
            dsender = new FabricPlayer(this, psender);
        } else {
            dsender = new FabricCommandSender(commandSource);
        }

        core.processCommand(dsender, cmd, cmd, args);
    }

    public class PlayerTracker {
        public void onPlayerLogin(ServerPlayerEntity player) {
            if (!core_enabled) return;
            final DynmapPlayer dp = getOrAddPlayer(player);
            /* This event can be called from off server thread, so push processing there */
            core.getServer().scheduleServerTask(new Runnable() {
                public void run() {
                    core.listenerManager.processPlayerEvent(DynmapListenerManager.EventType.PLAYER_JOIN, dp);
                }
            }, 2);
        }

        public void onPlayerLogout(ServerPlayerEntity player) {
            if (!core_enabled) return;
            final DynmapPlayer dp = getOrAddPlayer(player);
            final String name = player.getName().getString();
            /* This event can be called from off server thread, so push processing there */
            core.getServer().scheduleServerTask(new Runnable() {
                public void run() {
                    core.listenerManager.processPlayerEvent(DynmapListenerManager.EventType.PLAYER_QUIT, dp);
                    players.remove(name);
                }
            }, 0);
        }

        public void onPlayerChangedDimension(ServerPlayerEntity player) {
            if (!core_enabled) return;
            getOrAddPlayer(player);    // Freshen player object reference
        }

        public void onPlayerRespawn(ServerPlayerEntity player) {
            if (!core_enabled) return;
            getOrAddPlayer(player);    // Freshen player object reference
        }
    }

    private PlayerTracker playerTracker = null;

    private void registerPlayerLoginListener() {
        if (playerTracker == null) {
            playerTracker = new PlayerTracker();
            PlayerEvents.PLAYER_LOGGED_IN.register(player -> playerTracker.onPlayerLogin(player));
            PlayerEvents.PLAYER_LOGGED_OUT.register(player -> playerTracker.onPlayerLogout(player));
            PlayerEvents.PLAYER_CHANGED_DIMENSION.register(player -> playerTracker.onPlayerChangedDimension(player));
            PlayerEvents.PLAYER_RESPAWN.register(player -> playerTracker.onPlayerRespawn(player));
        }
    }

    public class WorldTracker {
        public void handleWorldLoad(MinecraftServer server, ServerWorld world) {
            if (!core_enabled) return;

            final FabricWorld fw = getWorld(world);
            // This event can be called from off server thread, so push processing there
            core.getServer().scheduleServerTask(new Runnable() {
                public void run() {
                    if (core.processWorldLoad(fw))    // Have core process load first - fire event listeners if good load after
                        core.listenerManager.processWorldEvent(DynmapListenerManager.EventType.WORLD_LOAD, fw);
                }
            }, 0);
        }

        public void handleWorldUnload(MinecraftServer server, ServerWorld world) {
            if (!core_enabled) return;

            final FabricWorld fw = getWorld(world);
            if (fw != null) {
                // This event can be called from off server thread, so push processing there
                core.getServer().scheduleServerTask(new Runnable() {
                    public void run() {
                        core.listenerManager.processWorldEvent(DynmapListenerManager.EventType.WORLD_UNLOAD, fw);
                        core.processWorldUnload(fw);
                    }
                }, 0);
                // Set world unloaded (needs to be immediate, since it may be invalid after event)
                fw.setWorldUnloaded();
                // Clean up tracker
                //WorldUpdateTracker wut = updateTrackers.remove(fw.getName());
                //if(wut != null) wut.world = null;
            }
        }

        public void handleChunkGenerate(ServerWorld world, Chunk chunk) {
            if (!onchunkgenerate) return;

            FabricWorld fw = getWorld(world, false);
            ChunkPos chunkPos = chunk.getPos();

			int ymax = Integer.MIN_VALUE;
			int ymin = Integer.MAX_VALUE;
            ChunkSection[] sections = chunk.getSectionArray();
            for (int i = 0; i < sections.length; i++) {
                if ((sections[i] != null) && (!sections[i].isEmpty())) {
					int sy = sections[i].getYOffset();
					if (sy < ymin) ymin = sy;
					if ((sy+16) > ymax) ymax = sy + 16;
                }
            }
            if (ymax != Integer.MIN_VALUE) {
                mapManager.touchVolume(fw.getName(),
                        chunkPos.getStartX(), ymin, chunkPos.getStartZ(),
                        chunkPos.getEndX(), ymax, chunkPos.getEndZ(),
                        "chunkgenerate");
                //Log.info("New generated chunk detected at %s[%s]".formatted(fw.getName(), chunkPos.getStartPos()));
            }
        }

        public void handleBlockEvent(World world, BlockPos pos) {
            if (!core_enabled) return;
            if (!onblockchange) return;
            if (!(world instanceof ServerWorld)) return;

            BlockUpdateRec r = new BlockUpdateRec();
            r.w = world;
            FabricWorld fw = getWorld(world, false);
            if (fw == null) return;
            r.wid = fw.getName();
            r.x = pos.getX();
            r.y = pos.getY();
            r.z = pos.getZ();
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
        if (worldTracker == null)
            worldTracker = new WorldTracker();
        if (onchunkpopulate || onchunkgenerate) {
            CustomServerChunkEvents.CHUNK_GENERATE.register((world, chunk) -> worldTracker.handleChunkGenerate(world, chunk));
        }
        if (onblockchange) {
            BlockEvents.BLOCK_EVENT.register((world, pos) -> worldTracker.handleBlockEvent(world, pos));
        }

        ServerWorldEvents.LOAD.register((server, world) -> worldTracker.handleWorldLoad(server, world));
        ServerWorldEvents.UNLOAD.register((server, world) -> worldTracker.handleWorldUnload(server, world));
    }

    FabricWorld getWorldByName(String name) {
        return worlds.get(name);
    }

    FabricWorld getWorld(World w) {
        return getWorld(w, true);
    }

    private FabricWorld getWorld(World w, boolean add_if_not_found) {
        if (last_world == w) {
            return last_fworld;
        }
        String wname = FabricWorld.getWorldName(this, w);

        for (FabricWorld fw : worlds.values()) {
            if (fw.getRawName().equals(wname)) {
                last_world = w;
                last_fworld = fw;
                if (!fw.isLoaded()) {
                    fw.setWorldLoaded(w);
                }
                fw.updateWorld(w);
                return fw;
            }
        }
        FabricWorld fw = null;
        if (add_if_not_found) {
            /* Add to list if not found */
            fw = new FabricWorld(this, w);
            worlds.put(fw.getName(), fw);
        }
        last_world = w;
        last_fworld = fw;
        return fw;
    }

    private void saveWorlds() {
        File f = new File(core.getDataFolder(), FabricWorld.SAVED_WORLDS_FILE);
        ConfigurationNode cn = new ConfigurationNode(f);
        ArrayList<HashMap<String, Object>> lst = new ArrayList<HashMap<String, Object>>();
        for (DynmapWorld fw : core.mapManager.getWorlds()) {
            HashMap<String, Object> vals = new HashMap<String, Object>();
            vals.put("name", fw.getRawName());
            vals.put("height", fw.worldheight);
            vals.put("miny", fw.minY);
            vals.put("sealevel", fw.sealevel);
            vals.put("nether", fw.isNether());
            vals.put("the_end", ((FabricWorld) fw).isTheEnd());
            vals.put("title", fw.getTitle());
            lst.add(vals);
        }
        cn.put("worlds", lst);
        cn.put("useSaveFolderAsName", useSaveFolder);
        cn.put("maxWorldHeight", FabricWorld.getMaxWorldHeight());

        cn.save();
    }

    private void loadWorlds() {
        File f = new File(core.getDataFolder(), FabricWorld.SAVED_WORLDS_FILE);
        if (f.canRead() == false) {
            useSaveFolder = true;
            return;
        }
        ConfigurationNode cn = new ConfigurationNode(f);
        cn.load();
        // If defined, use maxWorldHeight
        FabricWorld.setMaxWorldHeight(cn.getInteger("maxWorldHeight", 256));

        // If setting defined, use it
        if (cn.containsKey("useSaveFolderAsName")) {
            useSaveFolder = cn.getBoolean("useSaveFolderAsName", useSaveFolder);
        }
        List<Map<String, Object>> lst = cn.getMapList("worlds");
        if (lst == null) {
            Log.warning(String.format("Discarding bad %s", FabricWorld.SAVED_WORLDS_FILE));
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
                    FabricWorld fw = new FabricWorld(this, name, height, sealevel, nether, theend, title, (miny != null) ? miny : 0);
                    fw.setWorldUnloaded();
                    core.processWorldLoad(fw);
                    worlds.put(fw.getName(), fw);
                }
            } catch (Exception x) {
                Log.warning(String.format("Unable to load saved worlds from %s", FabricWorld.SAVED_WORLDS_FILE));
                return;
            }
        }
    }
}