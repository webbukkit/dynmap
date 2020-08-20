package org.dynmap.forge_1_14_4;

import java.io.File;

import org.apache.commons.lang3.tuple.Pair;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.Log;
import org.dynmap.forge_1_14_4.DynmapPlugin.OurLog;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.FMLNetworkConstants;

@Mod("dynmap")
public class DynmapMod
{
    // The instance of your mod that Forge uses.
    public static DynmapMod instance;

    // Says where the client and server 'proxy' code is loaded.
    public static Proxy proxy = DistExecutor.runForDist(() -> ClientProxy::new, () -> Proxy::new);
    
    public static DynmapPlugin plugin;
    public static File jarfile;
    public static String ver;
    public static boolean useforcedchunks;

    public class APICallback extends DynmapCommonAPIListener {
        @Override
        public void apiListenerAdded() {
            if(plugin == null) {
                plugin = proxy.startServer(server);
            }
        }
        @Override
        public void apiEnabled(DynmapCommonAPI api) {
        }
    } 
    
    //TODO
    //public class LoadingCallback implements net.minecraftforge.common.ForgeChunkManager.LoadingCallback {
    //    @Override
    //    public void ticketsLoaded(List<Ticket> tickets, World world) {
    //        if(tickets.size() > 0) {
    //            DynmapPlugin.setBusy(world, tickets.get(0));
    //            for(int i = 1; i < tickets.size(); i++) {
    //                ForgeChunkManager.releaseTicket(tickets.get(i));
    //            }
    //        }
    //    }
    //}

    public DynmapMod() {
    	instance = this;
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::init);

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));

        Log.setLogger(new OurLog());      
        org.dynmap.modsupport.ModSupportImpl.init();
    }
    
    public void setup(final FMLCommonSetupEvent event)
    {
    	//TOOO
        jarfile = ModList.get().getModFileById("dynmap").getFile().getFilePath().toFile();

        ver = ModList.get().getModContainerById("dynmap").get().getModInfo().getVersion().toString();

        //// Load configuration file - use suggested (config/WesterosBlocks.cfg)
        //Configuration cfg = new Configuration(event.getSuggestedConfigurationFile());
        //try {
        //    cfg.load();
        //    
        //    useforcedchunks = cfg.get("Settings",  "UseForcedChunks", true).getBoolean(true);
        //}
        //finally
        //{
        //    cfg.save();
        //}
    }

    public void init(FMLLoadCompleteEvent event)
    {
        /* Set up for chunk loading notice from chunk manager */
    	//TODO
        //if(useforcedchunks) {
        //    ForgeChunkManager.setForcedChunkLoadingCallback(DynmapMod.instance, new LoadingCallback());
        //}
        //else {
        //    System.out.println("[Dynmap] World loading using forced chunks is disabled");
        //}
    }

    private MinecraftServer server;
    
    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        server = event.getServer();
        if(plugin == null)
            plugin = proxy.startServer(server);
		plugin.onStarting(event.getCommandDispatcher());
	}
    
    @SubscribeEvent
    public void onServerStarted(FMLServerStartedEvent event) {
        DynmapCommonAPIListener.register(new APICallback()); 
        plugin.serverStarted();
    }

    @SubscribeEvent
    public void serverStopping(FMLServerStoppingEvent event)
    {
    	proxy.stopServer(plugin);
    	plugin = null;
    }
}
