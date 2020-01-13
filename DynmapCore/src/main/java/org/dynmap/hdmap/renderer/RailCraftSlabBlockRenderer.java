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

public class RailCraftSlabBlockRenderer extends CustomRenderer {
    private static final int TEXTURE_SIDES = 0;
    private static final int TEXTURE_TOP = 1;
    private static final int TEXTURE_BOTTOM = 2;
    private static BitSet stair_ids = new BitSet();
        
    // Array of meshes for normal steps - index = (0=bottom, 1=top, 2=double)
    private RenderPatch[][] stepmeshes = new RenderPatch[3][];
    
    private int textsetcnt = 0;
    private String[] tilefields = null;
    private String[] texturemap;
    
    private void setID(String bname) {
        DynmapBlockState bss = DynmapBlockState.getBaseStateByName(bname);
        if (bss.isNotAir()) {
            for (int i = 0; i < bss.getStateCount(); i++) {
                DynmapBlockState bs = bss.getState(i);
                stair_ids.set(bs.globalStateIndex);
            }
        }
    }
    
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        setID(blkname);   /* Mark block as a stair */
        /* Build step meshes */
        for(int i = 0; i < 3; i++) {
            stepmeshes[i] = buildStepMeshes(rpf, i);   
        }
        String cnt = custparm.get("texturecnt");
        if(cnt != null) 
            textsetcnt = Integer.parseInt(cnt);
        else
            textsetcnt = 16;
        tilefields = new String[] { "bottom", "top" };
        texturemap = new String[textsetcnt];
        for (int i = 0; i < textsetcnt; i++) {
            texturemap[i] = custparm.get("textmap" + i);
            if (texturemap[i] == null) {
                texturemap[i] = Integer.toString(i);
            }
        }
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return textsetcnt;
    }
    
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return tilefields;
    }

    private static final int[] patchlist = { TEXTURE_BOTTOM, TEXTURE_TOP, TEXTURE_SIDES, TEXTURE_SIDES, TEXTURE_SIDES, TEXTURE_SIDES };
    
    private void addBox(RenderPatchFactory rpf, List<RenderPatch> list, double xmin, double xmax, double ymin, double ymax, double zmin, double zmax)  {
        addBox(rpf, list, xmin, xmax, ymin, ymax, zmin, zmax, patchlist);
    }
    
    private RenderPatch[] buildStepMeshes(RenderPatchFactory rpf, int dat) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        switch (dat) {
            case 0:
                addBox(rpf, list, 0, 1, 0.0, 0.5, 0, 1);
                break;
            case 1:
                addBox(rpf, list, 0, 1, 0.5, 1, 0, 1);
                break;
            case 2:
                addBox(rpf, list, 0, 1, 0, 1, 0, 1);
                break;
        }
        return list.toArray(new RenderPatch[list.size()]);
    }
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int idx = 0;
        Object o = ctx.getBlockTileEntityField("bottom");
        Object o2 = ctx.getBlockTileEntityField("top");
        Object txtid = o;
        if (o == null) {
            txtid = o2;
        }
        if (txtid instanceof String) {
            String os = (String) txtid;
            for (int i = 0; i < texturemap.length; i++) {
                if (os.equals(texturemap[i])) {
                    idx = i;
                    break;
                }
            }
        }
        if((idx < 0) || (idx >= textsetcnt)) {
            idx = 0;
        }
        RenderPatch[] rp = this.stepmeshes[0];
        if (o2 != null) {
            if (o != null) {
                rp = this.stepmeshes[2];
            }
            else {
                rp = this.stepmeshes[1];
            }
        }

        RenderPatch[] rp2 = new RenderPatch[rp.length];
        for(int i = 0; i < rp.length; i++) {
            rp2[i] = ctx.getPatchFactory().getRotatedPatch(rp[i], 0, 0, 0, idx);
        }
        return rp2;
    }
    }
