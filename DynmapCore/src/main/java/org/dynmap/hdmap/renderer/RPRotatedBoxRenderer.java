package org.dynmap.hdmap.renderer;

import java.util.BitSet;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class RPRotatedBoxRenderer extends CustomRenderer {
    // From RenderContext.java in RP2 (for rotateTexturesNew())
    private static final int[][] rotTable = {
        { 0, 1, 2, 3, 4, 5, 0, 112347, 0 }, 
        { 0, 1, 4, 5, 3, 2, 45, 112320, 27 }, 
        { 0, 1, 3, 2, 5, 4, 27, 112347, 0 }, 
        { 0, 1, 5, 4, 2, 3, 54, 112320, 27 }, 
        { 1, 0, 2, 3, 5, 4, 112347, 112347, 0 }, 
        { 1, 0, 4, 5, 2, 3, 112374, 112320, 27 }, 
        { 1, 0, 3, 2, 4, 5, 112320, 112347, 0 }, 
        { 1, 0, 5, 4, 3, 2, 112365, 112320, 27 }, 
        { 4, 5, 0, 1, 2, 3, 217134, 1728, 110619 }, 
        { 3, 2, 0, 1, 4, 5, 220014, 0, 112347 }, 
        { 5, 4, 0, 1, 3, 2, 218862, 1728, 110619 }, 
        { 2, 3, 0, 1, 5, 4, 220590, 0, 112347 }, 
        { 4, 5, 1, 0, 3, 2, 188469, 1728, 110619 }, 
        { 3, 2, 1, 0, 5, 4, 191349, 0, 112347 }, 
        { 5, 4, 1, 0, 2, 3, 190197, 1728, 110619 }, 
        { 2, 3, 1, 0, 4, 5, 191925, 0, 112347 }, 
        { 4, 5, 3, 2, 0, 1, 2944, 110619, 1728 }, 
        { 3, 2, 5, 4, 0, 1, 187264, 27, 112320 }, 
        { 5, 4, 2, 3, 0, 1, 113536, 110619, 1728 }, 
        { 2, 3, 4, 5, 0, 1, 224128, 27, 112320 }, 
        { 4, 5, 2, 3, 1, 0, 3419, 110619, 1728 }, 
        { 3, 2, 4, 5, 1, 0, 187739, 27, 112320 }, 
        { 5, 4, 3, 2, 1, 0, 114011, 110619, 1728 }, 
        { 2, 3, 5, 4, 1, 0, 224603, 27, 112320 } };
    private int rotalg[] = new int[16]; // Rotaton algorithm (0=orientTextures,1=orientTexturesNew,2=fixed-at-0, 3=rotateTextures)
    // Models for rotation values
    private RenderPatch[][] models;

    
    private String[] tileEntityAttribs = { "rot" };

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;

        models = new RenderPatch[rotTable.length][];
        
        for(int i = 0; i < 16; i++) {
            String v = custparm.get("rotalg" + i);
            if(v != null) {
                rotalg[i] = Integer.parseInt(v);
            }
        }
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return 6;
    }
    
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return tileEntityAttribs;
    }
    
    private static final int rotgrid[][] = { 
        { 270, 180, 0, 90 }, // Bottom
        { 270, 180, 0, 90 }, // Top
        { 0, 270, 90, 180 }, // Z-
        { 0, 270, 90, 180 }, // Z+
        { 0, 270, 90, 180 }, // X-
        { 0, 270, 90, 180 } }; // X+
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        Object rot = ctx.getBlockTileEntityField("rot");
        int idx = 0;
        if(rot instanceof Number) {
            idx = ((Number)rot).intValue();
        }
        switch (rotalg[ctx.getBlockType().stateIndex]) {
            case 0: // Base rotation
                idx = idx * 4;  // Map to equivalent index in "new" algorithm map
                break;
            case 2: // Fixed to zero
                idx = 0;
                break;
            case 3: // rotateTextures
                if(idx > 3) idx = 0;
                break;
        }
        if((idx < 0) || (idx >= models.length)) {
            idx = 0;
        }
        if(models[idx] == null) {
            models[idx] = new RenderPatch[6];
            int v = rotTable[idx][6];
            for (int side = 0; side < 6; side++) {
                models[idx][side] = this.getSidePatch(ctx.getPatchFactory(), side, rotgrid[side][(v >> (3*side)) & 0x3], rotTable[idx][side]);
            }
        }
        return models[idx];
    }
}
