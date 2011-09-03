package org.dynmap;

import org.dynmap.markers.impl.MarkerAPIImpl;

/**
 * Markers component - ties in the component system, both on the server and client
 */
public class MarkersComponent extends ClientComponent {
    private MarkerAPIImpl api;
    public MarkersComponent(DynmapPlugin plugin, ConfigurationNode configuration) {
        super(plugin, configuration);
        /* Register API with plugin */
        api = MarkerAPIImpl.initializeMarkerAPI(plugin);
        plugin.registerMarkerAPI(api);
        
    }
    @Override
    public void dispose() {
        if(api != null) {
            /* Clean up API registered with plugin */
            plugin.registerMarkerAPI(null);
            api.cleanup(this.plugin);
            api = null;
        }
    }
}
