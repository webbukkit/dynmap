package org.dynmap.modsupport;

public interface TextureFile {
    /**
     * Get texture ID
     *
     * @return texture ID
     */
    String getTextureID();

    /**
     * Get texture file name
     *
     * @return texture file path and name
     */
    String getTextureFile();

    /**
     * Get horizontal dimension (xcount) of texture file, in standard square patches (16 x 16 default, unless scaled)
     *
     * @return xcount (horizontal dimension)
     */
    int getXCount();

    /**
     * Get vertical dimension (ycount) of texture file, in standard square patches (16 x 16 default, unless scaled)
     *
     * @return ycount (vertcal dimension)
     */
    int getYCount();

    /**
     * Get texture file format type
     *
     * @return format type
     */
    TextureFileType getFileType();

    /**
     * Get patch count for file (xcount x ycount for GRID, type specific counts for others).  Patches are from 0 to (count-1)
     *
     * @return patch count
     */
    int getPatchCount();
}
