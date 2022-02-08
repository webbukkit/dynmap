package org.dynmap.modsupport;

import java.util.List;
import java.util.Map;

/**
 * Record representing a texture mapping for one or more blocks
 */
public interface BlockTextureRecord {
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
     * Set transparency mode for block
     * @param mode - transparency mode
     */
    public void setTransparencyMode(TransparencyMode mode);
    /**
     * Get transparency mode for block
     * @return transparency mode
     */
    public TransparencyMode getTransparencyMode();
    /**
     * Set side texture (standard cube block model)
     * @param txtFileID - texture file ID (first texture in file used)
     * @param side - side to apply texture to
     */
    public void setSideTexture(String txtFileID, BlockSide side);
    /**
     * Set side texture (standard cube block model)
     * @param txtFile - texture file (first texture in file used)
     * @param side - side to apply texture to
     */
    public void setSideTexture(TextureFile txtFile, BlockSide side);
    /**
     * Set side texture (standard cube block model) with given texture index
     * @param txtFileID - texture file ID
     * @param txtIndex - texture index
     * @param side - side to apply texture to
     */
    public void setSideTexture(String txtFileID, int txtIndex, BlockSide side);
    /**
     * Set side texture (standard cube block model) with givcen texture index
     * @param txtFile - texture file
     * @param txtIndex - texture index
     * @param side - side to apply texture to
     */
    public void setSideTexture(TextureFile txtFile, int txtIndex, BlockSide side);
    /**
     * Set side texture (standard cube block model) with texture modifier
     * @param txtFileID - texture file ID (first texture in file used)
     * @param modifier - texture modifier
     * @param side - side to apply texture to
     */
    public void setSideTexture(String txtFileID, TextureModifier modifier, BlockSide side);
    /**
     * Set side texture (standard cube block model) with modifier
     * @param txtFile - texture file (first texture in file used)
     * @param modifier - texture modifier
     * @param side - side to apply texture to
     */
    public void setSideTexture(TextureFile txtFile, TextureModifier modifier, BlockSide side);
    /**
     * Set side texture (standard cube block model) with texture modifier and texture index
     * @param txtFileID - texture file ID
     * @param txtIndex - texture index
     * @param modifier - texture modifier
     * @param side - side to apply texture to
     */
    public void setSideTexture(String txtFileID, int txtIndex, TextureModifier modifier, BlockSide side);
    /**
     * Set side texture (standard cube block model) with texture modifier and texture index
     * @param txtFile - texture file
     * @param txtIndex - texture index
     * @param modifier - texture modifier
     * @param side - side to apply texture to
     */
    public void setSideTexture(TextureFile txtFile, int txtIndex, TextureModifier modifier, BlockSide side);
    /**
     * Set patch texture
     * @param txtFileID - texture file ID (first texture in file used)
     * @param patchIndex - patch index to apply texture to
     */
    public void setPatchTexture(String txtFileID, int patchIndex);
    /**
     * Set patch texture
     * @param txtFile - texture file (first texture in file used)
     * @param patchIndex - patch index to apply texture to
     */
    public void setPatchTexture(TextureFile txtFile, int patchIndex);
    /**
     * Set patch texture with given texture index
     * @param txtFileID - texture file ID
     * @param txtIndex - texture index
     * @param patchIndex - patch index to apply texture to
     */
    public void setPatchTexture(String txtFileID, int txtIndex, int patchIndex);
    /**
     * Set patch texture with givcen texture index
     * @param txtFile - texture file
     * @param txtIndex - texture index
     * @param patchIndex - patch index to apply texture to
     */
    public void setPatchTexture(TextureFile txtFile, int txtIndex, int patchIndex);
    /**
     * Set patch texture with texture modifier
     * @param txtFileID - texture file ID (first texture in file used)
     * @param modifier - texture modifier
     * @param patchIndex - patch index to apply texture to
     */
    public void setPatchTexture(String txtFileID, TextureModifier modifier, int patchIndex);
    /**
     * Set patch texture with modifier
     * @param txtFile - texture file (first texture in file used)
     * @param modifier - texture modifier
     * @param patchIndex - patch index to apply texture to
     */
    public void setPatchTexture(TextureFile txtFile, TextureModifier modifier, int patchIndex);
    /**
     * Set patch texture with texture modifier and texture index
     * @param txtFileID - texture file ID
     * @param txtIndex - texture index
     * @param modifier - texture modifier
     * @param patchIndex - patch index to apply texture to
     */
    public void setPatchTexture(String txtFileID, int txtIndex, TextureModifier modifier, int patchIndex);
    /**
     * Set patch texture with texture modifier and texture index
     * @param txtFile - texture file
     * @param txtIndex - texture index
     * @param modifier - texture modifier
     * @param patchIndex - patch index to apply texture to
     */
    public void setPatchTexture(TextureFile txtFile, int txtIndex, TextureModifier modifier, int patchIndex);
    /**
     * Get texture ID for given side
     * @param side - side
     * @return texture ID
     */
    public String getSideTextureID(BlockSide side);
    /**
     * Get texture modifier for given side
     * @param side - side
     * @return texture modifier
     */
    public TextureModifier getSideTextureModifier(BlockSide side);
    /**
     * Get texture index for given side
     * @param side - side
     * @return texture index
     */
    public int getSideTextureIndex(BlockSide side);
    /**
     * Get texture ID for given patch index
     * @param patchIndex - patch index
     * @return texture ID
     */
    public String getPatchTextureID(int patchIndex);
    /**
     * Get texture modifier for given patch index
     * @param patchIndex - patch index
     * @return texture modifier
     */
    public TextureModifier getPatchTextureModifier(int patchIndex);
    /**
     * Get texture index for given patch index
     * @param patchIndex - patch index
     * @return texture index
     */
    public int getPatchTextureIndex(int patchIndex);
    
    /**
     * Set block color map
     * @param txtFileID - texture file ID
     */
    public void setBlockColorMapTexture(String txtFileID);
    /**
     * Set block color map
     * @param txtFile - texture file
     */
    public void setBlockColorMapTexture(TextureFile txtFile);

}
