package org.dynmap.modsupport.impl;

import java.util.ArrayList;

import org.dynmap.modsupport.CustomTextureFile;
import org.dynmap.modsupport.TextureFileType;

public class CustomTextureFileImpl extends TextureFileImpl implements CustomTextureFile {
    
    private static class CustomPatch {
        int xpos;
        int ypos;
        int xdim;
        int ydim;
        int xdest;
        int ydest;
    };
    
    private ArrayList<CustomPatch> patches = new ArrayList<CustomPatch>();
    
    public CustomTextureFileImpl(String id, String filename, int xcount, int ycount) {
        super(id, filename, TextureFileType.GRID, xcount, ycount);
    }

    private void padList(int patchID) {
        while (patches.size() <= patchID) {
            patches.add(new CustomPatch());
        }
    }
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
    @Override
    public boolean setCustomPatch(int patchID, int xpos, int ypos, int xdim,
            int ydim, int xdest, int ydest) {
        padList(patchID);
        CustomPatch cp = patches.get(patchID);
        cp.xpos = xpos;
        cp.ypos = ypos;
        cp.xdim = xdim;
        cp.ydim = ydim;
        cp.xdest = xdest;
        cp.ydest = ydest;
        return true;
    }
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
    @Override
    public boolean setCustomPatch(int patchID, int xpos, int ypos, int xdim,
            int ydim) {
        return setCustomPatch(patchID, xpos, ypos, xdim, ydim, 0, 0);
    }
    
    public String getLine() {
        String s = super.getLine();
        s += ",format=CUSTOM";
        for (int i = 0; i < patches.size(); i++) {
            CustomPatch cp = patches.get(i);
            if (cp == null) continue;
            s += "tile" + i + "=" + cp.xpos + ":" + cp.ypos + "/" + cp.xdim + ":" + cp.ydim;
            if ((cp.xdest != 0) || (cp.ydest != 0)) {
                s += "/" + cp.xdest + ":" + cp.ydest;
            }
        }
        return s;
    }

}
