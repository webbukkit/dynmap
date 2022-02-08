package org.dynmap.modsupport;

import java.util.Map;

/**
 * Interface for texture definition for a given mod
 */
public interface ModTextureDefinition {
    /**
     * Get mod ID
     * @return mod ID
     */
    public String getModID();
    /**
     * Get mod version
     * @return mod version
     */
    public String getModVersion();
    /**
     * Get model definition for mod associated with texture definition
     * Only needed if mod needs to define models for non-simple,solid blocks
     * @return model definition
     */
    public ModModelDefinition getModelDefinition();
    /**
     * Final call for texture definition: publishes definiiton to Dynmap to be used for the mod
     * @return true if successful, false if error
     */
    public boolean publishDefinition();
    
    /**
     * Set texture path for texture resources (base path for resource loads from mod - for 1.6.x, default is assets/&lt;modid&gt;/textures/blocks)
     * @param txtpath - texture resource base path
     */
    public void setTexturePath(String txtpath);
    /**
     * Get texture path for texture resources
     * @return texture resource base path
     */
    public String getTexturePath();
    
    /**
     * Register texture file
     * This is suitable for typical 1.5+ single texture-per-file textures.  File is assumed to be at -texturePath-/-id-.png
     * @param id - texture ID
     * @return TextureFile associated with resource
     */
    public GridTextureFile registerTextureFile(String id);
    /**
     * Register texture file with explicit file path and name (texturePath is not used)
     * This is suitable for typical 1.5+ single texture-per-file textures
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @return TextureFile associated with resource
     */
    public GridTextureFile registerTextureFile(String id, String filename);
    /**
     * Register texture file with CHEST layout (standard single chest texture)
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @return TextureFile associated with resource
     */
    public ChestTextureFile registerChestTextureFile(String id, String filename);
    /**
     * Register texture file with BIGCHEST layout (standard double chest texture)
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @return TextureFile associated with resource
     */
    public BigChestTextureFile registerBigChestTextureFile(String id, String filename);
    /**
     * Register texture file with SIGN layout (standard signpost texture )
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @return TextureFile associated with resource
     */
    public SignTextureFile registerSignTextureFile(String id, String filename);
    /**
     * Register texture file with SKIN layout (standard player/humanoid skin texture)
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @return TextureFile associated with resource
     */
    public SkinTextureFile registerSkinTextureFile(String id, String filename);
    /**
     * Register texture file with SHULKER layout (standard shulker and shulker box texture)
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @return TextureFile associated with resource
     */
    public ShulkerTextureFile registerShulkerTextureFile(String id, String filename);
    /**
     * Register texture file with GRID layout (array of xcount x ycount square textures)
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @param xcount - horizontal patch count in texture file
     * @param ycount - vertical patch count in texture file
     * @return TextureFile associated with resource
     */
    public GridTextureFile registerGridTextureFile(String id, String filename, int xcount, int ycount);
    /** 
     * Register texture file with CUSTOM layout (custom defined patches within file)
     * The xcount and ycount attributes indicate the default horizontal and vertical dimensions of the texture file, assuming normal default
     * scale of 16 x 16 pixels for a texture patch (if the file is bigger, these data allow calculation of the texture scale)
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @param xcount - horizontal patch count in texture file
     * @param ycount - vertical patch count in texture file
     * @return CustomTextureFile associated with resource: use methods on this to define the custom patches within the file
     */
    public CustomTextureFile registerCustomTextureFile(String id, String filename, int xcount, int ycount);
    /**
     * Register texture file with BIOME layout
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @return TextureFile associated with resource
     */
    public BiomeTextureFile registerBiomeTextureFile(String id, String filename);
    
    /**
     * Add block texture record : default assumes all metadata values are matching
     * @param blockID - block ID 
     * @return block texture record: use methods to set texture use on faces/patches
     */
    @Deprecated
    public BlockTextureRecord addBlockTextureRecord(int blockID);
    /**
     * Add block texture record : default assumes all metadata values are matching
     * @param blockname - block name 
     * @return block texture record: use methods to set texture use on faces/patches
     */
    public BlockTextureRecord addBlockTextureRecord(String blockname);
    /**
     * Add block texture record, based on copying a record : default assumes all metadata values are matching
     * @param blockID - block ID 
     * @param srcBlockID - source block ID (definition copied from)
     * @param srcMeta - source meta (definition copied from)
     * @return block texture record: use methods to set texture use on faces/patches
     */
    @Deprecated
    public CopyBlockTextureRecord addCopyBlockTextureRecord(int blockID, int srcBlockID, int srcMeta);
    /**
     * Add block texture record, based on copying a record : default assumes all metadata values are matching
     * @param blockname - block name 
     * @param srcBlockname - source block name (definition copied from)
     * @param srcMeta - source meta (definition copied from)
     * @return block texture record: use methods to set texture use on faces/patches
     */
    @Deprecated
    public CopyBlockTextureRecord addCopyBlockTextureRecord(String blockname, String srcBlockname, int srcMeta);
    /**
     * Add block texture record, based on copying a record : default assumes all state values match
     * @param blockname - block name 
     * @param srcBlockname - source block name (definition copied from)
     * @param srcStateMap - source block state mapping (definition copied from)
     * @return block texture record: use methods to set texture use on faces/patches
     */
    public CopyBlockTextureRecord addCopyBlockTextureRecord(String blockname, String srcBlockname, Map<String,String> srcStateMap);
}
