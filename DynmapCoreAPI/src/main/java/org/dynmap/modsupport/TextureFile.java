package org.dynmap.modsupport;

public interface TextureFile {
    /**
     * Get texture ID
     * @return texture ID
     */
    public String getTextureID();
    /**
     * Get texture file name
     * @return texture file path and name
     */
    public String getTextureFile();
    /**
     * Get horizontal dimension (xcount) of texture file, in standard square patches (16 x 16 default, unless scaled)
     * @return xcount (horizontal dimension)
     */
    public int getXCount();
    /**
     * Get vertical dimension (ycount) of texture file, in standard square patches (16 x 16 default, unless scaled)
     * @return ycount (vertcal dimension)
     */
    public int getYCount();
    /**
     * Get texture file format type
     * @return format type
     */
    public TextureFileType getFileType(); 
    /**
     * Get patch count for file (xcount x ycount for GRID, type specific counts for others).  Patches are from 0 to (count-1)
     * @return patch count
     */
    public int getPatchCount();
}
