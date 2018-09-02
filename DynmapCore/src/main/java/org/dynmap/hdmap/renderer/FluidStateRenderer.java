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
    private RenderPatch[][] flat_meshes = new RenderPatch[10][];    // Meshes for each level from 0 (full) to 7 (most empty), no surface incline
    
    private static final int PATCH_STILL = 0;
    private static final int PATCH_FLOWING = 1;
    
    private static final int[] still_patches = { PATCH_STILL, PATCH_STILL, PATCH_FLOWING, PATCH_FLOWING, PATCH_FLOWING, PATCH_FLOWING };
    
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        // Create meshes for flat topped blocks
        for (int i = 0; i < 10; i++) {
            list.clear();
            CustomRenderer.addBox(rpf, list, 0.0, 1.0, 0.0, 1.0 - (i / 9.0), 0.0, 1.0, still_patches);
            flat_meshes[i] = list.toArray(new RenderPatch[list.size()]);
        }
        
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
    
    // Height of air, in ninths
    private int getAirHeight(DynmapBlockState bs) {
    	int idx = bs.stateIndex;
    	return (idx > 7) ? 1 : (idx + 1);
    }
    
    // Return height in ninths (round to nearest - 0-9)
    private int getCornerHeight(DynmapBlockState b0, DynmapBlockState b1, DynmapBlockState b2, DynmapBlockState b3, 
    		DynmapBlockState u0, DynmapBlockState u1, DynmapBlockState u2, DynmapBlockState u3) {
    	// If any above blocks are match, return full height
    	if (b0.matchingBaseState(u0) || b0.matchingBaseState(u1) || b0.matchingBaseState(u2) || b0.matchingBaseState(u3)) {
    		return 9;
    	}
    	int accum = 0;
    	int cnt = 0;
    	// Check each of 4 neighbors
    	// First, self
    	int h = getAirHeight(b0);	// Our block is always liquid
    	if (h == 1) {	// Max
    		accum += (11 * h);
    		cnt += 11;
    	}
    	else {
    		accum += h;
    		cnt++;
    	}
    	// Others are all nieghbors
    	if (b1.matchingBaseState(b0)) {
    		h = getAirHeight(b1);
        	if (h == 1) {	// Max
        		accum += (11 * h);
        		cnt += 11;
        	}
        	else {
        		accum += h;
        		cnt++;
        	}
    	}
    	else if (b1.isSolid() == false) {
    		accum += 9;
    		cnt += 1;
    	}
    	if (b2.matchingBaseState(b0)) {
    		h = getAirHeight(b2);
        	if (h == 1) {	// Max
        		accum += (11 * h);
        		cnt += 11;
        	}
        	else {
        		accum += h;
        		cnt++;
        	}
    	}
    	else if (b2.isSolid() == false) {
    		accum += 9;
    		cnt += 1;
    	}
    	if (b3.matchingBaseState(b0)) {
    		h = getAirHeight(b3);
        	if (h == 1) {	// Max
        		accum += (11 * h);
        		cnt += 11;
        	}
        	else {
        		accum += h;
        		cnt++;
        	}
    	}
    	else if (b3.isSolid() == false) {
    		accum += 9;
    		cnt += 1;
    	}
    	return 9 - ((accum + cnt/2) / cnt);
    }
    
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
    	DynmapBlockState bs_0_0_0 = getFluidState(ctx, 0, 0, 0);	// Get own state
    	// Check above block - if matching fluid, block will be full
    	DynmapBlockState bs_0_1_0 = getFluidState(ctx, 0, 1, 0);
    	if (bs_0_1_0.matchingBaseState(bs_0_0_0)) {
            return flat_meshes[0];
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
    	int bh_1_1 = getCornerHeight(bs_0_0_0, bs_0_0_1, bs_1_0_0, bs_1_0_1, bs_0_1_0, bs_1_1_0, bs_0_1_1, bs_1_1_1);
    	int bh_1_n1 = getCornerHeight(bs_0_0_0, bs_0_0_n1, bs_1_0_0, bs_1_0_n1, bs_0_1_0, bs_1_1_0, bs_0_1_n1, bs_1_1_n1);
    	int bh_n1_1 = getCornerHeight(bs_0_0_0, bs_0_0_1, bs_n1_0_0, bs_n1_0_1, bs_0_1_0, bs_n1_1_0, bs_0_1_1, bs_n1_1_1);
    	int bh_n1_n1 = getCornerHeight(bs_0_0_0, bs_0_0_n1, bs_n1_0_0, bs_n1_0_n1, bs_0_1_0, bs_n1_1_0, bs_0_1_n1, bs_n1_1_n1);
    	// If all same, use flat mesh
    	if ((bh_1_1 == bh_1_n1) && (bh_1_1 == bh_n1_1) && (bh_1_1 == bh_n1_n1)) {
    		return flat_meshes[9 - bh_1_1];
    	}
    	else {	// Return average flat mesh, for now
    		return flat_meshes[9 - ((bh_1_1 + bh_1_n1 + bh_n1_1 + bh_n1_n1) / 4)];
    	}
    }
}
