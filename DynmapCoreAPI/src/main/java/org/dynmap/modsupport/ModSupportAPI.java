package org.dynmap.modsupport;

/**
 * API interface for mods to publish block rendering details to Dynmap
 */
public abstract class ModSupportAPI {
    protected static ModSupportAPI api = null;
    /**
     * Get access to mod support API
     * Call and use before postInit()
     * @return API interface
     */
    public static ModSupportAPI getAPI() {
        return api;
    }
    /**
     *  Get texture definition object for calling mod
     * @param modid - Mod ID
     * @param modver - Mod version
     * @return texture definition to be populated for the mod
     */
    public abstract ModTextureDefinition getModTextureDefinition(String modid, String modver);
}
