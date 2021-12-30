package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

/**
 * Simple renderer for creating a model of a block, with a selection of two possible textures for each of the 6 faces
 * The texture selection is based on the value of the corresponding facing attribute in the state ('down','up','north','south','east','west')
 * Order of textures is false value followed by true value, for down, up, west, east, north, south
 */
public class BoxStateRenderer extends CustomRenderer {
    // Models for each state index
    private RenderPatch[] models[];

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        double xmin = 0.0, xmax = 1.0;
        double ymin = 0.0, ymax = 1.0;
        double zmin = 0.0, zmax = 1.0;
        /* Check limits */
        String lim = custparm.get("xmin");
        if (lim != null) {
            xmin = Double.valueOf(lim);
            if (xmin < 0.0) xmin = 0.0;
        }
        lim = custparm.get("xmax");
        if (lim != null) {
            xmax = Double.valueOf(lim);
            if (xmax > 1.0) xmax = 1.0;
        }
        lim = custparm.get("ymin");
        if (lim != null) {
            ymin = Double.valueOf(lim);
            if (ymin < 0.0) ymin = 0.0;
        }
        lim = custparm.get("ymax");
        if (lim != null) {
            ymax = Double.valueOf(lim);
            if (ymax > 1.0) ymax = 1.0;
        }
        lim = custparm.get("zmin");
        if (lim != null) {
            zmin = Double.valueOf(lim);
            if (zmin < 0.0) zmin = 0.0;
        }
        lim = custparm.get("zmax");
        if (lim != null) {
            zmax = Double.valueOf(lim);
            if (zmax > 1.0) zmax = 1.0;
        }
        DynmapBlockState bs = DynmapBlockState.getBaseStateByName(blkname);	// Look up block
        /* Now, build box models */
        models = new RenderPatch[bs.getStateCount()][];
        int[] patchlist = new int[6];
        for (int i = 0; i < models.length; i++) {
        	DynmapBlockState cbs = bs.getState(i);
            ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
            String[] states = cbs.stateList;
            // Produce patch list
            patchlist[0] = Arrays.binarySearch(states, "down=true") >= 0 ? 1 : 0;
            patchlist[1] = Arrays.binarySearch(states, "up=true") >= 0 ? 3 : 2;
            patchlist[2] = Arrays.binarySearch(states, "west=true") >= 0 ? 5 : 4;
            patchlist[3] = Arrays.binarySearch(states, "east=true") >= 0 ? 7 : 6;
            patchlist[4] = Arrays.binarySearch(states, "north=true") >= 0 ? 9 : 8;
            patchlist[5] = Arrays.binarySearch(states, "south=true") >= 0 ? 11 : 10;
            CustomRenderer.addBox(rpf, list, xmin, xmax, ymin, ymax, zmin, zmax, patchlist);
            models[i] = list.toArray(new RenderPatch[patchlist.length]);
        }
        
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return 12;
    }
        
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        return models[ctx.getBlockType().stateIndex];
    }
    @Override
    public boolean isOnlyBlockStateSensitive() {
    	return true;
    }
}
