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

    private TownyConfigHandler towny;
    private String regiontype;
    
    public RegionsComponent(final DynmapPlugin plugin, final ConfigurationNode configuration) {
        super(plugin, configuration);
        
        // For internal webserver.
        String fname = configuration.getString("filename", "regions.yml");

        regiontype = configuration.getString("name", "WorldGuard");

        
        /* Load special handler for Towny */
        if(regiontype.equals("Towny")) {
            towny = new TownyConfigHandler(configuration);
            plugin.webServer.handlers.put("/standalone/towny_*", new RegionHandler(configuration));
        }
        else {
            plugin.webServer.handlers.put("/standalone/" + fname.substring(0, fname.lastIndexOf('.')) + "_*", new RegionHandler(configuration));
            
        }
        // For external webserver.
        //Parse region file for multi world style
        if (configuration.getBoolean("useworldpath", false)) {
            plugin.events.addListener("clientupdatewritten", new Event.Listener<ClientUpdateEvent>() {
                @Override
                public void triggered(ClientUpdateEvent t) {
                    World world = t.world.world;
                    parseRegionFile(world.getName(), world.getName() + "/" + configuration.getString("filename", "regions.yml"), configuration.getString("filename", "regions.yml").replace(".", "_" + world.getName() + ".yml"));
                }
            });
        } else {
            plugin.events.addListener("clientupdatewritten", new Event.Listener<ClientUpdateEvent>() {
                @Override
                public void triggered(ClientUpdateEvent t) {
                    World world = t.world.world;
                    parseRegionFile(world.getName(), configuration.getString("filename", "regions.yml"), configuration.getString("filename", "regions.yml").replace(".", "_" + world.getName() + ".yml"));
                }
            });
        }
    }

    //handles parsing and writing region json files
    private void parseRegionFile(String wname, String regionFile, String outputFileName)
    {
        File outputFile;
        org.bukkit.util.config.Configuration regionConfig = null;
        Map<?, ?> regionData;
        File webWorldPath;
        
        if(regiontype.equals("Towny")) {
            regionData = towny.getRegionData(wname);
            outputFileName = "towny_" + wname + ".json";
            webWorldPath = new File(plugin.getWebPath()+"/standalone/", outputFileName);
        }
        else {
            if(configuration.getBoolean("useworldpath", false))
            {
                if(new File("plugins/"+configuration.getString("name", "WorldGuard"), regionFile).exists())
                    regionConfig = new org.bukkit.util.config.Configuration(new File("plugins/"+regiontype, regionFile));
                else if(new File("plugins/"+regiontype+"/worlds", regionFile).exists())
                    regionConfig = new org.bukkit.util.config.Configuration(new File("plugins/"+regiontype+"/worlds", regionFile));
            }
            else
                regionConfig = new org.bukkit.util.config.Configuration(new File("plugins/"+regiontype, regionFile));
            //File didn't exist
            if(regionConfig == null)
                return;
            regionConfig.load();

            regionData = (Map<?, ?>) regionConfig.getProperty(configuration.getString("basenode", "regions"));
            outputFileName = outputFileName.substring(0, outputFileName.lastIndexOf("."))+".json";
            webWorldPath = new File(plugin.getWebPath()+"/standalone/", outputFileName);
        }
        /* See if we have explicit list of regions to report - limit to this list if we do */
        List<String> idlist = configuration.getStrings("visibleregions", null);
        List<String> hidlist = configuration.getStrings("hiddenregions", null);
        if((idlist != null) || (hidlist != null)) {
            @SuppressWarnings("unchecked")
            HashSet<String> ids = new HashSet<String>((Collection<? extends String>) regionData.keySet());
            for(String id : ids) {
                /* If include list defined, and we're not in it, remove */
                if((idlist != null) && (!idlist.contains(id))) {
                    regionData.remove(id);
                }
                /* If exclude list defined, and we're on it, remove */
                else if((hidlist != null) && (hidlist.contains(id))) {
                    /* If residence, we want to zap the areas list, so that we still get subregions */
                    if(regiontype.equals("Residence")) {
                        Map<?,?> m = (Map<?,?>)regionData.get(id);
                        if(m != null) {
                            Map<?,?> a = (Map<?,?>)m.get("Areas");
                            if(a != null) {
                                a.clear();
                            }
                        }
                    }
                    else {
                        regionData.remove(id);
                    }
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
