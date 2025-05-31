package org.dynmap.neoforge_1_20_6;

import java.io.File;

import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.Log;
import org.dynmap.neoforge_1_20_6.DynmapPlugin.OurLog;

@Mod("dynmap")
public class DynmapMod {
	// The instance of your mod that NeoForge uses.
	public static DynmapMod instance;

	// Says where the client and server 'proxy' code is loaded.
	public static Proxy proxy;

	public static DynmapPlugin plugin;
	public static File jarfile;
	public static String ver;
	public static boolean useforcedchunks;

	public class APICallback extends DynmapCommonAPIListener {
		@Override
		public void apiListenerAdded() {
			if (plugin == null) {
				plugin = proxy.startServer(server);
			}
		}

		@Override
		public void apiEnabled(DynmapCommonAPI api) {
		}
	}

	// TODO
	// public class LoadingCallback implements
	// 		net.minecraftforge.common.ForgeChunkManager.LoadingCallback {
	// 	@Override
	// 	public void ticketsLoaded(List<Ticket> tickets, World world) {
	// 		if (tickets.size() > 0) {
	// 			DynmapPlugin.setBusy(world, tickets.get(0));
	// 			for (int i = 1; i < tickets.size(); i++) {
	// 				ForgeChunkManager.releaseTicket(tickets.get(i));
	// 			}
	// 		}
	// 	}
	// }

	public DynmapMod() {
		instance = this;

		if (FMLEnvironment.dist == Dist.CLIENT) {
			proxy = new ClientProxy();
		} else {
			proxy = new Proxy();
		}

		ModLoadingContext.get().getActiveContainer().getEventBus().addListener(this::setup);
		ModLoadingContext.get().getActiveContainer().getEventBus().addListener(this::init);

		NeoForge.EVENT_BUS.register(this);

		// NeoForge removed DisplayTest, with no current replacement.
		// A replacement may arrive in a future networking rework
		// ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
		// 		() -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY,
		// 				(remote, isServer) -> true));

		Log.setLogger(new OurLog());
		org.dynmap.modsupport.ModSupportImpl.init();
	}

	public void setup(final FMLCommonSetupEvent event) {
		// TOOO
		jarfile = ModList.get().getModFileById("dynmap").getFile().getFilePath().toFile();

		ver = ModList.get().getModContainerById("dynmap").get().getModInfo().getVersion().toString();

		// // Load configuration file - use suggested (config/WesterosBlocks.cfg)
		// Configuration cfg = new Configuration(event.getSuggestedConfigurationFile());
		// try {
		// 	cfg.load();

		// 	useforcedchunks = cfg.get("Settings", "UseForcedChunks",
		// 			// true).getBoolean(true);
		// } finally {
		// 	cfg.save();
		// }
	}

	public void init(FMLLoadCompleteEvent event) {
		/* Set up for chunk loading notice from chunk manager */
		// TODO
		// if (useforcedchunks) {
		// 	ForgeChunkManager.setForcedChunkLoadingCallback(DynmapMod.instance, new LoadingCallback());
		// } else {
		// 	Log.info("[Dynmap] World loading using forced chunks is disabled");
		// }
	}

	private MinecraftServer server;

	@SubscribeEvent
	public void onServerStarting(ServerAboutToStartEvent event) {
		server = event.getServer();
		if (plugin == null)
			plugin = proxy.startServer(server);
		plugin.onStarting(server.getCommands().getDispatcher());
	}

	@SubscribeEvent
	public void onServerStarted(ServerStartedEvent event) {
		DynmapCommonAPIListener.register(new APICallback());
		plugin.serverStarted();
	}

	@SubscribeEvent
	public void serverStopping(ServerStoppingEvent event) {
		proxy.stopServer(plugin);
		plugin = null;
	}
}
