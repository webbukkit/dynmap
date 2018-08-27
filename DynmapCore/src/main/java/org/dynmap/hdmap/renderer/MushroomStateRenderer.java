package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

/*
 * Huge mushroom renderer for v1.13+
 */
public class MushroomStateRenderer extends CustomRenderer {
    private static final int TEXTURE_OUTSIDE = 0;
    private static final int TEXTURE_INSIDE = 1;
    
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
        int[] faces = new int[6];
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        for (int i = 0; i < 64; i++) {
            list.clear();
            faces[0] = ((i & 0x20) == 0) ? TEXTURE_OUTSIDE : TEXTURE_INSIDE;    // Down
            faces[1] = ((i & 0x02) == 0) ? TEXTURE_OUTSIDE : TEXTURE_INSIDE;    // Up
            faces[2] = ((i & 0x01) == 0) ? TEXTURE_OUTSIDE : TEXTURE_INSIDE;    // West
            faces[3] = ((i & 0x10) == 0) ? TEXTURE_OUTSIDE : TEXTURE_INSIDE;    // East
            faces[4] = ((i & 0x08) == 0) ? TEXTURE_OUTSIDE : TEXTURE_INSIDE;    // North
            faces[5] = ((i & 0x04) == 0) ? TEXTURE_OUTSIDE : TEXTURE_INSIDE;    // North
            CustomRenderer.addBox(rpf, list, 0, 1, 0, 1, 0, 1, faces);
            meshes[i] = list.toArray(new RenderPatch[list.size()]);
        }
    }
    @Override
    public int getMaximumTextureCount() {
        return 2;
    }
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        return meshes[ctx.getBlockType().stateIndex];
    }    
}
