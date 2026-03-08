package org.dynmap.neoforge_1_21_1;

import java.io.File;

import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.Log;
import org.dynmap.neoforge_1_21_1.DynmapPlugin.OurLog;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.IExtensionPoint;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("dynmap")
public class DynmapMod
{
    public static DynmapMod instance;
    public static Proxy proxy = new Proxy();
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
    
    public DynmapMod(IEventBus modEventBus) {
        instance = this;
        modEventBus.addListener(this::setup);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        Log.setLogger(new OurLog());
        org.dynmap.modsupport.ModSupportImpl.init();
    }
    
    public void setup(final FMLCommonSetupEvent event) {
        jarfile = ModList.get().getModFileById("dynmap").getFile().getFilePath().toFile();
        ver = ModList.get().getModContainerById("dynmap").get().getModInfo().getVersion().toString();
    }

    private MinecraftServer server;
    
    public void onServerStarting(ServerAboutToStartEvent event) {
        server = event.getServer();
        if(plugin == null)
            plugin = proxy.startServer(server);
        plugin.onStarting(server.getCommands().getDispatcher());
    }
    
    public void onServerStarted(ServerStartedEvent event) {
        DynmapCommonAPIListener.register(new APICallback());
        plugin.serverStarted();
    }

    public void onServerStopping(ServerStoppingEvent event) {
        proxy.stopServer(plugin);
        plugin = null;
    }
}
