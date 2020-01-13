package org.dynmap.modsupport;

/**
 * Interface for custom texture files - provides methods needed to define custom patches within the texture file
 */
public interface CustomTextureFile extends TextureFile {
    /**
     * Set custom patch within texture file.  Coordinates assume that nominal dimensions of the texture file are 16*xcount wide and 16*ycount high.
     * @param patchID - ID of the patch within the file (must start from 0, and be consecutive)
     * @param xpos - horizontal position of top-left corner of the texture within the file (left column = 0, right column = (16*xcount - 1)
     * @param ypos - vertical positon of the top-left corner of the texture within the file (top row = 0, bottom row = (16*ycount - 1)
     * @param xdim - width of the patch, in scaled pixels
     * @param ydim - height of the patch, in scaled pixels
     * @param xdest - horizontal position within the destination patch of the top-left corner
     * @param ydest - vertical position within the destination oatch of the top-left corner
     * @return true if good patch, false if error
     */
    public boolean setCustomPatch(int patchID, int xpos, int ypos, int xdim, int ydim, int xdest, int ydest);
    /**
     * Set custom patch within texture file.  Coordinates assume that nominal dimensions of the texture file are 16*xcount wide and 16*ycount high.
     * Resulting patch is square with top-left corner of source in top-left corner of patch.
     * @param patchID - ID of the patch within the file (must start from 0, and be consecutive)
     * @param xpos - horizontal position of top-left corner of the texture within the file (left column = 0, right column = (16*xcount - 1)
     * @param ypos - vertical positon of the top-left corner of the texture within the file (top row = 0, bottom row = (16*ycount - 1)
     * @param xdim - width of the patch, in scaled pixels
     * @param ydim - height of the patch, in scaled pixels
     * @return true if good patch, false if error
     */
    public boolean setCustomPatch(int patchID, int xpos, int ypos, int xdim, int ydim);
    
}
