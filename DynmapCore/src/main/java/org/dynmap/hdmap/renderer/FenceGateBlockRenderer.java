package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class FenceGateBlockRenderer extends CustomRenderer {
    private static final int TEXTURE_SIDES = 0;
    private static final int TEXTURE_TOP = 1;
    private static final int TEXTURE_BOTTOM = 2;

    // Meshes, indexed by connection combination (bit 2=open(1)/close(0), bit 0-1=0(south),1(west),2(north),3(east))
    protected RenderPatch[][] meshes = new RenderPatch[8][];
    
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        buildMeshes(rpf);
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
    
    private void buildMeshes(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        for(int dat = 0; dat < 8; dat++) {
            // Add posts
            if ((dat & 1) == 0) {
                addBox(rpf, list, 0.0, 0.125, 0.3125, 1.0, 0.4375, 0.5625);
                addBox(rpf, list, 0.875, 1.0, 0.3125, 1.0, 0.4375, 0.5625);
                if ((dat & 4) == 0) {   // If closed
                    addBox(rpf, list, 0.375, 0.625, 0.375, 0.9375, 0.4375, 0.5625);
                    addBox(rpf, list, 0.625, 0.875, 0.375, 0.5625, 0.4375, 0.5625);
                    addBox(rpf, list, 0.625, 0.875, 0.75, 0.9375, 0.4375, 0.5625);
                    addBox(rpf, list, 0.125, 0.375, 0.375f, 0.5625, 0.4375, 0.5625);
                    addBox(rpf, list, 0.125, 0.375, 0.75, 0.9375, 0.4375, 0.5625);
                }
                else if ((dat & 3) == 0) {
                    addBox(rpf, list, 0.0, 0.125, 0.375, 0.9375, 0.8125, 0.9375);
                    addBox(rpf, list, 0.875,  1.0, 0.375, 0.9375, 0.8125, 0.9375);
                    addBox(rpf, list, 0.0, 0.125, 0.375, 0.5625, 0.5625, 0.8125);
                    addBox(rpf, list, 0.875, 1.0, 0.375, 0.5625, 0.5625, 0.8125);
                    addBox(rpf, list, 0.0, 0.125, 0.75, 0.9375, 0.5625, 0.8125);
                    addBox(rpf, list, 0.875, 1.0, 0.75, 0.9375, 0.5625, 0.8125);
                }
                else {
                    addBox(rpf, list, 0.0, 0.125, 0.375, 0.9375, 0.0625, 0.1875);
                    addBox(rpf, list, 0.875, 1.0, 0.375, 0.9375, 0.0625, 0.1875);
                    addBox(rpf, list, 0.0, 0.125, 0.375, 0.5625, 0.1875, 0.4375);
                    addBox(rpf, list, 0.875, 1.0, 0.375, 0.5625, 0.1875, 0.4375);
                    addBox(rpf, list, 0.0, 0.125, 0.75, 0.9375, 0.1875, 0.4375);
                    addBox(rpf, list, 0.875, 1.0, 0.75, 0.9375, 0.1875, 0.4375);
                }
            }
            else {
                addBox(rpf, list, 0.4375, 0.5625, 0.3125, 1.0, 0.0, 0.125);
                addBox(rpf, list, 0.4375, 0.5625, 0.3125, 1.0, 0.875, 1.0);
                if ((dat & 4) == 0) {   // If closed
                    addBox(rpf, list, 0.4375, 0.5625, 0.375, 0.9375, 0.375, 0.625);
                    addBox(rpf, list, 0.4375, 0.5625, 0.375, 0.5625, 0.625, 0.875);
                    addBox(rpf, list, 0.4375, 0.5625, 0.75, 0.9375, 0.625, 0.875);
                    addBox(rpf, list, 0.4375, 0.5625, 0.375, 0.5625, 0.125, 0.375);
                    addBox(rpf, list, 0.4375, 0.5625, 0.75, 0.9375, 0.125, 0.375);
                }
                else if ((dat & 3) == 3) {
                    addBox(rpf, list, 0.8125, 0.9375, 0.375, 0.9375, 0.0, 0.125);
                    addBox(rpf, list, 0.8125, 0.9375, 0.375, 0.9375, 0.875, 1.0);
                    addBox(rpf, list, 0.5625, 0.8125, 0.375, 0.5625, 0.0, 0.125);
                    addBox(rpf, list, 0.5625, 0.8125, 0.375, 0.5625, 0.875, 1.0);
                    addBox(rpf, list, 0.5625, 0.8125, 0.75, 0.9375, 0.0, 0.125);
                    addBox(rpf, list, 0.5625, 0.8125, 0.75, 0.9375, 0.875, 1.0);
                }
                else {
                    addBox(rpf, list, 0.0625, 0.1875, 0.375, 0.9375, 0.0, 0.125);
                    addBox(rpf, list, 0.0625, 0.1875, 0.375, 0.9375, 0.875, 1.0);
                    addBox(rpf, list, 0.1875, 0.4375, 0.375, 0.5625, 0.0, 0.125);
                    addBox(rpf, list, 0.1875, 0.4375, 0.375, 0.5625, 0.875, 1.0);
                    addBox(rpf, list, 0.1875, 0.4375, 0.75, 0.9375, 0.0, 0.125);
                    addBox(rpf, list, 0.1875, 0.4375, 0.75, 0.9375, 0.875, 1.0);
                }
            }
            
            meshes[dat] = list.toArray(new RenderPatch[list.size()]);
            
            list.clear();
        }
    }
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int meta = ctx.getBlockType().stateIndex;
        
        return meshes[meta & 0x7];
    }    
    @Override
    public boolean isOnlyBlockStateSensitive() {
    	return true;
    }
}
