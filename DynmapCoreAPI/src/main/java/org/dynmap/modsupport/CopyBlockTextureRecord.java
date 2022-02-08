package org.dynmap.modsupport;

import java.util.List;
import java.util.Map;

/**
 * Record representing a texture mapping for one or more blocks, based on copying an existing one
 */
public interface CopyBlockTextureRecord {
    public static final int METAMASK_ALL = -1;
    
    /**
     * Add block ID to mapping (in case multiple block IDs use same texture mapping)
     * @param blockID - block ID
     */
    @Deprecated
    public void addBlockID(int blockID);
    /**
     * Get block IDs
     * @return configured IDs
     */
    @Deprecated
    public int[] getBlockIDs();
    /**
     * Add block name to mapping (in case multiple block names use same model)
     * @param blockname - block name
     */
    public void addBlockName(String blockname);
    /**
     * Get block names
     * @return configured names
     */
    public String[] getBlockNames();
    /**
     * Set metadata value : default is for all values (data=*).  Setting other values will match only the values that are set
     * @param data - value to match (-1 = all, 0-15 is meta value to match)
     */
    @Deprecated
    public void setMetaValue(int data);
    /**
     * Get matching metadata value mask
     * @return matching metadata mask: bit N is set if given metadata value matches
     */
    @Deprecated
    public int getMetaValueMask();
    /**
     * Get source block ID
     * @return source block ID 
     */
    @Deprecated
    public int getSourceBlockID();
    /**
     * Get source metadata
     * @return souce meta ID
     */
    @Deprecated
    public int getSourceMeta();
    /**
     * Set matching block state mapping
     * Any key-value pairs included must match, while any not included are assumed to match unconditionall
     * @param statemap - map of attribute value pairs
     */
    public void setBlockStateMapping(Map<String, String> statemap);
    /**
     * Get all state mappings accumulated for the block model
     */
    public List<Map<String, String>> getBlockStateMappings();
    /**
     * Get source block name
     */
    public String getSourceBlockName();
    /**
     * Get source block state map
     */
    public Map<String, String> getSourceBlockStateMapping();
    /**
     * Set transparency mode for block
     * @param mode - transparency mode
     */
    public void setTransparencyMode(TransparencyMode mode);
    /**
     * Get transparency mode for block
     * @return transparency mode
     */
    public TransparencyMode getTransparencyMode();
}
