package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class TFCSupportRenderer extends CustomRenderer {
    private boolean isVert;
    private static BitSet vertid = new BitSet();
    private static BitSet horizid = new BitSet();

    private static final int SIDE_XP = 0x1;
    private static final int SIDE_XN = 0x2;
    private static final int SIDE_X = SIDE_XN | SIDE_XP;
    private static final int SIDE_ZP = 0x4;
    private static final int SIDE_ZN = 0x8;
    private static final int SIDE_Z = SIDE_ZN | SIDE_ZP;
    private static final int SIDE_YN = 0x10;

    // Meshes, indexed by connection combination (bit 0=X+, bit 1=X-, bit 2=Z+, bit 3=Z-, bit 4=Y-)
    private RenderPatch[][] meshes = new RenderPatch[32][];
    
    private void setID(BitSet set, String bname) {
        DynmapBlockState bbs = DynmapBlockState.getBaseStateByName(bname);
        if (bbs.isNotAir()) {
            for (int i = 0; i < bbs.getStateCount(); i++) {
                set.set(bbs.getState(i).globalStateIndex);
            }
        }
    }
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        String vert = custparm.get("vert");
        if((vert != null) && (vert.equals("true"))) {
            isVert = true;
            setID(vertid, blkname);
        }
        else {
            setID(horizid, blkname);
        }
        /* Generate meshes */
        buildMeshes(rpf);
        
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return 1;
    }
    
    private static final int[] patchlist = { 0, 0, 0, 0, 0, 0 };

    private void addBox(RenderPatchFactory rpf, List<RenderPatch> list, double xmin, double xmax, double ymin, double ymax, double zmin, double zmax)  {
        addBox(rpf, list, xmin, xmax, ymin, ymax, zmin, zmax, patchlist);
    }

    private void buildMeshes(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        for(int dat = 0; dat < 32; dat++) {
            switch(dat & SIDE_X) {
                case SIDE_XP: // Just X+
                    addBox(rpf, list, 0.75, 1.0, 0.5, 1.0, 0.25, 0.75);
                    break;
                case SIDE_XN: // Just X-
                    addBox(rpf, list, 0.0, 0.25, 0.5, 1.0, 0.25, 0.75);
                    break;
                case SIDE_X: // X- and X+
                    addBox(rpf, list, 0.0, 1.0, 0.5, 1.0, 0.25, 0.75);
                    break;
            }
            switch(dat & SIDE_Z) {
                case SIDE_ZP: // Just Z+
                    addBox(rpf, list, 0.25, 0.75, 0.5, 1.0, 0.75, 1.0);
                    break;
                case SIDE_ZN: // Just Z-
                    addBox(rpf, list, 0.25, 0.75, 0.5, 1.0, 0.0, 0.25);
                    break;
                case SIDE_Z: // Z- and Z+
                    addBox(rpf, list, 0.25, 0.75, 0.5, 1.0, 0.0, 1.0);
                    break;
            }
            /* Always have post on vertical */
            if(isVert || ((dat & SIDE_YN) != 0)) {
                addBox(rpf, list, 0.25, 0.75, 0.0, 1.0, 0.25, 0.75);
            }
            else {
                addBox(rpf, list, 0.25, 0.75, 0.5, 1.0, 0.25, 0.75);
            }
            meshes[dat] = list.toArray(new RenderPatch[list.size()]);
            list.clear();
        }
    }

    private static int[][] sides = {
        { 1, 0, 0, SIDE_XP },
        { -1, 0, 0, SIDE_XN },
        { 0, 0, 1, SIDE_ZP },
        { 0, 0, -1, SIDE_ZN }
    };
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        /* Build connection map - check each axis */
        int connect = 0;
        for (int i = 0; i < sides.length; i++) {
            DynmapBlockState blk = ctx.getBlockTypeAt(sides[i][0], sides[i][1], sides[i][2]);
            if (blk.isAir()) continue;
            if (vertid.get(blk.globalStateIndex) || horizid.get(blk.globalStateIndex)) {
                connect |= sides[i][3];
            }
        }
        if (!isVert) {   /* Link horizontal to verticals below */
            DynmapBlockState blk = ctx.getBlockTypeAt(0, -1, 0);
            if (blk.isNotAir() && vertid.get(blk.globalStateIndex)) {
                connect |= SIDE_YN;
            }
        }
        return meshes[connect];
    }    
}
