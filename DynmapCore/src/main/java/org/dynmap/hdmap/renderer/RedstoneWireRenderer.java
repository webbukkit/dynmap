package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;

public class RedstoneWireRenderer extends CustomRenderer {
    private static final int TEXTURE_REDSTONE_STRAIGHT = 0;
    private static final int TEXTURE_REDSTONE_CROSS = 1;
    private DynmapBlockState blkbs;;

    // Patches for bottom - indexed by connection graph (bit0=N,bit1=S,bit2=E,bit3=W)
    private RenderPatch[] bottom_patches = new RenderPatch[16];
    // Patches for sides - (N, S, E, W)
    private RenderPatch[] side_patches = new RenderPatch[4];
    // Array of lists - index: bit 0-3=bottom index, bit4=N side, 5=southside, 6=E side, 7=W side present
    protected RenderPatch[][] meshes = new RenderPatch[256][];
    
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        blkbs = DynmapBlockState.getBaseStateByName(blkname);
        /* Build list of side patches */
        side_patches[0] = rpf.getPatch(0.0,  0.0,  0.0,  0.0,  1.0,  0.0,  0.0,  0.0, 1.0, 0.0, 1.0, 0.0, 1.0, SideVisible.TOP, TEXTURE_REDSTONE_STRAIGHT);
        side_patches[1] = rpf.getRotatedPatch(side_patches[0], 0, 180, 0, TEXTURE_REDSTONE_STRAIGHT);
        side_patches[2] = rpf.getRotatedPatch(side_patches[0], 0, 90, 0, TEXTURE_REDSTONE_STRAIGHT);
        side_patches[3] = rpf.getRotatedPatch(side_patches[0], 0, 270, 0, TEXTURE_REDSTONE_STRAIGHT);
        /* Build bottom patches */
        for(int i = 0; i < 16; i++) {
            switch(i) {
                case 1: /* N */
                case 2: /* S */
                case 3: /* NS */
                    bottom_patches[i] = rpf.getPatch(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 
                        0.0, 1.0, 0.0, 1.0,
                        SideVisible.BOTTOM, TEXTURE_REDSTONE_STRAIGHT);
                    break;
                case 4: /* E */
                case 8: /* W */
                case 12: /* EW */
                    bottom_patches[i] = rpf.getPatch(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 
                            0.0, 1.0, 0.0, 1.0,
                            SideVisible.TOP, TEXTURE_REDSTONE_STRAIGHT);
                    break;
                default:
                    bottom_patches[i] = rpf.getPatch(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 
                        ((i & 0x04) != 0)?0.0:0.3125,
                        ((i & 0x08) != 0)?1.0:0.6875,
                        ((i & 0x01) != 0)?0.0:0.3125,
                        ((i & 0x02) != 0)?1.0:0.6875,
                        SideVisible.TOP, TEXTURE_REDSTONE_CROSS);
                    break;
            }
        }

        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return 2;
    }

    private static final int x_off[] = { -1, 1, 0, 0 };
    private static final int z_off[] = { 0, 0, -1, 1 };
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int idx = 0;
        /* Check in each direction for wire */
        for(int i = 0; i < x_off.length; i++) {
            /* Look up */
            if(mapDataCtx.getBlockTypeAt(x_off[i],  1, z_off[i]).matchingBaseState(blkbs)) {
                idx |= (1 << i) | (16 << i);
            }
            else if(mapDataCtx.getBlockTypeAt(x_off[i],  0, z_off[i]).matchingBaseState(blkbs)) {
                idx |= (1 << i);
            }
            else if(mapDataCtx.getBlockTypeAt(x_off[i],  -1, z_off[i]).matchingBaseState(blkbs)) {
                idx |= (1 << i);
            }
        }
        return getMesh(idx);
    }
    
    protected RenderPatch[] getMesh(int idx) {
        RenderPatch[] mesh = meshes[idx];   /* Look up mesh */
        /* If not yet generated, generate it */
        if(mesh == null) {
            mesh = buildMesh(idx);
            meshes[idx] = mesh;
        }
        return mesh;
    }
    
    private RenderPatch[] buildMesh(int idx) {
        ArrayList<RenderPatch> lst = new ArrayList<RenderPatch>();
        lst.add(bottom_patches[idx & 0xF]);
        /* Add any needed sides */
        for(int i = 0; i < 4; i++) {
            if((idx & (0x10 << i)) != 0) {
                lst.add(side_patches[i]);
            }
        }
        return lst.toArray(new RenderPatch[lst.size()]);
    }
}
