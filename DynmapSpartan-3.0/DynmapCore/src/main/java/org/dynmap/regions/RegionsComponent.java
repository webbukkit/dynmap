package org.dynmap.regions;

import org.dynmap.Component;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.Log;

public class RegionsComponent extends Component {
    private static String deprecated_ids[] = { "Residence", "Factions", "Towny", "WorldGuard" };
    private static String deprecated_new_plugins[] = { "dynmap-residence", "Dynmap-Factions", "Dynmap-Towny", "Dynmap-WorldGuard" };
    
    public RegionsComponent(final DynmapCore plugin, final ConfigurationNode configuration) {
        super(plugin, configuration);
        
        String regiontype = configuration.getString("name", "WorldGuard");
        /* Check if a deprecated component */
        for(int i = 0; i < deprecated_ids.length; i++) {
            if(regiontype.equals(deprecated_ids[i])) {  /* If match */
                Log.info("Region component for '" + regiontype + "' has been RETIRED - migrate to '" + deprecated_new_plugins[i] + "' plugin");
            }
        }
    }
}
