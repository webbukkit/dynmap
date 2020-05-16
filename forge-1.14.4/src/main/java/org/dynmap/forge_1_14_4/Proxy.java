package org.dynmap.forge_1_14_4;

import net.minecraft.server.MinecraftServer;

/**
 * Server side proxy - methods for creating and cleaning up plugin
 */
public class Proxy
{
    public Proxy()
    {
    }
	public DynmapPlugin startServer(MinecraftServer srv) {
	    DynmapPlugin plugin = DynmapPlugin.plugin; 
	    if (plugin == null) {
	        plugin = new DynmapPlugin(srv);
	        plugin.onEnable();
	    }
		return plugin;
	}
	public void stopServer(DynmapPlugin plugin) {
		plugin.onDisable();
	}
}
