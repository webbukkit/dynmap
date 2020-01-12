package org.dynmap.modsupport;

/**
 * Generic block model
 */
public interface BlockModel {
    int METAMASK_ALL = -1;

    /**
     * Add block ID to mapping (in case multiple block IDs use same model)
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
}
