package org.dynmap.modsupport;

/**
 * Record representing a texture mapping for one or more blocks, based on copying an existing one
 */
public interface CopyBlockTextureRecord {
    int METAMASK_ALL = -1;

    /**
     * Add block ID to mapping (in case multiple block IDs use same texture mapping)
     *
     * @param blockID - block ID
     */
    void addBlockID(int blockID);

    /**
     * Get block IDs
     *
     * @return configured IDs
     */
    int[] getBlockIDs();

    /**
     * Add block name to mapping (in case multiple block names use same model)
     *
     * @param blockname - block name
     */
    void addBlockName(String blockname);

    /**
     * Get block names
     *
     * @return configured names
     */
    String[] getBlockNames();

    /**
     * Set metadata value : default is for all values (data=*).  Setting other values will match only the values that are set
     *
     * @param data - value to match (-1 = all, 0-15 is meta value to match)
     */
    void setMetaValue(int data);

    /**
     * Get matching metadata value mask
     *
     * @return matching metadata mask: bit N is set if given metadata value matches
     */
    int getMetaValueMask();

    /**
     * Get source block ID
     *
     * @return source block ID
     */
    int getSourceBlockID();

    /**
     * Get source metadata
     *
     * @return souce meta ID
     */
    int getSourceMeta();

    /**
     * Set transparency mode for block
     *
     * @param mode - transparency mode
     */
    void setTransparencyMode(TransparencyMode mode);

    /**
     * Get transparency mode for block
     *
     * @return transparency mode
     */
    TransparencyMode getTransparencyMode();
}
