package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import org.dynmap.Log;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class FrameRenderer extends CustomRenderer {
    // Map of block ID sets for linking blocks
    private static Map<String, BitSet> linked_ids_by_set = new HashMap<String, BitSet>();
    // Set of linked blocks
    private BitSet linked_ids;
    // Diameter of frame/wore (1.0 = full block)
    private double diameter;
    // Models for connection graph (bit0=X+,bit1=X-,bit2=Y+,bit3=Y-,bit4=Z+,bit5=Z-), by texture index
    private RenderPatch[][][] models = new RenderPatch[64][][];
    // Base index (based on force parameter)
    private int base_index = 0;
    // Texture index map
    private int[] txtIndex;
    // Texture offset - added to texture key
    private int txtOffset = 0;
    // Indexing attribute
    private String idx_attrib = null;
    // Texture map ID, if being used
    private String map_id = null;
    // Texture count
    private int txtCount = 0;
    // Texture default index (if indexed and not found)
    private int txtDefIndex = -1;
    
    private String[] tileEntityAttribs = null;

    private void addIDs(String blkname) {
        DynmapBlockState bbs = DynmapBlockState.getBaseStateByName(blkname);
        if (bbs.isNotAir()) {
            for (int i = 0; i < bbs.getStateCount(); i++) {
                DynmapBlockState bs = bbs.getState(i);
                linked_ids.set(bs.globalStateIndex);
            }
        }
    }
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        String linkset = custparm.get("linkset");
        if(linkset == null) linkset = "default";
        linked_ids = linked_ids_by_set.get(linkset); /* Get our set */
        if(linked_ids == null) {
            linked_ids = new BitSet();
            linked_ids_by_set.put(linkset,  linked_ids);
        }
        addIDs(blkname);  // Add us to set
        // Get diameter
        String dia = custparm.get("diameter");
        if(dia == null) dia = "0.5";
        try {
            diameter = Double.parseDouble(dia);
        } catch (NumberFormatException nfx) {
            diameter = 0.5;
            Log.severe("Error: diameter must be number between 0.0 and 1.0");
        }
        if((diameter <= 0.0) || (diameter >= 1.0)) {
            Log.severe("Error: diameter must be number between 0.0 and 1.0");
            diameter = 0.5;
        }
        // Process other link block IDs
        for(String k : custparm.keySet()) {
            if(k.startsWith("link")) {
                addIDs(custparm.get(k));
            }
        }
        // Check for axis force
        String force = custparm.get("force");
        if(force != null) {
            String v = "xXyYzZ";
            for(int i = 0; i < v.length(); i++) {
                if(force.indexOf(v.charAt(i)) >= 0) {   
                    base_index |= (1 << i);
                }
            }
        }
        /* See if index attribute defined */
        String idx = custparm.get("textureIndex");
        if(idx != null) {
            txtOffset = 0;
            String txt_off = custparm.get("textureOffset");
            if(txt_off != null) {
                txtOffset = Integer.valueOf(txt_off);
            }
            idx_attrib = idx;
            String txt_def = custparm.get("textureDefault");
            if(txt_def != null) {
                txtDefIndex = Integer.valueOf(txt_def);
            }
            
            map_id = custparm.get("textureMap");
            if(map_id == null) {    /* If no map, indexes are explicit */
                ArrayList<Integer> map = new ArrayList<Integer>();
                for(int id = 0; ; id++) {
                    String v = custparm.get("index" + id);
                    if(v == null) break;
                    map.add(Integer.valueOf(v));
                }
                txtIndex = new int[map.size()];
                for(int id = 0; id < txtIndex.length; id++) {
                    txtIndex[id] = map.get(id).intValue() + txtOffset;
                }
                txtCount = txtIndex.length;
            }
            
            tileEntityAttribs = new String[1];
            tileEntityAttribs[0] = idx_attrib;
        }
        else {
            txtIndex = new int[1];
            txtCount = 1;
        }
        
        return true;
    }

    @Override
    public int getMaximumTextureCount(RenderPatchFactory rpf) {
        if(map_id != null) {
            if(txtCount == 0) {
                txtCount = rpf.getTextureCountFromMap(map_id);
                if(txtCount < 0) txtCount = 0;
            }
        }
        return txtCount;
    }
    
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return tileEntityAttribs;
    }

    private RenderPatch[] buildModel(RenderPatchFactory rpf, int idx, int txt_idx) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        int[] sides = { txt_idx,txt_idx,txt_idx,txt_idx,txt_idx,txt_idx };
        
        /* If we have an X axis match */
        if((idx & 0x3) != 0) {
            addBox(rpf, list, 
                ((idx & 1) != 0)?0.0:(0.5-diameter/2.0),
                ((idx & 2) != 0)?1.0:(0.5+diameter/2.0),
                (0.5 - diameter/2.0),
                (0.5 + diameter/2.0),
                (0.5 - diameter/2.0),
                (0.5 + diameter/2.0),
                sides);
        }
        /* If we have an Y axis match */
        if((idx & 0xC) != 0) {
            addBox(rpf, list, 
                (0.5 - diameter/2.0),
                (0.5 + diameter/2.0),
                ((idx & 0x4) != 0)?0.0:(0.5-diameter/2.0),
                ((idx & 0x8) != 0)?1.0:(0.5+diameter/2.0),
                (0.5 - diameter/2.0),
                (0.5 + diameter/2.0),
                sides);
        }
        /* If we have an Z axis match, or no links */
        if(((idx & 0x30) != 0) || (idx == 0)) {
            addBox(rpf, list, 
                (0.5 - diameter/2.0),
                (0.5 + diameter/2.0),
                (0.5 - diameter/2.0),
                (0.5 + diameter/2.0),
                ((idx & 0x10) != 0)?0.0:(0.5-diameter/2.0),
                ((idx & 0x20) != 0)?1.0:(0.5+diameter/2.0),
                sides);
        }
        
        return list.toArray(new RenderPatch[list.size()]);
    }

    private static final int[] x_off = { -1, 1, 0, 0, 0, 0 };
    private static final int[] y_off = { 0, 0, -1, 1, 0, 0 };
    private static final int[] z_off = { 0, 0, 0, 0, -1, 1 };
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int textureIdx = 0;
        
        if((map_id != null) && (txtCount == 0)) {
            txtCount = ctx.getPatchFactory().getTextureCountFromMap(map_id);
        }
        /* See if we have texture index */
        if(idx_attrib != null) {
            Object idxv = ctx.getBlockTileEntityField(idx_attrib);
            if(idxv instanceof Number) {
                int val = ((Number)idxv).intValue();
                if(map_id != null) {    /* If texture map, look up value there */
                    textureIdx = ctx.getPatchFactory().getTextureIndexFromMap(map_id, val - txtOffset);
                    if((textureIdx < 0) && (txtDefIndex >= 0))
                        textureIdx = ctx.getPatchFactory().getTextureIndexFromMap(map_id, txtDefIndex);
                    if(textureIdx < 0)
                        textureIdx = 0;
                }
                else {
                    for(int i = 0; i < txtIndex.length; i++) {
                        if(val == txtIndex[i]) {
                            textureIdx = i;
                            break;
                        }
                    }
                }
            }
        }
        
        int idx = base_index;
        for(int i = 0; i < x_off.length; i++) {
            if((idx & (1 << i)) != 0) continue;
            DynmapBlockState blk = ctx.getBlockTypeAt(x_off[i],  y_off[i],  z_off[i]);
            if(linked_ids.get(blk.globalStateIndex)) {
                idx |= (1 << i);
            }
        }
        RenderPatch[][] row = models[idx];
        /* If row not found, add it */
        if(row == null) {
            row = new RenderPatch[txtCount][];
            models[idx] = row;
        }
        /* If model not found, create it */
        RenderPatch[] model = null;
        if(textureIdx < row.length)
            model = row[textureIdx];
        if(model == null) {
            model = buildModel(ctx.getPatchFactory(), idx, textureIdx);
            if(textureIdx >= row.length) {
                row = Arrays.copyOf(row, textureIdx+1);
                models[idx] = row;
            }
            row[textureIdx] = model;
        }
        return model;
    }
}
