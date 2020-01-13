package org.dynmap.hdmap;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.dynmap.hdmap.TexturePack;
import org.dynmap.Log;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.hdmap.TexturePack.BlockTransparency;
import org.dynmap.hdmap.TexturePack.ColorizingData;
import org.dynmap.renderer.CustomColorMultiplier;
import org.dynmap.renderer.DynmapBlockState;

public class HDBlockStateTextureMap {

    private static HDBlockStateTextureMap[] texmaps = new HDBlockStateTextureMap[DynmapBlockState.getGlobalIndexMax()];   // List of texture maps, indexed by global state index

    int faces[];  /* texture index of image for each face (indexed by BlockStep.ordinal() OR patch index) */
    final byte[] layers;  /* If layered, each index corresponds to faces index, and value is index of next layer */
    final private String blockset;
    final int colorMult;
    final CustomColorMultiplier custColorMult;
    final boolean stdrotate; // Marked for corrected to proper : stdrot=true
    final private Integer colorMapping;   // If non-null, color mapping texture
    final BlockTransparency trans;

    public static final HDBlockStateTextureMap BLANK = new HDBlockStateTextureMap();
    
    // Default to a blank mapping
    HDBlockStateTextureMap() {
        blockset = null;
        colorMult = 0;
        custColorMult = null;
        faces = new int[] { TexturePack.TILEINDEX_BLANK, TexturePack.TILEINDEX_BLANK, TexturePack.TILEINDEX_BLANK, TexturePack.TILEINDEX_BLANK, TexturePack.TILEINDEX_BLANK, TexturePack.TILEINDEX_BLANK };
        layers = null;
        stdrotate = true;
        colorMapping = null;
        trans = BlockTransparency.TRANSPARENT;
    }
    // Create block state map with given attributes
    public HDBlockStateTextureMap(int[] faces, byte[] layers, int colorMult, CustomColorMultiplier custColorMult, String blockset, boolean stdrot, Integer colorIndex, BlockTransparency trans) {
        this.faces = faces;
        this.layers = layers;
        this.colorMult = colorMult;
        this.custColorMult = custColorMult;
        this.blockset = blockset;
        this.stdrotate = stdrot;
        this.colorMapping = colorIndex;
        this.trans = trans;
    }
    
    // Shallow copy state from another state map
    public HDBlockStateTextureMap(HDBlockStateTextureMap map, BlockTransparency bt) {
        this.faces = map.faces;
        this.layers = map.layers;
        this.blockset = map.blockset;
        this.colorMult = map.colorMult;
        this.custColorMult = map.custColorMult;
        this.stdrotate = map.stdrotate;
        this.colorMapping = map.colorMapping;
        if (bt != null)
            this.trans = bt;
        else
            this.trans = map.trans;
    }

    // Get texture index for given face
    public int getIndexForFace(int face) {
        if ((faces != null) && (faces.length > face))
            return faces[face];
        return TexturePack.TILEINDEX_BLANK;
    }

    public void resizeFaces(int cnt) {
        int[] newfaces = new int[cnt];
        System.arraycopy(faces, 0, newfaces, 0, faces.length);
        for(int i = faces.length; i < cnt; i++) {
            newfaces[i] = TexturePack.TILEINDEX_BLANK;
        }
        faces = newfaces;
    }
    
    // Add block state to table, with given block IDs and state indexes
    public void addToTable(List<String> blocknames, BitSet stateidx) {
        /* Add entries to lookup table */
        for (String blkname : blocknames) {
            DynmapBlockState baseblk = DynmapBlockState.getBaseStateByName(blkname);
            if (baseblk.isNotAir()) {
                if (stateidx != null) {
                	for (int stateid = stateidx.nextSetBit(0); stateid >= 0; stateid = stateidx.nextSetBit(stateid+1)) {
                        DynmapBlockState bs = baseblk.getState(stateid);
                        if (bs.isAir()) {
                        	Log.warning("Invalid texture block state: " + blkname + ":" + stateid);
                        	continue;
                        }
                        if ((this.blockset != null) && (this.blockset.equals("core") == false)) {
                            HDBlockModels.resetIfNotBlockSet(bs, this.blockset);
                        }
                        copyToStateIndex(bs, this, null);
                    }
                }
                else {  // Else, loop over all state IDs for given block
                    for (int stateid = 0; stateid < baseblk.getStateCount(); stateid++) {
                        DynmapBlockState bs = baseblk.getState(stateid);
                        if (bs.isAir()) {
                        	Log.warning("Invalid texture block state: " + blkname + ":" + stateid);
                        	continue;
                        }
                        if ((this.blockset != null) && (this.blockset.equals("core") == false)) {
                            HDBlockModels.resetIfNotBlockSet(bs, this.blockset);
                        }
                        copyToStateIndex(bs, this, null);
                    }
                }
            }
            else {
            	Log.warning("Invalid texture block name: " + blkname);
            }
        }
    }

