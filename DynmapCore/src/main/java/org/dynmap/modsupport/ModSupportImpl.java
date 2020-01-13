package org.dynmap.modsupport;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.dynmap.Log;
import org.dynmap.modsupport.impl.ModModelDefinitionImpl;
import org.dynmap.modsupport.impl.ModTextureDefinitionImpl;

/**
 * Implementation of ModSupportAPI
 */
public class ModSupportImpl extends ModSupportAPI {
    private HashMap<String, ModTextureDefinitionImpl> txtDefsByModID = new HashMap<String, ModTextureDefinitionImpl>();
    
    /**
     * Initialize mod support API
     */
    public static void init() {
        if (ModSupportAPI.api != null)
            return;
        // Initialize API object
        ModSupportAPI.api = new ModSupportImpl();
        
        Log.info("Mod Support API available");
    }
    
    /**
     * Complete processing of mod support
     * @param datadir - Dynmap data directory
     */
    public static void complete(File datadir) {
        File renderdata = new File(datadir, "renderdata");
        File dynamicrenderdata = new File(renderdata, "modsupport");
        dynamicrenderdata.mkdirs();
        // Clean up anything in directory
        File[] files = dynamicrenderdata.listFiles();
        for (File f : files) {
            if (f.isFile())
                f.delete();
        }
        // If no API init, quit here
        if (ModSupportAPI.api == null) {
            return;
        }
        ModSupportImpl api_impl = (ModSupportImpl) ModSupportAPI.api;
        // Loop through texture definitions
        for (ModTextureDefinitionImpl tdi : api_impl.txtDefsByModID.values()) {
            boolean good = false;
            if (tdi.isPublished()) {
                Log.info("Processing mod support from mod " + tdi.getModID() + " version " + tdi.getModVersion());
                try {
                    tdi.writeToFile(dynamicrenderdata);
                    good = true;
                } catch (IOException iox) {
                    Log.warning("Error creating texture definition for mod " + tdi.getModID() + " version " + tdi.getModVersion());
                }
                ModModelDefinitionImpl mdi = (ModModelDefinitionImpl) tdi.getModelDefinition();
                if ((mdi != null) && mdi.isPublished() && good) {
                    try {
                        mdi.writeToFile(dynamicrenderdata);
                    } catch (IOException iox) {
                        Log.warning("Error creating model definition for mod " + mdi.getModID() + " version " + mdi.getModVersion());
                    }
                }
            }
            else {
                Log.warning("Unpublished mod support from mod " + tdi.getModID() + " version " + tdi.getModVersion() + " skipped");
            }
        }
        Log.info("Mod Support processing completed");
    }
    
    /**
     * Get texture definition object for calling mod
     * @param modid - Mod ID
     * @param modver - Mod version
     * @return texture definition to be populated for the mod
     */
    @Override
    public ModTextureDefinition getModTextureDefinition(String modid,
            String modver) {
        ModTextureDefinitionImpl mtd = txtDefsByModID.get(modid);
        if (mtd == null) {
            mtd = new ModTextureDefinitionImpl(modid, modver);
            txtDefsByModID.put(modid, mtd);
        }
        return mtd;
    }
}
