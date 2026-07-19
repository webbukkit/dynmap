package org.dynmap.modsupport.impl;

import org.dynmap.modsupport.TextureFile;
import org.dynmap.modsupport.TextureFileType;

public abstract class TextureFileImpl implements TextureFile {
    private final String txtID;
    private final String txtFileName;
    private final int xCount;
    private final int yCount;
    private final TextureFileType type;
    
    protected TextureFileImpl(String txtID, String txtFileName, TextureFileType type, int xCount, int yCount) {
        this.txtID = txtID;
        this.txtFileName = txtFileName;
        this.xCount = xCount;
        this.yCount = yCount;
        this.type = type;
    }
    
    public boolean equals(TextureFileImpl tfi) {
        return txtID.equals(tfi.txtID) && txtFileName.equals(tfi.txtFileName) && (xCount == tfi.xCount) && (yCount == tfi.yCount) && (type == tfi.type); 
    }
    /**
     * Get texture ID
     * @return texture ID
     */
    @Override
    public String getTextureID() {
        return txtID;
    }
    /**
     * Get texture file name
     * @return texture file path and name
     */
    @Override
    public String getTextureFile() {
        return txtFileName;
    }
    /**
     * Get horizontal dimension (xcount) of texture file, in standard square patches (16 x 16 default, unless scaled)
     * @return xcount (horizontal dimension)
     */
    @Override
    public int getXCount() {
        return xCount;
    }
    /**
     * Get vertical dimension (ycount) of texture file, in standard square patches (16 x 16 default, unless scaled)
     * @return ycount (vertcal dimension)
     */
    @Override
    public int getYCount() {
        return yCount;
    }
    /**
     * Get texture file format type
     * @return format type
     */
    @Override
    public TextureFileType getFileType() {
        return type;
    }
    /**
     * Get patch count for file (xcount x ycount for GRID, type specific counts for others).  Patches are from 0 to (count-1)
     * @return patch count
     */
    @Override
    public int getPatchCount() {
        return xCount * yCount;
    }

    public String getLine() {
        String s = "texture:id=" + this.txtID + ",filename=" + this.txtFileName;
        s += ",xcount=" + this.xCount + ",ycount=" + this.yCount;
        return s;
    }
}
