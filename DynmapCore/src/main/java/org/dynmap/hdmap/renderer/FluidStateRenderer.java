package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.DynIntHashMap;

/**
 * Renderer for vanilla fluids - will attempt to emulate vanilla rendering behavior, but still WIP
 */
public class FluidStateRenderer extends CustomRenderer {
    private static final int PATCH_STILL = 0;
    private static final int PATCH_FLOWING = 1;
    
    private static final int[] still_patches = { PATCH_STILL, PATCH_STILL, PATCH_FLOWING, PATCH_FLOWING, PATCH_FLOWING, PATCH_FLOWING };

    private static boolean didIinit = false;
    
    private static RenderPatch bottom = null; 	// Common bottom patch

    private static DynIntHashMap meshcache = null;
    
    private static DynIntHashMap fullculledcache = null;
    
    private static final void init(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        // Create meshes for flat topped blocks
    	meshcache = new DynIntHashMap();
        for (int i = 0; i < 10; i++) {
            list.clear();
            CustomRenderer.addBox(rpf, list, 0.0, 1.0, 0.0, 1.0 - (i / 9.0), 0.0, 1.0, still_patches);
            putCachedModel(9 - i, 9 - i, 9 - i, 9 - i, list.toArray(new RenderPatch[list.size()]));
        }
    	bottom = rpf.getPatch(0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 1, 0, 1, SideVisible.TOP, PATCH_STILL);
        // For full height, build culled cache - eliminate surfaces adjacent to other fluid blocks
    	fullculledcache = new DynIntHashMap();
    	RenderPatch[] fullblkmodel = getCachedModel(9, 9, 9, 9);
        for (int i = 0; i < 64; i++) {
        	list.clear();
        	for (int f = 0; f < 6; f++) {
        		if ((i & (1 << f)) != 0) {	// Index by face list order: see which we need to keep (bit N=1 means keep face N patch)
        			list.add(fullblkmodel[f]);
        		}
        	}
        	fullculledcache.put(i, list.toArray(new RenderPatch[list.size()]));
        }
    }
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        if (!didIinit) {
        	init(rpf);
        	didIinit = true;
        }
        return true;
    }
    
    @Override
    public int getMaximumTextureCount() {
        return 2;
    }

    private static final DynmapBlockState getFluidState(MapDataContext ctx, int dx, int dy, int dz) {
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
    private static final int getAirHeight(DynmapBlockState bs) {
    	int idx = bs.stateIndex;
    	return (idx > 7) ? 1 : (idx + 1);
    }
    
    private static final int getIntKey(int h_1_1, int h_n1_1, int h_1_n1, int h_n1_n1) {
    	return (h_1_1) + (h_n1_1 << 4) + (h_1_n1 << 8) + (h_n1_n1 << 12);
    }

    // Get cached model
    private static RenderPatch[] getCachedModel(int h_1_1, int h_n1_1, int h_1_n1, int h_n1_n1) {
    	return (RenderPatch[]) meshcache.get(getIntKey(h_1_1, h_n1_1, h_1_n1, h_n1_n1));
    }
    
    // Put cached model
    private static void putCachedModel(int h_1_1, int h_n1_1, int h_1_n1, int h_n1_n1, RenderPatch[] model) {
    	meshcache.put(getIntKey(h_1_1, h_n1_1, h_1_n1, h_n1_n1), model);
    }

    // Get culled full model
    private static final RenderPatch[]  getFullCulledModel(MapDataContext ctx, DynmapBlockState bs_0_0_0, DynmapBlockState bs_0_1_0) {
    	DynmapBlockState bs_n1_0_0 = getFluidState(ctx, -1, 0, 0);
    	DynmapBlockState bs_1_0_0 = getFluidState(ctx, 1, 0, 0);
    	DynmapBlockState bs_0_0_n1 = getFluidState(ctx, 0, 0, -1);
    	DynmapBlockState bs_0_0_1 = getFluidState(ctx, 0, 0, 1);
    	return getFullCulledModel(ctx, bs_0_0_0, bs_0_1_0, bs_n1_0_0, bs_1_0_0, bs_0_0_n1, bs_0_0_1);
    }
    // Get culled full model
    private static final RenderPatch[]  getFullCulledModel(MapDataContext ctx, DynmapBlockState bs_0_0_0, 
    		DynmapBlockState bs_0_1_0, DynmapBlockState bs_n1_0_0, DynmapBlockState bs_1_0_0,
    		 DynmapBlockState bs_0_0_n1, DynmapBlockState bs_0_0_1) {
    	int idx = 0;
    	// Check bottom - keep if not match
    	if (!bs_0_0_0.matchingBaseState(getFluidState(ctx, 0, -1, 0))) {
    		idx += 1;
    	}
    	// Check top - keep if not match
    	if (!bs_0_0_0.matchingBaseState(bs_0_1_0)) {
    		idx += 2;
    	}
        // Check minX side  - keep if not match
    	if (!bs_0_0_0.matchingBaseState(bs_n1_0_0)) {
    		idx += 4;
    	}
        // Check maxX side  - keep if not match
    	if (!bs_0_0_0.matchingBaseState(bs_1_0_0)) {
    		idx += 8;
    	}
        // Check minZ side  - keep if not match
    	if (!bs_0_0_0.matchingBaseState(bs_0_0_n1)) {
    		idx += 16;
    	}
        // Check maxZ side  - keep if not match
    	if (!bs_0_0_0.matchingBaseState(bs_0_0_1)) {
    		idx += 32;
    	}
    	return (RenderPatch[]) fullculledcache.get(idx);
    }

    // Check if full height corner due to upper block states
    private static final boolean isUpperCornerHeightFull(DynmapBlockState b0, DynmapBlockState u0, DynmapBlockState u1, DynmapBlockState u2, DynmapBlockState u3) {
    	// If any above blocks are match, return full height
    	return (b0.matchingBaseState(u0) || b0.matchingBaseState(u1) || b0.matchingBaseState(u2) || b0.matchingBaseState(u3));
    }
    
    // Return height in ninths (round to nearest - 0-9)
    private static final int getCornerHeight(DynmapBlockState b0, DynmapBlockState b1, DynmapBlockState b2, DynmapBlockState b3) {
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
    public final RenderPatch[] getRenderPatchList(MapDataContext ctx) {
    	DynmapBlockState bs_0_0_0 = getFluidState(ctx, 0, 0, 0);	// Get own state
    	// Check above block - if matching fluid, block will be full
    	DynmapBlockState bs_0_1_0 = getFluidState(ctx, 0, 1, 0);
    	if (bs_0_1_0.matchingBaseState(bs_0_0_0)) {
    		return getFullCulledModel(ctx, bs_0_0_0, bs_0_1_0);
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
    	// See if full height corner due to upper blocks
    	boolean isfull_1_1 = isUpperCornerHeightFull(bs_0_0_0, bs_0_1_0, bs_1_1_0, bs_0_1_1, bs_1_1_1);
    	boolean isfull_1_n1 = isUpperCornerHeightFull(bs_0_0_0, bs_0_1_0, bs_1_1_0, bs_0_1_n1, bs_1_1_n1);
    	boolean isfull_n1_1 = isUpperCornerHeightFull(bs_0_0_0, bs_0_1_0, bs_n1_1_0, bs_0_1_1, bs_n1_1_1);
    	boolean isfull_n1_n1 = isUpperCornerHeightFull(bs_0_0_0, bs_0_1_0, bs_n1_1_0, bs_0_1_n1, bs_n1_1_n1);
    	// If full height
    	if (isfull_1_1 && isfull_1_n1 && isfull_n1_1 && isfull_n1_n1) {
    		return getFullCulledModel(ctx, bs_0_0_0, bs_0_1_0);
		}
    	// Get other neighbors to figure out corner heights
    	DynmapBlockState bs_0_0_1 = getFluidState(ctx, 0, 0, 1);
    	DynmapBlockState bs_1_0_0 = getFluidState(ctx, 1, 0, 0);
    	DynmapBlockState bs_1_0_1 = getFluidState(ctx, 1, 0, 1);
    	DynmapBlockState bs_0_0_n1 = getFluidState(ctx, 0, 0, -1);
    	DynmapBlockState bs_n1_0_0 = getFluidState(ctx, -1, 0, 0);
    	DynmapBlockState bs_n1_0_n1 = getFluidState(ctx, -1, 0, -1);
    	DynmapBlockState bs_1_0_n1 = getFluidState(ctx, 1, 0, -1);
    	DynmapBlockState bs_n1_0_1 = getFluidState(ctx, -1, 0, 1);
    	// Get each corner height
    	int bh_1_1 = isfull_1_1 ? 9 : getCornerHeight(bs_0_0_0, bs_0_0_1, bs_1_0_0, bs_1_0_1);
    	int bh_1_n1 = isfull_1_n1 ? 9 : getCornerHeight(bs_0_0_0, bs_0_0_n1, bs_1_0_0, bs_1_0_n1);
    	int bh_n1_1 = isfull_n1_1 ? 9 : getCornerHeight(bs_0_0_0, bs_0_0_1, bs_n1_0_0, bs_n1_0_1);
    	int bh_n1_n1 = isfull_n1_n1 ? 9 : getCornerHeight(bs_0_0_0, bs_0_0_n1, bs_n1_0_0, bs_n1_0_n1);
    	// If full height
    	if ((bh_1_1 == 9) && (bh_1_n1 == 9) && (bh_n1_1 == 9) && (bh_n1_n1 == 9)) {
    		return getFullCulledModel(ctx, bs_0_0_0, bs_0_1_0, bs_n1_0_0, bs_1_0_0, bs_0_0_n1, bs_0_0_1);
		}
    	// Do cached lookup of model
    	RenderPatch[] mod = getCachedModel(bh_1_1, bh_n1_1, bh_1_n1, bh_n1_n1);
    	// If not found, create model
    	if (mod == null) {
        	RenderPatchFactory rpf = ctx.getPatchFactory();
			ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
			list.add(bottom);	// All models have bottom patch
			// Add side for each face
			addSide(list, rpf, 0, 0, 0, 1, bh_n1_n1, bh_n1_1); // Xminus
			addSide(list, rpf, 1, 1, 1, 0, bh_1_1, bh_1_n1); // Xplus
			addSide(list, rpf, 1, 0, 0, 0, bh_1_n1, bh_n1_n1); // Zminus
			addSide(list, rpf, 0, 1, 1, 1, bh_n1_1, bh_1_1); // Zplus

			int edge_xm = bh_n1_n1 + bh_n1_1;
			int edge_xp = bh_1_n1 + bh_1_1;
			int edge_zm = bh_n1_n1 + bh_1_n1;
			int edge_zp = bh_1_1 + bh_n1_1;
			
			// See which edge is lowest
			if ((edge_xp <= edge_xm) && (edge_xp <= edge_zm) && (edge_xp <= edge_zp)) { // bh_1_1 and bh_1_n1 (Xplus)
				addTop(list, rpf, 1, 1, 1, 0, bh_1_1, bh_1_n1, bh_n1_1, bh_n1_n1);
			}
			else if ((edge_zp <= edge_zm) && (edge_zp <= edge_xm) && (edge_zp <= edge_xp)) {//  bh_n1_1 and bh_1_1 (zPlus)
				addTop(list, rpf, 0, 1, 1, 1, bh_n1_1, bh_1_1, bh_n1_n1, bh_1_n1);
			}
			else if ((edge_xm <= edge_xp) && (edge_xm <= edge_zm) && (edge_xm <= edge_zp)) {	// bh_n1_n1 and bh_n1_1 (xMinus)
				addTop(list, rpf, 0, 0, 0, 1, bh_n1_n1, bh_n1_1, bh_1_n1, bh_1_1);
			}
			else {	// bh_1_n1 and bh_n1_n1 (zMinus)
				addTop(list, rpf, 1, 0, 0, 0, bh_1_n1, bh_n1_n1, bh_1_1, bh_n1_1);
			}
			mod = list.toArray(new RenderPatch[list.size()]);
	    	putCachedModel(bh_1_1, bh_n1_1, bh_1_n1, bh_n1_n1, mod);
	    	
	    	//Log.info(String.format("%d:%d:%d::bh_1_1=%d,bh_1_n1=%d,bh_n1_1=%d,bh_n1_n1=%d", ctx.getX(), ctx.getY(), ctx.getZ(), bh_1_1, bh_1_n1, bh_n1_1, bh_n1_n1));
	    	//for (RenderPatch rp : list) {
	    	//	Log.info(rp.toString());
	    	//}
    	}
    	return mod;
    }
    
    private static final void addSide(ArrayList<RenderPatch> list, RenderPatchFactory rpf, double x0, double z0, double x1, double z1, int h0, int h1) {
    	if ((h0 == 0) && (h1 == 0))
    		return;
    	list.add(rpf.getPatch(x0, 0, z0, x1, 0, z1, x0, 1, z0, 0, 1, 0, 0, (double) h0 / 9.0, (double) h1 / 9.0, SideVisible.TOP, PATCH_FLOWING));
    }
    
    private static final void addTop(ArrayList<RenderPatch> list, RenderPatchFactory rpf, double x0, double z0, double x1, double z1, int h0, int h1, int h2, int h3) {
    	int h0_upper = h1 + h2 - h3;
    	if (x0 == x1) {	// edge is Z+/-
    		if (h0_upper == h0) {	// If single surface
    			list.add(rpf.getPatch(x0, (double) h0 / 9.0, z0, x1, (double) h1 / 9.0, z1, 1-x0, (double) h2 / 9.0, z0, 0, 1, 0, 0, 1, 1, SideVisible.TOP, PATCH_FLOWING));
    		}
    		else {
    			// Lower triangle
    			list.add(rpf.getPatch(x0, (double) h0 / 9.0, z0, x1, (double) h1 / 9.0, z1, 1-x0, (double) h2 / 9.0, z0, 0, 1, 0, 0, 1, 0, SideVisible.TOP, PATCH_FLOWING));
    			// Upper triangle
    			list.add(rpf.getPatch(x0, (double) h0_upper / 9.0, z0, x1, (double) h1 / 9.0, z1, 1-x0, (double) h2 / 9.0, z0, 0, 1, 1, 0, 1, 1, SideVisible.TOP, PATCH_FLOWING));
    		}
    	}
    	else {
    		if (h0_upper == h0) {	// If single surface
    			list.add(rpf.getPatch(x0, (double) h0 / 9.0, z0, x1, (double) h1 / 9.0, z1, x0, (double) h2 / 9.0, 1 - z0, 0, 1, 0, 0, 1, 1, SideVisible.TOP, PATCH_FLOWING));
    		}
    		else {
    			// Lower triangle
    			list.add(rpf.getPatch(x0, (double) h0 / 9.0, z0, x1, (double) h1 / 9.0, z1, x0, (double) h2 / 9.0, 1 - z0, 0, 1, 0, 0, 1, 0, SideVisible.TOP, PATCH_FLOWING));
    			// Upper triangle
    			list.add(rpf.getPatch(x0, (double) h0_upper / 9.0, z0, x1, (double) h1 / 9.0, z1, x0, (double) h2 / 9.0, 1 - z0, 0, 1, 1, 0, 1, 1, SideVisible.TOP, PATCH_FLOWING));
    		}
    	}
    }
}
