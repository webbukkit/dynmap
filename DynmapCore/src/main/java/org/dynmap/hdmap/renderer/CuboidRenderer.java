package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;

import org.dynmap.Log;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;

/**
 * Renderer for allowing creation of a set of one or more cuboid or crossed-patch blocks, with independent textures for each
 * Each cuboid is provided with two corner offsets within the cube (xmin,ymin,zmin) and (xmax,ymax,zmax), and a list of 6 patch indexes (default is 0,1,2,3,4,5 - order is bottom,top,xmin,xmax,zmin,zmax)
 * Each crossed-patch is provided with two corner offsets within the cube (xmin,ymin,zmin) and (xmax,ymax,zmax), and a single patch index (default is 0)
 */
public class CuboidRenderer extends CustomRenderer {
    private RenderPatch[][] models;
    private int textureCount;

    private static final int[] crossedPatchDefault = { 0 };
    private static final int[] cuboidPatchDefault = { 0, 1, 2, 3, 4, 5 };
    
    private static final double clamp(double f) {
        if (f < 0.0) { f = 0.0; }
        if (f > 1.0) { f = 1.0; }
        return f;
    }
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        // Loop through parameters
        for (String key : custparm.keySet()) {
            String v = custparm.get(key);
            if (key.startsWith("cuboid") || key.startsWith("cross")) {
                boolean isCrossed = key.startsWith("cross");
                String[] toks = v.split("/");
                if (toks.length < 2) { // Must be at least two
                    Log.severe("Invalid cuboid token value: " + v);
                    return false;
                }
                String[] mins = toks[0].split(":");
                String[] maxs = toks[1].split(":");
                if ((mins.length < 3) || (maxs.length < 3)) {
                    Log.severe("Invalid number of fields: " + v);
                    return false;
                }
                double xmin = clamp(Double.parseDouble(mins[0]));
                double ymin = clamp(Double.parseDouble(mins[1]));
                double zmin = clamp(Double.parseDouble(mins[2]));
                double xmax = clamp(Double.parseDouble(maxs[0]));
                double ymax = clamp(Double.parseDouble(maxs[1]));
                double zmax = clamp(Double.parseDouble(maxs[2]));
                
                int[] patches = (isCrossed)?crossedPatchDefault:cuboidPatchDefault;
                if (toks.length > 2) {
                    patches = new int[isCrossed?1:6];
                    String[] pidx = toks[2].split(":");
                    for (int j = 0; j < patches.length; j++) {
                        if (j >= pidx.length) {
                            patches[j] = Integer.parseInt(pidx[pidx.length-1]);
                        }
                        else {
                            patches[j] = Integer.parseInt(pidx[j]);
                        }
                        if (patches[j] >= textureCount) {
                            textureCount= patches[j] + 1;
                        }
                    }
                }
                // Build crossed patches
                if (isCrossed) {
                    RenderPatch VertX1Z0ToX0Z1 = rpf.getPatch(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0, Math.min(xmin, zmin), Math.max(xmax, zmax), 
                            ymin, ymax, SideVisible.FLIP, patches[0]);
                    RenderPatch VertX1Z0ToX0Z1_90 = rpf.getRotatedPatch(VertX1Z0ToX0Z1, 0, 90, 0, patches[0]);
                    list.add(VertX1Z0ToX0Z1);
                    list.add(VertX1Z0ToX0Z1_90);
                }
                else {
                    CustomRenderer.addBox(rpf, list, xmin, xmax, ymin, ymax, zmin, zmax, patches);
                }
            }
        }        
        RenderPatch[] model = list.toArray(new RenderPatch[list.size()]);
        
        String rotlist = custparm.get("rotlist");	// See if we have a rotation list
        if (rotlist != null) {
            String[] pidx = rotlist.split(":");	// Get list
        	models = new RenderPatch[pidx.length][];
            for (int idx = 0; idx < pidx.length; idx++) {
            	int rot = Integer.parseInt(pidx[idx]);

        		models[idx] = new RenderPatch[model.length];
            	if (rot != 0) {
            		for (int i = 0; i < model.length; i++) {
            			models[idx][i] = rpf.getRotatedPatch(model[i], 0, rot, 0, -1);
            		}
            	}
            	else {
            		models[idx] = model;
            	}
            }
        }
        else {
        	models = new RenderPatch[1][];
        	models[0] = model;
        }

        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return textureCount;
    }
        
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
    	int idx = ctx.getBlockType().stateIndex;
        return models[idx % models.length];
    }
}