    private static final void resize(int newend) {
        if (newend < texmaps.length) return; 
        HDBlockStateTextureMap[] newm = new HDBlockStateTextureMap[newend+1];
        System.arraycopy(texmaps,  0,  newm,  0,  texmaps.length);
        Arrays.fill(newm, texmaps.length, newm.length, HDBlockStateTextureMap.BLANK);
        texmaps = newm;
    }
    
    // Initialize/reset block texture table
    public static void initializeTable() {
    	Arrays.fill(texmaps, HDBlockStateTextureMap.BLANK);
    }
    
    // Lookup records by block state
    public static final HDBlockStateTextureMap getByBlockState(DynmapBlockState blk) {
        HDBlockStateTextureMap m = HDBlockStateTextureMap.BLANK;
        try {
            m = texmaps[blk.globalStateIndex];
        } catch (ArrayIndexOutOfBoundsException x) {
            resize(blk.globalStateIndex);
        }
        return m;
    }
    // Copy given block state to given state index
    public static void copyToStateIndex(DynmapBlockState blk, HDBlockStateTextureMap map, TexturePack.BlockTransparency trans) {
    	resize(blk.globalStateIndex);
    	if (trans == null) {
    		trans = map.trans;
    	}
        // Force waterloogged blocks to use SEMITRANSPARENT (same as water)
        if ((trans == TexturePack.BlockTransparency.TRANSPARENT) && blk.isWaterlogged()) {
            trans = TexturePack.BlockTransparency.SEMITRANSPARENT;
        }
        texmaps[blk.globalStateIndex] = new HDBlockStateTextureMap(map, trans);
    }
    // Copy textures from source block ID to destination
    public static void remapTexture(String dest, String src) {
        DynmapBlockState dblk = DynmapBlockState.getBaseStateByName(dest);
        DynmapBlockState sblk = DynmapBlockState.getBaseStateByName(src);
        int scnt = sblk.getStateCount();
        for (int i = 0; i < dblk.getStateCount(); i++) {
            int didx = dblk.getState(i).globalStateIndex;
            int sidx = sblk.getState(i % scnt).globalStateIndex;
            texmaps[didx] = new HDBlockStateTextureMap(texmaps[sidx], null);
        }
    }
    // Get by global state index
    public static HDBlockStateTextureMap getByGlobalIndex(int gidx) {
        HDBlockStateTextureMap m = HDBlockStateTextureMap.BLANK;
        try {
            m = texmaps[gidx];
        } catch (ArrayIndexOutOfBoundsException x) {
            resize(gidx);
        }
        return m;
    }
    // Get state by index
    public final HDBlockStateTextureMap getStateMap(DynmapBlockState blk, int stateid) {
    	return getByGlobalIndex(blk.getState(stateid).globalStateIndex);
    }
    // Get transparency for given block ID
    public static BlockTransparency getTransparency(DynmapBlockState blk) {
        BlockTransparency trans = BlockTransparency.OPAQUE;
        try {
            trans = texmaps[blk.globalStateIndex].trans;
        } catch (ArrayIndexOutOfBoundsException x) {
            resize(blk.globalStateIndex);
        }
        return trans;
    }
    // Build copy of block colorization data
    public static ColorizingData getColorizingData() {
        ColorizingData map = new ColorizingData();
        for (int j = 0; j < texmaps.length; j++) {
            if (texmaps[j].colorMapping != null) {
                map.setBlkStateValue(DynmapBlockState.getStateByGlobalIndex(j), texmaps[j].colorMapping);
            }
        }
        return map;
    }

}
