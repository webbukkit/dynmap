package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;

/*
 * Glow lichen renderer for v1.17+
 */
public class GlowLichenStateRenderer extends CustomRenderer {
    
    // Meshes, indexed by state index (bit5=down, bit4=east, bit3=north, bit2=south, bit1=up, bit0=west)
    protected RenderPatch[][] meshes = new RenderPatch[64][];
    
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        buildPatches(rpf);
        return true;
    }

    private void buildPatches(RenderPatchFactory rpf) {
        RenderPatch Top = rpf.getPatch(0.0, 0.95, 0.0, 1.0, 0.95, 0.0, 0.0, 0.95, 1.0, 0.0, 1.0, 0.0, 1.0, SideVisible.BOTH, 0);
        RenderPatch Bottom = rpf.getPatch(0.0, 0.05, 0.0, 1.0, 0.05, 0.0, 0.0, 0.05, 1.0, 0.0, 1.0, 0.0, 1.0, SideVisible.BOTH, 0);
        RenderPatch West = rpf.getPatch(0.05, 0.0, 0.0, 0.05, 0.0, 1.0, 0.05, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, SideVisible.BOTH, 0);
        RenderPatch East = rpf.getPatch(0.95, 0.0, 0.0, 0.95, 0.0, 1.0, 0.95, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, SideVisible.BOTH, 0);
        RenderPatch North = rpf.getPatch(0.0, 0.0, 0.05, 1.0, 0.0, 0.05, 0.0, 1.0, 0.05, 0.0, 1.0, 0.0, 1.0, SideVisible.BOTH, 0);
        RenderPatch South = rpf.getPatch(0.0, 0.0, 0.95, 1.0, 0.0, 0.95, 0.0, 1.0, 0.95, 0.0, 1.0, 0.0, 1.0, SideVisible.BOTH, 0);
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        for (int i = 0; i < 64; i++) {
            list.clear();
            if ((i & 0x20) == 0) list.add(Bottom);
            if ((i & 0x10) == 0) list.add(East);
            if ((i & 0x08) == 0) list.add(North);
            if ((i & 0x04) == 0) list.add(South);
            if ((i & 0x02) == 0) list.add(Top);
            if ((i & 0x01) == 0) list.add(West);
            meshes[i] = list.toArray(new RenderPatch[list.size()]);
        }
    }
    @Override
    public int getMaximumTextureCount() {
        return 2;
    }
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
    	int idx = ((ctx.getBlockType().stateIndex & 0x7C) >> 1) + (ctx.getBlockType().stateIndex & 0x1);	// Shift out waterlogged bit
        return meshes[idx];
    }    
}
