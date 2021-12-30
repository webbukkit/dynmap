package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class StairStateRenderer extends CustomRenderer {
    private static final int TEXTURE_SIDES = 0;
    private static final int TEXTURE_TOP = 1;
    private static final int TEXTURE_BOTTOM = 2;
        
    // Array of meshes for normal steps - index = (data value & 7)
    private RenderPatch[][] stepmeshes = new RenderPatch[8][];
    // Array of meshes for 3/4 steps - index = (data value & 7), with extra one clockwise from normal step
    private RenderPatch[][] step_3_4_meshes = new RenderPatch[8][];
    // Array of meshes for 1/4 steps - index = (data value & 7), with clockwise quarter clopped from normal step
    private RenderPatch[][] step_1_4_meshes = new RenderPatch[8][];
    
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        /* Build step meshes */
        for(int i = 0; i < 8; i++) {
            stepmeshes[i] = buildStepMeshes(rpf, i);   
            step_1_4_meshes[i] = buildCornerStepMeshes(rpf, i);   
            step_3_4_meshes[i] = buildIntCornerStepMeshes(rpf, i);   
        }
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return 3;
    }
    
    private static final int[] patchlist = { TEXTURE_BOTTOM, TEXTURE_TOP, TEXTURE_SIDES, TEXTURE_SIDES, TEXTURE_SIDES, TEXTURE_SIDES };
    
    private void addBox(RenderPatchFactory rpf, List<RenderPatch> list, double xmin, double xmax, double ymin, double ymax, double zmin, double zmax)  {
        addBox(rpf, list, xmin, xmax, ymin, ymax, zmin, zmax, patchlist);
    }
    
    private RenderPatch[] buildStepMeshes(RenderPatchFactory rpf, int dat) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        /* If inverted, add half top */
        if((dat & 0x4) != 0) {
            addBox(rpf, list, 0, 1, 0.5, 1, 0, 1);
        }
        else {  // Else, add half bottom
            addBox(rpf, list, 0, 1, 0.0, 0.5, 0, 1);
        }
        switch(dat & 0x3) {
            case 0: 
                addBox(rpf, list, 0.5, 1, 0, 1, 0, 1);
                break;
            case 1:
                addBox(rpf, list, 0, 0.5, 0, 1, 0, 1);
                break;
            case 2:
                addBox(rpf, list, 0, 1, 0, 1, 0.5, 1);
                break;
            case 3:
                addBox(rpf, list, 0, 1, 0, 1, 0, 0.5);
                break;
        }
        return list.toArray(new RenderPatch[list.size()]);
    }

    private RenderPatch[] buildCornerStepMeshes(RenderPatchFactory rpf, int dat) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        /* If inverted, add half top */
        if((dat & 0x4) != 0) {
            addBox(rpf, list, 0, 1, 0.5, 1, 0, 1);
        }
        else {  // Else, add half bottom
            addBox(rpf, list, 0, 1, 0.0, 0.5, 0, 1);
        }
        switch(dat & 0x3) {
            case 0: 
                addBox(rpf, list, 0.5, 1, 0, 1, 0.5, 1.0);
                break;
            case 1:
                addBox(rpf, list, 0, 0.5, 0, 1, 0, 0.5);
                break;
            case 2:
                addBox(rpf, list, 0, 0.5, 0, 1, 0.5, 1.0);
                break;
            case 3:
                addBox(rpf, list, 0.5, 1, 0, 1, 0, 0.5);
                break;
        }
        return list.toArray(new RenderPatch[list.size()]);
    }

    private RenderPatch[] buildIntCornerStepMeshes(RenderPatchFactory rpf, int dat) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        /* If inverted, add half top */
        if((dat & 0x4) != 0) {
            addBox(rpf, list, 0, 1, 0.5, 1, 0, 1);
        }
        else {  // Else, add half bottom
            addBox(rpf, list, 0, 1, 0.0, 0.5, 0, 1);
        }
        switch(dat & 0x3) {
            case 3: 
                addBox(rpf, list, 0.5, 1, 0, 1, 0, 1);
                addBox(rpf, list, 0, 0.5, 0, 1, 0, 0.5);
                break;
            case 0:
                addBox(rpf, list, 0.5, 1, 0, 1, 0, 1);
                addBox(rpf, list, 0, 0.5, 0, 1, 0.5, 1);
                break;
            case 1:
                addBox(rpf, list, 0, 0.5, 0, 1, 0, 1);
                addBox(rpf, list, 0.5, 1, 0, 1, 0, 0.5);
                break;
            case 2:
                addBox(rpf, list, 0, 0.5, 0, 1, 0, 1);
                addBox(rpf, list, 0.5, 1, 0, 1, 0.5, 1);
                break;
        }
        return list.toArray(new RenderPatch[list.size()]);
    }

    // Outer and inner left equivalent to outer and innter right minus 90 degrees
    private static final int midx_by_facing[] = { 3, 2, 1, 0 };
    private static final int midx_by_facing_left[] = { 1, 0, 2, 3 };
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
    	int idx = ctx.getBlockType().stateIndex;
    	// Parse index
    	int shape = (idx >> 1) % 5; // 0=straight, 1=inner left, 2 = inner right, 3=outer left, 4=outer right
    	int half = (idx / 10) % 2;	// 0=top, 1=bottom
    	int facing = (idx / 20) % 4;	// 0=north, 1=south, 2=west, 3=east
    	// Compute model index
    	RenderPatch[] rp = null;
    	switch (shape) {
    		case 0:	// Straight
    			rp = stepmeshes[midx_by_facing[facing] + ((1-half) * 4)];
    			break;
    		case 1: // inner left
    			rp = step_3_4_meshes[midx_by_facing_left[facing] + ((1-half) * 4)];
    			break;
    		case 2: // inner right
    			rp = step_3_4_meshes[midx_by_facing[facing] + ((1-half) * 4)];
    			break;
    		case 3:	// Outer left
    			rp = step_1_4_meshes[midx_by_facing_left[facing] + ((1-half) * 4)];
    			break;
    		case 4: // Outer right
    			rp = step_1_4_meshes[midx_by_facing[facing] + ((1-half) * 4)];
    			break;
    	}
    	return rp;
    }    
    @Override
    public boolean isOnlyBlockStateSensitive() {
    	return true;
    }
}
