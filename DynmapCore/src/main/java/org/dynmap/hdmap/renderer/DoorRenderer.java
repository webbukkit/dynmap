package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;

/**
 * Standard door renderer : two textures (top, bottom)
 */
public class DoorRenderer extends CustomRenderer {
    public static final int TXT_TOP = 0;
    public static final int TXT_BOTTOM = 1;
    
    // Indexed by combined meta
    protected RenderPatch[][] models = new RenderPatch[32][];

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        int[] txt = new int[6];
        for (int combined_meta = 0; combined_meta < 32; combined_meta++) {
            ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
            /* Get textures for each side */
            for (int side = 0; side < 6; side++) {
                txt[side] = sideAndMetaToTexture(combined_meta, side);
            }
            double[] bounds = getBoundsByMeta(combined_meta);
            double xmin = bounds[0];
            double zmin = bounds[1];
            double xmax = bounds[2];
            double zmax = bounds[3];
            /* Add bottom */
            list.add(rpf.getPatch(0, 0, 0, 1, 0, 0, 0, 0, 1, xmin, xmax, zmin, zmax, SideVisible.TOP, txt[0] & 1));
            /* Add top */
            list.add(rpf.getPatch(0, 1, 1, 1, 1, 1, 0, 1, 0, xmin, xmax, 1-zmax, 1-zmin, SideVisible.TOP, txt[1] & 1));
            /* Add minZ side */
            if ((txt[2] & 2) != 0) {
                list.add(rpf.getPatch(0, 0, zmin, 1, 0, zmin, 0, 1, zmin, xmin, xmax, 0, 1, SideVisible.BOTTOM, txt[2] & 1));
            }
            else {
                list.add(rpf.getPatch(1, 0, zmin, 0, 0, zmin, 1, 1, zmin, 1-xmax, 1-xmin, 0, 1, SideVisible.TOP, txt[2] & 1));
            }
            /* Add maxZ side */
            if ((txt[3] & 2) != 0) {
                list.add(rpf.getPatch(1, 0, zmax, 0, 0, zmax, 1, 1, zmax, 1-xmax, 1-xmin, 0, 1, SideVisible.BOTTOM, txt[3] & 1));
            }
            else {
                list.add(rpf.getPatch(0, 0, zmax, 1, 0, zmax, 0, 1, zmax, xmin, xmax, 0, 1, SideVisible.TOP, txt[3] & 1));
            }
            /* Add minX side */
            if ((txt[4] & 2) != 0) {
                list.add(rpf.getPatch(xmin, 0, 1, xmin, 0, 0, xmin, 1, 1, 1-zmax, 1-zmin, 0, 1, SideVisible.BOTTOM, txt[4] & 1));
            }
            else {
                list.add(rpf.getPatch(xmin, 0, 0, xmin, 0, 1, xmin, 1, 0, zmin, zmax, 0, 1, SideVisible.TOP, txt[4] & 1));
            }
            /* Add maxX side */
            if ((txt[5] & 2) != 0) {
                list.add(rpf.getPatch(xmax, 0, 0, xmax, 0, 1, xmax, 1, 0, zmin, zmax, 0, 1, SideVisible.BOTTOM, txt[5] & 1));
            }
            else {
                list.add(rpf.getPatch(xmax, 0, 1, xmax, 0, 0, xmax, 1, 1, 1-zmax, 1-zmin, 0, 1, SideVisible.TOP, txt[5] & 1));
            }
            models[combined_meta] = list.toArray(new RenderPatch[6]);
        }
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return 2;
    }

    private int getCombinedMeta(MapDataContext ctx, int meta) {
        boolean isTop = (meta & 0x8) != 0;
        int bottom;
        int top;

        if (isTop) {
            bottom = ctx.getBlockTypeAt(0,  -1,  0).stateIndex;
            top = meta;
        }
        else {
            bottom = meta;
            top = ctx.getBlockTypeAt(0,  1,  0).stateIndex;
        }
        boolean isOpen = (top & 1) != 0;
        return (bottom & 7) | (isTop ? 8 : 0) | (isOpen ? 16 : 0);
    }
    
    // Return 0 = top, 1 = bottom, 2 = top-flipped, 3 = bottom-flipped
    private int sideAndMetaToTexture(int combined_meta, int side) {
        if (side != 1 && side != 0) {
            int direction = combined_meta & 3;
            boolean flag = (combined_meta & 4) != 0;
            boolean flag1 = false;
            boolean flag2 = (combined_meta & 8) != 0;
            if (flag) {
                if (direction == 0 && side == 2) {
                    flag1 = !flag1;
                } else if (direction == 1 && side == 5) {
                    flag1 = !flag1;
                } else if (direction == 2 && side == 3) {
                    flag1 = !flag1;
                } else if (direction == 3 && side == 4) {
                    flag1 = !flag1;
                }
            } else {
                if (direction == 0 && side == 5) {
                    flag1 = !flag1;
                } else if (direction == 1 && side == 3) {
                    flag1 = !flag1;
                } else if (direction == 2 && side == 4) {
                    flag1 = !flag1;
                } else if (direction == 3 && side == 2) {
                    flag1 = !flag1;
                }
                if ((combined_meta & 16) != 0) {
                    flag1 = !flag1;
                }
            }
            return flag2 ? (flag1 ? 2 : 0) : (flag1 ? 3 : 1);
        } else {
            return 1;
        }
    }
    
    private static final double[][] bounds = {
        { 0.0, 0.0, 1.0, 0.1875 },
        { 0.0, 0.9125, 1.0, 1.0 },
        { 0.0, 0.0, 0.1875, 1.0 },
        { 0.9125, 0.0, 1.0, 1.0 }
    };
    
    private double[] getBoundsByMeta(int meta) {
        int direction = meta & 3;
        boolean flag = (meta & 4) != 0;
        boolean flag1 = (meta & 16) != 0;

        switch (direction) {
            case 0:
                if (flag) {
                    if (!flag1) {
                        return bounds[0];
                    }
                    else {
                        return bounds[1];
                    }
                }
                else {
                    return bounds[2];
                }
            case 1:
                if (flag) {
                    if (!flag1) {
                        return bounds[3];
                    }
                    else {
                        return bounds[2];
                    }
                }
                else {
                    return bounds[0];
                }
            case 2:
                if (flag) {
                    if (!flag1) {
                        return bounds[1];
                    }
                    else {
                        return bounds[0];
                    }
                }
                else {
                    return bounds[3];
                }
            case 3:
                if (flag) {
                    if (!flag1) {
                        return bounds[2];
                    }
                    else {
                        return bounds[3];
                    }
                }
                else {
                    return bounds[1];
                }
        }
        return bounds[0];
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int meta = ctx.getBlockType().stateIndex;  // Get our meta
        int combined_meta = getCombinedMeta(ctx, meta);
        
        return models[combined_meta];
    }
}
