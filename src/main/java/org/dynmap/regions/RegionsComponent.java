package org.dynmap.regions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.bukkit.World;
import org.dynmap.ClientComponent;
import org.dynmap.ClientUpdateEvent;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapPlugin;
import org.dynmap.Event;
import org.dynmap.Log;
import org.dynmap.web.Json;

public class RegionsComponent extends ClientComponent {

    public RegionsComponent(final DynmapPlugin plugin, final ConfigurationNode configuration) {
        super(plugin, configuration);
        
        // For internal webserver.
        String fname = configuration.getString("filename", "regions.yml");
        plugin.webServer.handlers.put("/standalone/" + fname.substring(0, fname.lastIndexOf('.')) + "_*", new RegionHandler(configuration));
        
        // For external webserver.
        //Parse region file for multi world style
        if (configuration.getBoolean("useworldpath", false)) {
            plugin.events.addListener("clientupdatewritten", new Event.Listener<ClientUpdateEvent>() {
                @Override
                public void triggered(ClientUpdateEvent t) {
                    World world = t.world.world;
                    parseRegionFile(world.getName() + "/" + configuration.getString("filename", "regions.yml"), configuration.getString("filename", "regions.yml").replace(".", "_" + world.getName() + ".yml"));
                }
            });
        } else {
            plugin.events.addListener("clientupdateswritten", new Event.Listener<Object>() {
                @Override
                public void triggered(Object t) {
                    parseRegionFile(configuration.getString("filename", "regions.yml"), configuration.getString("filename", "regions.yml"));
                }
            });
        }
    }

    //handles parsing and writing region json files
    private void parseRegionFile(String regionFile, String outputFileName)
    {
        File outputFile;
        org.bukkit.util.config.Configuration regionConfig = null;
        if(configuration.getBoolean("useworldpath", false))
        {
            if(new File("plugins/"+configuration.getString("name", "WorldGuard"), regionFile).exists())
                regionConfig = new org.bukkit.util.config.Configuration(new File("plugins/"+configuration.getString("name", "WorldGuard"), regionFile));
            else if(new File("plugins/"+configuration.getString("name", "WorldGuard")+"/worlds", regionFile).exists())
                regionConfig = new org.bukkit.util.config.Configuration(new File("plugins/"+configuration.getString("name", "WorldGuard")+"/worlds", regionFile));
        }
        else
            regionConfig = new org.bukkit.util.config.Configuration(new File("plugins/"+configuration.getString("name", "WorldGuard"), regionFile));
        //File didn't exist
        if(regionConfig == null)
            return;
        regionConfig.load();

        outputFileName = outputFileName.substring(0, outputFileName.lastIndexOf("."))+".json";

        File webWorldPath = new File(plugin.getWebPath()+"/standalone/", outputFileName);
        Map<?, ?> regionData = (Map<?, ?>) regionConfig.getProperty(configuration.getString("basenode", "regions"));
        /* See if we have explicit list of regions to report - limit to this list if we do */
        List<String> idlist = configuration.getStrings("visibleregions", null);
        if(idlist != null) {
            @SuppressWarnings("unchecked")
            HashSet<String> ids = new HashSet<String>((Collection<? extends String>) regionData.keySet());
            for(String id : ids) {
                /* If not in list, remove it */
                if(!idlist.contains(id)) {
                    regionData.remove(id);
                }
            }
        }
       
        if (webWorldPath.isAbsolute())
            outputFile = webWorldPath;
        else {
            outputFile = new File(plugin.getDataFolder(), webWorldPath.toString());
        }
        try {
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(Json.stringifyJson(regionData).getBytes());
            fos.close();
        } catch (FileNotFoundException ex) {
            Log.severe("Exception while writing JSON-file.", ex);
        } catch (IOException ioe) {
            Log.severe("Exception while writing JSON-file.", ioe);
        }
    }

}
