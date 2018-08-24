package org.dynmap.forge_1_8_9;

/**
 * Server side proxy - methods for creating and cleaning up plugin
 */
public class Proxy
{
    public Proxy()
    {
    }
	public DynmapPlugin startServer() {
	    DynmapPlugin plugin = DynmapPlugin.plugin; 
	    if (plugin == null) {
	        plugin = new DynmapPlugin();
	        plugin.onEnable();
	    }
		return plugin;
	}
	public void stopServer(DynmapPlugin plugin) {
		plugin.onDisable();
	}
}
