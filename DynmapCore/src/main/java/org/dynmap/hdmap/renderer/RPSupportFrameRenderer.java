package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;

import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;

public class RPSupportFrameRenderer extends RPMicroRenderer {
    private int frame_txt_side;
    private int frame_txt_edge;
 
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        frame_txt_side = super.getMaximumTextureCount();    /* Get index for side and edge textures */
        frame_txt_edge = frame_txt_side + 1;
        return true;
    }
    
    @Override
    public int getMaximumTextureCount() {
        return super.getMaximumTextureCount() + 2;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int covermask = 0;
        Object v = ctx.getBlockTileEntityField("cvm");
        if(v instanceof Integer) {
            covermask = ((Integer)v).intValue();
        }
        RenderPatchFactory rpf = ctx.getPatchFactory();
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        /* Use mask to add right sides first */
        /* Add top */
        list.add(rpf.getPatch(0, 1.001, 1, 1, 1.001, 1, 0, 1.001, 0, 0, 1, 0, 1, SideVisible.BOTH,
                ((covermask & 0x02) != 0)?frame_txt_edge:frame_txt_side));
        /* Add bottom */
        list.add(rpf.getPatch(0, -0.001, 1, 1, -0.001, 1, 0, -0.001, 0, 0, 1, 0, 1, SideVisible.BOTH,
                ((covermask & 0x01) != 0)?frame_txt_edge:frame_txt_side));
        /* Add minX side */
        list.add(rpf.getPatch(-0.001, 0, 0, -0.001, 0, 1, -0.001, 1, 0, 0, 1, 0, 1, SideVisible.BOTH,
                ((covermask & 0x10) != 0)?frame_txt_edge:frame_txt_side));
        /* Add maxX side */
        list.add(rpf.getPatch(1.001, 0, 1, 1.001, 0, 0, 1.001, 1, 1, 0, 1, 0, 1, SideVisible.BOTH,
                ((covermask & 0x20) != 0)?frame_txt_edge:frame_txt_side));
        /* Add minZ side */
        list.add(rpf.getPatch(1, 0, -0.001, 0, 0, -0.001, 1, 1, -0.001, 0, 1, 0, 1, SideVisible.BOTH, 
                ((covermask & 0x04) != 0)?frame_txt_edge:frame_txt_side));
        /* Add maxZ side */
        list.add(rpf.getPatch(0, 0, 1.001, 1, 0, 1.001, 0, 1, 1.001, 0, 1, 0, 1, SideVisible.BOTH,
                ((covermask & 0x08) != 0)?frame_txt_edge:frame_txt_side));
        /* Get patches from any microblocks */
        if((covermask & 0x3FFFFFFF) != 0) {
            RenderPatch[] rp = super.getRenderPatchList(ctx);
            for(int i = 0; i < rp.length; i++) {
                list.add(rp[i]);
            }
        }
        return list.toArray(new RenderPatch[list.size()]);
    }
}
