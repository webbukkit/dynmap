package org.dynmap.regions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.bukkit.util.config.Configuration;
import org.dynmap.ConfigurationNode;
import org.dynmap.Log;
import org.dynmap.web.HttpRequest;
import org.dynmap.web.HttpResponse;
import org.dynmap.web.Json;
import org.dynmap.web.handlers.FileHandler;

public class RegionHandler extends FileHandler {
    private ConfigurationNode regions;
    public RegionHandler(ConfigurationNode regions) {
        this.regions = regions;
    }
    @Override
    protected InputStream getFileInput(String path, HttpRequest request, HttpResponse response) {
        if(regions == null)
            return null;
        /* Right path? */
        if(path.endsWith(".json") == false)
            return null;

        String worldname = path.substring(0, path.lastIndexOf(".json"));
        Configuration regionConfig = null;
        File infile;
        String regionFile;
        
        /* If using worldpath, format is either plugins/<plugin>/<worldname>/<filename> OR 
         * plugins/<plugin>/worlds/<worldname>/<filename>
         */
        File basepath = new File("plugins", regions.getString("name", "WorldGuard"));
        if(basepath.exists() == false)
            return null;
        if(regions.getBoolean("useworldpath", false)) {
            regionFile = worldname + "/" + regions.getString("filename", "regions.yml");
            infile = new File(basepath, regionFile);
            if(!infile.exists()) {
                infile = new File(basepath, "worlds/" + regionFile);
            }
        }
        else {  /* Else, its plugins/<plugin>/<filename> */
            regionFile = regions.getString("filename", "regions.yml");
            infile = new File(basepath, regionFile);
        }
        if(infile.exists()) {
            regionConfig = new Configuration(infile);
        }
        //File didn't exist
        if(regionConfig == null)
            return null;
        regionConfig.load();
        /* Parse region data and store in MemoryInputStream */
        Map<?, ?> regionData = (Map<?, ?>) regionConfig.getProperty(regions.getString("basenode", "regions"));
        /* See if we have explicit list of regions to report - limit to this list if we do */
        List<String> idlist = regions.getStrings("visibleregions", null);
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
        try {
            ByteArrayOutputStream fos = new ByteArrayOutputStream();
            fos.write(Json.stringifyJson(regionData).getBytes());
            fos.close();
            return new ByteArrayInputStream(fos.toByteArray());
        } catch (FileNotFoundException ex) {
            Log.severe("Exception while writing JSON-file.", ex);
        } catch (IOException ioe) {
            Log.severe("Exception while writing JSON-file.", ioe);
        }        
        return null;
    }
}
