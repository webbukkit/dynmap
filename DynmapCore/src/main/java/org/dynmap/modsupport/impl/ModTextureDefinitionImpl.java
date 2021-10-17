package org.dynmap.modsupport.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.dynmap.modsupport.BigChestTextureFile;
import org.dynmap.modsupport.BiomeTextureFile;
import org.dynmap.modsupport.BlockTextureRecord;
import org.dynmap.modsupport.ChestTextureFile;
import org.dynmap.modsupport.CopyBlockTextureRecord;
import org.dynmap.modsupport.CustomTextureFile;
import org.dynmap.modsupport.GridTextureFile;
import org.dynmap.modsupport.ModModelDefinition;
import org.dynmap.modsupport.ModTextureDefinition;
import org.dynmap.modsupport.SignTextureFile;
import org.dynmap.modsupport.ShulkerTextureFile;
import org.dynmap.modsupport.SkinTextureFile;

/**
 * Implementation of mod texture definition
 */
public class ModTextureDefinitionImpl implements ModTextureDefinition {
    private final String modid;
    private final String modver;
    private ModModelDefinitionImpl modDef = null;
    private String texturePath;
    private LinkedHashMap<String, TextureFileImpl> txtFileByID = new LinkedHashMap<String, TextureFileImpl>();
    private ArrayList<BlockTextureRecordImpl> blkTextureRec = new ArrayList<BlockTextureRecordImpl>();
    private ArrayList<CopyBlockTextureRecordImpl> blkCopyTextureRec = new ArrayList<CopyBlockTextureRecordImpl>();
    private boolean published = false;
    
    public ModTextureDefinitionImpl(String modid, String modver) {
        this.modid = modid;
        this.modver = modver;
        // Default texture path
        this.texturePath = "assets/" + modid.toLowerCase() + "/textures/blocks/";
    }
    
    /**
     * Get mod ID
     * @return mod ID
     */
    @Override
    public String getModID() {
        return modid;
    }
    /**
     * Get mod version
     * @return mod version
     */
    @Override
    public String getModVersion() {
        return modver;
    }
    /**
     * Get model definition for mod associated with texture definition
     * Only needed if mod needs to define models for non-simple,solid blocks
     * @return model definition
     */
    @Override
    public ModModelDefinition getModelDefinition() {
        if (modDef == null) {
            modDef = new ModModelDefinitionImpl(this);
        }
        return modDef;
    }
    /**
     * Final call for texture definition: publishes definiiton to Dynmap to be used for the mod
     * @return true if successful, false if error
     */
    @Override
    public boolean publishDefinition() {
        published = true;
        return true;
    }

    /**
     * Set texture path for texture resources (base path for resource loads from mod - for 1.6.x, default is assets/&lt;modid&gt;/textures/blocks)
     * @param txtpath - texture resource base path
     */
    @Override
    public void setTexturePath(String txtpath) {
        this.texturePath = txtpath;
        if (this.texturePath.endsWith("/") == false) {
            this.texturePath += "/";
        }
        if (this.texturePath.startsWith("/")) {
            this.texturePath = this.texturePath.substring(1);
        }
    }

    /**
     * Get texture path for texture resources
     * @return texture resource base path
     */
    @Override
    public String getTexturePath() {
        return this.texturePath;
    }

    private TextureFileImpl registerTextureFile(TextureFileImpl tfi) {
        TextureFileImpl orig_tfi = txtFileByID.get(tfi.getTextureID());
        if (orig_tfi == null) {             // If new, add to table 
            txtFileByID.put(tfi.getTextureID(),  tfi);
        }
        else if (orig_tfi.equals(tfi)) {    // If matches existing, return original
            tfi = orig_tfi;
        }
        else {
            tfi = null;
        }
        return tfi;
    }
    
    private String getDefFilename(String id) {
        return texturePath + id + ".png";
    }
    /**
     * Register texture file
     * This is suitable for typical 1.5+ single texture-per-file textures.  File is assumed to be at -texturePath-/-id-.png
     * @param id - texture ID
     * @return TextureFile associated with resource
     */
    @Override
    public GridTextureFile registerTextureFile(String id) {
        return (GridTextureFile) registerTextureFile(new GridTextureFileImpl(id, getDefFilename(id), 1, 1));
    }
    /**
     * Register texture file with explicit file path and name (texturePath is not used)
     * This is suitable for typical 1.5+ single texture-per-file textures
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @return TextureFile associated with resource
     */
    @Override
    public GridTextureFile registerTextureFile(String id, String filename) {
        return (GridTextureFile) registerTextureFile(new GridTextureFileImpl(id, filename, 1, 1));
    }

