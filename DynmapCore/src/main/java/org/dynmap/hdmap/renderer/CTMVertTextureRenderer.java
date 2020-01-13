package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class CTMVertTextureRenderer extends CustomRenderer {
    private static final int TEXTURE_BOTTOM = 0;
    private static final int TEXTURE_TOP = 1;
    private static final int TEXTURE_SIDE_NO_NEIGHBOR = 2;
    private static final int TEXTURE_SIDE_ABOVE = 3;
    private static final int TEXTURE_SIDE_BELOW = 4;
    private static final int TEXTURE_SIDE_BOTH = 5;
    private DynmapBlockState blk;

    private RenderPatch[] mesh_no_neighbor;
    private RenderPatch[] mesh_above_neighbor;
    private RenderPatch[] mesh_below_neighbor;
    private RenderPatch[] mesh_both_neighbor;

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        blk = DynmapBlockState.getBaseStateByName(blkname);
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        /* Build no neighbors patches */
        addBox(rpf, list, 0, 1, 0, 1, 0, 1, new int[] { TEXTURE_BOTTOM, TEXTURE_TOP, TEXTURE_SIDE_NO_NEIGHBOR, TEXTURE_SIDE_NO_NEIGHBOR, TEXTURE_SIDE_NO_NEIGHBOR, TEXTURE_SIDE_NO_NEIGHBOR });
        mesh_no_neighbor = list.toArray(new RenderPatch[6]);
        /* Build above neighbor patches */
        list.clear();
        addBox(rpf, list, 0, 1, 0, 1, 0, 1, new int[] { TEXTURE_BOTTOM, TEXTURE_TOP, TEXTURE_SIDE_ABOVE, TEXTURE_SIDE_ABOVE, TEXTURE_SIDE_ABOVE, TEXTURE_SIDE_ABOVE });
        mesh_above_neighbor = list.toArray(new RenderPatch[6]);
        /* Build below neighbor patches */
        list.clear();
        addBox(rpf, list, 0, 1, 0, 1, 0, 1, new int[] { TEXTURE_BOTTOM, TEXTURE_TOP, TEXTURE_SIDE_BELOW, TEXTURE_SIDE_BELOW, TEXTURE_SIDE_BELOW, TEXTURE_SIDE_BELOW });
        mesh_below_neighbor = list.toArray(new RenderPatch[6]);
        /* Build both neighbor patches */
        list.clear();
        addBox(rpf, list, 0, 1, 0, 1, 0, 1, new int[] { TEXTURE_BOTTOM, TEXTURE_TOP, TEXTURE_SIDE_BOTH, TEXTURE_SIDE_BOTH, TEXTURE_SIDE_BOTH, TEXTURE_SIDE_BOTH });
        mesh_both_neighbor = list.toArray(new RenderPatch[6]);

        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return 6;
    }

    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        DynmapBlockState bs = mapDataCtx.getBlockType();
        int meta = bs.stateIndex;
        boolean above = false;
        DynmapBlockState id_above = mapDataCtx.getBlockTypeAt(0,  1,  0);
        if (id_above.baseState == blk) {    /* MIght match */
            int id_meta = id_above.stateIndex;
            if (meta == id_meta) {
                above = true;
            }
        }
        boolean below = false;
        DynmapBlockState id_below = mapDataCtx.getBlockTypeAt(0,  -1,  0);
        if (id_below.baseState == blk) {    /* MIght match */
            int id_meta = id_below.stateIndex;
            if (meta == id_meta) {
                below = true;
            }
        }
        RenderPatch[] mesh;
        if (above) {
            if (below) {
                mesh = this.mesh_both_neighbor;
            }
            else {
                mesh = this.mesh_above_neighbor;
            }
        }
        else {
            if (below) {
                mesh = this.mesh_below_neighbor;
            }
            else {
                mesh = this.mesh_no_neighbor;
            }
        }
        return mesh;
    }
}
