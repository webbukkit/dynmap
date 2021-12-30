package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

/**
 * Simple renderer for creating a model representing a normal cube (texture-wise), but with reductions in the X, Y and/or Z ranges
 */
public class BoxRenderer extends CustomRenderer {
    // Models for rotation values
    private RenderPatch[] model;
    // Patch index ordering, corresponding to BlockStep ordinal order
    private static final int patchlist[] = { 1, 4, 2, 5, 0, 3 };

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
        /* Now, build box model */
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, xmin, xmax, ymin, ymax, zmin, zmax, patchlist);
        model = list.toArray(new RenderPatch[patchlist.length]);
        
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return 6;
    }
        
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        return model;
    }
    @Override
    public boolean isOnlyBlockStateSensitive() {
    	return true;
    }
}
