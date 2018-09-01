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
            CustomRenderer.addBox(rpf, list, 0.0, 1.0, 0.0, 0.875 - (0.12 * i), 0.0, 1.0, still_patches);
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
        
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int idx = ctx.getBlockType().stateIndex;
        if ((idx == 0) || (idx >= 8)) {
            DynmapBlockState up = ctx.getBlockTypeAt(0, 1, 0);
            if (up.isWater() || up.isWaterlogged())
                return full_mesh;
        }
        return (idx < 8) ? flat_meshes[idx] : flat_meshes[0];
    }
}