    /**
     * Register texture file with CHEST layout (standard single chest texture)
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @return TextureFile associated with resource
     */
    @Override
    public ChestTextureFile registerChestTextureFile(String id, String filename) {
        return (ChestTextureFile) registerTextureFile(new ChestTextureFileImpl(id, filename));
    }
    /**
     * Register texture file with BIGCHEST layout (standard double chest texture)
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @return TextureFile associated with resource
     */
    @Override
    public BigChestTextureFile registerBigChestTextureFile(String id, String filename) {
        return (BigChestTextureFile) registerTextureFile(new BigChestTextureFileImpl(id, filename));
    }
    /**
     * Register texture file with SIGN layout (standard signpost texture )
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @return TextureFile associated with resource
     */
    @Override
    public SignTextureFile registerSignTextureFile(String id, String filename) {
        return (SignTextureFile) registerTextureFile(new SignTextureFileImpl(id, filename));
    }
    /**
     * Register texture file with SKIN layout (standard player/humanoid skin texture)
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @return TextureFile associated with resource
     */
    @Override
    public SkinTextureFile registerSkinTextureFile(String id, String filename) {
        return (SkinTextureFile) registerTextureFile(new SkinTextureFileImpl(id, filename));
    }
    /**
     * Register texture file with SHULKER layout (standard shulker and shulker box texture)
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @return TextureFile associated with resource
     */
    @Override
    public ShulkerTextureFile registerShulkerTextureFile(String id, String filename) {
        return (ShulkerTextureFile) registerTextureFile(new ShulkerTextureFileImpl(id, filename));
    }
    /**
     * Register texture file with GRID layout (array of xcount x ycount square textures)
     * @param id - texture ID
     * @param filename - texture file name (including .png)
     * @param xcount - horizontal patch count in texture file
     * @param ycount - vertical patch count in texture file
     * @return TextureFile associated with resource
     */
    @Override
    public GridTextureFile registerGridTextureFile(String id, String filename, int xcount, int ycount) {
        return (GridTextureFile) registerTextureFile(new GridTextureFileImpl(id, filename, xcount, ycount));
    }
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
    @Override
    public CustomTextureFile registerCustomTextureFile(String id, String filename, int xcount, int ycount) {
        return (CustomTextureFile) registerTextureFile(new CustomTextureFileImpl(id, filename, xcount, ycount));
    }
    @Override
    public BiomeTextureFile registerBiomeTextureFile(String id, String filename) {
        return (BiomeTextureFile) registerTextureFile(new BiomeTextureFileImpl(id, filename));
    }

    /**
     * Add block texture record : default assumes all metadata values are matching
     * @param blockID - block ID 
     * @return block texture record: use methods to set texture use on faces/patches
     */
    @Override
    public BlockTextureRecord addBlockTextureRecord(int blockID) {
        BlockTextureRecordImpl btr = new BlockTextureRecordImpl(blockID);
        blkTextureRec.add(btr);
        return btr;
    }
    /**
     * Add block texture record : default assumes all metadata values are matching
     * @param blockname - block name 
     * @return block texture record: use methods to set texture use on faces/patches
     */
    @Override
    public BlockTextureRecord addBlockTextureRecord(String blockname) {
        BlockTextureRecordImpl btr = new BlockTextureRecordImpl(blockname);
        blkTextureRec.add(btr);
        return btr;
    }
    
    @Override
    public CopyBlockTextureRecord addCopyBlockTextureRecord(int blockID,
            int srcBlockID, int srcMeta) {
        CopyBlockTextureRecordImpl btr = new CopyBlockTextureRecordImpl(blockID, srcBlockID, srcMeta);
        blkCopyTextureRec.add(btr);
        return btr;
    }
    @Override
    public CopyBlockTextureRecord addCopyBlockTextureRecord(String blockname,
            String srcBlockName, int srcMeta) {
        CopyBlockTextureRecordImpl btr = new CopyBlockTextureRecordImpl(blockname, srcBlockName, srcMeta);
        blkCopyTextureRec.add(btr);
        return btr;
    }

    public boolean isPublished() {
        return published;
    }
    
    public void writeToFile(File destdir) throws IOException {
        File f = new File(destdir, this.modid + "-texture.txt");
        FileWriter fw = null;
        try {
            fw = new FileWriter(f);
            // Write modname line
            String s = "modname:" + this.modid;
            fw.write(s + "\n\n");
            // Loop through textures
            for (String tid : txtFileByID.keySet()) {
            	TextureFileImpl tfi = txtFileByID.get(tid);
                String line = tfi.getLine();
                if (line != null) {
                    fw.write(line + "\n");
                }
            }
            // Loop through block texture records
            for (BlockTextureRecordImpl btr : blkTextureRec) {
                String line = btr.getLine();
                if (line != null) {
                    fw.write(line + "\n");
                }
            }
            // Loop through copy block texture records
            for (CopyBlockTextureRecordImpl btr : blkCopyTextureRec) {
                String line = btr.getLine();
                if (line != null) {
                    fw.write(line + "\n");
                }
            }
        } finally {
            if (fw != null) {
                fw.close(); 
            }
        }
    }
}
