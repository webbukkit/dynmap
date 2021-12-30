package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;

import org.dynmap.Log;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

/**
 * This renderer is used to define a model using a set of 1 or more patches (patch0=x, patch1=x - same syntax as patchblock),
 * which can then be rotated based on an orientation mapping.  This assumes the orientation is controlled by a numeric
 * field - either the field in the block's TileEntity defined by the 'index' attribute value, or the block
 * data/meta value (if index is not provided).  The rotations are then defined by providing a 'rotN' attribute
 * value (where N is the value of the rotation attribute that indicates the rotation is to be applied), with a value
 * formatted as 'X/Y/Z' or as 'Y', where X=degrees counterclockwise around X axis, Y=degrees counterclockwise around Y axis,
 * and Z=degrees counterclockwise around Z axis.  Note: X/Y/Z rotations are always applied in the order X, then Y, then Z.  If
 * the rotation index value N does not match a corresponding 'rotN' setting, no rotation is assumed.
 */
public class RotatedPatchRenderer extends CustomRenderer {
    // Default model
    private RenderPatch[] basemodel;
    // Models for rotation values
    private RenderPatch[][] models;
    // Highest texture index
    private int maxTextureIndex = 0;
    // Indexing attribute
    private String idx_attrib = null;
    
    private String[] tileEntityAttribs = null;

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        ArrayList<RenderPatch> patches = new ArrayList<RenderPatch>();
        ArrayList<int[]> rotations = new ArrayList<int[]>();
        /* See if index attribute defined */
        idx_attrib = custparm.get("index");
        /* Now, traverse parameters */
        for(String k : custparm.keySet()) {
            String v = custparm.get(k);
            /* If it is a patch definition */
            if(k.startsWith("patch")) {
                try {
                    int id = Integer.parseInt(k.substring(5));
                    RenderPatch p = rpf.getNamedPatch(custparm.get(k), id);
                    if(p == null) {
                        Log.warning("Invalid patch definition: " + v);
                        return false;
                    }
                    if(p.getTextureIndex() > maxTextureIndex) {
                        maxTextureIndex = p.getTextureIndex();
                    }
                    while(patches.size() <= id) {
                        patches.add(null);
                    }
                    patches.set(id, p);
                } catch (NumberFormatException nfx) {
                    Log.warning("Invalid index for parameter: " + k);
                    return false;
                }
            }
            /* If it is a rotation definition */
            else if(k.startsWith("rot")) {
                int id;
                try {
                    id = Integer.parseInt(k.substring(3));
                } catch (NumberFormatException nfx) {
                    Log.warning("Invalid index for parameter: " + k);
                    return false;
                }
                int[] rot = new int[3];
                String[] rotvals = v.split("/");
                try {
                    if(rotvals.length >= 3) {
                        rot[0] = Integer.parseInt(rotvals[0]);
                        rot[1] = Integer.parseInt(rotvals[1]);
                        rot[2] = Integer.parseInt(rotvals[2]);
                    }
                    else {
                        rot[1] = Integer.parseInt(rotvals[0]);
                    }
                    while(rotations.size() <= id) {
                        rotations.add(null);
                    }
                    rotations.set(id, rot);
                } catch (NumberFormatException nfx) {
                    Log.warning("Invalid rotation value: " + v);
                    return false;
                }
            }
        }
        /* Remove missing patches */
        for(int i = 0; i < patches.size(); i++) {
            if(patches.get(i) == null) {
                patches.remove(i);
                i--;
            }
        }
        /* Save patch list as base model */
        basemodel = patches.toArray(new RenderPatch[patches.size()]);
        /* Now build rotated models for all the defined rotations */
        models = new RenderPatch[rotations.size()][];
        for(int i = 0; i < rotations.size(); i++) {
            int[] rots = rotations.get(i);
            if (rots == null) continue;  /* Skip default values */
            models[i] = new RenderPatch[basemodel.length];  /* Build list of patches */
            for(int j = 0; j < basemodel.length; j++) {
                models[i][j] = rpf.getRotatedPatch(basemodel[j], rots[0], rots[1], rots[2], basemodel[j].getTextureIndex());
            }
        }
        if(idx_attrib != null) {
            tileEntityAttribs = new String[1];
            tileEntityAttribs[0] = idx_attrib;
        }
        else {
            tileEntityAttribs = null;
        }
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return maxTextureIndex+1;
    }
    
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return tileEntityAttribs;
    }
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int rotIdx = 0;
        
        /* See if we have rotation index */
        if(idx_attrib != null) {
            Object idxv = ctx.getBlockTileEntityField(idx_attrib);
            if(idxv instanceof Number) {
                rotIdx = ((Number)idxv).intValue();
            }
        }
        else {  /* Else, use data if no index attribute */
            rotIdx = ctx.getBlockType().stateIndex;
        }
        if((rotIdx >= 0) && (rotIdx < models.length) && (models[rotIdx] != null)) {
            return models[rotIdx];
        }
        else {
            return basemodel;
        }
    }
    @Override
    public boolean isOnlyBlockStateSensitive() {
    	return idx_attrib == null;
    }
}
