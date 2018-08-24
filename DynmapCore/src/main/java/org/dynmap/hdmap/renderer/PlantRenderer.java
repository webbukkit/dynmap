package org.dynmap.hdmap.renderer;

import java.util.BitSet;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;

/*
 * Plant (crossed texture) renderer : includes option for tall plants (index derived from meta below block)
 */
public class PlantRenderer extends CustomRenderer {
    // Meshes, indexed by metadata
    private RenderPatch[][] meshes;
    
    private boolean metaFromBelow = false;
    private int metaCnt = 16;
    
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        String idxsrc = custparm.get("metasrc");
        if ((idxsrc != null) && (idxsrc.equals("below"))) {
            metaFromBelow = true;
        }
        String maxmeta = custparm.get("metacnt");
        if (maxmeta != null) {
            metaCnt = Integer.parseInt(maxmeta,  10);
            if (metaCnt > 16) metaCnt = 16;
        }

        buildPatches(rpf);

        return true;
    }

    private void buildPatches(RenderPatchFactory rpf) {
        meshes = new RenderPatch[metaCnt][];
        for (int txtid = 0; txtid < metaCnt; txtid++) {
            RenderPatch VertX1Z0ToX0Z1 = rpf.getPatch(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, SideVisible.FLIP, txtid);
            RenderPatch VertX1Z0ToX0Z1_90 = rpf.getRotatedPatch(VertX1Z0ToX0Z1, 0, 90, 0, txtid);
            meshes[txtid] = new RenderPatch[] { VertX1Z0ToX0Z1, VertX1Z0ToX0Z1_90 };
        }
    }
    @Override
    public int getMaximumTextureCount() {
        return metaCnt;
    }
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int idx = 0;
        if (metaFromBelow) {
            idx = ctx.getBlockTypeAt(0, -1, 0).stateIndex;
        }
        else {
            idx = ctx.getBlockType().stateIndex;
        }
        if (idx >= metaCnt) {
            idx = 0;
        }
        return meshes[idx];
    }    
}
