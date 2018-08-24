package org.dynmap.hdmap.renderer;

import java.util.BitSet;
import java.util.Map;

import org.dynmap.Log;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class RailCraftTrackRenderer extends CustomRenderer {
    private String[] tileEntityAttribs = { "trackId" };
    
    private RenderPatch[][] basepatches;

    private int maxTrackId;
    
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;

        String cnt = custparm.get("maxTrackId"); // How many defined track IDs
        if(cnt != null) {
            maxTrackId = Integer.parseInt(cnt);
        }
        else {
            maxTrackId = 35;
        }
        String patchid = custparm.get("patch");
        if(patchid == null) {
            Log.severe("Missing patch ID");
            return false;
        }
        basepatches = new RenderPatch[maxTrackId+1][];
        basepatches[0] = new RenderPatch[] { rpf.getNamedPatch(patchid, 0) };
        if(basepatches[0][0] == null) {
            Log.severe("Error getting patch : " + patchid);
            return false;
        }
        for(int i = 1; i <= maxTrackId; i++) {
            basepatches[i] = new RenderPatch[] { rpf.getRotatedPatch(basepatches[0][0], 0, 0, 0, i) };
        }
        
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return maxTrackId + 1;
    }
    
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return tileEntityAttribs;
    }
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int trackId = 0;
        Object idxv = ctx.getBlockTileEntityField("trackId");
        if(idxv instanceof Number) {
            trackId = ((Number)idxv).intValue();
        }
        /* Clamp track ID */
        if(trackId > maxTrackId) {
            trackId = 0;
        }
        /* Return patch */
        return basepatches[trackId];
    }
}
