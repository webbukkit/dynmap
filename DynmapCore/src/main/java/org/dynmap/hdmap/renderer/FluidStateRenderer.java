package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

/**
 * Renderer for vanilla fluids - will attempt to emulate vanilla rendering behavior, but still WIP
 */
public class FluidStateRenderer extends CustomRenderer {
    private RenderPatch[][] flat_meshes = new RenderPatch[8][];    // Meshes for each level from 0 (full) to 7 (most empty), no surface incline
    private RenderPatch[] full_mesh;
    
    private static final int PATCH_STILL = 0;
    private static final int PATCH_FLOWING = 1;
    
    private static final int[] still_patches = { PATCH_STILL, PATCH_STILL, PATCH_FLOWING, PATCH_FLOWING, PATCH_FLOWING, PATCH_FLOWING };
    
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        // Create meshes for flat topped blocks
        for (int i = 0; i < 8; i++) {
            list.clear();
            CustomRenderer.addBox(rpf, list, 0.0, 1.0, 0.0, 1.0 - ((i + 1) / 9.0), 0.0, 1.0, still_patches);
            flat_meshes[i] = list.toArray(new RenderPatch[list.size()]);
        }
        list.clear();
        CustomRenderer.addBox(rpf, list, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, still_patches);
        full_mesh = list.toArray(new RenderPatch[list.size()]);
        
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return 2;
    }

    private DynmapBlockState getFluidState(MapDataContext ctx, int dx, int dy, int dz) {
    	DynmapBlockState bs;
    	if ((dx == 0) && (dy == 0) && (dz == 0)) {
    		bs = ctx.getBlockType();
    	}
    	else {
    		bs = ctx.getBlockTypeAt(dx, dy, dz);
		}
    	DynmapBlockState fbs = bs.getLiquidState();
    	return (fbs != null) ? fbs : bs;
    }
    
    private double getCornerHeight(DynmapBlockState b0, DynmapBlockState b1, DynmapBlockState b2, DynmapBlockState b3, 
    		DynmapBlockState u0, DynmapBlockState u1, DynmapBlockState u2, DynmapBlockState u3) {
    	// If any above blocks are match, return full height
    	if (b0.matchingBaseState(u0) || b0.matchingBaseState(u1) || b0.matchingBaseState(u2) || b0.matchingBaseState(u3)) {
    		return 1.0;
    	}
    	return 0;
    }
    
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
    	DynmapBlockState bs_0_0_0 = getFluidState(ctx, 0, 0, 0);	// Get own state
    	// Check above block - if matching fluid, block will be full
    	DynmapBlockState bs_0_1_0 = getFluidState(ctx, 0, 1, 0);
    	if (bs_0_1_0.matchingBaseState(bs_0_0_0)) {
            return full_mesh;
    	}
    	// Get other above blocks
    	DynmapBlockState bs_0_1_1 = getFluidState(ctx, 0, 1, 1);
    	DynmapBlockState bs_1_1_0 = getFluidState(ctx, 1, 1, 0);
    	DynmapBlockState bs_1_1_1 = getFluidState(ctx, 1, 1, 1);
    	DynmapBlockState bs_0_1_n1 = getFluidState(ctx, 0, 1, -1);
    	DynmapBlockState bs_n1_1_0 = getFluidState(ctx, -1, 1, 0);
    	DynmapBlockState bs_n1_1_n1 = getFluidState(ctx, -1, 1, -1);
    	DynmapBlockState bs_1_1_n1 = getFluidState(ctx, 1, 1, -1);
    	DynmapBlockState bs_n1_1_1 = getFluidState(ctx, -1, 1, 1);
    	// Get other neighbors
    	DynmapBlockState bs_0_0_1 = getFluidState(ctx, 0, 0, 1);
    	DynmapBlockState bs_1_0_0 = getFluidState(ctx, 1, 0, 0);
    	DynmapBlockState bs_1_0_1 = getFluidState(ctx, 1, 0, 1);
    	DynmapBlockState bs_0_0_n1 = getFluidState(ctx, 0, 0, -1);
    	DynmapBlockState bs_n1_0_0 = getFluidState(ctx, -1, 0, 0);
    	DynmapBlockState bs_n1_0_n1 = getFluidState(ctx, -1, 0, -1);
    	DynmapBlockState bs_1_0_n1 = getFluidState(ctx, 1, 0, -1);
    	DynmapBlockState bs_n1_0_1 = getFluidState(ctx, -1, 0, 1);
    	// Get each corner height
        int idx = bs_0_0_0.stateIndex;
        return (idx < 8) ? flat_meshes[idx] : flat_meshes[0];
    }
}
