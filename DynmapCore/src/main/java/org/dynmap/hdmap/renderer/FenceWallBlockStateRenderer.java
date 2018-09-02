package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class FenceWallBlockStateRenderer extends CustomRenderer {
    private static final int TEXTURE_SIDES = 0;
    private static final int TEXTURE_TOP = 1;
    private static final int TEXTURE_BOTTOM = 2;
    private boolean check_yplus;

    private static final int SIDE_XP = 0x1;	// East
    private static final int SIDE_XN = 0x2; // West
    private static final int SIDE_X = SIDE_XN | SIDE_XP;
    private static final int SIDE_ZP = 0x4; // South
    private static final int SIDE_ZN = 0x8; // North
    private static final int SIDE_Z = SIDE_ZN | SIDE_ZP;
    private static final int SIDE_YP = 0x10; // Up

    // Meshes, indexed by connection combination (bit 0=X+, bit 1=X-, bit 2=Z+, bit 3=Z-, bit 4=Y+)
    private RenderPatch[][] meshes = new RenderPatch[32][];
    
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        /* Build models, based on type of fence/wall we're set to be */
        String type = custparm.get("type");
        if((type != null) && (type.equals("wall"))) {
            buildWallMeshes(rpf);
            check_yplus = true;
        }
        else {
            buildFenceMeshes(rpf);
        }
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
    
    private void buildFenceMeshes(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        for(int dat = 0; dat < 16; dat++) {
            /* Add center post */
            addBox(rpf, list, 0.375, 0.625, 0.0, 1.0, 0.375, 0.625);
            switch(dat & SIDE_X) {
                case SIDE_XP: // Just X+
                    addBox(rpf, list, 0.625, 1.0, 0.375, 0.5625, 0.4375, 0.5625);
                    addBox(rpf, list, 0.625, 1.0, 0.75, 0.9275, 0.4375, 0.5625);
                    break;
                case SIDE_XN: // Just X-
                    addBox(rpf, list, 0.0, 0.375, 0.375, 0.5625, 0.4375, 0.5625);
                    addBox(rpf, list, 0.0, 0.375, 0.75, 0.9275, 0.4375, 0.5625);
                    break;
                case SIDE_X: // X- and X+
                    addBox(rpf, list, 0.0, 1.0, 0.375, 0.5625, 0.4375, 0.5625);
                    addBox(rpf, list, 0.0, 1.0, 0.75, 0.9275, 0.4375, 0.5625);
                    break;
            }
            switch(dat & SIDE_Z) {
                case SIDE_ZP: // Just Z+
                    addBox(rpf, list, 0.4375, 0.5625, 0.375, 0.5625, 0.625, 1.0);
                    addBox(rpf, list, 0.4375, 0.5625, 0.75, 0.9275, 0.625, 1.0);
                    break;
                case SIDE_ZN: // Just Z-
                    addBox(rpf, list, 0.4375, 0.5625, 0.375, 0.5625, 0.0, 0.375);
                    addBox(rpf, list, 0.4375, 0.5625, 0.75, 0.9275, 0.0, 0.375);
                    break;
                case SIDE_Z: // Z- and Z+
                    addBox(rpf, list, 0.4375, 0.5625, 0.375, 0.5625, 0.0, 1.0);
                    addBox(rpf, list, 0.4375, 0.5625, 0.75, 0.9275, 0.0, 1.0);
                    break;
            }
            meshes[dat] = list.toArray(new RenderPatch[list.size()]);
            list.clear();
        }
    }

    private void buildWallMeshes(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        for(int dat = 0; dat < 32; dat++) {
            boolean need_post = ((dat & 0xF) == 0) || ((dat & 0x10) == 0x10);
            switch(dat & SIDE_X) {
                case SIDE_XP: // Just X+
                    addBox(rpf, list, 0.75, 1.0, 0.0, 0.8125, 0.3125, 0.6875);
                    need_post = true;
                    break;
                case SIDE_XN: // Just X-
                    addBox(rpf, list, 0.0, 0.25, 0.0, 0.8125, 0.3125, 0.6875);
                    need_post = true;
                    break;
                case SIDE_X: // X- and X+
                    addBox(rpf, list, 0.0, 1.0, 0.0, 0.8125, 0.3125, 0.6875);
                    break;
            }
            switch(dat & SIDE_Z) {
                case SIDE_ZP: // Just Z+
                    addBox(rpf, list, 0.3125, 0.6875, 0.0, 0.8125, 0.75, 1.0);
                    need_post = true;
                    break;
                case SIDE_ZN: // Just Z-
                    addBox(rpf, list, 0.3125, 0.6875, 0.0, 0.8125, 0.0, 0.25);
                    need_post = true;
                    break;
                case SIDE_Z: // Z- and Z+
                    addBox(rpf, list, 0.3125, 0.6875, 0.0, 0.8125, 0.0, 1.0);
                    break;
            }
            if(need_post) {
                addBox(rpf, list, 0.25, 0.75, 0.0, 1.0, 0.25, 0.75);
            }
            meshes[dat] = list.toArray(new RenderPatch[list.size()]);
            list.clear();
        }
    }
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
    	int idx = ctx.getBlockType().stateIndex;
    	int off = 0;
        if(check_yplus) {	// Wall?
        	if ((idx & 0x20) == 0) off += SIDE_XP;	// East connected
        	if ((idx & 0x10) == 0) off += SIDE_ZN;	// North connected
        	if ((idx & 0x08) == 0) off += SIDE_ZP;	// South connected
        	if ((idx & 0x04) == 0) off += SIDE_YP;	// Up connected
        	if ((idx & 0x01) == 0) off += SIDE_XN;	// West connected
        }
        else {	// Fence
        	if ((idx & 0x10) == 0) off += SIDE_XP;	// East connected
        	if ((idx & 0x08) == 0) off += SIDE_ZN;	// North connected
        	if ((idx & 0x04) == 0) off += SIDE_ZP;	// South connected
        	if ((idx & 0x01) == 0) off += SIDE_XN;	// West connected
        }
        return meshes[off];
    }    
}
