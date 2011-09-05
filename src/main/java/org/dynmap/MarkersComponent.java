package org.dynmap;

import org.dynmap.markers.impl.MarkerAPIImpl;
import org.dynmap.markers.impl.MarkerSignManager;

/**
 * Markers component - ties in the component system, both on the server and client
 */
public class MarkersComponent extends ClientComponent {
    private MarkerAPIImpl api;
    private MarkerSignManager signmgr;
    public MarkersComponent(DynmapPlugin plugin, ConfigurationNode configuration) {
        super(plugin, configuration);
        /* Register API with plugin */
        api = MarkerAPIImpl.initializeMarkerAPI(plugin);
        plugin.registerMarkerAPI(api);
        /* If configuration has enabled sign support, prime it too */
        if(configuration.getBoolean("enablesigns", true)) {
            signmgr = MarkerSignManager.initializeSignManager(plugin);
        }
    }
    @Override
    public void dispose() {
        if(signmgr != null) {
            MarkerSignManager.terminateSignManager(this.plugin);
            signmgr = null;
        }
        if(api != null) {
            /* Clean up API registered with plugin */
            plugin.registerMarkerAPI(null);
            api.cleanup(this.plugin);
            api = null;
        }
    }
}
