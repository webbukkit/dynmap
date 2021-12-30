package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;

public class WallHeadRenderer extends CustomRenderer {
    private static final int NUM_DIRECTIONS = 16;
    
    private RenderPatch basemesh[];
    private RenderPatch meshes[][] = new RenderPatch[NUM_DIRECTIONS][];
    
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        list.add(rpf.getPatch(0.75, 0.0, 0.25, 0.25, 0.0, 0.25, 0.75, 0.0, 0.75, 0, 1, 0, 1, SideVisible.TOP, 0));
        list.add(rpf.getPatch(0.75, 0.5, 0.25, 0.25, 0.5, 0.25, 0.75, 0.5, 0.75, 0, 1, 0, 1, SideVisible.TOP, 0));
        RenderPatch side = rpf.getPatch(0.75, 0.0, 0.25, 0.25, 0.0, 0.25, 0.75, 0.5, 0.25, 0, 1, 0, 1, SideVisible.TOP, 0);
        RenderPatch side2 = rpf.getRotatedPatch(side, 0, 90, 0, 0);
        RenderPatch side3 = rpf.getRotatedPatch(side, 0, 180, 0, 0);
        RenderPatch side4 = rpf.getRotatedPatch(side, 0, 270, 0, 0);
        list.add(side4);
        list.add(side);
        list.add(side2);
        list.add(side3);
        basemesh = list.toArray(new RenderPatch[list.size()]);
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return 6;
    }

    private static final int faces[] = { 0, 1, 2, 3, 4, 5 };
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
    	int idx = ctx.getBlockType().stateIndex;
        while(idx < 0) idx += NUM_DIRECTIONS; // Normalize (bad values from some mods)
        if(idx < meshes.length) {
            if(meshes[idx] == null) {
                RenderPatchFactory rpf = ctx.getPatchFactory();
                RenderPatch[] rp = new RenderPatch[basemesh.length];
                for(int i = 0; i < rp.length; i++) {
                    rp[i] = rpf.getRotatedPatch(basemesh[i], 0, 45*idx/2, 0, faces[i]);
                }
                meshes[idx] = rp;
            }
            return meshes[idx];
        }
        else
            return meshes[0];
    }
    @Override
    public boolean isOnlyBlockStateSensitive() {
    	return true;
    }
}
